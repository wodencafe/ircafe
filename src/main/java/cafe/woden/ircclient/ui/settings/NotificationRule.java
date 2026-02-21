package cafe.woden.ircclient.ui.settings;

import java.util.Locale;
import java.util.Objects;

/**
 * User-configured notification rule.
 */
public record NotificationRule(
    String label,
    Type type,
    String pattern,
    boolean enabled,
    boolean caseSensitive,
    boolean wholeWord,
    /** Optional per-rule highlight color as a hex string (e.g. "#FF00FF"). */
    String highlightFg
) {

  public enum Type {
    WORD,
    REGEX
  }

  public NotificationRule {
    if (type == null) type = Type.WORD;

    String p = Objects.toString(pattern, "").trim();
    pattern = p;

    String l = Objects.toString(label, "").trim();
    if (l.isEmpty() && !p.isEmpty()) l = p;
    label = l;

    String fg = normalizeHexColor(Objects.toString(highlightFg, "").trim());
    highlightFg = fg;

    // Empty patterns don't match anything.
    if (pattern.isEmpty()) enabled = false;
  }

  private static String normalizeHexColor(String v) {
    if (v == null) return null;
    String s = v.trim();
    if (s.isEmpty()) return null;

    if (s.startsWith("#")) s = s.substring(1).trim();

    if (s.length() == 3) {
      // Expand #RGB -> #RRGGBB
      char r = s.charAt(0);
      char g = s.charAt(1);
      char b = s.charAt(2);
      s = "" + r + r + g + g + b + b;
    } else if (s.length() != 6) {
      return null;
    }

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok = (c >= '0' && c <= '9')
          || (c >= 'a' && c <= 'f')
          || (c >= 'A' && c <= 'F');
      if (!ok) return null;
    }

    return "#" + s.toUpperCase(Locale.ROOT);
  }
}
