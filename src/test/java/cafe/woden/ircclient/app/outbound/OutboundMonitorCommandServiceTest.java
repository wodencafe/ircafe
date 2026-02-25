package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OutboundMonitorCommandServiceTest {

  private final IrcClientService irc = Mockito.mock(IrcClientService.class);
  private final UiPort ui = Mockito.mock(UiPort.class);
  private final TargetCoordinator targetCoordinator = Mockito.mock(TargetCoordinator.class);
  private final ConnectionCoordinator connectionCoordinator =
      Mockito.mock(ConnectionCoordinator.class);
  private final MonitorRosterPort monitorRosterPort = Mockito.mock(MonitorRosterPort.class);
  private final MonitorFallbackPort monitorFallbackPort = Mockito.mock(MonitorFallbackPort.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final OutboundMonitorCommandService service =
      new OutboundMonitorCommandService(
          irc,
          ui,
          targetCoordinator,
          connectionCoordinator,
          monitorRosterPort,
          monitorFallbackPort);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void addPersistsAndSendsMonitorPlusWhenConnected() {
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(monitorRosterPort.parseNickInput("alice,bob"))
        .thenReturn(java.util.List.of("alice", "bob"));
    when(monitorRosterPort.addNicks(eq("libera"), eq(java.util.List.of("alice", "bob"))))
        .thenReturn(2);
    when(irc.negotiatedMonitorLimit("libera")).thenReturn(100);
    when(irc.sendRaw("libera", "MONITOR +alice,bob")).thenReturn(Completable.complete());

    service.handleMonitor(disposables, "+alice,bob");

    verify(monitorRosterPort).addNicks("libera", java.util.List.of("alice", "bob"));
    verify(irc).sendRaw("libera", "MONITOR +alice,bob");
  }

  @Test
  void listShowsLocalNicksAndDoesNotSendWhenDisconnected() {
    TargetRef active = new TargetRef("libera", "status");
    TargetRef monitor = TargetRef.monitorGroup("libera");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);
    when(monitorRosterPort.listNicks("libera")).thenReturn(java.util.List.of("alice"));

    service.handleMonitor(disposables, "list");

    verify(ui).appendStatus(monitor, "(monitor)", "Monitored nicks (1): alice");
    verify(irc, never()).sendRaw(any(), any());
  }

  @Test
  void statusUsesIsonFallbackWhenMonitorUnavailable() {
    TargetRef active = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(monitorFallbackPort.isFallbackActive("libera")).thenReturn(true);

    service.handleMonitor(disposables, "status");

    verify(monitorFallbackPort).requestImmediateRefresh("libera");
    verify(irc, never()).sendRaw(eq("libera"), any());
  }
}
