package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.outbound.ignore.OutboundIgnoreCommandService;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class AppIgnorePortsIntegrationTest extends AppModuleIntegrationTestSupport {

  private final ApplicationContext applicationContext;
  private final OutboundIgnoreCommandService outboundIgnoreCommandService;

  AppIgnorePortsIntegrationTest(
      ApplicationContext applicationContext,
      OutboundIgnoreCommandService outboundIgnoreCommandService) {
    this.applicationContext = applicationContext;
    this.outboundIgnoreCommandService = outboundIgnoreCommandService;
  }

  @Test
  void appModuleExposesIgnoreApiPortsWithoutRequiringIgnoreServiceBean() {
    assertEquals(1, applicationContext.getBeansOfType(IgnoreListQueryPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IgnoreListCommandPort.class).size());
    assertTrue(applicationContext.getBeansOfType(IgnoreListService.class).isEmpty());
  }

  @Test
  void ignoreAwareAppBeansAreWiredWithApiPortDependencies() {
    assertNotNull(outboundIgnoreCommandService);
  }
}
