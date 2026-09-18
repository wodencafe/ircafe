package cafe.woden.ircclient.ui.settings.network;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;

public record NetworkAdvancedControls(
    ProxyControls proxy,
    UserhostControls userhost,
    UserInfoEnrichmentControls enrichment,
    HeartbeatControls heartbeat,
    FloodProtectionControls floodProtection,
    BouncerControls bouncer,
    JSpinner monitorIsonPollIntervalSeconds,
    JCheckBox trustAllTlsCertificates,
    JPanel networkPanel,
    JPanel userLookupsPanel) {}
