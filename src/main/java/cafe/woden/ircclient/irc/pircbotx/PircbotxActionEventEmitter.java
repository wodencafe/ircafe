package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventMetadata;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.ActionEvent;

/** Emits structured action events for a single IRC connection. */
final class PircbotxActionEventEmitter {
  private static final String TAG_IRCAFE_PM_TARGET = "ircafe/pm-target";

  private final String serverId;

  private final PircbotxRosterEmitter rosterEmitter;
  private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  private final PircbotxPlaybackCaptureRecorder playbackCaptureRecorder;
  private final PircbotxPrivateConversationSupport privateConversationSupport;
  private final Consumer<ServerIrcEvent> emit;
  private final Function<PircBotX, String> selfNickResolver;
  private final Function<Object, String> privateTargetFromEvent;

  PircbotxActionEventEmitter(
      String serverId,
      PircbotxConnectionState conn,
      PircbotxRosterEmitter rosterEmitter,
      PircbotxChatHistoryBatchCollector chatHistoryBatches,
      Consumer<ServerIrcEvent> emit,
      Function<PircBotX, String> selfNickResolver,
      Function<Object, String> privateTargetFromEvent) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");

    this.rosterEmitter = Objects.requireNonNull(rosterEmitter, "rosterEmitter");
    this.chatHistoryBatches = Objects.requireNonNull(chatHistoryBatches, "chatHistoryBatches");
    this.playbackCaptureRecorder = new PircbotxPlaybackCaptureRecorder(conn);
    this.privateConversationSupport = new PircbotxPrivateConversationSupport(conn);
    this.emit = Objects.requireNonNull(emit, "emit");
    this.selfNickResolver = Objects.requireNonNull(selfNickResolver, "selfNickResolver");
    this.privateTargetFromEvent =
        Objects.requireNonNull(privateTargetFromEvent, "privateTargetFromEvent");
  }

  void onAction(ActionEvent event) {
    String botNick = selfNickResolver.apply(event != null ? event.getBot() : null);
    String pmDest = privateTargetFromEvent.apply(event);

    Optional<String> batchId = Ircv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      Instant at = PircbotxEventMetadata.inboundAt(event);
      String from = (event.getUser() != null) ? event.getUser().getNick() : "";
      String action = PircbotxUtil.safeStr(() -> event.getAction(), "");
      boolean fromSelf =
          botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
      Map<String, String> tags = PircbotxEventMetadata.ircv3TagsFromEvent(event);
      String batchMsgId = PircbotxEventMetadata.ircv3MessageId(tags);
      if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
        String hinted =
            privateConversationSupport.inferPrivateDestinationFromHints(
                from, "ACTION", action, batchMsgId);
        if (!hinted.isBlank()) {
          pmDest = hinted;
        }
      }

      String fallbackTarget;
      if (event.getChannel() != null) {
        fallbackTarget = event.getChannel().getName();
      } else {
        fallbackTarget = privateConversationSupport.deriveConversationTarget(botNick, from, pmDest);
      }

      if (chatHistoryBatches.appendIfActive(
          batchId.get(),
          ChatHistoryEntry.Kind.ACTION,
          at,
          fallbackTarget,
          from,
          action,
          batchMsgId,
          tags)) {
        return;
      }
    }

    Instant at = PircbotxEventMetadata.inboundAt(event);
    String from = (event.getUser() != null) ? event.getUser().getNick() : "";
    String action = PircbotxUtil.safeStr(() -> event.getAction(), "");
    boolean fromSelf =
        botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
    Map<String, String> tags = new HashMap<>(PircbotxEventMetadata.ircv3TagsFromEvent(event));
    String messageId = PircbotxEventMetadata.ircv3MessageId(tags);
    if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
      String hinted =
          privateConversationSupport.inferPrivateDestinationFromHints(
              from, "ACTION", action, messageId);
      if (!hinted.isBlank()) {
        pmDest = hinted;
      }
    }
    if (pmDest != null && !pmDest.isBlank()) {
      tags.put(TAG_IRCAFE_PM_TARGET, pmDest);
    }
    PircbotxEventMetadata.withObservedHostmaskTag(tags, event.getUser());
    Map<String, String> ircv3Tags = tags;
    messageId = PircbotxEventMetadata.ircv3MessageId(ircv3Tags);

    if (event.getChannel() != null) {
      String channel = event.getChannel().getName();
      rosterEmitter.maybeEmitHostmaskObserved(channel, event.getUser());

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

    rosterEmitter.maybeEmitHostmaskObserved("", event.getUser());

    String convTarget = privateConversationSupport.deriveConversationTarget(botNick, from, pmDest);
    if (!"*playback".equalsIgnoreCase(from)
        && playbackCaptureRecorder.maybeCapture(
            convTarget, at, ChatHistoryEntry.Kind.ACTION, from, action, messageId, ircv3Tags)) {
      return;
    }

    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.PrivateAction(at, from, action, messageId, ircv3Tags)));
  }
}
