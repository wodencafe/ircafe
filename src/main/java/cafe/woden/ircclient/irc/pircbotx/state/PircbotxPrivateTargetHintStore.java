package cafe.woden.ircclient.irc.pircbotx.state;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded hint cache for mapping private-message echoes back to their conversation target. */
final class PircbotxPrivateTargetHintStore {
  private static final long PRIVATE_TARGET_HINT_TTL_MS = 120_000L;
  private static final int PRIVATE_TARGET_HINT_MAX = 1_024;

  private record PrivateTargetHint(
      String fromLower, String target, String kind, String payload, long observedAtMs) {}

  private final Map<String, PrivateTargetHint> byMessageId = new ConcurrentHashMap<>();
  private final Map<String, PrivateTargetHint> byFingerprint = new ConcurrentHashMap<>();

  void remember(
      String fromNick,
      String target,
      String kind,
      String payload,
      String messageId,
      long observedAtMs) {
    String from = normalizeLower(fromNick);
    String dest = normalizeTarget(target);
    String k = normalizeKind(kind);
    String body = normalizePayload(payload);
    String msgId = normalizeMessageId(messageId);
    if (from.isEmpty() || dest.isEmpty() || k.isEmpty()) {
      return;
    }
    if (body.isEmpty() && msgId.isEmpty()) {
      return;
    }

    long now = observedAtMs > 0 ? observedAtMs : System.currentTimeMillis();
    cleanup(now);

    PrivateTargetHint hint = new PrivateTargetHint(from, dest, k, body, now);
    if (!msgId.isEmpty()) {
      byMessageId.put(msgId, hint);
    }
    if (!body.isEmpty()) {
      byFingerprint.put(fingerprint(from, k, body), hint);
    }
  }

  String find(String fromNick, String kind, String payload, String messageId, long nowMs) {
    String from = normalizeLower(fromNick);
    String k = normalizeKind(kind);
    String body = normalizePayload(payload);
    String msgId = normalizeMessageId(messageId);
    long now = nowMs > 0 ? nowMs : System.currentTimeMillis();
    if (from.isEmpty() || k.isEmpty()) {
      return "";
    }

    cleanup(now);

    if (!msgId.isEmpty()) {
      PrivateTargetHint byId = byMessageId.get(msgId);
      if (isUsableById(byId, from, k, now)) {
        return byId.target();
      }
    }

    if (!body.isEmpty()) {
      PrivateTargetHint byPayload = byFingerprint.get(fingerprint(from, k, body));
      if (isUsableByFingerprint(byPayload, from, k, body, now)) {
        return byPayload.target();
      }
    }
    return "";
  }

  void clear() {
    byMessageId.clear();
    byFingerprint.clear();
  }

  private static boolean isUsableById(PrivateTargetHint hint, String from, String kind, long now) {
    if (hint == null) {
      return false;
    }
    if (hint.observedAtMs() + PRIVATE_TARGET_HINT_TTL_MS < now) {
      return false;
    }
    if (!Objects.equals(hint.fromLower(), from)) {
      return false;
    }
    return Objects.equals(hint.kind(), kind);
  }

  private static boolean isUsableByFingerprint(
      PrivateTargetHint hint, String from, String kind, String payload, long now) {
    if (!isUsableById(hint, from, kind, now)) {
      return false;
    }
    return Objects.equals(hint.payload(), payload);
  }

  private void cleanup(long now) {
    long cutoff = now - PRIVATE_TARGET_HINT_TTL_MS;
    byMessageId
        .entrySet()
        .removeIf(e -> e.getValue() == null || e.getValue().observedAtMs() < cutoff);
    byFingerprint
        .entrySet()
        .removeIf(e -> e.getValue() == null || e.getValue().observedAtMs() < cutoff);

    // Hard cap in case event volume is very high and many entries have identical timestamps.
    if (byMessageId.size() > PRIVATE_TARGET_HINT_MAX * 2) {
      byMessageId.clear();
    }
    if (byFingerprint.size() > PRIVATE_TARGET_HINT_MAX * 2) {
      byFingerprint.clear();
    }
  }

  private static String normalizeLower(String raw) {
    String value = Objects.toString(raw, "").trim();
    return value.isEmpty() ? "" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private static String normalizeTarget(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String normalizeKind(String raw) {
    String value = Objects.toString(raw, "").trim().toUpperCase(java.util.Locale.ROOT);
    if (value.isEmpty()) {
      return "";
    }
    return switch (value) {
      case "PRIVMSG", "ACTION" -> value;
      default -> "";
    };
  }

  private static String normalizePayload(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String normalizeMessageId(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String fingerprint(String fromLower, String kind, String payload) {
    return fromLower + '\n' + kind + '\n' + payload;
  }
}
