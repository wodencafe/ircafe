package cafe.woden.ircclient.util;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Lightweight build/version helper.
 *
 * <p>We try (in order):
 *
 * <ol>
 *   <li>Spring Boot build info: META-INF/build-info.properties (build.version)
 *   <li>Jar manifest: Implementation-Version
 *   <li>System property: ircafe.version
 * </ol>
 */
public final class AppVersion {

  public static final String APP_NAME = "IRCafe";

  private static volatile String cachedVersion;
  private static volatile Instant cachedBuildTime;

  private AppVersion() {}

  /** Returns the build/version string, or null if unknown. */
  public static String version() {
    String v = cachedVersion;
    if (v != null) return v;

    v = readBuildInfoVersion();
    if (v == null || v.isBlank()) v = readManifestVersion();
    if (v == null || v.isBlank()) v = System.getProperty("ircafe.version");

    if (v != null) v = v.trim();
    cachedVersion = v;
    return v;
  }

  /** Default display name used for window titles and CTCP VERSION. */
  public static String appNameWithVersion() {
    String v = version();
    if (v == null || v.isBlank()) return APP_NAME;
    return APP_NAME + " " + v;
  }

  /** Returns the build timestamp (best-effort) or null if unknown. */
  public static Instant buildTime() {
    Instant t = cachedBuildTime;
    if (t != null) return t;

    t = readBuildInfoTime();
    cachedBuildTime = t;
    return t;
  }

  /** Window title including version + build date when available. */
  public static String windowTitle() {
    String base = appNameWithVersion();

    Instant t = buildTime();
    if (t == null) return base;

    try {
      String date =
          DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(t);
      return base + " • built " + date;
    } catch (Exception ignored) {
      return base;
    }
  }

  /**
   * Decorate {@code irc.client.version} when it looks like the default app name.
   *
   * <p>If the configured value is blank, equals {@link #APP_NAME}, or equals {@code APP_NAME +
   * version()}, we return a best-effort build string that matches the main window title (includes
   * build date when available). Otherwise the configured value is returned unchanged.
   */
  public static String decorateIfDefaultName(String configured) {
    String v = version();

    if (configured == null || configured.isBlank()) return windowTitle();

    String t = configured.trim();
    if (t.equalsIgnoreCase(APP_NAME)) return windowTitle();

    if (v != null && !v.isBlank()) {
      String def = APP_NAME + " " + v;
      if (t.equalsIgnoreCase(def)) return windowTitle();
    }

    return t;
  }

  private static String readBuildInfoVersion() {
    try (InputStream in =
        AppVersion.class.getClassLoader().getResourceAsStream("META-INF/build-info.properties")) {
      if (in == null) return null;

      Properties p = new Properties();
      p.load(in);
      String v = p.getProperty("build.version");
      return (v == null) ? null : v.trim();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Instant readBuildInfoTime() {
    try (InputStream in =
        AppVersion.class.getClassLoader().getResourceAsStream("META-INF/build-info.properties")) {
      if (in == null) return null;

      Properties p = new Properties();
      p.load(in);

      String t = p.getProperty("build.time");
      if (t == null || t.isBlank()) return null;

      // Spring Boot writes this as an ISO-8601 instant string.
      try {
        return Instant.parse(t.trim());
      } catch (Exception ignored) {
        // Fall back: sometimes people hand-edit this to a local date/time.
        return null;
      }
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String readManifestVersion() {
    try {
      Package pkg = AppVersion.class.getPackage();
      if (pkg == null) return null;

      String v = pkg.getImplementationVersion();
      return (v == null) ? null : v.trim();
    } catch (Exception ignored) {
      return null;
    }
  }
}
