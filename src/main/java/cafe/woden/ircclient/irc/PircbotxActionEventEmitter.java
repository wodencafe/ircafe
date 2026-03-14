package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
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
  private final PircbotxConnectionState conn;
  private final PircbotxRosterEmitter rosterEmitter;
  private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
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
    this.conn = Objects.requireNonNull(conn, "conn");
    this.rosterEmitter = Objects.requireNonNull(rosterEmitter, "rosterEmitter");
    this.chatHistoryBatches = Objects.requireNonNull(chatHistoryBatches, "chatHistoryBatches");
    this.emit = Objects.requireNonNull(emit, "emit");
    this.selfNickResolver = Objects.requireNonNull(selfNickResolver, "selfNickResolver");
    this.privateTargetFromEvent =
        Objects.requireNonNull(privateTargetFromEvent, "privateTargetFromEvent");
  }

  void onAction(ActionEvent event) {
    String botNick = selfNickResolver.apply(event != null ? event.getBot() : null);
    String pmDest = privateTargetFromEvent.apply(event);

    Optional<String> batchId = PircbotxIrcv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      Instant at = PircbotxEventMetadata.inboundAt(event);
      String from = (event.getUser() != null) ? event.getUser().getNick() : "";
      String action = PircbotxUtil.safeStr(() -> event.getAction(), "");
      boolean fromSelf =
          botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
      Map<String, String> tags = PircbotxEventMetadata.ircv3TagsFromEvent(event);
      String batchMsgId = PircbotxEventMetadata.ircv3MessageId(tags);
      if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
        String hinted = inferPrivateDestinationFromHints(from, "ACTION", action, batchMsgId);
        if (!hinted.isBlank()) {
          pmDest = hinted;
        }
      }

      String fallbackTarget;
      if (event.getChannel() != null) {
        fallbackTarget = event.getChannel().getName();
      } else {
        fallbackTarget =
            PircbotxPrivateMessageEmitter.derivePrivateConversationTarget(botNick, from, pmDest);
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
      String hinted = inferPrivateDestinationFromHints(from, "ACTION", action, messageId);
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

      if (maybeCaptureZncPlayback(
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

    String convTarget =
        PircbotxPrivateMessageEmitter.derivePrivateConversationTarget(botNick, from, pmDest);
    if (!"*playback".equalsIgnoreCase(from)
        && maybeCaptureZncPlayback(
            convTarget, at, ChatHistoryEntry.Kind.ACTION, from, action, messageId, ircv3Tags)) {
      return;
    }

    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.PrivateAction(at, from, action, messageId, ircv3Tags)));
  }

  private String inferPrivateDestinationFromHints(
      String from, String kind, String payload, String messageId) {
    String fromNick = Objects.toString(from, "").trim();
    String k = Objects.toString(kind, "").trim().toUpperCase(Locale.ROOT);
    String body = Objects.toString(payload, "").trim();
    if (fromNick.isBlank() || k.isBlank() || body.isBlank()) return "";
    return conn.findPrivateTargetHint(fromNick, k, body, messageId, System.currentTimeMillis());
  }

  private boolean maybeCaptureZncPlayback(
      String target,
      Instant at,
      ChatHistoryEntry.Kind kind,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    try {
      if (!conn.zncPlaybackCapture.shouldCapture(target, at)) return false;
      conn.zncPlaybackCapture.addEntry(
          new ChatHistoryEntry(
              at == null ? Instant.now() : at,
              kind == null ? ChatHistoryEntry.Kind.PRIVMSG : kind,
              target == null ? "" : target,
              from == null ? "" : from,
              text == null ? "" : text,
              messageId,
              ircv3Tags));
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
