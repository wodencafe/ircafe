package cafe.woden.ircclient.net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.config.IrcProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetProxySelectorTest {

  private static final IrcProperties.Proxy DIRECT =
      new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000);

  @BeforeEach
  void setUp() {
    NetProxyContext.configure(DIRECT);
  }

  @AfterEach
  void tearDown() {
    NetProxyContext.configure(DIRECT);
  }

  @Test
  void returnsNoProxyForNullUriOrUnsupportedSchemes() {
    NetProxySelector selector = NetProxySelector.INSTANCE;

    assertEquals(List.of(Proxy.NO_PROXY), selector.select(null));
    assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("ftp://example.com")));
    assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("ws://example.com")));
  }

  @Test
  void returnsConfiguredProxyForHttpAndHttps() {
    IrcProperties.Proxy cfg =
        new IrcProperties.Proxy(true, "proxy.local", 9050, "", "", true, 20_000, 30_000);
    NetProxyContext.configure(cfg);

    NetProxySelector selector = NetProxySelector.INSTANCE;
    Proxy httpProxy = selector.select(URI.create("http://example.com")).getFirst();
    Proxy httpsProxy = selector.select(URI.create("https://example.com")).getFirst();

    assertEquals(Proxy.Type.SOCKS, httpProxy.type());
    InetSocketAddress httpAddress = (InetSocketAddress) httpProxy.address();
    assertEquals("proxy.local", httpAddress.getHostString());
    assertEquals(9050, httpAddress.getPort());

    assertSame(httpProxy, httpsProxy);
  }

  @Test
  void connectFailedIsNoOp() {
    NetProxySelector selector = NetProxySelector.INSTANCE;

    assertDoesNotThrow(
        () ->
            selector.connectFailed(
                URI.create("https://example.com"),
                new InetSocketAddress("example.com", 443),
                new IOException("boom")));
  }
}
