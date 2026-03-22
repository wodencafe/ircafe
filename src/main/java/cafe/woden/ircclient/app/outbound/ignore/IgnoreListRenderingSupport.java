package cafe.woden.ircclient.app.outbound.ignore;

import cafe.woden.ircclient.ignore.api.IgnoreLevels;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared display rendering for outbound ignore-list command output. */
@ApplicationLayer
final class IgnoreListRenderingSupport {

  private IgnoreListRenderingSupport() {}

  static String formatHardMaskDisplay(
      String mask,
      List<String> levels,
      List<String> channels,
      long expiresAtEpochMs,
      String pattern,
      IgnoreTextPatternMode patternMode,
      boolean replies) {
    String normalizedMask = Objects.toString(mask, "").trim();
    if (normalizedMask.isEmpty()) return "";

    List<String> metadata = new ArrayList<>();
    List<String> normalizedLevels = IgnoreLevels.normalizeConfigured(levels);
    if (!(normalizedLevels.size() == 1 && "ALL".equalsIgnoreCase(normalizedLevels.getFirst()))) {
      metadata.add("levels=" + String.join(",", normalizedLevels));
    }
    if (channels != null && !channels.isEmpty()) {
      metadata.add("channels=" + String.join(",", channels));
    }
    if (expiresAtEpochMs > 0L) {
      metadata.add("expires=" + formatExpiry(expiresAtEpochMs));
    }

    String normalizedPattern = Objects.toString(pattern, "").trim();
    if (!normalizedPattern.isEmpty()) {
      metadata.add("pattern=" + renderPattern(normalizedPattern, patternMode));
    }
    if (replies) {
      metadata.add("replies");
    }

    if (metadata.isEmpty()) return normalizedMask;
    return normalizedMask + " [" + String.join("; ", metadata) + "]";
  }

  private static String formatExpiry(long expiresAtEpochMs) {
    long now = System.currentTimeMillis();
    long remaining = Math.max(0L, expiresAtEpochMs - now);
    String iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(expiresAtEpochMs));
    return iso + " (" + formatRemaining(remaining) + ")";
  }

  private static String formatRemaining(long remainingMs) {
    if (remainingMs <= 0L) return "expired";
    long totalSeconds = remainingMs / 1_000L;
    long days = totalSeconds / 86_400L;
    long hours = (totalSeconds % 86_400L) / 3_600L;
    long mins = (totalSeconds % 3_600L) / 60L;
    long secs = totalSeconds % 60L;
    StringBuilder sb = new StringBuilder("in ");
    if (days > 0) sb.append(days).append("d");
    if (hours > 0) sb.append(hours).append("h");
    if (mins > 0) sb.append(mins).append("m");
    if (secs > 0 || sb.length() == 3) sb.append(secs).append("s");
    return sb.toString();
  }

  private static String renderPattern(String pattern, IgnoreTextPatternMode mode) {
    String value = Objects.toString(pattern, "").trim();
    if (value.isEmpty()) return "";
    IgnoreTextPatternMode normalizedMode = (mode == null) ? IgnoreTextPatternMode.GLOB : mode;
    return switch (normalizedMode) {
      case REGEXP -> "/" + value + "/ (regexp)";
      case FULL -> value + " (full)";
      case GLOB -> value;
    };
  }
}
