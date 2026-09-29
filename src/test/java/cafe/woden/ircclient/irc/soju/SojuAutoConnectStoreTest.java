package cafe.woden.ircclient.irc.soju;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.bouncerDiscoveryPort;
import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SojuAutoConnectStoreTest {

  @Test
  void initializesFromPropertiesAndMatchesCaseInsensitively() {
    SojuProperties props =
        new SojuProperties(
            Map.of("soju", Map.of("Libera", true, "OFTC", false)),
            new SojuProperties.Discovery(true));

    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore();

    SojuAutoConnectStore store = new SojuAutoConnectStore(props, bouncerDiscoveryPort(runtime));

    assertTrue(store.isEnabled("soju", "libera"));
    assertTrue(store.isEnabled("soju", "LIBERA"));
    assertFalse(store.isEnabled("soju", "oftc"));
  }

  @Test
  void setEnabledAddsAndRemovesRules() {
    SojuProperties props = new SojuProperties(Map.of(), new SojuProperties.Discovery(true));
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore();
    SojuAutoConnectStore store = new SojuAutoConnectStore(props, bouncerDiscoveryPort(runtime));

    assertFalse(store.isEnabled("soju", "libera"));

    store.setEnabled("soju", "libera", true);
    assertTrue(store.isEnabled("soju", "LIBERA"));

    store.setEnabled("soju", "libera", false);
    assertFalse(store.isEnabled("soju", "libera"));
    assertTrue(store.snapshot().isEmpty());
  }

  @Test
  void sanitizesKeysToSafeChars() {
    SojuProperties props = new SojuProperties(Map.of(), new SojuProperties.Discovery(true));
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore();
    SojuAutoConnectStore store = new SojuAutoConnectStore(props, bouncerDiscoveryPort(runtime));

    store.setEnabled("soju", "Libera.Chat!!!", true);

    assertTrue(store.isEnabled("soju", "libera.chat"));
    assertEquals(Map.of("libera.chat", true), store.rulesForBouncer("soju"));
  }
}
