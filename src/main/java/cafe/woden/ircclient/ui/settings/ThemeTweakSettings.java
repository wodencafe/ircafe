package cafe.woden.ircclient.ui.settings;

import java.util.Locale;

/**
 * Small global Look & Feel tweaks that can be applied on top of a FlatLaf theme.
 *
 * <p>These include FlatLaf spacing tweaks and an optional global Swing UI font override.
 */
public record ThemeTweakSettings(
    ThemeDensity density,
    int cornerRadius,
    boolean uiFontOverrideEnabled,
    String uiFontFamily,
    int uiFontSize
) {

  public static final String DEFAULT_UI_FONT_FAMILY = "Dialog";
  public static final int DEFAULT_UI_FONT_SIZE = 13;

  public enum ThemeDensity {
    AUTO,
    COMPACT,
    COZY,
    SPACIOUS;

    public static ThemeDensity from(String raw) {
      if (raw == null) return AUTO;
      String s = raw.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "auto" -> AUTO;
        case "compact" -> COMPACT;
        case "cozy" -> COZY;
        case "spacious" -> SPACIOUS;
        default -> AUTO;
      };
    }

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public ThemeTweakSettings {
    if (density == null) density = ThemeDensity.AUTO;

    if (cornerRadius < 0) cornerRadius = 0;
    if (cornerRadius > 20) cornerRadius = 20;

    if (uiFontFamily == null || uiFontFamily.isBlank()) uiFontFamily = DEFAULT_UI_FONT_FAMILY;
    uiFontFamily = uiFontFamily.trim();

    if (uiFontSize < 8) uiFontSize = 8;
    if (uiFontSize > 48) uiFontSize = 48;
  }

  /**
   * Back-compat constructor used by existing call sites that only care about density + corner radius.
   */
  public ThemeTweakSettings(ThemeDensity density, int cornerRadius) {
    this(density, cornerRadius, false, DEFAULT_UI_FONT_FAMILY, DEFAULT_UI_FONT_SIZE);
  }

  public String densityId() {
    return density != null ? density.id() : ThemeDensity.AUTO.id();
  }
}
