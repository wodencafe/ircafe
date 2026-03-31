package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreAppDiagnosticsSettingsTest {

  @TempDir Path tempDir;

  @Test
  void assertjSwingSettingsRoundTripAndClamp() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertTrue(store.readAppDiagnosticsAssertjSwingEnabled(true));
    assertFalse(store.readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(false));

    store.rememberAppDiagnosticsAssertjSwingEnabled(false);
    store.rememberAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(true);
    store.rememberAppDiagnosticsAssertjSwingFreezeThresholdMs(100);
    store.rememberAppDiagnosticsAssertjSwingWatchdogPollMs(50);
    store.rememberAppDiagnosticsAssertjSwingFallbackViolationReportMs(100);
    store.rememberAppDiagnosticsAssertjSwingIssuePlaySound(true);
    store.rememberAppDiagnosticsAssertjSwingIssueShowNotification(false);

    assertFalse(store.readAppDiagnosticsAssertjSwingEnabled(true));
    assertTrue(store.readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(false));
    assertEquals(500, store.readAppDiagnosticsAssertjSwingFreezeThresholdMs(10_000));
    assertEquals(100, store.readAppDiagnosticsAssertjSwingWatchdogPollMs(5_000));
    assertEquals(250, store.readAppDiagnosticsAssertjSwingFallbackViolationReportMs(5_000));
    assertTrue(store.readAppDiagnosticsAssertjSwingIssuePlaySound(false));
    assertFalse(store.readAppDiagnosticsAssertjSwingIssueShowNotification(true));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("appDiagnostics"));
    assertTrue(yaml.contains("assertjSwing"));
    assertTrue(yaml.contains("edtFreezeThresholdMs: 500"));
    assertTrue(yaml.contains("edtWatchdogPollMs: 100"));
    assertTrue(yaml.contains("edtFallbackViolationReportMs: 250"));
  }

  @Test
  void jhiccupSettingsRoundTripAndFallback() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertFalse(store.readAppDiagnosticsJhiccupEnabled(false));
    assertEquals("", store.readAppDiagnosticsJhiccupJarPath(""));
    assertEquals("java", store.readAppDiagnosticsJhiccupJavaCommand(""));
    assertEquals(List.of("--fallback"), store.readAppDiagnosticsJhiccupArgs(List.of("--fallback")));

    store.rememberAppDiagnosticsJhiccupEnabled(true);
    store.rememberAppDiagnosticsJhiccupJarPath("  tools/jhiccup.jar  ");
    store.rememberAppDiagnosticsJhiccupJavaCommand("  java21  ");
    store.rememberAppDiagnosticsJhiccupArgs(List.of("  -Xmx64m  ", "", "  -Dsample=true  "));

    assertTrue(store.readAppDiagnosticsJhiccupEnabled(false));
    assertEquals("tools/jhiccup.jar", store.readAppDiagnosticsJhiccupJarPath(""));
    assertEquals("java21", store.readAppDiagnosticsJhiccupJavaCommand("java"));
    assertEquals(
        List.of("-Xmx64m", "-Dsample=true"),
        store.readAppDiagnosticsJhiccupArgs(List.of("--fallback")));

    store.rememberAppDiagnosticsJhiccupJarPath("   ");
    store.rememberAppDiagnosticsJhiccupJavaCommand("   ");
    store.rememberAppDiagnosticsJhiccupArgs(List.of());

    assertEquals("", store.readAppDiagnosticsJhiccupJarPath(""));
    assertEquals("java", store.readAppDiagnosticsJhiccupJavaCommand(""));
    assertEquals(List.of("--fallback"), store.readAppDiagnosticsJhiccupArgs(List.of("--fallback")));
  }
}
