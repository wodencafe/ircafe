package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

final class MatrixSyncSignalEventProjector {
  interface SessionView {
    String userId();

    void forgetJoinedRoom(String roomId);

    String peerForRoom(String roomId);

    Set<String> replaceTypingUsers(String roomId, Set<String> users);

    boolean shouldEmitReadMarker(String roomId, long markerTsMs);
  }

  private final Consumer<ServerIrcEvent> eventEmitter;

  MatrixSyncSignalEventProjector(Consumer<ServerIrcEvent> eventEmitter) {
    this.eventEmitter = Objects.requireNonNull(eventEmitter, "eventEmitter");
  }

  void emitMembershipEvents(
      String serverId,
      SessionView session,
      List<MatrixSyncClient.RoomMembershipEvent> membershipEvents) {
    if (session == null || membershipEvents == null || membershipEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;
    String selfUserId = normalize(session.userId());

    for (MatrixSyncClient.RoomMembershipEvent membershipEvent : membershipEvents) {
      if (membershipEvent == null) continue;
      String roomId = normalize(membershipEvent.roomId());
      String userId = normalize(membershipEvent.userId());
      if (roomId.isEmpty() || !looksLikeMatrixUserId(userId)) continue;

      String membership = normalize(membershipEvent.membership()).toLowerCase(Locale.ROOT);
      String prevMembership = normalize(membershipEvent.prevMembership()).toLowerCase(Locale.ROOT);
      String displayName = normalize(membershipEvent.displayName());
      String prevDisplayName = normalize(membershipEvent.prevDisplayName());
      String reason = normalize(membershipEvent.reason());
      long ts = membershipEvent.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();

      boolean joinedNow = "join".equals(membership);
      boolean joinedBefore = "join".equals(prevMembership);

      if (!userId.equals(selfUserId) && joinedNow && !joinedBefore) {
        emit(sid, new IrcEvent.UserJoinedChannel(at, roomId, userId));
      } else if (joinedBefore && !joinedNow) {
        if (userId.equals(selfUserId)) {
          session.forgetJoinedRoom(roomId);
          emit(sid, new IrcEvent.LeftChannel(at, roomId, reason));
        } else {
          emit(sid, new IrcEvent.UserPartedChannel(at, roomId, userId, reason));
        }
      }

      if (!displayName.isEmpty() && !displayName.equals(prevDisplayName)) {
        emit(
            sid,
            new IrcEvent.UserSetNameObserved(
                at, userId, displayName, IrcEvent.UserSetNameObserved.Source.SETNAME));
      }
    }
  }

  void emitSignalEvents(
      String serverId,
      SessionView session,
      List<MatrixSyncClient.TypingEvent> typingEvents,
      List<MatrixSyncClient.ReadReceiptEvent> readReceipts) {
    emitTypingEvents(serverId, session, typingEvents);
    emitReadMarkerEvents(serverId, session, readReceipts);
  }

  private void emitTypingEvents(
      String serverId, SessionView session, List<MatrixSyncClient.TypingEvent> typingEvents) {
    if (session == null || typingEvents == null || typingEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.TypingEvent typingEvent : typingEvents) {
      if (typingEvent == null) continue;
      String roomId = normalize(typingEvent.roomId());
      if (roomId.isEmpty()) continue;
      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;

      Set<String> currentUsers = normalizeTypingUsers(typingEvent.userIds(), session.userId());
      Set<String> previousUsers = session.replaceTypingUsers(roomId, currentUsers);

      Instant now = Instant.now();
      for (String userId : currentUsers) {
        if (previousUsers.contains(userId)) continue;
        emit(sid, new IrcEvent.UserTypingObserved(now, userId, target, "active"));
      }
      for (String userId : previousUsers) {
        if (currentUsers.contains(userId)) continue;
        emit(sid, new IrcEvent.UserTypingObserved(now, userId, target, "done"));
      }
    }
  }

  private void emitReadMarkerEvents(
      String serverId, SessionView session, List<MatrixSyncClient.ReadReceiptEvent> readReceipts) {
    if (session == null || readReceipts == null || readReceipts.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;
    String selfUserId = normalize(session.userId());
    if (selfUserId.isEmpty()) return;

    for (MatrixSyncClient.ReadReceiptEvent receipt : readReceipts) {
      if (receipt == null) continue;
      String roomId = normalize(receipt.roomId());
      String fromUserId = normalize(receipt.userId());
      long ts = receipt.timestampMs();
      if (roomId.isEmpty() || fromUserId.isEmpty() || ts <= 0L) continue;
      if (!selfUserId.equals(fromUserId)) continue;
      if (!session.shouldEmitReadMarker(roomId, ts)) continue;

      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;
      Instant markerAt = Instant.ofEpochMilli(ts);
      String marker = "timestamp=" + markerAt;
      emit(sid, new IrcEvent.ReadMarkerObserved(markerAt, fromUserId, target, marker));
    }
  }

  private void emit(String serverId, IrcEvent event) {
    eventEmitter.accept(new ServerIrcEvent(serverId, event));
  }

  private static Set<String> normalizeTypingUsers(List<String> rawUsers, String selfUserId) {
    if (rawUsers == null || rawUsers.isEmpty()) {
      return Set.of();
    }
    String self = normalize(selfUserId);
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String raw : rawUsers) {
      String userId = normalize(raw);
      if (!looksLikeMatrixUserId(userId)) continue;
      if (!self.isEmpty() && self.equals(userId)) continue;
      normalized.add(userId);
    }
    if (normalized.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(normalized);
  }

  private static String signalTargetForRoom(SessionView session, String roomId) {
    String rid = normalize(roomId);
    if (rid.isEmpty()) return "";
    String peer = session == null ? "" : session.peerForRoom(rid);
    if (!peer.isEmpty()) {
      return peer;
    }
    return rid;
  }

  private static boolean looksLikeMatrixUserId(String token) {
    String value = normalize(token);
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
