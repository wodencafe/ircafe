package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundNickAwayCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ChatCommandRuntimeConfigPort runtimeConfig =
      mock(ChatCommandRuntimeConfigPort.class);
  private final AwayRoutingPort awayRoutingState = mock(AwayRoutingPort.class);
  private final OutboundNickAwayCommandService service =
      new OutboundNickAwayCommandService(
          irc, ui, connectionCoordinator, targetCoordinator, runtimeConfig, awayRoutingState);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void nickWhileConnectedRequestsChangeWithoutPersistingPreferredNick() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.changeNick("libera", "alice1")).thenReturn(Completable.complete());

    service.handleNick(disposables, "alice1");

    verify(irc).changeNick("libera", "alice1");
    verify(runtimeConfig, never()).rememberNick(anyString(), anyString());
  }

  @Test
  void nickWhileDisconnectedPersistsPreferredNickOnly() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    service.handleNick(disposables, "alice1");

    verify(runtimeConfig).rememberNick("libera", "alice1");
    verify(irc, never()).changeNick(anyString(), anyString());
    verify(ui)
        .appendStatus(
            new TargetRef("libera", "status"),
            "(nick)",
            "Not connected. Saved preferred nick for next connect.");
  }

  @Test
  void awayWhenDisconnectedShowsNotConnected() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    service.handleAway(disposables, "brb");

    verify(ui).appendStatus(status, "(conn)", "Not connected");
    verify(irc, never()).setAway(anyString(), anyString());
  }

  @Test
  void bareAwayWhenNotAwaySetsDefaultReason() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(awayRoutingState.isAway("libera")).thenReturn(false);
    when(irc.setAway("libera", "Gone for now.")).thenReturn(Completable.complete());

    service.handleAway(disposables, "");

    verify(awayRoutingState).rememberOrigin("libera", status);
    verify(awayRoutingState).setLastReason("libera", "Gone for now.");
    verify(awayRoutingState).setAway("libera", true);
    verify(ui).appendStatus(new TargetRef("libera", "status"), "(away)", "Away set: Gone for now.");
  }

  @Test
  void bareAwayWhenAlreadyAwayClearsAway() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(awayRoutingState.isAway("libera")).thenReturn(true);
    when(irc.setAway("libera", "")).thenReturn(Completable.complete());

    service.handleAway(disposables, "");

    verify(awayRoutingState).setLastReason("libera", null);
    verify(awayRoutingState).setAway("libera", false);
    verify(ui).appendStatus(new TargetRef("libera", "status"), "(away)", "Away cleared");
  }
}
