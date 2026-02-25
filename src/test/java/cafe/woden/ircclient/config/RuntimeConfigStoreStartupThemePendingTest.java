package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreStartupThemePendingTest {

  @TempDir Path tempDir;

  @Test
  void startupThemePendingCanBePersistedReadAndCleared() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertEquals(Optional.empty(), store.readStartupThemePending());

    store.rememberStartupThemePending("darcula");
    assertEquals(Optional.of("darcula"), store.readStartupThemePending());
    assertTrue(Files.readString(cfg).contains("startupThemePending: darcula"));

    store.clearStartupThemePending();
    assertEquals(Optional.empty(), store.readStartupThemePending());
    assertFalse(Files.readString(cfg).contains("startupThemePending"));
  }

  @Test
  void blankPendingThemeRemovesPersistedKey() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberStartupThemePending("  darklaf  ");
    assertEquals(Optional.of("darklaf"), store.readStartupThemePending());

    store.rememberStartupThemePending("   ");
    assertEquals(Optional.empty(), store.readStartupThemePending());
    assertFalse(Files.readString(cfg).contains("startupThemePending"));
  }
}
