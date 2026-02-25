package cafe.woden.ircclient.modulith;

import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.logging.history.ChatHistoryTranscriptPort;
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

  @MockitoBean IrcClientService ircClientService;

  @MockitoBean ChatHistoryTranscriptPort chatHistoryTranscriptPort;

  @MockitoBean(name = "swingUiPort")
  UiPort swingUiPort;

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }
}
