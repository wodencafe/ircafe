package cafe.woden.ircclient.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads the saved TLS opt-in before Spring or JSSE initializes, on the main startup thread. */
public final class StartupTlsSettings {
  public static final String RUNTIME_CONFIG_PATH =
      "${ircafe.runtime-config:${XDG_CONFIG_HOME:${user.home}/.config}/ircafe/ircafe.yml}";

  private StartupTlsSettings() {}

  public static boolean readAllowLegacyRsa(String[] args) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(args));
    try {
      return readAllowLegacyRsa(resolveRuntimeConfigPath(environment));
    } catch (Exception ex) {
      warnUnreadableSettings();
      return false;
    }
  }

  static Path resolveRuntimeConfigPath(Environment environment) {
    return Path.of(environment.resolveRequiredPlaceholders(RUNTIME_CONFIG_PATH).trim());
  }

  static boolean readAllowLegacyRsa(Path file) {
    if (file.toString().isBlank() || !Files.exists(file)) return false;
    try (var reader = Files.newBufferedReader(file)) {
      Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
      for (String key : new String[] {"irc", "client", "tls", "allowLegacyRsa"}) {
        if (!(value instanceof Map<?, ?> map)) return false;
        value = map.get(key);
      }
      return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    } catch (Exception ex) {
      // YAML parse exception messages can contain passwords from adjacent configuration lines.
      warnUnreadableSettings();
      return false;
    }
  }

  private static void warnUnreadableSettings() {
    System.getLogger(StartupTlsSettings.class.getName())
        .log(
            System.Logger.Level.WARNING,
            "Could not read saved TLS compatibility preference; using the default disabled setting");
  }
}
