package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreTimestampSettingsTest {

  @TempDir Path tempDir;

  @Test
  void persistsTimestampSettingsUnderNestedTimestampsAndRemovesLegacyFlatKey() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        ircafe:
          ui:
            chatMessageTimestampsEnabled: false
        """);
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberTimestampsEnabled(false);
    store.rememberTimestampFormat(" HH:mm ");
    store.rememberTimestampsIncludeChatMessages(true);
    store.rememberTimestampsIncludePresenceMessages(false);

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("timestamps:"));
    assertTrue(yaml.contains("enabled: false"));
    assertTrue(yaml.contains("format: HH:mm"));
    assertTrue(yaml.contains("includeChatMessages: true"));
    assertTrue(yaml.contains("includePresenceMessages: false"));
    assertFalse(yaml.contains("chatMessageTimestampsEnabled"));
  }
}
