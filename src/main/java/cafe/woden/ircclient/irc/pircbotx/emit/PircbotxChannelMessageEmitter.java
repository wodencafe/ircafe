package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventMetadata;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits structured channel message events for a single IRC connection. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PircbotxChannelMessageEmitter {
  private static final Logger log = LoggerFactory.getLogger(PircbotxChannelMessageEmitter.class);
  @NonNull private final String serverId;

  @NonNull private final PircbotxRosterEmitter rosterEmitter;
  @NonNull private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  @NonNull private final Ircv3MultilineAccumulator multilineAccumulator;
  @NonNull private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  @NonNull private final Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport;
  @NonNull private final Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  public PircbotxChannelMessageEmitter(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxRosterEmitter rosterEmitter,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      Ircv3MultilineAccumulator multilineAccumulator,
      Consumer<ServerIrcEvent> emit,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport) {
    this(
        serverId,
        rosterEmitter,
        chatHistoryBatches,
        multilineAccumulator,
        new PircbotxPlaybackCaptureRecorder(conn),
        serverTimeRuntimeSupport,
        messageTagsRuntimeSupport,
        emit);
  }

  public void onMessage(MessageEvent event) {
    Optional<String> batchId =
        chatHistoryBatches.batchId(
            PircbotxEventMetadata.ircv3TagsFromEvent(event, messageTagsRuntimeSupport));
    if (batchId.isPresent()) {
      Instant at = PircbotxEventMetadata.inboundAt(event, serverTimeRuntimeSupport);
      String from = (event.getUser() != null) ? event.getUser().getNick() : "";
      String msg = PircbotxUtil.safeStr(event::getMessage, "");
      String action = PircbotxUtil.parseCtcpAction(msg);
      Map<String, String> tags =
          PircbotxEventMetadata.ircv3TagsFromEvent(event, messageTagsRuntimeSupport);
      String messageId = messageTagsRuntimeSupport.messageId(tags);
      String target = event.getChannel() != null ? event.getChannel().getName() : "";
      ChatHistoryEntry.Kind kind =
          action != null ? ChatHistoryEntry.Kind.ACTION : ChatHistoryEntry.Kind.PRIVMSG;
      String payload = action != null ? action : msg;

      if (chatHistoryBatches.appendIfActive(
          batchId.get(), kind, at, target, from, payload, messageId, tags)) {
        return;
      }
    }

    Instant at = PircbotxEventMetadata.inboundAt(event, serverTimeRuntimeSupport);
    String channel = event.getChannel().getName();
    rosterEmitter.maybeEmitHostmaskObserved(channel, event.getUser());
    String msg = event.getMessage();
    String from = (event.getUser() == null) ? "" : event.getUser().getNick();
    Map<String, String> ircv3Tags =
        PircbotxEventMetadata.withObservedHostmaskTag(
            new HashMap<>(
                PircbotxEventMetadata.ircv3TagsFromEvent(event, messageTagsRuntimeSupport)),
            event.getUser());
    String messageId = messageTagsRuntimeSupport.messageId(ircv3Tags);
    Ircv3MultilineAccumulator.FoldResult folded =
        multilineAccumulator.fold("PRIVMSG", from, channel, at, msg, messageId, ircv3Tags);
    if (folded.suppressed()) {
      return;
    }
    if (folded.at() != null) at = folded.at();
    msg = folded.text();
    ircv3Tags = folded.tags();
    if (folded.messageId() != null && !folded.messageId().isBlank()) {
      messageId = folded.messageId();
    } else {
      messageId = messageTagsRuntimeSupport.messageId(ircv3Tags);
    }

    String action = PircbotxUtil.parseCtcpAction(msg);
    if (action != null) {
      if (playbackCaptureRecorder.maybeCapture(
          channel, at, ChatHistoryEntry.Kind.ACTION, from, action, messageId, ircv3Tags)) {
        return;
      }
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelAction(at, channel, from, action, messageId, ircv3Tags)));
      return;
    }

    if (playbackCaptureRecorder.maybeCapture(
        channel, at, ChatHistoryEntry.Kind.PRIVMSG, from, msg, messageId, ircv3Tags)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "[{}] inbound channel message target={} at={} batch={} msgid={} outcome=history-captured",
            serverId,
            channel,
            at,
            ircv3Tags.get("batch"),
            messageId);
      }
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "[{}] inbound channel message target={} at={} batch={} msgid={} outcome=event-emitted",
          serverId,
          channel,
          at,
          ircv3Tags.get("batch"),
          messageId);
    }
    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.ChannelMessage(at, channel, from, msg, messageId, ircv3Tags)));
  }
}
