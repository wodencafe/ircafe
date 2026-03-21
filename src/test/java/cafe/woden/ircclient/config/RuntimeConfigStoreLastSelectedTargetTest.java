package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreLastSelectedTargetTest {

  @TempDir Path tempDir;

  @Test
  void lastSelectedTargetCanBePersistedAndReadBack() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberLastSelectedTarget("libera", "#ircafe");

    UiShellRuntimeConfigPort.LastSelectedTarget selected =
        store.readLastSelectedTarget().orElseThrow();
    assertEquals("libera", selected.serverId());
    assertEquals("#ircafe", selected.target());
  }

  @Test
  void blankSelectionClearsPersistedLastSelectedTarget() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

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

    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertFalse(store.readLastSelectedTarget().isPresent());
  }
}
