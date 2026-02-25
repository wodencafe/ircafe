package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
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
    return new TargetCoordinator(
        ui,
        mock(UserListStore.class),
        mock(IrcClientService.class),
        mock(ServerRegistry.class),
        mock(RuntimeConfigStore.class),
        mock(ConnectionCoordinator.class),
        mock(IgnoreListService.class),
        mock(UserhostQueryService.class),
        mock(UserInfoEnrichmentService.class),
        mock(TargetChatHistoryPort.class),
        mock(TargetLogMaintenancePort.class),
        mock(java.util.concurrent.ExecutorService.class),
        mock(java.util.concurrent.ScheduledExecutorService.class));
  }
}
