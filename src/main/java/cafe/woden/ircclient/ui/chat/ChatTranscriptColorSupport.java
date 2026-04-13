package cafe.woden.ircclient.ui.chat;

import java.awt.Color;

/**
 * Pure color-math utilities shared by transcript rendering and restyle passes.
 *
 * <p>All methods are stateless; they operate only on their parameters.
 */
final class ChatTranscriptColorSupport {

  private ChatTranscriptColorSupport() {}

  /**
   * Blends {@code source} toward {@code target} by {@code sourceWeight ∈ [0,1]}.
   *
   * <p>A weight of {@code 1.0} returns {@code source} unchanged; {@code 0.0} returns {@code
   * target}.
   */
  static Color blendToward(Color target, Color source, double sourceWeight) {
    if (target == null) return source;
    if (source == null) return target;

    double clamped = Math.max(0.0, Math.min(1.0, sourceWeight));
    double tw = 1.0 - clamped;
    int r = clampChannel((int) Math.round(target.getRed() * tw + source.getRed() * clamped));
    int g = clampChannel((int) Math.round(target.getGreen() * tw + source.getGreen() * clamped));
    int b = clampChannel((int) Math.round(target.getBlue() * tw + source.getBlue() * clamped));
    return new Color(r, g, b);
  }

  /**
   * Returns {@link Color#BLACK} or {@link Color#WHITE}, whichever contrasts more against {@code
   * bg}.
   */
  static Color bestTextColorForBackground(Color bg) {
    if (bg == null) return Color.BLACK;
    double blackContrast = contrastRatio(Color.BLACK, bg);
    double whiteContrast = contrastRatio(Color.WHITE, bg);
    return blackContrast >= whiteContrast ? Color.BLACK : Color.WHITE;
  }

  /** WCAG 2.1 contrast ratio between two colours (range 1–21). */
  static double contrastRatio(Color a, Color b) {
    if (a == null || b == null) return 0.0;
    double l1 = relativeLuminance(a);
    double l2 = relativeLuminance(b);
    if (l1 < l2) {
      double t = l1;
      l1 = l2;
      l2 = t;
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  /** WCAG 2.1 relative luminance of a colour. */
  static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed());
    double g = srgbToLinear(c.getGreen());
    double b = srgbToLinear(c.getBlue());
    return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
  }

  /** Converts an 8-bit sRGB channel value to a linear-light value. */
  static double srgbToLinear(int channel) {
    double v = channel / 255.0;
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  /** Clamps a colour channel to {@code [0, 255]}. */
  static int clampChannel(int v) {
    return Math.max(0, Math.min(255, v));
  }

  /**
   * Parses a CSS/hex colour string (with or without leading {@code #} or {@code 0x}).
   *
   * @return the parsed {@link Color}, or {@code null} if the input is null, blank, or malformed
   */
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
}
