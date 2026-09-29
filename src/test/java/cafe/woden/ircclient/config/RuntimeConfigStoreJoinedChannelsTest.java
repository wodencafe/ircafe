package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreJoinedChannelsTest {

  @TempDir Path tempDir;

  @Test
  void readJoinedChannelsFiltersPrivateMessageEntriesAndDeduplicatesByCase() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
              autoJoin:
                - "#Alpha"
                - "query:Alice"
                - "#beta"
                - "#alpha"
                - "QUERY:bob"
                - "&help"
        """);

    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    assertEquals(List.of("#Alpha", "#beta", "&help"), store.readJoinedChannels("libera"));
  }

  @Test
  void readJoinedChannelsReturnsEmptyWhenServerOrAutoJoinIsMissing() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("missing.yml"));

    assertEquals(List.of(), store.readJoinedChannels("libera"));
  }

  @Test
  void readJoinedChannelsReturnsEmptyWhenAutoJoinShapeIsMalformed() throws Exception {
    Path cfg = tempDir.resolve("malformed.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
              autoJoin: 42
        """);

    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    assertEquals(List.of(), store.readJoinedChannels("libera"));
  }
}
