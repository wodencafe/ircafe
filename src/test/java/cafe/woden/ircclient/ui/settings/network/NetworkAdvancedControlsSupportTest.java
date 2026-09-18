package cafe.woden.ircclient.ui.settings.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.NetworkSettingsRuntimeConfigPort;
import cafe.woden.ircclient.irc.backend.IrcHeartbeatMaintenanceService;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;

class NetworkAdvancedControlsSupportTest {

  @Test
  void readProxySettingsRequiresHostWhenEnabled() {
    ProxyControls proxy = proxyControls(true, "", 1080, "", "", true, 10, 30);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> NetworkAdvancedControlsSupport.readProxySettings(proxy));

    assertEquals("Proxy host is required when proxy is enabled.", ex.getMessage());
  }

  @Test
  void readProxySettingsNormalizesTimeoutsAndTrimsText() {
    ProxyControls proxy =
        proxyControls(true, " proxy.local ", 1080, " user ", "secret", false, 0, -2);

    IrcProperties.Proxy settings = NetworkAdvancedControlsSupport.readProxySettings(proxy);

    assertTrue(settings.enabled());
    assertEquals("proxy.local", settings.host());
    assertEquals("user", settings.username());
    assertEquals("secret", settings.password());
    assertFalse(settings.remoteDns());
    assertEquals(1000L, settings.connectTimeoutMs());
    assertEquals(1000L, settings.readTimeoutMs());
  }

  @Test
  void readHeartbeatSettingsRequiresTimeoutAboveCheckPeriodWhenEnabled() {
    HeartbeatControls heartbeat = heartbeatControls(true, 30, 30);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> NetworkAdvancedControlsSupport.readHeartbeatSettings(heartbeat));

    assertEquals("Timeout must be greater than check period.", ex.getMessage());
  }

  @Test
  void readHeartbeatSettingsNormalizesSecondsToMillis() {
    HeartbeatControls heartbeat = heartbeatControls(true, 15, 60);

    IrcProperties.Heartbeat settings =
        NetworkAdvancedControlsSupport.readHeartbeatSettings(heartbeat);

    assertTrue(settings.enabled());
    assertEquals(15_000L, settings.checkPeriodMs());
    assertEquals(60_000L, settings.timeoutMs());
  }

  @Test
  void readSettingsWrapsProxyValidationWithDialogTitle() {
    NetworkAdvancedControls controls =
        networkControls(
            proxyControls(true, "", 1080, "", "", true, 10, 30), heartbeatControls(true, 15, 60));

    NetworkAdvancedControlsSupport.NetworkSettingsException ex =
        assertThrows(
            NetworkAdvancedControlsSupport.NetworkSettingsException.class,
            () -> NetworkAdvancedControlsSupport.readSettings(controls));

    assertEquals("Invalid proxy settings", ex.title());
    assertEquals(
        "Invalid SOCKS proxy settings:\n\nProxy host is required when proxy is enabled.",
        ex.getMessage());
  }

  @Test
  void readSettingsReadsBouncerAndTlsSettings() {
    NetworkAdvancedControls controls =
        networkControls(
            proxyControls(false, "", 1080, "", "", true, 10, 30),
            heartbeatControls(true, 15, 60),
            bouncerControls(true, "  {base}/{network}  "),
            true);

    NetworkAdvancedControlsSupport.NetworkSettings settings =
        NetworkAdvancedControlsSupport.readSettings(controls);

    assertEquals(new IrcProperties.FloodProtection(1500, 5000), settings.floodProtection());
    assertTrue(settings.bouncer().preferLoginHint());
    assertEquals("{base}/{network}", settings.bouncer().loginTemplate());
    assertTrue(settings.trustAllTlsCertificates());
  }

  @Test
  void rememberSettingsPersistsNetworkSettingsAndReschedulesHeartbeats() {
    NetworkSettingsRuntimeConfigPort runtimeConfig = mock(NetworkSettingsRuntimeConfigPort.class);
    IrcHeartbeatMaintenanceService heartbeatMaintenance =
        mock(IrcHeartbeatMaintenanceService.class);
    IrcProperties.Proxy proxy =
        new IrcProperties.Proxy(true, "proxy.local", 1080, "", "", true, 1000, 2000);
    IrcProperties.Heartbeat heartbeat = new IrcProperties.Heartbeat(true, 15_000, 60_000);
    NetworkAdvancedControlsSupport.NetworkSettings settings =
        new NetworkAdvancedControlsSupport.NetworkSettings(
            proxy,
            heartbeat,
            new IrcProperties.FloodProtection(2000, 7000),
            new NetworkAdvancedControlsSupport.BouncerSettings(true, "{base}/{network}"),
            true);

    NetworkAdvancedControlsSupport.rememberSettings(runtimeConfig, heartbeatMaintenance, settings);

    verify(runtimeConfig).rememberClientProxy(proxy);
    verify(runtimeConfig).rememberClientHeartbeat(heartbeat);
    verify(runtimeConfig)
        .rememberClientFloodProtection(new IrcProperties.FloodProtection(2000, 7000));
    cafe.woden.ircclient.net.NetFloodProtectionContext.configure(null);
    verify(heartbeatMaintenance).rescheduleActiveHeartbeats();
    verify(runtimeConfig).rememberGenericBouncerPreferLoginHint(true);
    verify(runtimeConfig).rememberGenericBouncerLoginTemplate("{base}/{network}");
    verify(runtimeConfig).rememberClientTlsTrustAllCertificates(true);
  }

  private static ProxyControls proxyControls(
      boolean enabled,
      String host,
      int port,
      String username,
      String password,
      boolean remoteDns,
      int connectTimeoutSeconds,
      int readTimeoutSeconds) {
    JCheckBox enabledBox = new JCheckBox();
    enabledBox.setSelected(enabled);
    JCheckBox remoteDnsBox = new JCheckBox();
    remoteDnsBox.setSelected(remoteDns);
    return new ProxyControls(
        enabledBox,
        new JTextField(host),
        spinner(port),
        remoteDnsBox,
        new JTextField(username),
        new JPasswordField(password),
        new JButton("Clear"),
        spinner(connectTimeoutSeconds),
        spinner(readTimeoutSeconds));
  }

  private static HeartbeatControls heartbeatControls(
      boolean enabled, int checkSeconds, int timeoutSeconds) {
    JCheckBox enabledBox = new JCheckBox();
    enabledBox.setSelected(enabled);
    return new HeartbeatControls(enabledBox, spinner(checkSeconds), spinner(timeoutSeconds));
  }

  private static BouncerControls bouncerControls(boolean preferLoginHint, String loginTemplate) {
    JCheckBox preferLoginHintBox = new JCheckBox();
    preferLoginHintBox.setSelected(preferLoginHint);
    return new BouncerControls(preferLoginHintBox, new JTextField(loginTemplate));
  }

  private static NetworkAdvancedControls networkControls(
      ProxyControls proxy, HeartbeatControls heartbeat) {
    return networkControls(proxy, heartbeat, bouncerControls(false, ""), false);
  }

  private static NetworkAdvancedControls networkControls(
      ProxyControls proxy,
      HeartbeatControls heartbeat,
      BouncerControls bouncer,
      boolean trustAllTlsCertificates) {
    JCheckBox trustAllTlsCertificatesBox = new JCheckBox();
    trustAllTlsCertificatesBox.setSelected(trustAllTlsCertificates);
    return new NetworkAdvancedControls(
        proxy,
        null,
        null,
        heartbeat,
        new FloodProtectionControls(new JCheckBox("", true), spinner(1500), spinner(5)),
        bouncer,
        null,
        trustAllTlsCertificatesBox,
        null,
        null);
  }

  private static JSpinner spinner(int value) {
    return new JSpinner(new SpinnerNumberModel(value, -10, 100_000, 1));
  }
}
