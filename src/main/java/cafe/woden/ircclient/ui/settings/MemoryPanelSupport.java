package cafe.woden.ircclient.ui.settings;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import net.miginfocom.swing.MigLayout;

final class MemoryPanelSupport {
  private MemoryPanelSupport() {}

  static JPanel buildPanel(
      JComboBox<MemoryUsageDisplayMode> memoryUsageDisplayMode,
      JSpinner memoryUsageRefreshIntervalMs,
      MemoryWarningControls memoryWarnings) {
    JPanel form =
        new JPanel(new MigLayout("insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]"));
    form.add(PreferencesDialog.tabTitle("Memory"), "span 2, growx, wmin 0, wrap");

    form.add(PreferencesDialog.sectionTitle("Widget"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Memory usage widget"));
    form.add(memoryUsageDisplayMode, "growx");
    form.add(new JLabel("Refresh interval (ms)"));
    form.add(memoryUsageRefreshIntervalMs, "w 140!");

    form.add(PreferencesDialog.sectionTitle("Warnings"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Warn near max (%)"));
    form.add(memoryWarnings.nearMaxPercent, "w 110!");

    form.add(new JLabel("Warning actions"), "aligny top");
    JPanel warningActions =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]2[]2[]2[]"));
    warningActions.setOpaque(false);
    warningActions.add(memoryWarnings.tooltipEnabled, "growx");
    warningActions.add(memoryWarnings.toastEnabled, "growx");
    warningActions.add(memoryWarnings.pushyEnabled, "growx");
    warningActions.add(memoryWarnings.soundEnabled, "growx");
    form.add(warningActions, "growx");

    JTextArea hint = PreferencesDialog.subtleInfoText();
    hint.setText(
        "Controls the memory widget in the top menu bar and threshold-triggered warning behavior.");
    form.add(new JLabel(""));
    form.add(hint, "growx, wmin 0");

    JButton reset = new JButton("Reset memory defaults");
    reset.setToolTipText("Reset memory mode and warning actions to defaults.");
    reset.addActionListener(
        e -> {
          memoryUsageDisplayMode.setSelectedItem(MemoryUsageDisplayMode.LONG);
          memoryUsageRefreshIntervalMs.setValue(1000);
          memoryWarnings.nearMaxPercent.setValue(5);
          memoryWarnings.tooltipEnabled.setSelected(true);
          memoryWarnings.toastEnabled.setSelected(false);
          memoryWarnings.pushyEnabled.setSelected(false);
          memoryWarnings.soundEnabled.setSelected(false);
        });
    form.add(new JLabel(""));
    form.add(reset, "alignx left");

    return form;
  }
}
