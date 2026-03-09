package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class MatrixSyncMutationEventProjector {
  private static final String TAG_IRCAFE_PM_TARGET = "ircafe/pm-target";
  private static final String TAG_MATRIX_MSGTYPE = "matrix.msgtype";
  private static final String TAG_MATRIX_ROOM_ID = "matrix.room_id";
  private static final String TAG_DRAFT_EDIT = "draft/edit";

  interface SessionView {
    String userId();

    String peerForRoom(String roomId);

    String targetForRoom(String roomId);

    void rememberRoomEvent(String roomId, String eventId, long timestampMs);

    void rememberReactionEvent(
        String roomId,
        String reactionEventId,
        String targetEventId,
        String reaction,
        String sender);

    ReactionIndexEntry consumeReactionEvent(String roomId, String reactionEventId);
  }

  record ReactionIndexEntry(String targetEventId, String reaction, String sender) {}

  private final Consumer<ServerIrcEvent> eventEmitter;

  MatrixSyncMutationEventProjector(Consumer<ServerIrcEvent> eventEmitter) {
    this.eventEmitter = Objects.requireNonNull(eventEmitter, "eventEmitter");
  }

  void emitMutationEvents(
      String serverId,
      SessionView session,
      List<MatrixSyncClient.RoomMessageEditEvent> messageEdits,
      List<MatrixSyncClient.RoomReactionEvent> reactions,
      List<MatrixSyncClient.RoomRedactionEvent> redactions) {
    emitEditEvents(serverId, session, messageEdits);
    emitReactionEvents(serverId, session, reactions);
    emitRedactionEvents(serverId, session, redactions);
  }

  private void emitEditEvents(
      String serverId, SessionView session, List<MatrixSyncClient.RoomMessageEditEvent> edits) {
    if (session == null || edits == null || edits.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomMessageEditEvent edit : edits) {
      if (edit == null) continue;
      String roomId = normalize(edit.roomId());
      String sender = normalize(edit.sender());
      String messageId = normalize(edit.eventId());
      String targetMessageId = normalize(edit.targetEventId());
      String body = Objects.toString(edit.body(), "");
      String msgType = normalize(edit.msgType());
      if (roomId.isEmpty()
          || sender.isEmpty()
          || targetMessageId.isEmpty()
          || body.trim().isEmpty()) {
        continue;
      }

      long ts = edit.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      session.rememberRoomEvent(roomId, messageId, ts);

      String normalizedType = msgType.isEmpty() ? "m.text" : msgType;
      String peerUserId = session.peerForRoom(roomId);
      boolean fromSelf = sender.equals(session.userId());
      if (!peerUserId.isEmpty()) {
        emit(
            sid,
            new IrcEvent.PrivateMessage(
                at,
                sender,
                body,
                messageId,
                withTag(
                    privateMessageTags(peerUserId, roomId, normalizedType, fromSelf),
                    TAG_DRAFT_EDIT,
                    targetMessageId)));
      } else {
        emit(
            sid,
            new IrcEvent.ChannelMessage(
                at,
                roomId,
                sender,
                body,
                messageId,
                withTag(
                    Map.of(TAG_MATRIX_MSGTYPE, normalizedType), TAG_DRAFT_EDIT, targetMessageId)));
      }
    }
  }

  private void emitReactionEvents(
      String serverId,
      SessionView session,
      List<MatrixSyncClient.RoomReactionEvent> reactionEvents) {
    if (session == null || reactionEvents == null || reactionEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomReactionEvent reactionEvent : reactionEvents) {
      if (reactionEvent == null) continue;
      String roomId = normalize(reactionEvent.roomId());
      String sender = normalize(reactionEvent.sender());
      String eventId = normalize(reactionEvent.eventId());
      String targetMessageId = normalize(reactionEvent.targetEventId());
      String reaction = normalize(reactionEvent.reaction());
      if (roomId.isEmpty() || sender.isEmpty() || targetMessageId.isEmpty() || reaction.isEmpty()) {
        continue;
      }

      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;

      long ts = reactionEvent.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      session.rememberReactionEvent(roomId, eventId, targetMessageId, reaction, sender);
      emit(sid, new IrcEvent.MessageReactObserved(at, sender, target, reaction, targetMessageId));
    }
  }

  private void emitRedactionEvents(
      String serverId,
      SessionView session,
      List<MatrixSyncClient.RoomRedactionEvent> redactionEvents) {
    if (session == null || redactionEvents == null || redactionEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomRedactionEvent redactionEvent : redactionEvents) {
      if (redactionEvent == null) continue;
      String roomId = normalize(redactionEvent.roomId());
      String sender = normalize(redactionEvent.sender());
      String redactsEventId = normalize(redactionEvent.redactsEventId());
      if (roomId.isEmpty() || redactsEventId.isEmpty()) continue;

      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;

      long ts = redactionEvent.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      ReactionIndexEntry removedReaction = session.consumeReactionEvent(roomId, redactsEventId);
      if (removedReaction != null) {
        String reactionSender = normalize(removedReaction.sender());
        String reaction = normalize(removedReaction.reaction());
        String targetMessageId = normalize(removedReaction.targetEventId());
        if (!reactionSender.isEmpty() && !reaction.isEmpty() && !targetMessageId.isEmpty()) {
          emit(
              sid,
              new IrcEvent.MessageUnreactObserved(
                  at, reactionSender, target, reaction, targetMessageId));
          continue;
        }
      }

      emit(sid, new IrcEvent.MessageRedactionObserved(at, sender, target, redactsEventId));
    }
  }

  private void emit(String serverId, IrcEvent event) {
    eventEmitter.accept(new ServerIrcEvent(serverId, event));
  }

  private static String signalTargetForRoom(SessionView session, String roomId) {
    String rid = normalize(roomId);
    if (rid.isEmpty()) return "";
    if (session == null) return rid;
    String target = normalize(session.targetForRoom(rid));
    return target.isEmpty() ? rid : target;
  }

  private static Map<String, String> privateMessageTags(
      String peerUserId, String roomId, String msgType, boolean includePrivateTargetTag) {
    String peer = normalize(peerUserId);
    String rid = normalize(roomId);
    String type = normalize(msgType);
    if (type.isEmpty()) {
      type = "m.text";
    }

    if (includePrivateTargetTag && !peer.isEmpty()) {
      return Map.of(TAG_IRCAFE_PM_TARGET, peer, TAG_MATRIX_ROOM_ID, rid, TAG_MATRIX_MSGTYPE, type);
    }
    return Map.of(TAG_MATRIX_ROOM_ID, rid, TAG_MATRIX_MSGTYPE, type);
  }

  private static Map<String, String> withTag(Map<String, String> base, String key, String value) {
    String tagKey = normalize(key);
    String tagValue = normalize(value);
    if (tagKey.isEmpty() || tagValue.isEmpty()) {
      return base == null ? Map.of() : base;
    }
    LinkedHashMap<String, String> tags = new LinkedHashMap<>();
    if (base != null && !base.isEmpty()) {
      tags.putAll(base);
    }
    tags.put(tagKey, tagValue);
    return Map.copyOf(tags);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
