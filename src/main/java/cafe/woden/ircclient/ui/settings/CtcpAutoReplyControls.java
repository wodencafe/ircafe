package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;

final class CtcpAutoReplyControls {
  final JCheckBox enabled;
  final JCheckBox version;
  final JCheckBox ping;
  final JCheckBox time;

  CtcpAutoReplyControls(JCheckBox enabled, JCheckBox version, JCheckBox ping, JCheckBox time) {
    this.enabled = enabled;
    this.version = version;
    this.ping = ping;
    this.time = time;
  }
}
