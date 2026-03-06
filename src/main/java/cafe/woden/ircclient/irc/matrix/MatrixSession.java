package cafe.woden.ircclient.irc.matrix;

import io.reactivex.rxjava3.disposables.Disposable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class MatrixSession {
  private static final int ROOM_MESSAGE_INDEX_CAPACITY = 1_000;
  private static final int ROOM_REACTION_INDEX_CAPACITY = 1_000;

  final String userId;
  final String accessToken;
  final AtomicBoolean closed = new AtomicBoolean(false);
  final AtomicReference<Disposable> syncTask = new AtomicReference<>();
  final AtomicReference<String> sinceToken = new AtomicReference<>("");
  private final Map<String, String> directRoomByPeer = new ConcurrentHashMap<>();
  private final Map<String, String> directPeerByRoom = new ConcurrentHashMap<>();
  private final Map<String, String> joinedRoomByAlias = new ConcurrentHashMap<>();
  private final Map<String, HistoryCursor> historyCursorByRoom = new ConcurrentHashMap<>();
  private final Map<String, String> latestEventByRoom = new ConcurrentHashMap<>();
  private final Map<String, LinkedHashMap<String, Long>> messageTimestampByIdByRoom =
      new ConcurrentHashMap<>();
  private final Map<String, LinkedHashMap<String, ReactionIndexEntry>> reactionByEventIdByRoom =
      new ConcurrentHashMap<>();
  private final Map<String, Set<String>> typingUsersByRoom = new ConcurrentHashMap<>();
  private final Map<String, Long> readMarkerTsByRoom = new ConcurrentHashMap<>();

  MatrixSession(String userId, String accessToken) {
    this.userId = normalize(userId);
    this.accessToken = normalize(accessToken);
  }

  void rememberDirectRoom(String peerUserId, String roomId) {
    String peer = normalize(peerUserId);
    String rid = normalize(roomId);
    if (peer.isEmpty() || rid.isEmpty()) return;
    directRoomByPeer.put(peer, rid);
    directPeerByRoom.put(rid, peer);
  }

  void rememberDirectRooms(Map<String, String> directPeerByRoom) {
    if (directPeerByRoom == null || directPeerByRoom.isEmpty()) return;
    for (Map.Entry<String, String> entry : directPeerByRoom.entrySet()) {
      if (entry == null) continue;
      rememberDirectRoom(entry.getValue(), entry.getKey());
    }
  }

  String roomForPeer(String peerUserId) {
    return normalize(directRoomByPeer.get(normalize(peerUserId)));
  }

  String peerForRoom(String roomId) {
    return normalize(directPeerByRoom.get(normalize(roomId)));
  }

  void rememberJoinedAlias(String roomAlias, String roomId) {
    String alias = normalize(roomAlias);
    String rid = normalize(roomId);
    if (alias.isEmpty() || rid.isEmpty()) return;
    joinedRoomByAlias.put(alias, rid);
  }

  String roomForAlias(String roomAlias) {
    return normalize(joinedRoomByAlias.get(normalize(roomAlias)));
  }

  void forgetJoinedRoom(String roomId) {
    String rid = normalize(roomId);
    if (rid.isEmpty()) return;
    joinedRoomByAlias.entrySet().removeIf(entry -> rid.equals(normalize(entry.getValue())));
    historyCursorByRoom.remove(rid);
    latestEventByRoom.remove(rid);
    messageTimestampByIdByRoom.remove(rid);
    reactionByEventIdByRoom.remove(rid);
    typingUsersByRoom.remove(rid);
    readMarkerTsByRoom.remove(rid);
  }

  void rememberHistoryCursor(String roomId, String nextToken, long beforeEpochMs) {
    String rid = normalize(roomId);
    String token = normalize(nextToken);
    if (rid.isEmpty() || token.isEmpty()) return;
    historyCursorByRoom.put(rid, new HistoryCursor(token, beforeEpochMs));
  }

  HistoryCursor historyCursor(String roomId) {
    return historyCursorByRoom.get(normalize(roomId));
  }

  void rememberLatestRoomEvent(String roomId, String eventId) {
    rememberRoomEvent(roomId, eventId, System.currentTimeMillis());
  }

  void rememberRoomEvent(String roomId, String eventId, long timestampMs) {
    String rid = normalize(roomId);
    String eid = normalize(eventId);
    if (rid.isEmpty() || eid.isEmpty()) return;
    long ts = timestampMs > 0L ? timestampMs : System.currentTimeMillis();
    latestEventByRoom.put(rid, eid);
    LinkedHashMap<String, Long> index =
        messageTimestampByIdByRoom.computeIfAbsent(rid, ignored -> new LinkedHashMap<>());
    synchronized (index) {
      index.put(eid, ts);
      while (index.size() > ROOM_MESSAGE_INDEX_CAPACITY) {
        String eldest = index.entrySet().iterator().next().getKey();
        index.remove(eldest);
      }
    }
  }

  void rememberReactionEvent(
      String roomId,
      String reactionEventId,
      String targetEventId,
      String reaction,
      String sender) {
    String rid = normalize(roomId);
    String reactionId = normalize(reactionEventId);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (rid.isEmpty()
        || reactionId.isEmpty()
        || targetId.isEmpty()
        || key.isEmpty()
        || from.isEmpty()) {
      return;
    }
    LinkedHashMap<String, ReactionIndexEntry> index =
        reactionByEventIdByRoom.computeIfAbsent(rid, ignored -> new LinkedHashMap<>());
    synchronized (index) {
      index.put(reactionId, new ReactionIndexEntry(targetId, key, from));
      while (index.size() > ROOM_REACTION_INDEX_CAPACITY) {
        String eldest = index.entrySet().iterator().next().getKey();
        index.remove(eldest);
      }
    }
  }

  ReactionIndexEntry consumeReactionEvent(String roomId, String reactionEventId) {
    String rid = normalize(roomId);
    String reactionId = normalize(reactionEventId);
    if (rid.isEmpty() || reactionId.isEmpty()) return null;
    LinkedHashMap<String, ReactionIndexEntry> index = reactionByEventIdByRoom.get(rid);
    if (index == null) return null;
    synchronized (index) {
      return index.remove(reactionId);
    }
  }

  String findReactionEventId(String roomId, String targetEventId, String reaction, String sender) {
    String rid = normalize(roomId);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (rid.isEmpty() || targetId.isEmpty() || key.isEmpty() || from.isEmpty()) {
      return "";
    }
    LinkedHashMap<String, ReactionIndexEntry> index = reactionByEventIdByRoom.get(rid);
    if (index == null || index.isEmpty()) return "";
    synchronized (index) {
      String found = "";
      for (Map.Entry<String, ReactionIndexEntry> entry : index.entrySet()) {
        if (entry == null || entry.getValue() == null) continue;
        ReactionIndexEntry value = entry.getValue();
        if (!targetId.equals(normalize(value.targetEventId()))) continue;
        if (!key.equals(normalize(value.reaction()))) continue;
        if (!from.equals(normalize(value.sender()))) continue;
        found = normalize(entry.getKey());
      }
      return found;
    }
  }

  void rememberHistoryEvents(String roomId, List<MatrixRoomHistoryClient.RoomHistoryEvent> events) {
    if (events == null || events.isEmpty()) return;
    String rid = normalize(roomId);
    if (rid.isEmpty()) return;
    for (MatrixRoomHistoryClient.RoomHistoryEvent event : events) {
      if (event == null) continue;
      rememberRoomEvent(rid, event.eventId(), event.originServerTs());
    }
  }

  String latestRoomEventId(String roomId) {
    return normalize(latestEventByRoom.get(normalize(roomId)));
  }

  long roomEventTimestampMs(String roomId, String eventId) {
    String rid = normalize(roomId);
    String eid = normalize(eventId);
    if (rid.isEmpty() || eid.isEmpty()) return 0L;
    LinkedHashMap<String, Long> index = messageTimestampByIdByRoom.get(rid);
    if (index == null) return 0L;
    synchronized (index) {
      Long ts = index.get(eid);
      return ts == null ? 0L : ts.longValue();
    }
  }

  Set<String> replaceTypingUsers(String roomId, Set<String> users) {
    String rid = normalize(roomId);
    if (rid.isEmpty()) {
      return Set.of();
    }
    Set<String> next = (users == null || users.isEmpty()) ? Set.of() : Set.copyOf(users);
    Set<String> previous = typingUsersByRoom.get(rid);
    if (next.isEmpty()) {
      typingUsersByRoom.remove(rid);
    } else {
      typingUsersByRoom.put(rid, next);
    }
    if (previous == null || previous.isEmpty()) {
      return Set.of();
    }
    return previous;
  }

  boolean shouldEmitReadMarker(String roomId, long markerTsMs) {
    String rid = normalize(roomId);
    if (rid.isEmpty() || markerTsMs <= 0L) {
      return false;
    }
    Long prior = readMarkerTsByRoom.get(rid);
    if (prior != null && markerTsMs <= prior.longValue()) {
      return false;
    }
    readMarkerTsByRoom.put(rid, markerTsMs);
    return true;
  }

  record ReactionIndexEntry(String targetEventId, String reaction, String sender) {}

  record HistoryCursor(String nextToken, long beforeEpochMs) {}

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
