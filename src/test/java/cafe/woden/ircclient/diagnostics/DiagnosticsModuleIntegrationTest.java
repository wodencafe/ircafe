package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class DiagnosticsModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final ApplicationDiagnosticsService diagnosticsService;
  private final RuntimeJfrService runtimeJfrService;
  private final JfrRuntimeEventsService jfrRuntimeEventsService;
  private final SpringRuntimeEventsService springRuntimeEventsService;
  private final UiPort swingUiPort;

  DiagnosticsModuleIntegrationTest(
      ApplicationContext applicationContext,
      ApplicationDiagnosticsService diagnosticsService,
      RuntimeJfrService runtimeJfrService,
      JfrRuntimeEventsService jfrRuntimeEventsService,
      SpringRuntimeEventsService springRuntimeEventsService,
      @Qualifier("swingUiPort") UiPort swingUiPort) {
    this.applicationContext = applicationContext;
    this.diagnosticsService = diagnosticsService;
    this.runtimeJfrService = runtimeJfrService;
    this.jfrRuntimeEventsService = jfrRuntimeEventsService;
    this.springRuntimeEventsService = springRuntimeEventsService;
    this.swingUiPort = swingUiPort;
  }

  @Test
  void exposesDiagnosticsModuleEntryPointBeans() {
    assertEquals(1, applicationContext.getBeansOfType(ApplicationDiagnosticsService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(RuntimeJfrService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(JfrRuntimeEventsService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(SpringRuntimeEventsService.class).size());
    assertNotNull(diagnosticsService);
    assertNotNull(runtimeJfrService);
    assertNotNull(jfrRuntimeEventsService);
    assertNotNull(springRuntimeEventsService);
    assertTrue(!runtimeJfrService.statusReport().isBlank());
  }

  @Test
  void applicationDiagnosticsRoutesMessagesToUiPort() {
    TargetRef assertjTarget = TargetRef.applicationAssertjSwing();
    clearInvocations(swingUiPort);

    diagnosticsService.appendAssertjSwingStatus("watchdog ready");
    diagnosticsService.appendAssertjSwingError("freeze detected");

    verify(swingUiPort, atLeastOnce()).ensureTargetExists(assertjTarget);
    verify(swingUiPort)
        .appendStatus(eq(assertjTarget), eq("(assertj-swing)"), eq("watchdog ready"));
    verify(swingUiPort)
        .appendError(eq(assertjTarget), eq("(assertj-swing)"), eq("freeze detected"));
  }

  @Test
  void publishedSpringEventsAppearInRuntimeEventFeed() {
    applicationContext.publishEvent(
        new org.springframework.context.PayloadApplicationEvent<>(
            this, new IllegalStateException("module-event-boom")));

    assertTrue(
        springRuntimeEventsService.recentEvents(30).stream()
            .anyMatch(
                event ->
                    "ERROR".equals(event.level())
                        && event.summary().contains("IllegalStateException")));
  }
}
