package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort.ServerTreeBuiltInNodesVisibility;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerTreeBuiltInNodesVisibilityTest {

  @TempDir Path tempDir;

  @Test
  void builtInNodeVisibilityRoundTripsAndDefaultEntriesAreRemoved() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        RuntimeConfigStoreTestFixtures.storeWithServers(cfg, server("libera"), server("oftc"));

    assertEquals(Map.of(), store.readServerTreeBuiltInNodesVisibility());

    ServerTreeBuiltInNodesVisibility hidden =
        new ServerTreeBuiltInNodesVisibility(false, false, true, true, false);
    store.rememberServerTreeBuiltInNodesVisibility("libera", hidden);

    Map<String, ServerTreeBuiltInNodesVisibility> persisted =
        store.readServerTreeBuiltInNodesVisibility();
    assertEquals(1, persisted.size());
    assertEquals(hidden, persisted.get("libera"));

    store.rememberServerTreeBuiltInNodesVisibility(
        "libera", ServerTreeBuiltInNodesVisibility.defaults());

    assertEquals(Map.of(), store.readServerTreeBuiltInNodesVisibility());
    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("builtInNodesByServer"));
  }

  @Test
  void missingFieldsDefaultToVisibleWhenReading() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        "ircafe:\n"
            + "  ui:\n"
            + "    serverTree:\n"
            + "      builtInNodesByServer:\n"
            + "        libera:\n"
            + "          notifications: false\n");

    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    Map<String, ServerTreeBuiltInNodesVisibility> persisted =
        store.readServerTreeBuiltInNodesVisibility();
    ServerTreeBuiltInNodesVisibility visibility = persisted.get("libera");

    assertTrue(visibility.server());
    assertFalse(visibility.notifications());
    assertTrue(visibility.logViewer());
    assertTrue(visibility.monitor());
    assertTrue(visibility.interceptors());
  }

  private static IrcProperties.Server server(String id) {
    return IrcPropertiesTestFixtures.server(id);
  }
}
