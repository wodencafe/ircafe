package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class MatrixSyncTimelineEventProjector {
  private static final String TAG_IRCAFE_PM_TARGET = "ircafe/pm-target";
  private static final String TAG_MATRIX_MSGTYPE = "matrix.msgtype";
  private static final String TAG_MATRIX_MEDIA_URL = "matrix.media_url";
  private static final String TAG_MATRIX_ROOM_ID = "matrix.room_id";
  private static final String TAG_DRAFT_REPLY = "draft/reply";

  interface SessionView {
    String userId();

    String peerForRoom(String roomId);

    void rememberRoomEvent(String roomId, String eventId, long timestampMs);
  }

  private final Consumer<ServerIrcEvent> eventEmitter;

  MatrixSyncTimelineEventProjector(Consumer<ServerIrcEvent> eventEmitter) {
    this.eventEmitter = Objects.requireNonNull(eventEmitter, "eventEmitter");
  }

  void emitTimelineEvents(
      String serverId, SessionView session, List<MatrixSyncClient.RoomTimelineEvent> events) {
    if (events == null || events.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomTimelineEvent event : events) {
      if (event == null) continue;
      String roomId = normalize(event.roomId());
      String sender = normalize(event.sender());
      String body = Objects.toString(event.body(), "");
      if (roomId.isEmpty() || sender.isEmpty() || body.trim().isEmpty()) continue;

      String msgType = normalize(event.msgType());
      String replyToMessageId = normalize(event.replyToEventId());
      String messageId = normalize(event.eventId());
      String mediaUrl = normalize(event.mediaUrl());
      long ts = event.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      if (session != null) {
        session.rememberRoomEvent(roomId, messageId, ts);
      }
      String peerUserId = session == null ? "" : session.peerForRoom(roomId);
      boolean directMessageRoom = !peerUserId.isEmpty();
      boolean fromSelf = session != null && sender.equals(session.userId());

      if (directMessageRoom) {
        String normalizedType = msgType.isEmpty() ? "m.text" : msgType;
        if ("m.emote".equals(msgType)) {
          Map<String, String> tags =
              withTag(
                  withTag(
                      privateMessageTags(peerUserId, roomId, "m.emote", fromSelf),
                      TAG_MATRIX_MEDIA_URL,
                      mediaUrl),
                  TAG_DRAFT_REPLY,
                  replyToMessageId);
          emit(sid, new IrcEvent.PrivateAction(at, sender, body, messageId, tags));
          continue;
        }
        if ("m.notice".equals(msgType)) {
          Map<String, String> tags =
              withTag(
                  withTag(
                      privateMessageTags(peerUserId, roomId, "m.notice", fromSelf),
                      TAG_MATRIX_MEDIA_URL,
                      mediaUrl),
                  TAG_DRAFT_REPLY,
                  replyToMessageId);
          emit(sid, new IrcEvent.Notice(at, sender, peerUserId, body, messageId, tags));
          continue;
        }
        Map<String, String> tags =
            withTag(
                withTag(
                    privateMessageTags(peerUserId, roomId, normalizedType, fromSelf),
                    TAG_MATRIX_MEDIA_URL,
                    mediaUrl),
                TAG_DRAFT_REPLY,
                replyToMessageId);
        emit(sid, new IrcEvent.PrivateMessage(at, sender, body, messageId, tags));
        continue;
      }

      if ("m.emote".equals(msgType)) {
        Map<String, String> tags =
            withTag(
                withTag(Map.of(TAG_MATRIX_MSGTYPE, "m.emote"), TAG_MATRIX_MEDIA_URL, mediaUrl),
                TAG_DRAFT_REPLY,
                replyToMessageId);
        emit(sid, new IrcEvent.ChannelAction(at, roomId, sender, body, messageId, tags));
        continue;
      }

      if ("m.notice".equals(msgType)) {
        Map<String, String> tags =
            withTag(
                withTag(Map.of(TAG_MATRIX_MSGTYPE, "m.notice"), TAG_MATRIX_MEDIA_URL, mediaUrl),
                TAG_DRAFT_REPLY,
                replyToMessageId);
        emit(sid, new IrcEvent.Notice(at, sender, roomId, body, messageId, tags));
        continue;
      }

      String normalizedType = msgType.isEmpty() ? "m.text" : msgType;
      Map<String, String> tags =
          withTag(
              withTag(Map.of(TAG_MATRIX_MSGTYPE, normalizedType), TAG_MATRIX_MEDIA_URL, mediaUrl),
              TAG_DRAFT_REPLY,
              replyToMessageId);
      emit(sid, new IrcEvent.ChannelMessage(at, roomId, sender, body, messageId, tags));
    }
  }

  private void emit(String serverId, IrcEvent event) {
    eventEmitter.accept(new ServerIrcEvent(serverId, event));
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
