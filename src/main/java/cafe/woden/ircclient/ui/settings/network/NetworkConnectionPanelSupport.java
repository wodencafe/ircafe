package cafe.woden.ircclient.ui.settings.network;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.ui.localization.UiMessages;
import cafe.woden.ircclient.ui.settings.PreferencesUiSupport;
import cafe.woden.ircclient.ui.settings.SettingsDocumentListener;
import cafe.woden.ircclient.ui.util.MigConstraints;
import cafe.woden.ircclient.ui.util.MigLayoutConstraints;
import cafe.woden.ircclient.ui.util.MigLayouts;
import cafe.woden.ircclient.ui.util.SwingClientProperties;
import com.formdev.flatlaf.FlatClientProperties;
import java.util.List;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

final class NetworkConnectionPanelSupport {
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private NetworkConnectionPanelSupport() {}

  static NetworkConnectionPanelControls buildControls(
      IrcProperties.Proxy proxySettings,
      IrcProperties.Heartbeat heartbeatSettings,
      IrcProperties.FloodProtection floodSettings,
      List<AutoCloseable> closeables,
      boolean trustAllTlsCertificatesSelected,
      boolean preferLoginHintDefault,
      String loginTemplateDefault) {
    JPanel networkPanel = new JPanel(MigLayouts.singleColumnFill(0, "[]0[grow,fill]"));

    JPanel proxyTab = new JPanel(MigLayouts.twoColumnForm(12, MigLayouts.rows(2, 6)));
    proxyTab.setOpaque(false);

    JPanel proxyHeader = new JPanel(MigLayouts.fillXGrowTrailing(6));
    proxyHeader.setOpaque(false);
    proxyHeader.add(
        PreferencesUiSupport.sectionTitle(MESSAGES.text("preferences.network.proxy.section")),
        MigConstraints.growXMinWidth0());
    proxyHeader.add(
        PreferencesUiSupport.whyHelpButton(
            MESSAGES.text("preferences.network.proxy.help.title"),
            MESSAGES.text("preferences.network.proxy.help.message")),
        MigConstraints.alignXRight());
    proxyTab.add(proxyHeader, MigConstraints.span2GrowXMinWidth0Wrap());

    JTextArea proxyBlurb = PreferencesUiSupport.subtleInfoText();
    proxyBlurb.setText(MESSAGES.text("preferences.network.proxy.blurb"));
    proxyTab.add(proxyBlurb, MigConstraints.span2GrowXMinWidth0Wrap());

    JCheckBox proxyEnabled = new JCheckBox(MESSAGES.text("preferences.network.proxy.enabled"));
    proxyEnabled.setSelected(proxySettings.enabled());

    JTextField proxyHost = new JTextField(Objects.toString(proxySettings.host(), ""));
    PreferencesUiSupport.placeholder(proxyHost, "127.0.0.1");

    int portDefault =
        (proxySettings.port() > 0 && proxySettings.port() <= 65535) ? proxySettings.port() : 1080;
    JSpinner proxyPort = PreferencesUiSupport.numberSpinner(portDefault, 1, 65535, 1, closeables);

    JCheckBox proxyRemoteDns = new JCheckBox();
    proxyRemoteDns.setSelected(proxySettings.remoteDns());
    proxyRemoteDns.setToolTipText(MESSAGES.text("preferences.network.proxy.remoteDns.tooltip"));
    JComponent proxyRemoteDnsRow =
        PreferencesUiSupport.wrapCheckBox(
            proxyRemoteDns, MESSAGES.text("preferences.network.proxy.remoteDns"));

    JTextField proxyUsername = new JTextField(Objects.toString(proxySettings.username(), ""));
    PreferencesUiSupport.placeholder(
        proxyUsername, MESSAGES.text("preferences.network.placeholder.optional"));

    JPasswordField proxyPassword =
        new JPasswordField(Objects.toString(proxySettings.password(), ""));
    PreferencesUiSupport.placeholder(
        proxyPassword, MESSAGES.text("preferences.network.placeholder.optional"));
    proxyPassword.putClientProperty(SwingClientProperties.PASSWORD_FIELD_SHOW_REVEAL_BUTTON, true);
    proxyPassword.putClientProperty(FlatClientProperties.STYLE, "showRevealButton:true;");
    javax.swing.JButton clearPassword =
        new javax.swing.JButton(MESSAGES.text("common.button.clear"));
    clearPassword.addActionListener(e -> proxyPassword.setText(""));

    int connectTimeoutSec = (int) Math.max(1, proxySettings.connectTimeoutMs() / 1000L);
    int readTimeoutSec = (int) Math.max(1, proxySettings.readTimeoutMs() / 1000L);
    JSpinner connectTimeoutSeconds =
        PreferencesUiSupport.numberSpinner(connectTimeoutSec, 1, 300, 1, closeables);
    JSpinner readTimeoutSeconds =
        PreferencesUiSupport.numberSpinner(readTimeoutSec, 1, 600, 1, closeables);

    JPanel passwordRow = new JPanel(MigLayouts.fillXGrowTrailing(6));
    passwordRow.setOpaque(false);
    passwordRow.add(proxyPassword, MigConstraints.growXPushXMinWidth0());
    passwordRow.add(clearPassword);

    Runnable updateProxyEnabledState =
        () -> {
          boolean enabled = proxyEnabled.isSelected();
          proxyHost.setEnabled(enabled);
          proxyPort.setEnabled(enabled);
          proxyRemoteDns.setEnabled(enabled);
          proxyUsername.setEnabled(enabled);
          proxyPassword.setEnabled(enabled);
          clearPassword.setEnabled(enabled);
          connectTimeoutSeconds.setEnabled(enabled);
          readTimeoutSeconds.setEnabled(enabled);
        };

    Runnable validateProxyInputs =
        () -> {
          if (!proxyEnabled.isSelected()) {
            proxyHost.putClientProperty(FlatClientProperties.OUTLINE, null);
            proxyUsername.putClientProperty(FlatClientProperties.OUTLINE, null);
            proxyPassword.putClientProperty(FlatClientProperties.OUTLINE, null);
            return;
          }

          String host = PreferencesUiSupport.trimmedText(proxyHost);
          proxyHost.putClientProperty(
              FlatClientProperties.OUTLINE,
              host.isBlank() ? FlatClientProperties.OUTLINE_ERROR : null);

          String user = PreferencesUiSupport.trimmedText(proxyUsername);
          String pass = PreferencesUiSupport.trimmedPasswordText(proxyPassword);

          boolean hasUser = !user.isBlank();
          boolean hasPass = !pass.isBlank();
          boolean mismatch = hasUser ^ hasPass;

          Object outline = mismatch ? FlatClientProperties.OUTLINE_WARNING : null;
          proxyUsername.putClientProperty(FlatClientProperties.OUTLINE, outline);
          proxyPassword.putClientProperty(FlatClientProperties.OUTLINE, outline);
        };

    proxyEnabled.addActionListener(
        e -> {
          updateProxyEnabledState.run();
          validateProxyInputs.run();
        });
    updateProxyEnabledState.run();

    proxyHost.getDocument().addDocumentListener(new SettingsDocumentListener(validateProxyInputs));
    proxyUsername
        .getDocument()
        .addDocumentListener(new SettingsDocumentListener(validateProxyInputs));
    proxyPassword
        .getDocument()
        .addDocumentListener(new SettingsDocumentListener(validateProxyInputs));
    validateProxyInputs.run();

    proxyTab.add(proxyEnabled, MigConstraints.spanXWrap(2));
    proxyTab.add(new JLabel(MESSAGES.text("preferences.network.field.host")));
    proxyTab.add(proxyHost, MigConstraints.growXMinWidth0());
    proxyTab.add(new JLabel(MESSAGES.text("preferences.network.field.port")));
    proxyTab.add(proxyPort, MigConstraints.width(110));
    proxyTab.add(new JLabel(""));
    proxyTab.add(proxyRemoteDnsRow, MigConstraints.growXMinWidth0());
    proxyTab.add(new JLabel(MESSAGES.text("preferences.network.field.username")));
    proxyTab.add(proxyUsername, MigConstraints.growXMinWidth0());
    proxyTab.add(new JLabel(MESSAGES.text("preferences.network.field.password")));
    proxyTab.add(passwordRow, MigConstraints.growXMinWidth0());
    proxyTab.add(new JLabel(MESSAGES.text("preferences.network.field.connectTimeoutSec")));
    proxyTab.add(connectTimeoutSeconds, MigConstraints.width(110));
    proxyTab.add(new JLabel(MESSAGES.text("preferences.network.field.readTimeoutSec")));
    proxyTab.add(readTimeoutSeconds, MigConstraints.width(110));

    JPanel tlsTab = new JPanel(MigLayouts.singleColumn(MigLayouts.rows(2, 6)));
    tlsTab.setOpaque(false);
    JPanel tlsHeader = new JPanel(MigLayouts.fillXGrowTrailing(6));
    tlsHeader.setOpaque(false);
    tlsHeader.add(
        PreferencesUiSupport.sectionTitle(MESSAGES.text("preferences.network.tls.section")),
        MigConstraints.growXMinWidth0());
    tlsHeader.add(
        PreferencesUiSupport.whyHelpButton(
            MESSAGES.text("preferences.network.tls.help.title"),
            MESSAGES.text("preferences.network.tls.help.message")),
        MigConstraints.alignXRight());
    tlsTab.add(tlsHeader, MigConstraints.growXMinWidth0Wrap());

    JTextArea tlsBlurb = PreferencesUiSupport.subtleInfoText();
    tlsBlurb.setText(MESSAGES.text("preferences.network.tls.blurb"));
    tlsTab.add(tlsBlurb, MigConstraints.growXMinWidth0Wrap());

    JCheckBox trustAllTlsCertificates = new JCheckBox();
    trustAllTlsCertificates.setSelected(trustAllTlsCertificatesSelected);
    JComponent trustAllTlsRow =
        PreferencesUiSupport.wrapCheckBox(
            trustAllTlsCertificates, MESSAGES.text("preferences.network.tls.trustAll"));
    tlsTab.add(trustAllTlsRow, MigConstraints.growXMinWidth0Wrap());

    JPanel heartbeatTab = new JPanel(MigLayouts.twoColumnForm(12, MigLayouts.rows(2, 6)));
    heartbeatTab.setOpaque(false);
    JPanel heartbeatHeader = new JPanel(MigLayouts.fillXGrowTrailing(6));
    heartbeatHeader.setOpaque(false);
    heartbeatHeader.add(
        PreferencesUiSupport.sectionTitle(MESSAGES.text("preferences.network.heartbeat.section")),
        MigConstraints.growXMinWidth0());
    heartbeatHeader.add(
        PreferencesUiSupport.whyHelpButton(
            MESSAGES.text("preferences.network.heartbeat.help.title"),
            MESSAGES.text("preferences.network.heartbeat.help.message")),
        MigConstraints.alignXRight());
    heartbeatTab.add(heartbeatHeader, MigConstraints.span2GrowXMinWidth0Wrap());

    JTextArea heartbeatBlurb = PreferencesUiSupport.subtleInfoText();
    heartbeatBlurb.setText(MESSAGES.text("preferences.network.heartbeat.blurb"));
    heartbeatTab.add(heartbeatBlurb, MigConstraints.span2GrowXMinWidth0Wrap());

    JCheckBox heartbeatEnabled = new JCheckBox();
    heartbeatEnabled.setSelected(heartbeatSettings.enabled());
    JComponent heartbeatEnabledRow =
        PreferencesUiSupport.wrapCheckBox(
            heartbeatEnabled, MESSAGES.text("preferences.network.heartbeat.enabled"));

    int heartbeatCheckSec = (int) Math.max(1, heartbeatSettings.checkPeriodMs() / 1000L);
    int heartbeatTimeoutSec = (int) Math.max(1, heartbeatSettings.timeoutMs() / 1000L);
    JSpinner heartbeatCheckPeriodSeconds =
        PreferencesUiSupport.numberSpinner(heartbeatCheckSec, 1, 600, 1, closeables);
    JSpinner heartbeatTimeoutSeconds =
        PreferencesUiSupport.numberSpinner(heartbeatTimeoutSec, 5, 7200, 5, closeables);

    Runnable updateHeartbeatEnabledState =
        () -> {
          boolean enabled = heartbeatEnabled.isSelected();
          heartbeatCheckPeriodSeconds.setEnabled(enabled);
          heartbeatTimeoutSeconds.setEnabled(enabled);
        };
    heartbeatEnabled.addActionListener(e -> updateHeartbeatEnabledState.run());
    updateHeartbeatEnabledState.run();

    heartbeatTab.add(heartbeatEnabledRow, MigConstraints.span2GrowXMinWidth0Wrap());
    heartbeatTab.add(new JLabel(MESSAGES.text("preferences.network.field.checkPeriodSec")));
    heartbeatTab.add(heartbeatCheckPeriodSeconds, MigConstraints.width(110));
    heartbeatTab.add(new JLabel(MESSAGES.text("preferences.network.field.timeoutSec")));
    heartbeatTab.add(heartbeatTimeoutSeconds, MigConstraints.width(110));

    JPanel bouncerTab = new JPanel(MigLayouts.twoColumnForm(12, MigLayouts.rows(2, 6)));
    bouncerTab.setOpaque(false);
    JPanel bouncerHeader = new JPanel(MigLayouts.fillXGrowTrailing(6));
    bouncerHeader.setOpaque(false);
    bouncerHeader.add(
        PreferencesUiSupport.sectionTitle(MESSAGES.text("preferences.network.bouncer.section")),
        MigConstraints.growXMinWidth0());
    bouncerHeader.add(
        PreferencesUiSupport.whyHelpButton(
            MESSAGES.text("preferences.network.bouncer.help.title"),
            MESSAGES.text("preferences.network.bouncer.help.message")),
        MigConstraints.alignXRight());
    bouncerTab.add(bouncerHeader, MigConstraints.span2GrowXMinWidth0Wrap());

    JTextArea bouncerBlurb = PreferencesUiSupport.subtleInfoText();
    bouncerBlurb.setText(MESSAGES.text("preferences.network.bouncer.blurb"));
    bouncerTab.add(bouncerBlurb, MigConstraints.span2GrowXMinWidth0Wrap());

    JCheckBox genericBouncerPreferLoginHint = new JCheckBox();
    genericBouncerPreferLoginHint.setSelected(preferLoginHintDefault);
    JComponent genericBouncerPreferLoginHintRow =
        PreferencesUiSupport.wrapCheckBox(
            genericBouncerPreferLoginHint,
            MESSAGES.text("preferences.network.bouncer.preferLoginHint"));
    bouncerTab.add(genericBouncerPreferLoginHintRow, MigConstraints.span2GrowXMinWidth0Wrap());

    JTextField genericBouncerLoginTemplate = new JTextField(loginTemplateDefault);
    PreferencesUiSupport.placeholder(genericBouncerLoginTemplate, loginTemplateDefault);
    JTextArea genericBouncerTemplateHelp = PreferencesUiSupport.subtleInfoText();
    genericBouncerTemplateHelp.setText(MESSAGES.text("preferences.network.bouncer.templateHelp"));
    bouncerTab.add(new JLabel(MESSAGES.text("preferences.network.field.loginTemplate")));
    bouncerTab.add(genericBouncerLoginTemplate, MigConstraints.growXMinWidth0());
    bouncerTab.add(genericBouncerTemplateHelp, MigConstraints.span2GrowXMinWidth0Wrap());

    JPanel floodTab = new JPanel(MigLayouts.twoColumnForm(12, MigLayouts.rows(2, 6)));
    floodTab.setOpaque(false);
    JTextArea floodBlurb = PreferencesUiSupport.subtleInfoText();
    floodBlurb.setText(MESSAGES.text("preferences.network.flood.blurb"));
    floodTab.add(floodBlurb, MigConstraints.span2GrowXMinWidth0Wrap());
    JCheckBox floodEnabled = new JCheckBox(MESSAGES.text("preferences.network.flood.enabled"));
    floodEnabled.setName("ircRateLimitingEnabled");
    floodEnabled.setSelected(floodSettings.enabled());
    floodEnabled.setOpaque(false);
    floodTab.add(floodEnabled, MigConstraints.span2GrowXMinWidth0Wrap());
    JSpinner commandIntervalMs =
        PreferencesUiSupport.numberSpinner(
            (int) floodSettings.commandIntervalMs(), 100, 10_000, 100, closeables);
    commandIntervalMs.setName("ircCommandIntervalMs");
    commandIntervalMs.setEnabled(floodEnabled.isSelected());
    floodEnabled.addItemListener(e -> commandIntervalMs.setEnabled(floodEnabled.isSelected()));
    JSpinner autoJoinDelaySeconds =
        PreferencesUiSupport.numberSpinner(
            (int) (floodSettings.autoJoinDelayMs() / 1000), 0, 120, 1, closeables);
    autoJoinDelaySeconds.setName("ircAutoJoinDelaySeconds");
    floodTab.add(new JLabel(MESSAGES.text("preferences.network.flood.commandInterval")));
    floodTab.add(commandIntervalMs, MigConstraints.width(110));
    floodTab.add(new JLabel(MESSAGES.text("preferences.network.flood.autoJoinDelay")));
    floodTab.add(autoJoinDelaySeconds, MigConstraints.width(110));
    JTextArea floodReconnect = PreferencesUiSupport.subtleInfoText();
    floodReconnect.setText(MESSAGES.text("preferences.network.flood.reconnect"));
    floodTab.add(floodReconnect, MigConstraints.span2GrowXMinWidth0Wrap());

    JTabbedPane networkTabs = new JTabbedPane();
    networkTabs.addTab(
        MESSAGES.text("preferences.network.tab.proxy"), PreferencesUiSupport.padSubTab(proxyTab));
    networkTabs.addTab(
        MESSAGES.text("preferences.network.tab.tls"), PreferencesUiSupport.padSubTab(tlsTab));
    networkTabs.addTab(
        MESSAGES.text("preferences.network.tab.heartbeat"),
        PreferencesUiSupport.padSubTab(heartbeatTab));
    networkTabs.addTab(
        MESSAGES.text("preferences.network.tab.flood"), PreferencesUiSupport.padSubTab(floodTab));
    networkTabs.addTab(
        MESSAGES.text("preferences.network.tab.bouncer"),
        PreferencesUiSupport.padSubTab(bouncerTab));

    JPanel networkIntro =
        new JPanel(
            MigLayouts.fillXWrap(
                12, 2, MigLayoutConstraints.GROW_FILL_GAP_6_TRAILING, MigLayoutConstraints.ROW));
    networkIntro.setOpaque(false);
    networkIntro.add(
        PreferencesUiSupport.tabTitle(MESSAGES.text("preferences.network.title")),
        MigConstraints.growXMinWidth0());
    networkIntro.add(
        PreferencesUiSupport.whyHelpButton(
            MESSAGES.text("preferences.network.help.title"),
            MESSAGES.text("preferences.network.help.message")),
        MigConstraints.alignXRight());

    networkPanel.add(networkIntro, MigConstraints.growXMinWidth0Wrap());
    networkPanel.add(networkTabs, MigConstraints.growPushMinWidth0());

    ProxyControls proxyControls =
        new ProxyControls(
            proxyEnabled,
            proxyHost,
            proxyPort,
            proxyRemoteDns,
            proxyUsername,
            proxyPassword,
            clearPassword,
            connectTimeoutSeconds,
            readTimeoutSeconds);
    HeartbeatControls heartbeatControls =
        new HeartbeatControls(
            heartbeatEnabled, heartbeatCheckPeriodSeconds, heartbeatTimeoutSeconds);
    BouncerControls bouncerControls =
        new BouncerControls(genericBouncerPreferLoginHint, genericBouncerLoginTemplate);

    return new NetworkConnectionPanelControls(
        proxyControls,
        heartbeatControls,
        new FloodProtectionControls(floodEnabled, commandIntervalMs, autoJoinDelaySeconds),
        bouncerControls,
        trustAllTlsCertificates,
        networkPanel);
  }
}
