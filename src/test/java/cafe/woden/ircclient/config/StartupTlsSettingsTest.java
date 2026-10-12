package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class StartupTlsSettingsTest {
  @TempDir Path temp;

  @Test
  void missingMalformedAndAbsentPreferencesDefaultToDisabled() throws Exception {
    Path config = temp.resolve("ircafe.yml");
    assertFalse(StartupTlsSettings.readAllowLegacyRsa(config));
    for (String content : new String[] {"irc: [", "irc: {}", "irc:\n  client:\n    tls: {}"}) {
      Files.writeString(config, content);
      assertFalse(StartupTlsSettings.readAllowLegacyRsa(config));
    }
  }

  @Test
  void readsSavedBooleanFromSelectedRuntimeConfig() throws Exception {
    Path config = temp.resolve("custom profile.yml");
    String[] args = {"--ircafe.runtime-config=" + config};
    Files.writeString(config, "irc:\n  client:\n    tls:\n      allowLegacyRsa: true\n");
    assertTrue(StartupTlsSettings.readAllowLegacyRsa(args));
    Files.writeString(config, "irc:\n  client:\n    tls:\n      allowLegacyRsa: false\n");
    assertFalse(StartupTlsSettings.readAllowLegacyRsa(args));
  }

  @Test
  void resolvesExplicitXdgAndHomePathsLikeRuntimeStore() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    env.getPropertySources()
        .addFirst(new MapPropertySource("home", Map.of("user.home", temp.toString())));
    assertEquals(
        temp.resolve(".config/ircafe/ircafe.yml"),
        StartupTlsSettings.resolveRuntimeConfigPath(env));
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "xdg", Map.of("XDG_CONFIG_HOME", temp.resolve("xdg").toString())));
    assertEquals(
        temp.resolve("xdg/ircafe/ircafe.yml"), StartupTlsSettings.resolveRuntimeConfigPath(env));
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "explicit",
                Map.of("ircafe.runtime-config", temp.resolve("custom.yml").toString())));
    assertEquals(temp.resolve("custom.yml"), StartupTlsSettings.resolveRuntimeConfigPath(env));
  }
}
