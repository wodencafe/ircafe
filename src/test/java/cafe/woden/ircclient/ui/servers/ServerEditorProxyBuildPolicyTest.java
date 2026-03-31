package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import org.junit.jupiter.api.Test;

class ServerEditorProxyBuildPolicyTest {

  @Test
  void noOverrideReturnsNull() {
    IrcProperties.Proxy proxy =
        ServerEditorProxyBuildPolicy.buildOverride(
            false, true, "proxy.example.org", "1080", "", "", true, "10000", "20000");

    assertNull(proxy);
  }

  @Test
  void disabledOverrideUsesDefaultTimeouts() {
    IrcProperties.Proxy proxy =
        ServerEditorProxyBuildPolicy.buildOverride(true, false, "", "", "", "", false, "", "-1");

    assertNotNull(proxy);
    assertFalse(proxy.enabled());
    assertEquals(20_000, proxy.connectTimeoutMs());
    assertEquals(30_000, proxy.readTimeoutMs());
    assertTrue(proxy.remoteDns());
  }

  @Test
  void enabledOverrideParsesFields() {
    IrcProperties.Proxy proxy =
        ServerEditorProxyBuildPolicy.buildOverride(
            true,
            true,
            " proxy.example.org ",
            "1080",
            " alice ",
            "secret",
            false,
            "15000",
            "25000");

    assertNotNull(proxy);
    assertTrue(proxy.enabled());
    assertEquals("proxy.example.org", proxy.host());
    assertEquals(1080, proxy.port());
    assertEquals("alice", proxy.username());
    assertEquals("secret", proxy.password());
    assertFalse(proxy.remoteDns());
    assertEquals(15_000, proxy.connectTimeoutMs());
    assertEquals(25_000, proxy.readTimeoutMs());
  }

  @Test
  void enabledOverrideRejectsNonNumericPort() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ServerEditorProxyBuildPolicy.buildOverride(
                    true, true, "proxy.example.org", "abc", "", "", true, "", ""));

    assertEquals("Proxy port must be a number", error.getMessage());
  }
}
