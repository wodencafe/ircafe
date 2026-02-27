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
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
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

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  IrcClientService ircClientService;

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
