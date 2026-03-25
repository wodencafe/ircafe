package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

final class NetworkConnectionPanelControls {
  final ProxyControls proxy;
  final HeartbeatControls heartbeat;
  final BouncerControls bouncer;
  final JCheckBox trustAllTlsCertificates;
  final JPanel panel;

  NetworkConnectionPanelControls(
      ProxyControls proxy,
      HeartbeatControls heartbeat,
      BouncerControls bouncer,
      JCheckBox trustAllTlsCertificates,
      JPanel panel) {
    this.proxy = proxy;
    this.heartbeat = heartbeat;
    this.bouncer = bouncer;
    this.trustAllTlsCertificates = trustAllTlsCertificates;
    this.panel = panel;
  }
}
