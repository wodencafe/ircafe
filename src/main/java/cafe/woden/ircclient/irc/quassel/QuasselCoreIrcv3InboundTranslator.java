package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.stripLeadingColon;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3IsupportRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3StandardReplyRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Translates installed IRCv3 provider observations without owning transport or session state. */
final class QuasselCoreIrcv3InboundTranslator {
  private final QuasselIrcv3RuntimeSupport ircv3RuntimeSupport;

  QuasselCoreIrcv3InboundTranslator(QuasselIrcv3RuntimeSupport ircv3RuntimeSupport) {
    this.ircv3RuntimeSupport = Objects.requireNonNull(ircv3RuntimeSupport, "ircv3RuntimeSupport");
  }

  record Observation(
      Instant at, String fromDisplay, QuasselCoreIrcEnvelope envelope, String messageId) {}

  boolean handleCommand(
      Observation observation, Function<String, String> resolveTarget, Consumer<IrcEvent> emit) {
    QuasselCoreIrcEnvelope envelope = observation.envelope();
    if (envelope == null || !envelope.parsed()) return false;
    if (emitStandardReplyFromCommand(observation.at(), envelope, observation.messageId(), emit)) {
      return true;
    }
    switch (envelope.command()) {
      case "MARKREAD" -> {
        emitReadMarkerFromCommand(
            observation.at(), observation.fromDisplay(), envelope, resolveTarget, emit);
        return true;
      }
      case "REDACT" -> {
        emitRedactionFromCommand(
            observation.at(), observation.fromDisplay(), envelope, resolveTarget, emit);
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  boolean handleMonitor(
      Instant at,
      String rawLine,
      Consumer<Ircv3IsupportRuntimeSupport.MonitorSupport> observeSupport,
      Consumer<IrcEvent> emit) {
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) return false;

    ircv3RuntimeSupport.monitorSupport(raw).ifPresent(observeSupport);
    boolean handled = false;
    for (Ircv3InboundCommandSignal signal : ircv3RuntimeSupport.monitorSignals(raw)) {
      if (signal instanceof Ircv3InboundCommandSignal.MonitorStatusObserved status) {
        List<String> nicks = monitorNickList(status.entries());
        emitMonitorHostmaskObservations(at, status.entries(), emit);
        if (!nicks.isEmpty()) {
          IrcEvent event =
              status.online()
                  ? new IrcEvent.MonitorOnlineObserved(at, nicks)
                  : new IrcEvent.MonitorOfflineObserved(at, nicks);
          emit.accept(event);
        }
        handled = true;
      } else if (signal instanceof Ircv3InboundCommandSignal.MonitorListObserved list) {
        emit.accept(new IrcEvent.MonitorListObserved(at, list.nicks()));
        handled = true;
      } else if (signal instanceof Ircv3InboundCommandSignal.MonitorListEnded) {
        emit.accept(new IrcEvent.MonitorListEnded(at));
        handled = true;
      } else if (signal instanceof Ircv3InboundCommandSignal.MonitorListFull full) {
        emit.accept(new IrcEvent.MonitorListFull(at, full.limit(), full.nicks(), full.message()));
        handled = true;
      }
    }
    return handled;
  }

  private static List<String> monitorNickList(
      List<Ircv3InboundCommandSignal.MonitorStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(entries.size());
    for (Ircv3InboundCommandSignal.MonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      if (!nick.isEmpty()) out.add(nick);
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private static void emitMonitorHostmaskObservations(
      Instant at,
      List<Ircv3InboundCommandSignal.MonitorStatusEntry> entries,
      Consumer<IrcEvent> emit) {
    if (entries == null || entries.isEmpty()) return;
    for (Ircv3InboundCommandSignal.MonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      String hostmask = Objects.toString(entry.hostmask(), "").trim();
      if (nick.isEmpty() || !PircbotxUtil.isUsefulHostmask(hostmask)) continue;
      emit.accept(new IrcEvent.UserHostmaskObserved(at, "", nick, hostmask));
    }
  }

  void observeTags(
      Observation observation, Function<String, String> resolveTarget, Consumer<IrcEvent> emit) {
    Instant at = observation.at();
    String fromDisplay = observation.fromDisplay();
    QuasselCoreIrcEnvelope envelope = observation.envelope();
    String messageId = observation.messageId();
    if (envelope == null) return;
    Map<String, String> tags = envelope.ircv3Tags();
    if (tags == null || tags.isEmpty()) return;

    Ircv3InboundTagRequest request =
        new Ircv3InboundTagRequest(
            envelope.command(),
            fromDisplay,
            envelope.firstParam(),
            envelope.params(),
            tags,
            envelope.rawLine());
    String convTarget = resolveSignalTarget(request, resolveTarget);
    String from = request.sourceNick();
    if (from.isEmpty()) {
      // Route senderless direct messages using their recipient before applying the event fallback.
      from = "server";
      request =
          new Ircv3InboundTagRequest(
              request.command(),
              from,
              request.rawTarget(),
              request.parameters(),
              request.tags(),
              request.rawLine());
    }

    List<Ircv3InboundTagSignal> signals = ircv3RuntimeSupport.conversationSignals(request);
    for (Ircv3InboundTagSignal signal : signals) {
      if (signal == null) continue;
      switch (signal.type()) {
        case REPLY ->
            emit.accept(
                new IrcEvent.MessageReplyObserved(at, from, convTarget, signal.primaryValue()));
        case REACT -> {
          String targetMessageId = signal.secondaryValue();
          if (targetMessageId.isBlank()) {
            targetMessageId = Objects.toString(messageId, "").trim();
          }
          emit.accept(
              new IrcEvent.MessageReactObserved(
                  at, from, convTarget, signal.primaryValue(), targetMessageId));
        }
        case UNREACT -> {
          String targetMessageId = signal.secondaryValue();
          if (targetMessageId.isBlank()) {
            targetMessageId = Objects.toString(messageId, "").trim();
          }
          emit.accept(
              new IrcEvent.MessageUnreactObserved(
                  at, from, convTarget, signal.primaryValue(), targetMessageId));
        }
        case MESSAGE_REDACTION ->
            emit.accept(
                new IrcEvent.MessageRedactionObserved(at, from, convTarget, signal.primaryValue()));
        case TYPING ->
            emit.accept(
                new IrcEvent.UserTypingObserved(at, from, convTarget, signal.primaryValue()));
        case READ_MARKER ->
            emit.accept(
                new IrcEvent.ReadMarkerObserved(at, from, convTarget, signal.primaryValue()));
        default -> {
          // Other runtime tag signals are handled by their owning transport adapter.
        }
      }
    }
  }

  private boolean emitStandardReplyFromCommand(
      Instant at,
      QuasselCoreIrcEnvelope envelope,
      String fallbackMessageId,
      Consumer<IrcEvent> emit) {
    if (envelope == null || !envelope.parsed()) return false;
    Ircv3StandardReplyRuntimeSupport.Observation reply =
        ircv3RuntimeSupport
            .standardReply(
                envelope.command(),
                envelope.rawLine(),
                envelope.params(),
                envelope.trailing(),
                envelope.ircv3Tags(),
                fallbackMessageId)
            .orElse(null);
    if (reply == null) return false;

    emit.accept(
        new IrcEvent.StandardReply(
            at,
            toRootStandardReplyKind(reply.kind()),
            reply.command(),
            reply.code(),
            reply.context(),
            reply.description(),
            envelope.rawLine(),
            reply.messageId(),
            envelope.ircv3Tags()));
    return true;
  }

  private static IrcEvent.StandardReplyKind toRootStandardReplyKind(
      Ircv3StandardReplyRuntimeSupport.Kind kind) {
    return switch (kind) {
      case FAIL -> IrcEvent.StandardReplyKind.FAIL;
      case WARN -> IrcEvent.StandardReplyKind.WARN;
      case NOTE -> IrcEvent.StandardReplyKind.NOTE;
    };
  }

  private void emitReadMarkerFromCommand(
      Instant at,
      String fromDisplay,
      QuasselCoreIrcEnvelope envelope,
      Function<String, String> resolveTarget,
      Consumer<IrcEvent> emit) {
    if (envelope == null || !envelope.parsed()) return;
    String normalizedFrom = Objects.toString(fromDisplay, "").trim();
    String observedFrom = normalizedFrom.isEmpty() ? "server" : normalizedFrom;
    ircv3RuntimeSupport
        .readMarkerFromCommand(
            observedFrom,
            envelope.command(),
            envelope.rawLine(),
            envelope.params(),
            envelope.ircv3Tags())
        .ifPresent(
            observed -> {
              String resolvedTarget = resolveTarget.apply(observed.target());
              emit.accept(
                  new IrcEvent.ReadMarkerObserved(
                      at, observedFrom, resolvedTarget, observed.marker()));
            });
  }

  private void emitRedactionFromCommand(
      Instant at,
      String fromDisplay,
      QuasselCoreIrcEnvelope envelope,
      Function<String, String> resolveTarget,
      Consumer<IrcEvent> emit) {
    if (envelope == null || !envelope.parsed()) return;
    String observedFrom = Objects.toString(fromDisplay, "").trim();
    String from = observedFrom.isEmpty() ? "server" : observedFrom;
    ircv3RuntimeSupport
        .redactionFromCommand(
            from, envelope.command(), envelope.rawLine(), envelope.params(), envelope.ircv3Tags())
        .ifPresent(
            observed -> {
              String resolvedTarget = resolveTarget.apply(observed.target());
              emit.accept(
                  new IrcEvent.MessageRedactionObserved(
                      at, from, resolvedTarget, observed.messageId()));
            });
  }

  private String resolveSignalTarget(
      Ircv3InboundTagRequest request, Function<String, String> resolveTarget) {
    String channelContext = ircv3RuntimeSupport.channelContext(request);
    String targetHint = stripLeadingColon(channelContext);
    if (targetHint.isBlank()) {
      targetHint = stripLeadingColon(request.rawTarget());
    }
    return resolveTarget.apply(targetHint);
  }
}
