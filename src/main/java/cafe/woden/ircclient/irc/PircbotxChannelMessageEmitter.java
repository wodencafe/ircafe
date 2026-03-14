package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.pircbotx.hooks.events.MessageEvent;

/** Emits structured channel message events for a single IRC connection. */
final class PircbotxChannelMessageEmitter {
  private final String serverId;

  private final PircbotxRosterEmitter rosterEmitter;
  private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  private final Ircv3MultilineAccumulator multilineAccumulator;
  private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  private final Consumer<ServerIrcEvent> emit;

  PircbotxChannelMessageEmitter(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxRosterEmitter rosterEmitter,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      Ircv3MultilineAccumulator multilineAccumulator,
      Consumer<ServerIrcEvent> emit) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");

    this.rosterEmitter = Objects.requireNonNull(rosterEmitter, "rosterEmitter");
    this.chatHistoryBatches = Objects.requireNonNull(chatHistoryBatches, "chatHistoryBatches");
    this.multilineAccumulator =
        Objects.requireNonNull(multilineAccumulator, "multilineAccumulator");
    this.playbackCaptureRecorder = new PircbotxPlaybackCaptureRecorder(conn);
    this.emit = Objects.requireNonNull(emit, "emit");
  }

  void onMessage(MessageEvent event) {
    Optional<String> batchId = PircbotxIrcv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      Instant at = PircbotxEventMetadata.inboundAt(event);
      String from = (event.getUser() != null) ? event.getUser().getNick() : "";
      String msg = PircbotxUtil.safeStr(event::getMessage, "");
      String action = PircbotxUtil.parseCtcpAction(msg);
      Map<String, String> tags = PircbotxEventMetadata.ircv3TagsFromEvent(event);
      String messageId = PircbotxEventMetadata.ircv3MessageId(tags);
      String target = event.getChannel() != null ? event.getChannel().getName() : "";
      ChatHistoryEntry.Kind kind =
          action != null ? ChatHistoryEntry.Kind.ACTION : ChatHistoryEntry.Kind.PRIVMSG;
      String payload = action != null ? action : msg;

      if (chatHistoryBatches.appendIfActive(
          batchId.get(), kind, at, target, from, payload, messageId, tags)) {
        return;
      }
    }

    Instant at = PircbotxEventMetadata.inboundAt(event);
    String channel = event.getChannel().getName();
    rosterEmitter.maybeEmitHostmaskObserved(channel, event.getUser());
    String msg = event.getMessage();
    String from = (event.getUser() == null) ? "" : event.getUser().getNick();
    Map<String, String> ircv3Tags =
        PircbotxEventMetadata.withObservedHostmaskTag(
            new HashMap<>(PircbotxEventMetadata.ircv3TagsFromEvent(event)), event.getUser());
    String messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);
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
      messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);
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
      return;
    }

    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.ChannelMessage(at, channel, from, msg, messageId, ircv3Tags)));
  }
}
