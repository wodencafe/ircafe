package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundModeCommandServiceTest {

  private final IrcClientService irc = mock(IrcClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ModeRoutingState modeRoutingState = mock(ModeRoutingState.class);
  private final LabeledResponseRoutingState labeledResponseRoutingState =
      mock(LabeledResponseRoutingState.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final OutboundModeCommandService service =
      new OutboundModeCommandService(
          irc,
          ui,
          connectionCoordinator,
          targetCoordinator,
          modeRoutingState,
          labeledResponseRoutingState);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void modeQueryFromChannelTracksPendingTargetAndSendsRawModeLine() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "MODE #ircafe")).thenReturn(Completable.complete());

    service.handleMode(disposables, "", "");

    verify(modeRoutingState).putPendingModeTarget("libera", "#ircafe", channel);
    verify(ui).ensureTargetExists(channel);
    verify(ui).appendStatus(channel, "(mode)", "→ MODE #ircafe");
    verify(irc).sendRaw("libera", "MODE #ircafe");
  }

  @Test
  void opUsesPreparedLabeledRawLineAndRemembersCorrelation() {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef channel = new TargetRef("libera", "#ircafe");
    LabeledResponseRoutingState.PreparedRawLine prepared =
        new LabeledResponseRoutingState.PreparedRawLine(
            "@label=req-1 MODE #ircafe +o alice", "req-1", true);

    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("libera")).thenReturn(true);
    when(labeledResponseRoutingState.prepareOutgoingRaw("libera", "MODE #ircafe +o alice"))
        .thenReturn(prepared);
    when(irc.sendRaw("libera", "@label=req-1 MODE #ircafe +o alice"))
        .thenReturn(Completable.complete());

    service.handleOp(disposables, "#ircafe", List.of("alice"));

    verify(labeledResponseRoutingState)
        .remember(
            eq("libera"),
            eq("req-1"),
            eq(channel),
            eq("MODE #ircafe +o alice"),
            any(Instant.class));
    verify(ui).ensureTargetExists(channel);
    verify(ui).appendStatus(channel, "(mode)", "→ MODE #ircafe +o alice {label=req-1}");
    verify(irc).sendRaw("libera", "@label=req-1 MODE #ircafe +o alice");
  }

  @Test
  void banShapesNickIntoHostmaskAndPreservesExistingMasks() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendRaw("libera", "MODE #ircafe +b trouble!*@*")).thenReturn(Completable.complete());
    when(irc.sendRaw("libera", "MODE #ircafe +b bad@host")).thenReturn(Completable.complete());

    service.handleBan(disposables, "", List.of("trouble", "bad@host"));

    verify(ui).ensureTargetExists(channel);
    verify(irc).sendRaw("libera", "MODE #ircafe +b trouble!*@*");
    verify(irc).sendRaw("libera", "MODE #ircafe +b bad@host");
  }

  @Test
  void modeWhenDisconnectedShowsConnectionStatusAndDoesNotSend() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    service.handleMode(disposables, "#ircafe", "+m");

    verify(ui).appendStatus(new TargetRef("libera", "status"), "(conn)", "Not connected");
    verify(irc, never()).sendRaw(anyString(), anyString());
  }
}
