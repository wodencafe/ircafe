package cafe.woden.ircclient.ui.settings;

import java.util.Locale;
import java.util.Objects;

public enum MemoryUsageDisplayMode {
  LONG("long", "Long (used / max GiB)"),
  SHORT("short", "Short (percent badge)"),
  INDICATOR("indicator", "Indicator only"),
  MOON("moon", "Moon phases"),
  HIDDEN("hidden", "Hidden");

  private final String token;
  private final String label;

  MemoryUsageDisplayMode(String token, String label) {
    this.token = token;
    this.label = label;
  }

  public String token() {
    return token;
  }

  public String label() {
    return label;
  }

  public static MemoryUsageDisplayMode fromToken(String raw) {
    String v = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (v.isEmpty()) return LONG;
    return switch (v) {
      case "long", "full", "detailed" -> LONG;
      case "short", "compact" -> SHORT;
      case "indicator", "gauge", "bar" -> INDICATOR;
      case "moon", "moon-phase", "moon-phases", "lunar" -> MOON;
      case "hidden", "off", "none", "disable", "disabled" -> HIDDEN;
      default -> LONG;
    };
  }

  @Override
  public String toString() {
    return label;
  }
}
