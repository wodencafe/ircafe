package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixLocalEchoEmitter {
  private static final String CTCP_ACTION_PREFIX = "\u0001ACTION ";
  private static final String CTCP_SUFFIX = "\u0001";
  private static final String TAG_IRCAFE_PM_TARGET = "ircafe/pm-target";
  private static final String TAG_MATRIX_MSGTYPE = "matrix.msgtype";
  private static final String TAG_MATRIX_MEDIA_URL = "matrix.media_url";
  private static final String TAG_MATRIX_ROOM_ID = "matrix.room_id";

  interface SessionView {
    String userId();

    void rememberLatestRoomEvent(String roomId, String eventId);

    String targetForRoom(String roomId);
  }

  @NonNull private final Consumer<ServerIrcEvent> eventEmitter;

  void emitChannelMessage(
      String serverId, SessionView session, String roomId, String text, String eventId) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId());
    String rid = normalize(roomId);
    if (sid.isEmpty() || sender.isEmpty() || rid.isEmpty()) return;
    String target = normalize(session.targetForRoom(rid));
    if (target.isEmpty()) target = rid;

    String raw = Objects.toString(text, "");
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    if (isCtcpAction(raw)) {
      String action = extractCtcpAction(raw);
      emit(
          sid,
          new IrcEvent.ChannelAction(
              now, target, sender, action, mid, Map.of(TAG_MATRIX_MSGTYPE, "m.emote")));
      return;
    }

    emit(
        sid,
        new IrcEvent.ChannelMessage(
            now, target, sender, raw, mid, Map.of(TAG_MATRIX_MSGTYPE, "m.text")));
  }

  void emitPrivateMessage(
      String serverId,
      SessionView session,
      String peerUserId,
      String roomId,
      String text,
      String eventId) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId());
    String peer = normalize(peerUserId);
    String rid = normalize(roomId);
    if (sid.isEmpty() || sender.isEmpty() || peer.isEmpty() || rid.isEmpty()) return;

    String raw = Objects.toString(text, "");
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    if (isCtcpAction(raw)) {
      String action = extractCtcpAction(raw);
      emit(
          sid,
          new IrcEvent.PrivateAction(
              now, sender, action, mid, privateMessageTags(peer, rid, "m.emote")));
      return;
    }

    emit(
        sid,
        new IrcEvent.PrivateMessage(
            now, sender, raw, mid, privateMessageTags(peer, rid, "m.text")));
  }

  void emitChannelMediaMessage(
      String serverId,
      SessionView session,
      String roomId,
      String text,
      String eventId,
      String msgType,
      String mediaUrl) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId());
    String rid = normalize(roomId);
    String type = normalize(msgType);
    String mediaRef = normalize(mediaUrl);
    if (sid.isEmpty() || sender.isEmpty() || rid.isEmpty() || type.isEmpty()) return;
    String target = normalize(session.targetForRoom(rid));
    if (target.isEmpty()) target = rid;

    String raw = Objects.toString(text, "");
    String rendered = raw.trim().isEmpty() ? mediaRef : raw;
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    Map<String, String> tags =
        withTag(Map.of(TAG_MATRIX_MSGTYPE, type), TAG_MATRIX_MEDIA_URL, mediaRef);
    emit(sid, new IrcEvent.ChannelMessage(now, target, sender, rendered, mid, tags));
  }

  void emitPrivateMediaMessage(
      String serverId,
      SessionView session,
      String peerUserId,
      String roomId,
      String text,
      String eventId,
      String msgType,
      String mediaUrl) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId());
    String peer = normalize(peerUserId);
    String rid = normalize(roomId);
    String type = normalize(msgType);
    String mediaRef = normalize(mediaUrl);
    if (sid.isEmpty() || sender.isEmpty() || peer.isEmpty() || rid.isEmpty() || type.isEmpty())
      return;

    String raw = Objects.toString(text, "");
    String rendered = raw.trim().isEmpty() ? mediaRef : raw;
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    Map<String, String> tags =
        withTag(privateMessageTags(peer, rid, type), TAG_MATRIX_MEDIA_URL, mediaRef);
    emit(sid, new IrcEvent.PrivateMessage(now, sender, rendered, mid, tags));
  }

  void emitNotice(
      String serverId,
      SessionView session,
      String target,
      String text,
      String eventId,
      Map<String, String> tags) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId());
    String noticeTarget = normalize(target);
    if (sid.isEmpty() || sender.isEmpty() || noticeTarget.isEmpty()) return;

    String raw = Objects.toString(text, "");
    String mid = normalize(eventId);
    Map<String, String> safeTags = tags == null ? Map.of() : tags;
    String roomId = normalize(safeTags.get(TAG_MATRIX_ROOM_ID));
    if (roomId.isEmpty() && looksLikeMatrixRoomId(noticeTarget)) {
      roomId = noticeTarget;
    }
    session.rememberLatestRoomEvent(roomId, mid);
    if (looksLikeMatrixRoomId(noticeTarget)) {
      String preferred = normalize(session.targetForRoom(noticeTarget));
      if (!preferred.isEmpty()) {
        noticeTarget = preferred;
      }
    }

    emit(sid, new IrcEvent.Notice(Instant.now(), sender, noticeTarget, raw, mid, safeTags));
  }

  private void emit(String serverId, IrcEvent event) {
    eventEmitter.accept(new ServerIrcEvent(serverId, event));
  }

  private static boolean isCtcpAction(String text) {
    String raw = Objects.toString(text, "");
    return raw.startsWith(CTCP_ACTION_PREFIX)
        && raw.endsWith(CTCP_SUFFIX)
        && raw.length() > (CTCP_ACTION_PREFIX.length() + CTCP_SUFFIX.length());
  }

  private static String extractCtcpAction(String text) {
    String raw = Objects.toString(text, "");
    if (!isCtcpAction(raw)) return raw;
    String action = raw.substring(CTCP_ACTION_PREFIX.length(), raw.length() - 1).trim();
    return action.isEmpty() ? raw : action;
  }

  private static Map<String, String> privateMessageTags(
      String peerUserId, String roomId, String msgType) {
    return privateMessageTags(peerUserId, roomId, msgType, true);
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
    LinkedHashMap<String, String> nextTags = new LinkedHashMap<>();
    if (base != null && !base.isEmpty()) {
      nextTags.putAll(base);
    }
    nextTags.put(tagKey, tagValue);
    return Map.copyOf(nextTags);
  }

  private static boolean looksLikeMatrixRoomId(String token) {
    String value = normalize(token);
    if (!value.startsWith("!")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
