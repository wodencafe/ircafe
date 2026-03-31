package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.net.NetHeartbeatContext;
import cafe.woden.ircclient.net.NetProxyContext;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;

record NetworkAdvancedControls(
    ProxyControls proxy,
    UserhostControls userhost,
    UserInfoEnrichmentControls enrichment,
    HeartbeatControls heartbeat,
    BouncerControls bouncer,
    JSpinner monitorIsonPollIntervalSeconds,
    JCheckBox trustAllTlsCertificates,
    JPanel networkPanel,
    JPanel userLookupsPanel) {}

final class NetworkAdvancedControlsSupport {
  private NetworkAdvancedControlsSupport() {}

  static NetworkAdvancedControls buildControls(
      UiSettings current,
      List<AutoCloseable> closeables,
      RuntimeConfigStore runtimeConfig,
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
        connection.bouncer,
        userLookups.monitorIsonPollIntervalSeconds,
        connection.trustAllTlsCertificates,
        connection.panel,
        userLookups.panel);
  }
}
