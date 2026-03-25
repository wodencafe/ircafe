package cafe.woden.ircclient.ui.settings;

import java.awt.Color;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.UIManager;

final class SettingsColorSupport {
  private SettingsColorSupport() {}

  static String toHex(Color c) {
    if (c == null) return "";
    return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
  }

  static Color parseHexColor(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() != 6) return null;
    try {
      int rgb = Integer.parseInt(s, 16);
      return new Color(rgb);
    } catch (Exception ignored) {
      return null;
    }
  }

  static Color parseHexColorLenient(String raw) {
    Color c = parseHexColor(raw);
    if (c != null) return c;
    if (raw == null) return null;

    String s = raw.trim();
    if (s.startsWith("#")) s = s.substring(1).trim();
    if (s.length() != 3) return null;

    char r = s.charAt(0);
    char g = s.charAt(1);
    char b = s.charAt(2);
    return parseHexColor("#" + r + r + g + g + b + b);
  }

  static String normalizeOptionalHexForApply(String raw, String fieldLabel) {
    String hex = raw != null ? raw.trim() : "";
    if (hex.isBlank()) return null;
    Color c = parseHexColorLenient(hex);
    if (c == null) {
      String label = Objects.toString(fieldLabel, "Color");
      throw new IllegalArgumentException(
          label + " must be a hex value like #RRGGBB (or blank for default).");
    }
    return toHex(c);
  }

  static Color contrastTextColor(Color bg) {
    if (bg == null) return UIManager.getColor("Label.foreground");
    double r = bg.getRed() / 255.0;
    double g = bg.getGreen() / 255.0;
    double b = bg.getBlue() / 255.0;
    double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    return y < 0.55 ? Color.WHITE : Color.BLACK;
  }

  static Color preferredPreviewBackground() {
    Color bg = UIManager.getColor("TextPane.background");
    if (bg == null) bg = UIManager.getColor("TextArea.background");
    if (bg == null) bg = UIManager.getColor("Table.background");
    if (bg == null) bg = UIManager.getColor("Panel.background");
    return bg != null ? bg : new Color(30, 30, 30);
  }

  static Icon createColorSwatchIcon(Color color, int w, int h) {
    return new ColorSwatch(color, w, h);
  }

  static double contrastRatio(Color fg, Color bg) {
    if (fg == null || bg == null) return 0.0;

    double l1 = relativeLuminance(fg);
    double l2 = relativeLuminance(bg);
    if (l1 < l2) {
      double t = l1;
      l1 = l2;
      l2 = t;
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed());
    double g = srgbToLinear(c.getGreen());
    double b = srgbToLinear(c.getBlue());
    return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
  }

  private static double srgbToLinear(int channel) {
    double v = channel / 255.0;
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }
}
