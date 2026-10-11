package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Finds a reaction for unreact using the session index, sync, and bounded history scans. Runs on
 * the caller's thread; the service owns scheduling and the session owns all retained state.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixReactionEventResolver {
  private static final int SYNC_TIMEOUT_MS = 0;
  private static final int UNREACT_LOOKUP_HISTORY_PAGE_LIMIT = 200;
  private static final int UNREACT_LOOKUP_MAX_PAGES = 10;

  @NonNull private final MatrixRoomHistoryClient roomHistoryClient;
  @NonNull private final MatrixSyncClient syncClient;
  @NonNull private final MatrixHistoryCursorCoordinator historyCursorCoordinator;

  @NonNull
  private final Function<MatrixSession, MatrixHistoryCursorCoordinator.SessionView>
      historySessionViews;

  String resolveForUnreact(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String targetEventId,
      String reaction,
      String sender) {
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (session == null
        || sid.isEmpty()
        || rid.isEmpty()
        || targetId.isEmpty()
        || key.isEmpty()
        || from.isEmpty()) {
      return "";
    }

    String reactionEventId = session.findReactionEventId(rid, targetId, key, from);
    if (!reactionEventId.isEmpty()) {
      return reactionEventId;
    }

    rememberReactionMutationsFromSync(sid, server, session, rid);
    reactionEventId = session.findReactionEventId(rid, targetId, key, from);
    if (!reactionEventId.isEmpty()) {
      return reactionEventId;
    }

    return findReactionEventIdFromHistory(sid, server, session, rid, targetId, key, from);
  }

  private void rememberReactionMutationsFromSync(
      String serverId, IrcProperties.Server server, MatrixSession session, String roomId) {
    if (session == null) return;
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    if (sid.isEmpty() || rid.isEmpty()) return;
    String since = normalize(session.sinceToken.get());
    MatrixSyncClient.SyncResult sync =
        syncClient.sync(sid, server, session.accessToken, since, SYNC_TIMEOUT_MS);
    if (sync == null || !sync.success()) {
      return;
    }
    rememberSyncReactionMutations(
        session, rid, sync.reactionEvents(), sync.redactionEvents(), new LinkedHashSet<>());
  }

  private String findReactionEventIdFromHistory(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String targetEventId,
      String reaction,
      String sender) {
    if (session == null) return "";
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (sid.isEmpty() || rid.isEmpty() || targetId.isEmpty() || key.isEmpty() || from.isEmpty()) {
      return "";
    }

    MatrixHistoryCursorCoordinator.SessionView historySession = historySessionViews.apply(session);
    String fromToken =
        historyCursorCoordinator.resolveHistoryFromToken(
            sid, server, historySession, rid, System.currentTimeMillis());
    if (fromToken.isEmpty()) {
      return "";
    }

    String reactionEventId =
        findReactionEventIdByScanningHistoryBackward(
            sid, server, session, rid, fromToken, targetId, key, from);
    if (!reactionEventId.isEmpty()) {
      return reactionEventId;
    }

    long targetTimestampMs = session.roomEventTimestampMs(rid, targetId);
    if (targetTimestampMs <= 0L) {
      try {
        Instant targetInstant =
            historyCursorCoordinator.resolveHistoryMessageIdInstant(
                sid, server, historySession, rid, targetId, "unreact");
        if (targetInstant != null) {
          targetTimestampMs = targetInstant.toEpochMilli();
        }
      } catch (RuntimeException ignored) {
        return "";
      }
    }
    if (targetTimestampMs <= 0L) {
      return "";
    }

    String cursorNearTarget;
    try {
      cursorNearTarget =
          historyCursorCoordinator.resolveForwardCursorForTimestamp(
              sid, server, historySession, rid, targetTimestampMs);
    } catch (RuntimeException ignored) {
      return "";
    }
    if (cursorNearTarget.isEmpty()) {
      return "";
    }

    return findReactionEventIdByScanningHistoryForward(
        sid, server, session, rid, cursorNearTarget, targetId, key, from);
  }

  private String findReactionEventIdByScanningHistoryBackward(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String fromToken,
      String targetEventId,
      String reaction,
      String sender) {
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String cursor = normalize(fromToken);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (session == null
        || sid.isEmpty()
        || rid.isEmpty()
        || cursor.isEmpty()
        || targetId.isEmpty()
        || key.isEmpty()
        || from.isEmpty()) {
      return "";
    }

    Set<String> redactedReactionIds = new LinkedHashSet<>();
    for (int page = 0; page < UNREACT_LOOKUP_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              sid, server, session.accessToken, rid, cursor, UNREACT_LOOKUP_HISTORY_PAGE_LIMIT);
      if (result == null || !result.success()) {
        return "";
      }
      session.rememberHistoryEvents(rid, result.events());
      rememberHistoryReactionMutations(
          session, rid, result.reactionEvents(), result.redactionEvents(), redactedReactionIds);
      String reactionEventId = session.findReactionEventId(rid, targetId, key, from);
      if (!reactionEventId.isEmpty()) {
        return reactionEventId;
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        return "";
      }
      cursor = nextToken;
    }
    return "";
  }

  private String findReactionEventIdByScanningHistoryForward(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String fromToken,
      String targetEventId,
      String reaction,
      String sender) {
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String cursor = normalize(fromToken);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (session == null
        || sid.isEmpty()
        || rid.isEmpty()
        || cursor.isEmpty()
        || targetId.isEmpty()
        || key.isEmpty()
        || from.isEmpty()) {
      return "";
    }

    Set<String> redactedReactionIds = new LinkedHashSet<>();
    for (int page = 0; page < UNREACT_LOOKUP_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesAfter(
              sid, server, session.accessToken, rid, cursor, UNREACT_LOOKUP_HISTORY_PAGE_LIMIT);
      if (result == null || !result.success()) {
        return "";
      }
      session.rememberHistoryEvents(rid, result.events());
      rememberHistoryReactionMutations(
          session, rid, result.reactionEvents(), result.redactionEvents(), redactedReactionIds);
      String reactionEventId = session.findReactionEventId(rid, targetId, key, from);
      if (!reactionEventId.isEmpty()) {
        return reactionEventId;
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        return "";
      }
      cursor = nextToken;
    }
    return "";
  }

  private static void rememberSyncReactionMutations(
      MatrixSession session,
      String roomId,
      List<MatrixSyncClient.RoomReactionEvent> reactionEvents,
      List<MatrixSyncClient.RoomRedactionEvent> redactionEvents,
      Set<String> redactedReactionIds) {
    if (session == null) return;
    String rid = normalize(roomId);
    if (rid.isEmpty()) return;
    Set<String> tombstones =
        redactedReactionIds == null ? new LinkedHashSet<>() : redactedReactionIds;

    for (MatrixSyncClient.RoomRedactionEvent redactionEvent :
        redactionEvents == null
            ? List.<MatrixSyncClient.RoomRedactionEvent>of()
            : redactionEvents) {
      if (redactionEvent == null) continue;
      if (!rid.equals(normalize(redactionEvent.roomId()))) continue;
      String redactsEventId = normalize(redactionEvent.redactsEventId());
      if (redactsEventId.isEmpty()) continue;
      tombstones.add(redactsEventId);
      session.consumeReactionEvent(rid, redactsEventId);
    }

    for (MatrixSyncClient.RoomReactionEvent reactionEvent :
        reactionEvents == null ? List.<MatrixSyncClient.RoomReactionEvent>of() : reactionEvents) {
      if (reactionEvent == null) continue;
      if (!rid.equals(normalize(reactionEvent.roomId()))) continue;
      String eventId = normalize(reactionEvent.eventId());
      String sender = normalize(reactionEvent.sender());
      String targetMessageId = normalize(reactionEvent.targetEventId());
      String reaction = normalize(reactionEvent.reaction());
      if (eventId.isEmpty()
          || sender.isEmpty()
          || targetMessageId.isEmpty()
          || reaction.isEmpty()
          || tombstones.contains(eventId)) {
        continue;
      }
      session.rememberReactionEvent(rid, eventId, targetMessageId, reaction, sender);
    }
  }

  private static void rememberHistoryReactionMutations(
      MatrixSession session,
      String roomId,
      List<MatrixRoomHistoryClient.RoomReactionEvent> reactionEvents,
      List<MatrixRoomHistoryClient.RoomRedactionEvent> redactionEvents,
      Set<String> redactedReactionIds) {
    if (session == null) return;
    String rid = normalize(roomId);
    if (rid.isEmpty()) return;
    Set<String> tombstones =
        redactedReactionIds == null ? new LinkedHashSet<>() : redactedReactionIds;

    for (MatrixRoomHistoryClient.RoomRedactionEvent redactionEvent :
        redactionEvents == null
            ? List.<MatrixRoomHistoryClient.RoomRedactionEvent>of()
            : redactionEvents) {
      if (redactionEvent == null) continue;
      String redactsEventId = normalize(redactionEvent.redactsEventId());
      if (redactsEventId.isEmpty()) continue;
      tombstones.add(redactsEventId);
      session.consumeReactionEvent(rid, redactsEventId);
    }

    for (MatrixRoomHistoryClient.RoomReactionEvent reactionEvent :
        reactionEvents == null
            ? List.<MatrixRoomHistoryClient.RoomReactionEvent>of()
            : reactionEvents) {
      if (reactionEvent == null) continue;
      String eventId = normalize(reactionEvent.eventId());
      String sender = normalize(reactionEvent.sender());
      String targetMessageId = normalize(reactionEvent.targetEventId());
      String reaction = normalize(reactionEvent.reaction());
      if (eventId.isEmpty()
          || sender.isEmpty()
          || targetMessageId.isEmpty()
          || reaction.isEmpty()
          || tombstones.contains(eventId)) {
        continue;
      }
      session.rememberReactionEvent(rid, eventId, targetMessageId, reaction, sender);
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
