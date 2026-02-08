package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.Optional;

/**
 * Best-effort extraction of IRCv3 {@code server-time} timestamps.
 *
 * <p>When {@code server-time} is negotiated, servers/bouncers will prefix inbound lines with IRCv3
 * message tags like {@code @time=2026-02-07T12:34:56.789Z}. Using that timestamp lets the client
 * correctly order/play back backlog, and prevents local-clock jitter from corrupting history.
 */
final class PircbotxIrcv3ServerTime {

  private PircbotxIrcv3ServerTime() {
  }

  /**
   * Attempt to read {@code @time=} from a PircbotX event by reflection.
   *
   * <p>We avoid a hard compile-time dependency on any particular PircBotX event API for tags and
   * instead fall back to parsing the raw line if available.
   */
  static Optional<Instant> fromEvent(Object pircbotxEvent) {
    if (pircbotxEvent == null) return Optional.empty();

    // 1) If the event exposes tags, try that first.
    //    Many libraries use Map<String, String> or similar for IRCv3 message tags.
    Object tags = reflectCall(pircbotxEvent, "getTags");
    if (tags instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
      Object v = map.get("time");
      if (v == null) v = map.get("@time");
      if (v != null) {
        Optional<Instant> parsed = parseInstantSafe(String.valueOf(v));
        if (parsed.isPresent()) return parsed;
      }
    }

    // 2) Fall back to parsing raw line (preferred, since it works even if tags aren't mapped).
    Object raw = reflectCall(pircbotxEvent, "getRawLine");
    if (raw == null) raw = reflectCall(pircbotxEvent, "getLine");
    if (raw == null) raw = reflectCall(pircbotxEvent, "getRaw");
    if (raw != null) {
      return fromRawLine(String.valueOf(raw));
    }

    return Optional.empty();
  }

  /** Parse {@code @time=} tag from a raw IRC line. */
  static Optional<Instant> fromRawLine(String rawLine) {
    if (rawLine == null) return Optional.empty();
    String s = rawLine.trim();
    if (s.isEmpty() || s.charAt(0) != '@') return Optional.empty();

    int sp = s.indexOf(' ');
    if (sp <= 0) return Optional.empty();

    String tagSection = s.substring(1, sp); // exclude leading '@'
    if (tagSection.isEmpty()) return Optional.empty();

    // tags are separated by ';' and each entry is either "key" or "key=value".
    int idx = 0;
    while (idx < tagSection.length()) {
      int next = tagSection.indexOf(';', idx);
      if (next < 0) next = tagSection.length();
      String part = tagSection.substring(idx, next);
      idx = next + 1;

      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      String key = (eq >= 0) ? part.substring(0, eq) : part;
      if (!"time".equals(key)) continue;

      String val = (eq >= 0) ? part.substring(eq + 1) : "";
      return parseInstantSafe(val);
    }

    return Optional.empty();
  }

  /**
   * Backwards-compatible helper used by older call sites.
   *
   * <p>Returns {@code null} when no {@code @time=} tag is present or parsing fails.
   */
  static Instant parseServerTimeFromRawLine(String rawLine) {
    return fromRawLine(rawLine).orElse(null);
  }

  static Instant orNow(Optional<Instant> maybe, Instant fallbackNow) {
    return maybe != null && maybe.isPresent() ? maybe.get() : fallbackNow;
  }

  private static Optional<Instant> parseInstantSafe(String raw) {
    if (raw == null) return Optional.empty();
    String v = raw.trim();
    if (v.isEmpty()) return Optional.empty();
    try {
      return Optional.of(Instant.parse(v));
    } catch (Exception ignored) {
      // Some servers may emit non-ISO formats; ignore and fall back.
      return Optional.empty();
    }
  }

  private static Object reflectCall(Object target, String method) {
    if (target == null || method == null) return null;
    try {
      java.lang.reflect.Method m = target.getClass().getMethod(method);
      return m.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }
}
