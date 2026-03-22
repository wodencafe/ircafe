package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.BackendNamedOutboundCommandRouter;
import cafe.woden.ircclient.app.outbound.dcc.OutboundDccCommandService;
import cafe.woden.ircclient.app.outbound.dispatch.DefaultOutboundCommandDispatcher;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistrar;
import cafe.woden.ircclient.app.outbound.identity.OutboundNickAwayCommandService;
import cafe.woden.ircclient.app.outbound.ignore.OutboundIgnoreCommandService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultOutboundCommandDispatcherTest {

  private final OutboundModeCommandService mode = mock(OutboundModeCommandService.class);
  private final OutboundCtcpWhoisCommandService ctcp = mock(OutboundCtcpWhoisCommandService.class);
  private final OutboundDccCommandService dcc = mock(OutboundDccCommandService.class);
  private final OutboundHelpCommandService help = mock(OutboundHelpCommandService.class);
  private final OutboundMessagingCommandService messaging =
      mock(OutboundMessagingCommandService.class);
  private final OutboundSayQuoteCommandService sayQuote =
      mock(OutboundSayQuoteCommandService.class);
  private final OutboundJoinPartCommandService joinPart =
      mock(OutboundJoinPartCommandService.class);
  private final OutboundNickAwayCommandService nickAway =
      mock(OutboundNickAwayCommandService.class);
  private final OutboundConnectionLifecycleCommandService lifecycle =
      mock(OutboundConnectionLifecycleCommandService.class);
  private final OutboundChatHistoryCommandService chatHistory =
      mock(OutboundChatHistoryCommandService.class);
  private final OutboundInviteCommandService invite = mock(OutboundInviteCommandService.class);
  private final OutboundNamesWhoListCommandService namesWhoList =
      mock(OutboundNamesWhoListCommandService.class);
  private final OutboundTopicKickCommandService topicKick =
      mock(OutboundTopicKickCommandService.class);
  private final BackendNamedOutboundCommandRouter backendNamedRouter =
      mock(BackendNamedOutboundCommandRouter.class);
  private final OutboundUploadCommandService upload = mock(OutboundUploadCommandService.class);
  private final OutboundMessageMutationCommandService messageMutations =
      mock(OutboundMessageMutationCommandService.class);
  private final OutboundReadMarkerCommandService readMarker =
      mock(OutboundReadMarkerCommandService.class);
  private final OutboundMonitorCommandService monitor = mock(OutboundMonitorCommandService.class);
  private final OutboundIgnoreCommandService ignore = mock(OutboundIgnoreCommandService.class);
  private final LocalFilterCommandHandler filter = mock(LocalFilterCommandHandler.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final UiPort ui = mock(UiPort.class);
  private final UserCommandAliasesBus userCommandAliasesBus = mock(UserCommandAliasesBus.class);
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final List<OutboundCommandRegistrar> commandRegistrars =
      List.of(
          new LifecycleBackendOutboundCommandRegistrar(joinPart, lifecycle, backendNamedRouter),
          new IdentityMessagingOutboundCommandRegistrar(nickAway, messaging, ctcp),
          new ChannelModeOutboundCommandRegistrar(topicKick, invite, namesWhoList, monitor, mode),
          new IgnoreCtcpOutboundCommandRegistrar(ignore, filter, ctcp, dcc),
          new HistoryMutationOutboundCommandRegistrar(
              chatHistory, readMarker, help, upload, messageMutations, sayQuote),
          new UnknownOutboundCommandRegistrar(
              userCommandAliasesBus, sayQuote, targetCoordinator, ui));

  private final DefaultOutboundCommandDispatcher dispatcher =
      new DefaultOutboundCommandDispatcher(commandRegistrars);

  @AfterEach
  void tearDown() {
    disposables.dispose();
  }

  @Test
  void dispatchJoinRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Join("#ircafe", "hunter2"));
    verify(joinPart).handleJoin(disposables, "#ircafe", "hunter2");
  }

  @Test
  void dispatchPartRoutesToJoinPartService() {
    dispatcher.dispatch(disposables, new ParsedInput.Part("#ircafe", "later"));
    verify(joinPart).handlePart(disposables, "#ircafe", "later");
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
    verify(lifecycle).handleConnect("libera");

    dispatcher.dispatch(disposables, new ParsedInput.Disconnect("all"));
    verify(lifecycle).handleDisconnect("all");

    dispatcher.dispatch(disposables, new ParsedInput.Reconnect(""));
    verify(lifecycle).handleReconnect("");

    dispatcher.dispatch(disposables, new ParsedInput.Quit("bye"));
    verify(lifecycle).handleQuit("bye");
  }

  @Test
  void dispatchNickAndAwayRouteToNickAwayService() {
    dispatcher.dispatch(disposables, new ParsedInput.Nick("alice2"));
    verify(nickAway).handleNick(disposables, "alice2");

    dispatcher.dispatch(disposables, new ParsedInput.Away("brb"));
    verify(nickAway).handleAway(disposables, "brb");
  }

  @Test
  void dispatchQueryMsgNoticeAndMeRouteToMessagingService() {
    dispatcher.dispatch(disposables, new ParsedInput.Query("alice"));
    verify(messaging).handleQuery("alice");

    dispatcher.dispatch(disposables, new ParsedInput.Msg("alice", "hello"));
    verify(messaging).handleMsg(disposables, "alice", "hello");

    dispatcher.dispatch(disposables, new ParsedInput.Notice("#ircafe", "heads up"));
    verify(messaging).handleNotice(disposables, "#ircafe", "heads up");

    dispatcher.dispatch(disposables, new ParsedInput.Me("waves"));
    verify(messaging).handleMe(disposables, "waves");
  }

  @Test
  void dispatchQuasselSetupRoutesToQuasselService() {
    ParsedInput.BackendNamed command =
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel");
    dispatcher.dispatch(disposables, command);
    verify(backendNamedRouter).handle(disposables, command);
  }

  @Test
  void dispatchQuasselNetworkRoutesToQuasselService() {
    ParsedInput.BackendNamed command =
        new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_NETWORK, "list");
    dispatcher.dispatch(disposables, command);
    verify(backendNamedRouter).handle(disposables, command);
  }

  @Test
  void dispatchTopicRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Topic("#ircafe", "new topic"));
    verify(topicKick).handleTopic(disposables, "#ircafe", "new topic");
  }

  @Test
  void dispatchKickRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Kick("#ircafe", "bob", "reason"));
    verify(topicKick).handleKick(disposables, "#ircafe", "bob", "reason");
  }

  @Test
  void dispatchInviteRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Invite("bob", "#ircafe"));
    verify(invite).handleInvite(disposables, "bob", "#ircafe");
  }

  @Test
  void dispatchInviteActionCommandsRouteToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.InviteList("libera"));
    verify(invite).handleInviteList("libera");

    dispatcher.dispatch(disposables, new ParsedInput.InviteJoin("12"));
    verify(invite).handleInviteJoin(disposables, "12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteIgnore("12"));
    verify(invite).handleInviteIgnore("12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteWhois("12"));
    verify(invite).handleInviteWhois(disposables, "12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteBlock("12"));
    verify(invite).handleInviteBlock("12");

    dispatcher.dispatch(disposables, new ParsedInput.InviteAutoJoin("on"));
    verify(invite).handleInviteAutoJoin("on");
  }

  @Test
  void dispatchNamesRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Names("#ircafe"));
    verify(namesWhoList).handleNames(disposables, "#ircafe");
  }

  @Test
  void dispatchWhoRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.Who("#ircafe o"));
    verify(namesWhoList).handleWho(disposables, "#ircafe o");
  }

  @Test
  void dispatchListRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ListCmd(">10"));
    verify(namesWhoList).handleList(disposables, ">10");
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
    verify(chatHistory).handleChatHistoryBefore(disposables, 80, "msgid=abc123");
  }

  @Test
  void dispatchChatHistoryLatestRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryLatest(70, "*"));
    verify(chatHistory).handleChatHistoryLatest(disposables, 70, "*");
  }

  @Test
  void dispatchChatHistoryBetweenRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryBetween("msgid=a", "msgid=b", 60));
    verify(chatHistory).handleChatHistoryBetween(disposables, "msgid=a", "msgid=b", 60);
  }

  @Test
  void dispatchChatHistoryAroundRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.ChatHistoryAround("msgid=a", 50));
    verify(chatHistory).handleChatHistoryAround(disposables, "msgid=a", 50);
  }

  @Test
  void dispatchMarkReadRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.MarkRead());
    verify(readMarker).handleMarkRead(disposables);
  }

  @Test
  void dispatchEditAndRedactRouteToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.EditMessage("abc123", "new body"));
    verify(messageMutations).handleEditMessage(disposables, "abc123", "new body");

    dispatcher.dispatch(disposables, new ParsedInput.RedactMessage("abc123", "cleanup"));
    verify(messageMutations).handleRedactMessage(disposables, "abc123", "cleanup");
  }

  @Test
  void dispatchUnreactRoutesToChatService() {
    dispatcher.dispatch(disposables, new ParsedInput.UnreactMessage("abc123", ":+1:"));
    verify(messageMutations).handleUnreactMessage(disposables, "abc123", ":+1:");
  }

  @Test
  void dispatchReplyAndReactRoutesToMessageMutationService() {
    dispatcher.dispatch(disposables, new ParsedInput.ReplyMessage("abc123", "hello"));
    verify(messageMutations).handleReplyMessage(disposables, "abc123", "hello");

    dispatcher.dispatch(disposables, new ParsedInput.ReactMessage("abc123", ":+1:"));
    verify(messageMutations).handleReactMessage(disposables, "abc123", ":+1:");
  }

  @Test
  void dispatchHelpRoutesToHelpService() {
    dispatcher.dispatch(disposables, new ParsedInput.Help("edit"));
    verify(help).handleHelp("edit");
  }

  @Test
  void dispatchSayAndQuoteRouteToSayQuoteService() {
    dispatcher.dispatch(disposables, new ParsedInput.Say("hello world"));
    verify(sayQuote).handleSay(disposables, "hello world");

    dispatcher.dispatch(disposables, new ParsedInput.Quote("MONITOR +nick"));
    verify(sayQuote).handleQuote(disposables, "MONITOR +nick");
  }

  @Test
  void dispatchUploadRoutesToUploadService() {
    dispatcher.dispatch(disposables, new ParsedInput.Upload("m.image", "/tmp/photo.png", "photo"));

    verify(upload).handleUpload(disposables, "m.image", "/tmp/photo.png", "photo");
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

    verify(sayQuote).handleQuote(disposables, "wat arg");
    verify(ui, never()).appendStatus(any(), any(), startsWith("Unknown command:"));
  }
}
