package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerBackendIdTest {

  @TempDir Path tempDir;

  @Test
  void ensureFileExistsWithServersPersistsCustomBackendIds() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            cfg.toString(),
            new IrcProperties(null, List.of(server("plugin-net", "plugin-backend"))));

    store.ensureFileExistsWithServers();

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("backend: plugin-backend"));
  }

  @Test
  void ensureFileExistsWithServersOmitsDefaultIrcBackendId() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            cfg.toString(), new IrcProperties(null, List.of(server("libera", "irc"))));

    store.ensureFileExistsWithServers();

    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("backend: irc"));
  }

  private static IrcProperties.Server server(String id, String backendId) {
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
        null,
        List.of(),
        List.of(),
        null,
        backendId);
  }
}
