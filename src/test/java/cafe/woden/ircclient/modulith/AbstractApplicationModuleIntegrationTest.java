package cafe.woden.ircclient.modulith;

import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiEventPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.diagnostics.JfrSnapshotSummarizer;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.backend.BackendRoutingIrcClientService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.port.IrcShutdownPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.logging.history.ChatHistoryTranscriptPort;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Answers;
import org.springframework.boot.ApplicationRunner;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared baseline for Spring Modulith module integration tests.
 *
 * <p>These defaults keep tests deterministic and isolate runtime files under {@code build/tmp}.
 */
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.ui.appDiagnostics.assertjSwing.enabled=false",
      "ircafe.ui.appDiagnostics.assertjSwing.edtFreezeWatchdogEnabled=false",
      "ircafe.ui.appDiagnostics.jhiccup.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
public abstract class AbstractApplicationModuleIntegrationTest {

  @MockitoBean(name = "ircClientService", answers = Answers.RETURNS_DEEP_STUBS)
  BackendRoutingIrcClientService ircClientService;

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

  @TestBean BouncerBackendRegistry bouncerBackendRegistry;

  @SuppressWarnings("unused")
  static BouncerBackendRegistry bouncerBackendRegistry() {
    return new BouncerBackendRegistry(List.of());
  }

  @BeforeEach
  void resetIrcClientServiceDefaults() {
    when(ircClientService.events()).thenReturn(Flowable.empty());
    when(ircClientService.quasselCoreNetworkEvents()).thenReturn(Flowable.empty());
    when(ircClientService.currentNick(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Optional.empty());
    when(ircClientService.backendAvailabilityReason(org.mockito.ArgumentMatchers.anyString()))
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

  @MockitoBean(name = "ircShutdownPort")
  IrcShutdownPort ircShutdownPort;

  @MockitoBean(name = "ircCurrentNickPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcCurrentNickPort ircCurrentNickPort;

  @MockitoBean(name = "ircTargetMembershipPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcTargetMembershipPort ircTargetMembershipPort;

  @MockitoBean(name = "ircMediatorInteractionPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcMediatorInteractionPort ircMediatorInteractionPort;

  @MockitoBean(name = "ircConnectionLifecyclePort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcConnectionLifecyclePort ircConnectionLifecyclePort;

  @MockitoBean(name = "ircNegotiatedFeaturePort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcNegotiatedFeaturePort ircNegotiatedFeaturePort;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  IrcReadMarkerPort ircReadMarkerPort;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  IrcTypingPort ircTypingPort;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  IrcEchoCapabilityPort ircEchoCapabilityPort;

  @MockitoBean ChatHistoryTranscriptPort chatHistoryTranscriptPort;

  @MockitoBean(name = "swingUiPort", answers = Answers.RETURNS_DEEP_STUBS)
  UiPort swingUiPort;

  @MockitoBean(name = "swingUiEventPort", answers = Answers.RETURNS_DEEP_STUBS)
  UiEventPort swingUiEventPort;

  @MockitoBean UiSettingsPort uiSettingsPort;

  @MockitoBean ChatTranscriptHistoryPort chatTranscriptHistoryPort;

  @MockitoBean TrayNotificationsPort trayNotificationsPort;

  @MockitoBean UserListStore userListStore;

  @MockitoBean UserhostQueryService userhostQueryService;

  @MockitoBean UserInfoEnrichmentService userInfoEnrichmentService;

  @MockitoBean IgnoreListQueryPort ignoreListQueryPort;

  @MockitoBean IgnoreListCommandPort ignoreListCommandPort;

  @MockitoBean LocalFilterCommandHandler localFilterCommandHandler;

  @MockitoBean InboundIgnorePolicyPort inboundIgnorePolicy;

  @MockitoBean JfrSnapshotSummarizer jfrSnapshotSummarizer;

  @MockitoBean MonitorRosterPort monitorRosterPort;

  @MockitoBean MonitorFallbackPort monitorFallbackPort;

  @MockitoBean InterceptorIngestPort interceptorIngestPort;

  @MockitoBean IrcEventNotifierPort ircEventNotifierPort;

  @MockitoBean NotificationRuleMatcherPort notificationRuleMatcherPort;

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }
}
