package cafe.woden.ircclient.irc.soju;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.SojuProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SojuAutoConnectStoreTest {

  @Test
  void initializesFromPropertiesAndMatchesCaseInsensitively() {
    SojuProperties props =
        new SojuProperties(
            Map.of("soju", Map.of("Libera", true, "OFTC", false)),
            new SojuProperties.Discovery(true));

    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", new IrcProperties(null, List.of()));

    SojuAutoConnectStore store = new SojuAutoConnectStore(props, runtime);

    assertTrue(store.isEnabled("soju", "libera"));
    assertTrue(store.isEnabled("soju", "LIBERA"));
    assertFalse(store.isEnabled("soju", "oftc"));
  }

  @Test
  void setEnabledAddsAndRemovesRules() {
    SojuProperties props = new SojuProperties(Map.of(), new SojuProperties.Discovery(true));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", new IrcProperties(null, List.of()));
    SojuAutoConnectStore store = new SojuAutoConnectStore(props, runtime);

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
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", new IrcProperties(null, List.of()));
    SojuAutoConnectStore store = new SojuAutoConnectStore(props, runtime);

    store.setEnabled("soju", "Libera.Chat!!!", true);

    assertTrue(store.isEnabled("soju", "libera.chat"));
    assertEquals(Map.of("libera.chat", true), store.rulesForBouncer("soju"));
  }
}
