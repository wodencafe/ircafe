package cafe.woden.ircclient.ui.settings;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.table.TableColumn;

final class NotificationRulesControlsSupport {
  private NotificationRulesControlsSupport() {}

  static NotificationRulesControls buildControls(
      UiSettings current,
      List<AutoCloseable> closeables,
      ExecutorService notificationRuleTestExecutor) {
    int cooldown = current != null ? current.notificationRuleCooldownSeconds() : 15;
    javax.swing.JSpinner cooldownSeconds =
        PreferencesDialog.numberSpinner(cooldown, 0, 3600, 1, closeables);

    NotificationRulesTableModel model =
        new NotificationRulesTableModel(current != null ? current.notificationRules() : List.of());
    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(Math.max(22, table.getRowHeight()));
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.setDefaultEditor(Object.class, null);
    table.setDefaultEditor(Boolean.class, null);
    table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);

    TableColumn enabledCol =
        table.getColumnModel().getColumn(NotificationRulesTableModel.COL_ENABLED);
    enabledCol.setMaxWidth(80);
    enabledCol.setPreferredWidth(70);

    TableColumn labelCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_LABEL);
    labelCol.setPreferredWidth(190);

    TableColumn matchCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_MATCH);
    matchCol.setPreferredWidth(380);

    TableColumn optionsCol =
        table.getColumnModel().getColumn(NotificationRulesTableModel.COL_OPTIONS);
    optionsCol.setMaxWidth(220);
    optionsCol.setPreferredWidth(190);

    TableColumn colorCol = table.getColumnModel().getColumn(NotificationRulesTableModel.COL_COLOR);
    colorCol.setMaxWidth(130);
    colorCol.setPreferredWidth(110);
    colorCol.setCellRenderer(new RuleColorCellRenderer());

    JLabel validationLabel = new JLabel();
    validationLabel.setVisible(false);
    Color err = errorForeground();
    if (err != null) validationLabel.setForeground(err);

    JTextArea testInput = new JTextArea(4, 40);
    testInput.setLineWrap(true);
    testInput.setWrapStyleWord(true);

    JTextArea testOutput = new JTextArea(6, 40);
    testOutput.setEditable(false);
    testOutput.setLineWrap(true);
    testOutput.setWrapStyleWord(true);

    JLabel testStatus = new JLabel(" ");
    NotificationRuleTestRunner testRunner =
        new NotificationRuleTestRunner(notificationRuleTestExecutor);
    closeables.add(testRunner);

    return new NotificationRulesControls(
        cooldownSeconds,
        table,
        model,
        validationLabel,
        testInput,
        testOutput,
        testStatus,
        testRunner);
  }

  private static Color errorForeground() {
    Color color = UIManager.getColor("Label.errorForeground");
    if (color != null) return color;
    color = UIManager.getColor("Component.errorColor");
    if (color != null) return color;
    color = UIManager.getColor("Component.error.outlineColor");
    if (color != null) return color;
    color = UIManager.getColor("Component.error.borderColor");
    if (color != null) return color;
    color = UIManager.getColor("Component.error.focusedBorderColor");
    if (color != null) return color;
    return new Color(180, 0, 0);
  }
}
