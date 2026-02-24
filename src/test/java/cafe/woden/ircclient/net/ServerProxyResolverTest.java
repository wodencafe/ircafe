package cafe.woden.ircclient.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerProxyResolverTest {

  private static final IrcProperties.Proxy DEFAULT_PROXY =
      new IrcProperties.Proxy(true, "default.proxy", 1080, "", "", true, 20_000, 30_000);

  private static final IrcProperties.Proxy DIRECT =
      new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000);

  @BeforeEach
  void setUp() {
    NetProxyContext.configure(DEFAULT_PROXY);
  }

  @AfterEach
  void tearDown() {
    NetProxyContext.configure(DIRECT);
  }

  @Test
  void fallsBackToDefaultWhenServerHasNoOverride() {
    ServerCatalog catalog = mock(ServerCatalog.class);
    when(catalog.find("libera")).thenReturn(Optional.empty());

    ServerProxyResolver resolver = new ServerProxyResolver(catalog);

    assertSame(DEFAULT_PROXY, resolver.effectiveProxy("libera"));
    assertSame(DEFAULT_PROXY, resolver.effectiveProxy("  "));
    verify(catalog, never()).find("  ");
  }

  @Test
  void usesServerOverrideWhenPresent() {
    IrcProperties.Proxy override =
        new IrcProperties.Proxy(true, "override.proxy", 9050, "", "", true, 11_111, 22_222);
    IrcProperties.Server server = server("libera", override);
    ServerCatalog catalog = mock(ServerCatalog.class);
    when(catalog.find("libera")).thenReturn(Optional.of(server));

    ServerProxyResolver resolver = new ServerProxyResolver(catalog);
    ProxyPlan plan = resolver.planForServer("libera");

    assertSame(override, resolver.effectiveProxy("libera"));
    assertTrue(plan.enabled());
    assertEquals(Proxy.Type.SOCKS, plan.proxy().type());
    InetSocketAddress address = (InetSocketAddress) plan.proxy().address();
    assertEquals("override.proxy", address.getHostString());
    assertEquals(9050, address.getPort());
    assertEquals(11_111, plan.connectTimeoutMs());
    assertEquals(22_222, plan.readTimeoutMs());
  }

  @Test
  void explicitDisabledServerOverrideBeatsEnabledDefault() {
    IrcProperties.Proxy override =
        new IrcProperties.Proxy(false, "", 0, "", "", true, 7_000, 9_000);
    IrcProperties.Server server = server("libera", override);
    ServerCatalog catalog = mock(ServerCatalog.class);
    when(catalog.find("libera")).thenReturn(Optional.of(server));

    ServerProxyResolver resolver = new ServerProxyResolver(catalog);
    ProxyPlan plan = resolver.planForServer("libera");

    assertSame(override, resolver.effectiveProxy("libera"));
    assertFalse(plan.enabled());
    assertEquals(Proxy.NO_PROXY, plan.proxy());
  }

  private static IrcProperties.Server server(String id, IrcProperties.Proxy proxy) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "nick",
        "login",
        "real name",
        null,
        List.of(),
        List.of(),
        proxy);
  }
}
