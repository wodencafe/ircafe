package cafe.woden.ircclient.bouncer;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.bouncerDiscoveryPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericBouncerAutoConnectStoreTest {

  @TempDir Path tempDir;

  @Test
  void initializesFromRuntimeConfigAndMatchesCaseInsensitively() {
    RuntimeConfigStore runtime = runtimeConfig();
    runtime.rememberGenericBouncerAutoConnectNetwork("bouncer-main", "Libera", true);

    GenericBouncerAutoConnectStore store =
        new GenericBouncerAutoConnectStore(bouncerDiscoveryPort(runtime));

    assertTrue(store.isEnabled("bouncer-main", "libera"));
    assertTrue(store.isEnabled("BOUNCER-MAIN", "LIBERA"));
    assertFalse(store.isEnabled("bouncer-main", "oftc"));
  }

  @Test
  void setEnabledAddsRemovesAndPersistsRules() {
    RuntimeConfigStore runtime = runtimeConfig();
    GenericBouncerAutoConnectStore store =
        new GenericBouncerAutoConnectStore(bouncerDiscoveryPort(runtime));

    assertFalse(store.isEnabled("bouncer-main", "libera"));

    store.setEnabled("bouncer-main", "Lib Era", true);
    assertTrue(store.isEnabled("bouncer-main", "lib_era"));
    assertEquals(Map.of("lib_era", true), store.networksForBouncer("bouncer-main"));

    RuntimeConfigStore reloadedRuntime = runtimeConfig();
    GenericBouncerAutoConnectStore reloaded =
        new GenericBouncerAutoConnectStore(bouncerDiscoveryPort(reloadedRuntime));
    assertTrue(reloaded.isEnabled("bouncer-main", "lib_era"));

    store.setEnabled("bouncer-main", "lib era", false);
    assertFalse(store.isEnabled("bouncer-main", "lib_era"));
    assertTrue(store.snapshot().isEmpty());
    assertTrue(runtime.readGenericBouncerAutoConnectRules().isEmpty());
  }

  private RuntimeConfigStore runtimeConfig() {
    return RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));
  }
}
