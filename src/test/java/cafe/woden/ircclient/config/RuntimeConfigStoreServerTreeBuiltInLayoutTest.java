package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        new RuntimeConfigStore(
            cfg.toString(), new IrcProperties(null, List.of(server("libera"), server("oftc"))));

    assertEquals(Map.of(), store.readServerTreeBuiltInLayoutByServer());

    RuntimeConfigStore.ServerTreeBuiltInLayout layout =
        new RuntimeConfigStore.ServerTreeBuiltInLayout(
            List.of(
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.MONITOR,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER),
            List.of(
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.NOTIFICATIONS,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.LOG_VIEWER,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.FILTERS,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.IGNORES,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.INTERCEPTORS));
    store.rememberServerTreeBuiltInLayout("libera", layout);

    Map<String, RuntimeConfigStore.ServerTreeBuiltInLayout> persisted =
        store.readServerTreeBuiltInLayoutByServer();
    assertEquals(1, persisted.size());
    assertEquals(layout, persisted.get("libera"));

    store.rememberServerTreeBuiltInLayout(
        "libera", RuntimeConfigStore.ServerTreeBuiltInLayout.defaults());
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

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    Map<String, RuntimeConfigStore.ServerTreeBuiltInLayout> persisted =
        store.readServerTreeBuiltInLayoutByServer();

    RuntimeConfigStore.ServerTreeBuiltInLayout expected =
        new RuntimeConfigStore.ServerTreeBuiltInLayout(
            List.of(
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.MONITOR,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER),
            List.of(
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.FILTERS,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.NOTIFICATIONS,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.LOG_VIEWER,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.IGNORES,
                RuntimeConfigStore.ServerTreeBuiltInLayoutNode.INTERCEPTORS));
    assertEquals(expected, persisted.get("libera"));
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
