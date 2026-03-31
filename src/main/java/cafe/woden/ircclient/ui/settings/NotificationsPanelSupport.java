package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.NotificationRule;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import net.miginfocom.swing.MigLayout;

final class NotificationsPanelSupport {
  private NotificationsPanelSupport() {}

  static JPanel buildPanel(
      NotificationRulesControls notifications,
      JPanel ircEventTab,
      Component owner,
      NotificationRuleEditor notificationRuleEditor,
      ValidationRefresher validationRefresher) {
    JPanel panel =
        new JPanel(new MigLayout("insets 10, fill, wrap 1", "[grow,fill]", "[]8[]4[grow,fill]"));

    panel.add(PreferencesDialog.tabTitle("Notifications"), "growx, wmin 0, wrap");
    panel.add(PreferencesDialog.sectionTitle("Rule matches"), "growx, wmin 0, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Add custom word/regex rules to create notifications when messages match.\n"
                + "Rules only trigger for channels (not PMs), including the active channel."),
        "growx, wmin 0, wrap");

    JButton add = new JButton("Add");
    JButton edit = new JButton("Edit");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    PreferencesDialog.configureIconOnlyButton(add, "plus", "Add notification rule");
    PreferencesDialog.configureIconOnlyButton(edit, "edit", "Edit selected notification rule");
    PreferencesDialog.configureIconOnlyButton(
        duplicate, "copy", "Duplicate selected notification rule");
    PreferencesDialog.configureIconOnlyButton(remove, "trash", "Remove selected notification rule");
    PreferencesDialog.configureIconOnlyButton(up, "arrow-up", "Move selected notification rule up");
    PreferencesDialog.configureIconOnlyButton(
        down, "arrow-down", "Move selected notification rule down");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(add);
    buttons.add(edit);
    buttons.add(duplicate);
    buttons.add(remove);
    buttons.add(up);
    buttons.add(down);

    Runnable refreshRuleButtons =
        () -> {
          int viewRow = notifications.table.getSelectedRow();
          boolean hasSelection = viewRow >= 0;
          int modelRow = hasSelection ? notifications.table.convertRowIndexToModel(viewRow) : -1;
          edit.setEnabled(hasSelection);
          duplicate.setEnabled(hasSelection);
          remove.setEnabled(hasSelection);
          up.setEnabled(hasSelection && modelRow > 0);
          down.setEnabled(
              hasSelection && modelRow >= 0 && modelRow < (notifications.model.getRowCount() - 1));
        };

    Runnable openEditRuleDialog =
        () -> {
          int modelRow = selectedModelRow(notifications);
          if (modelRow < 0) return;
          NotificationRule seed = notifications.model.ruleAt(modelRow);
          if (seed == null) return;
          NotificationRule edited = notificationRuleEditor.prompt("Edit Notification Rule", seed);
          if (edited == null) return;
          notifications.model.setRule(modelRow, edited);
          selectModelRow(notifications, modelRow);
          refreshRuleButtons.run();
        };

    add.addActionListener(
        e -> {
          NotificationRule created = notificationRuleEditor.prompt("Add Notification Rule", null);
          if (created == null) return;
          int row = notifications.model.addRule(created);
          selectModelRow(notifications, row);
          refreshRuleButtons.run();
        });

    edit.addActionListener(e -> openEditRuleDialog.run());

    duplicate.addActionListener(
        e -> {
          int modelRow = selectedModelRow(notifications);
          if (modelRow < 0) return;
          int dup = notifications.model.duplicateRow(modelRow);
          selectModelRow(notifications, dup);
          refreshRuleButtons.run();
        });

    remove.addActionListener(
        e -> {
          int modelRow = selectedModelRow(notifications);
          if (modelRow < 0) return;
          NotificationRule rule = notifications.model.ruleAt(modelRow);
          String label = NotificationRulesTableModel.effectiveRuleLabel(rule);
          int res =
              JOptionPane.showConfirmDialog(
                  owner,
                  "Remove notification rule \"" + label + "\"?",
                  "Remove Notification Rule",
                  JOptionPane.OK_CANCEL_OPTION);
          if (res != JOptionPane.OK_OPTION) return;
          notifications.model.removeRow(modelRow);
          int nextModelRow = Math.min(modelRow, notifications.model.getRowCount() - 1);
          if (nextModelRow >= 0) {
            selectModelRow(notifications, nextModelRow);
          } else {
            notifications.table.clearSelection();
          }
          refreshRuleButtons.run();
        });

    up.addActionListener(
        e -> {
          int modelRow = selectedModelRow(notifications);
          if (modelRow < 0) return;
          int next = notifications.model.moveRow(modelRow, modelRow - 1);
          selectModelRow(notifications, next);
          refreshRuleButtons.run();
        });

    down.addActionListener(
        e -> {
          int modelRow = selectedModelRow(notifications);
          if (modelRow < 0) return;
          int next = notifications.model.moveRow(modelRow, modelRow + 1);
          selectModelRow(notifications, next);
          refreshRuleButtons.run();
        });

    notifications
        .table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e != null && e.getValueIsAdjusting()) return;
              refreshRuleButtons.run();
            });

    notifications.table.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e == null) return;
            if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
            if (e.getClickCount() != 2) return;
            int viewRow = notifications.table.rowAtPoint(e.getPoint());
            if (viewRow < 0) return;
            notifications.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            openEditRuleDialog.run();
          }
        });
    refreshRuleButtons.run();

    JScrollPane scroll = new JScrollPane(notifications.table);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JScrollPane testInScroll = new JScrollPane(notifications.testInput);
    testInScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    testInScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JScrollPane testOutScroll = new JScrollPane(notifications.testOutput);
    testOutScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    testOutScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JButton runTest = new JButton("Test");
    JButton clearTest = new JButton("Clear");
    PreferencesDialog.configureIconOnlyButton(
        runTest, "check", "Test sample message against notification rules");
    PreferencesDialog.configureIconOnlyButton(clearTest, "close", "Clear rule test input/output");

    JPanel testButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    testButtons.add(runTest);
    testButtons.add(clearTest);
    testButtons.add(notifications.testStatus);

    runTest.addActionListener(
        e -> {
          stopEditing(notifications.table);
          validationRefresher.refresh(notifications);
          notifications.testRunner.runTest(notifications);
        });

    clearTest.addActionListener(
        e -> {
          notifications.testInput.setText("");
          notifications.testOutput.setText("");
          notifications.testStatus.setText(" ");
        });

    JPanel rulesTab = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]8[]"));
    rulesTab.setOpaque(false);
    JPanel rulesBehaviorPanel =
        PreferencesDialog.captionPanel(
            "Rule behavior", "insets 0, fillx, wrap 2", "[right]10[grow,fill]", "[]");
    rulesBehaviorPanel.add(new JLabel("Cooldown (sec)"));
    rulesBehaviorPanel.add(notifications.cooldownSeconds, "w 110!, wrap");
    rulesTab.add(rulesBehaviorPanel, "growx, wmin 0, wrap");

    JPanel rulesTablePanel =
        PreferencesDialog.captionPanel(
            "Rule list", "insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]4[]4[]");
    rulesTablePanel.add(buttons, "growx, wmin 0, wrap");
    rulesTablePanel.add(scroll, "grow, push, h 260!, wmin 0, wrap");
    rulesTablePanel.add(notifications.validationLabel, "growx, wmin 0, wrap");
    rulesTablePanel.add(
        PreferencesDialog.helpText("Tip: Double-click a rule to edit it."), "growx, wmin 0, wrap");
    rulesTab.add(rulesTablePanel, "grow, push, wmin 0");

    JPanel testTab = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]"));
    testTab.setOpaque(false);
    JPanel testRunnerPanel =
        PreferencesDialog.captionPanel(
            "Message test", "insets 0, fill, wrap 2", "[right]10[grow,fill]", "[]6[]4[]4[]");
    testRunnerPanel.add(
        PreferencesDialog.helpText(
            "Paste a sample message to see which rules match. This is just a preview; it won't create real notifications."),
        "span 2, growx, wmin 0, wrap");
    testRunnerPanel.add(new JLabel("Sample"), "aligny top");
    testRunnerPanel.add(testInScroll, "growx, h 100!, wrap");
    testRunnerPanel.add(new JLabel("Matches"), "aligny top");
    testRunnerPanel.add(testOutScroll, "growx, h 160!, wrap");
    testRunnerPanel.add(new JLabel(""));
    testRunnerPanel.add(testButtons, "growx, wrap");
    testTab.add(testRunnerPanel, "grow, push, wmin 0");

    JTabbedPane subTabs = new JTabbedPane();
    Icon rulesTabIcon = SvgIcons.action("edit", 14);
    Icon testTabIcon = SvgIcons.action("check", 14);
    subTabs.addTab(
        "Rules",
        rulesTabIcon,
        PreferencesDialog.padSubTab(rulesTab),
        "Manage notification matching rules");
    subTabs.addTab(
        "Test",
        testTabIcon,
        PreferencesDialog.padSubTab(testTab),
        "Try a sample message against your rules");
    subTabs.addTab(
        "IRC Events",
        null,
        PreferencesDialog.padSubTab(ircEventTab),
        "Configure notifications for IRC events like kick/ban/invite/mode updates");

    panel.add(subTabs, "grow, push, wmin 0");

    validationRefresher.refresh(notifications);
    return panel;
  }

  private static int selectedModelRow(NotificationRulesControls notifications) {
    int viewRow = notifications.table.getSelectedRow();
    return viewRow >= 0 ? notifications.table.convertRowIndexToModel(viewRow) : -1;
  }

  private static void selectModelRow(NotificationRulesControls notifications, int modelRow) {
    if (modelRow < 0) return;
    int viewRow = notifications.table.convertRowIndexToView(modelRow);
    if (viewRow >= 0) {
      notifications.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
      notifications.table.scrollRectToVisible(notifications.table.getCellRect(viewRow, 0, true));
    }
  }

  private static void stopEditing(javax.swing.JTable table) {
    if (table == null || !table.isEditing()) return;
    try {
      table.getCellEditor().stopCellEditing();
    } catch (Exception ignored) {
    }
  }

  @FunctionalInterface
  interface NotificationRuleEditor {
    NotificationRule prompt(String title, NotificationRule seed);
  }

  @FunctionalInterface
  interface ValidationRefresher {
    boolean refresh(NotificationRulesControls notifications);
  }
}
