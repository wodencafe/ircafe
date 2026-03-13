package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
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
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.IrcTypingPort;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class IrcMediatorMockVerifyTest {

  private final IrcMediatorInteractionPort irc = mock(IrcMediatorInteractionPort.class);
  private final IrcTypingPort typingPort = mock(IrcTypingPort.class);
  private final IrcReadMarkerPort readMarkerPort = mock(IrcReadMarkerPort.class);
  private final IrcNegotiatedFeaturePort negotiatedFeaturePort =
      mock(IrcNegotiatedFeaturePort.class);
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
  private final WhoisRoutingPort whoisRoutingState = mock(WhoisRoutingPort.class);
  private final CtcpRoutingPort ctcpRoutingState = mock(CtcpRoutingPort.class);
  private final ModeRoutingPort modeRoutingState = mock(ModeRoutingPort.class);
  private final AwayRoutingPort awayRoutingState = mock(AwayRoutingPort.class);
  private final ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState =
      mock(ChatHistoryRequestRoutingPort.class);
  private final JoinRoutingPort joinRoutingState = mock(JoinRoutingPort.class);
  private final LabeledResponseRoutingPort labeledResponseRoutingState =
      mock(LabeledResponseRoutingPort.class);
  private final PendingEchoMessagePort pendingEchoMessageState = mock(PendingEchoMessagePort.class);
  private final PendingInvitePort pendingInviteState = mock(PendingInvitePort.class);
  private final ServerIsupportStatePort serverIsupportState = mock(ServerIsupportStatePort.class);
  private final InboundModeEventHandler inboundModeEventHandler =
      mock(InboundModeEventHandler.class);
  private final IrcEventNotifierPort ircEventNotifierPort = mock(IrcEventNotifierPort.class);
  private final cafe.woden.ircclient.app.api.InterceptorIngestPort interceptorIngestPort =
      mock(cafe.woden.ircclient.app.api.InterceptorIngestPort.class);
  private final InboundIgnorePolicyPort inboundIgnorePolicy = mock(InboundIgnorePolicyPort.class);
  private final MonitorFallbackPort monitorFallbackPort = mock(MonitorFallbackPort.class);
  private final ApplicationEventPublisher applicationEventPublisher =
      mock(ApplicationEventPublisher.class);

  private final IrcMediator mediator =
      new IrcMediator(
          irc,
          typingPort,
          readMarkerPort,
          negotiatedFeaturePort,
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
          serverIsupportState,
          inboundModeEventHandler,
          ircEventNotifierPort,
          interceptorIngestPort,
          inboundIgnorePolicy,
          monitorFallbackPort,
          applicationEventPublisher);

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
        .bind(eq(ui), eq(targetCoordinator), any(CompositeDisposable.class), any(), any(), any());
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
        .bind(eq(ui), eq(targetCoordinator), any(CompositeDisposable.class), any(), any(), any());
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
  void quasselNetworkManagerRequestFromUiBinderOpensNetworkManager() {
    when(irc.events()).thenReturn(Flowable.never());
    when(ui.ircv3CapabilityToggleRequests()).thenReturn(Flowable.never());

    mediator.start();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<ParsedInput.BackendNamed>> quasselRequestCaptor =
        ArgumentCaptor.forClass(
            (Class<Consumer<ParsedInput.BackendNamed>>) (Class<?>) Consumer.class);
    verify(mediatorUiSubscriptionBinder)
        .bind(
            eq(ui),
            eq(targetCoordinator),
            any(CompositeDisposable.class),
            any(),
            any(),
            quasselRequestCaptor.capture());

    quasselRequestCaptor
        .getValue()
        .accept(
            new ParsedInput.BackendNamed(
                BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "quassel"));

    verify(outboundCommandDispatcher)
        .dispatch(
            any(CompositeDisposable.class),
            eq(
                new ParsedInput.BackendNamed(
                    BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER, "quassel")));
  }

  @Test
  void quasselSetupRequestFromUiBinderOpensSetupFlow() {
    when(irc.events()).thenReturn(Flowable.never());
    when(ui.ircv3CapabilityToggleRequests()).thenReturn(Flowable.never());

    mediator.start();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<ParsedInput.BackendNamed>> quasselSetupRequestCaptor =
        ArgumentCaptor.forClass(
            (Class<Consumer<ParsedInput.BackendNamed>>) (Class<?>) Consumer.class);
    verify(mediatorUiSubscriptionBinder)
        .bind(
            eq(ui),
            eq(targetCoordinator),
            any(CompositeDisposable.class),
            any(),
            any(),
            quasselSetupRequestCaptor.capture());

    quasselSetupRequestCaptor
        .getValue()
        .accept(new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel"));

    verify(outboundCommandDispatcher)
        .dispatch(
            any(CompositeDisposable.class),
            eq(new ParsedInput.BackendNamed(BackendNamedCommandNames.QUASSEL_SETUP, "quassel")));
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
  void channelMessageFromSenderClearsTypingIndicatorsAsDone() throws Exception {
    TargetRef chan = new TargetRef("libera", "#ircafe");

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera", new IrcEvent.ChannelMessage(Instant.now(), "#ircafe", "alice", "hello")));

    verify(ui).showTypingIndicator(chan, "alice", "done");
    verify(ui).showTypingActivity(chan, "done");
    verify(ui).showUsersTypingIndicator(chan, "alice", "done");
  }

  @Test
  void matrixSelfEchoChannelMessageResolvesPendingUsingNormalizedSelfCheck() throws Exception {
    TargetRef chan = new TargetRef("matrix", "#ircafe:matrix.example.org");
    TargetRef active = new TargetRef("matrix", "#other:matrix.example.org");
    Instant at = Instant.parse("2026-03-10T16:09:00Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat(
            "pending-matrix-1", chan, "@alice:matrix.example.org", "hello matrix", at);

    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(irc.currentNick("matrix")).thenReturn(Optional.of("@alice:matrix.example.org"));
    when(pendingEchoMessageState.consumeByTargetAndText(
            eq(chan), eq("@alice:matrix.example.org"), eq("hello matrix")))
        .thenReturn(Optional.of(pending));
    when(ui.resolvePendingOutgoingChat(
            eq(chan),
            eq("pending-matrix-1"),
            eq(at),
            eq("@alice:matrix.example.org"),
            eq("hello matrix"),
            eq("$m-event-1"),
            eq(Map.of("msgid", "$m-event-1"))))
        .thenReturn(true);

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "matrix",
            new IrcEvent.ChannelMessage(
                at,
                "#ircafe:matrix.example.org",
                "@alice:matrix.example.org",
                "hello matrix",
                "$m-event-1",
                Map.of("msgid", "$m-event-1"))));

    verify(pendingEchoMessageState)
        .consumeByTargetAndText(eq(chan), eq("@alice:matrix.example.org"), eq("hello matrix"));
    verify(ui)
        .resolvePendingOutgoingChat(
            eq(chan),
            eq("pending-matrix-1"),
            eq(at),
            eq("@alice:matrix.example.org"),
            eq("hello matrix"),
            eq("$m-event-1"),
            eq(Map.of("msgid", "$m-event-1")));
    verify(ui, never())
        .appendChatAt(
            eq(chan),
            any(),
            eq("@alice:matrix.example.org"),
            eq("hello matrix"),
            eq(false),
            eq("$m-event-1"),
            eq(Map.of("msgid", "$m-event-1")),
            any());
  }

  @Test
  void privateMessageFromPeerClearsTypingIndicatorAsDone() throws Exception {
    TargetRef pm = new TargetRef("libera", "alice");
    when(targetCoordinator.allowPrivateAutoOpenFromInbound(eq(pm), eq(false))).thenReturn(false);

    invokeOnServerIrcEvent(
        new ServerIrcEvent("libera", new IrcEvent.PrivateMessage(Instant.now(), "alice", "hello")));

    verify(ui).showTypingIndicator(pm, "alice", "done");
    verify(ui, never()).showTypingActivity(any(), anyString());
    verify(ui, never()).showUsersTypingIndicator(any(), anyString(), anyString());
  }

  @Test
  void connectionReadyEventIsForwardedToConnectivityCoordinator() throws Exception {
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    IrcEvent.ConnectionReady ready = new IrcEvent.ConnectionReady(Instant.now());

    invokeOnServerIrcEvent(new ServerIrcEvent("libera", ready));

    verify(connectionCoordinator).handleConnectivityEvent("libera", ready, active);
    verify(targetCoordinator).refreshInputEnabledForActiveTarget();
  }

  @Test
  void connectionFeaturesEventIsForwardedToConnectivityCoordinator() throws Exception {
    TargetRef active = new TargetRef("quassel", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    IrcEvent.ConnectionFeaturesUpdated updated =
        new IrcEvent.ConnectionFeaturesUpdated(
            Instant.now(),
            "quassel-phase=setup-required;detail=core is not configured for client logins");

    invokeOnServerIrcEvent(new ServerIrcEvent("quassel", updated));

    verify(connectionCoordinator).handleConnectivityEvent("quassel", updated, active);
    verify(targetCoordinator).refreshInputEnabledForActiveTarget();
  }

  @Test
  void modeSnapshotObservedRoutesToHandlerWithoutModeInterceptorIngest() throws Exception {
    IrcEvent.ChannelModeObserved snapshot =
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            "#ircafe",
            "",
            "+nrf [10j#R10]:5",
            IrcEvent.ChannelModeKind.SNAPSHOT,
            IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT);

    invokeOnServerIrcEvent(new ServerIrcEvent("libera", snapshot));

    verify(inboundModeEventHandler).handleChannelModeObserved("libera", snapshot);
    verify(interceptorIngestPort, never())
        .ingestEvent(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq(InterceptorEventType.MODE));
  }

  @Test
  void modeDeltaObservedRoutesToHandlerAndRecordsModeInterceptorEvent() throws Exception {
    IrcEvent.ChannelModeObserved delta =
        new IrcEvent.ChannelModeObserved(
            Instant.now(),
            "#ircafe",
            "FurBot",
            "+o Arca",
            IrcEvent.ChannelModeKind.DELTA,
            IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT);

    invokeOnServerIrcEvent(new ServerIrcEvent("libera", delta));

    verify(inboundModeEventHandler).handleChannelModeObserved("libera", delta);
    verify(interceptorIngestPort)
        .ingestEvent(
            eq("libera"),
            eq("#ircafe"),
            eq("FurBot"),
            anyString(),
            eq("+o Arca"),
            eq(InterceptorEventType.MODE));
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
            eq("libera"),
            eq("#ircafe"),
            eq("alice"),
            anyString(),
            anyString(),
            eq(InterceptorEventType.MESSAGE));
    ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher, times(1)).publishEvent(published.capture());
    IrcMediator.InboundMessageDedupDiagnostics event =
        assertInstanceOf(IrcMediator.InboundMessageDedupDiagnostics.class, published.getValue());
    assertEquals("libera", event.serverId());
    assertEquals("#ircafe", event.target());
    assertEquals("channel-message", event.eventType());
    assertEquals(1L, event.suppressedCount());
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
  void ctcpReceiveIngestsNormalizedCommandPayloadForInterceptors() throws Exception {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(uiSettingsPort.get()).thenReturn(UiSettingsSnapshot.defaults());

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.CtcpRequestReceived(
                Instant.now(), "alice", "WODEN", "hello world", null)));

    verify(interceptorIngestPort)
        .ingestEvent(
            eq("libera"),
            eq("status"),
            eq("alice"),
            anyString(),
            eq("WODEN hello world"),
            eq(InterceptorEventType.CTCP));
  }

  @Test
  void ctcpReceiveInActiveTargetModeDoesNotCreatePrivateMessagePeer() throws Exception {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef active = new TargetRef("libera", "#ircafe");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(uiSettingsPort.get()).thenReturn(UiSettingsSnapshot.defaults());

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera", new IrcEvent.CtcpRequestReceived(Instant.now(), "title", "TITLE", "", null)));

    verify(ui, never()).setPrivateMessageOnlineState("libera", "title", true);
  }

  @Test
  void ctcpReceiveWhenRoutedToPmMarksPrivateMessagePeerOnline() throws Exception {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(uiSettingsPort.get())
        .thenReturn(new UiSettingsSnapshot(List.of(), 15, 30, false, true, true, true, true));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.CtcpRequestReceived(Instant.now(), "alice", "VERSION", "", null)));

    verify(ui).setPrivateMessageOnlineState("libera", "alice", true);
  }

  @Test
  void serverNoticeWithoutTargetRoutesToStatusEvenWhenPrivateTargetIsActive() throws Exception {
    TargetRef status = new TargetRef("libera", "status");
    TargetRef activePrivate = new TargetRef("libera", "title");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(targetCoordinator.getActiveTarget()).thenReturn(activePrivate);

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.Notice(
                Instant.now(), "server", "", "Last login from services", "", Map.of())));

    verify(ui)
        .appendNoticeAt(
            eq(status),
            any(),
            eq("(notice) server"),
            eq("Last login from services"),
            eq(""),
            eq(Map.of()));
    verify(ui, never())
        .appendNoticeAt(
            eq(activePrivate),
            any(),
            anyString(),
            anyString(),
            anyString(),
            org.mockito.ArgumentMatchers.<Map<String, String>>any());
  }

  @Test
  void ctcpReceivePassesCommandAndValueToIrcEventNotifier() throws Exception {
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);
    when(targetCoordinator.getActiveTarget()).thenReturn(status);
    when(uiSettingsPort.get()).thenReturn(UiSettingsSnapshot.defaults());

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.CtcpRequestReceived(
                Instant.now(), "alice", "VERSION", "HexChat 2.16.2", null)));

    verify(ircEventNotifierPort)
        .notifyConfigured(
            eq(IrcEventNotificationRule.EventType.CTCP_RECEIVED),
            eq("libera"),
            eq((String) null),
            eq("alice"),
            eq(Boolean.FALSE),
            anyString(),
            eq("VERSION HexChat 2.16.2"),
            eq("libera"),
            eq("status"),
            eq("VERSION"),
            eq("HexChat 2.16.2"));
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

  @Test
  void readMarkerTimestampSelectorParsesAndAppliesEpoch() throws Exception {
    when(readMarkerPort.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("me"));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ReadMarkerObserved(
                Instant.parse("2026-02-16T12:31:00.000Z"),
                "server",
                "#ircafe",
                "timestamp=2026-02-16T12:30:00.000Z")));

    verify(ui)
        .setReadMarker(
            new TargetRef("libera", "#ircafe"),
            Instant.parse("2026-02-16T12:30:00.000Z").toEpochMilli());
    verify(ui).clearUnread(new TargetRef("libera", "#ircafe"));
  }

  @Test
  void readMarkerWildcardAppliesZeroEpoch() throws Exception {
    when(readMarkerPort.isReadMarkerAvailable("libera")).thenReturn(true);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("me"));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ReadMarkerObserved(
                Instant.parse("2026-02-16T12:31:00.000Z"), "server", "#ircafe", "*")));

    verify(ui).setReadMarker(new TargetRef("libera", "#ircafe"), 0L);
    verify(ui).clearUnread(new TargetRef("libera", "#ircafe"));
  }

  @Test
  void channelRedirectRemapsJoinOriginAndInitiatesJoinOnRedirectTarget() throws Exception {
    TargetRef origin = new TargetRef("libera", "status");
    when(joinRoutingState.recentOriginIfFresh("libera", "#old", Duration.ofSeconds(15)))
        .thenReturn(origin);

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ChannelRedirected(
                Instant.now(), "#old", "#new", 470, "Forwarding to another channel")));

    verify(joinRoutingState).rememberOrigin("libera", "#new", origin);
    verify(joinRoutingState).clear("libera", "#old");
    verify(runtimeConfig).rememberJoinedChannel("libera", "#new");
    verify(targetCoordinator).joinChannel(new TargetRef("libera", "#new"));
  }

  @Test
  void channelRedirectOnQuasselSkipsPersistingJoinedChannel() throws Exception {
    TargetRef origin = new TargetRef("quassel", "status");
    when(joinRoutingState.recentOriginIfFresh("quassel", "#old", Duration.ofSeconds(15)))
        .thenReturn(origin);
    when(serverRegistry.find("quassel"))
        .thenReturn(
            Optional.of(serverWithBackend("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "quassel",
            new IrcEvent.ChannelRedirected(
                Instant.now(), "#old", "#new", 470, "Forwarding to another channel")));

    verify(joinRoutingState).rememberOrigin("quassel", "#new", origin);
    verify(joinRoutingState).clear("quassel", "#old");
    verify(runtimeConfig, never()).rememberJoinedChannel("quassel", "#new");
    verify(targetCoordinator).joinChannel(new TargetRef("quassel", "#new"));
  }

  @Test
  void noSuchNickServerResponseFailsMatchingPendingPmAndAppendsPmError() throws Exception {
    TargetRef pm = new TargetRef("libera", "ghost");
    Instant at = Instant.parse("2026-03-02T18:53:57Z");
    PendingEchoMessagePort.PendingOutboundChat pending =
        new PendingEchoMessagePort.PendingOutboundChat("pending-1", pm, "Birbasaurus", "pie", at);
    when(pendingEchoMessageState.consumeOldestByTarget(eq(pm))).thenReturn(Optional.of(pending));

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ServerResponseLine(
                at,
                401,
                "No such nick/channel",
                ":osmium.libera.chat 401 me ghost :No such nick/channel")));

    verify(ui)
        .failPendingOutgoingChat(
            eq(pm),
            eq("pending-1"),
            eq(at),
            eq("Birbasaurus"),
            eq("pie"),
            eq("[401] No such nick/channel"));
    verify(ui)
        .appendErrorAt(
            eq(pm),
            eq(at),
            eq("(send)"),
            eq("Cannot deliver to ghost [401]: No such nick/channel"));
  }

  @Test
  void noSuchNickWithoutMatchingPendingDoesNotAppendPmError() throws Exception {
    TargetRef pm = new TargetRef("libera", "ghost");
    Instant at = Instant.parse("2026-03-02T18:53:57Z");
    when(pendingEchoMessageState.consumeOldestByTarget(eq(pm))).thenReturn(Optional.empty());

    invokeOnServerIrcEvent(
        new ServerIrcEvent(
            "libera",
            new IrcEvent.ServerResponseLine(
                at,
                401,
                "No such nick/channel",
                ":osmium.libera.chat 401 me ghost :No such nick/channel")));

    verify(ui, never())
        .failPendingOutgoingChat(eq(pm), anyString(), any(), anyString(), anyString(), anyString());
    verify(ui, never()).appendErrorAt(eq(pm), any(), eq("(send)"), anyString());
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

  private static IrcProperties.Server serverWithBackend(
      String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "core.example.net",
        4242,
        false,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }
}
