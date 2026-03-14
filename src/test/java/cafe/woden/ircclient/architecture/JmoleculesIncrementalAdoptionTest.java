package cafe.woden.ircclient.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cafe.woden.ircclient.app.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.JoinModeBurstService;
import cafe.woden.ircclient.app.ModeFormattingService;
import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.commands.BackendNamedCommandParser;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.FilterCommandParser;
import cafe.woden.ircclient.app.commands.UserCommandAliasEngine;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.MediatorConnectionSubscriptionBinder;
import cafe.woden.ircclient.app.core.MediatorHistoryIngestOrchestrator;
import cafe.woden.ircclient.app.core.MediatorUiSubscriptionBinder;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.bouncer.AbstractBouncerAutoConnectStore;
import cafe.woden.ircclient.bouncer.BouncerAutoConnectStore;
import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventDispatcher;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkDiscoveryOrchestrator;
import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
import cafe.woden.ircclient.bouncer.GenericBouncerEphemeralNetworkImporter;
import cafe.woden.ircclient.bouncer.GenericBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.bouncer.ResolvedBouncerNetwork;
import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ConnectionRuntimeConfigPort;
import cafe.woden.ircclient.config.api.InviteAutoJoinConfigPort;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.diagnostics.ApplicationDiagnosticsService;
import cafe.woden.ircclient.diagnostics.AssertjSwingDiagnosticsService;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.JfrSnapshotSummarizer;
import cafe.woden.ircclient.diagnostics.JhiccupDiagnosticsService;
import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.diagnostics.RuntimeJfrService;
import cafe.woden.ircclient.diagnostics.SpringRuntimeEventsService;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.ignore.InboundIgnorePolicy;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.interceptors.InterceptorHit;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.BackendRoutingIrcClientService;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcHeartbeatMaintenanceService;
import cafe.woden.ircclient.irc.ServerIrcEvent;
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
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentPlanner;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyService;
import cafe.woden.ircclient.irc.matrix.MatrixIrcClientService;
import cafe.woden.ircclient.irc.pircbotx.PircbotxBotFactory;
import cafe.woden.ircclient.irc.pircbotx.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.pircbotx.PircbotxIrcClientService;
import cafe.woden.ircclient.irc.playback.NoOpPlaybackCursorProvider;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProviderConfig;
import cafe.woden.ircclient.irc.quassel.QuasselCoreIrcClientService;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.soju.SojuBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.soju.SojuEphemeralNetworkImporter;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncBouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.znc.ZncEphemeralNetworkImporter;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.InterceptorRule;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.LogLine;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.model.UserCommandAlias;
import cafe.woden.ircclient.monitor.MonitorIsonFallbackService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.monitor.MonitorSyncService;
import cafe.woden.ircclient.net.NetHeartbeatBootstrap;
import cafe.woden.ircclient.net.NetProxyBootstrap;
import cafe.woden.ircclient.net.NetTlsBootstrap;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.notifications.IrcEventNotificationService;
import cafe.woden.ircclient.notifications.NotificationRuleMatcher;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.pushy.PushySettingsBus;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettingsBus;
import cafe.woden.ircclient.perform.PerformOnConnectService;
import cafe.woden.ircclient.state.AwayRoutingState;
import cafe.woden.ircclient.state.ChannelFlagModeState;
import cafe.woden.ircclient.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.state.CtcpRoutingState;
import cafe.woden.ircclient.state.JoinRoutingState;
import cafe.woden.ircclient.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.state.ModeRoutingState;
import cafe.woden.ircclient.state.PendingEchoMessageState;
import cafe.woden.ircclient.state.PendingInviteState;
import cafe.woden.ircclient.state.RecentStatusModeState;
import cafe.woden.ircclient.state.WhoisRoutingState;
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
import cafe.woden.ircclient.ui.SwingUiPort;
import java.lang.annotation.Annotation;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

class JmoleculesIncrementalAdoptionTest {

  @Test
  void layeredMarkersArePresentOnInitialBoundaryTypes() {
    assertAnnotated(UiPort.class, ApplicationLayer.class);
    assertAnnotated(IrcClientService.class, ApplicationLayer.class);
    assertAnnotated(IrcMediator.class, ApplicationLayer.class);
    assertAnnotated(ConnectionCoordinator.class, ApplicationLayer.class);
    assertAnnotated(TargetCoordinator.class, ApplicationLayer.class);
    assertAnnotated(ApplicationShutdownCoordinator.class, ApplicationLayer.class);
    assertAnnotated(InboundModeEventHandler.class, ApplicationLayer.class);
    assertAnnotated(JoinModeBurstService.class, ApplicationLayer.class);
    assertAnnotated(ModeFormattingService.class, ApplicationLayer.class);
    assertAnnotated(MediatorConnectionSubscriptionBinder.class, ApplicationLayer.class);
    assertAnnotated(MediatorUiSubscriptionBinder.class, ApplicationLayer.class);
    assertAnnotated(MediatorHistoryIngestOrchestrator.class, ApplicationLayer.class);
    assertAnnotated(NotificationStore.class, ApplicationLayer.class);
    assertAnnotated(WhoisRoutingState.class, ApplicationLayer.class);
    assertAnnotated(CtcpRoutingState.class, ApplicationLayer.class);
    assertAnnotated(ModeRoutingState.class, ApplicationLayer.class);
    assertAnnotated(AwayRoutingState.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryRequestRoutingState.class, ApplicationLayer.class);
    assertAnnotated(JoinRoutingState.class, ApplicationLayer.class);
    assertAnnotated(LabeledResponseRoutingState.class, ApplicationLayer.class);
    assertAnnotated(PendingEchoMessageState.class, ApplicationLayer.class);
    assertAnnotated(PendingInviteState.class, ApplicationLayer.class);
    assertAnnotated(ChannelFlagModeState.class, ApplicationLayer.class);
    assertAnnotated(RecentStatusModeState.class, ApplicationLayer.class);
    assertAnnotated(ApplicationDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(AssertjSwingDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(JhiccupDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(RuntimeJfrService.class, ApplicationLayer.class);
    assertAnnotated(JfrRuntimeEventsService.class, ApplicationLayer.class);
    assertAnnotated(SpringRuntimeEventsService.class, ApplicationLayer.class);
    assertAnnotated(MediatorControlPort.class, ApplicationLayer.class);
    assertAnnotated(ActiveTargetPort.class, ApplicationLayer.class);
    assertAnnotated(TrayNotificationsPort.class, ApplicationLayer.class);
    assertAnnotated(UiSettingsPort.class, ApplicationLayer.class);
    assertAnnotated(InboundIgnorePolicyPort.class, ApplicationLayer.class);
    assertAnnotated(IgnoreListQueryPort.class, ApplicationLayer.class);
    assertAnnotated(IgnoreListCommandPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryIngestionPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryIngestEventsPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryBatchEventsPort.class, ApplicationLayer.class);
    assertAnnotated(ZncPlaybackEventsPort.class, ApplicationLayer.class);
    assertAnnotated(TargetChatHistoryPort.class, ApplicationLayer.class);
    assertAnnotated(TargetLogMaintenancePort.class, ApplicationLayer.class);
    assertAnnotated(ChatTranscriptHistoryPort.class, ApplicationLayer.class);
    assertAnnotated(InterceptorIngestPort.class, ApplicationLayer.class);
    assertAnnotated(IrcEventNotifierPort.class, ApplicationLayer.class);
    assertAnnotated(NotificationRuleMatcherPort.class, ApplicationLayer.class);
    assertAnnotated(MonitorFallbackPort.class, ApplicationLayer.class);
    assertAnnotated(MonitorRosterPort.class, ApplicationLayer.class);
    assertAnnotated(AwayRoutingPort.class, ApplicationLayer.class);
    assertAnnotated(CtcpRoutingPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryRequestRoutingPort.class, ApplicationLayer.class);
    assertAnnotated(JoinRoutingPort.class, ApplicationLayer.class);
    assertAnnotated(LabeledResponseRoutingPort.class, ApplicationLayer.class);
    assertAnnotated(ModeRoutingPort.class, ApplicationLayer.class);
    assertAnnotated(PendingEchoMessagePort.class, ApplicationLayer.class);
    assertAnnotated(PendingInvitePort.class, ApplicationLayer.class);
    assertAnnotated(WhoisRoutingPort.class, ApplicationLayer.class);
    assertAnnotated(ChannelFlagModeStatePort.class, ApplicationLayer.class);
    assertAnnotated(RecentStatusModePort.class, ApplicationLayer.class);
    assertAnnotated(InviteAutoJoinConfigPort.class, ApplicationLayer.class);
    assertAnnotated(ChatCommandRuntimeConfigPort.class, ApplicationLayer.class);
    assertAnnotated(ConnectionRuntimeConfigPort.class, ApplicationLayer.class);
    assertAnnotated(RuntimeConfigStore.class, ApplicationLayer.class);
    assertAnnotated(ServerRegistry.class, ApplicationLayer.class);
    assertAnnotated(ServerCatalog.class, ApplicationLayer.class);
    assertAnnotated(EphemeralServerRegistry.class, ApplicationLayer.class);
    assertAnnotated(NetProxyBootstrap.class, ApplicationLayer.class);
    assertAnnotated(NetHeartbeatBootstrap.class, ApplicationLayer.class);
    assertAnnotated(NetTlsBootstrap.class, ApplicationLayer.class);
    assertAnnotated(ServerProxyResolver.class, ApplicationLayer.class);
    assertAnnotated(BouncerConnectionPort.class, ApplicationLayer.class);
    assertAnnotated(BouncerAutoConnectStore.class, ApplicationLayer.class);
    assertAnnotated(AbstractBouncerAutoConnectStore.class, ApplicationLayer.class);
    assertAnnotated(BouncerDiscoveryEventPort.class, ApplicationLayer.class);
    assertAnnotated(BouncerBackendRegistry.class, ApplicationLayer.class);
    assertAnnotated(BouncerDiscoveryEventDispatcher.class, ApplicationLayer.class);
    assertAnnotated(BouncerNetworkDiscoveryOrchestrator.class, ApplicationLayer.class);
    assertAnnotated(GenericBouncerAutoConnectStore.class, ApplicationLayer.class);
    assertAnnotated(GenericBouncerNetworkMappingStrategy.class, ApplicationLayer.class);
    assertAnnotated(GenericBouncerEphemeralNetworkImporter.class, ApplicationLayer.class);
    assertAnnotated(CommandParser.class, ApplicationLayer.class);
    assertAnnotated(FilterCommandParser.class, ApplicationLayer.class);
    assertAnnotated(BackendNamedCommandParser.class, ApplicationLayer.class);
    assertAnnotated(UserCommandAliasEngine.class, ApplicationLayer.class);
    assertAnnotated(UserCommandAliasesBus.class, ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.commands.QuasselBackendNamedCommandHandler",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.BackendNamedOutboundCommandRouter",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.BackendUploadCommandRegistry", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.ChannelModeOutboundCommandRegistrar",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.HistoryMutationOutboundCommandRegistrar",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.IdentityMessagingOutboundCommandRegistrar",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.IgnoreCtcpOutboundCommandRegistrar",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.LifecycleBackendOutboundCommandRegistrar",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.UnknownOutboundCommandRegistrar",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundBackendFeatureRegistry", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundBackendCapabilityPolicy",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.CommandTargetPolicy", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.MessageMutationOutboundCommandsRouter",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.DefaultOutboundCommandDispatcher",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.ObservedOutboundCommandDispatcher",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.IrcMessageMutationOutboundCommands",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.MatrixMessageMutationOutboundCommands",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.MatrixOutboundBackendFeatureAdapter",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.MatrixOutboundCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.MatrixOutboundCommandSupport", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.MatrixUploadCommandTranslationHandler",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundChatHistoryCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundConnectionLifecycleCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundCtcpWhoisCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundDccCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundHelpCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundIgnoreCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundInviteCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundJoinPartCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundMessageMutationCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundMessagingCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundModeCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundMonitorCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundNamesWhoListCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundNickAwayCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundRawLineCorrelationService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundReadMarkerCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundSayQuoteCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundTopicKickCommandService",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.OutboundUploadCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.QuasselBackendNamedOutboundCommandHandler",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.QuasselMessageMutationOutboundCommands",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.QuasselOutboundBackendFeatureAdapter",
        ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.QuasselOutboundCommandService", ApplicationLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.app.outbound.QuasselOutboundCommandSupport", ApplicationLayer.class);
    assertAnnotated(LocalFilterCommandHandler.class, ApplicationLayer.class);
    assertAnnotated(PerformOnConnectService.class, ApplicationLayer.class);
    assertAnnotated(IrcHeartbeatMaintenanceService.class, ApplicationLayer.class);
    assertAnnotated(SojuAutoConnectStore.class, ApplicationLayer.class);
    assertAnnotated(SojuBouncerNetworkMappingStrategy.class, ApplicationLayer.class);
    assertAnnotated(SojuEphemeralNetworkImporter.class, ApplicationLayer.class);
    assertAnnotated(ZncAutoConnectStore.class, ApplicationLayer.class);
    assertAnnotated(ZncBouncerNetworkMappingStrategy.class, ApplicationLayer.class);
    assertAnnotated(ZncEphemeralNetworkImporter.class, ApplicationLayer.class);
    assertAnnotated(UserInfoEnrichmentService.class, ApplicationLayer.class);
    assertAnnotated(UserInfoEnrichmentPlanner.class, ApplicationLayer.class);
    assertAnnotated(UserListStore.class, ApplicationLayer.class);
    assertAnnotated(UserhostQueryService.class, ApplicationLayer.class);
    assertAnnotated(JfrSnapshotSummarizer.class, ApplicationLayer.class);
    assertAnnotated(MonitorListService.class, ApplicationLayer.class);
    assertAnnotated(MonitorIsonFallbackService.class, ApplicationLayer.class);
    assertAnnotated(MonitorSyncService.class, ApplicationLayer.class);
    assertAnnotated(NotificationSoundService.class, ApplicationLayer.class);
    assertAnnotated(NotificationSoundSettingsBus.class, ApplicationLayer.class);
    assertAnnotated(PushyNotificationService.class, ApplicationLayer.class);
    assertAnnotated(PushySettingsBus.class, ApplicationLayer.class);
    assertAnnotated(InterceptorStore.class, ApplicationLayer.class);
    assertAnnotated(IrcEventNotificationService.class, ApplicationLayer.class);
    assertAnnotated(NotificationRuleMatcher.class, ApplicationLayer.class);
    assertAnnotated(IrcEventNotificationRulesBus.class, ApplicationLayer.class);
    assertAnnotated(DccTransferStore.class, ApplicationLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.model.AwayStatusStore", ApplicationLayer.class);
    assertAnnotated(IgnoreListService.class, ApplicationLayer.class);
    assertAnnotated(IgnoreStatusService.class, ApplicationLayer.class);
    assertAnnotated(InboundIgnorePolicy.class, ApplicationLayer.class);

    assertAnnotated(SwingUiPort.class, InterfaceLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.ui.ChatDockable", InterfaceLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.ui.CommandHistoryStore", InterfaceLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.ui.NickContextMenuFactory", InterfaceLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.ui.UserListDockable", InterfaceLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.ui.servertree.ServerTreeDockable", InterfaceLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.ui.chat.embed.LinkPreviewResolverConfig", InterfaceLayer.class);
    assertAnnotated(BackendRoutingIrcClientService.class, InfrastructureLayer.class);
    assertAnnotated(BouncerIrcConnectionPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcConnectionLifecyclePortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcCurrentNickPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcEchoCapabilityPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcLagProbePortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcMediatorInteractionPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcNegotiatedFeaturePortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcReadMarkerPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcShutdownPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcTargetMembershipPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(IrcTypingPortAdapter.class, InfrastructureLayer.class);
    assertAnnotated(Ircv3StsPolicyService.class, InfrastructureLayer.class);
    assertAnnotated(NoOpPlaybackCursorProvider.class, InfrastructureLayer.class);
    assertAnnotated(PircbotxBotFactory.class, InfrastructureLayer.class);
    assertAnnotated(PircbotxInputParserHookInstaller.class, InfrastructureLayer.class);
    assertAnnotated(PlaybackCursorProviderConfig.class, InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionTimersRx", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.irc.playback.ZncPlaybackCaptureLifecycle", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.config.ExecutorConfig", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.config.IgnoreProperties", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.config.IrcProperties", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.config.LogProperties", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.config.PushyProperties", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.config.SojuProperties", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.config.UiProperties", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.config.ZncProperties", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.ChatLogDatabaseConfig", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.ChatLogMaintenanceConfig", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.ChatLogPlaybackCursorProvider", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.ChatLogWriterConfig", InfrastructureLayer.class);
    assertAnnotatedByName("cafe.woden.ircclient.logging.LogLineFactory", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.LoggingTargetLogMaintenancePortAdapter",
        InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.LoggingUiPortConfig", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.channelmeta.ChannelMetadataDatabaseConfig",
        InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.channelmeta.ChannelMetadataStore", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.ChatHistoryBatchBus", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.ChatHistoryIngestBus", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.ChatHistoryIngestorConfig",
        InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.ChatHistoryServiceConfig", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.DbChatHistoryIngestor", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.LoggingAppHistoryPortsAdapter",
        InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.RemoteOnlyChatHistoryService",
        InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.history.ZncPlaybackBus", InfrastructureLayer.class);
    assertAnnotatedByName(
        "cafe.woden.ircclient.logging.viewer.ChatLogViewerServiceConfig",
        InfrastructureLayer.class);
    assertAnnotated(PircbotxIrcClientService.class, InfrastructureLayer.class);
    assertAnnotated(MatrixIrcClientService.class, InfrastructureLayer.class);
    assertAnnotated(QuasselCoreIrcClientService.class, InfrastructureLayer.class);

    assertTrue(UiPort.class.isInterface(), "UiPort should remain an interface");
    assertTrue(
        MediatorControlPort.class.isInterface(), "MediatorControlPort should remain an interface");
    assertTrue(ActiveTargetPort.class.isInterface(), "ActiveTargetPort should remain an interface");
    assertTrue(IrcClientService.class.isInterface(), "IrcClientService should remain an interface");
    assertTrue(
        TrayNotificationsPort.class.isInterface(),
        "TrayNotificationsPort should remain an interface");
    assertTrue(UiSettingsPort.class.isInterface(), "UiSettingsPort should remain an interface");
    assertTrue(
        MonitorFallbackPort.class.isInterface(), "MonitorFallbackPort should remain an interface");
    assertTrue(
        MonitorRosterPort.class.isInterface(), "MonitorRosterPort should remain an interface");
    assertTrue(
        ChatHistoryIngestionPort.class.isInterface(),
        "ChatHistoryIngestionPort should remain an interface");
    assertTrue(
        LocalFilterCommandHandler.class.isInterface(),
        "LocalFilterCommandHandler should remain an interface");
    assertTrue(
        InterceptorIngestPort.class.isInterface(),
        "InterceptorIngestPort should remain an interface");
    assertTrue(
        IrcEventNotifierPort.class.isInterface(),
        "IrcEventNotifierPort should remain an interface");
    assertTrue(
        NotificationRuleMatcherPort.class.isInterface(),
        "NotificationRuleMatcherPort should remain an interface");
    assertTrue(
        InboundIgnorePolicyPort.class.isInterface(),
        "InboundIgnorePolicyPort should remain an interface");
    assertTrue(
        IgnoreListQueryPort.class.isInterface(), "IgnoreListQueryPort should remain an interface");
    assertTrue(
        IgnoreListCommandPort.class.isInterface(),
        "IgnoreListCommandPort should remain an interface");
    assertTrue(AwayRoutingPort.class.isInterface(), "AwayRoutingPort should remain an interface");
    assertTrue(CtcpRoutingPort.class.isInterface(), "CtcpRoutingPort should remain an interface");
    assertTrue(
        ChatHistoryRequestRoutingPort.class.isInterface(),
        "ChatHistoryRequestRoutingPort should remain an interface");
    assertTrue(JoinRoutingPort.class.isInterface(), "JoinRoutingPort should remain an interface");
    assertTrue(
        LabeledResponseRoutingPort.class.isInterface(),
        "LabeledResponseRoutingPort should remain an interface");
    assertTrue(ModeRoutingPort.class.isInterface(), "ModeRoutingPort should remain an interface");
    assertTrue(
        PendingEchoMessagePort.class.isInterface(),
        "PendingEchoMessagePort should remain an interface");
    assertTrue(
        PendingInvitePort.class.isInterface(), "PendingInvitePort should remain an interface");
    assertTrue(WhoisRoutingPort.class.isInterface(), "WhoisRoutingPort should remain an interface");
    assertTrue(
        ChannelFlagModeStatePort.class.isInterface(),
        "ChannelFlagModeStatePort should remain an interface");
    assertTrue(
        RecentStatusModePort.class.isInterface(),
        "RecentStatusModePort should remain an interface");
    assertTrue(
        InviteAutoJoinConfigPort.class.isInterface(),
        "InviteAutoJoinConfigPort should remain an interface");
    assertTrue(
        ChatCommandRuntimeConfigPort.class.isInterface(),
        "ChatCommandRuntimeConfigPort should remain an interface");
    assertTrue(
        ConnectionRuntimeConfigPort.class.isInterface(),
        "ConnectionRuntimeConfigPort should remain an interface");
    assertTrue(
        BouncerConnectionPort.class.isInterface(),
        "BouncerConnectionPort should remain an interface");
    assertTrue(
        BouncerDiscoveryEventPort.class.isInterface(),
        "BouncerDiscoveryEventPort should remain an interface");
    assertTrue(
        IrcHeartbeatMaintenanceService.class.isInterface(),
        "IrcHeartbeatMaintenanceService should remain an interface");
  }

  @Test
  void valueObjectMarkersArePresentOnSharedMessageTypes() {
    assertAnnotated(TargetRef.class, ValueObject.class);
    assertAnnotated(ChatHistoryEntry.class, ValueObject.class);
    assertAnnotated(ServerIrcEvent.class, ValueObject.class);
    assertAnnotated(LogLine.class, ValueObject.class);
    assertAnnotated(RuntimeDiagnosticEvent.class, ValueObject.class);
    assertAnnotated(PresenceEvent.class, ValueObject.class);
    assertAnnotated(PrivateMessageRequest.class, ValueObject.class);
    assertAnnotated(UserActionRequest.class, ValueObject.class);
    assertAnnotated(IrcEventNotificationRule.class, ValueObject.class);
    assertAnnotated(NotificationRuleMatch.class, ValueObject.class);
    assertAnnotated(UserCommandAlias.class, ValueObject.class);
    assertAnnotated(InterceptorDefinition.class, ValueObject.class);
    assertAnnotated(InterceptorRule.class, ValueObject.class);
    assertAnnotated(InterceptorHit.class, ValueObject.class);
    assertAnnotated(ResolvedBouncerNetwork.class, ValueObject.class);
  }

  @Test
  void componentEntryPointsInExtractedModulesRemainApplicationLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.perform", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.diagnostics", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.monitor", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.interceptors", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.notifications", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.dcc", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ignore", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.state", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.util", ApplicationLayer.class);
  }

  @Test
  void componentEntryPointsInUiSettingsRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.settings", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiFilterRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.filter", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiBusRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.bus", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiControlsRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.controls", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiUserlistRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.userlist", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiSupportPackagesRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.backend", InterfaceLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.coordinator", InterfaceLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.ignore", InterfaceLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.input", InterfaceLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.nickcolors", InterfaceLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.servers", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiShellRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.shell", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiTrayRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.tray", InterfaceLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.tray.dbus", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiTerminalRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.terminal", InterfaceLayer.class);
  }

  @Test
  void componentEntryPointsInUiChatRemainInterfaceLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.ui.chat", InterfaceLayer.class);
  }

  private static void assertComponentPackageAnnotated(
      String basePackage, Class<? extends Annotation> marker) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
    var candidates = scanner.findCandidateComponents(basePackage);
    assertTrue(!candidates.isEmpty(), "Expected at least one @Component in " + basePackage);
    for (BeanDefinition candidate : candidates) {
      String className = candidate.getBeanClassName();
      if (className == null || className.isBlank()) {
        fail("Missing bean class name for candidate in " + basePackage);
      }
      try {
        Class<?> type = Class.forName(className);
        if (!isMainSourceType(type)) {
          continue;
        }
        assertAnnotated(type, marker);
      } catch (ClassNotFoundException e) {
        fail("Could not load component class " + className, e);
      }
    }
  }

  private static void assertAnnotatedByName(String className, Class<? extends Annotation> marker) {
    try {
      Class<?> type = Class.forName(className);
      assertAnnotated(type, marker);
    } catch (ClassNotFoundException e) {
      fail("Could not load class " + className, e);
    }
  }

  private static void assertAnnotated(Class<?> type, Class<? extends Annotation> marker) {
    assertTrue(
        type.isAnnotationPresent(marker),
        () -> type.getName() + " must be annotated with @" + marker.getSimpleName());
  }

  private static boolean isMainSourceType(Class<?> type) {
    var codeSource = type.getProtectionDomain().getCodeSource();
    if (codeSource == null || codeSource.getLocation() == null) {
      return true;
    }
    return !codeSource.getLocation().getPath().contains("/test/");
  }
}
