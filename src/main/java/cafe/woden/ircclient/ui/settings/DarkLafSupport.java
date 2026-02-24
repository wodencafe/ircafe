package cafe.woden.ircclient.ui.settings;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional DarkLaf integration loaded via reflection.
 *
 * <p>This keeps IRCafe startup/theme switching resilient when DarkLaf is not present.
 */
final class DarkLafSupport {

  private static final Logger log = LoggerFactory.getLogger(DarkLafSupport.class);

  private static final String LAF_MANAGER_CLASS = "com.github.weisj.darklaf.LafManager";
  private static final String THEME_BASE_CLASS = "com.github.weisj.darklaf.theme.Theme";
  private static final String ONE_DARK_THEME_CLASS = "com.github.weisj.darklaf.theme.OneDarkTheme";
  private static final String DARCULA_THEME_CLASS = "com.github.weisj.darklaf.theme.DarculaTheme";
  private static final String SOLARIZED_DARK_THEME_CLASS =
      "com.github.weisj.darklaf.theme.SolarizedDarkTheme";
  private static final String SOLARIZED_LIGHT_THEME_CLASS =
      "com.github.weisj.darklaf.theme.SolarizedLightTheme";
  private static final String HIGH_CONTRAST_DARK_THEME_CLASS =
      "com.github.weisj.darklaf.theme.HighContrastDarkTheme";
  private static final String HIGH_CONTRAST_LIGHT_THEME_CLASS =
      "com.github.weisj.darklaf.theme.HighContrastLightTheme";
  private static final String INTELLIJ_THEME_CLASS = "com.github.weisj.darklaf.theme.IntelliJTheme";
  private static volatile Boolean available;

  private DarkLafSupport() {}

  static boolean isAvailable() {
    Boolean cached = available;
    if (cached != null) return cached;

    boolean ok;
    try {
      Class.forName(LAF_MANAGER_CLASS);
      ok = true;
    } catch (Throwable t) {
      ok = false;
    }

    available = ok;
    return ok;
  }

  static boolean installDefault() {
    return installThemeByClassName(ONE_DARK_THEME_CLASS);
  }

  static boolean installDarcula() {
    return installThemeByClassName(DARCULA_THEME_CLASS);
  }

  static boolean installSolarizedDark() {
    return installThemeByClassName(SOLARIZED_DARK_THEME_CLASS);
  }

  static boolean installLight() {
    return installThemeByClassName(SOLARIZED_LIGHT_THEME_CLASS);
  }

  static boolean installHighContrastDark() {
    return installThemeByClassName(HIGH_CONTRAST_DARK_THEME_CLASS);
  }

  static boolean installHighContrastLight() {
    return installThemeByClassName(HIGH_CONTRAST_LIGHT_THEME_CLASS);
  }

  static boolean installIntelliJ() {
    return installThemeByClassName(INTELLIJ_THEME_CLASS);
  }

  private static boolean installThemeByClassName(String themeClassName) {
    if (!isAvailable()) return false;
    if (themeClassName == null || themeClassName.isBlank()) return false;

    try {
      Class<?> manager = Class.forName(LAF_MANAGER_CLASS);
      Class<?> themeBase = Class.forName(THEME_BASE_CLASS);
      Class<?> themeClass = Class.forName(themeClassName);

      Object theme = themeClass.getDeclaredConstructor().newInstance();
      Method installTheme = manager.getMethod("install", themeBase);
      installTheme.invoke(null, theme);
      return true;
    } catch (Throwable t) {
      log.warn("[ircafe] Failed to install DarkLaf theme '{}'", themeClassName, t);
      return false;
    }
  }
}
