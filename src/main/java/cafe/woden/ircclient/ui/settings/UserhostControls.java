package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;

final class UserhostControls {
  final JCheckBox enabled;
  final JSpinner minIntervalSeconds;
  final JSpinner maxPerMinute;
  final JSpinner nickCooldownMinutes;
  final JSpinner maxNicksPerCommand;

  UserhostControls(
      JCheckBox enabled,
      JSpinner minIntervalSeconds,
      JSpinner maxPerMinute,
      JSpinner nickCooldownMinutes,
      JSpinner maxNicksPerCommand) {
    this.enabled = enabled;
    this.minIntervalSeconds = minIntervalSeconds;
    this.maxPerMinute = maxPerMinute;
    this.nickCooldownMinutes = nickCooldownMinutes;
    this.maxNicksPerCommand = maxNicksPerCommand;
  }
}
