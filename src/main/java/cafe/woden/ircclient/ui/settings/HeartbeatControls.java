package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;

final class HeartbeatControls {
  final JCheckBox enabled;
  final JSpinner checkPeriodSeconds;
  final JSpinner timeoutSeconds;

  HeartbeatControls(JCheckBox enabled, JSpinner checkPeriodSeconds, JSpinner timeoutSeconds) {
    this.enabled = enabled;
    this.checkPeriodSeconds = checkPeriodSeconds;
    this.timeoutSeconds = timeoutSeconds;
  }
}
