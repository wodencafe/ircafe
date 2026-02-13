package cafe.woden.ircclient.app;

import java.io.InputStream;
import java.util.Properties;

/**
 * Lightweight build/version helper.
 *
 * <p>We try (in order):
 * <ol>
 *   <li>Spring Boot build info: META-INF/build-info.properties (build.version)</li>
 *   <li>Jar manifest: Implementation-Version</li>
 *   <li>System property: ircafe.version</li>
 * </ol>
 */
public final class AppVersion {

  public static final String APP_NAME = "IRCafe";

  private static volatile String cachedVersion;

  private AppVersion() {
  }

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

  /**
   * If the configured value is blank or equals the default app name, return APP_NAME + version.
   * Otherwise return the configured value unchanged.
   */
  public static String decorateIfDefaultName(String configured) {
    if (configured == null || configured.isBlank()) return appNameWithVersion();

    String t = configured.trim();
    if (t.equalsIgnoreCase(APP_NAME)) return appNameWithVersion();

    // If the user already included our build version, leave it alone.
    String v = version();
    if (v != null && !v.isBlank() && t.contains(v)) return t;

    return t;
  }

  private static String readBuildInfoVersion() {
    try (InputStream in = AppVersion.class.getClassLoader()
        .getResourceAsStream("META-INF/build-info.properties")) {
      if (in == null) return null;

      Properties p = new Properties();
      p.load(in);
      String v = p.getProperty("build.version");
      return (v == null) ? null : v.trim();
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
