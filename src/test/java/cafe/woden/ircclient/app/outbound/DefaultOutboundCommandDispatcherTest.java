package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultOutboundCommandDispatcherTest {

  private final OutboundModeCommandService mode = mock(OutboundModeCommandService.class);
  private final OutboundCtcpWhoisCommandService ctcp = mock(OutboundCtcpWhoisCommandService.class);
  private final OutboundDccCommandService dcc = mock(OutboundDccCommandService.class);
  private final OutboundChatCommandService chat = mock(OutboundChatCommandService.class);
  private final OutboundMonitorCommandService monitor = mock(OutboundMonitorCommandService.class);
  private final OutboundIgnoreCommandService ignore = mock(OutboundIgnoreCommandService.class);
  private final LocalFilterCommandHandler filter = mock(LocalFilterCommandHandler.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final UiPort ui = mock(UiPort.class);
  private final UserCommandAliasesBus userCommandAliasesBus = mock(UserCommandAliasesBus.class);
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final DefaultOutboundCommandDispatcher dispatcher =
      new DefaultOutboundCommandDispatcher(
          mode,
          ctcp,
          dcc,
          chat,
          monitor,
          ignore,
          filter,
          targetCoordinator,
          ui,
          userCommandAliasesBus);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void dispatchJoinRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Join("#ircafe", "hunter2"));
    verify(chat).handleJoin(disposables, "#ircafe", "hunter2");
  }

  @Test
  void dispatchWhoisRoutesToWhoisService() {
    dispatcher.dispatch(disposables, new ParsedInput.Whois("alice"));
    verify(ctcp).handleWhois(disposables, "alice");
  }

  @Test
  void dispatchWhowasRoutesToWhoisService() {
    dispatcher.dispatch(disposables, new ParsedInput.Whowas("alice", 2));
    verify(ctcp).handleWhowas(disposables, "alice", 2);
  }

  @Test
  void dispatchDccRoutesToDccService() {
    dispatcher.dispatch(disposables, new ParsedInput.Dcc("send", "alice", "/tmp/example.txt"));
    verify(dcc).handleDcc(disposables, "send", "alice", "/tmp/example.txt");
  }

  @Test
  void dispatchConnectionLifecycleRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Connect("libera"));
    verify(chat).handleConnect("libera");

    dispatcher.dispatch(disposables, new ParsedInput.Disconnect("all"));
    verify(chat).handleDisconnect("all");

    dispatcher.dispatch(disposables, new ParsedInput.Reconnect(""));
    verify(chat).handleReconnect("");

    dispatcher.dispatch(disposables, new ParsedInput.Quit("bye"));
    verify(chat).handleQuit("bye");
  }

  @Test
  void dispatchTopicRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Topic("#ircafe", "new topic"));
    verify(chat).handleTopic(disposables, "#ircafe", "new topic");
  }

  @Test
  void dispatchKickRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Kick("#ircafe", "bob", "reason"));
    verify(chat).handleKick(disposables, "#ircafe", "bob", "reason");
  }

  @Test
  void dispatchInviteRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Invite("bob", "#ircafe"));
    verify(chat).handleInvite(disposables, "bob", "#ircafe");
  }

  @Test
  void dispatchInviteActionCommandsRouteToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.InviteList("libera"));
    verify(chat).handleInviteList("libera");

    dispatcher.dispatch(disposables, new ParsedInput.InviteJoin("12"));
    verify(chat).handleInviteJoin(disposables, "12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteIgnore("12"));
    verify(chat).handleInviteIgnore("12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteWhois("12"));
    verify(chat).handleInviteWhois(disposables, "12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteBlock("12"));
    verify(chat).handleInviteBlock("12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteAutoJoin("on"));
    verify(chat).handleInviteAutoJoin("on");
  }

  @Test
  void dispatchNamesRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Names("#ircafe"));
    verify(chat).handleNames(disposables, "#ircafe");
  }

  @Test
  void dispatchWhoRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Who("#ircafe o"));
    verify(chat).handleWho(disposables, "#ircafe o");
  }

  @Test
  void dispatchListRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ListCmd(">10"));
    verify(chat).handleList(disposables, ">10");
  }

  @Test
  void dispatchMonitorRoutesToMonitorService() {
    dispatcher.dispatch(disposables, new ParsedInput.Monitor("+alice,bob"));
    verify(monitor).handleMonitor(disposables, "+alice,bob");
  }

  @Test
  void dispatchFilterRoutesToLocalFilterService() {
    FilterCommand.Help cmd = new FilterCommand.Help();
    dispatcher.dispatch(disposables, new ParsedInput.Filter(cmd));
    verify(filter).handle(cmd);
  }

  @Test
  void dispatchChatHistoryRoutesSelectorAndLimit() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryBefore(80, "msgid=abc123"));
    verify(chat).handleChatHistoryBefore(disposables, 80, "msgid=abc123");
  }

  @Test
  void dispatchChatHistoryLatestRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryLatest(70, "*"));
    verify(chat).handleChatHistoryLatest(disposables, 70, "*");
  }

  @Test
  void dispatchChatHistoryBetweenRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryBetween("msgid=a", "msgid=b", 60));
    verify(chat).handleChatHistoryBetween(disposables, "msgid=a", "msgid=b", 60);
  }

  @Test
  void dispatchChatHistoryAroundRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryAround("msgid=a", 50));
    verify(chat).handleChatHistoryAround(disposables, "msgid=a", 50);
  }

  @Test
  void dispatchEditAndRedactRouteToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.EditMessage("abc123", "new body"));
    verify(chat).handleEditMessage(disposables, "abc123", "new body");

    dispatcher.dispatch(disposables, new ParsedInput.RedactMessage("abc123", "cleanup"));
    verify(chat).handleRedactMessage(disposables, "abc123", "cleanup");
  }

  @Test
  void dispatchHelpRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Help("edit"));
    verify(chat).handleHelp("edit");
  }

  @Test
  void dispatchUnknownShowsSystemMessageOnSafeTarget() {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(userCommandAliasesBus.unknownCommandAsRawEnabled()).thenReturn(false);

    dispatcher.dispatch(disposables, new ParsedInput.Unknown("/wat"));

    verify(ui).appendStatus(status, "(system)", "Unknown command: /wat");
  }

  @Test
  void dispatchUnknownFallsBackToRawQuoteWhenEnabled() {
    when(userCommandAliasesBus.unknownCommandAsRawEnabled()).thenReturn(true);

    dispatcher.dispatch(disposables, new ParsedInput.Unknown("/wat arg"));

    verify(chat).handleQuote(disposables, "wat arg");
    verify(ui, never()).appendStatus(any(), any(), startsWith("Unknown command:"));
  }
}
