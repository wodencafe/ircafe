package cafe.woden.ircclient.diagnostics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JhiccupDiagnosticsServiceTest {

  @Test
  void startReportsDisabledWhenFeatureOff() {
    ApplicationDiagnosticsService diagnostics = mock(ApplicationDiagnosticsService.class);
    UiProperties uiProps = mock(UiProperties.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(uiProps.appDiagnostics())
        .thenReturn(
            new UiProperties.AppDiagnostics(
                null, new UiProperties.Jhiccup(false, null, null, null)));

    JhiccupDiagnosticsService service =
        new JhiccupDiagnosticsService(diagnostics, uiProps, runtimeConfig);

    service.start();

    verify(diagnostics).appendJhiccupStatus("Disabled by configuration.");
    verifyNoMoreInteractions(diagnostics);
  }

  @Test
  void startReportsMissingJarPathWhenEnabledWithoutPath() {
    ApplicationDiagnosticsService diagnostics = mock(ApplicationDiagnosticsService.class);
    UiProperties uiProps = mock(UiProperties.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(uiProps.appDiagnostics())
        .thenReturn(
            new UiProperties.AppDiagnostics(
                null, new UiProperties.Jhiccup(true, null, "java", null)));

    JhiccupDiagnosticsService service =
        new JhiccupDiagnosticsService(diagnostics, uiProps, runtimeConfig);

    service.start();

    verify(diagnostics)
        .appendJhiccupError(
            "Enabled but no jarPath configured. Set ircafe.ui.appDiagnostics.jhiccup.jarPath.");
    verifyNoMoreInteractions(diagnostics);
  }

  @Test
  void startReportsMissingJarFile() {
    ApplicationDiagnosticsService diagnostics = mock(ApplicationDiagnosticsService.class);
    UiProperties uiProps = mock(UiProperties.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(runtimeConfig.runtimeConfigPath()).thenReturn(Path.of("/tmp/ircafe/runtime.yml"));
    when(uiProps.appDiagnostics())
        .thenReturn(
            new UiProperties.AppDiagnostics(
                null,
                new UiProperties.Jhiccup(
                    true, "missing-jhiccup.jar", "java", java.util.List.of("-v"))));

    JhiccupDiagnosticsService service =
        new JhiccupDiagnosticsService(diagnostics, uiProps, runtimeConfig);

    service.start();

    verify(diagnostics).appendJhiccupError("jHiccup jar not found: missing-jhiccup.jar");
    verifyNoMoreInteractions(diagnostics);
  }
}
