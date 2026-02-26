package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundCtcpWhoisCommandServiceTest {

  private final UiPort ui = mock(UiPort.class);
  private final IrcClientService irc = mock(IrcClientService.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final CtcpRoutingState ctcpRoutingState = mock(CtcpRoutingState.class);
  private final WhoisRoutingState whoisRoutingState = mock(WhoisRoutingState.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final OutboundCtcpWhoisCommandService service =
      new OutboundCtcpWhoisCommandService(
          ui, irc, targetCoordinator, connectionCoordinator, ctcpRoutingState, whoisRoutingState);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void requestWhoisRoutesToCollaboratorsWhenConnected() {
    TargetRef ctx = new TargetRef("libera", "#ircafe");
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.whois("libera", "alice")).thenReturn(Completable.complete());

    service.requestWhois(disposables, ctx, " alice ");

    verify(ui).ensureTargetExists(ctx);
    verify(whoisRoutingState).put("libera", "alice", ctx);
    verify(ui).appendStatus(ctx, "(whois)", "Requesting WHOIS for alice...");
    verify(irc).whois("libera", "alice");
  }

  @Test
  void whowasWithNegativeCountShowsUsageAndSkipsIrcCall() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handleWhowas(disposables, "alice", -1);

    verify(ui).appendStatus(status, "(whowas)", "Usage: /whowas <nick> [count]");
    verify(irc, never()).whowas(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void ctcpPingStoresPendingRoutingAndSendsCtcpPayload() {
    TargetRef ctx = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(ctx);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendPrivateMessage(
            eq("libera"),
            eq("alice"),
            argThat(msg -> msg != null && msg.startsWith("\u0001PING ") && msg.endsWith("\u0001"))))
        .thenReturn(Completable.complete());

    service.handleCtcpPing(disposables, "alice");

    verify(ui).ensureTargetExists(ctx);
    verify(ctcpRoutingState).put(eq("libera"), eq("alice"), eq("PING"), anyString(), eq(ctx));
    verify(ui)
        .appendStatus(
            eq(ctx),
            eq("(ctcp)"),
            argThat(text -> text != null && text.startsWith("→ alice PING")));
    verify(irc)
        .sendPrivateMessage(
            eq("libera"),
            eq("alice"),
            argThat(msg -> msg != null && msg.startsWith("\u0001PING ") && msg.endsWith("\u0001")));
  }

  @Test
  void ctcpActionCommandShowsHintAndDoesNotSend() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);

    service.handleCtcp(disposables, "alice", "ACTION", "waves");

    verify(ui).appendStatus(status, "(ctcp)", "Use /me for ACTION.");
    verify(irc, never()).sendPrivateMessage(anyString(), anyString(), anyString());
  }

  @Test
  void ctcpVersionWhenDisconnectedShowsNotConnected() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(connectionCoordinator.isConnected("libera")).thenReturn(false);

    service.handleCtcpVersion(disposables, "alice");

    verify(ui).appendStatus(new TargetRef("libera", "status"), "(conn)", "Not connected");
    verify(irc, never()).sendPrivateMessage(anyString(), anyString(), anyString());
  }
}
