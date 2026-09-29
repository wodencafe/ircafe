package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerTreeBuiltInLayoutTest {

  @TempDir Path tempDir;

  @Test
  void builtInLayoutRoundTripsAndDefaultEntriesAreRemoved() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        RuntimeConfigStoreTestFixtures.storeWithServers(cfg, server("libera"), server("oftc"));

    assertEquals(Map.of(), store.readServerTreeBuiltInLayoutByServer());

    ServerTreeBuiltInLayout layout =
        new ServerTreeBuiltInLayout(
            List.of(ServerTreeBuiltInLayoutNode.MONITOR, ServerTreeBuiltInLayoutNode.SERVER),
            List.of(
                ServerTreeBuiltInLayoutNode.NOTIFICATIONS,
                ServerTreeBuiltInLayoutNode.LOG_VIEWER,
                ServerTreeBuiltInLayoutNode.FILTERS,
                ServerTreeBuiltInLayoutNode.IGNORES,
                ServerTreeBuiltInLayoutNode.INTERCEPTORS));
    store.rememberServerTreeBuiltInLayout("libera", layout);

    Map<String, ServerTreeBuiltInLayout> persisted = store.readServerTreeBuiltInLayoutByServer();
    assertEquals(1, persisted.size());
    assertEquals(layout, persisted.get("libera"));

    store.rememberServerTreeBuiltInLayout("libera", ServerTreeBuiltInLayout.defaults());
    assertEquals(Map.of(), store.readServerTreeBuiltInLayoutByServer());

    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("builtInLayoutByServer"));
  }

  @Test
  void missingOrUnknownLayoutEntriesAreNormalizedAndCompleted() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        "ircafe:\n"
            + "  ui:\n"
            + "    serverTree:\n"
            + "      builtInLayoutByServer:\n"
            + "        libera:\n"
            + "          root:\n"
            + "            - monitor\n"
            + "            - server\n"
            + "            - monitor\n"
            + "            - unknown-node\n"
            + "          other:\n"
            + "            - filters\n");

    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    Map<String, ServerTreeBuiltInLayout> persisted = store.readServerTreeBuiltInLayoutByServer();

    ServerTreeBuiltInLayout expected =
        new ServerTreeBuiltInLayout(
            List.of(ServerTreeBuiltInLayoutNode.MONITOR, ServerTreeBuiltInLayoutNode.SERVER),
            List.of(
                ServerTreeBuiltInLayoutNode.FILTERS,
                ServerTreeBuiltInLayoutNode.NOTIFICATIONS,
                ServerTreeBuiltInLayoutNode.LOG_VIEWER,
                ServerTreeBuiltInLayoutNode.IGNORES,
                ServerTreeBuiltInLayoutNode.INTERCEPTORS));
    assertEquals(expected, persisted.get("libera"));
  }

  private static IrcProperties.Server server(String id) {
    return IrcPropertiesTestFixtures.server(id);
  }
}
