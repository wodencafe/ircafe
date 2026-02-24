package cafe.woden.ircclient.app;

import java.time.Instant;
import java.util.Objects;

/** Immutable runtime event row shown in diagnostic tables. */
public record RuntimeDiagnosticEvent(
    Instant at, String level, String type, String summary, String details) {

  public RuntimeDiagnosticEvent {
    if (at == null) at = Instant.now();
    level = normalizeLevel(level);
    type = normalize(type, "Event");
    summary = normalize(summary, "");
    details = normalize(details, "");
  }

  private static String normalize(String raw, String fallback) {
    String s = Objects.toString(raw, "").trim();
    return s.isEmpty() ? fallback : s;
  }

  private static String normalizeLevel(String raw) {
    String s = normalize(raw, "INFO").toUpperCase(java.util.Locale.ROOT);
    return switch (s) {
      case "TRACE", "DEBUG", "INFO", "WARN", "ERROR" -> s;
      default -> "INFO";
    };
  }
}
