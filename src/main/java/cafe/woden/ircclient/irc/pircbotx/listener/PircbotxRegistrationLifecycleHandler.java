package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles post-registration numerics and related connection bootstrap side effects. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxRegistrationLifecycleHandler {
  private static final Logger log =
      LoggerFactory.getLogger(PircbotxRegistrationLifecycleHandler.class);

  @NonNull private final String serverId;
  @NonNull private final PircbotxConnectionState conn;
  @NonNull private final PlaybackCursorProvider playbackCursorProvider;
  @NonNull private final PircbotxBouncerDiscoveryCoordinator bouncerDiscovery;
  @NonNull private final PircbotxServerResponseEmitter serverResponses;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  boolean maybeHandle(int code, PircBotX bot, String line) {
    switch (code) {
      case 4:
        handleMyInfo(bot, line);
        return true;
      case 324:
        handleChannelMode(bot, line);
        return true;
      case 376:
      case 422:
        handleRegistrationComplete(code, bot, line);
        return true;
      default:
        return false;
    }
  }

  private void handleMyInfo(PircBotX bot, String line) {
    String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
    if (PircbotxZncParsers.seemsRpl004Znc(rawLine)) {
      bouncerDiscovery.maybeMarkZncDetected("RPL_MYINFO/004", "(" + rawLine + ")");
    }
    serverResponses.emitServerResponseLine(bot, 4, line);
  }

  private void handleRegistrationComplete(int code, PircBotX bot, String line) {
    Instant now = Instant.now();
    serverResponses.emitServerResponseLine(bot, code, line);
    emit.accept(new ServerIrcEvent(serverId, new IrcEvent.ConnectionReady(now)));
    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.ConnectionFeaturesUpdated(now, "post-registration")));
    logNegotiatedCaps();
    bouncerDiscovery.maybeRequestZncNetworks(bot);
    maybeRequestZncPlayback(bot);
    bouncerDiscovery.maybeRequestSojuNetworks(bot);
  }

  private void handleChannelMode(PircBotX bot, String line) {
    PircbotxChannelModeParsers.ParsedRpl324 parsed = PircbotxChannelModeParsers.parseRpl324(line);
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

    serverResponses.emitServerResponseLine(bot, 324, line);
  }

  private void maybeRequestZncPlayback(PircBotX bot) {
    if (bot == null) return;
    if (!conn.isZncPlaybackCapAcked()) return;
    if (!conn.beginZncPlaybackRequest()) return;

    OptionalLong cursor = playbackCursorProvider.lastSeenEpochSeconds(serverId);
    long request = Math.max(0L, cursor.orElse(0L) - 1L);

    try {
      bot.sendIRC().message("*playback", "play * " + request);
      log.info("[{}] requested ZNC playback since {} (epoch seconds)", serverId, request);
    } catch (Exception ex) {
      conn.clearZncPlaybackRequest();
      log.warn("[{}] failed to request ZNC playback", serverId, ex);
    }
  }

  private void logNegotiatedCaps() {
    if (!conn.beginCapabilitySummaryLog()) return;
    PircbotxConnectionState.CapabilitySnapshot caps = conn.capabilitySnapshot();
    boolean multiline = caps.multilineAvailable();
    boolean typing = caps.typingAvailable();
    log.debug(
        "[{}] negotiated caps: server-time={} standard-replies={} echo-message={} cap-notify={} labeled-response={} "
            + "setname={} chghost={} sts={} multiline={} multiline(final)={} multiline(final,max-bytes)={} "
            + "multiline(final,max-lines)={} multiline(draft)={} multiline(draft,max-bytes)={} "
            + "multiline(draft,max-lines)={} "
            + "draft/channel-context={} draft/reply={} draft/react={} draft/unreact={} draft/message-edit={} draft/message-redaction={} "
            + "message-tags={} typing-policy-known={} typing-allowed={} typing-available={} typing(cap)={} read-marker={} "
            + "monitor(isupport)={} monitor(cap)={} extended-monitor(cap)={} monitor(max-targets)={} "
            + "chathistory={} batch={} znc.in/playback={}",
        serverId,
        caps.serverTimeCapAcked(),
        caps.standardRepliesCapAcked(),
        caps.echoMessageCapAcked(),
        caps.capNotifyCapAcked(),
        caps.labeledResponseCapAcked(),
        caps.setnameCapAcked(),
        caps.chghostCapAcked(),
        caps.stsCapAcked(),
        multiline,
        caps.multilineCapAcked(),
        caps.multilineMaxBytes(),
        caps.multilineMaxLines(),
        caps.draftMultilineCapAcked(),
        caps.draftMultilineMaxBytes(),
        caps.draftMultilineMaxLines(),
        caps.draftChannelContextCapAcked(),
        caps.draftReplyCapAcked(),
        caps.draftReactCapAcked(),
        caps.draftUnreactCapAcked(),
        caps.draftMessageEditCapAcked(),
        caps.draftMessageRedactionCapAcked(),
        caps.messageTagsCapAcked(),
        caps.typingClientTagPolicyKnown(),
        caps.typingClientTagAllowed(),
        typing,
        caps.typingCapAcked(),
        caps.readMarkerCapAcked(),
        caps.monitorSupported(),
        caps.monitorCapAcked(),
        caps.extendedMonitorCapAcked(),
        caps.monitorMaxTargets(),
        caps.chatHistoryCapAcked(),
        caps.batchCapAcked(),
        caps.zncPlaybackCapAcked());

    if (!caps.serverTimeCapAcked() && conn.shouldWarnMissingServerTime()) {
      String msg =
          "IRCv3 server-time was not negotiated; message ordering/timestamps may be less accurate (especially on reconnect/backlog).";
      log.warn("[{}] {}", serverId, msg);
      emit.accept(
          new ServerIrcEvent(serverId, new IrcEvent.ServerTimeNotNegotiated(Instant.now(), msg)));
    }

    if (!typing && conn.shouldWarnUnavailableTyping()) {
      String reason;
      if (!caps.messageTagsCapAcked()) {
        reason = "message-tags not negotiated";
      } else if (!caps.typingCapAcked() && !caps.typingClientTagPolicyKnown()) {
        reason = "typing capability not negotiated";
      } else if (!caps.typingCapAcked() && !caps.typingClientTagAllowed()) {
        reason = "server denies +typing via CLIENTTAGDENY";
      } else {
        reason = "unknown";
      }
      log.debug("[{}] IRCv3 typing indicators are unavailable ({})", serverId, reason);
    }
  }
}
