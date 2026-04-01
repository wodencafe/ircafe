package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.api.DiagnosticsRuntimeConfigPort;
import cafe.woden.ircclient.util.InstalledPluginDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JfrRuntimeEventsServiceTest {

  @TempDir Path tempDir;

  @Test
  void startsWithPersistedEnabledState() {
    DiagnosticsRuntimeConfigPort runtimeConfig = mock(DiagnosticsRuntimeConfigPort.class);
    when(runtimeConfig.readApplicationJfrEnabled(true)).thenReturn(false);

    JfrRuntimeEventsService service = new JfrRuntimeEventsService(runtimeConfig);

    assertFalse(service.isEnabled());
  }

  @Test
  void togglingEnabledPersistsPreference() {
    DiagnosticsRuntimeConfigPort runtimeConfig = mock(DiagnosticsRuntimeConfigPort.class);
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
    DiagnosticsRuntimeConfigPort runtimeConfig = mock(DiagnosticsRuntimeConfigPort.class);
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
    DiagnosticsRuntimeConfigPort runtimeConfig = mock(DiagnosticsRuntimeConfigPort.class);
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

  @Test
  void memoryDiagnosticsBundleIncludesInstalledPluginSummary() throws Exception {
    DiagnosticsRuntimeConfigPort runtimeConfig = mock(DiagnosticsRuntimeConfigPort.class);
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path runtimePath = runtimeConfigDirectory.resolve("ircafe.yml");
    Path pluginJar = runtimeConfigDirectory.resolve("plugins").resolve("sample-plugin.jar");
    when(runtimeConfig.readApplicationJfrEnabled(true)).thenReturn(true);
    when(runtimeConfig.runtimeConfigPath()).thenReturn(runtimePath);

    JfrRuntimeEventsService service =
        new JfrRuntimeEventsService(
            runtimeConfig,
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
            runtimeConfigDirectory.resolve("plugins"),
            List.of(new InstalledPluginDescriptor("sample-plugin", "1.4.0", 1, pluginJar)));
    try {
      JfrRuntimeEventsService.MemoryDiagnosticsExportReport report =
          service.captureMemoryDiagnosticsBundle(false);

      assertNotNull(report.bundlePath());
      try (ZipFile zipFile = new ZipFile(report.bundlePath().toFile())) {
        String runtimeSummary =
            new String(
                zipFile.getInputStream(zipFile.getEntry("runtime-summary.txt")).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(runtimeSummary.contains("Plugins:"));
        assertTrue(runtimeSummary.contains("Installed: 1"));
        assertTrue(runtimeSummary.contains("sample-plugin v1.4.0 (api 1)"));
        assertTrue(runtimeSummary.contains(pluginJar.toAbsolutePath().toString()));
      }
    } finally {
      service.stop();
    }
  }
}
