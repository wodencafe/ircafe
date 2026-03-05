package cafe.woden.ircclient.bouncer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BouncerDiscoveredNetworkTest {

  @Test
  void legacyConstructorNormalizesAndBackfillsHintsFromAttributes() {
    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "  GENERIC  ",
            "  libera  ",
            "  net-1  ",
            "  Libera Chat  ",
            " ",
            Map.of("loginUser", "alice/lib", "capabilities", "message-tags,draft/react"));

    assertEquals("generic", network.backendId());
    assertEquals("libera", network.originServerId());
    assertEquals("net-1", network.networkId());
    assertEquals("Libera Chat", network.displayName());
    assertEquals("Libera Chat", network.autoConnectName());
    assertEquals("alice/lib", network.loginUserHint());
    assertTrue(network.hasCapability("message-tags"));
    assertTrue(network.hasCapability("DRAFT/REACT"));
  }

  @Test
  void explicitHintsOverrideAttributeFallbacks() {
    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "generic",
            "libera",
            "net-2",
            "Libera",
            "Libera",
            "hint-user",
            Set.of("multi-line", "CHATHISTORY"),
            Map.of("loginUser", "attr-user", "caps", "message-tags"));

    assertEquals("hint-user", network.loginUserHint());
    assertTrue(network.hasCapability("multi-line"));
    assertTrue(network.hasCapability("chathistory"));
    assertFalse(network.hasCapability("message-tags"));
  }

  @Test
  void missingRequiredFieldsThrow() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BouncerDiscoveredNetwork(" ", "origin", "net", "name", "name", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BouncerDiscoveredNetwork("generic", " ", "net", "name", "name", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BouncerDiscoveredNetwork("generic", "origin", " ", "name", "name", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BouncerDiscoveredNetwork("generic", "origin", "net", " ", "name", Map.of()));
  }
}
