package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort.EmbedLoadPolicyScope;
import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort.EmbedLoadPolicySnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreEmbedLoadPolicyTest {

  @TempDir Path tempDir;

  @Test
  void embedLoadPolicyRoundTripsAndDefaultOverridesAreRemoved() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            cfg.toString(), new IrcProperties(null, List.of(server("libera"), server("oftc"))));

    assertEquals(EmbedLoadPolicySnapshot.defaults(), store.readEmbedLoadPolicy());

    EmbedLoadPolicyScope global =
        new EmbedLoadPolicyScope(
            List.of("nick:trusted*", "host:*.trusted.net"),
            List.of("nick:re:^spam.*"),
            List.of("#safe*"),
            List.of("#danger*"),
            true,
            true,
            14,
            List.of("https://example.com/*"),
            List.of("re:evil"),
            List.of("*.imgur.com"),
            List.of("bad.example"));

    EmbedLoadPolicyScope libera =
        new EmbedLoadPolicyScope(
            List.of("nick:libera-friend"),
            List.of(),
            List.of(),
            List.of("#ops"),
            false,
            true,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of("tracker.example"));

    EmbedLoadPolicySnapshot snapshot =
        new EmbedLoadPolicySnapshot(
            global,
            Map.of(
                "libera",
                libera,
                // Default scope entries are dropped when normalized.
                "oftc",
                EmbedLoadPolicyScope.defaults()));

    store.rememberEmbedLoadPolicy(snapshot);

    EmbedLoadPolicySnapshot readBack = store.readEmbedLoadPolicy();
    assertEquals(global, readBack.global());
    assertEquals(Map.of("libera", libera), readBack.byServer());

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("embedLoadPolicy"));

    store.rememberEmbedLoadPolicy(EmbedLoadPolicySnapshot.defaults());
    assertEquals(EmbedLoadPolicySnapshot.defaults(), store.readEmbedLoadPolicy());
    assertFalse(Files.readString(cfg).contains("embedLoadPolicy"));
  }

  @Test
  void invalidEmbedLoadPolicyEntriesAreIgnored() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        "ircafe:\n"
            + "  ui:\n"
            + "    embedLoadPolicy:\n"
            + "      global:\n"
            + "        userWhitelist: not-a-list\n"
            + "        requireLoggedIn: true\n"
            + "      byServer:\n"
            + "        libera: definitely-not-a-map\n");

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of(server("libera"))));

    EmbedLoadPolicySnapshot policy = store.readEmbedLoadPolicy();
    assertTrue(policy.global().requireLoggedIn());
    assertEquals(List.of(), policy.global().userWhitelist());
    assertEquals(Map.of(), policy.byServer());
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
