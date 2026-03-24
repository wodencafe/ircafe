package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
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

/** Emits structured channel message events for a single IRC connection. */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PircbotxChannelMessageEmitter {
  @NonNull private final String serverId;

  @NonNull private final PircbotxRosterEmitter rosterEmitter;
  @NonNull private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  @NonNull private final Ircv3MultilineAccumulator multilineAccumulator;
  @NonNull private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  public PircbotxChannelMessageEmitter(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxRosterEmitter rosterEmitter,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      Ircv3MultilineAccumulator multilineAccumulator,
      Consumer<ServerIrcEvent> emit) {
    this(
        serverId,
        rosterEmitter,
        chatHistoryBatches,
        multilineAccumulator,
        new PircbotxPlaybackCaptureRecorder(conn),
        emit);
  }

  public void onMessage(MessageEvent event) {
    Optional<String> batchId = Ircv3BatchTag.fromEvent(event);
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
