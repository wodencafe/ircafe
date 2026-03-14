package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pircbotx.Channel;
import org.pircbotx.hooks.events.NoticeEvent;

/** Emits structured notice events for a single IRC connection. */
final class PircbotxNoticeEventEmitter {
  private final String serverId;
  private final PircbotxConnectionState conn;
  private final PircbotxRosterEmitter rosterEmitter;
  private final PircbotxBouncerDiscoveryCoordinator bouncerDiscovery;
  private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  private final Ircv3MultilineAccumulator multilineAccumulator;
  private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  private final PircbotxServerResponseEmitter serverResponses;
  private final Consumer<ServerIrcEvent> emit;
  private final Function<Object, String> senderNickResolver;

  PircbotxNoticeEventEmitter(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxRosterEmitter rosterEmitter,
      PircbotxBouncerDiscoveryCoordinator bouncerDiscovery,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      Ircv3MultilineAccumulator multilineAccumulator,
      PircbotxServerResponseEmitter serverResponses,
      Consumer<ServerIrcEvent> emit,
      Function<Object, String> senderNickResolver) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.rosterEmitter = Objects.requireNonNull(rosterEmitter, "rosterEmitter");
    this.bouncerDiscovery = Objects.requireNonNull(bouncerDiscovery, "bouncerDiscovery");
    this.chatHistoryBatches = Objects.requireNonNull(chatHistoryBatches, "chatHistoryBatches");
    this.multilineAccumulator =
        Objects.requireNonNull(multilineAccumulator, "multilineAccumulator");
    this.playbackCaptureRecorder = new PircbotxPlaybackCaptureRecorder(conn);
    this.serverResponses = Objects.requireNonNull(serverResponses, "serverResponses");
    this.emit = Objects.requireNonNull(emit, "emit");
    this.senderNickResolver = Objects.requireNonNull(senderNickResolver, "senderNickResolver");
  }

  void onNotice(NoticeEvent event) {
    String from = senderNickResolver.apply(event);
    Optional<String> batchId = PircbotxIrcv3BatchTag.fromEvent(event);
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
      conn.zncPlaybackCapture.onPlaybackControlLine(notice);
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
