package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class AppModuleIntegrationTest extends AppModuleIntegrationTestSupport {
  private final ApplicationContext applicationContext;
  private final MediatorControlPort mediatorControlPort;
  private final ActiveTargetPort activeTargetPort;
  private final TargetCoordinator targetCoordinator;
  private final ConnectionCoordinator connectionCoordinator;
  private final ServerRegistry serverRegistry;
  private final IrcConnectionLifecyclePort ircConnectionLifecyclePort;
  private final UserListStore userListStore;
  private final UiPort swingUiPort;

  AppModuleIntegrationTest(
      ApplicationContext applicationContext,
      MediatorControlPort mediatorControlPort,
      ActiveTargetPort activeTargetPort,
      TargetCoordinator targetCoordinator,
      ConnectionCoordinator connectionCoordinator,
      ServerRegistry serverRegistry,
      @Qualifier("ircConnectionLifecyclePort")
          IrcConnectionLifecyclePort ircConnectionLifecyclePort,
      UserListStore userListStore,
      @Qualifier("swingUiPort") UiPort swingUiPort) {
    this.applicationContext = applicationContext;
    this.mediatorControlPort = mediatorControlPort;
    this.activeTargetPort = activeTargetPort;
    this.targetCoordinator = targetCoordinator;
    this.connectionCoordinator = connectionCoordinator;
    this.serverRegistry = serverRegistry;
    this.ircConnectionLifecyclePort = ircConnectionLifecyclePort;
    this.userListStore = userListStore;
    this.swingUiPort = swingUiPort;
  }

  @BeforeEach
  void clearInteractions() {
    clearInvocations(ircConnectionLifecyclePort, swingUiPort, targetChatHistoryPort);
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

  @Test
  void connectAllPortDelegatesToConnectionCoordinatorForEachConfiguredServer() {
    for (String sid : serverRegistry.serverIds()) {
      when(ircConnectionLifecyclePort.connect(sid)).thenReturn(Completable.complete());
    }

    mediatorControlPort.connectAll();

    for (String sid : serverRegistry.serverIds()) {
      verify(ircConnectionLifecyclePort).connect(sid);
    }
  }

  @Test
  void selectingTargetUpdatesActiveTargetPortAndUiContext() {
    String sid = serverRegistry.serverIds().stream().findFirst().orElse("default");
    TargetRef selected = new TargetRef(sid, "#integration-active");
    when(userListStore.get(sid, selected.target())).thenReturn(java.util.List.of());

    targetCoordinator.onTargetSelected(selected);

    assertEquals(selected, activeTargetPort.getActiveTarget());
    assertEquals(sid, activeTargetPort.safeStatusTarget().serverId());
    verify(targetChatHistoryPort).onTargetSelected(selected);
    verify(swingUiPort).setChatActiveTarget(selected);
    verify(swingUiPort).setUsersChannel(selected);
  }

  @Test
  void selectingIgnoresTargetUpdatesUiOnlyStatusContext() {
    String sid = serverRegistry.serverIds().stream().findFirst().orElse("default");
    TargetRef ignores = TargetRef.ignores(sid);

    targetCoordinator.onTargetSelected(ignores);

    assertEquals(ignores, activeTargetPort.getActiveTarget());
    assertEquals(sid, activeTargetPort.safeStatusTarget().serverId());
    verify(swingUiPort).setStatusBarChannel("Ignores");
    verify(swingUiPort).setChatActiveTarget(ignores);
    verify(swingUiPort).setUsersChannel(ignores);
  }

  @Test
  void connectionCoordinatorBeanIsSharedAcrossMediatorAndAppContext() {
    assertEquals(1, applicationContext.getBeansOfType(ConnectionCoordinator.class).size());
    assertNotNull(connectionCoordinator);
  }
}
