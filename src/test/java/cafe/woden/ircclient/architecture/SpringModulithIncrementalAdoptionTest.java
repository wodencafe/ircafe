package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cafe.woden.ircclient.IrcSwingApp;
import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiPromptPort;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandHandler;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.OutboundCommandDispatcher;
import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.dispatch.DefaultOutboundCommandDispatcher;
import cafe.woden.ircclient.app.outbound.dispatch.ObservedOutboundCommandDispatcher;
import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.app.outbound.spi.LocalFilterCommandHandler;
import cafe.woden.ircclient.app.outbound.upload.spi.SemanticUploadCommandHandler;
import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.bouncer.AbstractBouncerAutoConnectStore;
import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkDiscoveryOrchestrator;
import cafe.woden.ircclient.config.NotificationRule;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.api.BouncerDiscoveryConfigPort;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ConnectionRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.config.api.DiagnosticsRuntimeConfigPort;
import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort;
import cafe.woden.ircclient.config.api.FilterSettingsConfigPort;
import cafe.woden.ircclient.config.api.IgnoreRulesConfigPort;
import cafe.woden.ircclient.config.api.InterceptorConfigPort;
import cafe.woden.ircclient.config.api.InviteAutoJoinConfigPort;
import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.config.api.Ircv3StsPolicyConfigPort;
import cafe.woden.ircclient.config.api.MonitorRosterConfigPort;
import cafe.woden.ircclient.config.api.NickColorOverridesConfigPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.config.api.ServerAutoConnectRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeRuntimeConfigPort;
import cafe.woden.ircclient.config.api.UiSettingsRuntimeConfigPort;
import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import cafe.woden.ircclient.config.api.UserCommandAliasesConfigPort;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.diagnostics.ApplicationDiagnosticsService;
import cafe.woden.ircclient.diagnostics.AssertjSwingDiagnosticsService;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.JfrSnapshotSummarizer;
import cafe.woden.ircclient.diagnostics.JhiccupDiagnosticsService;
import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.diagnostics.RuntimeJfrService;
import cafe.woden.ircclient.diagnostics.SpringRuntimeEventsService;
import cafe.woden.ircclient.ignore.InboundIgnorePolicy;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcDisconnectWithSourcePort;
import cafe.woden.ircclient.irc.adapter.BouncerIrcConnectionPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcConnectionLifecyclePortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcCurrentNickPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcEchoCapabilityPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcLagProbePortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcMediatorInteractionPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcNegotiatedFeaturePortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcReadMarkerPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcShutdownPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcTargetMembershipPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcTypingPortAdapter;
import cafe.woden.ircclient.irc.backend.BackendRoutingIrcClientService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3DraftNormalizer;
import cafe.woden.ircclient.irc.matrix.MatrixIrcClientService;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProvider;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProviderConfig;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.port.IrcLagProbePort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.port.IrcShutdownPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.irc.presence.IsonParsers;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.logging.LogLine;
import cafe.woden.ircclient.logging.LogRow;
import cafe.woden.ircclient.logging.LoggingTargetLogMaintenancePortAdapter;
import cafe.woden.ircclient.logging.history.LoggingAppHistoryPortsAdapter;
import cafe.woden.ircclient.logging.viewer.DbChatLogViewerService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.monitor.MonitorIsonFallbackService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.monitor.MonitorSyncService;
import cafe.woden.ircclient.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.notifications.IrcEventNotificationService;
import cafe.woden.ircclient.notifications.NotificationRuleMatcher;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.perform.PerformOnConnectService;
import cafe.woden.ircclient.state.AwayStatusStore;
import cafe.woden.ircclient.state.ModeRoutingState;
import cafe.woden.ircclient.state.PendingInviteState;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.ChannelFlagModeStatePort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.RecentStatusModePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import cafe.woden.ircclient.ui.SwingUiEventAdapter;
import cafe.woden.ircclient.ui.SwingUiPort;
import cafe.woden.ircclient.ui.application.RuntimeEventsPanel;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.filter.FilterEngine;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.settings.theme.ThemeIdUtils;
import cafe.woden.ircclient.ui.settings.theme.ThemeManager;
import cafe.woden.ircclient.ui.shell.MainFrame;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.util.VirtualThreads;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;

class SpringModulithIncrementalAdoptionTest {

  @Test
  void applicationModulesCanBeDiscovered() {
    assertThatCode(() -> ApplicationModules.of(IrcSwingApp.class)).doesNotThrowAnyException();
  }

  @Test
  void runtimeDiagnosticsTypesResolveToExpectedModules() {
    ApplicationModules modules = ApplicationModules.of(IrcSwingApp.class);

    ApplicationModule appModule = moduleFor(modules, IrcMediator.class);
    assertThat(appModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.app");
    assertAppNamedInterfaces(appModule);

    ApplicationModule modelModule = moduleFor(modules, TargetRef.class);
    assertThat(modelModule).isNotEqualTo(appModule);
    assertThat(modelModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.model");

    ApplicationModule stateModule = moduleFor(modules, ModeRoutingState.class);
    assertThat(stateModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, PendingInviteState.class)).isEqualTo(stateModule);
    assertThat(moduleFor(modules, AwayStatusStore.class)).isEqualTo(stateModule);
    assertThat(stateModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.state");
    assertNamedInterfaceContains(
        stateModule,
        "api",
        AwayRoutingPort.class,
        CtcpRoutingPort.class,
        ChatHistoryRequestRoutingPort.class,
        JoinRoutingPort.class,
        ModeRoutingPort.class,
        WhoisRoutingPort.class,
        LabeledResponseRoutingPort.class,
        PendingEchoMessagePort.class,
        PendingInvitePort.class,
        ChannelFlagModeStatePort.class,
        RecentStatusModePort.class);

    ApplicationModule configModule = moduleFor(modules, RuntimeConfigStore.class);
    assertThat(configModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, NotificationRule.class)).isEqualTo(configModule);
    assertThat(configModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.config");
    assertNamedInterfaceContains(
        configModule,
        "api",
        BouncerDiscoveryConfigPort.class,
        InviteAutoJoinConfigPort.class,
        ChatCommandRuntimeConfigPort.class,
        ConnectionRuntimeConfigPort.class,
        CtcpReplyRuntimeConfigPort.class,
        DiagnosticsRuntimeConfigPort.class,
        EmbedLoadPolicyConfigPort.class,
        FilterSettingsConfigPort.class,
        IgnoreRulesConfigPort.class,
        InterceptorConfigPort.class,
        Ircv3StsPolicyConfigPort.class,
        IrcSessionRuntimeConfigPort.class,
        MonitorRosterConfigPort.class,
        NickColorOverridesConfigPort.class,
        RuntimeConfigPathPort.class,
        ServerAutoConnectRuntimeConfigPort.class,
        ServerTreeBuiltInVisibilityConfigPort.class,
        ServerTreeChannelStateConfigPort.class,
        ServerTreeLayoutConfigPort.class,
        ServerTreeRuntimeConfigPort.class,
        UiShellRuntimeConfigPort.class,
        UiSettingsRuntimeConfigPort.class,
        UserCommandAliasesConfigPort.class);

    ApplicationModule bouncerModule = moduleFor(modules, AbstractBouncerAutoConnectStore.class);
    assertThat(bouncerModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, BouncerNetworkDiscoveryOrchestrator.class))
        .isEqualTo(bouncerModule);
    assertThat(bouncerModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.bouncer");

    ApplicationModule performModule = moduleFor(modules, PerformOnConnectService.class);
    assertThat(performModule).isNotEqualTo(appModule);
    assertThat(performModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.perform");

    ApplicationModule diagnosticsSupportModule = moduleFor(modules, RuntimeJfrService.class);
    assertThat(diagnosticsSupportModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, JfrRuntimeEventsService.class))
        .isEqualTo(diagnosticsSupportModule);
    assertThat(moduleFor(modules, SpringRuntimeEventsService.class))
        .isEqualTo(diagnosticsSupportModule);
    assertThat(moduleFor(modules, RuntimeDiagnosticEvent.class))
        .isEqualTo(diagnosticsSupportModule);
    assertThat(moduleFor(modules, ApplicationDiagnosticsService.class))
        .isEqualTo(diagnosticsSupportModule);
    assertThat(moduleFor(modules, AssertjSwingDiagnosticsService.class))
        .isEqualTo(diagnosticsSupportModule);
    assertThat(moduleFor(modules, JhiccupDiagnosticsService.class))
        .isEqualTo(diagnosticsSupportModule);
    assertThat(moduleFor(modules, JfrSnapshotSummarizer.class)).isEqualTo(diagnosticsSupportModule);
    assertThat(diagnosticsSupportModule.getBasePackage().getName())
        .isEqualTo("cafe.woden.ircclient.diagnostics");

    ApplicationModule monitorModule = moduleFor(modules, MonitorListService.class);
    assertThat(monitorModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, MonitorIsonFallbackService.class)).isEqualTo(monitorModule);
    assertThat(moduleFor(modules, MonitorSyncService.class)).isEqualTo(monitorModule);
    assertThat(monitorModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.monitor");

    ApplicationModule interceptorsModule = moduleFor(modules, InterceptorStore.class);
    assertThat(interceptorsModule).isNotEqualTo(appModule);
    assertThat(interceptorsModule.getBasePackage().getName())
        .isEqualTo("cafe.woden.ircclient.interceptors");

    ApplicationModule notificationsModule = moduleFor(modules, IrcEventNotificationService.class);
    assertThat(notificationsModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, NotificationStore.class)).isEqualTo(notificationsModule);
    assertThat(moduleFor(modules, NotificationRuleMatcher.class)).isEqualTo(notificationsModule);
    assertThat(moduleFor(modules, IrcEventNotificationRulesBus.class))
        .isEqualTo(notificationsModule);
    assertThat(notificationsModule.getBasePackage().getName())
        .isEqualTo("cafe.woden.ircclient.notifications");

    ApplicationModule dccModule = moduleFor(modules, DccTransferStore.class);
    assertThat(dccModule).isNotEqualTo(appModule);
    assertThat(dccModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.dcc");

    ApplicationModule ircModule = moduleFor(modules, IrcClientService.class);
    assertThat(ircModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, MatrixIrcClientService.class)).isEqualTo(ircModule);
    assertThat(ircModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.irc");
    assertNamedInterfaceContains(ircModule, "matrix", MatrixIrcClientService.class);
    assertNamedInterfaceContains(ircModule, "soju", SojuAutoConnectStore.class);
    assertNamedInterfaceContains(ircModule, "znc", ZncAutoConnectStore.class);
    assertNamedInterfaceContains(ircModule, "enrichment", UserInfoEnrichmentService.class);
    assertNamedInterfaceContains(
        ircModule, "ircv3", Ircv3CapabilityCatalog.class, Ircv3DraftNormalizer.class);
    assertNamedInterfaceContains(
        ircModule, "roster", UserListStore.class, UserhostQueryService.class);
    assertNamedInterfaceContains(
        ircModule,
        "playback",
        IrcBouncerPlaybackPort.class,
        PlaybackCursorProvider.class,
        PlaybackCursorProviderConfig.class);
    assertNamedInterfaceContains(
        ircModule,
        "port",
        IrcConnectionLifecyclePort.class,
        IrcCurrentNickPort.class,
        IrcEchoCapabilityPort.class,
        IrcLagProbePort.class,
        IrcMediatorInteractionPort.class,
        IrcNegotiatedFeaturePort.class,
        IrcReadMarkerPort.class,
        IrcShutdownPort.class,
        IrcTargetMembershipPort.class,
        IrcTypingPort.class);
    assertNamedInterfaceContains(ircModule, "presence", IsonParsers.class);

    ApplicationModule ignoreModule = moduleFor(modules, InboundIgnorePolicy.class);
    assertThat(ignoreModule).isNotEqualTo(appModule);
    assertThat(ignoreModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.ignore");
    assertNamedInterfaceContains(
        ignoreModule,
        "api",
        InboundIgnorePolicyPort.class,
        IgnoreListQueryPort.class,
        IgnoreListCommandPort.class);

    ApplicationModule loggingModule =
        moduleFor(modules, LoggingTargetLogMaintenancePortAdapter.class);
    assertThat(loggingModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, LoggingAppHistoryPortsAdapter.class)).isEqualTo(loggingModule);
    assertThat(moduleFor(modules, LogLine.class)).isEqualTo(loggingModule);
    assertThat(moduleFor(modules, LogRow.class)).isEqualTo(loggingModule);
    assertThat(loggingModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.logging");
    assertNamedInterfaceContains(loggingModule, "history", LoggingAppHistoryPortsAdapter.class);
    assertNamedInterfaceContains(loggingModule, "viewer", DbChatLogViewerService.class);

    ApplicationModule notifyModule = moduleFor(modules, NotificationSoundService.class);
    assertThat(notifyModule).isNotEqualTo(appModule);
    assertThat(moduleFor(modules, PushyNotificationService.class)).isEqualTo(notifyModule);
    assertThat(notifyModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.notify");
    assertNamedInterfaceContains(notifyModule, "sound", NotificationSoundService.class);
    assertNamedInterfaceContains(notifyModule, "pushy", PushyNotificationService.class);

    ApplicationModule uiModule = moduleFor(modules, RuntimeEventsPanel.class);
    assertThat(uiModule).isNotEqualTo(appModule);
    assertThat(uiModule.getBasePackage().getName()).startsWith("cafe.woden.ircclient.ui");
    assertNamedInterfaceContains(uiModule, "chat", ChatDockManager.class);
    assertNamedInterfaceContains(uiModule, "chat-fold", LoadOlderMessagesComponent.class);
    assertNamedInterfaceContains(uiModule, "filter", FilterEngine.class);
    assertNamedInterfaceContains(uiModule, "settings", UiSettingsBus.class);
    assertNamedInterfaceContains(
        uiModule, "settings-theme", ThemeManager.class, ThemeIdUtils.class);
    assertNamedInterfaceContains(uiModule, "shell", MainFrame.class);
    assertNamedInterfaceContains(uiModule, "terminal", TerminalDockable.class);
    assertNamedInterfaceContains(uiModule, "tray", TrayService.class);

    ApplicationModule utilModule = moduleFor(modules, VirtualThreads.class);
    assertThat(utilModule).isNotEqualTo(appModule);
    assertThat(utilModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.util");
  }

  @Test
  void appAndUiModulesDeclareExplicitAllowedDependencies() {
    org.springframework.modulith.ApplicationModule appModuleAnnotation =
        cafe.woden.ircclient.app.ApplicationShutdownCoordinator.class
            .getPackage()
            .getAnnotation(org.springframework.modulith.ApplicationModule.class);
    org.springframework.modulith.ApplicationModule uiModuleAnnotation =
        SwingUiPort.class
            .getPackage()
            .getAnnotation(org.springframework.modulith.ApplicationModule.class);

    assertThat(appModuleAnnotation).isNotNull();
    assertThat(appModuleAnnotation.allowedDependencies())
        .containsExactlyInAnyOrder(
            "config",
            "config::api",
            "dcc",
            "ignore::api",
            "irc",
            "irc::backend",
            "irc::enrichment",
            "irc::playback",
            "irc::port",
            "irc::quassel-control",
            "irc::roster",
            "model",
            "state::api",
            "util");

    assertThat(uiModuleAnnotation).isNotNull();
    assertThat(uiModuleAnnotation.allowedDependencies())
        .containsExactlyInAnyOrder(
            "app",
            "app::api",
            "app::commands",
            "app::outbound-filter",
            "bouncer",
            "config",
            "config::api",
            "dcc",
            "diagnostics",
            "ignore",
            "ignore::api",
            "interceptors",
            "irc",
            "irc::backend",
            "irc::ircv3",
            "irc::playback",
            "irc::port",
            "irc::quassel-control",
            "irc::roster",
            "irc::runtime",
            "irc::soju",
            "irc::znc",
            "logging::history",
            "logging::viewer",
            "model",
            "monitor",
            "net",
            "notifications",
            "notify::pushy",
            "notify::sound",
            "state::api",
            "util");
  }

  @Test
  void hexagonalRolesAreDeclaredOnAppPortsAndAdapters() {
    assertThat(MediatorControlPort.class.isAnnotationPresent(PrimaryPort.class)).isTrue();
    assertThat(ActiveTargetPort.class.isAnnotationPresent(PrimaryPort.class)).isTrue();
    assertThat(UiEventPort.class.isAnnotationPresent(PrimaryPort.class)).isTrue();
    assertThat(OutboundCommandDispatcher.class.isAnnotationPresent(PrimaryPort.class)).isTrue();

    assertThat(UiPromptPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(UiPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(LocalFilterCommandHandler.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(BackendNamedCommandHandler.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(BackendExtension.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(MessageMutationOutboundCommands.class.isAnnotationPresent(SecondaryPort.class))
        .isTrue();
    assertThat(OutboundBackendFeatureAdapter.class.isAnnotationPresent(SecondaryPort.class))
        .isTrue();
    assertThat(SemanticUploadCommandHandler.class.isAnnotationPresent(SecondaryPort.class))
        .isTrue();
    assertThat(UploadCommandTranslationHandler.class.isAnnotationPresent(SecondaryPort.class))
        .isTrue();
    for (Class<?> type :
        new Class<?>[] {
          BouncerDiscoveryConfigPort.class,
          ChatCommandRuntimeConfigPort.class,
          ConnectionRuntimeConfigPort.class,
          CtcpReplyRuntimeConfigPort.class,
          DiagnosticsRuntimeConfigPort.class,
          EmbedLoadPolicyConfigPort.class,
          FilterSettingsConfigPort.class,
          IgnoreRulesConfigPort.class,
          InterceptorConfigPort.class,
          InviteAutoJoinConfigPort.class,
          IrcSessionRuntimeConfigPort.class,
          Ircv3StsPolicyConfigPort.class,
          MonitorRosterConfigPort.class,
          NickColorOverridesConfigPort.class,
          RuntimeConfigPathPort.class,
          ServerAutoConnectRuntimeConfigPort.class,
          ServerTreeBuiltInVisibilityConfigPort.class,
          ServerTreeChannelStateConfigPort.class,
          ServerTreeLayoutConfigPort.class,
          ServerTreeRuntimeConfigPort.class,
          UiSettingsRuntimeConfigPort.class,
          UiShellRuntimeConfigPort.class,
          UserCommandAliasesConfigPort.class
        }) {
      assertThat(type.isAnnotationPresent(SecondaryPort.class)).as(type.getSimpleName()).isTrue();
    }
    assertThat(BouncerConnectionPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcDisconnectWithSourcePort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcBouncerPlaybackPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcConnectionLifecyclePort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcCurrentNickPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcEchoCapabilityPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcLagProbePort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcMediatorInteractionPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcNegotiatedFeaturePort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcReadMarkerPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcShutdownPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcTargetMembershipPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcTypingPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(TargetChatHistoryPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(TargetLogMaintenancePort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(TrayNotificationsPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(NotificationRuleMatcherPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(MonitorFallbackPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(MonitorRosterPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(ChatHistoryIngestionPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(ChatHistoryIngestEventsPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(ChatHistoryBatchEventsPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(InterceptorIngestPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(IrcEventNotifierPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();
    assertThat(ZncPlaybackEventsPort.class.isAnnotationPresent(SecondaryPort.class)).isTrue();

    assertThat(IrcMediator.class.isAnnotationPresent(Application.class)).isTrue();
    assertThat(TargetCoordinator.class.isAnnotationPresent(Application.class)).isTrue();
    assertThat(RuntimeConfigStore.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(BackendRoutingIrcClientService.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(BouncerIrcConnectionPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(SwingUiEventAdapter.class.isAnnotationPresent(PrimaryAdapter.class)).isTrue();
    assertThat(DefaultOutboundCommandDispatcher.class.isAnnotationPresent(PrimaryAdapter.class))
        .isTrue();
    assertThat(ObservedOutboundCommandDispatcher.class.isAnnotationPresent(PrimaryAdapter.class))
        .isTrue();
    assertThat(IrcConnectionLifecyclePortAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(IrcCurrentNickPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(IrcEchoCapabilityPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(IrcLagProbePortAdapter.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(IrcMediatorInteractionPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(IrcNegotiatedFeaturePortAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(IrcReadMarkerPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(IrcShutdownPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(IrcTargetMembershipPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(IrcTypingPortAdapter.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(SwingUiPort.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(TrayNotificationService.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(LoggingAppHistoryPortsAdapter.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(
            LoggingTargetLogMaintenancePortAdapter.class.isAnnotationPresent(
                SecondaryAdapter.class))
        .isTrue();
    assertThat(NotificationRuleMatcher.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
    assertThat(IrcEventNotificationService.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(MonitorIsonFallbackService.class.isAnnotationPresent(SecondaryAdapter.class))
        .isTrue();
    assertThat(MonitorListService.class.isAnnotationPresent(SecondaryAdapter.class)).isTrue();
  }

  @Test
  void moduleVerificationPassesWithCurrentBoundaries() {
    ApplicationModules.of(IrcSwingApp.class).verify();
  }

  private static ApplicationModule moduleFor(ApplicationModules modules, Class<?> type) {
    return modules
        .getModuleByType(type)
        .orElseThrow(() -> new AssertionError("No module discovered for type " + type.getName()));
  }

  private static void assertAppNamedInterfaces(ApplicationModule appModule) {
    assertNamedInterfaceContains(
        appModule,
        "api",
        UiEventPort.class,
        UiPort.class,
        UiPromptPort.class,
        ActiveTargetPort.class,
        MediatorControlPort.class,
        ChatHistoryIngestionPort.class,
        ChatHistoryIngestEventsPort.class,
        ChatHistoryBatchEventsPort.class,
        TargetChatHistoryPort.class,
        TargetLogMaintenancePort.class,
        ZncPlaybackEventsPort.class,
        InterceptorIngestPort.class,
        IrcEventNotifierPort.class,
        NotificationRuleMatcherPort.class,
        MonitorFallbackPort.class,
        MonitorRosterPort.class,
        TrayNotificationsPort.class);
    assertNamedInterfaceContains(
        appModule, "commands", UserCommandAliasesBus.class, FilterCommand.class);
    assertNamedInterfaceContains(appModule, "outbound-filter", LocalFilterCommandHandler.class);
  }

  private static void assertNamedInterfaceContains(
      ApplicationModule module, String namedInterface, Class<?>... requiredTypes) {
    NamedInterface diagnosticsInterface =
        module
            .getNamedInterfaces()
            .getByName(namedInterface)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Missing named interface "
                            + namedInterface
                            + " in module "
                            + module.getBasePackage().getName()));
    for (Class<?> type : requiredTypes) {
      assertThat(diagnosticsInterface.contains(type))
          .as(
              module.getBasePackage().getName()
                  + "::"
                  + namedInterface
                  + " should contain "
                  + type.getSimpleName())
          .isTrue();
    }
  }
}
