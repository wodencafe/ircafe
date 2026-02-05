package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.config.UiProperties;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class NickColorService {
  public static final String ATTR_NICK = "chat.nick";

  private static final List<String> DEFAULT_32 = List.of(
      "#E06C75", "#BE5046", "#D19A66", "#E5C07B",
      "#98C379", "#56B6C2", "#61AFEF", "#C678DD",
      "#FF6B6B", "#F06595", "#CC5DE8", "#845EF7",
      "#5C7CFA", "#339AF0", "#22B8CF", "#20C997",
      "#51CF66", "#94D82D", "#FCC419", "#FF922B",
      "#FF8787", "#FFA8A8", "#B197FC", "#9775FA",
      "#74C0FC", "#66D9E8", "#63E6BE", "#8CE99A",
      "#D0BFFF", "#FFD8A8", "#FFE066", "#FFB5A7"
  );

  private final NickColorSettingsBus settingsBus;
  private final List<Color> palette;

  private volatile Map<String, Color> overrides = Map.of();
  private volatile Map<String, String> overridesHex = Map.of();

  public NickColorService(UiProperties props, NickColorSettingsBus settingsBus) {
    this.settingsBus = settingsBus;

    List<String> rawPalette = props != null ? props.nickColors() : null;
    List<String> src = (rawPalette == null || rawPalette.isEmpty()) ? DEFAULT_32 : rawPalette;
    this.palette = parsePalette(src);

    Map<String, String> rawOverrides = props != null ? props.nickColorOverrides() : null;
    setOverrides(rawOverrides);
  }

  public boolean enabled() {
    NickColorSettings s = (settingsBus != null) ? settingsBus.get() : null;
    return s == null || s.enabled();
  }

  public double minContrast() {
    NickColorSettings s = (settingsBus != null) ? settingsBus.get() : null;
    return s != null ? s.minContrast() : 3.0;
  }

  public Map<String, String> overridesHex() {
    return overridesHex;
  }

  /** Replace the current per-nick overrides. */
  public synchronized void setOverrides(Map<String, String> rawOverrides) {
    Map<String, String> norm = normalizeRawOverrides(rawOverrides);
    this.overridesHex = Map.copyOf(norm);
    this.overrides = parseOverrides(this.overridesHex);
  }

  /** Create an attribute set based on {@code base}, storing the nick marker and (optionally) applying a color. */
  public SimpleAttributeSet forNick(String nick, AttributeSet base) {
    SimpleAttributeSet a = new SimpleAttributeSet(base);
    String lower = normalizeNick(nick);
    if (!lower.isEmpty()) {
      a.addAttribute(ATTR_NICK, lower);
      applyColor(a, lower);
    }
    return a;
  }

  public void applyColor(SimpleAttributeSet attrs, String nickLower) {
    NickColorSettings s = (settingsBus != null) ? settingsBus.get() : null;
    boolean enabled = (s == null) || s.enabled();
    double minContrast = (s != null) ? s.minContrast() : 3.0;
    if (!enabled) return;
    String n = nickLower == null ? "" : nickLower.trim().toLowerCase(Locale.ROOT);
    if (n.isEmpty()) return;

    Color bg = StyleConstants.getBackground(attrs);
    if (bg == null) bg = UIManager.getColor("TextPane.background");
    if (bg == null) bg = Color.WHITE;

    Color fgFallback = StyleConstants.getForeground(attrs);
    if (fgFallback == null) fgFallback = UIManager.getColor("TextPane.foreground");

    Color chosen = adjustedForContrast(baseColor(n), bg, minContrast, fgFallback);
    if (chosen != null) {
      StyleConstants.setForeground(attrs, chosen);
    }
  }

  public Color colorForNick(String nick, Color background, Color fallbackForeground) {
    NickColorSettings s = (settingsBus != null) ? settingsBus.get() : null;
    boolean enabled = (s == null) || s.enabled();
    double minContrast = (s != null) ? s.minContrast() : 3.0;
    if (!enabled) return fallbackForeground;
    String lower = normalizeNick(nick);
    if (lower.isEmpty()) return fallbackForeground;

    Color bg = background;
    if (bg == null) bg = UIManager.getColor("TextPane.background");
    if (bg == null) bg = Color.WHITE;

    return adjustedForContrast(baseColor(lower), bg, minContrast, fallbackForeground);
  }

  /**
   * Compute a nick color for preview purposes using explicit parameters (does not depend on current runtime settings).
   * This lets the Preferences UI show an immediate preview while the user tweaks controls before pressing Apply.
   */
  public Color previewColorForNick(String nick,
                                  Color background,
                                  Color fallbackForeground,
                                  boolean enabled,
                                  double minContrast) {
    if (!enabled) return fallbackForeground;
    String lower = normalizeNick(nick);
    if (lower.isEmpty()) return fallbackForeground;

    Color bg = background;
    if (bg == null) bg = UIManager.getColor("TextPane.background");
    if (bg == null) bg = Color.WHITE;

    double mc = (minContrast > 0) ? minContrast : 3.0;
    return adjustedForContrast(baseColor(lower), bg, mc, fallbackForeground);
  }


  private Color baseColor(String nickLower) {
    Color o = overrides.get(nickLower);
    if (o != null) return o;
    if (palette.isEmpty()) return UIManager.getColor("TextPane.foreground");
    int idx = Math.floorMod(nickLower.hashCode(), palette.size());
    return palette.get(idx);
  }

  private static String normalizeNick(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return "";
    return n.toLowerCase(Locale.ROOT);
  }

  private static List<Color> parsePalette(List<String> src) {
    List<Color> out = new ArrayList<>();
    if (src == null) return out;

    for (String s : src) {
      Color c = parseColor(s);
      if (c != null) out.add(c);
    }

    // Always ensure we have something.
    if (out.isEmpty()) {
      for (String s : DEFAULT_32) {
        Color c = parseColor(s);
        if (c != null) out.add(c);
      }
    }

    return List.copyOf(out);
  }

  private static Map<String, Color> parseOverrides(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();

    java.util.HashMap<String, Color> out = new java.util.HashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String nick = Objects.toString(e.getKey(), "").trim().toLowerCase(Locale.ROOT);
      if (nick.isEmpty()) continue;
      Color c = parseColor(e.getValue());
      if (c != null) out.put(nick, c);
    }
    return Map.copyOf(out);
  }

  private static Map<String, String> normalizeRawOverrides(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String nick = Objects.toString(e.getKey(), "").trim().toLowerCase(Locale.ROOT);
      if (nick.isEmpty()) continue;
      String val = Objects.toString(e.getValue(), "").trim();
      if (val.isEmpty()) continue;
      // Keep last-write-wins for duplicate keys after normalization.
      out.put(nick, val);
    }
    return out;
  }

  private static Color parseColor(String raw) {
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

  /**
   * Returns a version of {@code fg} that meets the desired contrast ratio against {@code bg},
   * adjusting only lightness (HSL) when possible.
   */
  private static Color adjustedForContrast(Color fg, Color bg, double min, Color fallbackFg) {
    if (fg == null) return fallbackFg;
    if (bg == null) return fg;
    if (min <= 0) return fg;

    if (contrastRatio(fg, bg) >= min) return fg;

    double bgLum = relLum(bg);
    boolean bgDark = bgLum < 0.5;

    float[] hsl = rgbToHsl(fg);
    float h = hsl[0], s = hsl[1], l = hsl[2];

    // Push lightness toward the direction that improves contrast.
    for (int i = 1; i <= 24; i++) {
      float step = i / 24f;
      float nl = bgDark ? lerp(l, 1.0f, step) : lerp(l, 0.0f, step);
      Color cand = hslToRgb(h, s, nl);
      if (contrastRatio(cand, bg) >= min) return cand;
    }

    // Last resort: choose black/white based on contrast.
    Color black = Color.BLACK;
    Color white = Color.WHITE;
    double cb = contrastRatio(black, bg);
    double cw = contrastRatio(white, bg);
    if (Math.max(cb, cw) >= min) return cb >= cw ? black : white;

    // Give up (but keep something predictable).
    return fallbackFg != null ? fallbackFg : fg;
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  // WCAG contrast ratio.
  private static double contrastRatio(Color a, Color b) {
    double la = relLum(a);
    double lb = relLum(b);
    double hi = Math.max(la, lb);
    double lo = Math.min(la, lb);
    return (hi + 0.05) / (lo + 0.05);
  }

  private static double relLum(Color c) {
    double r = srgbToLinear(c.getRed() / 255.0);
    double g = srgbToLinear(c.getGreen() / 255.0);
    double b = srgbToLinear(c.getBlue() / 255.0);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double srgbToLinear(double c) {
    if (c <= 0.04045) return c / 12.92;
    return Math.pow((c + 0.055) / 1.055, 2.4);
  }

  // HSL conversion helpers.
  private static float[] rgbToHsl(Color c) {
    float r = c.getRed() / 255f;
    float g = c.getGreen() / 255f;
    float b = c.getBlue() / 255f;

    float max = Math.max(r, Math.max(g, b));
    float min = Math.min(r, Math.min(g, b));
    float h, s, l = (max + min) / 2f;

    if (max == min) {
      h = s = 0f;
    } else {
      float d = max - min;
      s = l > 0.5f ? d / (2f - max - min) : d / (max + min);

      if (max == r) {
        h = (g - b) / d + (g < b ? 6f : 0f);
      } else if (max == g) {
        h = (b - r) / d + 2f;
      } else {
        h = (r - g) / d + 4f;
      }
      h /= 6f;
    }
    return new float[]{h, s, l};
  }

  private static Color hslToRgb(float h, float s, float l) {
    float r, g, b;

    if (s == 0f) {
      r = g = b = l;
    } else {
      float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
      float p = 2f * l - q;
      r = hueToRgb(p, q, h + 1f / 3f);
      g = hueToRgb(p, q, h);
      b = hueToRgb(p, q, h - 1f / 3f);
    }

    return new Color(clamp(r), clamp(g), clamp(b));
  }

  private static float hueToRgb(float p, float q, float t) {
    if (t < 0f) t += 1f;
    if (t > 1f) t -= 1f;
    if (t < 1f / 6f) return p + (q - p) * 6f * t;
    if (t < 1f / 2f) return q;
    if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
    return p;
  }

  private static float clamp(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }
}
