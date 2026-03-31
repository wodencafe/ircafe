package cafe.woden.ircclient.ui.settings;

import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;

final class NotificationRulesControls {
  final JSpinner cooldownSeconds;
  final JTable table;
  final NotificationRulesTableModel model;
  final JLabel validationLabel;
  final JTextArea testInput;
  final JTextArea testOutput;
  final JLabel testStatus;
  final NotificationRuleTestRunner testRunner;

  NotificationRulesControls(
      JSpinner cooldownSeconds,
      JTable table,
      NotificationRulesTableModel model,
      JLabel validationLabel,
      JTextArea testInput,
      JTextArea testOutput,
      JLabel testStatus,
      NotificationRuleTestRunner testRunner) {
    this.cooldownSeconds = cooldownSeconds;
    this.table = table;
    this.model = model;
    this.validationLabel = validationLabel;
    this.testInput = testInput;
    this.testOutput = testOutput;
    this.testStatus = testStatus;
    this.testRunner = testRunner;
  }
}
