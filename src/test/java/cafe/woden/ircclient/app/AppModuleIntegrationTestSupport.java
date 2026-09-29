package cafe.woden.ircclient.app;

import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.outbound.filter.LocalFilterCommandHandler;
import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3LabeledResponseRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageIdRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageMutationRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageMutationRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MonitorCommandRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.port.IrcChatHistoryPort;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.port.IrcIdentityPort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcMessagingPort;
import cafe.woden.ircclient.irc.port.IrcMonitorPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.port.IrcShutdownPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.ChannelFlagModeStatePort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.ModeRoutingPort;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.PendingInvitePort;
import cafe.woden.ircclient.state.api.RecentStatusModePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Answers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * External boundary doubles for app-module integration tests only.
 *
 * <p>App tests mutate connection and target state. Rebuild the context between test classes even
 * when their boundary doubles match and Spring could otherwise reuse the same context.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AppModuleIntegrationTestSupport extends AbstractApplicationModuleIntegrationTest {

  @TestBean ServerIsupportStatePort serverIsupportStatePort;

  @SuppressWarnings("unused")
  static ServerIsupportStatePort serverIsupportStatePort() {
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

  @TestBean Ircv3CapabilityNameResolverPort ircv3CapabilityNameResolverPort;

  @SuppressWarnings("unused")
  static Ircv3CapabilityNameResolverPort ircv3CapabilityNameResolverPort() {
    return new Ircv3CapabilityNameResolverPort() {};
  }

  @TestBean Ircv3MessageMutationRuntimeCatalog ircv3MessageMutationRuntimeCatalog;

  @SuppressWarnings("unused")
  static Ircv3MessageMutationRuntimeCatalog ircv3MessageMutationRuntimeCatalog() {
    return Ircv3MessageMutationRuntimeCatalog.fromProviders(List.of());
  }

  @MockitoBean Ircv3MessageMutationRuntimeSupport ircv3MessageMutationRuntimeSupport;

  @TestBean Ircv3MessageIdRuntimeSupport ircv3MessageIdRuntimeSupport;

  @SuppressWarnings("unused")
  static Ircv3MessageIdRuntimeSupport ircv3MessageIdRuntimeSupport() {
    return new Ircv3MessageIdRuntimeSupport(
        Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of()));
  }

  @MockitoBean Ircv3LabeledResponseRuntimeSupport ircv3LabeledResponseRuntimeSupport;

  @MockitoBean Ircv3ChatHistoryRuntimeSupport ircv3ChatHistoryRuntimeSupport;

  @MockitoBean Ircv3MonitorCommandRuntimeSupport ircv3MonitorCommandRuntimeSupport;

  @MockitoBean Ircv3OutboundCommandRuntimeCatalog ircv3OutboundCommandRuntimeCatalog;

  @MockitoBean(name = "ircIdentityPort")
  IrcIdentityPort ircIdentityPort;

  @MockitoBean(name = "ircMessagingPort")
  IrcMessagingPort ircMessagingPort;

  @MockitoBean(name = "ircChatHistoryPort")
  IrcChatHistoryPort ircChatHistoryPort;

  @MockitoBean(name = "ircMonitorPort")
  IrcMonitorPort ircMonitorPort;

  @MockitoBean(name = "ircShutdownPort")
  IrcShutdownPort ircShutdownPort;

  @MockitoBean(name = "ircCurrentNickPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcCurrentNickPort ircCurrentNickPort;

  @MockitoBean(name = "ircTargetMembershipPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcTargetMembershipPort ircTargetMembershipPort;

  @MockitoBean(name = "ircMediatorInteractionPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcMediatorInteractionPort ircMediatorInteractionPort;

  @MockitoBean(name = "ircConnectionLifecyclePort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcConnectionLifecyclePort ircConnectionLifecyclePortDouble;

  @MockitoBean(name = "ircNegotiatedFeaturePort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcNegotiatedFeaturePort ircNegotiatedFeaturePort;

  @MockitoBean(name = "ircReadMarkerPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcReadMarkerPort ircReadMarkerPort;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  IrcTypingPort ircTypingPort;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  IrcEchoCapabilityPort ircEchoCapabilityPort;

  @MockitoBean(name = "swingUiPort", answers = Answers.RETURNS_DEEP_STUBS)
  UiPort swingUiPortDouble;

  @MockitoBean(name = "swingUiEventPort", answers = Answers.RETURNS_DEEP_STUBS)
  UiEventPort swingUiEventPort;

  @MockitoBean UiSettingsPort uiSettingsPort;

  @MockitoBean ChatTranscriptHistoryPort chatTranscriptHistoryPort;

  @MockitoBean TrayNotificationsPort trayNotificationsPort;

  @MockitoBean UserListStore userListStoreDouble;

  @MockitoBean UserhostQueryService userhostQueryService;

  @MockitoBean UserInfoEnrichmentService userInfoEnrichmentService;

  @MockitoBean IgnoreListQueryPort ignoreListQueryPort;

  @MockitoBean IgnoreListCommandPort ignoreListCommandPort;

  @MockitoBean LocalFilterCommandHandler localFilterCommandHandler;

  @MockitoBean InboundIgnorePolicyPort inboundIgnorePolicy;

  @MockitoBean MonitorRosterPort monitorRosterPort;

  @MockitoBean MonitorFallbackPort monitorFallbackPort;

  @MockitoBean InterceptorIngestPort interceptorIngestPort;

  @MockitoBean IrcEventNotifierPort ircEventNotifierPort;

  @MockitoBean NotificationRuleMatcherPort notificationRuleMatcherPort;

  @MockitoBean(name = "ircClientService", answers = Answers.RETURNS_DEEP_STUBS)
  BackendPorts backendPorts;

  // One qualified backend bean provides these three independent boundary contracts.
  interface BackendPorts
      extends IrcBackendAvailabilityPort, IrcBouncerPlaybackPort, QuasselCoreControlPort {}

  @BeforeEach
  void resetBoundaryStreams() {
    when(backendPorts.quasselCoreNetworkEvents()).thenReturn(Flowable.empty());
    when(backendPorts.backendAvailabilityReason(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("");
    when(swingUiEventPort.targetSelections()).thenReturn(Flowable.empty());
    when(swingUiEventPort.targetActivations()).thenReturn(Flowable.empty());
    when(swingUiEventPort.privateMessageRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.userActionRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.outboundLines()).thenReturn(Flowable.empty());
    when(swingUiEventPort.connectClicks()).thenReturn(Flowable.empty());
    when(swingUiEventPort.disconnectClicks()).thenReturn(Flowable.empty());
    when(swingUiEventPort.connectServerRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.disconnectServerRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.backendNamedCommandRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.closeTargetRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.joinChannelRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.disconnectChannelRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.bouncerDetachChannelRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.closeChannelRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.clearLogRequests()).thenReturn(Flowable.empty());
    when(swingUiEventPort.ircv3CapabilityToggleRequests()).thenReturn(Flowable.empty());
  }

  @MockitoBean ChatHistoryIngestionPort chatHistoryIngestionPort;

  @MockitoBean ChatHistoryIngestEventsPort chatHistoryIngestEventsPort;

  @MockitoBean ChatHistoryBatchEventsPort chatHistoryBatchEventsPort;

  @MockitoBean ZncPlaybackEventsPort zncPlaybackEventsPort;

  @MockitoBean TargetChatHistoryPort targetChatHistoryPort;

  @MockitoBean TargetLogMaintenancePort targetLogMaintenancePort;

  @MockitoBean DccTransferStore dccTransferStore;

  @MockitoBean ModeRoutingPort modeRoutingPort;

  @MockitoBean ChannelFlagModeStatePort channelFlagModeStatePort;

  @MockitoBean RecentStatusModePort recentStatusModePort;

  @MockitoBean AwayRoutingPort awayRoutingPort;

  @MockitoBean ChatHistoryRequestRoutingPort chatHistoryRequestRoutingPort;

  @MockitoBean CtcpRoutingPort ctcpRoutingPort;

  @MockitoBean JoinRoutingPort joinRoutingPort;

  @MockitoBean LabeledResponseRoutingPort labeledResponseRoutingPort;

  @MockitoBean PendingEchoMessagePort pendingEchoMessagePort;

  @MockitoBean PendingInvitePort pendingInvitePort;

  @MockitoBean WhoisRoutingPort whoisRoutingPort;
}
