package cafe.woden.ircclient.app.outbound.messaging;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.Ircv3MultilineFeatureSupport;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandCatalog;
import cafe.woden.ircclient.app.commands.BackendNamedCommandParser;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.FilterCommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.app.outbound.support.OutboundConnectionStatusSupport;
import cafe.woden.ircclient.irc.adapter.IrcCurrentNickPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcEchoCapabilityPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcMessagingPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcNegotiatedFeaturePortAdapter;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OutboundMessagingCommandServiceTest {

  private final IrcBackendRuntimeClientService irc = mock(IrcBackendRuntimeClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy =
      mock(OutboundBackendCapabilityPolicy.class);
  private final Ircv3MultilineFeatureSupport multilineFeatureSupport =
      new Ircv3MultilineFeatureSupport(
          backendCapabilityPolicy, new IrcNegotiatedFeaturePortAdapter(irc));
  private final OutboundMultilineMessageSupport outboundMultilineMessageSupport =
      new OutboundMultilineMessageSupport(multilineFeatureSupport, ui);
  private final OutboundConnectionStatusSupport outboundConnectionStatusSupport =
      new OutboundConnectionStatusSupport(ui, connectionCoordinator);
  private final OutboundMessagingCommandService service =
      new OutboundMessagingCommandService(
          new IrcMessagingPortAdapter(irc),
          new IrcCurrentNickPortAdapter(irc),
          new IrcEchoCapabilityPortAdapter(irc),
          outboundMultilineMessageSupport,
          outboundConnectionStatusSupport,
          ui,
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

  @ParameterizedTest
  @CsvSource({
    "/cs OP #ircafe,ChanServ,OP #ircafe",
    "/chanserv INFO #ircafe,ChanServ,INFO #ircafe",
    "/cs,ChanServ,HELP",
    "/ns IDENTIFY secret,NickServ,IDENTIFY secret",
    "/nickserv INFO Alice,NickServ,INFO Alice",
    "/ns,NickServ,HELP",
    "/ms SEND Alice hello there,MemoServ,SEND Alice hello there",
    "/memoserv READ 1,MemoServ,READ 1",
    "/ms,MemoServ,HELP",
    "/os HELP STATS,OperServ,HELP STATS",
    "/operserv HELP,OperServ,HELP",
    "/os,OperServ,HELP"
  })
  void serviceCommandSendsThroughPrivateMessagingFlow(
      String line, String serviceNick, String expectedBody) {
    CommandParser parser =
        new CommandParser(
            new FilterCommandParser(),
            new BackendNamedCommandParser(BackendNamedCommandCatalog.empty()));
    TargetRef at = new TargetRef("libera", "#ircafe");
    TargetRef pm = new TargetRef("libera", serviceNick);
    when(targetCoordinator.getActiveTarget()).thenReturn(at);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.sendMessage("libera", serviceNick, expectedBody)).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(false);

    ParsedInput.Msg msg = assertInstanceOf(ParsedInput.Msg.class, parser.parse(line));
    service.handleMsg(disposables, msg.nick(), msg.body());

    verify(irc).sendMessage("libera", serviceNick, expectedBody);
    verify(ui).ensureTargetExists(pm);
    verify(ui).selectTarget(pm);
    verify(ui).appendChat(pm, "(me)", expectedBody, true);
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

  @ParameterizedTest
  @ValueSource(strings = {"#ircafe", "alice"})
  void meWithEchoMessageDisplaysPendingActionBeforeSending(String target) {
    TargetRef at = new TargetRef("libera", target);
    Instant createdAt = Instant.now();
    var pending =
        new PendingEchoMessagePort.PendingOutboundChat(
            "action-1", at, "me", "waves", createdAt, true);
    when(targetCoordinator.getActiveTarget()).thenReturn(at);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(pendingEchoMessageState.registerAction(eq(at), eq("me"), eq("waves"), any()))
        .thenReturn(pending);
    when(irc.sendAction("libera", target, "waves")).thenReturn(Completable.never());

    service.handleMe(disposables, " waves ");

    var order = inOrder(ui, irc);
    order.verify(ui).appendPendingOutgoingAction(at, "action-1", createdAt, "me", "waves");
    order.verify(irc).sendAction("libera", target, "waves");
    verify(ui, never()).appendAction(at, "me", "waves", true);
  }

  @Test
  void meSendFailureRemovesPendingStateAndFailsAction() {
    TargetRef at = new TargetRef("libera", "#ircafe");
    Instant createdAt = Instant.now();
    var pending =
        new PendingEchoMessagePort.PendingOutboundChat(
            "action-1", at, "me", "waves", createdAt, true);
    var error = new IllegalStateException("network");
    when(targetCoordinator.getActiveTarget()).thenReturn(at);
    when(connectionCoordinator.isConnected("libera")).thenReturn(true);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    when(irc.isEchoMessageAvailable("libera")).thenReturn(true);
    when(pendingEchoMessageState.registerAction(eq(at), eq("me"), eq("waves"), any()))
        .thenReturn(pending);
    when(irc.sendAction("libera", "#ircafe", "waves")).thenReturn(Completable.error(error));

    service.handleMe(disposables, "waves");

    verify(pendingEchoMessageState).removeById("action-1");
    verify(ui)
        .failPendingOutgoingAction(
            eq(at), eq("action-1"), any(), eq("me"), eq("waves"), eq(error.toString()));
  }
}
