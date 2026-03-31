package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;

record DiagnosticsControls(
    JCheckBox assertjSwingEnabled,
    JCheckBox assertjSwingFreezeWatchdogEnabled,
    JSpinner assertjSwingFreezeThresholdMs,
    JSpinner assertjSwingWatchdogPollMs,
    JSpinner assertjSwingFallbackViolationReportMs,
    JCheckBox assertjSwingOnIssuePlaySound,
    JCheckBox assertjSwingOnIssueShowNotification,
    JCheckBox jhiccupEnabled,
    JTextField jhiccupJarPath,
    JTextField jhiccupJavaCommand,
    JTextArea jhiccupArgs) {}
