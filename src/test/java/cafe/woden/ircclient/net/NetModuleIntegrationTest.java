package cafe.woden.ircclient.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.ServerCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class NetModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final ServerCatalog serverCatalog;
  private final ServerProxyResolver serverProxyResolver;
  private final NetProxyBootstrap netProxyBootstrap;
  private final NetTlsBootstrap netTlsBootstrap;
  private final NetHeartbeatBootstrap netHeartbeatBootstrap;

  NetModuleIntegrationTest(
      ApplicationContext applicationContext,
      ServerCatalog serverCatalog,
      ServerProxyResolver serverProxyResolver,
      NetProxyBootstrap netProxyBootstrap,
      NetTlsBootstrap netTlsBootstrap,
      NetHeartbeatBootstrap netHeartbeatBootstrap) {
    this.applicationContext = applicationContext;
    this.serverCatalog = serverCatalog;
    this.serverProxyResolver = serverProxyResolver;
    this.netProxyBootstrap = netProxyBootstrap;
    this.netTlsBootstrap = netTlsBootstrap;
    this.netHeartbeatBootstrap = netHeartbeatBootstrap;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesNetworkingModuleBeans() {
    assertEquals(1, applicationContext.getBeansOfType(ServerProxyResolver.class).size());
    assertEquals(1, applicationContext.getBeansOfType(NetProxyBootstrap.class).size());
    assertEquals(1, applicationContext.getBeansOfType(NetTlsBootstrap.class).size());
    assertEquals(1, applicationContext.getBeansOfType(NetHeartbeatBootstrap.class).size());
    assertNotNull(serverProxyResolver);
    assertNotNull(netProxyBootstrap);
    assertNotNull(netTlsBootstrap);
    assertNotNull(netHeartbeatBootstrap);
  }

  @Test
  void resolvesProxyPlansForKnownAndUnknownServers() {
    String knownServerId =
        serverCatalog.entries().stream()
            .map(entry -> entry.server().id())
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected at least one configured server"));

    assertNotNull(serverProxyResolver.defaultProxy());
    assertNotNull(serverProxyResolver.effectiveProxy(knownServerId));
    assertNotNull(serverProxyResolver.effectiveProxy("missing-server"));
    assertNotNull(serverProxyResolver.planForServer(knownServerId));
    assertNotNull(serverProxyResolver.planForServer("missing-server"));
    assertTrue(serverProxyResolver.planForServer("missing-server").cfg() != null);
  }
}
