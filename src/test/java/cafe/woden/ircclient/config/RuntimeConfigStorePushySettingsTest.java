package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStorePushySettingsTest {

  @TempDir
  Path tempDir;

  @Test
  void pushySettingsArePersistedUnderIrcafePushySection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = new RuntimeConfigStore(
        cfg.toString(),
        new IrcProperties(null, List.of()));

    store.rememberPushySettings(new PushyProperties(
        true,
        "https://api.pushy.me/push",
        "api-key-123",
        "device-token-1",
        null,
        "IRCafe",
        5,
        8));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("pushy"));
    assertTrue(yaml.contains("enabled: true"));
    assertTrue(
        yaml.contains("apiKey: api-key-123")
            || yaml.contains("apiKey: 'api-key-123'")
            || yaml.contains("apiKey: \"api-key-123\""));
    assertTrue(
        yaml.contains("deviceToken: device-token-1")
            || yaml.contains("deviceToken: 'device-token-1'")
            || yaml.contains("deviceToken: \"device-token-1\""));
    assertTrue(yaml.contains("connectTimeoutSeconds: 5"));
    assertTrue(yaml.contains("readTimeoutSeconds: 8"));
  }

  @Test
  void blankOptionalPushyFieldsAreRemovedWhenDisabled() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = new RuntimeConfigStore(
        cfg.toString(),
        new IrcProperties(null, List.of()));

    store.rememberPushySettings(new PushyProperties(
        true,
        null,
        "api-key-123",
        null,
        "alerts",
        "Office",
        4,
        9));
    store.rememberPushySettings(new PushyProperties(
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("enabled: false"));
    assertFalse(yaml.contains("apiKey:"));
    assertFalse(yaml.contains("deviceToken:"));
    assertFalse(yaml.contains("topic:"));
    assertFalse(yaml.contains("titlePrefix:"));
  }
}
