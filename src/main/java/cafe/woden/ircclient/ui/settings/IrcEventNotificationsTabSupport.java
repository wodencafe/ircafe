package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.IrcEventNotificationRule;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.miginfocom.swing.MigLayout;

final class IrcEventNotificationsTabSupport {
  private IrcEventNotificationsTabSupport() {}

  static JPanel buildTab(
      IrcEventNotificationControls controls, Component owner, RuleEditor ruleEditor) {
    JPanel tab =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]"));
    tab.setOpaque(false);

    JComboBox<IrcEventNotificationPresetSupport.Preset> defaultsPreset =
        new JComboBox<>(IrcEventNotificationPresetSupport.Preset.values());
    JButton applyDefaults = new JButton("Apply defaults");
    JButton resetToIrcafeDefaults = new JButton("Reset to IRCafe defaults");
    PreferencesDialog.configureIconOnlyButton(
        applyDefaults, "check", "Apply preset defaults to matching IRC event types");
    PreferencesDialog.configureIconOnlyButton(
        resetToIrcafeDefaults, "refresh", "Replace all IRC event rules with IRCafe defaults");

    JPanel defaultsRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]8[]8[]", "[]"));
    defaultsRow.setOpaque(false);
    defaultsRow.add(new JLabel("Defaults"));
    defaultsRow.add(defaultsPreset, "w 240!");
    defaultsRow.add(applyDefaults, "w 36!, h 28!");
    defaultsRow.add(resetToIrcafeDefaults, "w 36!, h 28!");

    JButton add = new JButton("Add");
    JButton edit = new JButton("Edit");
    JButton enableRule = new JButton("Enable");
    JButton disableRule = new JButton("Disable");
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    PreferencesDialog.configureIconOnlyButton(add, "plus", "Add IRC event rule");
    PreferencesDialog.configureIconOnlyButton(edit, "edit", "Edit selected IRC event rule");
    PreferencesDialog.configureIconOnlyButton(
        enableRule, "check", "Enable selected IRC event rule");
    PreferencesDialog.configureIconOnlyButton(
        disableRule, "pause", "Disable selected IRC event rule");
    PreferencesDialog.configureIconOnlyButton(
        duplicate, "copy", "Duplicate selected IRC event rule");
    PreferencesDialog.configureIconOnlyButton(remove, "trash", "Remove selected IRC event rule");
    PreferencesDialog.configureIconOnlyButton(up, "arrow-up", "Move selected IRC event rule up");
    PreferencesDialog.configureIconOnlyButton(
        down, "arrow-down", "Move selected IRC event rule down");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(add);
    buttons.add(edit);
    buttons.add(enableRule);
    buttons.add(disableRule);
    buttons.add(duplicate);
    buttons.add(remove);
    buttons.add(up);
    buttons.add(down);

    Runnable refreshRuleButtons =
        () -> {
          int viewRow = controls.table().getSelectedRow();
          boolean hasSelection = viewRow >= 0;
          int modelRow = hasSelection ? controls.table().convertRowIndexToModel(viewRow) : -1;
          IrcEventNotificationRule selectedRule =
              modelRow >= 0 ? controls.model().ruleAt(modelRow) : null;
          boolean selectedEnabled = selectedRule != null && selectedRule.enabled();
          edit.setEnabled(hasSelection);
          enableRule.setEnabled(hasSelection && !selectedEnabled);
          disableRule.setEnabled(hasSelection && selectedEnabled);
          duplicate.setEnabled(hasSelection);
          remove.setEnabled(hasSelection);
          up.setEnabled(hasSelection && modelRow > 0);
          down.setEnabled(
              hasSelection && modelRow >= 0 && modelRow < (controls.model().getRowCount() - 1));
        };

    Runnable openEditRuleDialog =
        () -> {
          int viewRow = controls.table().getSelectedRow();
          if (viewRow < 0) return;
          int modelRow = controls.table().convertRowIndexToModel(viewRow);
          IrcEventNotificationRule seed = controls.model().ruleAt(modelRow);
          if (seed == null) return;
          IrcEventNotificationRule edited = ruleEditor.prompt("Edit IRC Event Rule", seed);
          if (edited == null) return;
          controls.model().setRule(modelRow, edited);
          selectModelRow(controls, modelRow);
          refreshRuleButtons.run();
        };

    add.addActionListener(
        e -> {
          IrcEventNotificationRule created = ruleEditor.prompt("Add IRC Event Rule", null);
          if (created == null) return;
          int row = controls.model().addRule(created);
          selectModelRow(controls, row);
          refreshRuleButtons.run();
        });

    edit.addActionListener(e -> openEditRuleDialog.run());

    enableRule.addActionListener(
        e -> {
          int modelRow = selectedModelRow(controls);
          if (modelRow < 0) return;
          controls.model().setEnabledAt(modelRow, true);
          refreshRuleButtons.run();
        });

    disableRule.addActionListener(
        e -> {
          int modelRow = selectedModelRow(controls);
          if (modelRow < 0) return;
          controls.model().setEnabledAt(modelRow, false);
          refreshRuleButtons.run();
        });

    duplicate.addActionListener(
        e -> {
          int modelRow = selectedModelRow(controls);
          if (modelRow < 0) return;
          int dup = controls.model().duplicateRow(modelRow);
          selectModelRow(controls, dup);
          refreshRuleButtons.run();
        });

    remove.addActionListener(
        e -> {
          int modelRow = selectedModelRow(controls);
          if (modelRow < 0) return;
          IrcEventNotificationRule rule = controls.model().ruleAt(modelRow);
          String label = IrcEventNotificationTableModel.effectiveRuleLabel(rule);
          int res =
              JOptionPane.showConfirmDialog(
                  owner,
                  "Remove IRC event rule \"" + label + "\"?",
                  "Remove IRC Event Rule",
                  JOptionPane.OK_CANCEL_OPTION);
          if (res != JOptionPane.OK_OPTION) return;
          controls.model().removeRow(modelRow);
          int nextModelRow = Math.min(modelRow, controls.model().getRowCount() - 1);
          if (nextModelRow >= 0) {
            selectModelRow(controls, nextModelRow);
          } else {
            controls.table().clearSelection();
          }
          refreshRuleButtons.run();
        });

    up.addActionListener(
        e -> {
          int modelRow = selectedModelRow(controls);
          if (modelRow < 0) return;
          int next = controls.model().moveRow(modelRow, modelRow - 1);
          selectModelRow(controls, next);
          refreshRuleButtons.run();
        });

    down.addActionListener(
        e -> {
          int modelRow = selectedModelRow(controls);
          if (modelRow < 0) return;
          int next = controls.model().moveRow(modelRow, modelRow + 1);
          selectModelRow(controls, next);
          refreshRuleButtons.run();
        });

    applyDefaults.addActionListener(
        e -> {
          IrcEventNotificationPresetSupport.Preset preset =
              (IrcEventNotificationPresetSupport.Preset) defaultsPreset.getSelectedItem();
          if (preset == null) return;
          List<IrcEventNotificationRule> rules =
              IrcEventNotificationPresetSupport.buildPreset(preset);
          if (rules.isEmpty()) return;
          controls.model().applyPreset(rules);
          int row = controls.model().firstRowForEvent(rules.getFirst().eventType());
          if (row < 0) row = 0;
          selectModelRow(controls, row);
          refreshRuleButtons.run();
        });

    resetToIrcafeDefaults.addActionListener(
        e -> {
          int confirm =
              JOptionPane.showConfirmDialog(
                  owner,
                  "Replace all IRC event rules with IRCafe defaults?",
                  "Reset IRC event rules",
                  JOptionPane.OK_CANCEL_OPTION);
          if (confirm != JOptionPane.OK_OPTION) return;

          List<IrcEventNotificationRule> defaults = IrcEventNotificationRule.defaults();
          if (defaults.isEmpty()) return;
          controls.model().replaceAll(defaults);
          if (controls.table().getRowCount() > 0) {
            controls.table().getSelectionModel().setSelectionInterval(0, 0);
            controls.table().scrollRectToVisible(controls.table().getCellRect(0, 0, true));
          } else {
            controls.table().clearSelection();
          }
          refreshRuleButtons.run();
        });

    controls
        .table()
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e != null && e.getValueIsAdjusting()) return;
              refreshRuleButtons.run();
            });

    controls
        .table()
        .addMouseListener(
            new java.awt.event.MouseAdapter() {
              @Override
              public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e == null) return;
                if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
                if (e.getClickCount() != 2) return;
                int viewRow = controls.table().rowAtPoint(e.getPoint());
                if (viewRow < 0) return;
                controls.table().getSelectionModel().setSelectionInterval(viewRow, viewRow);
                openEditRuleDialog.run();
              }
            });
    refreshRuleButtons.run();

    JScrollPane scroll = new JScrollPane(controls.table());
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JPanel presetsPanel =
        PreferencesDialog.captionPanelWithPadding(
            "Presets", "insets 0, fillx, wrap 1", "[grow,fill]", "[]4[]", 10, 10, 10, 10);
    presetsPanel.add(defaultsRow, "growx, wmin 0, wrap");
    presetsPanel.add(
        PreferencesDialog.helpText(
            "Configure event actions for kicks, bans, invites, joins, and mode changes.\n"
                + "Source supports self/others/specific nicks/glob/regex. Channel scope supports Active channel only.\n"
                + "CTCP rules can filter command/value and include quick templates in the Filters tab.\n"
                + "Apply defaults merges by event type. Reset to IRCafe defaults replaces the full rule list."),
        "growx, wmin 0, wrap");
    tab.add(presetsPanel, "growx, wmin 0, wrap");

    JPanel rulesPanel =
        PreferencesDialog.captionPanelWithPadding(
            "Rules", "insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]4[]", 10, 10, 10, 10);
    rulesPanel.add(buttons, "growx, wmin 0, wrap");
    scroll.setPreferredSize(new Dimension(400, 260));
    rulesPanel.add(scroll, "grow, push, wmin 0, wrap");
    rulesPanel.add(
        PreferencesDialog.helpText("Tip: Double-click a rule to edit it."), "growx, wmin 0, wrap");
    tab.add(rulesPanel, "grow, push, wmin 0, wrap");

    return tab;
  }

  private static int selectedModelRow(IrcEventNotificationControls controls) {
    int viewRow = controls.table().getSelectedRow();
    return viewRow >= 0 ? controls.table().convertRowIndexToModel(viewRow) : -1;
  }

  private static void selectModelRow(IrcEventNotificationControls controls, int modelRow) {
    if (modelRow < 0) return;
    int viewRow = controls.table().convertRowIndexToView(modelRow);
    if (viewRow >= 0) {
      controls.table().getSelectionModel().setSelectionInterval(viewRow, viewRow);
      controls.table().scrollRectToVisible(controls.table().getCellRect(viewRow, 0, true));
    }
  }

  @FunctionalInterface
  interface RuleEditor {
    IrcEventNotificationRule prompt(String title, IrcEventNotificationRule seed);
  }
}
