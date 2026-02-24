package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

final class PircbotxIrcv3ServerTime {

  private PircbotxIrcv3ServerTime() {}

  static Optional<Instant> fromEvent(Object pircbotxEvent) {
    if (pircbotxEvent == null) return Optional.empty();

    Map<String, String> tags = PircbotxIrcv3Tags.fromEvent(pircbotxEvent);
    String rawTime = PircbotxIrcv3Tags.firstTagValue(tags, "time");
    if (!rawTime.isBlank()) {
      Optional<Instant> parsed = parseInstantSafe(rawTime);
      if (parsed.isPresent()) return parsed;
    }

    return Optional.empty();
  }

  /** Parse {@code @time=} tag from a raw IRC line. */
  static Optional<Instant> fromRawLine(String rawLine) {
    Map<String, String> tags = PircbotxIrcv3Tags.fromRawLine(rawLine);
    String rawTime = PircbotxIrcv3Tags.firstTagValue(tags, "time");
    if (rawTime.isBlank()) return Optional.empty();
    return parseInstantSafe(rawTime);
  }

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
      return Optional.empty();
    }
  }
}
