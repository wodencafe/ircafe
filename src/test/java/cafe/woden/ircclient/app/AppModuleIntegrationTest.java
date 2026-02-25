package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class AppModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final MediatorControlPort mediatorControlPort;
  private final ActiveTargetPort activeTargetPort;
  private final ServerRegistry serverRegistry;

  AppModuleIntegrationTest(
      ApplicationContext applicationContext,
      MediatorControlPort mediatorControlPort,
      ActiveTargetPort activeTargetPort,
      ServerRegistry serverRegistry) {
    this.applicationContext = applicationContext;
    this.mediatorControlPort = mediatorControlPort;
    this.activeTargetPort = activeTargetPort;
    this.serverRegistry = serverRegistry;
  }

  @Test
  void exposesSingleApiControlPortsForCoreOrchestration() {
    assertEquals(1, applicationContext.getBeansOfType(MediatorControlPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(ActiveTargetPort.class).size());
  }

  @Test
  void apiPortsResolveToCoreImplementations() {
    assertEquals(IrcMediator.class, AopUtils.getTargetClass(mediatorControlPort));
    assertEquals(TargetCoordinator.class, AopUtils.getTargetClass(activeTargetPort));
  }

  @Test
  void safeStatusTargetUsesConfiguredServerContext() {
    TargetRef status = activeTargetPort.safeStatusTarget();

    assertNotNull(status);
    assertEquals("status", status.target());
    assertFalse(status.serverId().isBlank());
    assertTrue(serverRegistry.containsId(status.serverId()) || "default".equals(status.serverId()));
  }
}
