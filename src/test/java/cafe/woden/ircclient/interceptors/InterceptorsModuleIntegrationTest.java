package cafe.woden.ircclient.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.InterceptorRule;
import cafe.woden.ircclient.model.InterceptorRuleMode;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import java.util.List;
import org.junit.jupiter.api.Test;
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
class InterceptorsModuleIntegrationTest {

  @MockitoBean TrayNotificationsPort trayNotificationsPort;
  @MockitoBean NotificationSoundService notificationSoundService;

  private final ApplicationContext applicationContext;
  private final InterceptorStore interceptorStore;
  private final InterceptorIngestPort interceptorIngestPort;

  InterceptorsModuleIntegrationTest(
      ApplicationContext applicationContext,
      InterceptorStore interceptorStore,
      InterceptorIngestPort interceptorIngestPort) {
    this.applicationContext = applicationContext;
    this.interceptorStore = interceptorStore;
    this.interceptorIngestPort = interceptorIngestPort;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesInterceptorModuleBeans() {
    assertEquals(1, applicationContext.getBeansOfType(InterceptorStore.class).size());
    assertEquals(1, applicationContext.getBeansOfType(InterceptorIngestPort.class).size());
    assertNotNull(interceptorStore);
    assertSame(interceptorStore, interceptorIngestPort);
  }

  @Test
  void interceptorDefinitionCanCaptureMatchingHits() throws Exception {
    String serverId = "libera";
    interceptorStore.clearServer(serverId);

    InterceptorDefinition created = interceptorStore.createInterceptor(serverId, "Ping watcher");
    InterceptorRule rule =
        new InterceptorRule(
            true,
            "Ping rule",
            "message",
            InterceptorRuleMode.LIKE,
            "ping",
            InterceptorRuleMode.LIKE,
            "",
            InterceptorRuleMode.GLOB,
            "");
    InterceptorDefinition updated =
        new InterceptorDefinition(
            created.id(),
            created.name(),
            true,
            serverId,
            InterceptorRuleMode.ALL,
            "",
            InterceptorRuleMode.NONE,
            "",
            false,
            false,
            false,
            "NOTIF_1",
            false,
            "",
            false,
            "",
            "",
            "",
            List.of(rule));
    assertTrue(interceptorStore.saveInterceptor(serverId, updated));

    interceptorStore.ingestEvent(
        serverId,
        "#general",
        "alice",
        "alice!ident@host.example",
        "ping from alice",
        InterceptorEventType.MESSAGE);

    List<InterceptorHit> hits = waitForHits(serverId, created.id(), 1);
    assertEquals(1, hits.size());
    assertEquals("Ping rule", hits.getFirst().reason());
    assertEquals("message", hits.getFirst().eventType());
    assertEquals("#general", hits.getFirst().channel());
  }

  @Test
  void interceptorIngestPortFeedsMatchingPipeline() throws Exception {
    String serverId = "libera";
    interceptorStore.clearServer(serverId);

    InterceptorDefinition created = interceptorStore.createInterceptor(serverId, "Port watcher");
    InterceptorRule rule =
        new InterceptorRule(
            true,
            "Port rule",
            "notice",
            InterceptorRuleMode.LIKE,
            "alert",
            InterceptorRuleMode.LIKE,
            "",
            InterceptorRuleMode.GLOB,
            "");
    InterceptorDefinition updated =
        new InterceptorDefinition(
            created.id(),
            created.name(),
            true,
            serverId,
            InterceptorRuleMode.ALL,
            "",
            InterceptorRuleMode.NONE,
            "",
            false,
            false,
            false,
            "NOTIF_1",
            false,
            "",
            false,
            "",
            "",
            "",
            List.of(rule));
    assertTrue(interceptorStore.saveInterceptor(serverId, updated));

    interceptorIngestPort.ingestEvent(
        serverId,
        "#alerts",
        "carol",
        "carol!ident@host.example",
        "alert notice",
        InterceptorEventType.NOTICE);

    List<InterceptorHit> hits = waitForHits(serverId, created.id(), 1);
    assertEquals(1, hits.size());
    assertEquals("Port rule", hits.getFirst().reason());
    assertEquals("notice", hits.getFirst().eventType());
    assertEquals("#alerts", hits.getFirst().channel());
  }

  private List<InterceptorHit> waitForHits(String serverId, String interceptorId, int atLeast)
      throws InterruptedException {
    for (int i = 0; i < 30; i++) {
      List<InterceptorHit> hits = interceptorStore.listHits(serverId, interceptorId, 100);
      if (hits.size() >= atLeast) {
        return hits;
      }
      Thread.sleep(40L);
    }
    return interceptorStore.listHits(serverId, interceptorId, 100);
  }
}
