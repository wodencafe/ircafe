package cafe.woden.ircclient.ui.settings;

import java.util.Locale;

/**
 * Optional theme accent override.
 *
 * <p>If {@link #accentColor()} is null, the theme's built-in accent is used. {@link #strength()}
 * blends between the theme's default accent and the chosen accent.
 */
public record ThemeAccentSettings(String accentColor, int strength) {

  public ThemeAccentSettings {
    if (accentColor != null && accentColor.isBlank()) accentColor = null;
    accentColor = normalizeHexOrNull(accentColor);

    if (strength < 0) strength = 0;
    if (strength > 100) strength = 100;
  }

  public boolean enabled() {
    return accentColor != null;
  }

  static String normalizeHexOrNull(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;

    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);

    if (s.length() == 3) {
      char r = s.charAt(0);
      char g = s.charAt(1);
      char b = s.charAt(2);
      s = "" + r + r + g + g + b + b;
    }

    if (s.length() != 6) return null;

    for (int i = 0; i < 6; i++) {
      char c = s.charAt(i);
      boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!ok) return null;
    }

    return "#" + s.toUpperCase(Locale.ROOT);
  }
}
