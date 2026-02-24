package cafe.woden.ircclient.ui.settings;

import java.awt.Color;
import javax.swing.plaf.ColorUIResource;

final class ThemeColorUtils {

  private ThemeColorUtils() {}

  static ColorUIResource uiColor(int r, int g, int b) {
    return new ColorUIResource(r, g, b);
  }

  static Color parseHexColor(String raw) {
    String normalized = ThemeAccentSettings.normalizeHexOrNull(raw);
    if (normalized == null) return null;

    try {
      int rgb = Integer.parseInt(normalized.substring(1), 16);
      return new Color(rgb);
    } catch (Exception ignored) {
      return null;
    }
  }

  static Color mix(Color a, Color b, double t) {
    if (a == null) return b;
    if (b == null) return a;

    double ratio = Math.max(0.0, Math.min(1.0, t));
    int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * ratio);
    int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * ratio);
    int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * ratio);
    return new Color(clamp255(r), clamp255(g), clamp255(bl));
  }

  static Color lighten(Color c, double amount) {
    return mix(c, Color.WHITE, amount);
  }

  static Color darken(Color c, double amount) {
    return mix(c, Color.BLACK, amount);
  }

  static Color ensureContrastAgainstBackground(Color fg, Color bg, double minRatio) {
    if (fg == null || bg == null) return fg;
    if (contrastRatio(fg, bg) >= minRatio) return fg;

    Color best = fg;
    double bestRatio = contrastRatio(fg, bg);

    for (int i = 1; i <= 12; i++) {
      double t = i / 12.0;
      Color lighter = mix(fg, Color.WHITE, t);
      Color darker = mix(fg, Color.BLACK, t);
      double lighterRatio = contrastRatio(lighter, bg);
      double darkerRatio = contrastRatio(darker, bg);

      if (lighterRatio >= minRatio || darkerRatio >= minRatio) {
        if (lighterRatio >= minRatio && darkerRatio >= minRatio) {
          return lighterRatio >= darkerRatio ? lighter : darker;
        }
        return lighterRatio >= minRatio ? lighter : darker;
      }

      if (lighterRatio > bestRatio) {
        best = lighter;
        bestRatio = lighterRatio;
      }
      if (darkerRatio > bestRatio) {
        best = darker;
        bestRatio = darkerRatio;
      }
    }

    return best;
  }

  static boolean isDark(Color c) {
    if (c == null) return true;
    return relativeLuminance(c) < 0.45;
  }

  static double relativeLuminance(Color c) {
    if (c == null) return 0.0;

    double r = srgbToLinear(c.getRed() / 255.0);
    double g = srgbToLinear(c.getGreen() / 255.0);
    double b = srgbToLinear(c.getBlue() / 255.0);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  static double contrastRatio(Color c1, Color c2) {
    if (c1 == null || c2 == null) return 1.0;

    double l1 = relativeLuminance(c1);
    double l2 = relativeLuminance(c2);
    double lighter = Math.max(l1, l2);
    double darker = Math.min(l1, l2);
    return (lighter + 0.05) / (darker + 0.05);
  }

  static Color bestTextColor(Color bg) {
    if (bg == null) return Color.WHITE;
    return relativeLuminance(bg) > 0.55 ? Color.BLACK : Color.WHITE;
  }

  private static double srgbToLinear(double v) {
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  private static int clamp255(int v) {
    return Math.max(0, Math.min(255, v));
  }
}
