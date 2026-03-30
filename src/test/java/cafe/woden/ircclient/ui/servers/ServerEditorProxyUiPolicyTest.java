package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import org.junit.jupiter.api.Test;

class ServerEditorProxyUiPolicyTest {

  @Test
  void inheritDisabledGlobalProxyDisablesOverrideFields() {
    ServerEditorProxyUiPolicy.ProxyUiState state =
        ServerEditorProxyUiPolicy.uiState(
            false, true, new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000));

    assertTrue(state.hint().contains("disabled"));
    assertFalse(state.proxyEnabledToggleEnabled());
    assertFalse(state.proxyDetailsEnabled());
    assertFalse(state.connectTimeoutEnabled());
    assertFalse(state.readTimeoutEnabled());
  }

  @Test
  void overrideWithProxyDisabledKeepsTimeoutsButNotProxyDetailsEnabled() {
    ServerEditorProxyUiPolicy.ProxyUiState state =
        ServerEditorProxyUiPolicy.uiState(
            true,
            false,
            new IrcProperties.Proxy(true, "proxy.example.org", 1080, "", "", true, 20_000, 30_000));

    assertTrue(state.hint().contains("Override the global proxy"));
    assertTrue(state.proxyEnabledToggleEnabled());
    assertFalse(state.proxyDetailsEnabled());
    assertTrue(state.connectTimeoutEnabled());
    assertTrue(state.readTimeoutEnabled());
  }

  @Test
  void overrideWithProxyEnabledEnablesAllProxyControls() {
    ServerEditorProxyUiPolicy.ProxyUiState state =
        ServerEditorProxyUiPolicy.uiState(
            true,
            true,
            new IrcProperties.Proxy(true, "proxy.example.org", 1080, "", "", true, 20_000, 30_000));

    assertTrue(state.proxyEnabledToggleEnabled());
    assertTrue(state.proxyDetailsEnabled());
    assertTrue(state.remoteDnsEnabled());
    assertTrue(state.connectTimeoutEnabled());
    assertTrue(state.readTimeoutEnabled());
  }
}
