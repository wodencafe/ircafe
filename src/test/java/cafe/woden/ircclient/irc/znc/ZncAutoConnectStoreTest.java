package cafe.woden.ircclient.irc.znc;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ZncProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZncAutoConnectStoreTest {

  @Test
  void initializesFromPropertiesAndMatchesCaseInsensitively() {
    ZncProperties props =
        new ZncProperties(
            Map.of("znc", Map.of("Libera", true, "OFTC", false)),
            new ZncProperties.Discovery(true));

    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", new IrcProperties(null, List.of()));

    ZncAutoConnectStore store = new ZncAutoConnectStore(props, runtime);

    assertTrue(store.isEnabled("znc", "libera"));
    assertTrue(store.isEnabled("znc", "LIBERA"));
    assertFalse(store.isEnabled("znc", "oftc"));
  }

  @Test
  void setEnabledAddsAndRemovesRules() {
    ZncProperties props = new ZncProperties(Map.of(), new ZncProperties.Discovery(true));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", new IrcProperties(null, List.of()));
    ZncAutoConnectStore store = new ZncAutoConnectStore(props, runtime);

    assertFalse(store.isEnabled("znc", "libera"));

    store.setEnabled("znc", "libera", true);
    assertTrue(store.isEnabled("znc", "LIBERA"));

    store.setEnabled("znc", "libera", false);
    assertFalse(store.isEnabled("znc", "libera"));
    assertTrue(store.snapshot().isEmpty());
  }

  @Test
  void sanitizesKeysToSafeChars() {
    ZncProperties props = new ZncProperties(Map.of(), new ZncProperties.Discovery(true));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", new IrcProperties(null, List.of()));
    ZncAutoConnectStore store = new ZncAutoConnectStore(props, runtime);

    store.setEnabled("znc", "Libera.Chat!!!", true);

    assertTrue(store.isEnabled("znc", "libera.chat"));
    assertEquals(Map.of("libera.chat", true), store.networksForBouncer("znc"));
  }
}
