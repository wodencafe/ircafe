package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStorePushySettingsTest {

  @TempDir Path tempDir;

  @Test
  void pushySettingsArePersistedUnderIrcafePushySection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberPushySettings(
        PushyPropertiesTestFixtures.builder()
            .enabled(true)
            .endpoint("https://api.pushy.me/push")
            .apiKey("api-key-123")
            .deviceToken("device-token-1")
            .titlePrefix("IRCafe")
            .connectTimeoutSeconds(5)
            .readTimeoutSeconds(8)
            .build());

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
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberPushySettings(
        PushyPropertiesTestFixtures.builder()
            .enabled(true)
            .apiKey("api-key-123")
            .topic("alerts")
            .titlePrefix("Office")
            .connectTimeoutSeconds(4)
            .readTimeoutSeconds(9)
            .build());
    store.rememberPushySettings(PushyPropertiesTestFixtures.disabled());

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("enabled: false"));
    assertFalse(yaml.contains("apiKey:"));
    assertFalse(yaml.contains("deviceToken:"));
    assertFalse(yaml.contains("topic:"));
    assertFalse(yaml.contains("titlePrefix:"));
  }
}
