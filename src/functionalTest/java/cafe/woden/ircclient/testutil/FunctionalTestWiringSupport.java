package cafe.woden.ircclient.testutil;

import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.JoinModeBurstService;
import cafe.woden.ircclient.app.ModeFormattingService;
import cafe.woden.ircclient.app.api.ChannelMetadataPort;
import cafe.woden.ircclient.app.api.Ircv3ReadMarkerFeatureSupport;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiPortDecorator;
import cafe.woden.ircclient.app.api.UiTranscriptPort;
import cafe.woden.ircclient.app.commands.SlashCommandPresentationCatalog;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.ConnectionRuntimeConfigPort;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.config.api.ServerTreeRuntimeConfigPort;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.diagnostics.ApplicationDiagnosticsService;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.SpringRuntimeEventsService;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionCatalog;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.logging.ChatLogWriter;
import cafe.woden.ircclient.logging.LogLineFactory;
import cafe.woden.ircclient.logging.LoggingUiPortDecorator;
import cafe.woden.ircclient.logging.NoOpChatRedactionAuditService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.state.api.ChannelFlagModeStatePort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.RecentStatusModePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import cafe.woden.ircclient.ui.UserListDockable;
import cafe.woden.ircclient.ui.backend.BackendUiProfileProvider;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor;
import cafe.woden.ircclient.ui.servertree.ServerTreeExternalStreamBinder;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeChannelInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeCompositionAssembler;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLifecycleSettingsCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeTargetLifecycleCoordinatorFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeTreeInteractionBindingsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeViewInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeApplicationRootVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelTargetOperations;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreePrivateMessageOnlineStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRequestApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRuntimeHeaderApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLeafVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiRefreshCoordinator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeNodeActionsFactory;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutVisibilityFacade;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutApplier;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeRootSiblingOrderCoordinator;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeChannelListNodeEnsurer;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionFallbackPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionPersistencePolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeStartupSelectionRestorer;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilitySettings;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeExpansionStateManager;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeCellPresentationPolicy;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeQuasselNetworkNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTargetNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipTextPolicy;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.mockito.Mockito;

/** Compatibility constructors for functional tests while wiring evolves during refactors. */
public final class FunctionalTestWiringSupport {
  private FunctionalTestWiringSupport() {}

  public static ServerIsupportStatePort fallbackIsupportState() {
    return new ServerIsupportStatePort() {
      @Override
      public void applyIsupportToken(String serverId, String tokenName, String tokenValue) {}

      @Override
      public ModeVocabulary vocabularyForServer(String serverId) {
        return ModeVocabulary.fallback();
      }

      @Override
      public void clearServer(String serverId) {}
    };
  }

  public static ModeFormattingService newModeFormattingService(
      ServerIsupportStatePort serverIsupportState) {
    try {
      Constructor<ModeFormattingService> ctor =
          ModeFormattingService.class.getConstructor(ServerIsupportStatePort.class);
      return ctor.newInstance(serverIsupportState);
    } catch (NoSuchMethodException ignored) {
      try {
        Constructor<ModeFormattingService> ctor = ModeFormattingService.class.getConstructor();
        return ctor.newInstance();
      } catch (Exception e) {
        throw new IllegalStateException("Unable to construct ModeFormattingService", e);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to construct ModeFormattingService", e);
    }
  }

  public static ServerTreeDockable newServerTreeDockable(
      ServerCatalog serverCatalog,
      ServerTreeRuntimeConfigPort runtimeConfig,
      LogProperties logProps,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      UiSettingsBus settingsBus,
      ServerDialogs serverDialogs) {
    ServerTreeEdtExecutor edtExecutor = new ServerTreeEdtExecutor();
    return new ServerTreeDockable(
        serverCatalog,
        runtimeConfig,
        logProps,
        null,
        sojuAutoConnect,
        zncAutoConnect,
        connectBtn,
        disconnectBtn,
        notificationStore,
        interceptorStore,
        settingsBus,
        serverDialogs,
        null,
        null,
        new ServerTreeNetworkInfoDialogBuilder(
            runtimeConfig, Ircv3ExtensionCatalog.builtInCatalog()),
        new ServerTreeBuiltInVisibilitySettings(),
        new ServerTreeCellPresentationPolicy(),
        new ServerTreeServerLabelPolicy(),
        new ServerTreeBouncerDetachPolicy(),
        new ServerTreeSelectionFallbackPolicy(),
        new ServerTreeSelectionPersistencePolicy(),
        new ServerTreeStartupSelectionRestorer(),
        new ServerTreeTargetNodePolicy(interceptorStore),
        new cafe.woden.ircclient.ui.servertree.query.ServerTreeServerNodeResolver(),
        new cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier(),
        new cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver(),
        new cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager(),
        new ServerTreeNetworkGroupManager(),
        new cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater(),
        new cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions(),
        new ServerTreeServerCatalogSynchronizer(),
        new ServerTreePrivateMessageOnlineStateCoordinator(),
        new ServerTreeUiLeafVisibilitySynchronizer(),
        new ServerTreeApplicationRootVisibilityCoordinator(),
        new ServerTreeChannelListNodeEnsurer(),
        new ServerTreeTargetSnapshotProvider(),
        new ServerTreeChannelQueryService(edtExecutor),
        new ServerTreeChannelTargetOperations(edtExecutor),
        new ServerTreeRequestApi(),
        new ServerTreeRuntimeHeaderApi(new ServerTreeServerRuntimeUiUpdater()),
        new ServerTreeUiRefreshCoordinator(),
        new ServerTreeServerLeafVisibilityCoordinator(),
        new ServerTreeExpansionStateManager(),
        new ServerTreeBuiltInLayoutVisibilityFacade(),
        new ServerTreeExternalStreamBinder(),
        edtExecutor,
        newServerTreeCompositionAssembler(runtimeConfig));
  }

  private static ServerTreeCompositionAssembler newServerTreeCompositionAssembler(
      ServerTreeRuntimeConfigPort runtimeConfig) {
    ServerTreeLayoutApplier layoutApplier = new ServerTreeLayoutApplier();
    ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator =
        new ServerTreeBuiltInLayoutCoordinator(runtimeConfig);
    ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator =
        new ServerTreeRootSiblingOrderCoordinator(runtimeConfig);
    ServerTreeServerNodeBuilder serverNodeBuilder = new ServerTreeServerNodeBuilder();
    ServerTreeNodeActionsFactory nodeActionsFactory = new ServerTreeNodeActionsFactory();
    return new ServerTreeCompositionAssembler(
        new ServerTreeLayoutCollaboratorsFactory(
            layoutApplier, builtInLayoutCoordinator, rootSiblingOrderCoordinator),
        new ServerTreeStateInteractionCollaboratorsFactory(
            new ServerTreeEnsureNodeParentResolver(),
            new ServerTreeEnsureNodeLeafInserter(),
            new ServerTreeServerRuntimeUiUpdater(),
            new ServerTreeServerStateCleaner(),
            new cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator(),
            new ServerTreeTargetRemovalStateCoordinator()),
        new ServerTreeViewInteractionCollaboratorsFactory(
            new ServerTreeContextMenuBuilder(
                new ServerTreeServerNodeMenuBuilder(),
                new ServerTreeTargetNodeMenuBuilder(),
                new ServerTreeQuasselNetworkNodeMenuBuilder()),
            new ServerTreeTooltipResolver(),
            new ServerTreeTooltipTextPolicy()),
        new ServerTreeLifecycleSettingsCollaboratorsFactory(serverNodeBuilder),
        new ServerTreeTargetLifecycleCoordinatorFactory(),
        new ServerTreeChannelInteractionCollaboratorsFactory(),
        new ServerTreeTreeInteractionBindingsFactory(nodeActionsFactory));
  }

  public static InboundModeEventHandler newInboundModeEventHandler(
      UiPort ui,
      ModeRoutingPort modeRoutingState,
      JoinModeBurstService joinModeBurstService,
      ModeFormattingService modeFormattingService,
      ChannelFlagModeStatePort channelFlagModeState,
      RecentStatusModePort recentStatusModeState,
      ServerIsupportStatePort serverIsupportState) {
    try {
      Constructor<InboundModeEventHandler> ctor =
          InboundModeEventHandler.class.getConstructor(
              UiPort.class,
              ModeRoutingPort.class,
              JoinModeBurstService.class,
              ModeFormattingService.class,
              ChannelFlagModeStatePort.class,
              RecentStatusModePort.class,
              ServerIsupportStatePort.class);
      return ctor.newInstance(
          ui,
          modeRoutingState,
          joinModeBurstService,
          modeFormattingService,
          channelFlagModeState,
          recentStatusModeState,
          serverIsupportState);
    } catch (NoSuchMethodException ignored) {
      try {
        Constructor<InboundModeEventHandler> ctor =
            InboundModeEventHandler.class.getConstructor(
                UiPort.class,
                ModeRoutingPort.class,
                JoinModeBurstService.class,
                ModeFormattingService.class,
                ChannelFlagModeStatePort.class,
                RecentStatusModePort.class);
        return ctor.newInstance(
            ui,
            modeRoutingState,
            joinModeBurstService,
            modeFormattingService,
            channelFlagModeState,
            recentStatusModeState);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to construct InboundModeEventHandler", e);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to construct InboundModeEventHandler", e);
    }
  }

  public static ConnectionCoordinator newConnectionCoordinator(
      IrcConnectionLifecyclePort lifecycle,
      IrcBackendAvailabilityPort backendAvailability,
      QuasselCoreControlPort quasselControl,
      UiPort ui,
      ServerRegistry serverRegistry,
      ServerCatalog serverCatalog,
      ConnectionRuntimeConfigPort runtimeConfig,
      LogProperties logProps,
      TrayNotificationsPort trayNotificationsPort) {
    try {
      Constructor<ConnectionCoordinator> ctor =
          ConnectionCoordinator.class.getConstructor(
              IrcConnectionLifecyclePort.class,
              IrcBackendClientService.class,
              UiPort.class,
              ServerRegistry.class,
              ServerCatalog.class,
              ConnectionRuntimeConfigPort.class,
              LogProperties.class,
              TrayNotificationsPort.class);
      return ctor.newInstance(
          lifecycle,
          compositeBackendClient(backendAvailability, quasselControl),
          ui,
          serverRegistry,
          serverCatalog,
          runtimeConfig,
          logProps,
          trayNotificationsPort);
    } catch (NoSuchMethodException ignored) {
      try {
        Constructor<ConnectionCoordinator> ctor =
            ConnectionCoordinator.class.getConstructor(
                IrcConnectionLifecyclePort.class,
                IrcBackendAvailabilityPort.class,
                QuasselCoreControlPort.class,
                UiPort.class,
                ServerRegistry.class,
                ServerCatalog.class,
                ConnectionRuntimeConfigPort.class,
                LogProperties.class,
                TrayNotificationsPort.class);
        return ctor.newInstance(
            lifecycle,
            backendAvailability,
            quasselControl,
            ui,
            serverRegistry,
            serverCatalog,
            runtimeConfig,
            logProps,
            trayNotificationsPort);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to construct ConnectionCoordinator", e);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to construct ConnectionCoordinator", e);
    }
  }

  public static ConnectionCoordinator newConnectionCoordinator(
      IrcConnectionLifecyclePort lifecycle,
      IrcBackendClientService backendClient,
      UiPort ui,
      ServerRegistry serverRegistry,
      ServerCatalog serverCatalog,
      ConnectionRuntimeConfigPort runtimeConfig,
      LogProperties logProps,
      TrayNotificationsPort trayNotificationsPort) {
    try {
      Constructor<ConnectionCoordinator> ctor =
          ConnectionCoordinator.class.getConstructor(
              IrcConnectionLifecyclePort.class,
              IrcBackendClientService.class,
              UiPort.class,
              ServerRegistry.class,
              ServerCatalog.class,
              ConnectionRuntimeConfigPort.class,
              LogProperties.class,
              TrayNotificationsPort.class);
      return ctor.newInstance(
          lifecycle,
          backendClient,
          ui,
          serverRegistry,
          serverCatalog,
          runtimeConfig,
          logProps,
          trayNotificationsPort);
    } catch (NoSuchMethodException ignored) {
      return newConnectionCoordinator(
          lifecycle,
          IrcBackendAvailabilityPort.from(backendClient),
          (backendClient instanceof QuasselCoreControlPort control)
              ? control
              : new QuasselCoreControlPort() {},
          ui,
          serverRegistry,
          serverCatalog,
          runtimeConfig,
          logProps,
          trayNotificationsPort);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to construct ConnectionCoordinator", e);
    }
  }

  public static ChatDockable newChatDockable(
      ChatTranscriptStore transcripts,
      ServerTreeDockable serverTree,
      NotificationStore notificationStore,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      IrcClientService irc,
      ModeRoutingPort modeRoutingState,
      ServerIsupportStatePort serverIsupportState,
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
      ApplicationDiagnosticsService applicationDiagnosticsService,
      JfrRuntimeEventsService jfrRuntimeEventsService,
      SpringRuntimeEventsService springRuntimeEventsService,
      UiSettingsBus settingsBus,
      SpellcheckSettingsBus spellcheckSettingsBus,
      CommandHistoryStore commandHistoryStore,
      ExecutorService logViewerExecutor,
      ExecutorService interceptorRefreshExecutor) {
    ChatRedactionAuditService redactionAuditService = new NoOpChatRedactionAuditService();
    InstalledPluginsPort installedPluginsPort = Mockito.mock(InstalledPluginsPort.class);
    SlashCommandPresentationCatalog slashCommandPresentationCatalog =
        Mockito.mock(SlashCommandPresentationCatalog.class);
    Ircv3ReadMarkerFeatureSupport readMarkerFeatureSupport =
        Mockito.mock(Ircv3ReadMarkerFeatureSupport.class);
    return new ChatDockable(
        transcripts,
        serverTree,
        notificationStore,
        activationBus,
        outboundBus,
        irc,
        readMarkerFeatureSupport,
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
        redactionAuditService,
        interceptorStore,
        dccTransferStore,
        terminalDockable,
        applicationDiagnosticsService,
        jfrRuntimeEventsService,
        springRuntimeEventsService,
        installedPluginsPort,
        slashCommandPresentationCatalog,
        settingsBus,
        spellcheckSettingsBus,
        commandHistoryStore,
        logViewerExecutor,
        interceptorRefreshExecutor);
  }

  public static UiPort newLoggingUiPort(
      UiPort swingUiPort, ChatLogWriter writer, LogLineFactory factory, LogProperties props) {
    UiTranscriptPort loggingTranscriptUiPort =
        new LoggingUiPortDecorator(
            swingUiPort,
            writer,
            org.mockito.Mockito.mock(cafe.woden.ircclient.logging.ChatLogRepository.class),
            new NoOpChatRedactionAuditService(),
            factory,
            props);
    try {
      Class<?> decoratorClass =
          Class.forName("cafe.woden.ircclient.logging.TranscriptDecoratingUiPort");
      Constructor<?> ctor =
          decoratorClass.getDeclaredConstructor(UiPort.class, UiTranscriptPort.class);
      ctor.setAccessible(true);
      return (UiPort) ctor.newInstance(swingUiPort, loggingTranscriptUiPort);
    } catch (Exception e) {
      return new UiPortDecorator(swingUiPort) {};
    }
  }

  private static IrcBackendClientService compositeBackendClient(
      IrcBackendAvailabilityPort backendAvailability, QuasselCoreControlPort quasselControl) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
          }
          if ("backend".equals(method.getName()) && method.getParameterCount() == 0) {
            return IrcProperties.Server.Backend.QUASSEL_CORE;
          }
          Object delegated = invokeIfPresent(backendAvailability, method, args);
          if (delegated != NOT_HANDLED) return delegated;
          delegated = invokeIfPresent(quasselControl, method, args);
          if (delegated != NOT_HANDLED) return delegated;
          return defaultValue(method.getReturnType());
        };
    return (IrcBackendClientService)
        Proxy.newProxyInstance(
            FunctionalTestWiringSupport.class.getClassLoader(),
            new Class<?>[] {IrcBackendClientService.class},
            handler);
  }

  private static final Object NOT_HANDLED = new Object();

  private static Object invokeIfPresent(Object target, Method method, Object[] args)
      throws Throwable {
    if (target == null) return NOT_HANDLED;
    try {
      Method delegateMethod =
          target.getClass().getMethod(method.getName(), method.getParameterTypes());
      return delegateMethod.invoke(target, args);
    } catch (NoSuchMethodException e) {
      return NOT_HANDLED;
    }
  }

  private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
    return switch (method.getName()) {
      case "toString" ->
          FunctionalTestWiringSupport.class.getSimpleName() + "$CompositeBackendClient";
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
      default -> null;
    };
  }

  private static Object defaultValue(Class<?> returnType) {
    Objects.requireNonNull(returnType, "returnType");
    if (returnType == Void.TYPE) return null;
    if (returnType == Boolean.TYPE) return false;
    if (returnType == Byte.TYPE) return (byte) 0;
    if (returnType == Short.TYPE) return (short) 0;
    if (returnType == Integer.TYPE) return 0;
    if (returnType == Long.TYPE) return 0L;
    if (returnType == Float.TYPE) return 0f;
    if (returnType == Double.TYPE) return 0d;
    if (returnType == Character.TYPE) return '\0';
    if (returnType == String.class) return "";
    if (returnType == Optional.class) return Optional.empty();
    if (returnType == List.class) return List.of();
    if (returnType == Set.class) return Set.of();
    if (returnType == Map.class) return Map.of();
    if (returnType == Completable.class) return Completable.complete();
    if (returnType == Flowable.class) return Flowable.empty();
    return null;
  }
}
