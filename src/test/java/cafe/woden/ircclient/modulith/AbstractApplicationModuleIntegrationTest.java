package cafe.woden.ircclient.modulith;

import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.diagnostics.JfrSnapshotSummarizer;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.IrcCurrentNickPort;
import cafe.woden.ircclient.irc.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.IrcShutdownPort;
import cafe.woden.ircclient.irc.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.IrcTypingPort;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.irc.roster.UserhostQueryService;
import cafe.woden.ircclient.logging.history.ChatHistoryTranscriptPort;
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
  IrcBackendClientService ircClientService;

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
