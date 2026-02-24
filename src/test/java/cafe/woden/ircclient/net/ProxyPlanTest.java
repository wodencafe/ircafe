package cafe.woden.ircclient.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProxyPlanTest {

  private static final IrcProperties.Proxy DIRECT =
      new IrcProperties.Proxy(false, "", 0, "", "", true, 20_000, 30_000);

  @AfterEach
  void resetContext() {
    NetProxyContext.configure(DIRECT);
  }

  @Test
  void fromNullReturnsDirectPlanWithDefaultTimeouts() {
    ProxyPlan plan = ProxyPlan.from(null);

    assertFalse(plan.enabled());
    assertEquals(Proxy.NO_PROXY, plan.proxy());
    assertEquals(20_000, plan.connectTimeoutMs());
    assertEquals(30_000, plan.readTimeoutMs());
  }

  @Test
  void fromEnabledProxyBuildsSocksPlan() {
    IrcProperties.Proxy cfg =
        new IrcProperties.Proxy(true, "proxy.local", 1080, "", "", true, 12_345, 23_456);

    ProxyPlan plan = ProxyPlan.from(cfg);

    assertTrue(plan.enabled());
    assertEquals(Proxy.Type.SOCKS, plan.proxy().type());
    InetSocketAddress addr = (InetSocketAddress) plan.proxy().address();
    assertEquals("proxy.local", addr.getHostString());
    assertEquals(1080, addr.getPort());
    assertEquals(12_345, plan.connectTimeoutMs());
    assertEquals(23_456, plan.readTimeoutMs());
  }

  @Test
  void fromDisabledProxyAlwaysUsesNoProxy() {
    IrcProperties.Proxy cfg =
        new IrcProperties.Proxy(false, "proxy.local", 1080, "", "", false, 11_111, 22_222);

    ProxyPlan plan = ProxyPlan.from(cfg);

    assertFalse(plan.enabled());
    assertEquals(Proxy.NO_PROXY, plan.proxy());
    assertEquals(11_111, plan.connectTimeoutMs());
    assertEquals(22_222, plan.readTimeoutMs());
  }
}
