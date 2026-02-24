package cafe.woden.ircclient.ui.icons;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Small helper for loading classpath SVG icons via FlatLaf's {@link FlatSVGIcon}.
 *
 * <p>Why: crisp scaling on HiDPI, consistent icon styling, and easy recoloring to match the active
 * theme.
 */
public final class SvgIcons {

  /**
   * A simple palette enum that lets us recolor a small set of token colors used in our SVGs. SVG
   * authoring rule: use only these token colors (or nothing) for strokes/fills.
   */
  public enum Palette {
    /** Tree icons that should follow tree/label foreground. */
    TREE,

    /** Tree icons in disabled state. */
    TREE_DISABLED,

    /** Quiet icons for list rows (e.g. account dot in user list). */
    QUIET,

    /** Action icons used in menus/buttons that should follow standard label foreground. */
    ACTION,

    /** Disabled action icons used in menus/buttons. */
    ACTION_DISABLED,

    /** Tree PM icon with an online dot that follows accent. */
    TREE_PM_ONLINE,

    /** Tree PM icon with an offline dot that follows disabled foreground. */
    TREE_PM_OFFLINE
  }

  // Token colors used in our SVG resources.
  private static final int TOKEN_BASE = 0xFF6E6E6E; // standard FlatLaf "Actions.Grey" token
  private static final int TOKEN_ACCENT = 0xFF00C853; // online dot token (mapped to @accentColor)
  private static final int TOKEN_MUTED = 0xFF9E9E9E; // offline dot token (mapped to disabled)

  private static final Map<Key, Icon> CACHE = new ConcurrentHashMap<>();

  private SvgIcons() {}

  public static Icon icon(String name, int size, Palette palette) {
    if (name == null || name.isBlank()) return null;
    int s = Math.max(1, size);
    Palette p = palette != null ? palette : Palette.TREE;

    return CACHE.computeIfAbsent(
        new Key(name, s, p),
        k -> {
          FlatSVGIcon svg = new FlatSVGIcon(resourcePath(k.name), k.size, k.size);
          svg.setColorFilter(new FlatSVGIcon.ColorFilter(colorMapper(k.palette)));
          return svg;
        });
  }

  public static Icon tree(String name, int size) {
    return icon(name, size, Palette.TREE);
  }

  public static Icon treeDisabled(String name, int size) {
    return icon(name, size, Palette.TREE_DISABLED);
  }

  public static Icon quiet(String name, int size) {
    return icon(name, size, Palette.QUIET);
  }

  public static Icon action(String name, int size) {
    return icon(name, size, Palette.ACTION);
  }

  public static Icon actionDisabled(String name, int size) {
    return icon(name, size, Palette.ACTION_DISABLED);
  }

  /** Clears the icon cache (useful after LAF / theme changes). */
  public static void clearCache() {
    CACHE.clear();
  }

  private static String resourcePath(String name) {
    // resources are under src/main/resources/icons/svg/
    return "icons/svg/" + name + ".svg";
  }

  private static Function<Color, Color> colorMapper(Palette palette) {
    return in -> {
      if (in == null) return null;
      int rgb = in.getRGB();

      // Base icon color
      if (rgb == TOKEN_BASE || rgb == 0xFF000000) {
        Color fg =
            (palette == Palette.ACTION || palette == Palette.ACTION_DISABLED)
                ? labelForeground()
                : treeOrLabelForeground();
        if (palette == Palette.QUIET) {
          return withAlpha(fg, 170);
        }
        if (palette == Palette.TREE_DISABLED || palette == Palette.ACTION_DISABLED) {
          return disabledForeground(fg);
        }
        // TREE / ACTION / PM
        return withAlpha(fg, 235);
      }

      // Accent token (online dot)
      if (rgb == TOKEN_ACCENT) {
        Color accent = UIManager.getColor("@accentColor");
        if (accent == null) accent = UIManager.getColor("Component.focusColor");
        if (accent == null) accent = treeOrLabelForeground();
        return withAlpha(accent, 240);
      }

      // Muted token (offline dot)
      if (rgb == TOKEN_MUTED) {
        Color dis = UIManager.getColor("Label.disabledForeground");
        if (dis == null) dis = disabledForeground(treeOrLabelForeground());
        return withAlpha(dis, 220);
      }

      // Any other color remains unchanged.
      return in;
    };
  }

  private static Color labelForeground() {
    Color c = UIManager.getColor("Label.foreground");
    if (c == null) c = Color.DARK_GRAY;
    return c;
  }

  private static Color treeOrLabelForeground() {
    Color c = UIManager.getColor("Tree.textForeground");
    if (c == null) c = UIManager.getColor("Label.foreground");
    if (c == null) c = Color.DARK_GRAY;
    return c;
  }

  private static Color disabledForeground(Color fallback) {
    Color c = UIManager.getColor("Label.disabledForeground");
    if (c != null) return c;
    // Fallback: fade the normal foreground.
    return withAlpha(Objects.requireNonNullElse(fallback, Color.DARK_GRAY), 140);
  }

  private static Color withAlpha(Color c, int alpha) {
    int a = Math.max(0, Math.min(255, alpha));
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
  }

  private record Key(String name, int size, Palette palette) {}
}
