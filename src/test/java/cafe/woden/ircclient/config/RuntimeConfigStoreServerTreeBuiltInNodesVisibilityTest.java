package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerTreeBuiltInNodesVisibilityTest {

  @TempDir
  Path tempDir;

  @Test
  void builtInNodeVisibilityRoundTripsAndDefaultEntriesAreRemoved() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            cfg.toString(),
            new IrcProperties(null, List.of(server("libera"), server("oftc"))));

    assertEquals(Map.of(), store.readServerTreeBuiltInNodesVisibility());

    RuntimeConfigStore.ServerTreeBuiltInNodesVisibility hidden =
        new RuntimeConfigStore.ServerTreeBuiltInNodesVisibility(false, false, true, true, false);
    store.rememberServerTreeBuiltInNodesVisibility("libera", hidden);

    Map<String, RuntimeConfigStore.ServerTreeBuiltInNodesVisibility> persisted =
        store.readServerTreeBuiltInNodesVisibility();
    assertEquals(1, persisted.size());
    assertEquals(hidden, persisted.get("libera"));

    store.rememberServerTreeBuiltInNodesVisibility(
        "libera",
        RuntimeConfigStore.ServerTreeBuiltInNodesVisibility.defaults());

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

    RuntimeConfigStore store = new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    Map<String, RuntimeConfigStore.ServerTreeBuiltInNodesVisibility> persisted =
        store.readServerTreeBuiltInNodesVisibility();
    RuntimeConfigStore.ServerTreeBuiltInNodesVisibility visibility = persisted.get("libera");

    assertTrue(visibility.server());
    assertFalse(visibility.notifications());
    assertTrue(visibility.logViewer());
    assertTrue(visibility.monitor());
    assertTrue(visibility.interceptors());
  }

  private static IrcProperties.Server server(String id) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        List.of(),
        List.of(),
        null);
  }
}
