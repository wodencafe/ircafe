package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import org.junit.jupiter.api.Test;

class OutboundConnectionLifecycleCommandServiceTest {

  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final OutboundConnectionLifecycleCommandService service =
      new OutboundConnectionLifecycleCommandService(ui, connectionCoordinator, targetCoordinator);

  @Test
  void connectWithoutArgUsesActiveServerContext() {
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(pm);

    service.handleConnect("");

    verify(connectionCoordinator).connectOne("libera");
  }

  @Test
  void connectAllKeywordConnectsAllServers() {
    when(targetCoordinator.getActiveTarget()).thenReturn(null);

    service.handleConnect("all");

    verify(connectionCoordinator).connectAll();
  }

  @Test
  void reconnectWithExplicitServerRoutesToCoordinator() {
    service.handleReconnect("oftc");

    verify(connectionCoordinator).reconnectOne("oftc");
  }

  @Test
  void disconnectAllKeywordRoutesToCoordinator() {
    service.handleDisconnect("all");

    verify(connectionCoordinator).disconnectAll();
  }

  @Test
  void quitWithReasonDisconnectsCurrentServerUsingReason() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(targetCoordinator.safeStatusTarget()).thenReturn(new TargetRef("libera", "status"));

    service.handleQuit("gone for lunch");

    verify(connectionCoordinator).disconnectOne("libera", "gone for lunch");
  }

  @Test
  void connectInvalidTargetShowsUsage() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    service.handleConnect("bad input");

    verify(connectionCoordinator, never()).connectOne(anyString());
    verify(connectionCoordinator, never()).connectAll();
    verify(ui).appendStatus(status, "(connect)", "Usage: /connect [serverId|all]");
  }
}
