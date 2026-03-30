package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.AutoJoinEntryCodec;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.SocksProxySocketFactory;
import cafe.woden.ircclient.net.SocksProxySslSocketFactory;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import net.miginfocom.swing.MigLayout;

/** Add/edit a single IRC server configuration. */
public class ServerEditorDialog extends JDialog {

  private Optional<IrcProperties.Server> result = Optional.empty();
  private final String seedBackendId;
  private final ServerEditorBackendProfiles backendProfiles;
  private final JComboBox<String> backendCombo;

  private final JTextField idField = new JTextField();
  private final JTextField hostField = new JTextField();
  private final JTextField portField = new JTextField();
  private final JCheckBox tlsBox = new JCheckBox("Use TLS (SSL)");
  private final JPasswordField serverPassField = new JPasswordField();
  private final JCheckBox autoConnectOnStartBox =
      new JCheckBox("Auto-connect this server on startup");
  private final JLabel hostLabel = new JLabel("Host");
  private final JLabel serverPasswordLabel = new JLabel("Server password");
  private final JLabel connectionBackendHintLabel = new JLabel(" ");

  private final JTextField nickField = new JTextField();
  private final JTextField loginField = new JTextField();
  private final JTextField realNameField = new JTextField();
  private final JLabel nickLabel = new JLabel("Nick");
  private final JLabel loginLabel = new JLabel("Login/Ident");
  private final JLabel realNameLabel = new JLabel("Real name");

  private static final String AUTH_CARD_DISABLED = "auth-disabled";
  private static final String AUTH_CARD_SASL = "auth-sasl";
  private static final String AUTH_CARD_NICKSERV = "auth-nickserv";
  private static final String AUTH_DISABLED_HINT_TEXT =
      "No authentication on connect. Use this for networks that don't require account auth.";
  private static final String SASL_CONTINUE_ON_FAILURE_TEXT =
      "Stay connected if SASL authentication fails";
  private static final String NICKSERV_DELAY_JOIN_TEXT =
      "Delay channel auto-join until identification succeeds";

  private final JComboBox<ServerEditorAuthMode> authModeCombo =
      new JComboBox<>(
          new ServerEditorAuthMode[] {
            ServerEditorAuthMode.DISABLED, ServerEditorAuthMode.SASL, ServerEditorAuthMode.NICKSERV
          });
  private final JLabel authModeLabel = new JLabel("Method");
  private final JPanel authModeCardPanel = new JPanel(new CardLayout());
  private final JLabel authDisabledHintLabel = new JLabel();
  private final JLabel matrixAuthModeLabel = new JLabel("Matrix auth");
  private final JLabel matrixAuthUserLabel = new JLabel("Username");
  private final JComboBox<ServerEditorMatrixAuthMode> matrixAuthModeCombo =
      new JComboBox<>(
          new ServerEditorMatrixAuthMode[] {
            ServerEditorMatrixAuthMode.ACCESS_TOKEN, ServerEditorMatrixAuthMode.USERNAME_PASSWORD
          });
  private final JTextField matrixAuthUserField = new JTextField();
  private final JLabel matrixAuthHintLabel = new JLabel();

  private final JTextField saslUserField = new JTextField();

  /**
   * SASL secret (password / key material). Use a password field so we don't echo secrets in plain
   * text.
   */
  private final JPasswordField saslPassField = new JPasswordField();

  private final JComboBox<String> saslMechanism =
      new JComboBox<>(
          new String[] {
            "AUTO", "PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-1", "EXTERNAL", "ECDSA-NIST256P-CHALLENGE"
          });
  private final JCheckBox saslContinueOnFailureBox = new JCheckBox(SASL_CONTINUE_ON_FAILURE_TEXT);

  private final JLabel saslHintLabel = new JLabel();
  private final JTextField nickservServiceField = new JTextField();
  private final JPasswordField nickservPassField = new JPasswordField();
  private final JCheckBox nickservDelayJoinBox = new JCheckBox(NICKSERV_DELAY_JOIN_TEXT);
  private final JLabel nickservHintLabel = new JLabel();

  private final JTextArea autoJoinArea = new JTextArea(8, 30);
  private final JTextArea autoJoinPmArea = new JTextArea(6, 30);
  private final JTextArea performArea = new JTextArea(8, 30);

  // Per-server proxy override
  private final JCheckBox proxyOverrideBox = new JCheckBox("Override proxy for this server");
  private final JCheckBox proxyEnabledBox = new JCheckBox("Use SOCKS5 proxy");
  private final JTextField proxyHostField = new JTextField();
  private final JTextField proxyPortField = new JTextField();
  private final JCheckBox proxyRemoteDnsBox =
      new JCheckBox("Remote DNS (resolve hostnames via proxy)");
  private final JTextField proxyUserField = new JTextField();
  private final JPasswordField proxyPassField = new JPasswordField();
  private final JTextField proxyConnectTimeoutMsField = new JTextField();
  private final JTextField proxyReadTimeoutMsField = new JTextField();
  private final JButton proxyTestBtn = new JButton("Test…");
  private final JLabel proxyHintLabel = new JLabel();
  private final JLabel proxyStatusLabel = new JLabel(" ");

  private final JButton saveBtn = new JButton("Save");
  private final JButton cancelBtn = new JButton("Cancel");

  /**
   * When a proxy test succeeds, we paint "success" outlines on relevant fields. We only keep the
   * success state while the tested inputs remain unchanged.
   */
  private ProxyTestSnapshot lastProxyTestOk;

  private boolean portAuto = true;
  private boolean updatingPortProgrammatically = false;

  private record ProxyTestSnapshot(
      boolean override,
      boolean proxyEnabled,
      String proxyHost,
      String proxyPort,
      String proxyUser,
      int proxyPassHash,
      String connectTimeoutMs,
      String readTimeoutMs) {
    static ProxyTestSnapshot capture(ServerEditorDialog d) {
      return new ProxyTestSnapshot(
          d.proxyOverrideBox.isSelected(),
          d.proxyEnabledBox.isSelected(),
          trim(d.proxyHostField.getText()),
          trim(d.proxyPortField.getText()),
          trim(d.proxyUserField.getText()),
          java.util.Arrays.hashCode(d.proxyPassField.getPassword()),
          trim(d.proxyConnectTimeoutMsField.getText()),
          trim(d.proxyReadTimeoutMsField.getText()));
    }
  }

  public ServerEditorDialog(Window parent, String title, IrcProperties.Server seed) {
    this(parent, title, seed, true, ServerEditorBackendProfiles.builtIns());
  }

  public ServerEditorDialog(
      Window parent, String title, IrcProperties.Server seed, boolean autoConnectOnStart) {
    this(parent, title, seed, autoConnectOnStart, ServerEditorBackendProfiles.builtIns());
  }

  ServerEditorDialog(
      Window parent,
      String title,
      IrcProperties.Server seed,
      boolean autoConnectOnStart,
      ServerEditorBackendProfiles backendProfiles) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.backendProfiles = Objects.requireNonNull(backendProfiles, "backendProfiles");
    this.seedBackendId =
        seed != null
            ? backendProfile(seed.backendId()).backendId()
            : backendProfiles.defaultBackendId();
    this.backendCombo =
        new JComboBox<>(backendProfiles.selectableBackendIds(seedBackendId).toArray(String[]::new));
    backendCombo.setSelectedItem(seedBackendId);
    backendCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof String backendId) {
              label.setText(backendLabel(backendId));
            }
            return label;
          }
        });
    // Keep combo sizing stable so short selections do not collapse Auth-tab field widths.
    authModeCombo.setPrototypeDisplayValue(ServerEditorAuthMode.NICKSERV);
    matrixAuthModeCombo.setPrototypeDisplayValue(ServerEditorMatrixAuthMode.USERNAME_PASSWORD);
    saslMechanism.setPrototypeDisplayValue("ECDSA-NIST256P-CHALLENGE");
    nickservDelayJoinBox.setSelected(true);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout(10, 10));
    ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Connection", buildConnectionPanel());
    tabs.addTab("Identity", buildIdentityPanel());
    tabs.addTab("Auth", buildSaslPanel());
    tabs.addTab("Auto-Join", buildAutoJoinPanel());
    tabs.addTab("Perform", buildPerformPanel());
    tabs.addTab("Proxy", wrapScrollTab(buildProxyPanel()));
    add(tabs, BorderLayout.CENTER);

    JPanel actions = new JPanel();
    actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
    actions.add(javax.swing.Box.createHorizontalGlue());
    saveBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");
    cancelBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "default");
    saveBtn.setIcon(SvgIcons.action("check", 16));
    saveBtn.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    cancelBtn.setIcon(SvgIcons.action("close", 16));
    cancelBtn.setDisabledIcon(SvgIcons.actionDisabled("close", 16));
    actions.add(cancelBtn);
    actions.add(javax.swing.Box.createHorizontalStrut(8));
    actions.add(saveBtn);
    add(actions, BorderLayout.SOUTH);

    cancelBtn.addActionListener(
        e -> {
          result = Optional.empty();
          dispose();
        });
    saveBtn.addActionListener(e -> onSave());

    // Seed values
    if (seed != null) {
      idField.setText(Objects.toString(seed.id(), ""));
      hostField.setText(Objects.toString(seed.host(), ""));
      portField.setText(String.valueOf(seed.port()));
      tlsBox.setSelected(seed.tls());
      serverPassField.setText(Objects.toString(seed.serverPassword(), ""));

      nickField.setText(Objects.toString(seed.nick(), ""));
      loginField.setText(Objects.toString(seed.login(), ""));
      realNameField.setText(Objects.toString(seed.realName(), ""));

      if (seed.sasl() != null) {
        saslUserField.setText(Objects.toString(seed.sasl().username(), ""));
        saslPassField.setText(Objects.toString(seed.sasl().password(), ""));
        saslMechanism.setSelectedItem(Objects.toString(seed.sasl().mechanism(), "PLAIN"));
        saslContinueOnFailureBox.setSelected(
            !Boolean.TRUE.equals(seed.sasl().disconnectOnFailure()));
      }
      if (seed.nickserv() != null) {
        nickservServiceField.setText(Objects.toString(seed.nickserv().service(), "NickServ"));
        nickservPassField.setText(Objects.toString(seed.nickserv().password(), ""));
        nickservDelayJoinBox.setSelected(
            seed.nickserv().delayJoinUntilIdentified() == null
                || seed.nickserv().delayJoinUntilIdentified());
      }
      setAuthMode(seedAuthMode(seed));
      ServerEditorBackendProfile seedProfile = backendProfile(seed.backendId());
      setMatrixAuthMode(ServerEditorAuthPolicy.seedMatrixAuthMode(seedProfile, seed));
      if (seedProfile.matrixAuthSupported()
          && ServerEditorAuthPolicy.isMatrixPasswordAuthMode(seed.sasl())) {
        matrixAuthUserField.setText(Objects.toString(seed.sasl().username(), ""));
        serverPassField.setText(Objects.toString(seed.sasl().password(), ""));
      }

      List<String> autoJoinSeed = seed.autoJoin() == null ? List.of() : seed.autoJoin();
      autoJoinArea.setText(String.join("\n", AutoJoinEntryCodec.channelEntries(autoJoinSeed)));
      autoJoinPmArea.setText(
          String.join("\n", AutoJoinEntryCodec.privateMessageNicks(autoJoinSeed)));
      List<String> performSeed = seed.perform() == null ? List.of() : seed.perform();
      performArea.setText(String.join("\n", performSeed));
      portAuto = false; // user likely set explicitly

      seedProxy(seed.proxy());
    } else {
      tlsBox.setSelected(true);
      portField.setText("6697");
      portAuto = true;
      setAuthMode(ServerEditorAuthMode.DISABLED);
      setMatrixAuthMode(ServerEditorMatrixAuthMode.ACCESS_TOKEN);

      seedProxy(null);
    }
    autoConnectOnStartBox.setSelected(autoConnectOnStart);

    // Placeholders / styling
    applyFieldStyle(idField, "libera");
    applyFieldStyle(hostField, "irc.example.net");
    applyFieldStyle(portField, "6697");
    applyFieldStyle(serverPassField, "(optional)");
    applyFieldStyle(nickField, "IRCafeUser");
    applyFieldStyle(loginField, "ircafe");
    applyFieldStyle(realNameField, "IRCafe User");
    applyFieldStyle(matrixAuthUserField, "alice");
    applyFieldStyle(saslUserField, "account");
    applyFieldStyle(saslPassField, "password / key");
    applyFieldStyle(nickservServiceField, "NickServ");
    applyFieldStyle(nickservPassField, "password");
    // FlatLaf: show the standard "reveal" (eye) button inside password fields.
    // Using the string key avoids any compile-time dependency on FlatLaf constants.
    serverPassField.putClientProperty("JPasswordField.showRevealButton", true);
    appendStyle(serverPassField, "showRevealButton:true");
    saslPassField.putClientProperty("JPasswordField.showRevealButton", true);
    // FlatLaf also supports a STYLE flag; keep both for compatibility.
    appendStyle(saslPassField, "showRevealButton:true");
    nickservPassField.putClientProperty("JPasswordField.showRevealButton", true);
    appendStyle(nickservPassField, "showRevealButton:true");
    applyFieldStyle(proxyHostField, "127.0.0.1");
    applyFieldStyle(proxyPortField, "1080");
    applyFieldStyle(proxyUserField, "(optional)");
    applyFieldStyle(proxyPassField, "(optional)");
    // FlatLaf: show the standard "reveal" (eye) button inside password fields.
    proxyPassField.putClientProperty("JPasswordField.showRevealButton", true);
    // FlatLaf also supports a STYLE flag; keep both for compatibility.
    appendStyle(proxyPassField, "showRevealButton:true");
    applyFieldStyle(proxyConnectTimeoutMsField, "20000");
    applyFieldStyle(proxyReadTimeoutMsField, "30000");
    appendStyle(backendCombo, "arc:10");
    appendStyle(matrixAuthModeCombo, "arc:10");
    autoJoinArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#channel\n#another");
    autoJoinPmArea.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, "NickServ\nfriend_nick");
    performArea.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        "/msg NickServ IDENTIFY password\n"
            + "/join #project\n"
            + "/quote MONITOR +friend\n"
            + "/sleep 1000");

    // Auto-update default port when toggling TLS, if the user hasn't customized it.
    tlsBox.addActionListener(e -> maybeAdjustPortForBackendAndTls());
    portField
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              @Override
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                if (!updatingPortProgrammatically) {
                  portAuto = false;
                }
                updateValidation();
              }

              @Override
              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                if (!updatingPortProgrammatically) {
                  portAuto = false;
                }
                updateValidation();
              }

              @Override
              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                if (!updatingPortProgrammatically) {
                  portAuto = false;
                }
                updateValidation();
              }
            });

    authModeCombo.addActionListener(e -> updateAuthModeUi());
    matrixAuthModeCombo.addActionListener(
        e -> {
          updateMatrixAuthUi();
          updateValidation();
        });
    saslMechanism.addActionListener(e -> updateAuthModeUi());
    nickservDelayJoinBox.addActionListener(e -> updateValidation());
    backendCombo.addActionListener(
        e -> {
          maybeAdjustPortForBackendAndTls();
          updateBackendUi();
          updateAuthModeUi();
        });
    updateAuthModeUi();
    updateBackendUi();

    proxyOverrideBox.addActionListener(e -> updateProxyEnabled());
    proxyEnabledBox.addActionListener(e -> updateProxyEnabled());
    updateProxyEnabled();

    // Live validation outlines + Save button state.
    Runnable validate = this::updateValidation;
    javax.swing.event.DocumentListener vdl = new SimpleDocListener(validate);
    idField.getDocument().addDocumentListener(vdl);
    hostField.getDocument().addDocumentListener(vdl);
    // portField already has a listener for portAuto; it also calls updateValidation().
    serverPassField.getDocument().addDocumentListener(vdl);
    matrixAuthUserField.getDocument().addDocumentListener(vdl);
    nickField.getDocument().addDocumentListener(vdl);
    loginField.getDocument().addDocumentListener(vdl);
    realNameField.getDocument().addDocumentListener(vdl);

    saslUserField.getDocument().addDocumentListener(vdl);
    saslPassField.getDocument().addDocumentListener(vdl);
    nickservServiceField.getDocument().addDocumentListener(vdl);
    nickservPassField.getDocument().addDocumentListener(vdl);

    proxyHostField.getDocument().addDocumentListener(vdl);
    proxyPortField.getDocument().addDocumentListener(vdl);
    proxyUserField.getDocument().addDocumentListener(vdl);
    proxyPassField.getDocument().addDocumentListener(vdl);
    proxyConnectTimeoutMsField.getDocument().addDocumentListener(vdl);
    proxyReadTimeoutMsField.getDocument().addDocumentListener(vdl);

    updateValidation();

    setPreferredSize(new Dimension(640, 520));
    pack();
    setLocationRelativeTo(parent);
  }

  private static JComponent wrapScrollTab(JComponent content) {
    JScrollPane scroll = new JScrollPane(content);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    return scroll;
  }

  private JPanel buildProxyPanel() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    GridBagConstraints g = baseGbc();

    g.gridx = 0;
    g.gridy = 0;
    g.gridwidth = 2;
    g.weightx = 1.0;
    g.fill = GridBagConstraints.HORIZONTAL;
    p.add(proxyOverrideBox, g);

    g.gridy++;
    proxyHintLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    p.add(proxyHintLabel, g);

    g.gridwidth = 1;
    addRow(p, g, 2, "Use proxy", proxyEnabledBox);
    addRow(p, g, 3, "Proxy host", proxyHostField);

    proxyPortField.setColumns(8);
    addRow(p, g, 4, "Proxy port", proxyPortField);
    addRow(p, g, 5, "Remote DNS", proxyRemoteDnsBox);
    addRow(p, g, 6, "Username", proxyUserField);
    addRow(p, g, 7, "Password", proxyPassField);
    addRow(p, g, 8, "Connect timeout (ms)", proxyConnectTimeoutMsField);
    addRow(p, g, 9, "Read timeout (ms)", proxyReadTimeoutMsField);

    // Test row
    JPanel testRow = new JPanel();
    testRow.setLayout(new javax.swing.BoxLayout(testRow, javax.swing.BoxLayout.X_AXIS));
    proxyTestBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "default");
    proxyTestBtn.setIcon(SvgIcons.action("refresh", 16));
    proxyTestBtn.setDisabledIcon(SvgIcons.actionDisabled("refresh", 16));
    testRow.add(proxyTestBtn);
    testRow.add(javax.swing.Box.createHorizontalStrut(10));
    proxyStatusLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    testRow.add(proxyStatusLabel);
    testRow.add(javax.swing.Box.createHorizontalGlue());

    addRow(p, g, 10, "Test", testRow);

    proxyTestBtn.addActionListener(e -> onTestProxy());

    g.gridy = 11;
    g.gridx = 0;
    g.gridwidth = 2;
    g.weighty = 1.0;
    p.add(new JLabel(""), g);

    return p;
  }

  private void seedProxy(IrcProperties.Proxy serverProxy) {
    IrcProperties.Proxy global = NetProxyContext.normalize(NetProxyContext.settings());

    if (serverProxy != null) {
      proxyOverrideBox.setSelected(true);
      proxyEnabledBox.setSelected(serverProxy.enabled());
      proxyHostField.setText(Objects.toString(serverProxy.host(), ""));
      proxyPortField.setText(serverProxy.port() > 0 ? Integer.toString(serverProxy.port()) : "");
      proxyRemoteDnsBox.setSelected(serverProxy.remoteDns());
      proxyUserField.setText(Objects.toString(serverProxy.username(), ""));
      proxyPassField.setText(Objects.toString(serverProxy.password(), ""));
      proxyConnectTimeoutMsField.setText(Long.toString(serverProxy.connectTimeoutMs()));
      proxyReadTimeoutMsField.setText(Long.toString(serverProxy.readTimeoutMs()));
    } else {
      proxyOverrideBox.setSelected(false);
      // Show global values read-only as a hint, but server will inherit.
      proxyEnabledBox.setSelected(global.enabled());
      proxyHostField.setText(Objects.toString(global.host(), ""));
      proxyPortField.setText(global.port() > 0 ? Integer.toString(global.port()) : "");
      proxyRemoteDnsBox.setSelected(global.remoteDns());
      proxyUserField.setText(Objects.toString(global.username(), ""));
      proxyPassField.setText(Objects.toString(global.password(), ""));
      proxyConnectTimeoutMsField.setText(Long.toString(global.connectTimeoutMs()));
      proxyReadTimeoutMsField.setText(Long.toString(global.readTimeoutMs()));
    }

    updateProxyEnabled();
  }

  private void updateProxyEnabled() {
    boolean override = proxyOverrideBox.isSelected();
    IrcProperties.Proxy global = NetProxyContext.normalize(NetProxyContext.settings());

    if (!override) {
      proxyHintLabel.setText(
          global.enabled()
              ? "Inheriting global proxy from Preferences (enabled: "
                  + global.host()
                  + ":"
                  + global.port()
                  + ")"
              : "Inheriting global proxy from Preferences (disabled)");
    } else {
      proxyHintLabel.setText("Override the global proxy for this server.\n");
    }

    proxyEnabledBox.setEnabled(override);

    boolean proxyDetailsEnabled = override && proxyEnabledBox.isSelected();
    proxyHostField.setEnabled(proxyDetailsEnabled);
    proxyPortField.setEnabled(proxyDetailsEnabled);
    proxyRemoteDnsBox.setEnabled(proxyDetailsEnabled);
    proxyUserField.setEnabled(proxyDetailsEnabled);
    proxyPassField.setEnabled(proxyDetailsEnabled);
    proxyConnectTimeoutMsField.setEnabled(override);
    proxyReadTimeoutMsField.setEnabled(override);

    proxyTestBtn.setEnabled(true);

    updateValidation();
  }

  private void onTestProxy() {
    proxyStatusLabel.setText("Testing…");
    proxyTestBtn.setEnabled(false);

    // Clear any previous "success" state while we re-test.
    lastProxyTestOk = null;
    updateValidation();

    final String host = trim(hostField.getText());
    final String portText = trim(portField.getText());
    final boolean tls = tlsBox.isSelected();

    final IrcProperties.Proxy cfg;
    try {
      cfg = resolveProxyForTest();
    } catch (IllegalArgumentException ex) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(
          this, ex.getMessage(), "Invalid proxy settings", JOptionPane.ERROR_MESSAGE);
      return;
    }

    int port;
    try {
      port = Integer.parseInt(portText);
    } catch (Exception e) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(
          this, "Port must be a number", "Invalid server configuration", JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (host.isBlank()) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(
          this, "Host is required", "Invalid server configuration", JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (port <= 0 || port > 65535) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(
          this, "Port must be 1-65535", "Invalid server configuration", JOptionPane.ERROR_MESSAGE);
      return;
    }

    new SwingWorker<TestResult, Void>() {
      @Override
      protected TestResult doInBackground() {
        long start = System.nanoTime();
        try {
          testConnect(host, port, tls, cfg);
          long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
          return TestResult.ok(elapsedMs);
        } catch (Exception e) {
          return TestResult.fail(e);
        }
      }

      @Override
      protected void done() {
        proxyTestBtn.setEnabled(true);
        try {
          TestResult r = get();
          if (r.ok) {
            proxyStatusLabel.setText("OK (" + r.elapsedMs + " ms)");

            // Mark the tested proxy inputs as "known good" until they change.
            lastProxyTestOk = ProxyTestSnapshot.capture(ServerEditorDialog.this);
            updateValidation();

            JOptionPane.showMessageDialog(
                ServerEditorDialog.this,
                "Connection test succeeded.\n\n"
                    + "TLS: "
                    + (tls ? "yes" : "no")
                    + "\n"
                    + "Proxy: "
                    + (cfg.enabled() ? (cfg.host() + ":" + cfg.port()) : "disabled")
                    + "\n"
                    + "Time: "
                    + r.elapsedMs
                    + " ms",
                "Proxy test",
                JOptionPane.INFORMATION_MESSAGE);
          } else {
            proxyStatusLabel.setText("Failed: " + r.shortMessage());
            lastProxyTestOk = null;
            updateValidation();
            JOptionPane.showMessageDialog(
                ServerEditorDialog.this,
                "Connection test failed.\n\n" + r.longMessage(),
                "Proxy test",
                JOptionPane.ERROR_MESSAGE);
          }
        } catch (Exception e) {
          proxyStatusLabel.setText("Failed");
          lastProxyTestOk = null;
          updateValidation();
          JOptionPane.showMessageDialog(
              ServerEditorDialog.this,
              "Connection test failed.\n\n" + e,
              "Proxy test",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private IrcProperties.Proxy resolveProxyForTest() {
    boolean override = proxyOverrideBox.isSelected();
    if (!override) {
      return NetProxyContext.normalize(NetProxyContext.settings());
    }

    long connectMs = parseLongOrDefault(proxyConnectTimeoutMsField.getText(), 20_000);
    long readMs = parseLongOrDefault(proxyReadTimeoutMsField.getText(), 30_000);

    if (!proxyEnabledBox.isSelected()) {
      // Explicitly disable proxy for this server.
      return new IrcProperties.Proxy(false, "", 0, "", "", true, connectMs, readMs);
    }

    String host = trim(proxyHostField.getText());
    int port;
    try {
      port = Integer.parseInt(trim(proxyPortField.getText()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Proxy port must be a number");
    }
    boolean remoteDns = proxyRemoteDnsBox.isSelected();
    String user = trim(proxyUserField.getText());
    String pass = new String(proxyPassField.getPassword());

    return new IrcProperties.Proxy(true, host, port, user, pass, remoteDns, connectMs, readMs);
  }

  private static void testConnect(String host, int port, boolean tls, IrcProperties.Proxy cfg)
      throws Exception {
    long connectTimeoutMs = Math.max(1, cfg.connectTimeoutMs());
    int readTimeoutMs = (int) Math.max(1, Math.min(Integer.MAX_VALUE, cfg.readTimeoutMs()));

    if (cfg.enabled()) {
      // Proxy path
      Socket s;
      if (tls) {
        s =
            new SocksProxySslSocketFactory(cfg, NetTlsContext.sslSocketFactory())
                .createSocket(host, port);
      } else {
        s = new SocksProxySocketFactory(cfg).createSocket(host, port);
      }
      s.setSoTimeout(readTimeoutMs);
      if (s instanceof SSLSocket ssl) {
        ssl.startHandshake();
      }
      s.close();
      return;
    }

    // Direct path: explicitly bypass any JVM-level SOCKS properties.
    Socket tcp = new Socket(Proxy.NO_PROXY);
    tcp.connect(
        new InetSocketAddress(host, port), (int) Math.min(Integer.MAX_VALUE, connectTimeoutMs));
    tcp.setSoTimeout(readTimeoutMs);

    if (!tls) {
      tcp.close();
      return;
    }

    SSLSocketFactory ssl = NetTlsContext.sslSocketFactory();
    try (SSLSocket sock = (SSLSocket) ssl.createSocket(tcp, host, port, true)) {
      sock.setSoTimeout(readTimeoutMs);
      sock.startHandshake();
    }
  }

  private record TestResult(boolean ok, long elapsedMs, Exception err) {
    static TestResult ok(long elapsedMs) {
      return new TestResult(true, elapsedMs, null);
    }

    static TestResult fail(Exception err) {
      return new TestResult(false, 0, err);
    }

    String shortMessage() {
      if (err == null) return "";
      String msg = err.getMessage();
      if (msg == null || msg.isBlank()) msg = err.getClass().getSimpleName();
      return msg;
    }

    String longMessage() {
      if (err == null) return "";
      String msg = err.toString();
      return msg;
    }
  }

  private static long parseLongOrDefault(String s, long dflt) {
    try {
      long v = Long.parseLong(trim(s));
      return v > 0 ? v : dflt;
    } catch (Exception e) {
      return dflt;
    }
  }

  public Optional<IrcProperties.Server> open() {
    setVisible(true);
    return result;
  }

  public boolean autoConnectOnStartSelected() {
    return autoConnectOnStartBox.isSelected();
  }

  private JPanel buildConnectionPanel() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    GridBagConstraints g = baseGbc();

    addRow(p, g, 0, "Server ID", idField);
    addRow(p, g, 1, "Backend", backendCombo);
    addRow(p, g, 2, hostLabel, hostField);

    // Port row with TLS
    JPanel portRow = new JPanel(new GridBagLayout());
    GridBagConstraints pg = new GridBagConstraints();
    pg.insets = new Insets(0, 0, 0, 0);
    pg.gridx = 0;
    pg.gridy = 0;
    pg.weightx = 0.0;
    pg.fill = GridBagConstraints.NONE;
    portField.setColumns(8);
    portRow.add(portField, pg);
    pg.gridx++;
    pg.insets = new Insets(0, 10, 0, 0);
    pg.weightx = 1.0;
    pg.fill = GridBagConstraints.HORIZONTAL;
    portRow.add(tlsBox, pg);

    addRow(p, g, 3, "Port", portRow);
    addRow(p, g, 4, "Startup", autoConnectOnStartBox);
    connectionBackendHintLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    addRow(p, g, 5, "Backend hint", connectionBackendHintLabel);

    g.gridy = 7;
    g.gridx = 0;
    g.gridwidth = 2;
    g.weighty = 1.0;
    p.add(new JLabel(""), g);

    return p;
  }

  private JPanel buildIdentityPanel() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    GridBagConstraints g = baseGbc();
    addRow(p, g, 0, nickLabel, nickField);
    addRow(p, g, 1, loginLabel, loginField);
    addRow(p, g, 2, realNameLabel, realNameField);

    g.gridy = 3;
    g.gridx = 0;
    g.gridwidth = 2;
    g.weighty = 1.0;
    p.add(new JLabel(""), g);
    return p;
  }

  private JPanel buildSaslPanel() {
    JPanel p =
        new JPanel(
            new MigLayout(
                "insets 8, fillx, wrap 2, hidemode 3",
                "[right]12[grow,fill,min:0]",
                "[]6[]6[]6[]8[grow,fill,min:0]"));

    p.add(matrixAuthModeLabel);
    p.add(matrixAuthModeCombo, "growx, wmin 0, wrap");
    p.add(matrixAuthUserLabel);
    p.add(matrixAuthUserField, "growx, wmin 0, wrap");
    p.add(serverPasswordLabel);
    p.add(serverPassField, "growx, wmin 0, wrap");
    p.add(authModeLabel);
    p.add(authModeCombo, "growx, wmin 0, wrap");
    matrixAuthHintLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    matrixAuthHintLabel.setText(" ");
    p.add(matrixAuthHintLabel, "span 2, growx, wmin 0, wrap");

    authModeCardPanel.add(buildAuthDisabledCard(), AUTH_CARD_DISABLED);
    authModeCardPanel.add(buildAuthSaslCard(), AUTH_CARD_SASL);
    authModeCardPanel.add(buildAuthNickservCard(), AUTH_CARD_NICKSERV);
    p.add(authModeCardPanel, "span 2, grow, push, wmin 0");
    return p;
  }

  private JPanel buildAuthDisabledCard() {
    JPanel p = new JPanel(new MigLayout("insets 6 0 0 0, fillx", "[grow,fill,min:0]", "[]"));
    authDisabledHintLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    authDisabledHintLabel.setText(asHtml(AUTH_DISABLED_HINT_TEXT));
    p.add(authDisabledHintLabel, "growx, wmin 0");
    return p;
  }

  private JPanel buildAuthSaslCard() {
    JPanel p =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill,min:0]", "[]6[]6[]6[]8[]push"));
    p.add(new JLabel("Username"));
    p.add(saslUserField, "growx, wmin 0, wrap");
    p.add(new JLabel("Secret"));
    p.add(saslPassField, "growx, wmin 0, wrap");
    p.add(new JLabel("Mechanism"));
    p.add(saslMechanism, "growx, wmin 0, wrap");
    p.add(new JLabel("On failure"), "top");
    p.add(saslContinueOnFailureBox, "growx, wmin 0, wrap");

    saslHintLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    saslHintLabel.setText(" ");
    p.add(saslHintLabel, "span 2, growx, wmin 0, pushy");
    return p;
  }

  private JPanel buildAuthNickservCard() {
    JPanel p =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill,min:0]", "[]6[]6[]8[]push"));
    p.add(new JLabel("Service"));
    p.add(nickservServiceField, "growx, wmin 0, wrap");
    p.add(new JLabel("Password"));
    p.add(nickservPassField, "growx, wmin 0, wrap");
    p.add(new JLabel("Delay auto-join"), "top");
    p.add(nickservDelayJoinBox, "growx, wmin 0, wrap");

    nickservHintLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    nickservHintLabel.setText(" ");
    p.add(nickservHintLabel, "span 2, growx, wmin 0, pushy");
    return p;
  }

  private JPanel buildAutoJoinPanel() {
    JPanel p = new JPanel(new BorderLayout(8, 8));
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    JLabel hint =
        new JLabel(
            "<html>Channels and PM targets restored after connect.<br/>One entry per line.</html>");
    hint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    p.add(hint, BorderLayout.NORTH);

    JPanel center = new JPanel(new java.awt.GridLayout(2, 1, 0, 8));

    JPanel channels = new JPanel(new BorderLayout(6, 6));
    channels.add(new JLabel("Auto-join channels"), BorderLayout.NORTH);
    autoJoinArea.setLineWrap(true);
    autoJoinArea.setWrapStyleWord(true);
    JScrollPane sc = new JScrollPane(autoJoinArea);
    sc.putClientProperty(FlatClientProperties.STYLE, "arc:12;");
    channels.add(sc, BorderLayout.CENTER);
    center.add(channels);

    JPanel pms = new JPanel(new BorderLayout(6, 6));
    pms.add(new JLabel("Auto-open private messages"), BorderLayout.NORTH);
    autoJoinPmArea.setLineWrap(true);
    autoJoinPmArea.setWrapStyleWord(true);
    JScrollPane pmSc = new JScrollPane(autoJoinPmArea);
    pmSc.putClientProperty(FlatClientProperties.STYLE, "arc:12;");
    pms.add(pmSc, BorderLayout.CENTER);
    center.add(pms);

    p.add(center, BorderLayout.CENTER);
    return p;
  }

  private JPanel buildPerformPanel() {
    JPanel p = new JPanel(new BorderLayout(8, 8));
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JLabel hint =
        new JLabel(
            "<html>Run commands automatically after connect.<br/>"
                + "One command per line. Use slash commands (for example: /join, /msg, /quote, /sleep)."
                + "</html>");
    hint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    p.add(hint, BorderLayout.NORTH);

    performArea.setLineWrap(true);
    performArea.setWrapStyleWord(true);
    JScrollPane sc = new JScrollPane(performArea);
    sc.putClientProperty(FlatClientProperties.STYLE, "arc:12;");
    p.add(sc, BorderLayout.CENTER);

    JLabel hint2 =
        new JLabel(
            "<html>Notes: prefer explicit channels in perform commands. "
                + "/sleep accepts milliseconds between commands.</html>");
    hint2.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    p.add(hint2, BorderLayout.SOUTH);

    return p;
  }

  private ServerEditorAuthMode selectedAuthMode() {
    Object selected = authModeCombo.getSelectedItem();
    if (selected instanceof ServerEditorAuthMode mode) return mode;
    return ServerEditorAuthMode.DISABLED;
  }

  private void setAuthMode(ServerEditorAuthMode mode) {
    authModeCombo.setSelectedItem(mode == null ? ServerEditorAuthMode.DISABLED : mode);
  }

  private ServerEditorMatrixAuthMode selectedMatrixAuthMode() {
    Object selected = matrixAuthModeCombo.getSelectedItem();
    if (selected instanceof ServerEditorMatrixAuthMode mode) return mode;
    return ServerEditorMatrixAuthMode.ACCESS_TOKEN;
  }

  private void setMatrixAuthMode(ServerEditorMatrixAuthMode mode) {
    matrixAuthModeCombo.setSelectedItem(
        mode == null ? ServerEditorMatrixAuthMode.ACCESS_TOKEN : mode);
  }

  private static ServerEditorAuthMode seedAuthMode(IrcProperties.Server seed) {
    if (seed == null) return ServerEditorAuthMode.DISABLED;
    boolean saslEnabled = seed.sasl() != null && seed.sasl().enabled();
    boolean nickservEnabled = seed.nickserv() != null && seed.nickserv().enabled();
    if (saslEnabled) return ServerEditorAuthMode.SASL;
    if (nickservEnabled) return ServerEditorAuthMode.NICKSERV;
    return ServerEditorAuthMode.DISABLED;
  }

  private void showAuthCard(ServerEditorAuthMode mode) {
    CardLayout card = (CardLayout) authModeCardPanel.getLayout();
    switch (mode) {
      case SASL -> card.show(authModeCardPanel, AUTH_CARD_SASL);
      case NICKSERV -> card.show(authModeCardPanel, AUTH_CARD_NICKSERV);
      default -> card.show(authModeCardPanel, AUTH_CARD_DISABLED);
    }
  }

  private void updateAuthModeUi() {
    ServerEditorAuthMode mode =
        ServerEditorAuthPolicy.effectiveAuthMode(selectedBackendProfile(), selectedAuthMode());
    if (mode != selectedAuthMode()) {
      authModeCombo.setSelectedItem(mode);
    }
    showAuthCard(mode);
    updateMatrixAuthUi();
    updateSaslEnabled();
    updateNickservEnabled();
    updateValidation();
  }

  private String selectedBackendId() {
    Object selected = backendCombo.getSelectedItem();
    if (selected instanceof String backendId) return backendId;
    return seedBackendId;
  }

  private ServerEditorBackendProfile selectedBackendProfile() {
    return backendProfile(selectedBackendId());
  }

  private ServerEditorBackendProfile backendProfile(String backendId) {
    return backendProfiles.profileForBackendId(backendId);
  }

  private void updateBackendUi() {
    ServerEditorBackendProfile profile = selectedBackendProfile();
    hostLabel.setText(profile.hostLabel());
    serverPasswordLabel.setText(profile.serverPasswordLabel());
    nickLabel.setText(profile.nickLabel());
    loginLabel.setText(profile.loginLabel());
    realNameLabel.setText(profile.realNameLabel());
    tlsBox.setText(profile.tlsToggleLabel());
    connectionBackendHintLabel.setText(profile.connectionHint());
    authModeCombo.setEnabled(profile.directAuthEnabled());
    if (!profile.directAuthEnabled()) {
      authModeCombo.setSelectedItem(ServerEditorAuthMode.DISABLED);
    }
    authDisabledHintLabel.setText(asHtml(profile.authDisabledHint()));
    applyFieldStyle(serverPassField, profile.serverPasswordPlaceholder());
    applyFieldStyle(hostField, profile.hostPlaceholder());
    applyFieldStyle(loginField, profile.loginPlaceholder());
    applyFieldStyle(nickField, profile.nickPlaceholder());
    applyFieldStyle(realNameField, profile.realNamePlaceholder());
    updateMatrixAuthUi();
    updateValidation();
  }

  private void updateMatrixAuthUi() {
    ServerEditorAuthUiPolicy.MatrixUiState state =
        ServerEditorAuthUiPolicy.matrixUiState(selectedBackendProfile(), selectedMatrixAuthMode());

    authModeLabel.setVisible(state.authModeControlsVisible());
    authModeCombo.setVisible(state.authModeControlsVisible());
    authModeCardPanel.setVisible(state.authModeCardVisible());
    matrixAuthModeLabel.setVisible(state.matrixAuthControlsVisible());
    matrixAuthModeCombo.setVisible(state.matrixAuthControlsVisible());
    matrixAuthHintLabel.setVisible(state.matrixAuthControlsVisible());
    matrixAuthUserLabel.setVisible(state.matrixAuthUserVisible());
    matrixAuthUserField.setVisible(state.matrixAuthUserVisible());
    matrixAuthUserField.setEnabled(state.matrixAuthUserEnabled());

    if (!state.matrixAuthControlsVisible()) {
      matrixAuthHintLabel.setText(" ");
      matrixAuthHintLabel.setToolTipText(null);
      java.awt.Container parent = authModeLabel.getParent();
      if (parent != null) {
        parent.revalidate();
        parent.repaint();
      }
      return;
    }

    serverPasswordLabel.setText(state.serverPasswordLabel());
    applyFieldStyle(serverPassField, state.serverPasswordPlaceholder());
    matrixAuthHintLabel.setText(asHtml(state.hint()));
    matrixAuthHintLabel.setToolTipText(state.hint());
    java.awt.Container parent = authModeLabel.getParent();
    if (parent != null) {
      parent.revalidate();
      parent.repaint();
    }
  }

  private void updateSaslEnabled() {
    ServerEditorAuthUiPolicy.SaslUiState state =
        ServerEditorAuthUiPolicy.saslUiState(
            selectedAuthMode(), Objects.toString(saslMechanism.getSelectedItem(), "PLAIN"));

    saslMechanism.setEnabled(state.mechanismEnabled());
    saslContinueOnFailureBox.setEnabled(state.continueOnFailureEnabled());
    saslUserField.setEnabled(state.userEnabled());
    saslPassField.setEnabled(state.secretEnabled());
    saslHintLabel.setText(state.hintVisible() ? asHtml(state.hint()) : " ");
    saslHintLabel.setToolTipText(state.hint());
    saslPassField.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, state.secretPlaceholder());
  }

  private void updateNickservEnabled() {
    ServerEditorAuthUiPolicy.NickservUiState state =
        ServerEditorAuthUiPolicy.nickservUiState(selectedAuthMode());
    nickservServiceField.setEnabled(state.enabled());
    nickservPassField.setEnabled(state.enabled());
    nickservDelayJoinBox.setEnabled(state.enabled());
    nickservHintLabel.setText(state.enabled() ? asHtml(state.hint()) : " ");
    nickservHintLabel.setToolTipText(state.hint());
  }

  // FlatLaf validation outlines.
  private static final String OUTLINE_PROP = "JComponent.outline";
  private static final String OUTLINE_ERROR = "error";
  private static final String OUTLINE_WARNING = "warning";
  private static final String OUTLINE_SUCCESS = "success";

  private static void setOutline(JComponent c, String outline) {
    c.putClientProperty(OUTLINE_PROP, outline);
  }

  private static void clearOutline(JComponent c) {
    c.putClientProperty(OUTLINE_PROP, null);
  }

  private static void setError(JComponent c, boolean on) {
    setOutline(c, on ? OUTLINE_ERROR : null);
  }

  private static void setWarning(JComponent c, boolean on) {
    setOutline(c, on ? OUTLINE_WARNING : null);
  }

  private static void setSuccess(JComponent c, boolean on) {
    Object cur = c.getClientProperty(OUTLINE_PROP);
    if (on) {
      // Only paint success if nothing else (error/warning) is currently displayed.
      if (cur == null) setOutline(c, OUTLINE_SUCCESS);
    } else {
      if (Objects.equals(cur, OUTLINE_SUCCESS)) clearOutline(c);
    }
  }

  private static boolean isValidPort(String s) {
    try {
      int p = Integer.parseInt(trim(s));
      return p > 0 && p <= 65535;
    } catch (Exception e) {
      return false;
    }
  }

  private void updateValidation() {
    boolean ok = true;
    ServerEditorBackendProfile profile = selectedBackendProfile();

    boolean idBad = trim(idField.getText()).isEmpty();
    setError(idField, idBad);
    ok &= !idBad;

    boolean hostBad = trim(hostField.getText()).isEmpty();
    setError(hostField, hostBad);
    ok &= !hostBad;

    boolean portBad = !isValidPort(portField.getText());
    setError(portField, portBad);
    ok &= !portBad;

    ServerEditorMatrixAuthMode matrixAuthMode = selectedMatrixAuthMode();
    ServerEditorAuthPolicy.MatrixValidation matrixValidation =
        ServerEditorAuthPolicy.matrixValidation(
            profile, matrixAuthMode, serverPasswordValue(), matrixAuthUserField.getText());
    setError(serverPassField, matrixValidation.credentialBad());
    ok &= !matrixValidation.credentialBad();

    if (matrixValidation.applicable()) {
      setError(matrixAuthUserField, matrixValidation.usernameBad());
      ok &= !matrixValidation.usernameBad();
    } else {
      clearOutline(matrixAuthUserField);
    }

    boolean loginBad = false;
    setError(loginField, loginBad);
    ok &= !loginBad;

    boolean nickBad = profile.requiresNick() && trim(nickField.getText()).isEmpty();
    setError(nickField, nickBad);
    ok &= !nickBad;

    ServerEditorAuthMode authMode =
        ServerEditorAuthPolicy.effectiveAuthMode(profile, selectedAuthMode());
    ServerEditorAuthPolicy.SaslValidation saslValidation =
        ServerEditorAuthPolicy.saslValidation(
            authMode,
            Objects.toString(saslMechanism.getSelectedItem(), "PLAIN"),
            saslUserField.getText(),
            new String(saslPassField.getPassword()));

    // SASL validation
    if (!saslValidation.applicable()) {
      clearOutline(saslUserField);
      clearOutline(saslPassField);
    } else {
      setError(saslUserField, saslValidation.userBad());
      setError(saslPassField, saslValidation.secretBad());
      ok &= !saslValidation.userBad();
      ok &= !saslValidation.secretBad();
    }

    // NickServ validation
    ServerEditorAuthPolicy.NickservValidation nickservValidation =
        ServerEditorAuthPolicy.nickservValidation(
            authMode, new String(nickservPassField.getPassword()));
    if (!nickservValidation.applicable()) {
      clearOutline(nickservServiceField);
      clearOutline(nickservPassField);
    } else {
      setError(nickservServiceField, false);
      setError(nickservPassField, nickservValidation.passwordBad());
      ok &= !nickservValidation.passwordBad();
    }

    ServerEditorProxyValidationPolicy.ProxyValidation proxyValidation =
        ServerEditorProxyValidationPolicy.validate(
            proxyOverrideBox.isSelected(),
            proxyEnabledBox.isSelected(),
            proxyHostField.getText(),
            proxyPortField.getText(),
            proxyUserField.getText(),
            new String(proxyPassField.getPassword()),
            proxyConnectTimeoutMsField.getText(),
            proxyReadTimeoutMsField.getText());

    // Proxy override validation
    if (!proxyValidation.applicable()) {
      clearOutline(proxyHostField);
      clearOutline(proxyPortField);
      clearOutline(proxyUserField);
      clearOutline(proxyPassField);
      clearOutline(proxyConnectTimeoutMsField);
      clearOutline(proxyReadTimeoutMsField);
    } else {
      setWarning(proxyConnectTimeoutMsField, proxyValidation.connectTimeoutWarning());
      setWarning(proxyReadTimeoutMsField, proxyValidation.readTimeoutWarning());

      if (!proxyValidation.proxyDetailsApplicable()) {
        clearOutline(proxyHostField);
        clearOutline(proxyPortField);
        clearOutline(proxyUserField);
        clearOutline(proxyPassField);
      } else {
        setError(proxyHostField, proxyValidation.hostBad());
        setError(proxyPortField, proxyValidation.portBad());

        ok &= !proxyValidation.hostBad();
        ok &= !proxyValidation.portBad();

        setWarning(proxyUserField, proxyValidation.authMismatch());
        setWarning(proxyPassField, proxyValidation.authMismatch());
      }
    }

    // If a proxy test previously succeeded, keep success outlines only while inputs remain
    // unchanged.
    applyProxyTestSuccessDecoration();

    saveBtn.setEnabled(ok);
    saveBtn.setToolTipText(ok ? null : "Fix highlighted fields to enable Save.");
  }

  private void applyProxyTestSuccessDecoration() {
    // Clear success outlines by default.
    setSuccess(proxyHostField, false);
    setSuccess(proxyPortField, false);
    setSuccess(proxyConnectTimeoutMsField, false);
    setSuccess(proxyReadTimeoutMsField, false);

    if (lastProxyTestOk == null) return;

    ProxyTestSnapshot now = ProxyTestSnapshot.capture(this);
    if (!lastProxyTestOk.equals(now)) {
      lastProxyTestOk = null;
      return;
    }

    // Only paint success when the relevant proxy fields are enabled (per-server override + proxy
    // enabled).
    if (!proxyHostField.isEnabled() || !proxyPortField.isEnabled()) return;

    // If the proxy fields have an error/warning outline, don't overwrite it.
    setSuccess(proxyHostField, true);
    setSuccess(proxyPortField, true);

    // Timeouts are part of the test config too; mark them success if user entered valid values or
    // left blank.
    boolean ctoOk =
        trim(proxyConnectTimeoutMsField.getText()).isEmpty()
            || parseLongOrDefault(proxyConnectTimeoutMsField.getText(), -1) > 0;
    boolean rtoOk =
        trim(proxyReadTimeoutMsField.getText()).isEmpty()
            || parseLongOrDefault(proxyReadTimeoutMsField.getText(), -1) > 0;
    setSuccess(proxyConnectTimeoutMsField, ctoOk);
    setSuccess(proxyReadTimeoutMsField, rtoOk);
  }

  private static final class SimpleDocListener implements javax.swing.event.DocumentListener {
    private final Runnable onChange;

    private SimpleDocListener(Runnable onChange) {
      this.onChange = onChange;
    }

    @Override
    public void insertUpdate(javax.swing.event.DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void removeUpdate(javax.swing.event.DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void changedUpdate(javax.swing.event.DocumentEvent e) {
      onChange.run();
    }
  }

  private void maybeAdjustPortForBackendAndTls() {
    if (!portAuto) return;
    String nextPort = Integer.toString(selectedBackendProfile().defaultPort(tlsBox.isSelected()));
    updatingPortProgrammatically = true;
    try {
      portField.setText(nextPort);
    } finally {
      updatingPortProgrammatically = false;
    }
  }

  private void onSave() {
    try {
      IrcProperties.Server server = buildServer();
      result = Optional.of(server);
      dispose();
    } catch (IllegalArgumentException ex) {
      JOptionPane.showMessageDialog(
          this, ex.getMessage(), "Invalid server configuration", JOptionPane.ERROR_MESSAGE);
    }
  }

  private IrcProperties.Server buildServer() {
    String id = trim(idField.getText());
    if (id.isEmpty()) throw new IllegalArgumentException("Server ID is required");

    String host = trim(hostField.getText());
    if (host.isEmpty()) throw new IllegalArgumentException("Host is required");

    int port;
    try {
      port = Integer.parseInt(trim(portField.getText()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Port must be a number");
    }
    if (port <= 0 || port > 65535) throw new IllegalArgumentException("Port must be 1-65535");

    String backendId = selectedBackendId();
    ServerEditorBackendProfile profile = backendProfile(backendId);

    boolean tls = tlsBox.isSelected();
    String serverPassword = serverPasswordValue();
    ServerEditorMatrixAuthMode matrixAuthMode = selectedMatrixAuthMode();

    String matrixAuthUser = trim(matrixAuthUserField.getText());
    ServerEditorAuthPolicy.validateMatrixCredentials(
        profile, matrixAuthMode, serverPassword, matrixAuthUser);
    if (containsCrlf(serverPassword)) {
      throw new IllegalArgumentException("Server/Core password must not contain newlines");
    }

    String nick = trim(nickField.getText());
    if (profile.requiresNick() && nick.isEmpty()) {
      throw new IllegalArgumentException("Nick is required");
    }

    String login =
        ServerEditorAuthPolicy.resolveLogin(
            profile, loginField.getText(), nick, matrixAuthUser, matrixAuthMode);

    String realName = trim(realNameField.getText());
    if (realName.isEmpty()) realName = nick.isEmpty() ? login : nick;

    if (nick.isEmpty() && !login.isEmpty()) {
      nick = login;
    }

    ServerEditorAuthPolicy.SaslBuildResult saslConfig =
        ServerEditorAuthPolicy.buildSasl(
            profile,
            selectedAuthMode(),
            matrixAuthMode,
            serverPassword,
            matrixAuthUser,
            saslUserField.getText(),
            new String(saslPassField.getPassword()),
            Objects.toString(saslMechanism.getSelectedItem(), "PLAIN"),
            saslContinueOnFailureBox.isSelected());
    serverPassword = saslConfig.serverPassword();
    IrcProperties.Server.Sasl sasl = saslConfig.sasl();
    IrcProperties.Server.Nickserv nickserv =
        ServerEditorAuthPolicy.buildNickserv(
            saslConfig.authMode(),
            nickservServiceField.getText(),
            new String(nickservPassField.getPassword()),
            nickservDelayJoinBox.isSelected());

    List<String> autoJoin =
        ServerEditorCommandListPolicy.autoJoinEntries(
            autoJoinArea.getText(), autoJoinPmArea.getText());
    List<String> perform = ServerEditorCommandListPolicy.performCommands(performArea.getText());
    IrcProperties.Proxy proxyOverride =
        ServerEditorProxyBuildPolicy.buildOverride(
            proxyOverrideBox.isSelected(),
            proxyEnabledBox.isSelected(),
            proxyHostField.getText(),
            proxyPortField.getText(),
            proxyUserField.getText(),
            new String(proxyPassField.getPassword()),
            proxyRemoteDnsBox.isSelected(),
            proxyConnectTimeoutMsField.getText(),
            proxyReadTimeoutMsField.getText());

    return new IrcProperties.Server(
        id,
        host,
        port,
        tls,
        serverPassword,
        nick,
        login,
        realName,
        sasl,
        nickserv,
        autoJoin,
        perform,
        proxyOverride,
        backendId);
  }

  private String serverPasswordValue() {
    // JPasswordField stores secret as char[]; convert only at validation/save boundaries.
    return new String(serverPassField.getPassword());
  }

  private String backendLabel(String backendId) {
    return backendProfile(backendId).displayName();
  }

  private static void applyFieldStyle(JTextField f, String placeholder) {
    f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    f.putClientProperty(FlatClientProperties.STYLE, "arc:10;");
  }

  private static void appendStyle(JComponent c, String styleSnippet) {
    if (c == null || styleSnippet == null) return;
    String snip = styleSnippet.trim();
    if (snip.isBlank()) return;

    Object existing = c.getClientProperty(FlatClientProperties.STYLE);
    String s = existing != null ? existing.toString().trim() : "";
    if (!s.isBlank() && !s.endsWith(";")) s = s + ";";
    s = s + snip;
    if (!s.endsWith(";")) s = s + ";";
    c.putClientProperty(FlatClientProperties.STYLE, s);
  }

  private static GridBagConstraints baseGbc() {
    GridBagConstraints g = new GridBagConstraints();
    g.insets = new Insets(6, 6, 6, 6);
    g.anchor = GridBagConstraints.WEST;
    g.fill = GridBagConstraints.HORIZONTAL;
    g.weightx = 1.0;
    return g;
  }

  private static void addRow(
      JPanel panel, GridBagConstraints g, int row, String label, java.awt.Component field) {
    addRow(panel, g, row, styledLabel(label), field);
  }

  private static void addRow(
      JPanel panel, GridBagConstraints g, int row, JLabel label, java.awt.Component field) {
    g.gridy = row;
    g.gridx = 0;
    g.weightx = 0.0;
    g.gridwidth = 1;
    JLabel l = label == null ? styledLabel("") : label;
    if (l.getClientProperty(FlatClientProperties.STYLE) == null) {
      l.putClientProperty(FlatClientProperties.STYLE, "font:+0");
    }
    panel.add(l, g);

    g.gridx = 1;
    g.weightx = 1.0;
    panel.add(field, g);
  }

  private static JLabel styledLabel(String text) {
    JLabel l = new JLabel(text);
    l.putClientProperty(FlatClientProperties.STYLE, "font:+0");
    return l;
  }

  private static String asHtml(String text) {
    return "<html>" + escapeHtml(text) + "</html>";
  }

  private static String escapeHtml(String text) {
    if (text == null || text.isEmpty()) return "";
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  private static boolean containsCrlf(String s) {
    String v = Objects.toString(s, "");
    return v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
  }
}
