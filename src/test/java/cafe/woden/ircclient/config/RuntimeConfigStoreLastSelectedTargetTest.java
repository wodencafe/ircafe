package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.SelectedTargetRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreLastSelectedTargetTest {

  @TempDir Path tempDir;

  @Test
  void lastSelectedTargetCanBePersistedAndReadBack() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    store.rememberLastSelectedTarget("libera", "#ircafe");

    SelectedTargetRuntimeConfigPort.LastSelectedTarget selected =
        store.readLastSelectedTarget().orElseThrow();
    assertEquals("libera", selected.serverId());
    assertEquals("#ircafe", selected.target());
  }

  @Test
  void blankSelectionClearsPersistedLastSelectedTarget() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    store.rememberLastSelectedTarget("libera", "#ircafe");
    assertTrue(store.readLastSelectedTarget().isPresent());

    store.rememberLastSelectedTarget("   ", " ");
    assertTrue(store.readLastSelectedTarget().isEmpty());
  }

  @Test
  void invalidPersistedLastSelectedTargetIsIgnored() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        ircafe:
          ui:
            lastSelectedTarget:
              serverId: "libera"
              target: ""
        """);

    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    assertFalse(store.readLastSelectedTarget().isPresent());
  }
}
