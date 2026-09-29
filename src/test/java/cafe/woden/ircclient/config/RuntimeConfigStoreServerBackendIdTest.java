package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreServerBackendIdTest {

  @TempDir Path tempDir;

  @Test
  void ensureFileExistsWithServersPersistsCustomBackendIds() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        RuntimeConfigStoreTestFixtures.storeWithServers(
            cfg, server("plugin-net", "plugin-backend"));

    store.ensureFileExistsWithServers();

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("backend: plugin-backend"));
  }

  @Test
  void ensureFileExistsWithServersOmitsDefaultIrcBackendId() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        RuntimeConfigStoreTestFixtures.storeWithServers(cfg, server("libera", "irc"));

    store.ensureFileExistsWithServers();

    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("backend: irc"));
  }

  private static IrcProperties.Server server(String id, String backendId) {
    return IrcPropertiesTestFixtures.serverBuilder(id).backendId(backendId).build();
  }
}
