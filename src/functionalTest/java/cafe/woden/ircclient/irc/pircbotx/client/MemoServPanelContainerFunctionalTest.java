package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ChannelMetadataPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.MediatorAlertNotificationHandler;
import cafe.woden.ircclient.app.core.MediatorChannelMembershipEventHandler;
import cafe.woden.ircclient.app.core.MediatorChannelStateEventHandler;
import cafe.woden.ircclient.app.core.MediatorConnectionSubscriptionBinder;
import cafe.woden.ircclient.app.core.MediatorConnectivityLifecycleOrchestrator;
import cafe.woden.ircclient.app.core.MediatorInboundEventPreparationService;
import cafe.woden.ircclient.app.core.MediatorInboundTextEventHandler;
import cafe.woden.ircclient.app.core.MediatorInviteEventHandler;
import cafe.woden.ircclient.app.core.MediatorIrcv3EventHandler;
import cafe.woden.ircclient.app.core.MediatorIrcv3PresenceEventHandler;
import cafe.woden.ircclient.app.core.MediatorNotificationSupport;
import cafe.woden.ircclient.app.core.MediatorOutboundUiActionHandler;
import cafe.woden.ircclient.app.core.MediatorPendingEchoFailureHandler;
import cafe.woden.ircclient.app.core.MediatorRosterStatusEventHandler;
import cafe.woden.ircclient.app.core.MediatorServerStatusEventHandler;
import cafe.woden.ircclient.app.core.MediatorTargetUiSupport;
import cafe.woden.ircclient.app.core.MediatorUiSubscriptionBinder;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.dcc.OutboundDccCommandService;
import cafe.woden.ircclient.app.translation.MessageTranslationDispatcher;
import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.RuntimeConfigDiagnosticsAdapter;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.properties.ZncProperties;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.diagnostics.ApplicationDiagnosticsService;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.SpringRuntimeEventsService;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.pircbotx.listener.PircbotxBridgeListenerFactory;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.playback.NoOpPlaybackCursorProvider;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.state.ServerIsupportState;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.testutil.FunctionalTestWiringSupport;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import cafe.woden.ircclient.ui.SwingUiPort;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.backend.BackendUiContext;
import cafe.woden.ircclient.ui.backend.BackendUiProfile;
import cafe.woden.ircclient.ui.backend.BackendUiProfileProvider;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.settings.spellcheck.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.shell.StatusBar;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeService;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import cafe.woden.ircclient.util.VirtualThreads;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.awt.Component;
import java.awt.Container;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class MemoServPanelContainerFunctionalTest {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(80);
  private static final Duration MEMOSERV_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration UI_TIMEOUT = Duration.ofSeconds(20);
  private static final long POLL_INTERVAL_MS = 50L;
  private static final int IRC_PORT = 6667;

  @AfterEach
  void tearDownSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void refreshButtonListsMemosAgainstContainerIrcdThroughLiveUiAndMediator() throws Exception {
    ContainerMemoServConfig cfg = ContainerMemoServConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "MemoServ container functional test disabled. Set -Dmemoserv.it.container.functional.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    try (GenericContainer<?> ircServer =
        new GenericContainer<>(DockerImageName.parse(cfg.ircImage()))
            .withExposedPorts(IRC_PORT)
            .withEnv("TZ", "UTC")
            .withEnv("PUID", "1000")
            .withEnv("PGID", "1000")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(STARTUP_TIMEOUT)) {
      ircServer.start();

      RuntimeIrcConfig appCfg =
          cfg.runtimeConfig(ircServer.getHost(), ircServer.getMappedPort(IRC_PORT));
      try (ServiceFixture runtime = newService(appCfg);
          ScriptedMemoServ memoServ =
              ScriptedMemoServ.connect(appCfg.host(), appCfg.port(), cfg.memoServNick());
          ChatFixture chat = newChatFixture(runtime.service());
          MediatorFixture mediator = newMediatorFixture(runtime.service(), chat.ui(), appCfg)) {
        TestSubscriber<ServerIrcEvent> events = runtime.service().events().test();
        try {
          int readyCount = countEvents(events, appCfg.serverId(), IrcEvent.ConnectionReady.class);
          runtime.service().connect(appCfg.serverId()).blockingAwait();
          awaitNextEvent(
              events,
              appCfg.serverId(),
              IrcEvent.ConnectionReady.class,
              readyCount,
              CONNECT_TIMEOUT);

          TargetRef memoServTarget = TargetRef.memoServ(appCfg.serverId());
          onEdt(
              () -> {
                chat.chat().setActiveTarget(memoServTarget);
                chat.chat().setInputEnabled(true);
              });
          flushEdt();

          JTable table = requireComponent(chat.chat(), JTable.class, "memoserv.table");
          JButton refresh = requireComponent(chat.chat(), JButton.class, "memoserv.refreshButton");

          Thread serviceThread =
              new Thread(
                  () -> memoServ.replyToList(appCfg.nick(), cfg.memoRows()), "test-memoserv");
          serviceThread.start();

          onEdt(refresh::doClick);

          waitFor(() -> onEdtCall(() -> table.getRowCount() >= cfg.memoRows().size()), UI_TIMEOUT);
          serviceThread.join(MEMOSERV_TIMEOUT.toMillis());
          assertFalseAlive(serviceThread);

          onEdt(
              () -> {
                assertEquals(2, table.getRowCount());
                assertEquals("1", table.getValueAt(0, 2));
                assertEquals("alice", table.getValueAt(0, 3));
                assertEquals("container memo one", table.getValueAt(0, 5));
                assertEquals("2", table.getValueAt(1, 2));
                assertEquals("bob", table.getValueAt(1, 3));
                assertEquals("container memo two", table.getValueAt(1, 5));
              });
        } finally {
          try {
            runtime.service().disconnect(appCfg.serverId(), "MemoServ functional shutdown");
          } catch (Exception ignored) {
          }
          events.cancel();
        }
      }
    }
  }

  private static void assertFalseAlive(Thread thread) {
    assertNotNull(thread);
    assertTrue(!thread.isAlive(), "MemoServ script thread did not finish");
  }

  private static ChatFixture newChatFixture(IrcClientService irc) throws Exception {
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    when(serverTree.managedChannelsChangedByServer()).thenReturn(Flowable.never());
    when(serverTree.openChannelsForServer(anyString())).thenReturn(List.of());
    when(serverTree.managedChannelsForServer(anyString())).thenReturn(List.of());
    when(serverTree.channelSortModeForServer(anyString()))
        .thenReturn(ServerTreeDockable.ChannelSortMode.CUSTOM);

    NotificationStore notificationStore = new NotificationStore();
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    ModeRoutingPort modeRoutingState = mock(ModeRoutingPort.class);
    ServerIsupportStatePort serverIsupportState =
        FunctionalTestWiringSupport.fallbackIsupportState();
    BackendUiProfileProvider backendUiProfileProvider = mock(BackendUiProfileProvider.class);
    when(backendUiProfileProvider.backendUiContext()).thenReturn(BackendUiContext.ircOnly());
    when(backendUiProfileProvider.profileForServer(anyString()))
        .thenAnswer(
            invocation ->
                BackendUiProfile.ircOnly(Objects.toString(invocation.getArgument(0), "")));
    MessageActionCapabilityPolicy messageActionCapabilityPolicy =
        mock(MessageActionCapabilityPolicy.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    IgnoreListDialog ignoreListDialog = mock(IgnoreListDialog.class);
    MonitorListService monitorListService = mock(MonitorListService.class);
    when(monitorListService.changes()).thenReturn(Flowable.never());
    when(monitorListService.listNicks(anyString())).thenReturn(List.of());
    UserListStore userListStore = mock(UserListStore.class);
    when(userListStore.get(anyString(), anyString())).thenReturn(List.of());
    UserListDockable usersDock = mock(UserListDockable.class);
    NickContextMenuFactory nickContextMenuFactory = new NickContextMenuFactory();
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    ChannelMetadataPort channelMetadata = mock(ChannelMetadataPort.class);
    ChatLogViewerService chatLogViewerService = mock(ChatLogViewerService.class);
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.changes()).thenReturn(Flowable.never());
    DccTransferStore dccTransferStore = new DccTransferStore();
    TerminalDockable terminalDockable = new TerminalDockable(mock(ConsoleTeeService.class));
    ApplicationDiagnosticsService applicationDiagnosticsService =
        mock(ApplicationDiagnosticsService.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    JfrRuntimeEventsService jfrRuntimeEventsService =
        new JfrRuntimeEventsService(new RuntimeConfigDiagnosticsAdapter(runtimeConfig));
    SpringRuntimeEventsService springRuntimeEventsService = new SpringRuntimeEventsService();
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    SpellcheckSettingsBus spellcheckSettingsBus = mock(SpellcheckSettingsBus.class);
    CommandHistoryStore commandHistoryStore = mock(CommandHistoryStore.class);
    ExecutorService logViewerExecutor =
        VirtualThreads.newSingleThreadExecutor("test-memoserv-log-viewer");
    ExecutorService interceptorRefreshExecutor =
        VirtualThreads.newSingleThreadExecutor("test-memoserv-interceptor");

    AtomicReference<ChatDockable> chatRef = new AtomicReference<>();
    onEdt(
        () ->
            chatRef.set(
                FunctionalTestWiringSupport.newChatDockable(
                    transcripts,
                    serverTree,
                    notificationStore,
                    activationBus,
                    outboundBus,
                    irc,
                    modeRoutingState,
                    serverIsupportState,
                    backendUiProfileProvider,
                    messageActionCapabilityPolicy,
                    activeInputRouter,
                    ignoreListService,
                    ignoreStatusService,
                    ignoreListDialog,
                    monitorListService,
                    userListStore,
                    usersDock,
                    nickContextMenuFactory,
                    proxyResolver,
                    chatHistoryService,
                    channelMetadata,
                    chatLogViewerService,
                    interceptorStore,
                    dccTransferStore,
                    terminalDockable,
                    applicationDiagnosticsService,
                    jfrRuntimeEventsService,
                    springRuntimeEventsService,
                    settingsBus,
                    spellcheckSettingsBus,
                    commandHistoryStore,
                    logViewerExecutor,
                    interceptorRefreshExecutor)));

    ChatDockable chat = chatRef.get();
    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            chat,
            transcripts,
            mock(MentionPatternRegistry.class),
            notificationStore,
            usersDock,
            mock(StatusBar.class),
            mock(ConnectButton.class),
            mock(DisconnectButton.class),
            activationBus,
            outboundBus,
            mock(ChatDockManager.class),
            activeInputRouter);
    return new ChatFixture(chat, ui, logViewerExecutor, interceptorRefreshExecutor);
  }

  private static MediatorFixture newMediatorFixture(
      PircbotxIrcClientService service, UiPort ui, RuntimeIrcConfig appCfg) {
    IrcMediatorInteractionPort mediatorIrc = IrcMediatorInteractionPort.from(service);
    TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
    TargetRef memoServTarget = TargetRef.memoServ(appCfg.serverId());
    when(targetCoordinator.getActiveTarget()).thenReturn(memoServTarget);
    when(targetCoordinator.safeStatusTarget())
        .thenReturn(new TargetRef(appCfg.serverId(), "status"));
    when(targetCoordinator.allowPrivateAutoOpenFromInbound(any(TargetRef.class), eq(false)))
        .thenReturn(true);

    MediatorInboundEventPreparationService preparationService =
        new MediatorInboundEventPreparationService(
            mediatorIrc,
            mock(NotificationRuleMatcherPort.class),
            allowAllInbound(),
            targetCoordinator);
    MediatorTargetUiSupport targetUiSupport =
        new MediatorTargetUiSupport(ui, targetCoordinator, preparationService);
    MediatorNotificationSupport notificationSupport =
        new MediatorNotificationSupport(
            mock(IrcEventNotifierPort.class),
            mock(InterceptorIngestPort.class),
            mock(UserListStore.class),
            targetCoordinator,
            targetUiSupport);
    PendingEchoMessagePort pendingEchoState = mock(PendingEchoMessagePort.class);
    when(pendingEchoState.consumeByTargetAndText(any(TargetRef.class), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(pendingEchoState.consumePrivateFallback(anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());

    PircbotxFunctionalRuntimeFixtures.Runtime ircv3Runtime =
        PircbotxFunctionalRuntimeFixtures.runtime();
    MediatorInboundTextEventHandler inboundTextHandler =
        new MediatorInboundTextEventHandler(
            mock(IrcNegotiatedFeaturePort.class),
            ircv3Runtime.messageMutation(),
            ircv3Runtime.messageId(),
            ui,
            targetCoordinator,
            mock(cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService.class),
            pendingEchoState,
            mock(OutboundDccCommandService.class),
            mock(TrayNotificationsPort.class),
            mock(UiSettingsPort.class),
            mock(IrcEventNotifierPort.class),
            mock(ApplicationEventPublisher.class),
            mock(CtcpRoutingPort.class),
            preparationService,
            mock(MessageTranslationDispatcher.class));

    IrcMediator mediator =
        new IrcMediator(
            mediatorIrc,
            emptyUiEvents(),
            ui,
            mock(cafe.woden.ircclient.config.servers.ServerRegistry.class),
            mock(ConnectionCoordinator.class),
            mock(MediatorConnectivityLifecycleOrchestrator.class),
            mock(MediatorServerStatusEventHandler.class),
            mock(MediatorInviteEventHandler.class),
            mock(MediatorChannelMembershipEventHandler.class),
            mock(MediatorRosterStatusEventHandler.class),
            mock(MediatorIrcv3PresenceEventHandler.class),
            mock(MediatorIrcv3EventHandler.class),
            mock(MediatorAlertNotificationHandler.class),
            mock(MediatorChannelStateEventHandler.class),
            mock(MediatorOutboundUiActionHandler.class),
            notificationSupport,
            mock(MediatorPendingEchoFailureHandler.class),
            targetUiSupport,
            mock(MediatorConnectionSubscriptionBinder.class),
            mock(MediatorUiSubscriptionBinder.class),
            targetCoordinator,
            preparationService,
            inboundTextHandler);
    mediator.start();
    return new MediatorFixture(mediator);
  }

  private static InboundIgnorePolicyPort allowAllInbound() {
    return (serverId, fromNick, hostmask, isCtcp, inboundLevels, inboundChannel, inboundText) ->
        InboundIgnorePolicyPort.Decision.ALLOW;
  }

  private static UiEventPort emptyUiEvents() {
    return new UiEventPort() {
      @Override
      public Flowable<TargetRef> targetSelections() {
        return Flowable.never();
      }

      @Override
      public Flowable<TargetRef> targetActivations() {
        return Flowable.never();
      }

      @Override
      public Flowable<PrivateMessageRequest> privateMessageRequests() {
        return Flowable.never();
      }

      @Override
      public Flowable<cafe.woden.ircclient.app.api.UserActionRequest> userActionRequests() {
        return Flowable.never();
      }

      @Override
      public Flowable<String> outboundLines() {
        return Flowable.never();
      }

      @Override
      public Flowable<Object> connectClicks() {
        return Flowable.never();
      }

      @Override
      public Flowable<Object> disconnectClicks() {
        return Flowable.never();
      }

      @Override
      public Flowable<String> connectServerRequests() {
        return Flowable.never();
      }

      @Override
      public Flowable<String> disconnectServerRequests() {
        return Flowable.never();
      }

      @Override
      public Flowable<ParsedInput.BackendNamed> backendNamedCommandRequests() {
        return Flowable.never();
      }

      @Override
      public Flowable<TargetRef> closeTargetRequests() {
        return Flowable.never();
      }

      @Override
      public Flowable<TargetRef> clearLogRequests() {
        return Flowable.never();
      }
    };
  }

  private static ServiceFixture newService(RuntimeIrcConfig cfg) {
    LinkedHashMap<String, IrcProperties.Server> serversById = new LinkedHashMap<>();
    serversById.put(cfg.serverId(), cfg.toServer());
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    when(serverCatalog.require(anyString()))
        .thenAnswer(
            invocation -> {
              String sid = Objects.toString(invocation.getArgument(0), "").trim();
              IrcProperties.Server server = serversById.get(sid);
              if (server == null) {
                throw new IllegalArgumentException("Unknown server id: " + sid);
              }
              return server;
            });
    when(serverCatalog.find(anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(
                    serversById.get(Objects.toString(invocation.getArgument(0), "").trim())));
    when(serverCatalog.containsId(anyString()))
        .thenAnswer(
            invocation ->
                serversById.containsKey(Objects.toString(invocation.getArgument(0), "").trim()));

    IrcProperties props =
        new IrcProperties(
            new IrcProperties.Client(
                "IRCafe MemoServ Functional",
                new IrcProperties.Reconnect(false, 250, 1_000, 1.5, 0, 3),
                null,
                null,
                null),
            List.copyOf(serversById.values()));
    PircbotxFunctionalRuntimeFixtures.Runtime ircv3Runtime =
        PircbotxFunctionalRuntimeFixtures.runtime();
    Ircv3StsPolicyService stsPolicies = ircv3Runtime.stsPolicyService();
    PircbotxInputParserHookInstaller hookInstaller =
        new PircbotxInputParserHookInstaller(stsPolicies, ircv3Runtime.catalogs());
    SojuProperties sojuProps = new SojuProperties(Map.of(), new SojuProperties.Discovery(false));
    ZncProperties zncProps = new ZncProperties(Map.of(), new ZncProperties.Discovery(false));
    ServerProxyResolver proxyResolver = new ServerProxyResolver(serverCatalog);
    PircbotxBotFactory botFactory =
        new PircbotxBotFactory(
            proxyResolver,
            sojuProps,
            null,
            Ircv3ExtensionCatalog.builtInCatalog(),
            ircv3Runtime.catalogs());
    BouncerBackendRegistry bouncerBackends = mock(BouncerBackendRegistry.class);
    BouncerDiscoveryEventPort bouncerDiscoveryEvents = mock(BouncerDiscoveryEventPort.class);
    when(bouncerBackends.backendIds()).thenReturn(Set.of());
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    ScheduledExecutorService heartbeatExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("test-memoserv-heartbeat"));
    ScheduledExecutorService reconnectExec =
        Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("test-memoserv-reconnect"));
    PircbotxConnectionTimersRx timers =
        new PircbotxConnectionTimersRx(props, serverCatalog, heartbeatExec, reconnectExec);
    ServerIsupportState serverIsupportState = new ServerIsupportState();
    PircbotxBridgeListenerFactory bridgeListenerFactory =
        new PircbotxBridgeListenerFactory(
            bouncerBackends,
            bouncerDiscoveryEvents,
            new NoOpPlaybackCursorProvider(),
            serverIsupportState,
            sojuProps,
            zncProps,
            ircv3Runtime.catalogs(),
            ircv3Runtime.serverTime(),
            ircv3Runtime.messageTags());
    PircbotxIrcClientService service =
        new PircbotxIrcClientService(
            props,
            serverCatalog,
            hookInstaller,
            botFactory,
            bridgeListenerFactory,
            (CtcpReplyRuntimeConfigPort) runtimeConfig,
            runtimeConfig::readDefaultQuitMessage,
            stsPolicies,
            Ircv3OutboundCommandRuntimeCatalog.applicationClasspath(),
            bouncerBackends,
            bouncerDiscoveryEvents,
            timers,
            serverIsupportState);
    return new ServiceFixture(service, heartbeatExec, reconnectExec);
  }

  private static <T extends IrcEvent> int countEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    return matchingEvents(events, serverId, eventType).size();
  }

  private static <T extends IrcEvent> T awaitNextEvent(
      TestSubscriber<ServerIrcEvent> events,
      String serverId,
      Class<T> eventType,
      int alreadySeenCount,
      Duration timeout)
      throws InterruptedException {
    long deadlineNs = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNs) {
      List<T> matches = matchingEvents(events, serverId, eventType);
      if (matches.size() > alreadySeenCount) {
        return matches.get(alreadySeenCount);
      }
      Thread.sleep(POLL_INTERVAL_MS);
    }
    throw new AssertionError("Timed out waiting for " + eventType.getSimpleName());
  }

  private static <T extends IrcEvent> List<T> matchingEvents(
      TestSubscriber<ServerIrcEvent> events, String serverId, Class<T> eventType) {
    ArrayList<T> matches = new ArrayList<>();
    for (ServerIrcEvent event : new ArrayList<>(events.values())) {
      if (event == null || !Objects.equals(serverId, event.serverId())) continue;
      if (eventType.isInstance(event.event())) {
        matches.add(eventType.cast(event.event()));
      }
    }
    return matches;
  }

  private static void waitFor(ThrowingBooleanSupplier condition, Duration timeout)
      throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(POLL_INTERVAL_MS);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void onEdt(ThrowingRunnable runnable)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    AtomicReference<T> out = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static <T extends Component> T requireComponent(
      Component root, Class<T> type, String name) {
    T found = findByName(root, type, name);
    assertNotNull(found, "Missing component named " + name);
    return found;
  }

  private static <T extends Component> T findByName(Component root, Class<T> type, String name) {
    if (root == null || type == null || name == null) return null;
    if (type.isInstance(root) && name.equals(root.getName())) {
      return type.cast(root);
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findByName(child, type, name);
      if (found != null) return found;
    }
    return null;
  }

  private static ThreadFactory namedDaemonFactory(String name) {
    String threadName = Objects.toString(name, "").trim();
    if (threadName.isEmpty()) threadName = "test-memoserv";
    final String finalThreadName = threadName;
    return runnable -> {
      Thread thread = new Thread(runnable, finalThreadName);
      thread.setDaemon(true);
      return thread;
    };
  }

  private static void invokeChatShutdown(ChatDockable chat) throws Exception {
    Method method = ChatDockable.class.getDeclaredMethod("shutdown");
    method.setAccessible(true);
    method.invoke(chat);
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private record ChatFixture(
      ChatDockable chat,
      SwingUiPort ui,
      ExecutorService logViewerExecutor,
      ExecutorService interceptorRefreshExecutor)
      implements AutoCloseable {
    @Override
    public void close() throws Exception {
      onEdt(() -> invokeChatShutdown(chat));
      logViewerExecutor.shutdownNow();
      interceptorRefreshExecutor.shutdownNow();
      logViewerExecutor.awaitTermination(2, TimeUnit.SECONDS);
      interceptorRefreshExecutor.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  private record MediatorFixture(IrcMediator mediator) implements AutoCloseable {
    @Override
    public void close() {
      mediator.stop();
    }
  }

  private record ServiceFixture(
      PircbotxIrcClientService service,
      ScheduledExecutorService heartbeatExec,
      ScheduledExecutorService reconnectExec)
      implements AutoCloseable {
    @Override
    public void close() throws Exception {
      service.shutdownNow();
      heartbeatExec.shutdownNow();
      reconnectExec.shutdownNow();
      heartbeatExec.awaitTermination(2, TimeUnit.SECONDS);
      reconnectExec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  private record RuntimeIrcConfig(
      String serverId, String host, int port, String nick, String login, String realName) {
    IrcProperties.Server toServer() {
      return IrcPropertiesTestFixtures.serverBuilder(serverId)
          .host(host)
          .port(port)
          .tls(false)
          .nick(nick)
          .login(login)
          .realName(realName)
          .build();
    }
  }

  private record ContainerMemoServConfig(
      boolean enabled, String ircImage, String serverId, String appNick, String memoServNick) {
    private static final String DEFAULT_IRC_IMAGE = "linuxserver/ngircd:latest";
    private static final String DEFAULT_SERVER_ID = "memoserv-functional";
    private static final String DEFAULT_APP_NICK = "ircafems";
    private static final String DEFAULT_MEMOSERV_NICK = "MemoServ";

    static ContainerMemoServConfig fromSystem() {
      return new ContainerMemoServConfig(
          readBoolean(
              "memoserv.it.container.functional.enabled",
              "MEMOSERV_IT_CONTAINER_FUNCTIONAL_ENABLED",
              false),
          readString(
              "memoserv.it.container.functional.irc-image",
              "MEMOSERV_IT_CONTAINER_FUNCTIONAL_IRC_IMAGE",
              DEFAULT_IRC_IMAGE),
          readString(
              "memoserv.it.container.functional.server-id",
              "MEMOSERV_IT_CONTAINER_FUNCTIONAL_SERVER_ID",
              DEFAULT_SERVER_ID),
          readString(
              "memoserv.it.container.functional.app-nick",
              "MEMOSERV_IT_CONTAINER_FUNCTIONAL_APP_NICK",
              DEFAULT_APP_NICK),
          readString(
              "memoserv.it.container.functional.service-nick",
              "MEMOSERV_IT_CONTAINER_FUNCTIONAL_SERVICE_NICK",
              DEFAULT_MEMOSERV_NICK));
    }

    RuntimeIrcConfig runtimeConfig(String host, int port) {
      return new RuntimeIrcConfig(serverId, host, port, appNick, appNick, "IRCafe MemoServ Test");
    }

    List<String> memoRows() {
      return List.of("1 from alice: container memo one", "2 from bob: container memo two");
    }

    private static String readString(String propName, String envName, String fallback) {
      String prop = System.getProperty(propName);
      if (prop != null && !prop.isBlank()) return prop.trim();
      String env = System.getenv(envName);
      if (env != null && !env.isBlank()) return env.trim();
      return fallback;
    }

    private static boolean readBoolean(String propName, String envName, boolean fallback) {
      String raw = readString(propName, envName, Boolean.toString(fallback));
      return switch (raw.trim().toLowerCase(Locale.ROOT)) {
        case "1", "true", "yes", "y", "on" -> true;
        case "0", "false", "no", "n", "off" -> false;
        default -> fallback;
      };
    }
  }

  private static final class ScriptedMemoServ implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final String nick;

    private ScriptedMemoServ(Socket socket, BufferedReader in, BufferedWriter out, String nick) {
      this.socket = socket;
      this.in = in;
      this.out = out;
      this.nick = nick;
    }

    static ScriptedMemoServ connect(String host, int port, String nick) throws Exception {
      String serviceNick = Objects.toString(nick, "").trim();
      if (serviceNick.isEmpty()) {
        throw new IllegalArgumentException("MemoServ nick is blank");
      }
      Socket socket = new Socket(host, port);
      socket.setSoTimeout((int) MEMOSERV_TIMEOUT.toMillis());
      BufferedReader in =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      BufferedWriter out =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      ScriptedMemoServ service = new ScriptedMemoServ(socket, in, out, serviceNick);
      service.sendLine("NICK " + serviceNick);
      service.sendLine("USER " + serviceNick + " 0 * :" + serviceNick);
      service.awaitWelcome();
      return service;
    }

    void replyToList(String appNick, List<String> rows) {
      try {
        awaitPrivmsg(nick, "LIST");
        String target = Objects.toString(appNick, "").trim();
        for (String row : rows) {
          sendLine("PRIVMSG " + target + " :" + Objects.toString(row, "").trim());
        }
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    private void awaitWelcome() throws Exception {
      long deadlineNs = System.nanoTime() + MEMOSERV_TIMEOUT.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        if (line.contains(" 001 " + nick + " ")) {
          return;
        }
      }
      throw new IllegalStateException("timed out waiting for MemoServ IRC welcome");
    }

    private void awaitPrivmsg(String target, String expectedText) throws Exception {
      long deadlineNs = System.nanoTime() + MEMOSERV_TIMEOUT.toNanos();
      while (System.nanoTime() < deadlineNs) {
        String line = readLine();
        if (line == null) continue;
        if (line.startsWith("PING ")) {
          sendLine("PONG " + line.substring(5));
          continue;
        }
        ParsedPrivmsg privmsg = parsePrivmsg(line);
        if (privmsg == null) continue;
        if (!target.equalsIgnoreCase(privmsg.target())) continue;
        if (!privmsg.text().contains(expectedText)) continue;
        return;
      }
      throw new IllegalStateException("timed out waiting for MemoServ LIST command");
    }

    private String readLine() throws IOException {
      try {
        return in.readLine();
      } catch (java.net.SocketTimeoutException timeout) {
        return null;
      }
    }

    private void sendLine(String line) throws IOException {
      String value = Objects.toString(line, "").trim();
      if (value.isEmpty()) return;
      if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
        throw new IllegalArgumentException("line contains CR/LF");
      }
      out.write(value);
      out.write("\r\n");
      out.flush();
    }

    @Override
    public void close() {
      try {
        sendLine("QUIT :bye");
      } catch (Exception ignored) {
      }
      try {
        socket.close();
      } catch (Exception ignored) {
      }
    }
  }

  private static ParsedPrivmsg parsePrivmsg(String rawLine) {
    ParsedIrcLine parsed = parseIrcLine(rawLine);
    if (parsed == null || !"PRIVMSG".equalsIgnoreCase(parsed.command())) {
      return null;
    }
    return new ParsedPrivmsg(parsed.target(), parsed.trailing());
  }

  private static ParsedIrcLine parseIrcLine(String rawLine) {
    String line = Objects.toString(rawLine, "");
    if (line.startsWith("@")) {
      int firstSpace = line.indexOf(' ');
      if (firstSpace <= 0 || firstSpace + 1 >= line.length()) return null;
      line = line.substring(firstSpace + 1);
    }
    if (line.startsWith(":")) {
      int firstSpace = line.indexOf(' ');
      if (firstSpace <= 1 || firstSpace + 1 >= line.length()) return null;
      line = line.substring(firstSpace + 1);
    }
    line = line.trim();
    int trailingIdx = line.indexOf(" :");
    String trailing = trailingIdx >= 0 ? line.substring(trailingIdx + 2) : "";
    String preTrailing = trailingIdx >= 0 ? line.substring(0, trailingIdx) : line;
    String[] parts = preTrailing.trim().split("\\s+");
    if (parts.length < 2) return null;
    return new ParsedIrcLine(parts[0], parts[1], trailing);
  }

  private record ParsedIrcLine(String command, String target, String trailing) {}

  private record ParsedPrivmsg(String target, String text) {}
}
