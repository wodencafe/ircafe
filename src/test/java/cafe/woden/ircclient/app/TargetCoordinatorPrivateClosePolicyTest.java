package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.model.TargetRef;
import org.junit.jupiter.api.Test;

class TargetCoordinatorPrivateClosePolicyTest {

  @Test
  void closingPrivateTargetBlocksSelfAutoOpen() {
    UiPort ui = mock(UiPort.class);
    TargetCoordinator coordinator = newCoordinator(ui);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);

    assertFalse(coordinator.allowPrivateAutoOpenFromInbound(pm, true));
  }

  @Test
  void peerInboundAutoOpenReopensAndClearsClosedFlag() {
    UiPort ui = mock(UiPort.class);
    TargetCoordinator coordinator = newCoordinator(ui);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);
    assertTrue(coordinator.allowPrivateAutoOpenFromInbound(pm, false));

    // Peer-originated inbound has reopened it; self inbound is no longer blocked.
    assertTrue(coordinator.allowPrivateAutoOpenFromInbound(pm, true));
  }

  @Test
  void explicitOpenPrivateConversationClearsClosedPolicy() {
    UiPort ui = mock(UiPort.class);
    TargetCoordinator coordinator = newCoordinator(ui);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);
    assertFalse(coordinator.allowPrivateAutoOpenFromInbound(pm, true));

    coordinator.openPrivateConversation(new PrivateMessageRequest("libera", "alice"));
    assertTrue(coordinator.allowPrivateAutoOpenFromInbound(pm, true));
  }

  @Test
  void serverDisconnectClearsClosedPrivateTargetsForServer() {
    UiPort ui = mock(UiPort.class);
    TargetCoordinator coordinator = newCoordinator(ui);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);
    assertFalse(coordinator.allowPrivateAutoOpenFromInbound(pm, true));

    coordinator.onServerDisconnected("libera");
    assertTrue(coordinator.allowPrivateAutoOpenFromInbound(pm, true));
  }

  @Test
  void selectingClosedPrivateTargetIsIgnored() {
    UiPort ui = mock(UiPort.class);
    TargetCoordinator coordinator = newCoordinator(ui);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);
    reset(ui);

    coordinator.onTargetSelected(pm);

    verify(ui, never()).ensureTargetExists(pm);
    verify(ui, never()).setChatActiveTarget(pm);
    assertFalse(coordinator.allowPrivateAutoOpenFromInbound(pm, true));
  }

  @Test
  void activatingClosedPrivateTargetIsIgnored() {
    UiPort ui = mock(UiPort.class);
    TargetCoordinator coordinator = newCoordinator(ui);

    TargetRef pm = new TargetRef("libera", "alice");
    coordinator.closeTarget(pm);
    reset(ui);

    coordinator.onTargetActivated(pm);

    verify(ui, never()).ensureTargetExists(pm);
    assertFalse(coordinator.allowPrivateAutoOpenFromInbound(pm, true));
  }

  private static TargetCoordinator newCoordinator(UiPort ui) {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    return new TargetCoordinator(
        ui,
        mock(UserListStore.class),
        IrcTargetMembershipPort.from(irc),
        irc,
        mock(ServerRegistry.class),
        mock(RuntimeConfigStore.class),
        mock(ConnectionCoordinator.class),
        mock(IgnoreListQueryPort.class),
        mock(UserhostQueryService.class),
        mock(UserInfoEnrichmentService.class),
        mock(TargetChatHistoryPort.class),
        mock(TargetLogMaintenancePort.class),
        mock(java.util.concurrent.ExecutorService.class),
        mock(java.util.concurrent.ScheduledExecutorService.class));
  }
}
