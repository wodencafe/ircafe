package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Reassembles IRCv3 multiline message chunks into one logical payload.
 *
 * <p>Chunks are associated by {@code @batch=} and the sender/target tuple. Lines carrying {@code
 * multiline-concat} (or {@code draft/multiline-concat}) are buffered; the first subsequent line
 * without concat finalizes and emits the joined message.
 */
final class Ircv3MultilineAccumulator {

  private static final int MAX_BUFFERS = 512;
  private static final long BUFFER_TTL_MS = 60_000L;

  record FoldResult(
      boolean suppressed, Instant at, String text, String messageId, Map<String, String> tags) {
    static FoldResult passThrough(
        Instant at, String text, String messageId, Map<String, String> tags) {
      return new FoldResult(false, at, text, messageId, tags == null ? Map.of() : tags);
    }

    static FoldResult suppressChunk() {
      return new FoldResult(true, null, "", "", Map.of());
    }
  }

  private record Key(String batchId, String kind, String fromLower, String targetLower) {}

  private static final class Buffer {
    private final StringBuilder text = new StringBuilder();
    private final Map<String, String> tags = new HashMap<>();
    private Instant at;
    private String messageId;
    private long lastTouchedAtMs;

    Buffer(Instant at, String messageId, Map<String, String> tags, String textPart, long nowMs) {
      this.at = at;
      this.messageId = Objects.toString(messageId, "").trim();
      if (tags != null && !tags.isEmpty()) this.tags.putAll(tags);
      append(textPart, nowMs);
    }

    void merge(
        Instant at, String messageId, Map<String, String> tags, String textPart, long nowMs) {
      if (this.at == null && at != null) this.at = at;
      String msgId = Objects.toString(messageId, "").trim();
      if (this.messageId == null || this.messageId.isBlank()) {
        this.messageId = msgId;
      }
      if (tags != null && !tags.isEmpty()) this.tags.putAll(tags);
      append(textPart, nowMs);
    }

    private void append(String textPart, long nowMs) {
      String part = Objects.toString(textPart, "");
      if (text.length() > 0) text.append('\n');
      text.append(part);
      lastTouchedAtMs = nowMs;
    }
  }

  private final Map<Key, Buffer> buffers = new HashMap<>();

  FoldResult fold(
      String kind,
      String from,
      String target,
      Instant at,
      String text,
      String messageId,
      Map<String, String> tags) {
    long now = System.currentTimeMillis();
    pruneExpired(now);

    Map<String, String> safeTags = (tags == null) ? Map.of() : tags;
    String batchId = PircbotxIrcv3Tags.firstTagValue(safeTags, "batch");
    Map<String, String> cleanedTags = stripConcatTags(safeTags);
    if (batchId.isBlank()) {
      return FoldResult.passThrough(at, Objects.toString(text, ""), messageId, cleanedTags);
    }

    boolean concat = hasConcatTag(safeTags);
    Key key = new Key(batchId, normalizeToken(kind), normalizeToken(from), normalizeToken(target));

    Buffer buf = buffers.get(key);
    if (buf == null && !concat) {
      // batch-tagged line that is not part of multiline concat chaining
      return FoldResult.passThrough(at, Objects.toString(text, ""), messageId, cleanedTags);
    }

    if (buf == null) {
      if (buffers.size() >= MAX_BUFFERS) {
        buffers.clear();
      }
      buf = new Buffer(at, messageId, cleanedTags, text, now);
      buffers.put(key, buf);
    } else {
      buf.merge(at, messageId, cleanedTags, text, now);
    }

    if (concat) {
      return FoldResult.suppressChunk();
    }

    buffers.remove(key);
    Instant outAt = (buf.at == null) ? at : buf.at;
    String outText = buf.text.toString();
    String outMessageId = Objects.toString(buf.messageId, "").trim();
    Map<String, String> outTags = buf.tags.isEmpty() ? Map.of() : Map.copyOf(buf.tags);
    return FoldResult.passThrough(outAt, outText, outMessageId, outTags);
  }

  void clear() {
    buffers.clear();
  }

  private void pruneExpired(long nowMs) {
    if (buffers.isEmpty()) return;
    buffers
        .entrySet()
        .removeIf(
            e -> {
              Buffer b = e.getValue();
              long touched = (b == null) ? 0L : b.lastTouchedAtMs;
              return touched <= 0L || touched + BUFFER_TTL_MS < nowMs;
            });
  }

  private static boolean hasConcatTag(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) return false;
    for (String key : tags.keySet()) {
      if (isConcatTag(key)) return true;
    }
    return false;
  }

  private static Map<String, String> stripConcatTags(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) return Map.of();
    Map<String, String> out = null;
    for (Map.Entry<String, String> e : tags.entrySet()) {
      if (isConcatTag(e.getKey())) {
        if (out == null) {
          out = new HashMap<>(tags);
        }
        out.remove(e.getKey());
      }
    }
    if (out == null) {
      return tags;
    }
    if (out.isEmpty()) return Map.of();
    return Map.copyOf(out);
  }

  private static boolean isConcatTag(String rawKey) {
    String key = normalizeTagKey(rawKey);
    return "multiline-concat".equals(key) || "draft/multiline-concat".equals(key);
  }

  private static String normalizeTagKey(String rawKey) {
    String key = Objects.toString(rawKey, "").trim();
    if (key.startsWith("@")) key = key.substring(1).trim();
    if (key.startsWith("+")) key = key.substring(1).trim();
    return key.toLowerCase(Locale.ROOT);
  }

  private static String normalizeToken(String raw) {
    return Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
  }
}
