package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.SocksProxySocketFactory;
import cafe.woden.ircclient.net.SocksProxySslSocketFactory;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.localization.UiMessages;
import cafe.woden.ircclient.ui.util.SwingClientProperties;
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

/** Add/edit a single IRC server configuration. */
public class ServerEditorDialog extends JDialog {
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private Optional<IrcProperties.Server> result = Optional.empty();
  private final String seedBackendId;
  private final ServerEditorBackendProfiles backendProfiles;
  private final JComboBox<String> backendCombo;

  private final JTextField idField = new JTextField();
  private final JTextField hostField = new JTextField();
  private final JTextField portField = new JTextField();
  private final JCheckBox tlsBox = new JCheckBox(MESSAGES.text("servers.editor.connection.useTls"));
  private final JCheckBox trustAllCertificatesBox =
      new JCheckBox(MESSAGES.text("servers.editor.connection.trustAllCertificates"));
  private final JPasswordField serverPassField = new JPasswordField();
  private final JCheckBox autoConnectOnStartBox =
      new JCheckBox(MESSAGES.text("servers.editor.connection.autoConnectOnStartup"));
  private final JLabel hostLabel = new JLabel(MESSAGES.text("servers.editor.connection.host"));
  private final JLabel serverPasswordLabel =
      new JLabel(MESSAGES.text("servers.editor.connection.serverPassword"));
  private final JLabel connectionBackendHintLabel = new JLabel(" ");

  private final JTextField nickField = new JTextField();
  private final JTextField loginField = new JTextField();
  private final JTextField realNameField = new JTextField();
  private final JLabel nickLabel = new JLabel(MESSAGES.text("servers.editor.identity.nick"));
  private final JLabel loginLabel = new JLabel(MESSAGES.text("servers.editor.identity.login"));
  private final JLabel realNameLabel =
      new JLabel(MESSAGES.text("servers.editor.identity.realName"));

  private static final String AUTH_CARD_DISABLED = "auth-disabled";
  private static final String AUTH_CARD_SASL = "auth-sasl";
  private static final String AUTH_CARD_NICKSERV = "auth-nickserv";
  private static final String SASL_CONTINUE_ON_FAILURE_TEXT =
      MESSAGES.text("servers.editor.auth.sasl.continueOnFailure");
  private static final String NICKSERV_DELAY_JOIN_TEXT =
      MESSAGES.text("servers.editor.auth.nickserv.delayAutoJoin");

  private final JComboBox<ServerEditorAuthMode> authModeCombo =
      new JComboBox<>(
          new ServerEditorAuthMode[] {
            ServerEditorAuthMode.DISABLED, ServerEditorAuthMode.SASL, ServerEditorAuthMode.NICKSERV
          });
  private final JLabel authModeLabel = new JLabel(MESSAGES.text("servers.editor.auth.method"));
  private final JPanel authModeCardPanel = new JPanel(new CardLayout());
  private final JLabel authDisabledHintLabel = new JLabel();
  private final JLabel matrixAuthModeLabel =
      new JLabel(MESSAGES.text("servers.editor.auth.matrixAuth"));
  private final JLabel matrixAuthUserLabel =
      new JLabel(MESSAGES.text("servers.editor.auth.username"));
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
  private final JCheckBox proxyOverrideBox =
      new JCheckBox(MESSAGES.text("servers.editor.proxy.override"));
  private final JCheckBox proxyEnabledBox =
      new JCheckBox(MESSAGES.text("servers.editor.proxy.useSocks5"));
  private final JTextField proxyHostField = new JTextField();
  private final JTextField proxyPortField = new JTextField();
  private final JCheckBox proxyRemoteDnsBox =
      new JCheckBox(MESSAGES.text("servers.editor.proxy.remoteDns"));
  private final JTextField proxyUserField = new JTextField();
  private final JPasswordField proxyPassField = new JPasswordField();
  private final JTextField proxyConnectTimeoutMsField = new JTextField();
  private final JTextField proxyReadTimeoutMsField = new JTextField();
  private final JButton proxyTestBtn =
      new JButton(MESSAGES.text("servers.editor.proxy.test.ellipsis"));
  private final JLabel proxyHintLabel = new JLabel();
  private final JLabel proxyStatusLabel = new JLabel(" ");

  private final JButton saveBtn = new JButton(MESSAGES.text("common.button.save"));
  private final JButton cancelBtn = new JButton(MESSAGES.text("common.button.cancel"));

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
    tabs.addTab(MESSAGES.text("servers.editor.tab.connection"), buildConnectionPanel());
    tabs.addTab(MESSAGES.text("servers.editor.tab.identity"), buildIdentityPanel());
    tabs.addTab(MESSAGES.text("servers.editor.tab.auth"), buildSaslPanel());
    tabs.addTab(MESSAGES.text("servers.editor.tab.autoJoin"), buildAutoJoinPanel());
    tabs.addTab(MESSAGES.text("servers.editor.tab.perform"), buildPerformPanel());
    tabs.addTab(MESSAGES.text("servers.editor.tab.proxy"), wrapScrollTab(buildProxyPanel()));
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

    if (seed != null) {
      seedFromServer(seed);
    } else {
      seedDefaultValues();
    }
    autoConnectOnStartBox.setSelected(autoConnectOnStart);

    configureFieldStyles();
    installInteractionHandlers();
    refreshBackendAndAuthUi();
    updateProxyEnabled();
    installValidationListeners();
    updateValidation();

    setPreferredSize(new Dimension(640, 520));
    pack();
    setLocationRelativeTo(parent);
  }

  private void seedFromServer(IrcProperties.Server seed) {
    idField.setText(Objects.toString(seed.id(), ""));
    hostField.setText(Objects.toString(seed.host(), ""));
    portField.setText(String.valueOf(seed.port()));
    tlsBox.setSelected(seed.tls());
    trustAllCertificatesBox.setSelected(seed.trustAllCertificates());
    serverPassField.setText(Objects.toString(seed.serverPassword(), ""));

    nickField.setText(Objects.toString(seed.nick(), ""));
    loginField.setText(Objects.toString(seed.login(), ""));
    realNameField.setText(Objects.toString(seed.realName(), ""));

    if (seed.sasl() != null) {
      saslUserField.setText(Objects.toString(seed.sasl().username(), ""));
      saslPassField.setText(Objects.toString(seed.sasl().password(), ""));
      saslMechanism.setSelectedItem(Objects.toString(seed.sasl().mechanism(), "PLAIN"));
      saslContinueOnFailureBox.setSelected(!Boolean.TRUE.equals(seed.sasl().disconnectOnFailure()));
    }
    if (seed.nickserv() != null) {
      nickservServiceField.setText(Objects.toString(seed.nickserv().service(), "NickServ"));
      nickservPassField.setText(Objects.toString(seed.nickserv().password(), ""));
      nickservDelayJoinBox.setSelected(
          seed.nickserv().delayJoinUntilIdentified() == null
              || seed.nickserv().delayJoinUntilIdentified());
    }
    setAuthMode(ServerEditorAuthPolicy.seedAuthMode(seed));
    ServerEditorBackendProfile seedProfile = backendProfile(seed.backendId());
    setMatrixAuthMode(ServerEditorAuthPolicy.seedMatrixAuthMode(seedProfile, seed));
    if (seedProfile.matrixAuthSupported()
        && ServerEditorAuthPolicy.isMatrixPasswordAuthMode(seed.sasl())) {
      matrixAuthUserField.setText(Objects.toString(seed.sasl().username(), ""));
      serverPassField.setText(Objects.toString(seed.sasl().password(), ""));
    }

    autoJoinArea.setText(ServerEditorCommandListPolicy.channelSeedText(seed.autoJoin()));
    autoJoinPmArea.setText(ServerEditorCommandListPolicy.privateMessageSeedText(seed.autoJoin()));
    performArea.setText(ServerEditorCommandListPolicy.performSeedText(seed.perform()));
    portAuto = false;

    seedProxy(seed.proxy());
  }

  private void seedDefaultValues() {
    tlsBox.setSelected(true);
    portField.setText("6697");
    portAuto = true;
    setAuthMode(ServerEditorAuthMode.DISABLED);
    setMatrixAuthMode(ServerEditorMatrixAuthMode.ACCESS_TOKEN);
    seedProxy(null);
  }

  private void configureFieldStyles() {
    String optional = MESSAGES.text("servers.editor.placeholder.optional");
    String password = MESSAGES.text("servers.editor.placeholder.password");

    applyFieldStyle(idField, MESSAGES.text("servers.editor.placeholder.serverId"));
    applyFieldStyle(hostField, MESSAGES.text("servers.editor.placeholder.host"));
    applyFieldStyle(portField, MESSAGES.text("servers.editor.placeholder.port"));
    applyFieldStyle(serverPassField, optional);
    applyFieldStyle(nickField, MESSAGES.text("servers.editor.placeholder.nick"));
    applyFieldStyle(loginField, MESSAGES.text("servers.editor.placeholder.login"));
    applyFieldStyle(realNameField, MESSAGES.text("servers.editor.placeholder.realName"));
    applyFieldStyle(
        matrixAuthUserField, MESSAGES.text("servers.editor.placeholder.matrixUsername"));
    applyFieldStyle(saslUserField, MESSAGES.text("servers.editor.placeholder.saslUsername"));
    applyFieldStyle(saslPassField, MESSAGES.text("servers.editor.placeholder.passwordOrKey"));
    applyFieldStyle(
        nickservServiceField, MESSAGES.text("servers.editor.placeholder.nickservService"));
    applyFieldStyle(nickservPassField, password);
    enablePasswordReveal(serverPassField);
    enablePasswordReveal(saslPassField);
    enablePasswordReveal(nickservPassField);
    applyFieldStyle(proxyHostField, MESSAGES.text("servers.editor.placeholder.proxyHost"));
    applyFieldStyle(proxyPortField, MESSAGES.text("servers.editor.placeholder.proxyPort"));
    applyFieldStyle(proxyUserField, optional);
    applyFieldStyle(proxyPassField, optional);
    enablePasswordReveal(proxyPassField);
    applyFieldStyle(
        proxyConnectTimeoutMsField,
        MESSAGES.text("servers.editor.placeholder.proxyConnectTimeoutMs"));
    applyFieldStyle(
        proxyReadTimeoutMsField, MESSAGES.text("servers.editor.placeholder.proxyReadTimeoutMs"));
    appendStyle(backendCombo, "arc:10");
    appendStyle(matrixAuthModeCombo, "arc:10");
    autoJoinArea.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        MESSAGES.text("servers.editor.placeholder.autoJoinChannels"));
    autoJoinPmArea.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        MESSAGES.text("servers.editor.placeholder.autoJoinPrivateMessages"));
    performArea.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT,
        MESSAGES.text("servers.editor.placeholder.performCommands"));
  }

  private void enablePasswordReveal(JPasswordField field) {
    field.putClientProperty(SwingClientProperties.PASSWORD_FIELD_SHOW_REVEAL_BUTTON, true);
    appendStyle(field, "showRevealButton:true");
  }

  private void installInteractionHandlers() {
    tlsBox.addActionListener(
        e -> {
          maybeAdjustPortForBackendAndTls();
          updateCertificateUi();
        });
    portField.getDocument().addDocumentListener(new PortTrackingListener());

    authModeCombo.addActionListener(e -> refreshAuthPanelUiAndValidation());
    matrixAuthModeCombo.addActionListener(e -> refreshAuthPanelUiAndValidation());
    saslMechanism.addActionListener(e -> refreshAuthPanelUiAndValidation());
    nickservDelayJoinBox.addActionListener(e -> updateValidation());
    backendCombo.addActionListener(
        e -> {
          maybeAdjustPortForBackendAndTls();
          refreshBackendAndAuthUi();
        });

    proxyOverrideBox.addActionListener(e -> updateProxyEnabled());
    proxyEnabledBox.addActionListener(e -> updateProxyEnabled());
  }

  private void installValidationListeners() {
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
  }

  private final class PortTrackingListener implements javax.swing.event.DocumentListener {
    @Override
    public void insertUpdate(javax.swing.event.DocumentEvent e) {
      updatePortAutoAndValidation();
    }

    @Override
    public void removeUpdate(javax.swing.event.DocumentEvent e) {
      updatePortAutoAndValidation();
    }

    @Override
    public void changedUpdate(javax.swing.event.DocumentEvent e) {
      updatePortAutoAndValidation();
    }

    private void updatePortAutoAndValidation() {
      if (!updatingPortProgrammatically) {
        portAuto = false;
      }
      updateValidation();
    }
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
    addRow(p, g, 2, MESSAGES.text("servers.editor.proxy.useProxy"), proxyEnabledBox);
    addRow(p, g, 3, MESSAGES.text("servers.editor.proxy.host"), proxyHostField);

    proxyPortField.setColumns(8);
    addRow(p, g, 4, MESSAGES.text("servers.editor.proxy.port"), proxyPortField);
    addRow(p, g, 5, MESSAGES.text("servers.editor.proxy.remoteDns.label"), proxyRemoteDnsBox);
    addRow(p, g, 6, MESSAGES.text("servers.editor.auth.username"), proxyUserField);
    addRow(p, g, 7, MESSAGES.text("servers.editor.auth.password"), proxyPassField);
    addRow(
        p,
        g,
        8,
        MESSAGES.text("servers.editor.proxy.connectTimeoutMs"),
        proxyConnectTimeoutMsField);
    addRow(p, g, 9, MESSAGES.text("servers.editor.proxy.readTimeoutMs"), proxyReadTimeoutMsField);

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

    addRow(p, g, 10, MESSAGES.text("servers.editor.proxy.test"), testRow);

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
    ServerEditorProxySeedPolicy.ProxySeedState state =
        ServerEditorProxySeedPolicy.seedState(serverProxy, global);

    proxyOverrideBox.setSelected(state.overrideSelected());
    proxyEnabledBox.setSelected(state.proxyEnabled());
    proxyHostField.setText(state.host());
    proxyPortField.setText(state.portText());
    proxyRemoteDnsBox.setSelected(state.remoteDns());
    proxyUserField.setText(state.username());
    proxyPassField.setText(state.password());
    proxyConnectTimeoutMsField.setText(state.connectTimeoutMsText());
    proxyReadTimeoutMsField.setText(state.readTimeoutMsText());

    updateProxyEnabled();
  }

  private void updateProxyEnabled() {
    IrcProperties.Proxy global = NetProxyContext.normalize(NetProxyContext.settings());
    ServerEditorProxyUiPolicy.ProxyUiState state =
        ServerEditorProxyUiPolicy.uiState(
            proxyOverrideBox.isSelected(), proxyEnabledBox.isSelected(), global);

    proxyHintLabel.setText(state.hint());
    proxyEnabledBox.setEnabled(state.proxyEnabledToggleEnabled());
    proxyHostField.setEnabled(state.proxyDetailsEnabled());
    proxyPortField.setEnabled(state.proxyDetailsEnabled());
    proxyRemoteDnsBox.setEnabled(state.remoteDnsEnabled());
    proxyUserField.setEnabled(state.proxyDetailsEnabled());
    proxyPassField.setEnabled(state.proxyDetailsEnabled());
    proxyConnectTimeoutMsField.setEnabled(state.connectTimeoutEnabled());
    proxyReadTimeoutMsField.setEnabled(state.readTimeoutEnabled());

    proxyTestBtn.setEnabled(true);

    updateValidation();
  }

  private void onTestProxy() {
    proxyStatusLabel.setText(MESSAGES.text("servers.editor.proxy.status.testing"));
    proxyTestBtn.setEnabled(false);

    // Clear any previous "success" state while we re-test.
    lastProxyTestOk = null;
    updateValidation();

    final boolean tls = tlsBox.isSelected();
    final boolean trustAllCertificates = trustAllCertificatesBox.isSelected();

    final IrcProperties.Proxy cfg;
    try {
      cfg = resolveProxyForTest();
    } catch (IllegalArgumentException ex) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(
          this,
          ex.getMessage(),
          MESSAGES.text("servers.editor.proxy.invalidSettings.title"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    final ServerEditorConnectionPolicy.ServerEndpoint endpoint;
    try {
      endpoint =
          ServerEditorConnectionPolicy.parseEndpoint(hostField.getText(), portField.getText());
    } catch (IllegalArgumentException ex) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(
          this,
          ex.getMessage(),
          MESSAGES.text("servers.editor.validation.invalidServer.title"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    new SwingWorker<TestResult, Void>() {
      @Override
      protected TestResult doInBackground() {
        long start = System.nanoTime();
        try {
          testConnect(endpoint.host(), endpoint.port(), tls, trustAllCertificates, cfg);
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
            handleSuccessfulProxyTest(tls, cfg, r.elapsedMs);
          } else {
            handleFailedProxyTest(r.shortMessage(), r.longMessage());
          }
        } catch (Exception e) {
          handleUnexpectedProxyTestFailure(e);
        }
      }
    }.execute();
  }

  private void handleSuccessfulProxyTest(boolean tls, IrcProperties.Proxy cfg, long elapsedMs) {
    ServerEditorProxyTestPresentationPolicy.ProxyTestSuccessPresentation presentation =
        ServerEditorProxyTestPresentationPolicy.successPresentation(tls, cfg, elapsedMs);
    proxyStatusLabel.setText(presentation.statusText());

    // Mark the tested proxy inputs as "known good" until they change.
    lastProxyTestOk = ProxyTestSnapshot.capture(this);
    updateValidation();

    JOptionPane.showMessageDialog(
        this,
        presentation.dialogMessage(),
        MESSAGES.text("servers.editor.proxy.test.title"),
        JOptionPane.INFORMATION_MESSAGE);
  }

  private void handleFailedProxyTest(String shortMessage, String longMessage) {
    ServerEditorProxyTestPresentationPolicy.ProxyTestFailurePresentation presentation =
        ServerEditorProxyTestPresentationPolicy.failurePresentation(shortMessage, longMessage);
    proxyStatusLabel.setText(presentation.statusText());
    lastProxyTestOk = null;
    updateValidation();
    JOptionPane.showMessageDialog(
        this,
        presentation.dialogMessage(),
        MESSAGES.text("servers.editor.proxy.test.title"),
        JOptionPane.ERROR_MESSAGE);
  }

  private void handleUnexpectedProxyTestFailure(Exception error) {
    ServerEditorProxyTestPresentationPolicy.ProxyTestFailurePresentation presentation =
        ServerEditorProxyTestPresentationPolicy.unexpectedFailurePresentation(error.toString());
    proxyStatusLabel.setText(presentation.statusText());
    lastProxyTestOk = null;
    updateValidation();
    JOptionPane.showMessageDialog(
        this,
        presentation.dialogMessage(),
        MESSAGES.text("servers.editor.proxy.test.title"),
        JOptionPane.ERROR_MESSAGE);
  }

  private IrcProperties.Proxy resolveProxyForTest() {
    IrcProperties.Proxy global = NetProxyContext.normalize(NetProxyContext.settings());
    IrcProperties.Proxy override =
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
    return override != null ? override : global;
  }

  static void testConnect(
      String host, int port, boolean tls, boolean trustAllCertificates, IrcProperties.Proxy cfg)
      throws Exception {
    long connectTimeoutMs = Math.max(1, cfg.connectTimeoutMs());
    int readTimeoutMs = (int) Math.max(1, Math.min(Integer.MAX_VALUE, cfg.readTimeoutMs()));

    if (cfg.enabled()) {
      // Proxy path
      try (Socket s =
          tls
              ? new SocksProxySslSocketFactory(
                      cfg, NetTlsContext.sslSocketFactory(trustAllCertificates))
                  .createSocket(host, port)
              : new SocksProxySocketFactory(cfg).createSocket(host, port)) {
        s.setSoTimeout(readTimeoutMs);
        if (s instanceof SSLSocket ssl) {
          ssl.startHandshake();
        }
      }
      return;
    }

    // Direct path: explicitly bypass any JVM-level SOCKS properties.
    try (Socket tcp = new Socket(Proxy.NO_PROXY)) {
      tcp.connect(
          new InetSocketAddress(host, port), (int) Math.min(Integer.MAX_VALUE, connectTimeoutMs));
      tcp.setSoTimeout(readTimeoutMs);
      if (!tls) return;

      SSLSocketFactory ssl = NetTlsContext.sslSocketFactory(trustAllCertificates);
      try (SSLSocket sock = (SSLSocket) ssl.createSocket(tcp, host, port, true)) {
        sock.setSoTimeout(readTimeoutMs);
        sock.startHandshake();
      }
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

    addRow(p, g, 0, MESSAGES.text("servers.editor.connection.serverId"), idField);
    addRow(p, g, 1, MESSAGES.text("servers.editor.connection.backend"), backendCombo);
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

    addRow(p, g, 3, MESSAGES.text("servers.editor.connection.port"), portRow);
    trustAllCertificatesBox.setToolTipText(
        MESSAGES.text("servers.editor.connection.trustAllCertificates.tooltip"));
    addRow(
        p, g, 4, MESSAGES.text("servers.editor.connection.certificates"), trustAllCertificatesBox);
    addRow(p, g, 5, MESSAGES.text("servers.editor.connection.startup"), autoConnectOnStartBox);
    connectionBackendHintLabel.putClientProperty(
        FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    addRow(
        p,
        g,
        6,
        MESSAGES.text("servers.editor.connection.backendHint"),
        connectionBackendHintLabel);

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
    return ServerEditorAuthTabBuilder.build(
        new ServerEditorAuthTabBuilder.AuthTabWidgets(
            matrixAuthModeLabel,
            matrixAuthModeCombo,
            matrixAuthUserLabel,
            matrixAuthUserField,
            serverPasswordLabel,
            serverPassField,
            authModeLabel,
            authModeCombo,
            matrixAuthHintLabel,
            authModeCardPanel,
            AUTH_CARD_DISABLED,
            AUTH_CARD_SASL,
            AUTH_CARD_NICKSERV,
            MESSAGES.text("servers.editor.auth.disabled.hint"),
            authDisabledHintLabel,
            saslUserField,
            saslPassField,
            saslMechanism,
            saslContinueOnFailureBox,
            saslHintLabel,
            nickservServiceField,
            nickservPassField,
            nickservDelayJoinBox,
            nickservHintLabel));
  }

  private JPanel buildAutoJoinPanel() {
    return ServerEditorCommandTabBuilder.buildAutoJoinPanel(
        new ServerEditorCommandTabBuilder.AutoJoinWidgets(autoJoinArea, autoJoinPmArea));
  }

  private JPanel buildPerformPanel() {
    return ServerEditorCommandTabBuilder.buildPerformPanel(
        new ServerEditorCommandTabBuilder.PerformWidgets(performArea));
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

  private void refreshBackendAndAuthUi() {
    updateBackendUi();
    updateCertificateUi();
    refreshAuthPanelUiAndValidation();
  }

  private void updateCertificateUi() {
    trustAllCertificatesBox.setEnabled(
        tlsBox.isSelected() && !selectedBackendProfile().matrixAuthSupported());
  }

  private void refreshAuthPanelUi() {
    ServerEditorAuthPanelUiApplier.apply(
        new ServerEditorAuthPanelUiApplier.RefreshRequest(
            selectedBackendProfile(),
            selectedAuthMode(),
            selectedMatrixAuthMode(),
            Objects.toString(saslMechanism.getSelectedItem(), "PLAIN")),
        new ServerEditorAuthPanelUiApplier.AuthPanelWidgets(
            new ServerEditorAuthModeUiApplier.AuthModeWidgets(
                authModeCombo,
                authModeCardPanel,
                AUTH_CARD_DISABLED,
                AUTH_CARD_SASL,
                AUTH_CARD_NICKSERV),
            new ServerEditorAuthUiApplier.MatrixAuthWidgets(
                authModeLabel,
                authModeCombo,
                authModeCardPanel,
                matrixAuthModeLabel,
                matrixAuthModeCombo,
                matrixAuthHintLabel,
                matrixAuthUserLabel,
                matrixAuthUserField,
                serverPasswordLabel,
                serverPassField,
                authModeLabel.getParent()),
            new ServerEditorAuthUiApplier.SaslWidgets(
                saslMechanism,
                saslContinueOnFailureBox,
                saslUserField,
                saslPassField,
                saslHintLabel),
            new ServerEditorAuthUiApplier.NickservWidgets(
                nickservServiceField, nickservPassField, nickservDelayJoinBox, nickservHintLabel)));
  }

  private void refreshAuthPanelUiAndValidation() {
    refreshAuthPanelUi();
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
    ServerEditorBackendPresentationPolicy.BackendPresentationState state =
        ServerEditorBackendPresentationPolicy.presentationState(selectedBackendProfile());
    ServerEditorBackendPresentationApplier.apply(
        state,
        new ServerEditorBackendPresentationApplier.BackendWidgets(
            hostLabel,
            serverPasswordLabel,
            nickLabel,
            loginLabel,
            realNameLabel,
            tlsBox,
            connectionBackendHintLabel,
            authDisabledHintLabel,
            serverPassField,
            hostField,
            loginField,
            nickField,
            realNameField));
  }

  private void updateValidation() {
    ServerEditorValidationPolicy.ValidationState state =
        ServerEditorValidationPolicy.validate(
            new ServerEditorValidationPolicy.ValidationRequest(
                selectedBackendProfile(),
                idField.getText(),
                hostField.getText(),
                portField.getText(),
                nickField.getText(),
                selectedMatrixAuthMode(),
                serverPasswordValue(),
                matrixAuthUserField.getText(),
                selectedAuthMode(),
                Objects.toString(saslMechanism.getSelectedItem(), "PLAIN"),
                saslUserField.getText(),
                new String(saslPassField.getPassword()),
                new String(nickservPassField.getPassword()),
                proxyOverrideBox.isSelected(),
                proxyEnabledBox.isSelected(),
                proxyHostField.getText(),
                proxyPortField.getText(),
                proxyUserField.getText(),
                new String(proxyPassField.getPassword()),
                proxyConnectTimeoutMsField.getText(),
                proxyReadTimeoutMsField.getText()));

    ServerEditorValidationUiApplier.apply(
        state,
        new ServerEditorValidationUiApplier.ValidationWidgets(
            idField,
            hostField,
            portField,
            serverPassField,
            matrixAuthUserField,
            loginField,
            nickField,
            saslUserField,
            saslPassField,
            nickservServiceField,
            nickservPassField,
            proxyHostField,
            proxyPortField,
            proxyUserField,
            proxyPassField,
            proxyConnectTimeoutMsField,
            proxyReadTimeoutMsField,
            saveBtn));

    // If a proxy test previously succeeded, keep success outlines only while inputs remain
    // unchanged.
    applyProxyTestSuccessDecoration();
  }

  private void applyProxyTestSuccessDecoration() {
    // Clear success outlines by default.
    ServerEditorValidationUiApplier.setSuccess(proxyHostField, false);
    ServerEditorValidationUiApplier.setSuccess(proxyPortField, false);
    ServerEditorValidationUiApplier.setSuccess(proxyConnectTimeoutMsField, false);
    ServerEditorValidationUiApplier.setSuccess(proxyReadTimeoutMsField, false);

    ServerEditorProxyTestDecorationPolicy.ProxyTestDecorationState state =
        ServerEditorProxyTestDecorationPolicy.decorationState(
            lastProxyTestOk != null,
            lastProxyTestOk != null && lastProxyTestOk.equals(ProxyTestSnapshot.capture(this)),
            proxyHostField.isEnabled() && proxyPortField.isEnabled(),
            proxyConnectTimeoutMsField.getText(),
            proxyReadTimeoutMsField.getText());
    if (!state.retainLastSuccessfulSnapshot()) {
      lastProxyTestOk = null;
    }
    ServerEditorValidationUiApplier.setSuccess(proxyHostField, state.hostSuccess());
    ServerEditorValidationUiApplier.setSuccess(proxyPortField, state.portSuccess());
    ServerEditorValidationUiApplier.setSuccess(
        proxyConnectTimeoutMsField, state.connectTimeoutSuccess());
    ServerEditorValidationUiApplier.setSuccess(proxyReadTimeoutMsField, state.readTimeoutSuccess());
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
          this,
          ex.getMessage(),
          MESSAGES.text("servers.editor.validation.invalidServer.title"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private IrcProperties.Server buildServer() {
    String backendId = selectedBackendId();
    return ServerEditorServerBuildPolicy.build(
        new ServerEditorServerBuildPolicy.ServerBuildRequest(
            backendProfile(backendId),
            backendId,
            idField.getText(),
            hostField.getText(),
            portField.getText(),
            tlsBox.isSelected(),
            serverPasswordValue(),
            selectedMatrixAuthMode(),
            matrixAuthUserField.getText(),
            nickField.getText(),
            loginField.getText(),
            realNameField.getText(),
            selectedAuthMode(),
            saslUserField.getText(),
            new String(saslPassField.getPassword()),
            Objects.toString(saslMechanism.getSelectedItem(), "PLAIN"),
            saslContinueOnFailureBox.isSelected(),
            nickservServiceField.getText(),
            new String(nickservPassField.getPassword()),
            nickservDelayJoinBox.isSelected(),
            autoJoinArea.getText(),
            autoJoinPmArea.getText(),
            performArea.getText(),
            proxyOverrideBox.isSelected(),
            proxyEnabledBox.isSelected(),
            proxyHostField.getText(),
            proxyPortField.getText(),
            proxyUserField.getText(),
            new String(proxyPassField.getPassword()),
            proxyRemoteDnsBox.isSelected(),
            proxyConnectTimeoutMsField.getText(),
            proxyReadTimeoutMsField.getText(),
            trustAllCertificatesBox.isSelected()));
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

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }
}
