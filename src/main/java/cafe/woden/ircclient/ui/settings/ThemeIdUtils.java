package cafe.woden.ircclient.ui.settings;

import java.util.Locale;
import java.util.Set;

public final class ThemeIdUtils {

  private static final Set<String> NON_FLAT_THEME_IDS =
      Set.of(
          "system",
          "nimbus",
          "nimbus-dark",
          "nimbus-dark-amber",
          "nimbus-dark-blue",
          "nimbus-dark-violet",
          "nimbus-dark-green",
          "nimbus-dark-orange",
          "nimbus-dark-magenta",
          "nimbus-orange",
          "nimbus-green",
          "nimbus-blue",
          "nimbus-violet",
          "nimbus-magenta",
          "nimbus-amber",
          "metal",
          "metal-ocean",
          "metal-steel",
          "motif",
          "windows",
          "gtk",
          "darklaf",
          "darklaf-darcula",
          "darklaf-solarized-dark",
          "darklaf-high-contrast-dark",
          "darklaf-light",
          "darklaf-high-contrast-light",
          "darklaf-intellij");

  private ThemeIdUtils() {}

  public static String normalizeThemeId(String id) {
    String s = String.valueOf(id != null ? id : "").trim();
    if (s.isEmpty()) return "darcula";

    if (s.regionMatches(true, 0, IntelliJThemePack.ID_PREFIX, 0, IntelliJThemePack.ID_PREFIX.length())) {
      return IntelliJThemePack.ID_PREFIX + s.substring(IntelliJThemePack.ID_PREFIX.length());
    }
    if (looksLikeClassName(s)) return s;

    return s.toLowerCase(Locale.ROOT);
  }

  public static boolean sameTheme(String a, String b) {
    return normalizeThemeId(a).equals(normalizeThemeId(b));
  }

  public static boolean looksLikeClassName(String raw) {
    if (raw == null) return false;
    String s = raw.trim();
    if (!s.contains(".")) return false;

    if (s.startsWith("com.") || s.startsWith("org.") || s.startsWith("net.") || s.startsWith("io.")) {
      return true;
    }

    String last = s.substring(s.lastIndexOf('.') + 1);
    return !last.isBlank() && Character.isUpperCase(last.charAt(0));
  }

  public static boolean isLikelyFlatTarget(String themeId) {
    String raw = themeId != null ? themeId.trim() : "";
    if (raw.isEmpty()) return true;

    if (raw.regionMatches(true, 0, IntelliJThemePack.ID_PREFIX, 0, IntelliJThemePack.ID_PREFIX.length())) {
      return true;
    }
    if (looksLikeClassName(raw)) {
      return raw.toLowerCase(Locale.ROOT).contains("flatlaf");
    }

    return !NON_FLAT_THEME_IDS.contains(raw.toLowerCase(Locale.ROOT));
  }
}
