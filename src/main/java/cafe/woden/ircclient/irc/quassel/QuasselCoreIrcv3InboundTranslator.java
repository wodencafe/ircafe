package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.stripLeadingColon;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3StandardReplyRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import java.time.Instant;
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

  void observeTags(
      Observation observation, Function<String, String> resolveTarget, Consumer<IrcEvent> emit) {
    Instant at = observation.at();
    String fromDisplay = observation.fromDisplay();
    QuasselCoreIrcEnvelope envelope = observation.envelope();
    String messageId = observation.messageId();
    if (envelope == null) return;
    Map<String, String> tags = envelope.ircv3Tags();
    if (tags == null || tags.isEmpty()) return;

    String from = Objects.toString(fromDisplay, "").trim();
    if (from.isEmpty()) from = "server";
    String convTarget = resolveSignalTarget(fromDisplay, envelope, tags, resolveTarget);

    List<Ircv3InboundTagSignal> signals =
        ircv3RuntimeSupport.conversationSignals(
            envelope.command(),
            from,
            envelope.firstParam(),
            envelope.params(),
            tags,
            envelope.rawLine());
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
      String fromDisplay,
      QuasselCoreIrcEnvelope envelope,
      Map<String, String> tags,
      Function<String, String> resolveTarget) {
    String channelContext =
        ircv3RuntimeSupport.channelContext(
            envelope.command(),
            fromDisplay,
            envelope.firstParam(),
            envelope.params(),
            tags,
            envelope.rawLine());
    String targetHint = stripLeadingColon(channelContext);
    if (targetHint.isBlank()) {
      targetHint = stripLeadingColon(envelope.firstParam());
    }
    return resolveTarget.apply(targetHint);
  }
}
