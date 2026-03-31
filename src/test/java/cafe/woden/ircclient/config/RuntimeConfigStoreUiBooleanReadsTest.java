package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreUiBooleanReadsTest {

  @TempDir Path tempDir;

  @Test
  void trayCloseToTrayIsOptionalUntilPersisted() {
    RuntimeConfigStore store = newStore();

    assertEquals(Optional.empty(), store.readTrayCloseToTrayIfPresent());

    store.rememberTrayCloseToTray(true);
    assertEquals(Optional.of(true), store.readTrayCloseToTrayIfPresent());

    store.rememberTrayCloseToTray(false);
    assertEquals(Optional.of(false), store.readTrayCloseToTrayIfPresent());
  }

  @Test
  void inviteAutoJoinFallsBackUntilPersisted() {
    RuntimeConfigStore store = newStore();

    assertTrue(store.readInviteAutoJoinEnabled(true));
    assertFalse(store.readInviteAutoJoinEnabled(false));

    store.rememberInviteAutoJoinEnabled(false);
    assertFalse(store.readInviteAutoJoinEnabled(true));

    store.rememberInviteAutoJoinEnabled(true);
    assertTrue(store.readInviteAutoJoinEnabled(false));
  }

  @Test
  void updateNotifierFallsBackUntilPersisted() {
    RuntimeConfigStore store = newStore();

    assertTrue(store.readUpdateNotifierEnabled(true));
    assertFalse(store.readUpdateNotifierEnabled(false));

    store.rememberUpdateNotifierEnabled(false);
    assertFalse(store.readUpdateNotifierEnabled(true));

    store.rememberUpdateNotifierEnabled(true);
    assertTrue(store.readUpdateNotifierEnabled(false));
  }

  private RuntimeConfigStore newStore() {
    return new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));
  }
}
