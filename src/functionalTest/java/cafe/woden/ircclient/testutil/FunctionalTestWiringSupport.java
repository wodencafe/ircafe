package cafe.woden.ircclient.testutil;

import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.JoinModeBurstService;
import cafe.woden.ircclient.app.ModeFormattingService;
import cafe.woden.ircclient.app.api.ChannelMetadataPort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiPortDecorator;
import cafe.woden.ircclient.app.api.UiTranscriptPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.ConnectionRuntimeConfigPort;
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
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.logging.ChatLogWriter;
import cafe.woden.ircclient.logging.LogLineFactory;
import cafe.woden.ircclient.logging.LoggingUiPortDecorator;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
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
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
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

/** Compatibility constructors for functional tests while wiring evolves during refactors. */
public final class FunctionalTestWiringSupport {
  private FunctionalTestWiringSupport() {}

  public static ServerIsupportStatePort fallbackIsupportState() {
    return new ServerIsupportStatePort() {
      @Override
      public ModeVocabulary vocabularyForServer(String serverId) {
        return ModeVocabulary.fallback();
      }
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
    try {
      Constructor<ChatDockable> ctor =
          ChatDockable.class.getConstructor(
              ChatTranscriptStore.class,
              ServerTreeDockable.class,
              NotificationStore.class,
              TargetActivationBus.class,
              OutboundLineBus.class,
              IrcClientService.class,
              ModeRoutingPort.class,
              ServerIsupportStatePort.class,
              BackendUiProfileProvider.class,
              MessageActionCapabilityPolicy.class,
              ActiveInputRouter.class,
              IgnoreListService.class,
              IgnoreStatusService.class,
              IgnoreListDialog.class,
              MonitorListService.class,
              UserListStore.class,
              UserListDockable.class,
              NickContextMenuFactory.class,
              ServerProxyResolver.class,
              ChatHistoryService.class,
              ChannelMetadataPort.class,
              ChatLogViewerService.class,
              InterceptorStore.class,
              DccTransferStore.class,
              TerminalDockable.class,
              ApplicationDiagnosticsService.class,
              JfrRuntimeEventsService.class,
              SpringRuntimeEventsService.class,
              UiSettingsBus.class,
              SpellcheckSettingsBus.class,
              CommandHistoryStore.class,
              ExecutorService.class,
              ExecutorService.class);
      return ctor.newInstance(
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
          interceptorRefreshExecutor);
    } catch (NoSuchMethodException ignored) {
      try {
        Constructor<ChatDockable> ctor =
            ChatDockable.class.getConstructor(
                ChatTranscriptStore.class,
                ServerTreeDockable.class,
                NotificationStore.class,
                TargetActivationBus.class,
                OutboundLineBus.class,
                IrcClientService.class,
                BackendUiProfileProvider.class,
                MessageActionCapabilityPolicy.class,
                ActiveInputRouter.class,
                IgnoreListService.class,
                IgnoreStatusService.class,
                IgnoreListDialog.class,
                MonitorListService.class,
                UserListStore.class,
                UserListDockable.class,
                NickContextMenuFactory.class,
                ServerProxyResolver.class,
                ChatHistoryService.class,
                ChannelMetadataPort.class,
                ChatLogViewerService.class,
                InterceptorStore.class,
                DccTransferStore.class,
                TerminalDockable.class,
                ApplicationDiagnosticsService.class,
                JfrRuntimeEventsService.class,
                SpringRuntimeEventsService.class,
                UiSettingsBus.class,
                SpellcheckSettingsBus.class,
                CommandHistoryStore.class,
                ExecutorService.class,
                ExecutorService.class);
        return ctor.newInstance(
            transcripts,
            serverTree,
            notificationStore,
            activationBus,
            outboundBus,
            irc,
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
            interceptorRefreshExecutor);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to construct ChatDockable", e);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to construct ChatDockable", e);
    }
  }

  public static UiPort newLoggingUiPort(
      UiPort swingUiPort, ChatLogWriter writer, LogLineFactory factory, LogProperties props) {
    UiTranscriptPort loggingTranscriptUiPort =
        new LoggingUiPortDecorator(swingUiPort, writer, factory, props);
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
