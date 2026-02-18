package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.SocksProxySocketFactory;
import cafe.woden.ircclient.net.SocksProxySslSocketFactory;
import cafe.woden.ircclient.net.NetTlsContext;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Add/edit a single IRC server configuration.
 */
public class ServerEditorDialog extends JDialog {

  private Optional<IrcProperties.Server> result = Optional.empty();

  private final JTextField idField = new JTextField();
  private final JTextField hostField = new JTextField();
  private final JTextField portField = new JTextField();
  private final JCheckBox tlsBox = new JCheckBox("Use TLS (SSL)");
  private final JTextField serverPassField = new JTextField();

  private final JTextField nickField = new JTextField();
  private final JTextField loginField = new JTextField();
  private final JTextField realNameField = new JTextField();

  private final JCheckBox saslEnabledBox = new JCheckBox("Enable SASL");
  private final JTextField saslUserField = new JTextField();
  /**
   * SASL secret (password / key material). Use a password field so we don't echo secrets in plain text.
   */
  private final JPasswordField saslPassField = new JPasswordField();
  private final JComboBox<String> saslMechanism = new JComboBox<>(new String[]{
      "AUTO",
      "PLAIN",
      "SCRAM-SHA-256",
      "SCRAM-SHA-1",
      "EXTERNAL",
      "ECDSA-NIST256P-CHALLENGE"
  });

  private final JLabel saslHintLabel = new JLabel();

  private final JTextArea autoJoinArea = new JTextArea(8, 30);

  private final List<String> performSeed;

  // Per-server proxy override
  private final JCheckBox proxyOverrideBox = new JCheckBox("Override proxy for this server");
  private final JCheckBox proxyEnabledBox = new JCheckBox("Use SOCKS5 proxy");
  private final JTextField proxyHostField = new JTextField();
  private final JTextField proxyPortField = new JTextField();
  private final JCheckBox proxyRemoteDnsBox = new JCheckBox("Remote DNS (resolve hostnames via proxy)");
  private final JTextField proxyUserField = new JTextField();
  private final JPasswordField proxyPassField = new JPasswordField();
  private final JTextField proxyConnectTimeoutMsField = new JTextField();
  private final JTextField proxyReadTimeoutMsField = new JTextField();
  private final JButton proxyTestBtn = new JButton("Test…");
  private final JLabel proxyHintLabel = new JLabel();
  private final JLabel proxyStatusLabel = new JLabel(" ");

  private final JButton saveBtn = new JButton("Save");
  private final JButton cancelBtn = new JButton("Cancel");

  private boolean portAuto = true;

  public ServerEditorDialog(Window parent, String title, IrcProperties.Server seed) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout(10, 10));
    ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Connection", buildConnectionPanel());
    tabs.addTab("Identity", buildIdentityPanel());
    tabs.addTab("SASL", buildSaslPanel());
    tabs.addTab("Auto-Join", buildAutoJoinPanel());
    tabs.addTab("Proxy", buildProxyPanel());
    add(tabs, BorderLayout.CENTER);

    JPanel actions = new JPanel();
    actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
    actions.add(javax.swing.Box.createHorizontalGlue());
    saveBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "primary");
    cancelBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, "default");
    actions.add(cancelBtn);
    actions.add(javax.swing.Box.createHorizontalStrut(8));
    actions.add(saveBtn);
    add(actions, BorderLayout.SOUTH);

    cancelBtn.addActionListener(e -> {
      result = Optional.empty();
      dispose();
    });
    saveBtn.addActionListener(e -> onSave());

    // Seed values
    if (seed != null) {
      performSeed = (seed.perform() == null) ? List.of() : List.copyOf(seed.perform());
      idField.setText(Objects.toString(seed.id(), ""));
      hostField.setText(Objects.toString(seed.host(), ""));
      portField.setText(String.valueOf(seed.port()));
      tlsBox.setSelected(seed.tls());
      serverPassField.setText(Objects.toString(seed.serverPassword(), ""));

      nickField.setText(Objects.toString(seed.nick(), ""));
      loginField.setText(Objects.toString(seed.login(), ""));
      realNameField.setText(Objects.toString(seed.realName(), ""));

      if (seed.sasl() != null) {
        saslEnabledBox.setSelected(seed.sasl().enabled());
        saslUserField.setText(Objects.toString(seed.sasl().username(), ""));
        saslPassField.setText(Objects.toString(seed.sasl().password(), ""));
        saslMechanism.setSelectedItem(Objects.toString(seed.sasl().mechanism(), "PLAIN"));
      }

      autoJoinArea.setText(String.join("\n", seed.autoJoin() == null ? List.of() : seed.autoJoin()));
      portAuto = false; // user likely set explicitly

      seedProxy(seed.proxy());
    } else {
      performSeed = List.of();
      tlsBox.setSelected(true);
      portField.setText("6697");
      portAuto = true;

      seedProxy(null);
    }

    // Placeholders / styling
    applyFieldStyle(idField, "libera");
    applyFieldStyle(hostField, "irc.example.net");
    applyFieldStyle(portField, "6697");
    applyFieldStyle(serverPassField, "(optional)");
    applyFieldStyle(nickField, "IRCafeUser");
    applyFieldStyle(loginField, "ircafe");
    applyFieldStyle(realNameField, "IRCafe User");
    applyFieldStyle(saslUserField, "account");
    applyFieldStyle(saslPassField, "password / key");
    // FlatLaf: show the standard "reveal" (eye) button inside password fields.
    // Using the string key avoids any compile-time dependency on FlatLaf constants.
    saslPassField.putClientProperty("JPasswordField.showRevealButton", true);
    // FlatLaf also supports a STYLE flag; keep both for compatibility.
    appendStyle(saslPassField, "showRevealButton:true");
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
    autoJoinArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#channel\n#another");

    // Auto-update default port when toggling TLS, if the user hasn't customized it.
    tlsBox.addActionListener(e -> maybeAdjustPortForTls());
    portField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { portAuto = false; }
      @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { portAuto = false; }
      @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { portAuto = false; }
    });

    saslEnabledBox.addActionListener(e -> updateSaslEnabled());
    saslMechanism.addActionListener(e -> updateSaslEnabled());
    updateSaslEnabled();

    proxyOverrideBox.addActionListener(e -> updateProxyEnabled());
    proxyEnabledBox.addActionListener(e -> updateProxyEnabled());
    updateProxyEnabled();

    setPreferredSize(new Dimension(640, 520));
    pack();
    setLocationRelativeTo(parent);
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
    proxyHintLabel.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
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
    testRow.add(proxyTestBtn);
    testRow.add(javax.swing.Box.createHorizontalStrut(10));
    proxyStatusLabel.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
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
      proxyHintLabel.setText(global.enabled()
          ? "Inheriting global proxy from Preferences (enabled: " + global.host() + ":" + global.port() + ")"
          : "Inheriting global proxy from Preferences (disabled)"
      );
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
  }

  private void onTestProxy() {
    proxyStatusLabel.setText("Testing…");
    proxyTestBtn.setEnabled(false);

    final String host = trim(hostField.getText());
    final String portText = trim(portField.getText());
    final boolean tls = tlsBox.isSelected();

    final IrcProperties.Proxy cfg;
    try {
      cfg = resolveProxyForTest();
    } catch (IllegalArgumentException ex) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid proxy settings", JOptionPane.ERROR_MESSAGE);
      return;
    }

    int port;
    try {
      port = Integer.parseInt(portText);
    } catch (Exception e) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(this, "Port must be a number", "Invalid server configuration", JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (host.isBlank()) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(this, "Host is required", "Invalid server configuration", JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (port <= 0 || port > 65535) {
      proxyStatusLabel.setText(" ");
      proxyTestBtn.setEnabled(true);
      JOptionPane.showMessageDialog(this, "Port must be 1-65535", "Invalid server configuration", JOptionPane.ERROR_MESSAGE);
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
            JOptionPane.showMessageDialog(ServerEditorDialog.this,
                "Connection test succeeded.\n\n" +
                    "TLS: " + (tls ? "yes" : "no") + "\n" +
                    "Proxy: " + (cfg.enabled() ? (cfg.host() + ":" + cfg.port()) : "disabled") + "\n" +
                    "Time: " + r.elapsedMs + " ms",
                "Proxy test",
                JOptionPane.INFORMATION_MESSAGE);
          } else {
            proxyStatusLabel.setText("Failed: " + r.shortMessage());
            JOptionPane.showMessageDialog(ServerEditorDialog.this,
                "Connection test failed.\n\n" + r.longMessage(),
                "Proxy test",
                JOptionPane.ERROR_MESSAGE);
          }
        } catch (Exception e) {
          proxyStatusLabel.setText("Failed");
          JOptionPane.showMessageDialog(ServerEditorDialog.this,
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

  private static void testConnect(String host, int port, boolean tls, IrcProperties.Proxy cfg) throws Exception {
    long connectTimeoutMs = Math.max(1, cfg.connectTimeoutMs());
    int readTimeoutMs = (int) Math.max(1, Math.min(Integer.MAX_VALUE, cfg.readTimeoutMs()));

    if (cfg.enabled()) {
      // Proxy path
      Socket s;
      if (tls) {
        s = new SocksProxySslSocketFactory(cfg, NetTlsContext.sslSocketFactory())
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
    tcp.connect(new InetSocketAddress(host, port), (int) Math.min(Integer.MAX_VALUE, connectTimeoutMs));
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

  private JPanel buildConnectionPanel() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    GridBagConstraints g = baseGbc();

    addRow(p, g, 0, "Server ID", idField);
    addRow(p, g, 1, "Host", hostField);

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

    addRow(p, g, 2, "Port", portRow);
    addRow(p, g, 3, "Server password", serverPassField);

    g.gridy = 5;
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
    addRow(p, g, 0, "Nick", nickField);
    addRow(p, g, 1, "Login/Ident", loginField);
    addRow(p, g, 2, "Real name", realNameField);

    g.gridy = 3;
    g.gridx = 0;
    g.gridwidth = 2;
    g.weighty = 1.0;
    p.add(new JLabel(""), g);
    return p;
  }

  private JPanel buildSaslPanel() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    GridBagConstraints g = baseGbc();
    g.gridx = 0;
    g.gridy = 0;
    g.gridwidth = 2;
    g.weightx = 1.0;
    g.fill = GridBagConstraints.HORIZONTAL;
    p.add(saslEnabledBox, g);

    g.gridwidth = 1;
    addRow(p, g, 1, "Username", saslUserField);
    addRow(p, g, 2, "Secret", saslPassField);
    addRow(p, g, 3, "Mechanism", saslMechanism);

    saslHintLabel.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    saslHintLabel.setText(" ");
    g.gridy = 4;
    g.gridx = 0;
    g.gridwidth = 2;
    g.weightx = 1.0;
    g.fill = GridBagConstraints.HORIZONTAL;
    p.add(saslHintLabel, g);

    g.gridy = 5;
    g.gridx = 0;
    g.gridwidth = 2;
    g.weighty = 1.0;
    p.add(new JLabel(""), g);
    return p;
  }

  private JPanel buildAutoJoinPanel() {
    JPanel p = new JPanel(new BorderLayout(8, 8));
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    JLabel hint = new JLabel("One channel per line (e.g. #java)");
    hint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    p.add(hint, BorderLayout.NORTH);
    autoJoinArea.setLineWrap(true);
    autoJoinArea.setWrapStyleWord(true);
    JScrollPane sc = new JScrollPane(autoJoinArea);
    sc.putClientProperty(FlatClientProperties.STYLE, "arc:12;");
    p.add(sc, BorderLayout.CENTER);
    return p;
  }

  private void updateSaslEnabled() {
    boolean en = saslEnabledBox.isSelected();
    saslMechanism.setEnabled(en);

    String mech = Objects.toString(saslMechanism.getSelectedItem(), "PLAIN").trim();
    String mechUpper = mech.toUpperCase(java.util.Locale.ROOT);

    // Default: username + secret are enabled when SASL is enabled.
    boolean userEnabled = en;
    boolean secretEnabled = en;

    String hint;
    String secretPlaceholder;

    switch (mechUpper) {
      case "EXTERNAL" -> {
        // TLS client certificate auth; secret is unused.
        secretEnabled = false;
        secretPlaceholder = "(ignored)";
        hint = "EXTERNAL uses your TLS client certificate. Secret is ignored; username is optional.";
      }
      case "ECDSA-NIST256P-CHALLENGE" -> {
        secretPlaceholder = "base64 PKCS#8 EC private key";
        hint = "ECDSA challenge-response. Secret should be a base64 PKCS#8 EC private key. Username is usually required.";
      }
      case "SCRAM-SHA-256" -> {
        secretPlaceholder = "password";
        hint = "SCRAM-SHA-256 (recommended). Secret = password.";
      }
      case "SCRAM-SHA-1" -> {
        secretPlaceholder = "password";
        hint = "SCRAM-SHA-1. Secret = password.";
      }
      case "AUTO" -> {
        secretPlaceholder = "password (leave blank for EXTERNAL)";
        hint = "AUTO prefers SCRAM (256/1) or PLAIN when a secret is provided, and falls back to EXTERNAL when secret is blank.";
      }
      default -> {
        secretPlaceholder = "password";
        hint = "PLAIN. Secret = password.";
      }
    }

    saslUserField.setEnabled(userEnabled);
    saslPassField.setEnabled(secretEnabled);

    // Make the hint wrap nicely.
    String html = "<html><body style='width: 520px'>" + hint + "</body></html>";
    saslHintLabel.setText(en ? html : " ");
    saslHintLabel.setToolTipText(hint);

    // Update placeholder dynamically.
    saslPassField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, secretPlaceholder);
  }

  private void maybeAdjustPortForTls() {
    if (!portAuto) return;
    portField.setText(tlsBox.isSelected() ? "6697" : "6667");
  }

  private void onSave() {
    try {
      IrcProperties.Server server = buildServer();
      result = Optional.of(server);
      dispose();
    } catch (IllegalArgumentException ex) {
      JOptionPane.showMessageDialog(this,
          ex.getMessage(),
          "Invalid server configuration",
          JOptionPane.ERROR_MESSAGE);
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

    boolean tls = tlsBox.isSelected();
    String serverPassword = Objects.toString(serverPassField.getText(), "");

    String nick = trim(nickField.getText());
    if (nick.isEmpty()) throw new IllegalArgumentException("Nick is required");

    String login = trim(loginField.getText());
    if (login.isEmpty()) login = nick;

    String realName = trim(realNameField.getText());
    if (realName.isEmpty()) realName = nick;

    IrcProperties.Server.Sasl sasl;
    if (saslEnabledBox.isSelected()) {
      String u = trim(saslUserField.getText());
      // JPasswordField stores secret as a char[]. Convert only when building the immutable config object.
      String p = new String(saslPassField.getPassword());
      String mech = Objects.toString(saslMechanism.getSelectedItem(), "PLAIN").trim();

      String mechUpper = mech.toUpperCase(java.util.Locale.ROOT);
      boolean hasSecret = !p.isBlank();
      boolean needsUser = switch (mechUpper) {
        case "EXTERNAL" -> false;
        case "AUTO" -> hasSecret;
        default -> true;
      };
      boolean needsSecret = switch (mechUpper) {
        case "EXTERNAL" -> false;
        case "AUTO" -> false;
        default -> true;
      };

      if (needsUser && u.isEmpty()) {
        throw new IllegalArgumentException("SASL username is required for mechanism " + mechUpper);
      }
      if (needsSecret && p.isBlank()) {
        throw new IllegalArgumentException("SASL secret is required for mechanism " + mechUpper);
      }
      sasl = new IrcProperties.Server.Sasl(true, u, p, mech, null);
    } else {
      sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    }

    List<String> autoJoin = new ArrayList<>();
    for (String line : Objects.toString(autoJoinArea.getText(), "").split("\\R")) {
      String ch = trim(line);
      if (ch.isEmpty()) continue;
      autoJoin.add(ch);
    }

    IrcProperties.Proxy proxyOverride = null;
    if (proxyOverrideBox.isSelected()) {
      // Build a per-server override (including the ability to explicitly disable proxying).
      long connectMs = parseLongOrDefault(proxyConnectTimeoutMsField.getText(), 20_000);
      long readMs = parseLongOrDefault(proxyReadTimeoutMsField.getText(), 30_000);

      if (!proxyEnabledBox.isSelected()) {
        proxyOverride = new IrcProperties.Proxy(false, "", 0, "", "", true, connectMs, readMs);
      } else {
        String pHost = trim(proxyHostField.getText());
        int pPort;
        try {
          pPort = Integer.parseInt(trim(proxyPortField.getText()));
        } catch (Exception e) {
          throw new IllegalArgumentException("Proxy port must be a number");
        }
        boolean remoteDns = proxyRemoteDnsBox.isSelected();
        String user = trim(proxyUserField.getText());
        String pass = new String(proxyPassField.getPassword());
        proxyOverride = new IrcProperties.Proxy(true, pHost, pPort, user, pass, remoteDns, connectMs, readMs);
      }
    }

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
        autoJoin,
        performSeed,
        proxyOverride
    );
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

  private static void addRow(JPanel panel, GridBagConstraints g, int row, String label, java.awt.Component field) {
    g.gridy = row;
    g.gridx = 0;
    g.weightx = 0.0;
    g.gridwidth = 1;
    JLabel l = new JLabel(label);
    l.putClientProperty(FlatClientProperties.STYLE, "font:+0");
    panel.add(l, g);

    g.gridx = 1;
    g.weightx = 1.0;
    panel.add(field, g);
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }
}
