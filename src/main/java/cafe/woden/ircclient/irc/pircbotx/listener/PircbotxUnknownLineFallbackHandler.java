package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPlaybackCaptureRecorder;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPrivateConversationSupport;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventMetadata;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.UnknownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles raw unknown-line fallbacks after the dedicated unknown-line translators decline them. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class PircbotxUnknownLineFallbackHandler {
  private static final Logger log =
      LoggerFactory.getLogger(PircbotxUnknownLineFallbackHandler.class);

  @NonNull private final String serverId;
  @NonNull private final PircbotxConnectionState conn;

  @NonNull private final PircbotxBouncerDiscoveryCoordinator bouncerDiscovery;
  @NonNull private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  @NonNull private final PircbotxServerResponseEmitter serverResponses;
  @NonNull private final PircbotxSaslFailureHandler saslFailures;
  @NonNull private final PircbotxIsupportObserver isupportObserver;
  @NonNull private final PircbotxWhoEventEmitter whoEvents;
  @NonNull private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  @NonNull private final PircbotxPrivateConversationSupport privateConversationSupport;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  @NonNull private final Function<PircBotX, String> selfNickResolver;

  PircbotxUnknownLineFallbackHandler(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxBouncerDiscoveryCoordinator bouncerDiscovery,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      PircbotxServerResponseEmitter serverResponses,
      PircbotxSaslFailureHandler saslFailures,
      PircbotxIsupportObserver isupportObserver,
      PircbotxWhoEventEmitter whoEvents,
      Consumer<ServerIrcEvent> emit,
      Function<PircBotX, String> selfNickResolver) {
    this(
        serverId,
        conn,
        bouncerDiscovery,
        chatHistoryBatches,
        serverResponses,
        saslFailures,
        isupportObserver,
        whoEvents,
        new PircbotxPlaybackCaptureRecorder(conn),
        new PircbotxPrivateConversationSupport(conn),
        emit,
        selfNickResolver);
  }

  void handle(UnknownEvent event, String lineWithTags, String normalizedRawLine) {
    if (maybeCaptureUnknownPrivmsgOrNotice(event, lineWithTags, normalizedRawLine)) {
      return;
    }
    if (chatHistoryBatches.maybeCaptureUnknownLine(lineWithTags, normalizedRawLine)) {
      return;
    }

    Integer saslCode = saslFailures.parseFailureCode(normalizedRawLine);
    if (saslCode != null) {
      saslFailures.handle(saslCode, normalizedRawLine);
      return;
    }

    if (bouncerDiscovery.maybeCaptureUnknownLine(normalizedRawLine)) {
      return;
    }

    isupportObserver.observe(normalizedRawLine);
    if (normalizedRawLine != null && normalizedRawLine.contains(" AWAY") && log.isDebugEnabled()) {
      log.debug(
          "[{}] inbound AWAY-ish line received in onUnknown: {}", serverId, normalizedRawLine);
    }

    PircbotxAwayParsers.ParsedAwayNotify awayNotify =
        PircbotxAwayParsers.parseAwayNotify(normalizedRawLine);
    if (awayNotify != null && awayNotify.nick() != null && !awayNotify.nick().isBlank()) {
      log.debug(
          "[{}] parsed away-notify: nick={} state={} msg={}",
          serverId,
          awayNotify.nick(),
          awayNotify.awayState(),
          awayNotify.message());
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserAwayStateObserved(
                  Instant.now(), awayNotify.nick(), awayNotify.awayState(), awayNotify.message())));
      return;
    } else if (normalizedRawLine != null
        && normalizedRawLine.contains(" AWAY")
        && log.isDebugEnabled()) {
      log.debug(
          "[{}] inbound AWAY-ish line did NOT parse as away-notify: {}",
          serverId,
          normalizedRawLine);
    }

    PircbotxChannelModeParsers.ParsedRpl324 parsed =
        PircbotxChannelModeParsers.parseRpl324(normalizedRawLine);
    if (parsed != null) {
      if (!conn.tryClaimChannelMode324(parsed.channel(), parsed.details())) {
        return;
      }
      emit.accept(
          new ServerIrcEvent(
              serverId,
              ChannelModeObservationFactory.fromNumeric324(
                  Instant.now(), parsed.channel(), parsed.details())));
      return;
    }

    PircbotxAwayParsers.ParsedAwayConfirmation away =
        PircbotxAwayParsers.parseRpl305or306Away(normalizedRawLine);
    if (away != null) {
      String msg = away.message();
      if (msg == null || msg.isBlank()) {
        msg =
            away.away()
                ? "You have been marked as being away"
                : "You are no longer marked as being away";
      }
      emit.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.AwayStatusChanged(Instant.now(), away.away(), msg)));
      return;
    }

    whoEvents.maybeEmitLine(normalizedRawLine);
  }

  private boolean maybeCaptureUnknownPrivmsgOrNotice(
      UnknownEvent event, String lineWithTags, String normalizedRawLine) {
    try {
      ParsedIrcLine parsed = PircbotxInboundLineParsers.parseIrcLine(normalizedRawLine);
      if (parsed == null || parsed.command() == null) return false;

      String command = parsed.command().toUpperCase(Locale.ROOT);
      if (!"PRIVMSG".equals(command) && !"NOTICE".equals(command)) return false;

      Map<String, String> ircv3Tags = Ircv3Tags.fromRawLine(lineWithTags);
      String messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);
      Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(lineWithTags);
      if (at == null) at = Instant.now();
      String from = PircbotxInboundLineParsers.nickFromPrefix(parsed.prefix());

      String botNick =
          Objects.toString(selfNickResolver.apply(event != null ? event.getBot() : null), "");
      String trailing = Objects.toString(parsed.trailing(), "");
      String dest =
          (parsed.params() != null && !parsed.params().isEmpty()) ? parsed.params().get(0) : "";
      String target = Objects.toString(dest, "");
      if (target.isBlank()) target = from;

      String action = PircbotxUtil.parseCtcpAction(trailing);
      ChatHistoryEntry.Kind kind;
      String payload;
      if (action != null) {
        kind = ChatHistoryEntry.Kind.ACTION;
        payload = action;
      } else if ("NOTICE".equals(command)) {
        kind = ChatHistoryEntry.Kind.NOTICE;
        payload = trailing;
      } else {
        kind = ChatHistoryEntry.Kind.PRIVMSG;
        payload = trailing;
      }

      if (!target.startsWith("#") && !target.startsWith("&")) {
        target = privateConversationSupport.deriveConversationTarget(botNick, from, dest);
      }

      boolean fromSelf = !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
      if (privateConversationSupport.shouldSuppressSelfBootstrapMessage(fromSelf, dest, payload)) {
        return true;
      }

      if (bouncerDiscovery.maybeCaptureZncListNetworks(from, payload)) {
        return true;
      }

      if ("NOTICE".equals(command)
          && serverResponses.maybeEmitAlisChannelListEntry(at, from, payload)) {
        return true;
      }

      return playbackCaptureRecorder.maybeCapture(
          target, at, kind, from, payload, messageId, ircv3Tags);
    } catch (Exception ignored) {
      return false;
    }
  }
}
