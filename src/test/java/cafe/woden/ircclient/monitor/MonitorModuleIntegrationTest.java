package cafe.woden.ircclient.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.IrcClientService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class MonitorModuleIntegrationTest {

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  IrcClientService ircClientService;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  UiPort uiPort;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  UiSettingsPort uiSettingsPort;

  private final ApplicationContext applicationContext;
  private final RuntimeConfigStore runtimeConfigStore;
  private final MonitorListService monitorListService;
  private final MonitorIsonFallbackService monitorIsonFallbackService;
  private final MonitorSyncService monitorSyncService;
  private final MonitorRosterPort monitorRosterPort;
  private final MonitorFallbackPort monitorFallbackPort;

  MonitorModuleIntegrationTest(
      ApplicationContext applicationContext,
      RuntimeConfigStore runtimeConfigStore,
      MonitorListService monitorListService,
      MonitorIsonFallbackService monitorIsonFallbackService,
      MonitorSyncService monitorSyncService,
      MonitorRosterPort monitorRosterPort,
      MonitorFallbackPort monitorFallbackPort) {
    this.applicationContext = applicationContext;
    this.runtimeConfigStore = runtimeConfigStore;
    this.monitorListService = monitorListService;
    this.monitorIsonFallbackService = monitorIsonFallbackService;
    this.monitorSyncService = monitorSyncService;
    this.monitorRosterPort = monitorRosterPort;
    this.monitorFallbackPort = monitorFallbackPort;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesMonitorModuleBeans() {
    assertEquals(1, applicationContext.getBeansOfType(MonitorListService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(MonitorIsonFallbackService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(MonitorSyncService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(MonitorRosterPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(MonitorFallbackPort.class).size());
    assertNotNull(monitorListService);
    assertNotNull(monitorIsonFallbackService);
    assertNotNull(monitorSyncService);
    assertEquals(MonitorListService.class, AopUtils.getTargetClass(monitorRosterPort));
    assertEquals(MonitorIsonFallbackService.class, AopUtils.getTargetClass(monitorFallbackPort));
    assertSame(monitorListService, monitorRosterPort);
    assertSame(monitorIsonFallbackService, monitorFallbackPort);
  }

  @Test
  void monitorRosterMutationsPersistIntoRuntimeConfig() {
    String serverId = "libera";
    monitorListService.clearNicks(serverId);

    assertEquals(2, monitorListService.addNicks(serverId, List.of("Alice", "bob", "alice")));
    assertEquals(List.of("Alice", "bob"), monitorListService.listNicks(serverId));
    assertEquals(List.of("Alice", "bob"), runtimeConfigStore.readMonitorNicks(serverId));

    assertEquals(1, monitorListService.removeNicks(serverId, List.of("ALICE")));
    assertEquals(List.of("bob"), monitorListService.listNicks(serverId));
    assertEquals(List.of("bob"), runtimeConfigStore.readMonitorNicks(serverId));
  }

  @Test
  void monitorPortsExposeParsingAndFallbackDefaultsBeforeConnection() {
    assertEquals(
        List.of("alice", "bob", "carol"), monitorRosterPort.parseNickInput("alice,bob carol"));

    assertFalse(monitorFallbackPort.isFallbackActive("libera"));
    assertFalse(monitorFallbackPort.shouldSuppressIsonServerResponse("libera"));
  }
}
