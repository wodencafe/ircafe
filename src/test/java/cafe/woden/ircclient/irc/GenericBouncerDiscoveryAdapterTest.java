package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import org.junit.jupiter.api.Test;

class GenericBouncerDiscoveryAdapterTest {

  private final GenericBouncerDiscoveryAdapter adapter = new GenericBouncerDiscoveryAdapter();

  @Test
  void parsesLoginHintCapabilitiesAndBackend() {
    BouncerDiscoveredNetwork network =
        adapter.parseNetworkLine(
            "libera",
            ":srv BOUNCER NETWORK netA backend=Generic;name=Libera;auto=lib;loginUser=alice/lib;caps=message-tags,draft/react");

    assertNotNull(network);
    assertEquals("generic", network.backendId());
    assertEquals("libera", network.originServerId());
    assertEquals("netA", network.networkId());
    assertEquals("Libera", network.displayName());
    assertEquals("lib", network.autoConnectName());
    assertEquals("alice/lib", network.loginUserHint());
    assertTrue(network.hasCapability("message-tags"));
    assertTrue(network.hasCapability("draft/react"));
  }

  @Test
  void defaultsBackendDisplayAndAutoConnectWhenMissing() {
    BouncerDiscoveredNetwork network =
        adapter.parseNetworkLine("bouncer-1", ":srv BOUNCER NETWORK netB");

    assertNotNull(network);
    assertEquals("generic", network.backendId());
    assertEquals("netB", network.displayName());
    assertEquals("netB", network.autoConnectName());
  }

  @Test
  void returnsNullForNonDiscoveryLine() {
    assertNull(adapter.parseNetworkLine("libera", ":srv NOTICE me :hello"));
  }
}
