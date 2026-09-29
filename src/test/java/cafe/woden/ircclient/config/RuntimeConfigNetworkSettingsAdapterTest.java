package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.junit.jupiter.api.Test;

class RuntimeConfigNetworkSettingsAdapterTest {

  @Test
  void readsBouncerDefaultsFromRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(runtimeConfig.readGenericBouncerPreferLoginHint(true)).thenReturn(false);
    when(runtimeConfig.readGenericBouncerLoginTemplate("{base}/{network}"))
        .thenReturn("{account}@{network}");
    RuntimeConfigNetworkSettingsAdapter adapter =
        new RuntimeConfigNetworkSettingsAdapter(runtimeConfig);

    assertFalse(adapter.readGenericBouncerPreferLoginHint(true));
    assertEquals(
        "{account}@{network}", adapter.readGenericBouncerLoginTemplate("{base}/{network}"));
  }

  @Test
  void writesNetworkSettingsToRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigNetworkSettingsAdapter adapter =
        new RuntimeConfigNetworkSettingsAdapter(runtimeConfig);
    IrcProperties.Proxy proxy =
        new IrcProperties.Proxy(true, "127.0.0.1", 1080, "user", "pass", true, 10_000, 30_000);
    IrcProperties.Heartbeat heartbeat = new IrcProperties.Heartbeat(true, 15_000, 360_000);

    adapter.rememberClientProxy(proxy);
    adapter.rememberClientHeartbeat(heartbeat);
    adapter.rememberClientFloodProtection(IrcProperties.FloodProtection.defaults());
    adapter.rememberGenericBouncerPreferLoginHint(false);
    adapter.rememberGenericBouncerLoginTemplate("{account}@{network}");
    adapter.rememberClientTlsTrustAllCertificates(true);

    verify(runtimeConfig).rememberClientProxy(proxy);
    verify(runtimeConfig).rememberClientHeartbeat(heartbeat);
    verify(runtimeConfig).rememberClientFloodProtection(IrcProperties.FloodProtection.defaults());
    verify(runtimeConfig).rememberGenericBouncerPreferLoginHint(false);
    verify(runtimeConfig).rememberGenericBouncerLoginTemplate("{account}@{network}");
    verify(runtimeConfig).rememberClientTlsTrustAllCertificates(true);
  }
}
