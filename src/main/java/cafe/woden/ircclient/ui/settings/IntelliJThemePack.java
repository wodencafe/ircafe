package cafe.woden.ircclient.ui.settings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for accessing the FlatLaf IntelliJ Themes Pack (flatlaf-intellij-themes).
 *
 * <p>This class is intentionally defensive: it uses reflection so that IRCafe can still
 * start even if the theme pack is not present on the classpath.
 */
public final class IntelliJThemePack {

  private static final Logger log = LoggerFactory.getLogger(IntelliJThemePack.class);

  /** Prefix used for theme ids that refer to a LookAndFeel class name. */
  public static final String ID_PREFIX = "ij:";

  public record PackTheme(String id, String label, boolean dark, String lafClassName) {}

  private static volatile List<PackTheme> cached;

  private IntelliJThemePack() {}

  /**
   * Enumerate all themes bundled in the IntelliJ themes pack.
   *
   * <p>Returns an empty list if the theme pack is not available.
   */
  public static List<PackTheme> listThemes() {
    List<PackTheme> existing = cached;
    if (existing != null) return existing;

    List<PackTheme> out = new ArrayList<>();
    try {
      Class<?> all = Class.forName("com.formdev.flatlaf.intellijthemes.FlatAllIJThemes");
      Field infosField = all.getField("INFOS");
      Object infos = infosField.get(null);

      if (infos instanceof Object[] arr) {
        for (Object info : arr) {
          if (info == null) continue;

          String name = callString(info, "getName");
          if (name == null || name.isBlank()) name = String.valueOf(info);

          String className = callString(info, "getClassName");
          if (className == null || className.isBlank()) {
            // Fallback for older/newer API variants.
            className = callString(info, "getLookAndFeelClassName");
          }
          if (className == null || className.isBlank()) continue;

          Boolean isDark = callBoolean(info, "isDark");
          if (isDark == null) isDark = callBoolean(info, "isDarkTheme");
          boolean dark = isDark != null ? isDark.booleanValue() : looksDark(name);

          out.add(new PackTheme(ID_PREFIX + className, name, dark, className));
        }
      }
    } catch (Throwable t) {
      // No theme pack, or API changed. Keep it quiet; caller can fall back.
      log.debug("[ircafe] IntelliJ theme pack not available (or incompatible): {}", t.toString());
      out.clear();
    }

    out.sort(Comparator.comparing(PackTheme::label, String.CASE_INSENSITIVE_ORDER));
    cached = Collections.unmodifiableList(out);
    return cached;
  }

  /**
   * Attempt to install a LookAndFeel given an id in {@link #ID_PREFIX} form or a raw class name.
   */
  public static boolean install(String idOrClassName) {
    if (idOrClassName == null || idOrClassName.isBlank()) return false;

    String s = idOrClassName.trim();
    String className = s.startsWith(ID_PREFIX) ? s.substring(ID_PREFIX.length()).trim() : s;

    // First try the class name as-is.
    if (tryInstallExact(className)) return true;

    // If the class name was persisted with the wrong case (e.g. older builds lowercased it),
    // try to resolve it case-insensitively against the theme pack's known classes.
    String resolved = resolveThemeClassNameIgnoreCase(className);
    if (resolved != null && !resolved.equals(className)) {
      return tryInstallExact(resolved);
    }

    return false;
  }

  private static boolean tryInstallExact(String className) {
    try {
      Class<?> clazz = Class.forName(className);
      Object inst = clazz.getDeclaredConstructor().newInstance();

      if (inst instanceof LookAndFeel laf) {
        UIManager.setLookAndFeel(laf);
      } else {
        UIManager.setLookAndFeel(className);
      }
      return true;
    } catch (Throwable t) {
      log.debug("[ircafe] Failed to install IntelliJ theme LAF {}: {}", className, t.toString());
      return false;
    }
  }

  private static String resolveThemeClassNameIgnoreCase(String className) {
    if (className == null || className.isBlank()) return null;
    for (PackTheme t : listThemes()) {
      if (t == null) continue;
      if (t.lafClassName() != null && t.lafClassName().equalsIgnoreCase(className)) {
        return t.lafClassName();
      }
    }
    return null;
  }

  private static String callString(Object target, String method) {
    try {
      Method m = target.getClass().getMethod(method);
      Object v = m.invoke(target);
      return v != null ? v.toString() : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Boolean callBoolean(Object target, String method) {
    try {
      Method m = target.getClass().getMethod(method);
      Object v = m.invoke(target);
      return v instanceof Boolean b ? b : (v != null ? Boolean.valueOf(v.toString()) : null);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static boolean looksDark(String name) {
    if (name == null) return false;
    String n = name.toLowerCase();
    return n.contains("dark") || n.contains("darcula") || n.contains("dracula") || n.contains("black");
  }
}
