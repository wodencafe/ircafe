package cafe.woden.ircclient.ui.settings;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;

final class ProxyControls {
  final JCheckBox enabled;
  final JTextField host;
  final JSpinner port;
  final JCheckBox remoteDns;
  final JTextField username;
  final JPasswordField password;
  final JButton clearPassword;
  final JSpinner connectTimeoutSeconds;
  final JSpinner readTimeoutSeconds;

  ProxyControls(
      JCheckBox enabled,
      JTextField host,
      JSpinner port,
      JCheckBox remoteDns,
      JTextField username,
      JPasswordField password,
      JButton clearPassword,
      JSpinner connectTimeoutSeconds,
      JSpinner readTimeoutSeconds) {
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.remoteDns = remoteDns;
    this.username = username;
    this.password = password;
    this.clearPassword = clearPassword;
    this.connectTimeoutSeconds = connectTimeoutSeconds;
    this.readTimeoutSeconds = readTimeoutSeconds;
  }
}
