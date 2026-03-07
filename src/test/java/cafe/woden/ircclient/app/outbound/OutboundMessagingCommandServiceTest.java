package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundMessagingCommandServiceTest {

  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy =
      mock(OutboundBackendCapabilityPolicy.class);
  private final OutboundMessagingCommandService service =
      new OutboundMessagingCommandService(
          irc,
          IrcNegotiatedFeaturePort.from(irc),
          backendCapabilityPolicy,
          ui,
          connectionCoordinator,
          targetCoordinator,
          pendingEchoMessageState);
  private final CompositeDisposable disposables = new CompositeDisposable();

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void queryCreatesAndSelectsPmTarget() {
    TargetRef at = new TargetRef("libera", "#ircafe");
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(at);

    service.handleQuery("alice");

    verify(ui).ensureTargetExists(pm);
    verify(ui).selectTarget(pm);
  }

  @Test
  void msgWithoutBodyShowsUsage() {
    TargetRef at = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(at);

    service.handleMsg(disposables, "alice", " ");

    verify(ui).appendStatus(at, "(msg)", "Usage: /msg <nick> <message>");
    verify(irc, never()).sendMessage(anyString(), anyString(), anyString());
  }

  @Test
  void msgSendsToPmTarget() {
    TargetRef at = new TargetRef("libera", "#ircafe");
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.getActiveTarget()).thenReturn(at);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", "alice", "hello")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);

    service.handleMsg(disposables, "alice", "hello");

    verify(ui).ensureTargetExists(pm);
    verify(ui).selectTarget(pm);
    verify(irc).sendMessage("libera", "alice", "hello");
    verify(ui).appendChat(pm, "(me)", "hello", true);
  }

  @Test
  void noticeSendsAndEchoesOnActiveTarget() {
    TargetRef at = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(at);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendNotice("libera", "#ops", "heads up")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);

    service.handleNotice(disposables, "#ops", "heads up");

    verify(irc).sendNotice("libera", "#ops", "heads up");
    verify(ui).appendNotice(at, "(me)", "NOTICE → #ops: heads up");
  }

  @Test
  void meOnStatusTargetShowsUsageError() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handleMe(disposables, "waves");

    verify(ui).appendStatus(status, "(me)", "Select a channel or PM first.");
    verify(irc, never()).sendAction(anyString(), anyString(), anyString());
  }

  @Test
  void meSendsActionAndEchoesWhenEchoMessageIsUnavailable() {
    TargetRef at = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(at);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);
    when(irc.sendAction("libera", "#ircafe", "waves")).thenReturn(Completable.complete());

    service.handleMe(disposables, "waves");

    verify(ui).appendAction(at, "me", "waves", true);
    verify(irc).sendAction("libera", "#ircafe", "waves");
  }
}
