package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

final class DiagnosticsControlsSupport {
  private DiagnosticsControlsSupport() {}

  static DiagnosticsControls buildControls(RuntimeConfigStore runtimeConfig) {
    JCheckBox assertjSwingEnabled = new JCheckBox("Enable AssertJ Swing diagnostics");
    assertjSwingEnabled.setSelected(runtimeConfig.readAppDiagnosticsAssertjSwingEnabled(true));
    assertjSwingEnabled.setToolTipText(
        "Installs AssertJ Swing (or a fallback detector) for EDT thread violation checks.");

    JCheckBox assertjSwingFreezeWatchdogEnabled = new JCheckBox("Enable EDT freeze watchdog");
    assertjSwingFreezeWatchdogEnabled.setSelected(
        runtimeConfig.readAppDiagnosticsAssertjSwingFreezeWatchdogEnabled(true));
    assertjSwingFreezeWatchdogEnabled.setToolTipText(
        "Reports prolonged Event Dispatch Thread stalls into Application -> AssertJ Swing.");

    int freezeThresholdMs = runtimeConfig.readAppDiagnosticsAssertjSwingFreezeThresholdMs(2500);
    JSpinner assertjSwingFreezeThresholdMs =
        new JSpinner(new SpinnerNumberModel(freezeThresholdMs, 500, 120_000, 100));

    int watchdogPollMs = runtimeConfig.readAppDiagnosticsAssertjSwingWatchdogPollMs(500);
    JSpinner assertjSwingWatchdogPollMs =
        new JSpinner(new SpinnerNumberModel(watchdogPollMs, 100, 10_000, 100));

    int fallbackViolationReportMs =
        runtimeConfig.readAppDiagnosticsAssertjSwingFallbackViolationReportMs(5000);
    JSpinner assertjSwingFallbackViolationReportMs =
        new JSpinner(new SpinnerNumberModel(fallbackViolationReportMs, 250, 120_000, 250));

    JCheckBox assertjSwingOnIssuePlaySound = new JCheckBox("Play sound when an issue is detected");
    assertjSwingOnIssuePlaySound.setSelected(
        runtimeConfig.readAppDiagnosticsAssertjSwingIssuePlaySound(false));
    assertjSwingOnIssuePlaySound.setToolTipText(
        "Uses the configured tray notification sound when EDT freeze/violation issues are detected.");

    JCheckBox assertjSwingOnIssueShowNotification =
        new JCheckBox("Show desktop notification when an issue is detected");
    assertjSwingOnIssueShowNotification.setSelected(
        runtimeConfig.readAppDiagnosticsAssertjSwingIssueShowNotification(false));
    assertjSwingOnIssueShowNotification.setToolTipText(
        "Uses the tray notification pipeline; desktop-notification delivery still follows tray settings.");

    JCheckBox jhiccupEnabled = new JCheckBox("Enable jHiccup process integration");
    jhiccupEnabled.setSelected(runtimeConfig.readAppDiagnosticsJhiccupEnabled(false));
    jhiccupEnabled.setToolTipText(
        "Runs an external jHiccup process and mirrors output into Application -> jHiccup.");

    JTextField jhiccupJarPath = new JTextField(runtimeConfig.readAppDiagnosticsJhiccupJarPath(""));
    jhiccupJarPath.setToolTipText(
        "Path to jHiccup jar file. Relative paths are resolved from the runtime-config directory.");

    JTextField jhiccupJavaCommand =
        new JTextField(runtimeConfig.readAppDiagnosticsJhiccupJavaCommand("java"));
    jhiccupJavaCommand.setToolTipText("Java launcher command used to start jHiccup.");

    JTextArea jhiccupArgs = new JTextArea(5, 40);
    jhiccupArgs.setLineWrap(false);
    jhiccupArgs.setWrapStyleWord(false);
    jhiccupArgs.setText(String.join("\n", runtimeConfig.readAppDiagnosticsJhiccupArgs(List.of())));
    jhiccupArgs.setToolTipText("One argument per line.");

    Runnable syncEnabledState =
        () -> {
          boolean assertjEnabled = assertjSwingEnabled.isSelected();
          assertjSwingFreezeWatchdogEnabled.setEnabled(assertjEnabled);
          boolean watchdogEnabled =
              assertjEnabled && assertjSwingFreezeWatchdogEnabled.isSelected();
          assertjSwingFreezeThresholdMs.setEnabled(watchdogEnabled);
          assertjSwingWatchdogPollMs.setEnabled(watchdogEnabled);
          assertjSwingFallbackViolationReportMs.setEnabled(assertjEnabled);
          assertjSwingOnIssuePlaySound.setEnabled(assertjEnabled);
          assertjSwingOnIssueShowNotification.setEnabled(assertjEnabled);
        };
    assertjSwingEnabled.addActionListener(e -> syncEnabledState.run());
    assertjSwingFreezeWatchdogEnabled.addActionListener(e -> syncEnabledState.run());
    syncEnabledState.run();

    return new DiagnosticsControls(
        assertjSwingEnabled,
        assertjSwingFreezeWatchdogEnabled,
        assertjSwingFreezeThresholdMs,
        assertjSwingWatchdogPollMs,
        assertjSwingFallbackViolationReportMs,
        assertjSwingOnIssuePlaySound,
        assertjSwingOnIssueShowNotification,
        jhiccupEnabled,
        jhiccupJarPath,
        jhiccupJavaCommand,
        jhiccupArgs);
  }
}
