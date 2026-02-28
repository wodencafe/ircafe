package cafe.woden.ircclient.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasEngine;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.MediatorConnectionSubscriptionBinder;
import cafe.woden.ircclient.app.core.MediatorHistoryIngestOrchestrator;
import cafe.woden.ircclient.app.core.MediatorUiSubscriptionBinder;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.OutboundCommandDispatcher;
import cafe.woden.ircclient.app.outbound.OutboundDccCommandService;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.JoinRoutingState;
import cafe.woden.ircclient.app.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.PendingEchoMessageState;
import cafe.woden.ircclient.app.state.PendingInviteState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class IrcMediatorMockVerifyTest {

  private final IrcClientService irc = mock(IrcClientService.class);
  private final UiPort ui = mock(UiPort.class);
  private final CommandParser commandParser = mock(CommandParser.class);
  private final UserCommandAliasEngine userCommandAliasEngine = mock(UserCommandAliasEngine.class);
  private final ServerRegistry serverRegistry = mock(ServerRegistry.class);
  private final RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
  private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
  private final MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder =
      mock(MediatorConnectionSubscriptionBinder.class);
  private final MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder =
      mock(MediatorUiSubscriptionBinder.class);
  private final MediatorHistoryIngestOrchestrator mediatorHistoryIngestOrchestrator =
      mock(MediatorHistoryIngestOrchestrator.class);
  private final OutboundCommandDispatcher outboundCommandDispatcher =
      mock(OutboundCommandDispatcher.class);
  private final OutboundDccCommandService outboundDccCommandService =
      mock(OutboundDccCommandService.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final UiSettingsPort uiSettingsPort = mock(UiSettingsPort.class);
  private final TrayNotificationsPort trayNotificationsPort = mock(TrayNotificationsPort.class);
  private final NotificationRuleMatcherPort notificationRuleMatcherPort =
      mock(NotificationRuleMatcherPort.class);
  private final UserInfoEnrichmentService userInfoEnrichmentService =
      mock(UserInfoEnrichmentService.class);
  private final UserListStore userListStore = mock(UserListStore.class);
  private final WhoisRoutingState whoisRoutingState = mock(WhoisRoutingState.class);
  private final CtcpRoutingState ctcpRoutingState = mock(CtcpRoutingState.class);
  private final ModeRoutingState modeRoutingState = mock(ModeRoutingState.class);
  private final AwayRoutingState awayRoutingState = mock(AwayRoutingState.class);
  private final ChatHistoryRequestRoutingState chatHistoryRequestRoutingState =
      mock(ChatHistoryRequestRoutingState.class);
  private final JoinRoutingState joinRoutingState = mock(JoinRoutingState.class);
  private final LabeledResponseRoutingState labeledResponseRoutingState =
      mock(LabeledResponseRoutingState.class);
  private final PendingEchoMessageState pendingEchoMessageState =
      mock(PendingEchoMessageState.class);
  private final PendingInviteState pendingInviteState = mock(PendingInviteState.class);
  private final InboundModeEventHandler inboundModeEventHandler =
      mock(InboundModeEventHandler.class);
  private final IrcEventNotifierPort ircEventNotifierPort = mock(IrcEventNotifierPort.class);
  private final cafe.woden.ircclient.app.api.InterceptorIngestPort interceptorIngestPort =
      mock(cafe.woden.ircclient.app.api.InterceptorIngestPort.class);
  private final InboundIgnorePolicyPort inboundIgnorePolicy = mock(InboundIgnorePolicyPort.class);
  private final MonitorFallbackPort monitorFallbackPort = mock(MonitorFallbackPort.class);

  private final IrcMediator mediator =
      new IrcMediator(
          irc,
          ui,
          commandParser,
          userCommandAliasEngine,
          serverRegistry,
          runtimeConfig,
          connectionCoordinator,
          mediatorConnectionSubscriptionBinder,
          mediatorUiSubscriptionBinder,
          mediatorHistoryIngestOrchestrator,
          outboundCommandDispatcher,
          outboundDccCommandService,
          targetCoordinator,
          uiSettingsPort,
          trayNotificationsPort,
          notificationRuleMatcherPort,
          userInfoEnrichmentService,
          userListStore,
          whoisRoutingState,
          ctcpRoutingState,
          modeRoutingState,
          awayRoutingState,
          chatHistoryRequestRoutingState,
          joinRoutingState,
          labeledResponseRoutingState,
          pendingEchoMessageState,
          pendingInviteState,
          inboundModeEventHandler,
          ircEventNotifierPort,
          interceptorIngestPort,
          inboundIgnorePolicy,
          monitorFallbackPort);

  @Test
  void startBindsUiIrcAndConnectionCollaboratorsInOrderOnce() {
    when(irc.events()).thenReturn(Flowable.never());
    when(ui.ircv3CapabilityToggleRequests()).thenReturn(Flowable.never());

    mediator.start();
    mediator.start();

    InOrder inOrder =
        inOrder(mediatorUiSubscriptionBinder, irc, ui, mediatorConnectionSubscriptionBinder);
    inOrder
        .verify(mediatorUiSubscriptionBinder)
        .bind(eq(ui), eq(targetCoordinator), any(CompositeDisposable.class), any(), any());
    inOrder.verify(irc).events();
    inOrder.verify(ui).ircv3CapabilityToggleRequests();
    inOrder
        .verify(mediatorConnectionSubscriptionBinder)
        .bind(
            eq(ui),
            eq(connectionCoordinator),
            eq(targetCoordinator),
            eq(serverRegistry),
            any(CompositeDisposable.class));

    verify(mediatorUiSubscriptionBinder, times(1))
        .bind(eq(ui), eq(targetCoordinator), any(CompositeDisposable.class), any(), any());
    verify(mediatorConnectionSubscriptionBinder, times(1))
        .bind(
            eq(ui),
            eq(connectionCoordinator),
            eq(targetCoordinator),
            eq(serverRegistry),
            any(CompositeDisposable.class));
  }

  @Test
  void outgoingLineExpansionPrintsWarningsAndDispatchesParsedLinesInOrder() throws Exception {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    ParsedInput whois = new ParsedInput.Whois("alice");
    ParsedInput message = new ParsedInput.Msg("alice", "hi");

    when(targetCoordinator.getActiveTarget()).thenReturn(channel);
    when(userCommandAliasEngine.expand("/alias", channel))
        .thenReturn(
            new UserCommandAliasEngine.ExpansionResult(
                List.of("/whois alice", "/msg alice hi"), List.of("Alias warning")));
    when(commandParser.parse("/whois alice")).thenReturn(whois);
    when(commandParser.parse("/msg alice hi")).thenReturn(message);

    invokeHandleOutgoingLine("/alias");

    verify(ui).appendStatus(status, "(alias)", "Alias warning");

    InOrder inOrder = inOrder(commandParser, outboundCommandDispatcher);
    inOrder.verify(commandParser).parse("/whois alice");
    inOrder.verify(outboundCommandDispatcher).dispatch(any(CompositeDisposable.class), same(whois));
    inOrder.verify(commandParser).parse("/msg alice hi");
    inOrder
        .verify(outboundCommandDispatcher)
        .dispatch(any(CompositeDisposable.class), same(message));
  }

  @Test
  void outgoingLineWarningsUseSafeStatusWhenNoActiveTarget() throws Exception {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(userCommandAliasEngine.expand("/alias", status))
        .thenReturn(new UserCommandAliasEngine.ExpansionResult(List.of(), List.of("warn")));

    invokeHandleOutgoingLine("/alias");

    verify(ui).appendStatus(status, "(alias)", "warn");
  }

  @Test
  void mentionInActiveChannelStillRecordsHighlightNotification() throws Exception {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("bob"));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera", new IrcEvent.ChannelMessage(Instant.now(), "#ircafe", "alice", "hi bob")));

    verify(ui).recordHighlight(chan, "alice", "hi bob");
    verify(ui, never()).markHighlight(chan);
    verify(trayNotificationsPort).notifyHighlight("libera", "#ircafe", "alice", "hi bob");
  }

  @Test
  void mentionInBackgroundChannelMarksUnreadHighlightAndRecordsNotification() throws Exception {
    TargetRef active = new TargetRef("libera", "#other");
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("bob"));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera", new IrcEvent.ChannelMessage(Instant.now(), "#ircafe", "alice", "hi bob")));

    verify(ui).markHighlight(chan);
    verify(ui).recordHighlight(chan, "alice", "hi bob");
    verify(trayNotificationsPort).notifyHighlight("libera", "#ircafe", "alice", "hi bob");
  }

  @Test
  void duplicateChannelMessageByMsgIdIsSuppressedBeforeUiAndSideEffects() throws Exception {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("bob"));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ChannelMessage(
                Instant.now(), "#ircafe", "alice", "hello", "dup-1", Map.of("msgid", "dup-1"))));
    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ChannelMessage(
                Instant.now(),
                "#ircafe",
                "alice",
                "hello again",
                "dup-1",
                Map.of("msgid", "dup-1"))));

    verify(ui, times(1))
        .appendChatAt(
            eq(chan), any(), eq("alice"), anyString(), eq(false), eq("dup-1"), any(), any());
    verify(interceptorIngestPort, times(1))
        .ingestEvent(
            eq("libera"), eq("#ircafe"), eq("alice"), anyString(), anyString(), eq(InterceptorEventType.MESSAGE));
  }

  @Test
  void duplicatePrivateMessageFallsBackToTagMsgIdWhenMessageIdFieldIsBlank() throws Exception {
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.allowPrivateAutoOpenFromInbound(eq(pm), eq(false))).thenReturn(false);

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.PrivateMessage(
                Instant.now(), "alice", "hello", "", Map.of("msgid", "pm-dup-1"))));
    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.PrivateMessage(
                Instant.now(), "alice", "hello again", "", Map.of("msgid", "pm-dup-1"))));

    verify(ui, times(1))
        .appendChatAt(eq(pm), any(), eq("alice"), anyString(), eq(false), anyString(), any());
    verify(interceptorIngestPort, times(1))
        .ingestEvent(
            eq("libera"),
            eq("pm:alice"),
            eq("alice"),
            anyString(),
            anyString(),
            eq(InterceptorEventType.PRIVATE_MESSAGE));
    verify(trayNotificationsPort, times(1)).notifyPrivateMessage("libera", "alice", "hello");
  }

  @Test
  void ruleMatchInActiveChannelStillRecordsNotificationWithoutUnreadHighlight() throws Exception {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(chan);
    when(notificationRuleMatcherPort.matchAll("deploy now"))
        .thenReturn(List.of(new NotificationRuleMatch("Rule A", "deploy", 0, 6, "#FF9900")));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ChannelMessage(Instant.now(), "#ircafe", "alice", "deploy now")));

    verify(ui).recordRuleMatch(eq(chan), eq("alice"), eq("Rule A"), anyString());
    verify(ui, never()).markHighlight(chan);
  }

  @Test
  void ruleMatchInBackgroundChannelRecordsNotificationAndUnreadHighlight() throws Exception {
    TargetRef active = new TargetRef("libera", "#other");
    TargetRef chan = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(notificationRuleMatcherPort.matchAll("deploy now"))
        .thenReturn(List.of(new NotificationRuleMatch("Rule A", "deploy", 0, 6, "#FF9900")));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ChannelMessage(Instant.now(), "#ircafe", "alice", "deploy now")));

    verify(ui).recordRuleMatch(eq(chan), eq("alice"), eq("Rule A"), anyString());
    verify(ui).markHighlight(chan);
  }

  @Test
  void extendedJoinSetnameUpdatesRosterWithoutTranscriptStatusLine() throws Exception {
    IrcEvent.UserSetNameObserved event =
        new IrcEvent.UserSetNameObserved(
            Instant.now(),
            "alice",
            "Alice Liddell",
            IrcEvent.UserSetNameObserved.Source.EXTENDED_JOIN);

    invokeOnServerIrcEvent(new ServerIrcEvent("libera", event));

    verify(targetCoordinator).onUserSetNameObserved("libera", event);
    verify(ui, never()).appendStatusAt(any(), any(), eq("(setname)"), anyString());
  }

  @Test
  void explicitSetnameStillAppendsStatusLine() throws Exception {
    when(targetCoordinator.sharedChannelTargetsForNick("libera", "alice")).thenReturn(List.of());
    IrcEvent.UserSetNameObserved event =
        new IrcEvent.UserSetNameObserved(
            Instant.now(), "alice", "Alice Liddell", IrcEvent.UserSetNameObserved.Source.SETNAME);

    invokeOnServerIrcEvent(new ServerIrcEvent("libera", event));

    verify(targetCoordinator).onUserSetNameObserved("libera", event);
    verify(ui)
        .appendStatusAt(any(), any(), eq("(setname)"), eq("alice set name to: Alice Liddell"));
  }

  private void invokeHandleOutgoingLine(String raw) throws Exception {
    Method method = IrcMediator.class.getDeclaredMethod("handleOutgoingLine", String.class);
    method.setAccessible(true);
    method.invoke(mediator, raw);
  }

  private void invokeOnServerIrcEvent(ServerIrcEvent event) throws Exception {
    Method method = IrcMediator.class.getDeclaredMethod("onServerIrcEvent", ServerIrcEvent.class);
    method.setAccessible(true);
    method.invoke(mediator, event);
  }
}
