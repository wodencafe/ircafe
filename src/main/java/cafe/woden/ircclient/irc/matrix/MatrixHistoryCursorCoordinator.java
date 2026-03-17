package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixHistoryCursorCoordinator {
  private static final String HISTORY_SELECTOR_TIMESTAMP_PREFIX = "timestamp=";
  private static final String HISTORY_SELECTOR_MSGID_PREFIX = "msgid=";
  private static final String TAG_MATRIX_MSGTYPE = "matrix.msgtype";
  private static final String TAG_MATRIX_MEDIA_URL = "matrix.media_url";
  private static final String TAG_MATRIX_ROOM_ID = "matrix.room_id";
  private static final String TAG_DRAFT_REPLY = "draft/reply";
  private static final int MSGID_LOOKUP_SCAN_PAGE_LIMIT = 200;
  private static final int MSGID_LOOKUP_MAX_PAGES = 10;
  private static final int TIMESTAMP_CURSOR_SCAN_PAGE_LIMIT = 200;
  private static final int TIMESTAMP_CURSOR_MAX_PAGES = 20;
  private static final int SYNC_TIMEOUT_MS = 0;

  interface SessionView {
    String accessToken();

    String sinceToken();

    void setSinceToken(String nextToken);

    void rememberDirectRooms(Map<String, String> directPeerByRoom);

    MatrixSession.HistoryCursor historyCursor(String roomId);

    void rememberHistoryCursor(String roomId, String nextToken, long beforeEpochMs);

    void rememberHistoryEvents(
        String roomId, List<MatrixRoomHistoryClient.RoomHistoryEvent> events);

    long roomEventTimestampMs(String roomId, String eventId);
  }

  @NonNull private final MatrixRoomHistoryClient roomHistoryClient;
  @NonNull private final MatrixSyncClient syncClient;

  int normalizeHistoryLimit(int limit) {
    int normalized = limit <= 0 ? 50 : limit;
    return Math.max(1, Math.min(normalized, 200));
  }

  Instant resolveHistorySelectorInstant(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String roomId,
      String selector,
      String operation,
      String selectorName) {
    String token = normalize(selector);
    if (token.isEmpty() || "*".equals(token)) {
      return null;
    }
    Instant ts = parseHistoryTimestampSelector(token);
    if (ts != null) {
      return ts;
    }
    String messageId = parseHistoryMessageIdSelector(token);
    if (!messageId.isEmpty()) {
      return resolveHistoryMessageIdInstant(
          serverId, server, session, roomId, messageId, operation);
    }
    throw new IllegalArgumentException(selectorName + " must be '*', msgid=..., or timestamp=...");
  }

  Instant resolveHistoryMessageIdInstant(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String roomId,
      String messageId,
      String operation) {
    long timestampMs =
        session == null ? 0L : session.roomEventTimestampMs(roomId, normalize(messageId));
    if (timestampMs <= 0L) {
      timestampMs =
          lookupMessageTimestampByScanningHistory(
              serverId, server, session, roomId, messageId, operation);
    }
    if (timestampMs <= 0L) {
      throw new BackendNotAvailableException(
          IrcProperties.Server.Backend.MATRIX,
          operation,
          normalize(serverId),
          "Matrix backend cannot resolve msgid selector in this session");
    }
    return Instant.ofEpochMilli(timestampMs);
  }

  String resolveHistoryFromToken(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String roomId,
      long beforeEpochMs) {
    MatrixSession.HistoryCursor cursor = session == null ? null : session.historyCursor(roomId);
    if (cursor != null && !cursor.nextToken().isEmpty() && beforeEpochMs < cursor.beforeEpochMs()) {
      return cursor.nextToken();
    }

    String anchor = session == null ? "" : normalize(session.sinceToken());
    if (!anchor.isEmpty()) {
      return anchor;
    }
    if (session == null) {
      return "";
    }

    MatrixSyncClient.SyncResult sync =
        syncClient.sync(serverId, server, session.accessToken(), "", SYNC_TIMEOUT_MS);
    if (sync != null && sync.success()) {
      String nextBatch = normalize(sync.nextBatch());
      if (!nextBatch.isEmpty()) {
        session.setSinceToken(nextBatch);
        anchor = nextBatch;
      }
      session.rememberDirectRooms(sync.directPeerByRoom());
    }
    return anchor;
  }

  List<ChatHistoryEntry> fetchHistoryEntriesBefore(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String requestedTarget,
      String roomId,
      long beforeEpochMs,
      int requestedLimit) {
    if (session == null) {
      throw new IllegalStateException("Matrix history session is unavailable");
    }
    String fromToken = resolveHistoryFromToken(serverId, server, session, roomId, beforeEpochMs);
    if (fromToken.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }

    MatrixRoomHistoryClient.HistoryResult result =
        roomHistoryClient.fetchMessagesBefore(
            serverId, server, session.accessToken(), roomId, fromToken, requestedLimit);
    if (result == null) {
      throw new IllegalStateException("Matrix history fetch returned no result");
    }
    if (!result.success()) {
      throw new IllegalStateException(
          "Matrix history fetch failed at " + result.endpoint() + ": " + result.detail());
    }

    String nextToken = normalize(result.endToken());
    if (nextToken.isEmpty()) {
      nextToken = fromToken;
    }
    session.rememberHistoryCursor(roomId, nextToken, beforeEpochMs);
    session.rememberHistoryEvents(roomId, result.events());
    return toHistoryEntries(requestedTarget, roomId, result.events());
  }

  String resolveForwardCursorForTimestamp(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String roomId,
      long targetEpochMs) {
    if (session == null) {
      throw new IllegalStateException("Matrix history session is unavailable");
    }
    long targetMs = targetEpochMs <= 0L ? System.currentTimeMillis() : targetEpochMs;
    String cursor = resolveHistoryFromToken(serverId, server, session, roomId, targetMs);
    if (cursor.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }

    String scanCursor = cursor;
    for (int page = 0; page < TIMESTAMP_CURSOR_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              serverId,
              server,
              session.accessToken(),
              roomId,
              scanCursor,
              TIMESTAMP_CURSOR_SCAN_PAGE_LIMIT);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new IllegalStateException(
            "Matrix history timestamp scan failed at "
                + result.endpoint()
                + ": "
                + result.detail());
      }

      session.rememberHistoryEvents(roomId, result.events());

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty()) {
        nextToken = scanCursor;
      }
      session.rememberHistoryCursor(roomId, nextToken, targetMs);

      long minTs = minHistoryTimestamp(result.events());
      long maxTs = maxHistoryTimestamp(result.events());
      if (minTs > 0L && maxTs > 0L && targetMs >= minTs && targetMs <= maxTs) {
        return nextToken;
      }
      if (minTs > 0L && targetMs > maxTs) {
        return scanCursor;
      }

      if (nextToken.equals(scanCursor)) {
        return nextToken;
      }
      scanCursor = nextToken;
    }

    return scanCursor;
  }

  List<ChatHistoryEntry> fetchHistoryEntriesForwardFromCursor(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String requestedTarget,
      String roomId,
      String fromToken,
      Instant fromInclusive,
      Instant untilExclusive,
      int limit) {
    if (session == null) {
      throw new IllegalStateException("Matrix history session is unavailable");
    }
    String cursor = normalize(fromToken);
    if (cursor.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }
    int requestedLimit = normalizeHistoryLimit(limit);
    List<ChatHistoryEntry> out = new java.util.ArrayList<>(requestedLimit);
    Set<String> seenMessageIds = new LinkedHashSet<>();
    Set<Integer> seenFallbackFingerprints = new LinkedHashSet<>();

    for (int page = 0; page < TIMESTAMP_CURSOR_MAX_PAGES && out.size() < requestedLimit; page++) {
      int remaining = requestedLimit - out.size();
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesAfter(
              serverId, server, session.accessToken(), roomId, cursor, remaining);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new IllegalStateException(
            "Matrix history forward fetch failed at " + result.endpoint() + ": " + result.detail());
      }

      session.rememberHistoryEvents(roomId, result.events());
      List<ChatHistoryEntry> pageEntries =
          toHistoryEntries(requestedTarget, roomId, result.events());
      for (ChatHistoryEntry entry : pageEntries) {
        if (entry == null) continue;
        Instant at = entry.at();
        if (fromInclusive != null && at.isBefore(fromInclusive)) continue;
        if (untilExclusive != null && !at.isBefore(untilExclusive)) {
          return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
        out.add(entry);
        if (out.size() >= requestedLimit) {
          return List.copyOf(out);
        }
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        break;
      }
      cursor = nextToken;
    }

    if (out.isEmpty()) {
      return List.of();
    }
    return List.copyOf(out);
  }

  List<ChatHistoryEntry> fetchHistoryEntriesBackwardFromCursor(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String requestedTarget,
      String roomId,
      String fromToken,
      Instant fromInclusive,
      Instant untilExclusive,
      int limit) {
    if (session == null) {
      throw new IllegalStateException("Matrix history session is unavailable");
    }
    String cursor = normalize(fromToken);
    if (cursor.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }
    int requestedLimit = normalizeHistoryLimit(limit);
    List<ChatHistoryEntry> out = new java.util.ArrayList<>(requestedLimit);
    Set<String> seenMessageIds = new LinkedHashSet<>();
    Set<Integer> seenFallbackFingerprints = new LinkedHashSet<>();

    for (int page = 0; page < TIMESTAMP_CURSOR_MAX_PAGES && out.size() < requestedLimit; page++) {
      int remaining = requestedLimit - out.size();
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              serverId, server, session.accessToken(), roomId, cursor, remaining);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new IllegalStateException(
            "Matrix history backward fetch failed at "
                + result.endpoint()
                + ": "
                + result.detail());
      }

      session.rememberHistoryEvents(roomId, result.events());
      List<ChatHistoryEntry> pageEntries =
          toHistoryEntries(requestedTarget, roomId, result.events());
      for (ChatHistoryEntry entry : pageEntries) {
        if (entry == null) continue;
        Instant at = entry.at();
        if (fromInclusive != null && at.isBefore(fromInclusive)) continue;
        if (untilExclusive != null && !at.isBefore(untilExclusive)) continue;
        if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
        out.add(entry);
        if (out.size() >= requestedLimit) {
          out.sort(Comparator.comparing(ChatHistoryEntry::at));
          return List.copyOf(out);
        }
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        break;
      }
      cursor = nextToken;
      session.rememberHistoryCursor(roomId, cursor, System.currentTimeMillis());
    }

    if (out.isEmpty()) {
      return List.of();
    }
    out.sort(Comparator.comparing(ChatHistoryEntry::at));
    return List.copyOf(out);
  }

  static List<ChatHistoryEntry> mergeHistoryCandidates(
      List<ChatHistoryEntry> first, List<ChatHistoryEntry> second) {
    if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
      return List.of();
    }
    Set<String> seenMessageIds = new LinkedHashSet<>();
    Set<Integer> seenFallbackFingerprints = new LinkedHashSet<>();
    List<ChatHistoryEntry> merged = new java.util.ArrayList<>();

    for (ChatHistoryEntry entry : first == null ? List.<ChatHistoryEntry>of() : first) {
      if (entry == null) continue;
      if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
      merged.add(entry);
    }
    for (ChatHistoryEntry entry : second == null ? List.<ChatHistoryEntry>of() : second) {
      if (entry == null) continue;
      if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
      merged.add(entry);
    }

    if (merged.isEmpty()) {
      return List.of();
    }
    merged.sort(Comparator.comparing(ChatHistoryEntry::at));
    return List.copyOf(merged);
  }

  static List<ChatHistoryEntry> selectEntriesAroundTimestamp(
      List<ChatHistoryEntry> entries, Instant centerAt, int limit) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    Instant center = centerAt == null ? Instant.now() : centerAt;
    int requestedLimit = normalizeHistoryLimitStatic(limit);

    List<ChatHistoryEntry> safe = entries.stream().filter(Objects::nonNull).toList();
    if (safe.isEmpty()) {
      return List.of();
    }

    long centerMs = center.toEpochMilli();
    List<ChatHistoryEntry> ranked = new java.util.ArrayList<>(safe);
    ranked.sort(
        Comparator.comparingLong(
                (ChatHistoryEntry e) -> distanceAbs(e.at().toEpochMilli(), centerMs))
            .thenComparing(ChatHistoryEntry::at)
            .thenComparing(
                ChatHistoryEntry::messageId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

    if (ranked.size() > requestedLimit) {
      ranked = new java.util.ArrayList<>(ranked.subList(0, requestedLimit));
    }
    ranked.sort(Comparator.comparing(ChatHistoryEntry::at));
    return List.copyOf(ranked);
  }

  private long lookupMessageTimestampByScanningHistory(
      String serverId,
      IrcProperties.Server server,
      SessionView session,
      String roomId,
      String messageId,
      String operation) {
    if (session == null) return 0L;
    String rid = normalize(roomId);
    String mid = normalize(messageId);
    if (rid.isEmpty() || mid.isEmpty()) return 0L;

    long cached = session.roomEventTimestampMs(rid, mid);
    if (cached > 0L) return cached;

    long beforeEpochMs = System.currentTimeMillis();
    String fromToken = resolveHistoryFromToken(serverId, server, session, rid, beforeEpochMs);
    if (fromToken.isEmpty()) return 0L;

    String cursor = fromToken;
    for (int page = 0; page < MSGID_LOOKUP_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              serverId, server, session.accessToken(), rid, cursor, MSGID_LOOKUP_SCAN_PAGE_LIMIT);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new BackendNotAvailableException(
            IrcProperties.Server.Backend.MATRIX,
            operation,
            normalize(serverId),
            "Matrix backend could not scan history for msgid selector: " + result.detail());
      }

      session.rememberHistoryEvents(rid, result.events());
      long found = session.roomEventTimestampMs(rid, mid);
      if (found > 0L) return found;

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        break;
      }
      session.rememberHistoryCursor(rid, nextToken, beforeEpochMs);
      cursor = nextToken;
      if (result.events() == null || result.events().isEmpty()) {
        break;
      }
    }

    return session.roomEventTimestampMs(rid, mid);
  }

  private static Instant parseHistoryTimestampSelector(String selector) {
    String token = normalize(selector);
    if (!token.toLowerCase(Locale.ROOT).startsWith(HISTORY_SELECTOR_TIMESTAMP_PREFIX)) {
      return null;
    }
    String value = normalize(token.substring(HISTORY_SELECTOR_TIMESTAMP_PREFIX.length()));
    if (value.isEmpty()) {
      throw new IllegalArgumentException("timestamp selector is blank");
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("timestamp selector is invalid: " + value, ex);
    }
  }

  private static String parseHistoryMessageIdSelector(String selector) {
    String token = normalize(selector);
    if (!token.toLowerCase(Locale.ROOT).startsWith(HISTORY_SELECTOR_MSGID_PREFIX)) {
      return "";
    }
    String messageId = normalize(token.substring(HISTORY_SELECTOR_MSGID_PREFIX.length()));
    if (messageId.isEmpty()) {
      throw new IllegalArgumentException("msgid selector is blank");
    }
    return messageId;
  }

  private static List<ChatHistoryEntry> toHistoryEntries(
      String requestedTarget,
      String roomId,
      List<MatrixRoomHistoryClient.RoomHistoryEvent> historyEvents) {
    if (historyEvents == null || historyEvents.isEmpty()) {
      return List.of();
    }
    String target = normalize(requestedTarget);
    String rid = normalize(roomId);
    if (target.isEmpty() || rid.isEmpty()) {
      return List.of();
    }

    List<ChatHistoryEntry> entries = new java.util.ArrayList<>(historyEvents.size());
    for (MatrixRoomHistoryClient.RoomHistoryEvent event : historyEvents) {
      if (event == null) continue;
      String sender = normalize(event.sender());
      String body = Objects.toString(event.body(), "");
      if (sender.isEmpty() || body.trim().isEmpty()) continue;

      String msgType = normalize(event.msgType());
      if (msgType.isEmpty()) {
        msgType = "m.text";
      }
      String mediaUrl = normalize(event.mediaUrl());
      ChatHistoryEntry.Kind kind =
          switch (msgType) {
            case "m.emote" -> ChatHistoryEntry.Kind.ACTION;
            case "m.notice" -> ChatHistoryEntry.Kind.NOTICE;
            default -> ChatHistoryEntry.Kind.PRIVMSG;
          };

      long ts = event.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      String messageId = normalize(event.eventId());
      String replyToMessageId = normalize(event.replyToEventId());
      Map<String, String> tags =
          withTag(
              withTag(
                  Map.of(TAG_MATRIX_ROOM_ID, rid, TAG_MATRIX_MSGTYPE, msgType),
                  TAG_MATRIX_MEDIA_URL,
                  mediaUrl),
              TAG_DRAFT_REPLY,
              replyToMessageId);
      entries.add(new ChatHistoryEntry(at, kind, target, sender, body, messageId, tags));
    }
    if (entries.isEmpty()) {
      return List.of();
    }
    return List.copyOf(entries);
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

  private static boolean rememberHistoryEntry(
      ChatHistoryEntry entry, Set<String> seenMessageIds, Set<Integer> seenFallbackFingerprints) {
    if (entry == null) {
      return false;
    }
    String msgId = normalize(entry.messageId());
    if (!msgId.isEmpty()) {
      return seenMessageIds.add(msgId);
    }
    return seenFallbackFingerprints.add(historyEntryFingerprint(entry));
  }

  private static int historyEntryFingerprint(ChatHistoryEntry entry) {
    Instant at = entry.at();
    return Objects.hash(
        at == null ? 0L : at.toEpochMilli(),
        entry.kind(),
        normalize(entry.target()),
        normalize(entry.from()),
        Objects.toString(entry.text(), ""));
  }

  private static long minHistoryTimestamp(List<MatrixRoomHistoryClient.RoomHistoryEvent> events) {
    if (events == null || events.isEmpty()) return 0L;
    long min = Long.MAX_VALUE;
    boolean found = false;
    for (MatrixRoomHistoryClient.RoomHistoryEvent event : events) {
      if (event == null) continue;
      long ts = event.originServerTs();
      if (ts <= 0L) continue;
      if (ts < min) min = ts;
      found = true;
    }
    return found ? min : 0L;
  }

  private static long maxHistoryTimestamp(List<MatrixRoomHistoryClient.RoomHistoryEvent> events) {
    if (events == null || events.isEmpty()) return 0L;
    long max = 0L;
    for (MatrixRoomHistoryClient.RoomHistoryEvent event : events) {
      if (event == null) continue;
      long ts = event.originServerTs();
      if (ts > max) max = ts;
    }
    return max;
  }

  private static int normalizeHistoryLimitStatic(int limit) {
    int normalized = limit <= 0 ? 50 : limit;
    return Math.max(1, Math.min(normalized, 200));
  }

  private static long distanceAbs(long left, long right) {
    long diff = left - right;
    return diff < 0L ? -diff : diff;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
