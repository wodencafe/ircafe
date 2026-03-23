package cafe.woden.ircclient.irc.pircbotx;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived dedupe cache for repeated RPL 324 channel-mode snapshots. */
final class PircbotxChannelMode324Deduper {
  private static final long CHANNEL_MODE_324_DEDUPE_TTL_MS = 2_000L;
  private static final int CHANNEL_MODE_324_DEDUPE_MAX = 256;

  private final Map<String, Long> recentByKey = new ConcurrentHashMap<>();

  boolean tryClaim(String channel, String details) {
    return tryClaim(channel, details, System.currentTimeMillis());
  }

  boolean tryClaim(String channel, String details, long nowMs) {
    String key = dedupeKey(channel, details);
    if (key.isEmpty()) {
      return true;
    }

    long now = nowMs > 0 ? nowMs : System.currentTimeMillis();
    cleanup(now);

    Long previous = recentByKey.put(key, now);
    return previous == null || (now - previous.longValue()) > CHANNEL_MODE_324_DEDUPE_TTL_MS;
  }

  void clear() {
    recentByKey.clear();
  }

  private void cleanup(long now) {
    long cutoff = now - CHANNEL_MODE_324_DEDUPE_TTL_MS;
    recentByKey.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < cutoff);

    // Keep this short-lived dedupe map hard-bounded even during very large join bursts.
    if (recentByKey.size() > CHANNEL_MODE_324_DEDUPE_MAX) {
      recentByKey.clear();
    }
  }

  private static String dedupeKey(String channel, String details) {
    String normalizedChannel = normalizeLower(channel);
    String normalizedDetails = normalizeWhitespace(details);
    if (normalizedChannel.isEmpty() || normalizedDetails.isEmpty()) {
      return "";
    }
    return normalizedChannel + '\n' + normalizedDetails;
  }

  private static String normalizeLower(String raw) {
    String value = Objects.toString(raw, "").trim();
    return value.isEmpty() ? "" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private static String normalizeWhitespace(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) {
      return "";
    }
    return value.replaceAll("\\s+", " ");
  }
}
