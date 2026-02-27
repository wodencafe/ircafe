package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JfrRuntimeEventsServiceTest {

  @Test
  void startsWithPersistedEnabledState() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(runtimeConfig.readApplicationJfrEnabled(true)).thenReturn(false);

    JfrRuntimeEventsService service = new JfrRuntimeEventsService(runtimeConfig);

    assertFalse(service.isEnabled());
  }

  @Test
  void togglingEnabledPersistsPreference() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(runtimeConfig.readApplicationJfrEnabled(true)).thenReturn(true);
    JfrRuntimeEventsService service = new JfrRuntimeEventsService(runtimeConfig);

    service.setEnabled(false);
    assertFalse(service.isEnabled());
    verify(runtimeConfig).rememberApplicationJfrEnabled(false);

    service.setEnabled(true);
    assertTrue(service.isEnabled());
    verify(runtimeConfig).rememberApplicationJfrEnabled(true);
  }

  @Test
  void pauseFlagCanBeToggledIndependently() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(runtimeConfig.readApplicationJfrEnabled(true)).thenReturn(true);
    JfrRuntimeEventsService service = new JfrRuntimeEventsService(runtimeConfig);

    assertFalse(service.isTableLoggingPaused());
    service.setTableLoggingPaused(true);
    assertTrue(service.isTableLoggingPaused());
    service.setTableLoggingPaused(false);
    assertFalse(service.isTableLoggingPaused());
  }

  @Test
  void canExportLightweightMemoryDiagnosticsBundle() throws Exception {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    Path runtimePath = Files.createTempDirectory("ircafe-jfr-export-test").resolve("ircafe.yml");
    when(runtimeConfig.readApplicationJfrEnabled(true)).thenReturn(true);
    when(runtimeConfig.runtimeConfigPath()).thenReturn(runtimePath);

    JfrRuntimeEventsService service = new JfrRuntimeEventsService(runtimeConfig);
    JfrRuntimeEventsService.MemoryDiagnosticsExportReport report =
        service.captureMemoryDiagnosticsBundle(false);

    assertNotNull(report);
    assertTrue(report.success());
    assertNotNull(report.bundlePath());
    assertTrue(Files.exists(report.bundlePath()));
    assertTrue(report.summary().contains("Memory diagnostics bundle"));
  }
}
