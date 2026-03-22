package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.app.api.UiInteractionPort;
import cafe.woden.ircclient.app.api.UiViewStatePort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.BackendNamedCommandNames;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JOptionPane;
import net.miginfocom.swing.MigLayout;

/** Swing adapter for user-initiated streams and UI prompts. */
final class SwingUiInteractionPort implements UiInteractionPort {

  private final SwingEdtExecutor edt;
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final UserListDockable users;
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;
  private final TargetActivationBus activationBus;
  private final OutboundLineBus outboundBus;
  private final UiViewStatePort viewStatePort;
  private final SwingUiBackendCommandBridge backendCommandBridge;

  SwingUiInteractionPort(
      SwingEdtExecutor edt,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UserListDockable users,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      UiViewStatePort viewStatePort,
      SwingUiBackendCommandBridge backendCommandBridge) {
    this.edt = Objects.requireNonNull(edt, "edt");
    this.serverTree = Objects.requireNonNull(serverTree, "serverTree");
    this.chat = Objects.requireNonNull(chat, "chat");
    this.users = Objects.requireNonNull(users, "users");
    this.connectBtn = Objects.requireNonNull(connectBtn, "connectBtn");
    this.disconnectBtn = Objects.requireNonNull(disconnectBtn, "disconnectBtn");
    this.activationBus = Objects.requireNonNull(activationBus, "activationBus");
    this.outboundBus = Objects.requireNonNull(outboundBus, "outboundBus");
    this.viewStatePort = Objects.requireNonNull(viewStatePort, "viewStatePort");
    this.backendCommandBridge =
        Objects.requireNonNull(backendCommandBridge, "backendCommandBridge");
  }

  @Override
  public Flowable<TargetRef> targetSelections() {
    return serverTree.selectionStream();
  }

  @Override
  public Flowable<TargetRef> targetActivations() {
    return activationBus.stream();
  }

  @Override
  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return Flowable.merge(users.privateMessageRequests(), chat.privateMessageRequests());
  }

  @Override
  public Flowable<UserActionRequest> userActionRequests() {
    return Flowable.mergeArray(users.userActionRequests(), chat.userActionRequests())
        .onBackpressureBuffer();
  }

  @Override
  public Flowable<String> outboundLines() {
    return outboundBus.stream();
  }

  @Override
  public boolean confirmMultilineSplitFallback(
      TargetRef target, int lineCount, long payloadUtf8Bytes, String reason) {
    return edt.call(
        () -> {
          String where = (target == null) ? "this target" : target.target();
          String why = Objects.toString(reason, "").trim();
          StringBuilder body = new StringBuilder();
          body.append("This message cannot be sent using IRCv3 multiline for ")
              .append(where)
              .append(".\n\n");
          if (!why.isEmpty()) {
            body.append("Reason: ").append(why).append("\n\n");
          }
          body.append("Message size: ")
              .append(Math.max(0, lineCount))
              .append(" lines, ")
              .append(Math.max(0L, payloadUtf8Bytes))
              .append(" UTF-8 bytes.\n\n")
              .append("Send as separate lines instead?");

          Object[] options = {"Send " + Math.max(0, lineCount) + " Lines", "Cancel"};
          int choice =
              JOptionPane.showOptionDialog(
                  chat,
                  body.toString(),
                  "Multiline Fallback",
                  JOptionPane.DEFAULT_OPTION,
                  JOptionPane.WARNING_MESSAGE,
                  null,
                  options,
                  options[0]);
          return choice == 0;
        },
        false);
  }

  @Override
  public Flowable<Object> connectClicks() {
    return connectBtn.onClick();
  }

  @Override
  public Flowable<Object> disconnectClicks() {
    return disconnectBtn.onClick();
  }

  @Override
  public Flowable<String> connectServerRequests() {
    return serverTree.connectServerRequests();
  }

  @Override
  public Flowable<String> disconnectServerRequests() {
    return serverTree.disconnectServerRequests();
  }

  @Override
  public Flowable<ParsedInput.BackendNamed> backendNamedCommandRequests() {
    Flowable<String> setupRequests = serverTree.quasselSetupRequests();
    Flowable<String> networkManagerRequests =
        Flowable.mergeArray(
                serverTree.quasselNetworkManagerRequests(),
                backendCommandBridge.quasselNetworkManagerRequestsFromApp())
            .onBackpressureBuffer();
    return Flowable.mergeArray(
            setupRequests.map(
                sid -> {
                  String normalized = normalizeBackendCommandArgs(sid);
                  revealBackendStatusTarget(normalized);
                  return new ParsedInput.BackendNamed(
                      BackendNamedCommandNames.QUASSEL_SETUP, normalized);
                }),
            networkManagerRequests.map(
                sid ->
                    new ParsedInput.BackendNamed(
                        BackendNamedCommandNames.QUASSEL_NETWORK_MANAGER,
                        normalizeBackendCommandArgs(sid))))
        .onBackpressureBuffer();
  }

  @Override
  public void openQuasselNetworkManager(String serverId) {
    backendCommandBridge.openQuasselNetworkManager(serverId);
  }

  @Override
  public Flowable<TargetRef> closeTargetRequests() {
    return serverTree.closeTargetRequests();
  }

  @Override
  public Flowable<TargetRef> joinChannelRequests() {
    return serverTree.joinChannelRequests();
  }

  @Override
  public Flowable<TargetRef> disconnectChannelRequests() {
    return serverTree.disconnectChannelRequests();
  }

  @Override
  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return serverTree.bouncerDetachChannelRequests();
  }

  @Override
  public Flowable<TargetRef> closeChannelRequests() {
    return serverTree.closeChannelRequests();
  }

  @Override
  public Flowable<TargetRef> clearLogRequests() {
    return serverTree.clearLogRequests();
  }

  @Override
  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return serverTree.ircv3CapabilityToggleRequests();
  }

  @Override
  public Optional<QuasselCoreControlPort.QuasselCoreSetupRequest> promptQuasselCoreSetup(
      String serverId, QuasselCoreControlPort.QuasselCoreSetupPrompt prompt) {
    return edt.call(
        () -> {
          String sid = Objects.toString(serverId, "").trim();
          String detail = prompt == null ? "" : Objects.toString(prompt.detail(), "").trim();

          List<String> storageOptions = new ArrayList<>();
          if (prompt != null && prompt.storageBackends() != null) {
            for (String option : prompt.storageBackends()) {
              String v = Objects.toString(option, "").trim();
              if (!v.isEmpty()) storageOptions.add(v);
            }
          }
          if (storageOptions.isEmpty()) storageOptions.add("SQLite");

          List<String> authOptions = new ArrayList<>();
          if (prompt != null && prompt.authenticators() != null) {
            for (String option : prompt.authenticators()) {
              String v = Objects.toString(option, "").trim();
              if (!v.isEmpty()) authOptions.add(v);
            }
          }
          if (authOptions.isEmpty()) authOptions.add("Database");

          javax.swing.JTextField adminUserField = new javax.swing.JTextField(20);
          adminUserField.putClientProperty(
              com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "admin");
          javax.swing.JPasswordField adminPasswordField = new javax.swing.JPasswordField(20);
          adminPasswordField.putClientProperty(
              com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "admin password");
          adminPasswordField.putClientProperty("JPasswordField.showRevealButton", true);
          adminPasswordField.putClientProperty(
              com.formdev.flatlaf.FlatClientProperties.STYLE, "showRevealButton:true");

          javax.swing.JComboBox<String> storageCombo =
              new javax.swing.JComboBox<>(storageOptions.toArray(String[]::new));
          javax.swing.JComboBox<String> authCombo =
              new javax.swing.JComboBox<>(authOptions.toArray(String[]::new));
          storageCombo.setEditable(storageOptions.size() <= 1);
          authCombo.setEditable(authOptions.size() <= 1);

          javax.swing.JPanel panel =
              new javax.swing.JPanel(
                  new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
          panel.add(new javax.swing.JLabel("Admin user"));
          panel.add(adminUserField, "growx, wrap");
          panel.add(new javax.swing.JLabel("Admin password"));
          panel.add(adminPasswordField, "growx, wrap");
          panel.add(new javax.swing.JLabel("Storage backend"));
          panel.add(storageCombo, "growx, wrap");
          panel.add(new javax.swing.JLabel("Authenticator"));
          panel.add(authCombo, "growx, wrap");

          String title = sid.isEmpty() ? "Quassel Core Setup" : ("Quassel Core Setup - " + sid);
          while (true) {
            int choice =
                JOptionPane.showConfirmDialog(
                    chat, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
              return Optional.empty();
            }

            String adminUser = Objects.toString(adminUserField.getText(), "").trim();
            String adminPassword = new String(adminPasswordField.getPassword());
            String storage =
                storageCombo.isEditable()
                    ? Objects.toString(storageCombo.getEditor().getItem(), "").trim()
                    : Objects.toString(storageCombo.getSelectedItem(), "").trim();
            String auth =
                authCombo.isEditable()
                    ? Objects.toString(authCombo.getEditor().getItem(), "").trim()
                    : Objects.toString(authCombo.getSelectedItem(), "").trim();

            if (adminUser.isEmpty()) {
              JOptionPane.showMessageDialog(
                  chat, "Admin user is required.", "Quassel setup", JOptionPane.ERROR_MESSAGE);
              continue;
            }
            if (adminPassword.isBlank()) {
              JOptionPane.showMessageDialog(
                  chat, "Admin password is required.", "Quassel setup", JOptionPane.ERROR_MESSAGE);
              continue;
            }
            if (storage.isEmpty()) {
              JOptionPane.showMessageDialog(
                  chat, "Storage backend is required.", "Quassel setup", JOptionPane.ERROR_MESSAGE);
              continue;
            }
            if (auth.isEmpty()) {
              JOptionPane.showMessageDialog(
                  chat, "Authenticator is required.", "Quassel setup", JOptionPane.ERROR_MESSAGE);
              continue;
            }

            if (!detail.isEmpty()) {
              viewStatePort.enqueueStatusNotice("Submitting Quassel setup: " + detail, null);
            }
            return Optional.of(
                new QuasselCoreControlPort.QuasselCoreSetupRequest(
                    adminUser, adminPassword, storage, auth, Map.of(), Map.of()));
          }
        },
        Optional.empty());
  }

  @Override
  public Optional<QuasselNetworkManagerAction> promptQuasselNetworkManagerAction(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    return edt.call(
        () -> {
          String sid = Objects.toString(serverId, "").trim();
          List<QuasselCoreControlPort.QuasselCoreNetworkSummary> safeNetworks =
              networks == null ? List.of() : List.copyOf(networks);
          viewStatePort.syncQuasselNetworks(sid, safeNetworks);

          while (true) {
            javax.swing.DefaultListModel<QuasselNetworkChoice> model =
                new javax.swing.DefaultListModel<>();
            for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : safeNetworks) {
              if (summary == null) continue;
              model.addElement(new QuasselNetworkChoice(summary));
            }

            javax.swing.JList<QuasselNetworkChoice> list = new javax.swing.JList<>(model);
            list.setVisibleRowCount(Math.min(12, Math.max(5, model.size())));
            if (!model.isEmpty()) {
              list.setSelectedIndex(0);
            }
            javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(list);
            scroll.setPreferredSize(new java.awt.Dimension(680, 280));

            javax.swing.JPanel panel =
                new javax.swing.JPanel(
                    new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]"));
            panel.add(new javax.swing.JLabel("Select a network and choose an action."), "growx");
            panel.add(scroll, "grow, push");

            Object[] options = {
              "Connect", "Disconnect", "Add...", "Edit...", "Remove", "Refresh", "Close"
            };
            String title =
                sid.isEmpty() ? "Quassel Network Manager" : ("Quassel Network Manager - " + sid);
            int choice =
                JOptionPane.showOptionDialog(
                    chat,
                    panel,
                    title,
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice < 0 || choice == 6) {
              return Optional.empty();
            }

            if (choice == 5) {
              return Optional.of(QuasselNetworkManagerAction.refresh());
            }

            if (choice == 2) {
              Optional<QuasselCoreControlPort.QuasselCoreNetworkCreateRequest> addRequest =
                  promptQuasselNetworkCreateRequest();
              if (addRequest.isEmpty()) {
                continue;
              }
              return Optional.of(QuasselNetworkManagerAction.add(addRequest.orElseThrow()));
            }

            QuasselNetworkChoice selected = list.getSelectedValue();
            if (selected == null) {
              JOptionPane.showMessageDialog(
                  chat,
                  "Select a network first.",
                  "Quassel network manager",
                  JOptionPane.WARNING_MESSAGE);
              continue;
            }

            String networkToken = selected.idTokenOrName();
            if (choice == 0) {
              return Optional.of(QuasselNetworkManagerAction.connect(networkToken));
            }
            if (choice == 1) {
              return Optional.of(QuasselNetworkManagerAction.disconnect(networkToken));
            }
            if (choice == 4) {
              return Optional.of(QuasselNetworkManagerAction.remove(networkToken));
            }
            if (choice == 3) {
              Optional<QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest> updateRequest =
                  promptQuasselNetworkUpdateRequest(selected.summary());
              if (updateRequest.isEmpty()) {
                continue;
              }
              return Optional.of(
                  QuasselNetworkManagerAction.edit(networkToken, updateRequest.orElseThrow()));
            }
          }
        },
        Optional.empty());
  }

  private void revealBackendStatusTarget(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    TargetRef status = new TargetRef(sid, "status");
    viewStatePort.ensureTargetExists(status);
    viewStatePort.selectTarget(status);
  }

  private static String normalizeBackendCommandArgs(String args) {
    return Objects.toString(args, "").trim();
  }

  private Optional<QuasselCoreControlPort.QuasselCoreNetworkCreateRequest>
      promptQuasselNetworkCreateRequest() {
    javax.swing.JTextField nameField = new javax.swing.JTextField(28);
    javax.swing.JTextField hostField = new javax.swing.JTextField(28);
    javax.swing.JTextField portField = new javax.swing.JTextField("6697", 8);
    javax.swing.JCheckBox tlsCheck = new javax.swing.JCheckBox("Use TLS", true);
    javax.swing.JCheckBox enabledCheck = new javax.swing.JCheckBox("Enabled", true);

    javax.swing.JPanel panel =
        new javax.swing.JPanel(
            new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    panel.add(new javax.swing.JLabel("Network name"));
    panel.add(nameField, "growx, wrap");
    panel.add(new javax.swing.JLabel("Server host"));
    panel.add(hostField, "growx, wrap");
    panel.add(new javax.swing.JLabel("Server port"));
    panel.add(portField, "growx, wrap");
    panel.add(new javax.swing.JLabel(""));
    panel.add(tlsCheck, "growx, wrap");
    panel.add(new javax.swing.JLabel(""));
    panel.add(enabledCheck, "growx, wrap");

    while (true) {
      int result =
          JOptionPane.showConfirmDialog(
              chat,
              panel,
              "Add Quassel Network",
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE);
      if (result != JOptionPane.OK_OPTION) {
        return Optional.empty();
      }

      String networkName = Objects.toString(nameField.getText(), "").trim();
      String serverHost = Objects.toString(hostField.getText(), "").trim();
      int serverPort = parseQuasselPortOrDefault(portField.getText(), tlsCheck.isSelected());
      if (networkName.isEmpty()) {
        JOptionPane.showMessageDialog(
            chat,
            "Network name is required.",
            "Quassel network manager",
            JOptionPane.ERROR_MESSAGE);
        continue;
      }
      if (serverHost.isEmpty()) {
        JOptionPane.showMessageDialog(
            chat, "Server host is required.", "Quassel network manager", JOptionPane.ERROR_MESSAGE);
        continue;
      }
      if (serverPort <= 0 || serverPort > 65535) {
        JOptionPane.showMessageDialog(
            chat,
            "Server port must be 1-65535.",
            "Quassel network manager",
            JOptionPane.ERROR_MESSAGE);
        continue;
      }
      return Optional.of(
          new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
              networkName,
              serverHost,
              serverPort,
              tlsCheck.isSelected(),
              "",
              true,
              null,
              List.of()));
    }
  }

  private Optional<QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest>
      promptQuasselNetworkUpdateRequest(QuasselNetworkChoiceSummary summary) {
    String defaultName = summary == null ? "" : summary.networkName();
    String defaultHost = summary == null ? "" : summary.serverHost();
    int defaultPort = summary == null ? 0 : summary.serverPort();
    boolean defaultTls = summary != null && summary.useTls();
    boolean defaultEnabled = summary == null || summary.enabled();

    javax.swing.JTextField nameField = new javax.swing.JTextField(defaultName, 28);
    nameField.putClientProperty(
        com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "(blank keeps existing name)");
    javax.swing.JTextField hostField = new javax.swing.JTextField(defaultHost, 28);
    javax.swing.JTextField portField =
        new javax.swing.JTextField(
            defaultPort > 0 ? Integer.toString(defaultPort) : (defaultTls ? "6697" : "6667"), 8);
    javax.swing.JCheckBox tlsCheck = new javax.swing.JCheckBox("Use TLS", defaultTls);
    javax.swing.JCheckBox enabledCheck = new javax.swing.JCheckBox("Enabled", defaultEnabled);

    javax.swing.JPanel panel =
        new javax.swing.JPanel(
            new MigLayout("insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
    panel.add(new javax.swing.JLabel("Network name"));
    panel.add(nameField, "growx, wrap");
    panel.add(new javax.swing.JLabel("Server host"));
    panel.add(hostField, "growx, wrap");
    panel.add(new javax.swing.JLabel("Server port"));
    panel.add(portField, "growx, wrap");
    panel.add(new javax.swing.JLabel(""));
    panel.add(tlsCheck, "growx, wrap");
    panel.add(new javax.swing.JLabel(""));
    panel.add(enabledCheck, "growx, wrap");

    while (true) {
      int result =
          JOptionPane.showConfirmDialog(
              chat,
              panel,
              "Edit Quassel Network",
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE);
      if (result != JOptionPane.OK_OPTION) {
        return Optional.empty();
      }

      String networkName = Objects.toString(nameField.getText(), "").trim();
      String serverHost = Objects.toString(hostField.getText(), "").trim();
      int serverPort = parseQuasselPortOrDefault(portField.getText(), tlsCheck.isSelected());
      if (serverHost.isEmpty()) {
        JOptionPane.showMessageDialog(
            chat, "Server host is required.", "Quassel network manager", JOptionPane.ERROR_MESSAGE);
        continue;
      }
      if (serverPort <= 0 || serverPort > 65535) {
        JOptionPane.showMessageDialog(
            chat,
            "Server port must be 1-65535.",
            "Quassel network manager",
            JOptionPane.ERROR_MESSAGE);
        continue;
      }
      return Optional.of(
          new QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
              networkName,
              serverHost,
              serverPort,
              tlsCheck.isSelected(),
              "",
              true,
              null,
              enabledCheck.isSelected()));
    }
  }

  private static int parseQuasselPortOrDefault(String raw, boolean useTls) {
    String text = Objects.toString(raw, "").trim();
    if (text.isEmpty()) {
      return useTls ? 6697 : 6667;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private record QuasselNetworkChoice(
      QuasselNetworkChoiceSummary summary, String idTokenOrName, String label) {
    QuasselNetworkChoice(QuasselCoreControlPort.QuasselCoreNetworkSummary summary) {
      this(
          new QuasselNetworkChoiceSummary(
              summary.networkId(),
              Objects.toString(summary.networkName(), "").trim(),
              Objects.toString(summary.serverHost(), "").trim(),
              summary.serverPort(),
              summary.useTls(),
              summary.enabled(),
              summary.connected()),
          SwingQuasselNetworkSupport.networkChoiceToken(summary),
          SwingQuasselNetworkSupport.renderChoiceLabel(summary));
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private record QuasselNetworkChoiceSummary(
      int networkId,
      String networkName,
      String serverHost,
      int serverPort,
      boolean useTls,
      boolean enabled,
      boolean connected) {}
}
