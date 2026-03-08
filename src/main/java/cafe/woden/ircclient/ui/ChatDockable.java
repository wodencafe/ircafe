package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.ChannelMetadataPort;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.diagnostics.ApplicationDiagnosticsService;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.diagnostics.SpringRuntimeEventsService;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.IrcTypingPort;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.application.InboundDedupDiagnosticsPanel;
import cafe.woden.ircclient.ui.application.JfrDiagnosticsPanel;
import cafe.woden.ircclient.ui.application.RuntimeEventsPanel;
import cafe.woden.ircclient.ui.backend.BackendUiProfileProvider;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import cafe.woden.ircclient.ui.coordinator.ChatActiveTargetCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatBanListCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatChannelListCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatDockTitleCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatHistoryActionCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatInterceptorCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatMonitorCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatNickContextCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatReadMarkerCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatTargetViewRouter;
import cafe.woden.ircclient.ui.coordinator.ChatTopicCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatTranscriptInteractionCoordinator;
import cafe.woden.ircclient.ui.coordinator.ChatTypingCoordinator;
import cafe.woden.ircclient.ui.coordinator.DccActionCoordinator;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import cafe.woden.ircclient.ui.dcc.DccTransfersPanel;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.ignore.IgnoresPanel;
import cafe.woden.ircclient.ui.input.MessageInputPanel;
import cafe.woden.ircclient.ui.interceptors.InterceptorPanel;
import cafe.woden.ircclient.ui.logviewer.LogViewerPanel;
import cafe.woden.ircclient.ui.monitor.MonitorPanel;
import cafe.woden.ircclient.ui.notifications.NotificationsPanel;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javax.swing.*;
import javax.swing.text.DefaultStyledDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The main chat dockable.
 *
 * <p>Displays a single transcript at a time (selected via the server tree), but keeps per-target
 * scroll state so switching targets feels natural.
 *
 * <p>Transcripts themselves live in {@link ChatTranscriptStore} so other views (e.g., pinned chat
 * docks) can share them.
 */
@Component
@Lazy
public class ChatDockable extends ChatViewPanel implements Dockable {

  public static final String ID = "chat";
  private static final int MAX_DRAFT_TARGETS = 512;
  private static final int MAIN_DOCK_ACCENT_RAIL_PX = 4;
  private static final int MAIN_DOCK_FRAME_PX = 1;
  private static final int MAIN_DOCK_ICON_SIZE_PX = 12;
  private static final String MAIN_DOCK_TAB_TOOLTIP =
      "Main chat view (follows server-tree selection)";
  private static final String MAIN_DOCK_CLOSE_CONFIRM_TITLE = "Close Main View Dock";
  private static final String MAIN_DOCK_CLOSE_CONFIRM_MESSAGE =
      "Close the main chat view dock?\n\nUse Window -> Reset Main View Dock to restore it.";

  private final ChatTranscriptStore transcripts;
  private final ServerTreeDockable serverTree;

  private final ActiveInputRouter activeInputRouter;

  private final MessageInputPanel inputPanel;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final FlowableProcessor<UserActionRequest> userActions =
      PublishProcessor.<UserActionRequest>create().toSerialized();

  private final NickContextMenuFactory.NickContextMenu nickContextMenu;

  private final ServerProxyResolver proxyResolver;

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();

  private final Map<TargetRef, String> draftByTarget =
      new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TargetRef, String> eldest) {
          return size() > MAX_DRAFT_TARGETS;
        }
      };

  // Center content swaps between transcript and UI-only per-server views.
  private final JPanel centerCards = new JPanel(new CardLayout());
  private final NotificationsPanel notificationsPanel;
  private final ChannelListPanel channelListPanel = new ChannelListPanel();
  private final IgnoresPanel ignoresPanel;
  private final DccTransfersPanel dccTransfersPanel;
  private final MonitorPanel monitorPanel = new MonitorPanel();
  private final LogViewerPanel logViewerPanel;
  private final InterceptorStore interceptorStore;
  private final InterceptorPanel interceptorPanel;
  private final RuntimeEventsPanel appUnhandledErrorsPanel;
  private final RuntimeEventsPanel appAssertjPanel;
  private final RuntimeEventsPanel appJhiccupPanel;
  private final InboundDedupDiagnosticsPanel appInboundDedupPanel;
  private final JfrDiagnosticsPanel appJfrPanel;
  private final RuntimeEventsPanel appSpringPanel;
  private final TerminalDockable terminalPanel;
  private final ChatTopicCoordinator topicCoordinator;
  private final ChatBanListCoordinator banListCoordinator;

  private final ChatMonitorCoordinator monitorCoordinator;
  private final ChatInterceptorCoordinator interceptorCoordinator;
  private final ChatTargetViewRouter targetViewRouter;
  private final ChatTypingCoordinator typingCoordinator;
  private final ChatHistoryActionCoordinator historyActionCoordinator;
  private final ChatNickContextCoordinator nickContextCoordinator;
  private final ChatReadMarkerCoordinator readMarkerCoordinator;
  private final ChatActiveTargetCoordinator activeTargetCoordinator;
  private final ChatDockTitleCoordinator dockTitleCoordinator;
  private final ChatTranscriptInteractionCoordinator transcriptInteractionCoordinator;
  private final DccActionCoordinator dccActionCoordinator;

  private TargetRef activeTarget;

  /** Channel topic update for pinned docks and other secondary views. */
  public record TopicUpdate(TargetRef target, String topic) {}

  private record TopicCoordinatorBundle(
      ChatTopicCoordinator topicCoordinator, ChatBanListCoordinator banListCoordinator) {}

  private record CenterViewCoordinatorBundle(
      NotificationsPanel notificationsPanel,
      IgnoresPanel ignoresPanel,
      ChatMonitorCoordinator monitorCoordinator,
      DccTransfersPanel dccTransfersPanel,
      LogViewerPanel logViewerPanel,
      InterceptorPanel interceptorPanel,
      ChatInterceptorCoordinator interceptorCoordinator,
      ChatTargetViewRouter targetViewRouter) {}

  private record InputCoordinatorBundle(
      ChatTypingCoordinator typingCoordinator,
      ChatHistoryActionCoordinator historyActionCoordinator,
      ChatTranscriptInteractionCoordinator transcriptInteractionCoordinator,
      DccActionCoordinator dccActionCoordinator,
      ChatReadMarkerCoordinator readMarkerCoordinator,
      ChatActiveTargetCoordinator activeTargetCoordinator) {}

  public ChatDockable(
      ChatTranscriptStore transcripts,
      ServerTreeDockable serverTree,
      NotificationStore notificationStore,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      IrcClientService irc,
      BackendUiProfileProvider backendUiProfileProvider,
      MessageActionCapabilityPolicy messageActionCapabilityPolicy,
      ActiveInputRouter activeInputRouter,
      IgnoreListService ignoreListService,
      IgnoreStatusService ignoreStatusService,
      IgnoreListDialog ignoreListDialog,
      MonitorListService monitorListService,
      UserListStore userListStore,
      UserListDockable usersDock,
      NickContextMenuFactory nickContextMenuFactory,
      ServerProxyResolver proxyResolver,
      ChatHistoryService chatHistoryService,
      ChannelMetadataPort channelMetadata,
      ChatLogViewerService chatLogViewerService,
      InterceptorStore interceptorStore,
      DccTransferStore dccTransferStore,
      TerminalDockable terminalDockable,
      @Lazy ApplicationDiagnosticsService applicationDiagnosticsService,
      JfrRuntimeEventsService jfrRuntimeEventsService,
      SpringRuntimeEventsService springRuntimeEventsService,
      UiSettingsBus settingsBus,
      SpellcheckSettingsBus spellcheckSettingsBus,
      CommandHistoryStore commandHistoryStore,
      @Qualifier(ExecutorConfig.UI_LOG_VIEWER_EXECUTOR) ExecutorService logViewerExecutor,
      @Qualifier(ExecutorConfig.UI_INTERCEPTOR_REFRESH_EXECUTOR)
          ExecutorService interceptorRefreshExecutor) {
    super(settingsBus);

    this.transcripts = transcripts;
    this.serverTree = serverTree;

    this.activeInputRouter = activeInputRouter;
    this.proxyResolver = proxyResolver;

    this.interceptorStore = java.util.Objects.requireNonNull(interceptorStore, "interceptorStore");
    this.dockTitleCoordinator =
        new ChatDockTitleCoordinator(
            this,
            () -> activeTarget,
            this.interceptorStore,
            this::getName,
            this::setName,
            SwingUtilities::invokeLater,
            () -> Docking.updateTabInfo(this));
    this.terminalPanel = java.util.Objects.requireNonNull(terminalDockable, "terminalDockable");
    this.appUnhandledErrorsPanel = createUnhandledErrorsPanel(applicationDiagnosticsService);
    this.appAssertjPanel = createAssertjEventsPanel(applicationDiagnosticsService);
    this.appJhiccupPanel = createJhiccupEventsPanel(applicationDiagnosticsService);
    this.appInboundDedupPanel = createInboundDedupPanel(springRuntimeEventsService);
    this.appJfrPanel = new JfrDiagnosticsPanel(jfrRuntimeEventsService);
    this.appSpringPanel = createSpringEventsPanel(springRuntimeEventsService);

    this.nickContextMenu = createNickContextMenu(nickContextMenuFactory);
    this.nickContextCoordinator =
        createNickContextCoordinator(ignoreListService, ignoreStatusService, userListStore);

    // Show something harmless on startup; first selection will swap it.
    setDocument(new DefaultStyledDocument());

    TopicCoordinatorBundle topicBundle =
        createTopicCoordinatorBundle(notificationStore, channelMetadata);
    this.topicCoordinator = topicBundle.topicCoordinator();
    this.banListCoordinator = topicBundle.banListCoordinator();
    bindTopicNotificationIndicatorUpdates(notificationStore);

    CenterViewCoordinatorBundle centerViewBundle =
        createCenterViewCoordinatorBundle(
            notificationStore,
            serverTree,
            outboundBus,
            irc,
            backendUiProfileProvider,
            userListStore,
            usersDock,
            ignoreListDialog,
            monitorListService,
            dccTransferStore,
            chatLogViewerService,
            activationBus,
            logViewerExecutor,
            interceptorRefreshExecutor);
    this.notificationsPanel = centerViewBundle.notificationsPanel();
    this.ignoresPanel = centerViewBundle.ignoresPanel();

    this.monitorCoordinator = centerViewBundle.monitorCoordinator();
    this.dccTransfersPanel = centerViewBundle.dccTransfersPanel();
    this.logViewerPanel = centerViewBundle.logViewerPanel();
    this.interceptorPanel = centerViewBundle.interceptorPanel();
    this.interceptorCoordinator = centerViewBundle.interceptorCoordinator();
    this.targetViewRouter = centerViewBundle.targetViewRouter();
    registerCenterCards();

    // Input panel is embedded in the main chat dock so input is always coupled with the transcript.
    this.inputPanel =
        new MessageInputPanel(settingsBus, commandHistoryStore, spellcheckSettingsBus);
    this.inputPanel.setBackendUiProfile(backendUiProfileProvider.profileForServer(""));
    add(inputPanel, BorderLayout.SOUTH);
    MessageActionCapabilityPolicy capabilityPolicy =
        Objects.requireNonNull(messageActionCapabilityPolicy, "messageActionCapabilityPolicy");
    configureTranscriptContextMenuActions(transcripts, chatHistoryService);
    configureInputActivation(activationBus);
    InputCoordinatorBundle inputBundle =
        createInputCoordinatorBundle(
            transcripts,
            irc,
            chatHistoryService,
            activationBus,
            outboundBus,
            capabilityPolicy,
            backendUiProfileProvider);
    this.typingCoordinator = inputBundle.typingCoordinator();
    this.historyActionCoordinator = inputBundle.historyActionCoordinator();
    this.transcriptInteractionCoordinator = inputBundle.transcriptInteractionCoordinator();
    this.dccActionCoordinator = inputBundle.dccActionCoordinator();
    this.readMarkerCoordinator = inputBundle.readMarkerCoordinator();
    this.activeTargetCoordinator = inputBundle.activeTargetCoordinator();
    configureReactionChipActions(transcripts, capabilityPolicy, activationBus, outboundBus);
    inputPanel.setOnTypingStateChanged(typingCoordinator::onLocalTypingStateChanged);
    bindInputOutboundMessages(outboundBus);

    // Keep an initial view state so the first auto-scroll behaves.
    this.activeTarget = new TargetRef("default", "status");
    updateDockTitle();
    applyMainDockVisualIdentity();

    this.monitorCoordinator.bind(disposables);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    SwingUtilities.invokeLater(this::applyMainDockVisualIdentity);
  }

  private RuntimeEventsPanel createAssertjEventsPanel(
      ApplicationDiagnosticsService applicationDiagnosticsService) {
    return new RuntimeEventsPanel(
        "AssertJ Swing",
        "EDT watchdog, violation checks, and UI freeze diagnostics.",
        () ->
            applicationDiagnosticsService != null
                ? applicationDiagnosticsService.recentAssertjSwingEvents(1200)
                : List.of(),
        () -> {
          if (applicationDiagnosticsService != null) {
            applicationDiagnosticsService.clearAssertjSwingEvents();
          }
        },
        "assertj-swing",
        applicationDiagnosticsService != null
            ? applicationDiagnosticsService.assertjSwingChangeStream()
            : null);
  }

  private RuntimeEventsPanel createUnhandledErrorsPanel(
      ApplicationDiagnosticsService applicationDiagnosticsService) {
    return new RuntimeEventsPanel(
        "Unhandled Errors",
        "Uncaught exceptions observed by the global thread exception handler.",
        () ->
            applicationDiagnosticsService != null
                ? applicationDiagnosticsService.recentUnhandledErrorEvents(1200)
                : List.of(),
        () -> {
          if (applicationDiagnosticsService != null) {
            applicationDiagnosticsService.clearUnhandledErrorEvents();
          }
        },
        "uncaught",
        applicationDiagnosticsService != null
            ? applicationDiagnosticsService.unhandledErrorChangeStream()
            : null);
  }

  private RuntimeEventsPanel createJhiccupEventsPanel(
      ApplicationDiagnosticsService applicationDiagnosticsService) {
    return new RuntimeEventsPanel(
        "jHiccup",
        "External jHiccup process lifecycle and latency diagnostics.",
        () ->
            applicationDiagnosticsService != null
                ? applicationDiagnosticsService.recentJhiccupEvents(1200)
                : List.of(),
        () -> {
          if (applicationDiagnosticsService != null) {
            applicationDiagnosticsService.clearJhiccupEvents();
          }
        },
        "jhiccup",
        applicationDiagnosticsService != null
            ? applicationDiagnosticsService.jhiccupChangeStream()
            : null);
  }

  private RuntimeEventsPanel createSpringEventsPanel(
      SpringRuntimeEventsService springRuntimeEventsService) {
    return new RuntimeEventsPanel(
        "Spring Events",
        "Application lifecycle, availability, and framework events.",
        () ->
            springRuntimeEventsService != null
                ? springRuntimeEventsService.recentEvents(600)
                : List.of(),
        () -> {
          if (springRuntimeEventsService != null) {
            springRuntimeEventsService.clearEvents();
          }
        },
        "spring-events",
        springRuntimeEventsService != null ? springRuntimeEventsService.changeStream() : null);
  }

  private InboundDedupDiagnosticsPanel createInboundDedupPanel(
      SpringRuntimeEventsService springRuntimeEventsService) {
    return new InboundDedupDiagnosticsPanel(
        () ->
            springRuntimeEventsService != null
                ? filterInboundDedupEvents(springRuntimeEventsService.recentEvents(1200))
                : List.of(),
        springRuntimeEventsService != null ? springRuntimeEventsService.changeStream() : null);
  }

  private static List<RuntimeDiagnosticEvent> filterInboundDedupEvents(
      List<RuntimeDiagnosticEvent> events) {
    if (events == null || events.isEmpty()) return List.of();
    ArrayList<RuntimeDiagnosticEvent> out = new ArrayList<>(events.size());
    for (RuntimeDiagnosticEvent event : events) {
      if (event == null) continue;
      String details = Objects.toString(event.details(), "");
      String summary = Objects.toString(event.summary(), "");
      if (details.contains(
              "payloadType=cafe.woden.ircclient.app.core.IrcMediator$InboundMessageDedupDiagnostics")
          || summary.contains("InboundMessageDedupDiagnostics[")) {
        out.add(event);
      }
    }
    return List.copyOf(out);
  }

  private TopicCoordinatorBundle createTopicCoordinatorBundle(
      NotificationStore notificationStore, ChannelMetadataPort channelMetadata) {
    ChannelMetadataPort metadata =
        java.util.Objects.requireNonNull(channelMetadata, "channelMetadata");
    // Insert an optional topic panel above the transcript.
    remove(scroll);
    ChatTopicCoordinator topicCoordinator =
        new ChatTopicCoordinator(
            scroll,
            channelListPanel,
            java.util.Objects.requireNonNull(notificationStore, "notificationStore"),
            ref -> {
              if (ref == null) return;
              if (serverTree != null) {
                serverTree.selectTarget(ref);
              }
            },
            () -> {
              revalidate();
              repaint();
            },
            metadata::topicFor,
            (target, topic) -> metadata.rememberTopic(target, topic, null, null),
            metadata::topicPanelHeightPxFor,
            metadata::rememberTopicPanelHeight);
    ChatBanListCoordinator banListCoordinator = new ChatBanListCoordinator(channelListPanel);
    return new TopicCoordinatorBundle(topicCoordinator, banListCoordinator);
  }

  private void bindTopicNotificationIndicatorUpdates(NotificationStore notificationStore) {
    if (notificationStore == null) return;
    disposables.add(
        notificationStore
            .changes()
            .subscribe(
                change -> {
                  TargetRef target = activeTarget;
                  if (target == null || !target.isChannel()) return;
                  if (change == null || !Objects.equals(target.serverId(), change.serverId()))
                    return;
                  SwingUtilities.invokeLater(
                      () -> topicCoordinator.updateTopicPanelForActiveTarget(activeTarget));
                },
                err -> {
                  // Never break chat view updates due to best-effort indicator refreshes.
                }));
  }

  private CenterViewCoordinatorBundle createCenterViewCoordinatorBundle(
      NotificationStore notificationStore,
      ServerTreeDockable serverTree,
      OutboundLineBus outboundBus,
      IrcClientService irc,
      BackendUiProfileProvider backendUiProfileProvider,
      UserListStore userListStore,
      UserListDockable usersDock,
      IgnoreListDialog ignoreListDialog,
      MonitorListService monitorListService,
      DccTransferStore dccTransferStore,
      ChatLogViewerService chatLogViewerService,
      TargetActivationBus activationBus,
      ExecutorService logViewerExecutor,
      ExecutorService interceptorRefreshExecutor) {
    NotificationsPanel notificationsPanel = createNotificationsPanel(notificationStore);
    ChatChannelListCoordinator channelListCoordinator =
        createChannelListCoordinator(
            serverTree, outboundBus, backendUiProfileProvider, userListStore, usersDock, irc);
    channelListCoordinator.bind(disposables);
    ChatMonitorCoordinator monitorCoordinator =
        new ChatMonitorCoordinator(monitorPanel, monitorListService, () -> activeTarget);
    DccTransfersPanel dccTransfersPanel =
        createDccTransfersPanel(dccTransferStore, activationBus, outboundBus);
    configureMonitorPanelCommandEmission(activationBus, outboundBus);
    LogViewerPanel logViewerPanel = createLogViewerPanel(chatLogViewerService, logViewerExecutor);
    IgnoresPanel ignoresPanel = createIgnoresPanel(ignoreListDialog);
    InterceptorPanel interceptorPanel =
        new InterceptorPanel(this.interceptorStore, interceptorRefreshExecutor);
    ChatInterceptorCoordinator interceptorCoordinator =
        createInterceptorCoordinator(interceptorPanel);
    interceptorCoordinator.bind(disposables);
    ChatTargetViewRouter targetViewRouter =
        createTargetViewRouter(
            notificationsPanel,
            ignoresPanel,
            channelListCoordinator,
            monitorCoordinator,
            dccTransfersPanel,
            logViewerPanel,
            interceptorPanel,
            backendUiProfileProvider);
    return new CenterViewCoordinatorBundle(
        notificationsPanel,
        ignoresPanel,
        monitorCoordinator,
        dccTransfersPanel,
        logViewerPanel,
        interceptorPanel,
        interceptorCoordinator,
        targetViewRouter);
  }

  private ChatChannelListCoordinator createChannelListCoordinator(
      ServerTreeDockable serverTree,
      OutboundLineBus outboundBus,
      BackendUiProfileProvider backendUiProfileProvider,
      UserListStore userListStore,
      UserListDockable usersDock,
      IrcClientService irc) {
    channelListPanel.setBackendUiProfile(backendUiProfileProvider.profileForServer(""));
    return new ChatChannelListCoordinator(
        channelListPanel,
        serverTree,
        outboundBus,
        userListStore,
        usersDock,
        () -> activeTarget,
        sid -> irc.currentNick(sid).orElse(""),
        (serverId, channel) -> topicFor(new TargetRef(serverId, channel)),
        banListCoordinator::snapshot);
  }

  private ChatInterceptorCoordinator createInterceptorCoordinator(
      InterceptorPanel interceptorPanel) {
    return new ChatInterceptorCoordinator(
        this.interceptorStore,
        interceptorPanel,
        () -> activeTarget,
        this::updateDockTitle,
        ref -> {
          if (ChatDockable.this.serverTree != null) {
            ChatDockable.this.serverTree.selectTarget(ref);
          } else {
            ChatDockable.this.setActiveTarget(ref);
          }
        });
  }

  private ChatTargetViewRouter createTargetViewRouter(
      NotificationsPanel notificationsPanel,
      IgnoresPanel ignoresPanel,
      ChatChannelListCoordinator channelListCoordinator,
      ChatMonitorCoordinator monitorCoordinator,
      DccTransfersPanel dccTransfersPanel,
      LogViewerPanel logViewerPanel,
      InterceptorPanel interceptorPanel,
      BackendUiProfileProvider backendUiProfileProvider) {
    return new ChatTargetViewRouter(
        centerCards,
        notificationsPanel,
        channelListPanel,
        ignoresPanel,
        dccTransfersPanel,
        monitorPanel,
        logViewerPanel,
        interceptorPanel,
        appUnhandledErrorsPanel,
        appAssertjPanel,
        appJhiccupPanel,
        appInboundDedupPanel,
        appJfrPanel,
        appSpringPanel,
        channelListCoordinator::refreshManagedChannelsCard,
        monitorCoordinator::refreshMonitorRows,
        backendUiProfileProvider::profileForServer);
  }

  private InputCoordinatorBundle createInputCoordinatorBundle(
      ChatTranscriptStore transcripts,
      IrcClientService irc,
      ChatHistoryService chatHistoryService,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      MessageActionCapabilityPolicy messageActionCapabilityPolicy,
      BackendUiProfileProvider backendUiProfileProvider) {
    ChatTypingCoordinator typingCoordinator =
        createTypingCoordinator(irc, messageActionCapabilityPolicy);
    ChatHistoryActionCoordinator historyActionCoordinator =
        createHistoryActionCoordinator(
            messageActionCapabilityPolicy, chatHistoryService, activationBus, outboundBus);
    ChatTranscriptInteractionCoordinator transcriptInteractionCoordinator =
        createTranscriptInteractionCoordinator(
            activationBus, outboundBus, transcripts, historyActionCoordinator);
    DccActionCoordinator dccActionCoordinator =
        createDccActionCoordinator(activationBus, outboundBus);
    ChatReadMarkerCoordinator readMarkerCoordinator = createReadMarkerCoordinator(transcripts, irc);
    ChatActiveTargetCoordinator activeTargetCoordinator =
        createActiveTargetCoordinator(
            transcripts, typingCoordinator, readMarkerCoordinator, backendUiProfileProvider);
    return new InputCoordinatorBundle(
        typingCoordinator,
        historyActionCoordinator,
        transcriptInteractionCoordinator,
        dccActionCoordinator,
        readMarkerCoordinator,
        activeTargetCoordinator);
  }

  private ChatTypingCoordinator createTypingCoordinator(
      IrcClientService irc, MessageActionCapabilityPolicy messageActionCapabilityPolicy) {
    return new ChatTypingCoordinator(
        inputPanel,
        IrcTypingPort.from(irc),
        messageActionCapabilityPolicy,
        () -> activeTarget,
        this::isTranscriptAtBottom,
        this::armTailPinOnNextAppendIfAtBottom,
        this::isFollowTail,
        this::scrollToBottom,
        draftByTarget);
  }

  private ChatHistoryActionCoordinator createHistoryActionCoordinator(
      MessageActionCapabilityPolicy messageActionCapabilityPolicy,
      ChatHistoryService chatHistoryService,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus) {
    return new ChatHistoryActionCoordinator(
        messageActionCapabilityPolicy,
        chatHistoryService,
        () -> activeTarget,
        activationBus::activate,
        this::activateInputPanel,
        inputPanel::focusInput,
        this::armTailPinOnNextAppendIfAtBottom,
        outboundBus::emit,
        (target, msgId, preview, jumpAction) ->
            inputPanel.beginReplyCompose(target, msgId, preview, jumpAction),
        inputPanel::openQuickReactionPicker,
        inputPanel::setDraftText,
        ChatDockable::buildChatHistoryLatestCommand,
        ChatDockable::buildChatHistoryAroundByMsgIdCommand,
        transcripts::messagePreviewById,
        transcripts::messageOffsetById,
        () -> setFollowTail(false),
        this::scrollToTranscriptOffset);
  }

  private void configureReactionChipActions(
      ChatTranscriptStore transcripts,
      MessageActionCapabilityPolicy messageActionCapabilityPolicy,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus) {
    if (transcripts == null) return;
    transcripts.setReactionChipActionHandler(
        (target, messageId, reactionToken, unreactRequested) -> {
          if (target == null || target.isUiOnly()) return;
          String msgId = Objects.toString(messageId, "").trim();
          String token = Objects.toString(reactionToken, "").trim();
          if (msgId.isEmpty() || token.isEmpty()) return;

          boolean supported =
              unreactRequested
                  ? messageActionCapabilityPolicy.canUnreact(target.serverId())
                  : messageActionCapabilityPolicy.canReact(target.serverId());
          if (!supported) return;

          activationBus.activate(target);
          armTailPinOnNextAppendIfAtBottom();
          outboundBus.emit((unreactRequested ? "/unreact " : "/react ") + msgId + " " + token);
        });
  }

  private ChatTranscriptInteractionCoordinator createTranscriptInteractionCoordinator(
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      ChatTranscriptStore transcripts,
      ChatHistoryActionCoordinator historyActionCoordinator) {
    return new ChatTranscriptInteractionCoordinator(
        () -> activeTarget,
        activationBus::activate,
        this::activateInputPanel,
        inputPanel::focusInput,
        openPrivate::onNext,
        outboundBus::emit,
        transcripts::messageOffsetById,
        historyActionCoordinator::requestHistoryAroundMessage,
        () -> setFollowTail(false),
        this::scrollToTranscriptOffset);
  }

  private DccActionCoordinator createDccActionCoordinator(
      TargetActivationBus activationBus, OutboundLineBus outboundBus) {
    return new DccActionCoordinator(
        this,
        (ctx, cmd) -> {
          activationBus.activate(ctx);
          activateInputPanel();
          armTailPinOnNextAppendIfAtBottom();
          outboundBus.emit(cmd);
        });
  }

  private ChatReadMarkerCoordinator createReadMarkerCoordinator(
      ChatTranscriptStore transcripts, IrcClientService irc) {
    return new ChatReadMarkerCoordinator(
        transcripts,
        IrcReadMarkerPort.from(irc),
        () -> activeTarget,
        this::scrollToTranscriptOffset,
        this::updateScrollStateFromBar,
        this::isTranscriptAtBottom);
  }

  private ChatActiveTargetCoordinator createActiveTargetCoordinator(
      ChatTranscriptStore transcripts,
      ChatTypingCoordinator typingCoordinator,
      ChatReadMarkerCoordinator readMarkerCoordinator,
      BackendUiProfileProvider backendUiProfileProvider) {
    return new ChatActiveTargetCoordinator(
        () -> activeTarget,
        target -> activeTarget = target,
        inputPanel,
        backendUiProfileProvider::profileForServer,
        draftByTarget,
        this::updateScrollStateFromBar,
        this::updateDockTitle,
        typingCoordinator::refreshTypingSignalAvailabilityForActiveTarget,
        interceptorCoordinator::onActiveTargetChanged,
        targetViewRouter::route,
        transcripts,
        this::setDocument,
        () -> topicCoordinator.updateTopicPanelForActiveTarget(activeTarget),
        readMarkerCoordinator::applyReadMarkerViewState,
        SwingUtilities::invokeLater,
        this::revalidate,
        this::repaint);
  }

  private void activateInputPanel() {
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
  }

  private NotificationsPanel createNotificationsPanel(NotificationStore notificationStore) {
    // Notifications panel is a UI-only target view (selected from the server tree).
    NotificationsPanel panel =
        new NotificationsPanel(
            java.util.Objects.requireNonNull(notificationStore, "notificationStore"));
    panel.setOnSelectTarget(
        ref -> {
          // Clickable channel names in the notifications list should navigate like a tree
          // selection.
          if (ChatDockable.this.serverTree != null) {
            ChatDockable.this.serverTree.selectTarget(ref);
          }
        });
    return panel;
  }

  private NickContextMenuFactory.NickContextMenu createNickContextMenu(
      NickContextMenuFactory nickContextMenuFactory) {
    return nickContextMenuFactory.create(buildNickContextCallbacks());
  }

  private NickContextMenuFactory.Callbacks buildNickContextCallbacks() {
    return new NickContextMenuFactory.Callbacks() {
      @Override
      public void openQuery(TargetRef ctx, String nick) {
        if (ctx == null) return;
        if (nick == null || nick.isBlank()) return;
        openPrivate.onNext(new PrivateMessageRequest(ctx.serverId(), nick));
      }

      @Override
      public void emitUserAction(TargetRef ctx, String nick, UserActionRequest.Action action) {
        if (ctx == null) return;
        if (nick == null || nick.isBlank()) return;
        if (action == null) return;
        userActions.onNext(new UserActionRequest(ctx, nick, action));
      }

      @Override
      public void promptIgnore(TargetRef ctx, String nick, boolean removing, boolean soft) {
        ChatDockable.this.promptIgnore(ctx, nick, removing, soft);
      }

      @Override
      public void requestDccAction(
          TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
        ChatDockable.this.requestDccAction(ctx, nick, action);
      }
    };
  }

  private ChatNickContextCoordinator createNickContextCoordinator(
      IgnoreListService ignoreListService,
      IgnoreStatusService ignoreStatusService,
      UserListStore userListStore) {
    return new ChatNickContextCoordinator(
        ignoreListService,
        ignoreStatusService,
        userListStore,
        this.nickContextMenu,
        () -> activeTarget,
        this);
  }

  private DccTransfersPanel createDccTransfersPanel(
      DccTransferStore dccTransferStore,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus) {
    DccTransfersPanel panel =
        new DccTransfersPanel(
            java.util.Objects.requireNonNull(dccTransferStore, "dccTransferStore"));
    panel.setOnEmitCommand(
        line -> {
          String cmd = Objects.toString(line, "").trim();
          TargetRef t = activeTarget;
          if (cmd.isEmpty() || t == null || !t.isDccTransfers()) return;
          activationBus.activate(t);
          armTailPinOnNextAppendIfAtBottom();
          outboundBus.emit(cmd);
        });
    return panel;
  }

  private void configureMonitorPanelCommandEmission(
      TargetActivationBus activationBus, OutboundLineBus outboundBus) {
    monitorPanel.setOnEmitCommand(
        line -> {
          String cmd = Objects.toString(line, "").trim();
          TargetRef t = activeTarget;
          if (cmd.isEmpty() || t == null || !t.isMonitorGroup()) return;
          activationBus.activate(t);
          // Ensure activation has a chance to propagate before command handling.
          SwingUtilities.invokeLater(() -> outboundBus.emit(cmd));
        });
  }

  private LogViewerPanel createLogViewerPanel(
      ChatLogViewerService chatLogViewerService, ExecutorService logViewerExecutor) {
    return new LogViewerPanel(
        java.util.Objects.requireNonNull(chatLogViewerService, "chatLogViewerService"),
        sid ->
            (this.serverTree == null)
                ? java.util.List.of()
                : this.serverTree.openChannelsForServer(sid),
        logViewerExecutor);
  }

  private IgnoresPanel createIgnoresPanel(IgnoreListDialog ignoreListDialog) {
    IgnoresPanel panel = new IgnoresPanel();
    panel.setOnOpenIgnoreDialog(
        targetRef -> {
          if (ignoreListDialog == null) return;
          String sid = ignoreScopeServerId(targetRef);
          if (sid.isEmpty()) return;
          Window owner = SwingUtilities.getWindowAncestor(this);
          ignoreListDialog.open(owner, sid);
        });
    return panel;
  }

  private static String ignoreScopeServerId(TargetRef targetRef) {
    if (targetRef == null) return "";
    String sid = Objects.toString(targetRef.serverId(), "").trim();
    if (sid.isEmpty()) return "";
    String token = Objects.toString(targetRef.networkQualifierToken(), "").trim();
    return token.isEmpty() ? sid : TargetRef.withNetworkQualifier(sid, token);
  }

  private void registerCenterCards() {
    centerCards.add(topicCoordinator.topicSplit(), ChatTargetViewRouter.CARD_TRANSCRIPT);
    centerCards.add(notificationsPanel, ChatTargetViewRouter.CARD_NOTIFICATIONS);
    centerCards.add(channelListPanel, ChatTargetViewRouter.CARD_CHANNEL_LIST);
    centerCards.add(ignoresPanel, ChatTargetViewRouter.CARD_IGNORES);
    centerCards.add(dccTransfersPanel, ChatTargetViewRouter.CARD_DCC_TRANSFERS);
    centerCards.add(monitorPanel, ChatTargetViewRouter.CARD_MONITOR);
    centerCards.add(logViewerPanel, ChatTargetViewRouter.CARD_LOG_VIEWER);
    centerCards.add(interceptorPanel, ChatTargetViewRouter.CARD_INTERCEPTOR);
    centerCards.add(appUnhandledErrorsPanel, ChatTargetViewRouter.CARD_APP_UNHANDLED_ERRORS);
    centerCards.add(appAssertjPanel, ChatTargetViewRouter.CARD_APP_ASSERTJ);
    centerCards.add(appJhiccupPanel, ChatTargetViewRouter.CARD_APP_JHICCUP);
    centerCards.add(appInboundDedupPanel, ChatTargetViewRouter.CARD_APP_INBOUND_DEDUP);
    centerCards.add(appJfrPanel, ChatTargetViewRouter.CARD_APP_JFR);
    centerCards.add(appSpringPanel, ChatTargetViewRouter.CARD_APP_SPRING);
    centerCards.add(terminalPanel, ChatTargetViewRouter.CARD_TERMINAL);
    add(centerCards, BorderLayout.CENTER);
    targetViewRouter.showTranscriptCard();
  }

  private void configureTranscriptContextMenuActions(
      ChatTranscriptStore transcripts, ChatHistoryService chatHistoryService) {
    // Context menu: Clear (buffer only) + Reload recent history (clear + reload from DB/bouncer).
    setTranscriptContextMenuActions(
        () -> {
          try {
            TargetRef t = activeTarget;
            if (t == null || t.isUiOnly()) return;
            transcripts.clearTarget(t);
          } catch (Exception ignored) {
          }
        },
        () -> {
          try {
            TargetRef t = activeTarget;
            if (t == null || t.isUiOnly()) return;
            if (chatHistoryService != null && chatHistoryService.canReloadRecent(t)) {
              chatHistoryService.reloadRecent(t);
            }
          } catch (Exception ignored) {
          }
        });
  }

  private void configureInputActivation(TargetActivationBus activationBus) {
    if (this.activeInputRouter == null) return;

    // Default active typing surface is the main chat input.
    this.activeInputRouter.activate(inputPanel);
    inputPanel.setOnActivated(
        () -> {
          this.activeInputRouter.activate(inputPanel);
          if (activeTarget != null) {
            activationBus.activate(activeTarget);
          }
        });
  }

  private void bindInputOutboundMessages(OutboundLineBus outboundBus) {
    disposables.add(
        inputPanel
            .outboundMessages()
            .subscribe(
                line -> {
                  armTailPinOnNextAppendIfAtBottom();
                  outboundBus.emit(line);
                },
                err -> {
                  // Never crash the UI because an outbound subscriber failed.
                }));
  }

  @Override
  protected ProxyPlan currentProxyPlan() {
    try {
      if (proxyResolver == null) return null;
      if (activeTarget == null) return null;
      return proxyResolver.planForServer(activeTarget.serverId());
    } catch (Exception ignored) {
      return null;
    }
  }

  public void setActiveTarget(TargetRef target) {
    activeTargetCoordinator.setActiveTarget(target);
  }

  /**
   * Enable/disable the embedded input bar.
   *
   * <p>We intentionally preserve any draft text when disabling so the user doesn't lose what they
   * were typing during connect/disconnect transitions.
   */
  public void setInputEnabled(boolean enabled) {
    inputPanel.setInputEnabled(enabled);
    if (!enabled) {
      inputPanel.setTypingSignalAvailable(false);
    } else {
      typingCoordinator.refreshTypingSignalAvailabilityForActiveTarget();
    }
  }

  /** Update the nick completion list used by the embedded input bar. */
  public void setNickCompletions(java.util.List<String> nicks) {
    inputPanel.setNickCompletions(nicks);
  }

  public void setTopic(TargetRef target, String topic) {
    topicCoordinator.setTopic(target, topic, activeTarget);
  }

  public void clearTopic(TargetRef target) {
    topicCoordinator.clearTopic(target, activeTarget);
  }

  public String topicFor(TargetRef target) {
    return topicCoordinator.topicFor(target);
  }

  public int topicPanelHeightPx() {
    return topicCoordinator.topicPanelHeightPx();
  }

  public int topicPanelHeightPxFor(TargetRef target) {
    return topicCoordinator.topicPanelHeightPxFor(target);
  }

  public void setTopicPanelHeightPx(int heightPx) {
    topicCoordinator.setTopicPanelHeightPx(heightPx);
  }

  public void setTopicPanelHeightPxFor(TargetRef target, int heightPx) {
    topicCoordinator.setTopicPanelHeightPxFor(target, heightPx);
  }

  public Flowable<TopicUpdate> topicUpdates() {
    return topicCoordinator.topicUpdates();
  }

  public void beginChannelList(String serverId, String banner) {
    channelListPanel.beginList(serverId, banner);
  }

  public void appendChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {
    channelListPanel.appendEntry(serverId, channel, visibleUsers, topic);
  }

  public void appendChannelListEntries(
      String serverId, List<ChannelListPanel.ListEntryRow> entries) {
    channelListPanel.appendEntries(serverId, entries);
  }

  public void endChannelList(String serverId, String summary) {
    channelListPanel.endList(serverId, summary);
  }

  public void beginChannelBanList(String serverId, String channel) {
    banListCoordinator.beginBanList(serverId, channel);
  }

  public void appendChannelBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {
    banListCoordinator.appendBanListEntry(serverId, channel, mask, setBy, setAtEpochSeconds);
  }

  public void endChannelBanList(String serverId, String channel, String summary) {
    banListCoordinator.endBanList(serverId, channel, summary);
  }

  public void setChannelModeSnapshot(
      String serverId, String channel, String rawModes, String friendlySummary) {
    channelListPanel.setChannelModeSnapshot(serverId, channel, rawModes, friendlySummary);
  }

  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    monitorCoordinator.setPrivateMessageOnlineState(serverId, nick, online);
  }

  public void clearPrivateMessageOnlineStates(String serverId) {
    monitorCoordinator.clearPrivateMessageOnlineStates(serverId);
    readMarkerCoordinator.clearServer(serverId);
  }

  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return openPrivate.onBackpressureBuffer();
  }

  public Flowable<UserActionRequest> userActionRequests() {
    return userActions.onBackpressureBuffer();
  }

  public void onTargetClosed(TargetRef target) {
    if (target == null) return;
    draftByTarget.remove(target);
    readMarkerCoordinator.onTargetClosed(target);
  }

  private void promptIgnore(TargetRef ctx, String nick, boolean removing, boolean soft) {
    nickContextCoordinator.promptIgnore(ctx, nick, removing, soft);
  }

  @Override
  protected JPopupMenu nickContextMenuFor(String nick) {
    return nickContextCoordinator.nickContextMenuFor(nick);
  }

  @Override
  protected void onTranscriptClicked() {
    transcriptInteractionCoordinator.onTranscriptClicked();
  }

  public void showTypingIndicator(TargetRef target, String nick, String state) {
    typingCoordinator.showTypingIndicator(target, nick, state);
  }

  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    typingCoordinator.normalizeIrcv3CapabilityUiState(serverId, capability);
    readMarkerCoordinator.normalizeIrcv3CapabilityUiState(serverId, capability);
  }

  @Override
  protected boolean replyContextActionVisible() {
    return historyActionCoordinator.replyContextActionVisible();
  }

  @Override
  protected boolean reactContextActionVisible() {
    return historyActionCoordinator.reactContextActionVisible();
  }

  @Override
  protected boolean unreactContextActionVisible() {
    return historyActionCoordinator.unreactContextActionVisible();
  }

  @Override
  protected boolean editContextActionVisible() {
    return historyActionCoordinator.editContextActionVisible();
  }

  @Override
  protected boolean redactContextActionVisible() {
    return historyActionCoordinator.redactContextActionVisible();
  }

  @Override
  protected boolean loadNewerHistoryContextActionVisible() {
    return historyActionCoordinator.loadNewerHistoryContextActionVisible();
  }

  @Override
  protected boolean loadAroundMessageContextActionVisible() {
    return historyActionCoordinator.loadAroundMessageContextActionVisible();
  }

  @Override
  protected void onLoadNewerHistoryRequested() {
    historyActionCoordinator.onLoadNewerHistoryRequested();
  }

  @Override
  protected void onLoadContextAroundMessageRequested(String messageId) {
    historyActionCoordinator.onLoadContextAroundMessageRequested(messageId);
  }

  @Override
  protected void onReplyToMessageRequested(String messageId) {
    historyActionCoordinator.onReplyToMessageRequested(messageId);
  }

  @Override
  protected void onReactToMessageRequested(String messageId) {
    historyActionCoordinator.onReactToMessageRequested(messageId);
  }

  @Override
  protected void onUnreactToMessageRequested(String messageId) {
    historyActionCoordinator.onUnreactToMessageRequested(messageId);
  }

  @Override
  protected void onEditMessageRequested(String messageId) {
    historyActionCoordinator.onEditMessageRequested(messageId);
  }

  @Override
  protected void onRedactMessageRequested(String messageId) {
    historyActionCoordinator.onRedactMessageRequested(messageId);
  }

  @Override
  protected boolean isOwnMessageForContextActions(String messageId) {
    TargetRef target = activeTarget;
    if (target == null || target.isUiOnly()) return false;
    return transcripts != null && transcripts.isOwnMessage(target, messageId);
  }

  private void requestDccAction(
      TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
    dccActionCoordinator.requestAction(ctx, nick, action);
  }

  @Override
  protected boolean onNickClicked(String nick) {
    return transcriptInteractionCoordinator.onNickClicked(nick);
  }

  @Override
  protected boolean onChannelClicked(String channel) {
    return transcriptInteractionCoordinator.onChannelClicked(channel);
  }

  @Override
  protected boolean onMessageReferenceClicked(String messageId) {
    return transcriptInteractionCoordinator.onMessageReferenceClicked(messageId);
  }

  @Override
  protected boolean onManualPreviewRequested(String url, int insertOffset) {
    TargetRef target = activeTarget;
    if (target == null || target.isUiOnly()) return false;
    return transcripts != null && transcripts.insertManualPreviewAt(target, insertOffset, url);
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return dockTitleCoordinator.tabText();
  }

  @Override
  public Icon getIcon() {
    return SvgIcons.action("star", MAIN_DOCK_ICON_SIZE_PX);
  }

  @Override
  public String getTabTooltip() {
    return MAIN_DOCK_TAB_TOOLTIP;
  }

  @Override
  public boolean requestClose() {
    Window owner = SwingUtilities.getWindowAncestor(this);
    int choice =
        JOptionPane.showConfirmDialog(
            owner != null ? owner : this,
            MAIN_DOCK_CLOSE_CONFIRM_MESSAGE,
            MAIN_DOCK_CLOSE_CONFIRM_TITLE,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return choice == JOptionPane.YES_OPTION;
  }

  private void updateDockTitle() {
    dockTitleCoordinator.updateDockTitle();
  }

  private void applyMainDockVisualIdentity() {
    Color accent = resolveMainDockAccentColor();

    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, MAIN_DOCK_ACCENT_RAIL_PX, 0, 0, accent),
            BorderFactory.createLineBorder(resolveMainDockFrameColor(accent), MAIN_DOCK_FRAME_PX)));
  }

  private static Color resolveMainDockAccentColor() {
    Color accent = UIManager.getColor("@accentColor");
    if (accent == null) accent = UIManager.getColor("Component.focusColor");
    if (accent == null) accent = UIManager.getColor("Tree.selectionBackground");
    if (accent == null) accent = UIManager.getColor("Label.foreground");
    if (accent == null) accent = new Color(0x2D, 0x6B, 0xFF);
    return withAlpha(accent, 235);
  }

  private static Color resolveMainDockFrameColor(Color accent) {
    if (accent == null) return new Color(0, 0, 0, 80);
    return withAlpha(accent, 170);
  }

  private static Color withAlpha(Color color, int alpha) {
    if (color == null) return null;
    int clamped = Math.max(0, Math.min(255, alpha));
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
  }

  @Override
  protected boolean isFollowTail() {
    return readMarkerCoordinator.isFollowTail();
  }

  @Override
  protected void setFollowTail(boolean followTail) {
    readMarkerCoordinator.setFollowTail(followTail);
  }

  @Override
  protected int getSavedScrollValue() {
    return readMarkerCoordinator.savedScrollValue();
  }

  @Override
  protected void setSavedScrollValue(int value) {
    readMarkerCoordinator.setSavedScrollValue(value);
  }

  @PreDestroy
  void shutdown() {
    try {
      inputPanel.flushTypingDone();
    } catch (Exception ignored) {
    }
    try {
      inputPanel.shutdownResources();
    } catch (Exception ignored) {
    }
    try {
      disposables.dispose();
    } catch (Exception ignored) {
    }
    try {
      notificationsPanel.close();
    } catch (Exception ignored) {
    }
    try {
      dccTransfersPanel.close();
    } catch (Exception ignored) {
    }
    try {
      logViewerPanel.close();
    } catch (Exception ignored) {
    }
    try {
      interceptorPanel.close();
    } catch (Exception ignored) {
    }
    try {
      readMarkerCoordinator.clearAll();
    } catch (Exception ignored) {
    }
    try {
      transcripts.setReactionChipActionHandler(null);
    } catch (Exception ignored) {
    }
    // Ensure decorator listeners/subscriptions are removed when Spring disposes this dock.
    closeDecorators();
  }
}
