package cafe.woden.ircclient.ui.settings.network;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.NetworkSettingsRuntimeConfigPort;
import cafe.woden.ircclient.irc.backend.IrcHeartbeatMaintenanceService;
import cafe.woden.ircclient.net.NetFloodProtectionContext;
import cafe.woden.ircclient.net.NetHeartbeatContext;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.ui.localization.UiMessages;
import cafe.woden.ircclient.ui.settings.PreferencesUiSupport;
import cafe.woden.ircclient.ui.settings.SettingsValueSupport;
import cafe.woden.ircclient.ui.settings.UiSettings;
import java.util.List;

public final class NetworkAdvancedControlsSupport {
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private NetworkAdvancedControlsSupport() {}

  public static NetworkAdvancedControls buildControls(
      UiSettings current,
      List<AutoCloseable> closeables,
      NetworkSettingsRuntimeConfigPort runtimeConfig,
      boolean trustAllTlsCertificates,
      boolean defaultPreferLoginHint,
      String defaultLoginTemplate) {
    IrcProperties.Proxy proxy = NetProxyContext.settings();
    if (proxy == null) {
      proxy = new IrcProperties.Proxy(false, "", 1080, "", "", true, 10_000, 30_000);
    }

    IrcProperties.Heartbeat heartbeat = NetHeartbeatContext.settings();
    if (heartbeat == null) {
      heartbeat = new IrcProperties.Heartbeat(true, 15_000, 360_000);
    }

    boolean preferLoginHintDefault =
        runtimeConfig == null
            ? defaultPreferLoginHint
            : runtimeConfig.readGenericBouncerPreferLoginHint(defaultPreferLoginHint);
    String loginTemplateDefault =
        runtimeConfig == null
            ? defaultLoginTemplate
            : runtimeConfig.readGenericBouncerLoginTemplate(defaultLoginTemplate);

    NetworkConnectionPanelControls connection =
        NetworkConnectionPanelSupport.buildControls(
            proxy,
            heartbeat,
            NetFloodProtectionContext.settings(),
            closeables,
            trustAllTlsCertificates,
            preferLoginHintDefault,
            loginTemplateDefault);
    UserLookupsPanelControls userLookups =
        UserLookupsPanelSupport.buildControls(current, closeables);

    return new NetworkAdvancedControls(
        connection.proxy,
        userLookups.userhost,
        userLookups.enrichment,
        connection.heartbeat,
        connection.floodProtection,
        connection.bouncer,
        userLookups.monitorIsonPollIntervalSeconds,
        connection.trustAllTlsCertificates,
        connection.panel,
        userLookups.panel);
  }

  public static IrcProperties.Proxy readProxySettings(ProxyControls proxy) {
    boolean enabled = proxy.enabled.isSelected();
    String host = PreferencesUiSupport.trimmedText(proxy.host);
    int port = PreferencesUiSupport.spinnerInt(proxy.port);
    String username = PreferencesUiSupport.trimmedText(proxy.username);
    String password = PreferencesUiSupport.passwordText(proxy.password);
    boolean remoteDns = proxy.remoteDns.isSelected();
    int connectTimeoutSeconds = PreferencesUiSupport.spinnerInt(proxy.connectTimeoutSeconds);
    int readTimeoutSeconds = PreferencesUiSupport.spinnerInt(proxy.readTimeoutSeconds);

    if (enabled) {
      if (host.isBlank()) {
        throw new IllegalArgumentException(
            MESSAGES.text("preferences.network.validation.proxyHostRequired"));
      }
      if (port <= 0 || port > 65535) {
        throw new IllegalArgumentException(
            MESSAGES.text("preferences.network.validation.proxyPortRange"));
      }
    }

    return new IrcProperties.Proxy(
        enabled,
        host,
        port,
        username,
        password,
        remoteDns,
        Math.max(1L, connectTimeoutSeconds) * 1000L,
        Math.max(1L, readTimeoutSeconds) * 1000L);
  }

  public static IrcProperties.Heartbeat readHeartbeatSettings(HeartbeatControls heartbeat) {
    boolean enabled = heartbeat.enabled.isSelected();
    int checkSeconds = PreferencesUiSupport.spinnerInt(heartbeat.checkPeriodSeconds);
    int timeoutSeconds = PreferencesUiSupport.spinnerInt(heartbeat.timeoutSeconds);

    checkSeconds = Math.max(1, checkSeconds);
    timeoutSeconds = Math.max(1, timeoutSeconds);
    if (enabled && timeoutSeconds <= checkSeconds) {
      throw new IllegalArgumentException(
          MESSAGES.text("preferences.network.validation.heartbeatTimeoutGreater"));
    }

    return new IrcProperties.Heartbeat(enabled, checkSeconds * 1000L, timeoutSeconds * 1000L);
  }

  public static NetworkSettings readSettings(NetworkAdvancedControls controls) {
    IrcProperties.Proxy proxy;
    try {
      proxy = readProxySettings(controls.proxy());
    } catch (Exception ex) {
      throw new NetworkSettingsException(
          MESSAGES.text("preferences.network.validation.proxy.title"),
          MESSAGES.text("preferences.network.validation.proxy.message", ex.getMessage()),
          ex);
    }

    IrcProperties.Heartbeat heartbeat;
    try {
      heartbeat = readHeartbeatSettings(controls.heartbeat());
    } catch (Exception ex) {
      throw new NetworkSettingsException(
          MESSAGES.text("preferences.network.validation.heartbeat.title"),
          MESSAGES.text("preferences.network.validation.heartbeat.message", ex.getMessage()),
          ex);
    }

    BouncerSettings bouncer = readBouncerSettings(controls.bouncer());
    boolean trustAllTlsCertificates = controls.trustAllTlsCertificates().isSelected();
    FloodProtectionControls flood = controls.floodProtection();
    IrcProperties.FloodProtection floodProtection =
        new IrcProperties.FloodProtection(
            flood.enabled().isSelected(),
            PreferencesUiSupport.spinnerInt(flood.commandIntervalMs()),
            PreferencesUiSupport.spinnerInt(flood.autoJoinDelaySeconds()) * 1000L);
    return new NetworkSettings(proxy, heartbeat, floodProtection, bouncer, trustAllTlsCertificates);
  }

  public static void rememberSettings(
      NetworkSettingsRuntimeConfigPort runtimeConfig,
      IrcHeartbeatMaintenanceService heartbeatMaintenance,
      NetworkSettings settings) {
    runtimeConfig.rememberClientProxy(settings.proxy());
    NetProxyContext.configure(settings.proxy());
    runtimeConfig.rememberClientHeartbeat(settings.heartbeat());
    NetHeartbeatContext.configure(settings.heartbeat());
    runtimeConfig.rememberClientFloodProtection(settings.floodProtection());
    NetFloodProtectionContext.configure(settings.floodProtection());
    if (heartbeatMaintenance != null) {
      heartbeatMaintenance.rescheduleActiveHeartbeats();
    }
    runtimeConfig.rememberGenericBouncerPreferLoginHint(settings.bouncer().preferLoginHint());
    runtimeConfig.rememberGenericBouncerLoginTemplate(settings.bouncer().loginTemplate());
    runtimeConfig.rememberClientTlsTrustAllCertificates(settings.trustAllTlsCertificates());
    NetTlsContext.configure(settings.trustAllTlsCertificates());
  }

  private static BouncerSettings readBouncerSettings(BouncerControls bouncer) {
    return new BouncerSettings(
        bouncer.preferLoginHint.isSelected(),
        PreferencesUiSupport.trimmedText(bouncer.loginTemplate));
  }

  public record NetworkSettings(
      IrcProperties.Proxy proxy,
      IrcProperties.Heartbeat heartbeat,
      IrcProperties.FloodProtection floodProtection,
      BouncerSettings bouncer,
      boolean trustAllTlsCertificates) {}

  public record BouncerSettings(boolean preferLoginHint, String loginTemplate) {
    public BouncerSettings {
      loginTemplate = SettingsValueSupport.trimmedString(loginTemplate);
    }
  }

  public static final class NetworkSettingsException extends IllegalArgumentException {
    private final String title;

    private NetworkSettingsException(String title, String message, Throwable cause) {
      super(message, cause);
      this.title = title;
    }

    public String title() {
      return title;
    }
  }
}
