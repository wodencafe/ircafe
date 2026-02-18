package cafe.woden.ircclient.ui.settings;

import java.util.Locale;

/**
 * Small global Look & Feel tweaks that can be applied on top of a FlatLaf theme.
 *
 * <p>These are intentionally "cheap wins": density (padding/row height) and rounded corner radius.
 */
public record ThemeTweakSettings(
    ThemeDensity density,
    int cornerRadius
) {

  public enum ThemeDensity {
    COMPACT,
    COZY,
    SPACIOUS;

    public static ThemeDensity from(String raw) {
      if (raw == null) return COZY;
      String s = raw.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "compact" -> COMPACT;
        case "spacious" -> SPACIOUS;
        default -> COZY;
      };
    }

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public ThemeTweakSettings {
    if (density == null) density = ThemeDensity.COZY;

    if (cornerRadius < 0) cornerRadius = 0;
    if (cornerRadius > 20) cornerRadius = 20;
  }

  public String densityId() {
    return density != null ? density.id() : ThemeDensity.COZY.id();
  }
}
