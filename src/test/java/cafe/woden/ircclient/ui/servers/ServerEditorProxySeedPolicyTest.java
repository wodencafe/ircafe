package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import org.junit.jupiter.api.Test;

class ServerEditorProxySeedPolicyTest {

  @Test
  void serverSpecificProxyTakesPrecedenceOverGlobalSettings() {
    ServerEditorProxySeedPolicy.ProxySeedState state =
        ServerEditorProxySeedPolicy.seedState(
            new IrcProperties.Proxy(
                true, "server.proxy", 1443, "alice", "secret", false, 10_000, 20_000),
            new IrcProperties.Proxy(
                true, "global.proxy", 1080, "bob", "global", true, 30_000, 40_000));

    assertTrue(state.overrideSelected());
    assertTrue(state.proxyEnabled());
    assertEquals("server.proxy", state.host());
    assertEquals("1443", state.portText());
    assertFalse(state.remoteDns());
    assertEquals("alice", state.username());
    assertEquals("secret", state.password());
    assertEquals("10000", state.connectTimeoutMsText());
    assertEquals("20000", state.readTimeoutMsText());
  }

  @Test
  void globalProxySeedsInheritedValuesWhenNoOverrideExists() {
    ServerEditorProxySeedPolicy.ProxySeedState state =
        ServerEditorProxySeedPolicy.seedState(
            null,
            new IrcProperties.Proxy(
                false, "global.proxy", 1080, "bob", "global", true, 30_000, 40_000));

    assertFalse(state.overrideSelected());
    assertFalse(state.proxyEnabled());
    assertEquals("global.proxy", state.host());
    assertEquals("1080", state.portText());
    assertTrue(state.remoteDns());
    assertEquals("bob", state.username());
    assertEquals("global", state.password());
    assertEquals("30000", state.connectTimeoutMsText());
    assertEquals("40000", state.readTimeoutMsText());
  }
}
