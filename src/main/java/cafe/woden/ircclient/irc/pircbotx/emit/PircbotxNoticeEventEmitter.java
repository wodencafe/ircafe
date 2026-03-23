package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.PircbotxBouncerDiscoveryCoordinator;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventMetadata;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.Channel;
import org.pircbotx.hooks.events.NoticeEvent;

/** Emits structured notice events for a single IRC connection. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxNoticeEventEmitter {
  @NonNull private final String serverId;
  @NonNull private final PircbotxConnectionState conn;
  @NonNull private final PircbotxRosterEmitter rosterEmitter;
  @NonNull private final PircbotxBouncerDiscoveryCoordinator bouncerDiscovery;
  @NonNull private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  @NonNull private final Ircv3MultilineAccumulator multilineAccumulator;
  @NonNull private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  @NonNull private final PircbotxServerResponseEmitter serverResponses;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  @NonNull private final Function<Object, String> senderNickResolver;

  public PircbotxNoticeEventEmitter(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxRosterEmitter rosterEmitter,
      PircbotxBouncerDiscoveryCoordinator bouncerDiscovery,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      Ircv3MultilineAccumulator multilineAccumulator,
      PircbotxServerResponseEmitter serverResponses,
      Consumer<ServerIrcEvent> emit,
      Function<Object, String> senderNickResolver) {
    this(
        serverId,
        conn,
        rosterEmitter,
        bouncerDiscovery,
        chatHistoryBatches,
        multilineAccumulator,
        new PircbotxPlaybackCaptureRecorder(conn),
        serverResponses,
        emit,
        senderNickResolver);
  }

  public void onNotice(NoticeEvent event) {
    String from = senderNickResolver.apply(event);
    Optional<String> batchId = Ircv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      Instant at = PircbotxEventMetadata.inboundAt(event);
      String notice = PircbotxUtil.safeStr(event::getNotice, "");
      Map<String, String> tags = PircbotxEventMetadata.ircv3TagsFromEvent(event);
      String messageId = PircbotxEventMetadata.ircv3MessageId(tags);
      if (chatHistoryBatches.appendIfActive(
          batchId.get(),
          ChatHistoryEntry.Kind.NOTICE,
          at,
          "status",
          from,
          notice,
          messageId,
          tags)) {
        return;
      }
    }

    Instant at = PircbotxEventMetadata.inboundAt(event);
    String notice = event.getNotice();
    Map<String, String> ircv3Tags =
        PircbotxEventMetadata.withObservedHostmaskTag(
            new HashMap<>(PircbotxEventMetadata.ircv3TagsFromEvent(event)), event.getUser());
    String messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);
    String foldTarget = null;
    try {
      Channel channel = event.getChannel();
      if (channel != null) foldTarget = channel.getName();
    } catch (Exception ignored) {
    }
    if (foldTarget == null || foldTarget.isBlank()) foldTarget = from;

    Ircv3MultilineAccumulator.FoldResult folded =
        multilineAccumulator.fold("NOTICE", from, foldTarget, at, notice, messageId, ircv3Tags);
    if (folded.suppressed()) {
      return;
    }
    if (folded.at() != null) at = folded.at();
    notice = folded.text();
    ircv3Tags = folded.tags();
    if (folded.messageId() != null && !folded.messageId().isBlank()) {
      messageId = folded.messageId();
    } else {
      messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);
    }

    boolean recognizedAlisNotice = serverResponses.maybeEmitAlisChannelListEntry(at, from, notice);

    if (bouncerDiscovery.maybeCaptureZncListNetworks(from, notice)) {
      return;
    }

    if ("*playback".equalsIgnoreCase(from)) {
      conn.onPlaybackControlLine(notice);
    }

    if (!recognizedAlisNotice && !"*playback".equalsIgnoreCase(from)) {
      String target = null;
      try {
        Channel channel = event.getChannel();
        if (channel != null) target = channel.getName();
      } catch (Exception ignored) {
      }
      if (target == null || target.isBlank()) target = from;
      if (target != null
          && playbackCaptureRecorder.maybeCapture(
              target, at, ChatHistoryEntry.Kind.NOTICE, from, notice, messageId, ircv3Tags)) {
        return;
      }
    }

    if (event.getUser() != null) {
      rosterEmitter.maybeEmitHostmaskObserved("", event.getUser());
    }

    String target = null;
    try {
      Channel channel = event.getChannel();
      if (channel != null) target = channel.getName();
    } catch (Exception ignored) {
    }

    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.Notice(at, from, target, notice, messageId, ircv3Tags)));
  }
}
