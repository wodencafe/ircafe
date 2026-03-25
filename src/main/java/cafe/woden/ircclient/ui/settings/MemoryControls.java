package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;

final class MemoryWarningControls {
  final JSpinner nearMaxPercent;
  final JCheckBox tooltipEnabled;
  final JCheckBox toastEnabled;
  final JCheckBox pushyEnabled;
  final JCheckBox soundEnabled;

  MemoryWarningControls(
      JSpinner nearMaxPercent,
      JCheckBox tooltipEnabled,
      JCheckBox toastEnabled,
      JCheckBox pushyEnabled,
      JCheckBox soundEnabled) {
    this.nearMaxPercent = nearMaxPercent;
    this.tooltipEnabled = tooltipEnabled;
    this.toastEnabled = toastEnabled;
    this.pushyEnabled = pushyEnabled;
    this.soundEnabled = soundEnabled;
  }
}
