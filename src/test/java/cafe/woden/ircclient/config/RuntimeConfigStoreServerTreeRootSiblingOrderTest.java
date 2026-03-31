package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerTreeRootSiblingOrderTest {

  @TempDir Path tempDir;

  @Test
  void rootSiblingOrderRoundTripsAndDefaultEntriesAreRemoved() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            cfg.toString(), new IrcProperties(null, List.of(server("libera"), server("oftc"))));

    assertEquals(Map.of(), store.readServerTreeRootSiblingOrderByServer());

    ServerTreeRootSiblingOrder order =
        new ServerTreeRootSiblingOrder(
            List.of(
                ServerTreeRootSiblingNode.OTHER,
                ServerTreeRootSiblingNode.PRIVATE_MESSAGES,
                ServerTreeRootSiblingNode.CHANNEL_LIST,
                ServerTreeRootSiblingNode.NOTIFICATIONS));
    store.rememberServerTreeRootSiblingOrder("libera", order);

    Map<String, ServerTreeRootSiblingOrder> persisted =
        store.readServerTreeRootSiblingOrderByServer();
    assertEquals(1, persisted.size());
    assertEquals(order, persisted.get("libera"));

    store.rememberServerTreeRootSiblingOrder("libera", ServerTreeRootSiblingOrder.defaults());
    assertEquals(Map.of(), store.readServerTreeRootSiblingOrderByServer());

    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("rootSiblingOrderByServer"));
  }

  @Test
  void missingOrUnknownRootSiblingEntriesAreNormalizedAndCompleted() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        "ircafe:\n"
            + "  ui:\n"
            + "    serverTree:\n"
            + "      rootSiblingOrderByServer:\n"
            + "        libera:\n"
            + "          - other\n"
            + "          - privateMessages\n"
            + "          - other\n"
            + "          - unknown-node\n");

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    Map<String, ServerTreeRootSiblingOrder> persisted =
        store.readServerTreeRootSiblingOrderByServer();

    ServerTreeRootSiblingOrder expected =
        new ServerTreeRootSiblingOrder(
            List.of(
                ServerTreeRootSiblingNode.OTHER,
                ServerTreeRootSiblingNode.PRIVATE_MESSAGES,
                ServerTreeRootSiblingNode.CHANNEL_LIST,
                ServerTreeRootSiblingNode.NOTIFICATIONS));
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
