package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

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
  private final JTextField saslPassField = new JTextField();
  private final JComboBox<String> saslMechanism = new JComboBox<>(new String[]{"PLAIN"});

  private final JTextArea autoJoinArea = new JTextArea(8, 30);

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
    } else {
      tlsBox.setSelected(true);
      portField.setText("6697");
      portAuto = true;
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
    applyFieldStyle(saslPassField, "password");
    autoJoinArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#channel\n#another");

    // Auto-update default port when toggling TLS, if the user hasn't customized it.
    tlsBox.addActionListener(e -> maybeAdjustPortForTls());
    portField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { portAuto = false; }
      @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { portAuto = false; }
      @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { portAuto = false; }
    });

    saslEnabledBox.addActionListener(e -> updateSaslEnabled());
    updateSaslEnabled();

    setPreferredSize(new Dimension(640, 520));
    pack();
    setLocationRelativeTo(parent);
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

    g.gridy = 4;
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
    addRow(p, g, 2, "Password", saslPassField);
    addRow(p, g, 3, "Mechanism", saslMechanism);

    g.gridy = 4;
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
    saslUserField.setEnabled(en);
    saslPassField.setEnabled(en);
    saslMechanism.setEnabled(en);
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
      String p = Objects.toString(saslPassField.getText(), "");
      String mech = Objects.toString(saslMechanism.getSelectedItem(), "PLAIN").trim();
      if (u.isEmpty()) throw new IllegalArgumentException("SASL username is required when SASL is enabled");
      if (p.isBlank()) throw new IllegalArgumentException("SASL password is required when SASL is enabled");
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
        autoJoin
    );
  }

  private static void applyFieldStyle(JTextField f, String placeholder) {
    f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    f.putClientProperty(FlatClientProperties.STYLE, "arc:10;");
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
