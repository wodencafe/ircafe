package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerAutoConnectOnStartTest {

  @TempDir Path tempDir;

  @Test
  void serverAutoConnectOnStartRoundTripsAndDefaultEntriesAreRemoved() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        RuntimeConfigStoreTestFixtures.storeWithServers(cfg, server("libera"), server("oftc"));

    assertEquals(Map.of(), store.readServerAutoConnectOnStartByServer());
    assertTrue(store.readServerAutoConnectOnStart("libera", true));
    assertTrue(store.readServerAutoConnectOnStart("oftc", true));

    store.rememberServerAutoConnectOnStart("libera", false);

    assertEquals(Map.of("libera", false), store.readServerAutoConnectOnStartByServer());
    assertFalse(store.readServerAutoConnectOnStart("libera", true));
    assertTrue(store.readServerAutoConnectOnStart("oftc", true));

    store.rememberServerAutoConnectOnStart("libera", true);

    assertEquals(Map.of(), store.readServerAutoConnectOnStartByServer());
    assertTrue(store.readServerAutoConnectOnStart("libera", true));

    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("serverAutoConnectOnStartByServer"));
  }

  @Test
  void invalidEntriesAreIgnoredAndFallBackToDefaults() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        "ircafe:\n"
            + "  ui:\n"
            + "    serverAutoConnectOnStartByServer:\n"
            + "      libera: false\n"
            + "      oftc: definitely-not-bool\n");

    RuntimeConfigStore store =
        RuntimeConfigStoreTestFixtures.storeWithServers(cfg, server("libera"));

    assertEquals(Map.of("libera", false), store.readServerAutoConnectOnStartByServer());
    assertFalse(store.readServerAutoConnectOnStart("libera", true));
    assertTrue(store.readServerAutoConnectOnStart("oftc", true));
  }

  private static IrcProperties.Server server(String id) {
    return IrcPropertiesTestFixtures.server(id);
  }
}
