package cafe.woden.ircclient.irc.quassel;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Session-owned, bounded message/timestamp lookup and Quassel backlog selector rules. */
final class QuasselCoreHistorySupport {
  static final int UNKNOWN_MSG_ID = -1;
  private static final int HISTORY_LIMIT_DEFAULT = 50;
  private static final int HISTORY_LIMIT_MAX = 200;
  private static final int MAX_HISTORY_TARGETS_PER_SESSION = 4_096;
  private static final int MAX_HISTORY_MSGID_SAMPLES_PER_TARGET = 512;

  private final Map<String, TargetHistoryState> historyByTarget = new ConcurrentHashMap<>();

  void observe(String target, long messageId, Instant at) {
    if (messageId <= 0) return;
    String key = normalizeHistoryTargetKey(target);
    if (key.isEmpty()) return;
    Instant when = at == null ? Instant.now() : at;
    historyByTarget
        .computeIfAbsent(key, ignored -> new TargetHistoryState())
        .observe(messageId, when.toEpochMilli());
    int toRemove = historyByTarget.size() - MAX_HISTORY_TARGETS_PER_SESSION;
    if (toRemove <= 0) return;
    for (String candidate : historyByTarget.keySet()) {
      if (toRemove <= 0) break;
      if (historyByTarget.remove(candidate) != null) toRemove--;
    }
  }

  long msgIdForTimestamp(String target, Instant timestamp) {
    TargetHistoryState state = historyByTarget.get(normalizeHistoryTargetKey(target));
    return state == null ? UNKNOWN_MSG_ID : state.anchorForTimestamp(timestamp);
  }

  long firstMsgIdAtOrAfterTimestamp(String target, Instant timestamp) {
    TargetHistoryState state = historyByTarget.get(normalizeHistoryTargetKey(target));
    return state == null ? UNKNOWN_MSG_ID : state.firstMsgIdAtTimestampBoundary(timestamp, false);
  }

  long firstMsgIdAfterTimestamp(String target, Instant timestamp) {
    TargetHistoryState state = historyByTarget.get(normalizeHistoryTargetKey(target));
    return state == null ? UNKNOWN_MSG_ID : state.firstMsgIdAtTimestampBoundary(timestamp, true);
  }

  long timestampForMsgId(String target, long msgId) {
    if (msgId <= 0L) return UNKNOWN_MSG_ID;
    TargetHistoryState state = historyByTarget.get(normalizeHistoryTargetKey(target));
    return state == null ? UNKNOWN_MSG_ID : state.timestampForMsgId(msgId);
  }

  long exactTimestampForMsgId(String target, long msgId) {
    if (msgId <= 0L) return UNKNOWN_MSG_ID;
    TargetHistoryState state = historyByTarget.get(normalizeHistoryTargetKey(target));
    return state == null ? UNKNOWN_MSG_ID : state.exactTimestampForMsgId(msgId);
  }

  long readMarkerMsgIdForTimestamp(String target, Instant timestamp) {
    TargetHistoryState state = historyByTarget.get(normalizeHistoryTargetKey(target));
    return state == null ? UNKNOWN_MSG_ID : state.readMarkerMsgIdForTimestamp(timestamp);
  }

  void clear() {
    historyByTarget.clear();
  }

  static HistorySelector parseHistorySelector(String selector, boolean wildcardAllowed) {
    String raw = Objects.toString(selector, "").trim();
    if (wildcardAllowed && "*".equals(raw)) {
      return new HistorySelector(HistorySelectorKind.WILDCARD, UNKNOWN_MSG_ID, null);
    }

    int eq = raw.indexOf('=');
    if (eq <= 0 || eq == raw.length() - 1) {
      throw new IllegalArgumentException("history selector must be key=value");
    }
    String normalized = raw;
    String key = normalized.substring(0, eq).trim().toLowerCase(Locale.ROOT);
    String value = normalized.substring(eq + 1).trim();
    if ("msgid".equals(key)) {
      return new HistorySelector(HistorySelectorKind.MSGID, parsePositiveMsgId(value), null);
    }
    if ("timestamp".equals(key)) {
      try {
        return new HistorySelector(
            HistorySelectorKind.TIMESTAMP, UNKNOWN_MSG_ID, Instant.parse(value));
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("timestamp selector must be ISO-8601 (UTC)", e);
      }
    }
    throw new IllegalArgumentException(
        "Quassel history selectors support only msgid=... and timestamp=...");
  }

  static int normalizeHistoryLimit(int limit) {
    int lim = limit <= 0 ? HISTORY_LIMIT_DEFAULT : limit;
    if (lim > HISTORY_LIMIT_MAX) return HISTORY_LIMIT_MAX;
    return lim;
  }

  private static long parsePositiveMsgId(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("msgid selector is blank");
    }
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0L) {
        throw new IllegalArgumentException("msgid selector must be a positive integer");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("msgid selector must be numeric for Quassel backlog", e);
    }
  }

  static int clampMsgId(long value) {
    if (value <= 0L) return UNKNOWN_MSG_ID;
    if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return (int) value;
  }

  private static String normalizeHistoryTargetKey(String target) {
    String normalized = Objects.toString(target, "").trim();
    if (normalized.isEmpty()) return "";
    return normalized.toLowerCase(Locale.ROOT);
  }

  enum HistorySelectorKind {
    WILDCARD,
    MSGID,
    TIMESTAMP
  }

  record HistorySelector(HistorySelectorKind kind, long msgId, Instant timestamp) {}

  private static final class TargetHistoryState {
    private long oldestMsgId = Long.MAX_VALUE;
    private long newestMsgId = UNKNOWN_MSG_ID;
    private long oldestTsEpochMs = Long.MAX_VALUE;
    private long newestTsEpochMs = UNKNOWN_MSG_ID;
    private final NavigableMap<Long, Long> timestampByMsgId = new TreeMap<>();

    synchronized void observe(long messageId, long timestampEpochMs) {
      if (messageId <= 0L) return;
      long ts = timestampEpochMs > 0L ? timestampEpochMs : System.currentTimeMillis();
      if (messageId < oldestMsgId) oldestMsgId = messageId;
      if (messageId > newestMsgId) newestMsgId = messageId;
      if (ts < oldestTsEpochMs) oldestTsEpochMs = ts;
      if (ts > newestTsEpochMs) newestTsEpochMs = ts;

      timestampByMsgId.put(messageId, ts);
      if (timestampByMsgId.size() > MAX_HISTORY_MSGID_SAMPLES_PER_TARGET) {
        boolean dropOldest =
            timestampByMsgId.lastKey() - messageId < messageId - timestampByMsgId.firstKey();
        if (dropOldest) {
          timestampByMsgId.pollFirstEntry();
        } else {
          timestampByMsgId.pollLastEntry();
        }
      }
    }

    synchronized long anchorForTimestamp(Instant timestamp) {
      if (newestMsgId <= 0L) return UNKNOWN_MSG_ID;
      long ts = (timestamp == null ? Instant.now() : timestamp).toEpochMilli();
      if (oldestTsEpochMs != Long.MAX_VALUE && ts <= oldestTsEpochMs) {
        return oldestMsgId > 0L ? oldestMsgId : newestMsgId;
      }
      if (newestTsEpochMs > 0L && ts >= newestTsEpochMs) {
        return newestMsgId;
      }
      if (oldestTsEpochMs == Long.MAX_VALUE || newestTsEpochMs <= 0L) {
        return newestMsgId;
      }
      long midpoint = oldestTsEpochMs + ((newestTsEpochMs - oldestTsEpochMs) / 2L);
      return ts <= midpoint ? oldestMsgId : newestMsgId;
    }

    synchronized long firstMsgIdAtTimestampBoundary(Instant timestamp, boolean exclusive) {
      if (newestMsgId <= 0L) return UNKNOWN_MSG_ID;
      long ts = (timestamp == null ? Instant.now() : timestamp).toEpochMilli();
      if (ts < oldestTsEpochMs || (!exclusive && ts == oldestTsEpochMs)) {
        return oldestMsgId;
      }
      for (Map.Entry<Long, Long> sample : timestampByMsgId.entrySet()) {
        if (sample.getValue() > ts || (!exclusive && sample.getValue() == ts)) {
          return sample.getKey();
        }
      }
      // Core accepts an inclusive lower / exclusive upper ID bound. Beyond the newest
      // observation this includes all known messages for BEFORE and none for LATEST.
      return newestMsgId + 1L;
    }

    synchronized long timestampForMsgId(long messageId) {
      if (messageId <= 0L || timestampByMsgId.isEmpty()) return UNKNOWN_MSG_ID;
      Long exact = timestampByMsgId.get(messageId);
      if (exact != null && exact.longValue() > 0L) {
        return exact.longValue();
      }

      Map.Entry<Long, Long> floor = timestampByMsgId.floorEntry(messageId);
      Map.Entry<Long, Long> ceil = timestampByMsgId.ceilingEntry(messageId);
      if (floor == null && ceil == null) return UNKNOWN_MSG_ID;
      if (floor == null) return sanitizeTimestampSample(ceil.getValue());
      if (ceil == null) return sanitizeTimestampSample(floor.getValue());

      long floorDelta = Math.abs(messageId - floor.getKey());
      long ceilDelta = Math.abs(ceil.getKey() - messageId);
      return floorDelta <= ceilDelta
          ? sanitizeTimestampSample(floor.getValue())
          : sanitizeTimestampSample(ceil.getValue());
    }

    synchronized long exactTimestampForMsgId(long messageId) {
      return sanitizeTimestampSample(timestampByMsgId.get(messageId));
    }

    synchronized long readMarkerMsgIdForTimestamp(Instant timestamp) {
      long atMs = (timestamp == null ? Instant.now() : timestamp).toEpochMilli();
      // Native timestamps have second precision; ties must include the latest message ID.
      for (Map.Entry<Long, Long> sample : timestampByMsgId.descendingMap().entrySet()) {
        if (sample.getValue() <= atMs) return sample.getKey();
      }
      return UNKNOWN_MSG_ID;
    }

    private static long sanitizeTimestampSample(Long sample) {
      if (sample == null || sample.longValue() <= 0L) return UNKNOWN_MSG_ID;
      return sample.longValue();
    }
  }
}
