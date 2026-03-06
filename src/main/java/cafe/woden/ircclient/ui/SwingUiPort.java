package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.shell.StatusBar;
import com.formdev.flatlaf.FlatClientProperties;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Adapter that exposes the Swing UI to the application layer. */
@Component
@Lazy
@InterfaceLayer
public class SwingUiPort implements UiPort {
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final ChatTranscriptStore transcripts;
  private final MentionPatternRegistry mentions;
  private final NotificationStore notificationStore;
  private final UserListDockable users;
  private final StatusBar statusBar;
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;
  private final TargetActivationBus activationBus;
  private final OutboundLineBus outboundBus;
  private final ChatDockManager chatDockManager;
  private final ActiveInputRouter activeInputRouter;
  private final FlowableProcessor<String> quasselNetworkManagerRequestsFromApp =
      PublishProcessor.<String>create().toSerialized();

  // Avoid rebuilding nick completions on every metadata refresh (away/account/hostmask) by
  // skipping completion updates if the nick *set* hasn't changed.
  private int lastNickCompletionSize = -1;
  private int lastNickCompletionHash = 0;
  private final Object channelListAppendLock = new Object();
  private final Map<String, ArrayList<ChannelListPanel.ListEntryRow>>
      pendingChannelListEntriesByServer = new HashMap<>();
  private boolean channelListAppendFlushScheduled;

  private void onEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }

  private <T> T onEdtCall(Supplier<T> supplier, T fallback) {
    if (supplier == null) return fallback;
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception ignored) {
        return fallback;
      }
    }

    AtomicReference<T> out = new AtomicReference<>(fallback);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              out.set(supplier.get());
            } catch (Exception ignored) {
              out.set(fallback);
            }
          });
    } catch (Exception ignored) {
      return fallback;
    }
    return out.get();
  }

  private void enqueueChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    synchronized (channelListAppendLock) {
      pendingChannelListEntriesByServer
          .computeIfAbsent(sid, __ -> new ArrayList<>())
          .add(new ChannelListPanel.ListEntryRow(ch, Math.max(0, visibleUsers), topic));
      if (channelListAppendFlushScheduled) return;
      channelListAppendFlushScheduled = true;
    }
    onEdt(this::flushPendingChannelListEntriesOnEdt);
  }

  private void flushPendingChannelListEntriesOnEdt() {
    Map<String, ArrayList<ChannelListPanel.ListEntryRow>> drained = new HashMap<>();
    synchronized (channelListAppendLock) {
      if (pendingChannelListEntriesByServer.isEmpty()) {
        channelListAppendFlushScheduled = false;
        return;
      }
      for (Map.Entry<String, ArrayList<ChannelListPanel.ListEntryRow>> e :
          pendingChannelListEntriesByServer.entrySet()) {
        drained.put(e.getKey(), new ArrayList<>(e.getValue()));
      }
      pendingChannelListEntriesByServer.clear();
      channelListAppendFlushScheduled = false;
    }

    for (Map.Entry<String, ArrayList<ChannelListPanel.ListEntryRow>> e : drained.entrySet()) {
      String sid = Objects.toString(e.getKey(), "").trim();
      if (sid.isEmpty()) continue;
      List<ChannelListPanel.ListEntryRow> rows = e.getValue();
      if (rows == null || rows.isEmpty()) continue;
      serverTree.ensureNode(TargetRef.channelList(sid));
      chat.appendChannelListEntries(sid, List.copyOf(rows));
    }

    boolean reschedule;
    synchronized (channelListAppendLock) {
      reschedule = !pendingChannelListEntriesByServer.isEmpty() && !channelListAppendFlushScheduled;
      if (reschedule) channelListAppendFlushScheduled = true;
    }
    if (reschedule) {
      onEdt(this::flushPendingChannelListEntriesOnEdt);
    }
  }

  public SwingUiPort(
      ServerTreeDockable serverTree,
      ChatDockable chat,
      ChatTranscriptStore transcripts,
      MentionPatternRegistry mentions,
      NotificationStore notificationStore,
      UserListDockable users,
      StatusBar statusBar,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      ChatDockManager chatDockManager,
      ActiveInputRouter activeInputRouter) {
    this.serverTree = serverTree;
    this.chat = chat;
    this.transcripts = transcripts;
    this.mentions = mentions;
    this.notificationStore = notificationStore;
    this.users = users;
    this.statusBar = statusBar;
    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;
    this.activationBus = activationBus;
    this.outboundBus = outboundBus;
    this.chatDockManager = chatDockManager;
    this.activeInputRouter = activeInputRouter;
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
    // Users can request PMs from multiple UI surfaces (user list, transcript nick clicks, etc.)
    return Flowable.merge(users.privateMessageRequests(), chat.privateMessageRequests());
  }

  @Override
  public Flowable<UserActionRequest> userActionRequests() {
    return Flowable.mergeArray(users.userActionRequests(), chat.userActionRequests())
        .onBackpressureBuffer();
  }

  @Override
  public Flowable<String> outboundLines() {
    // The main chat dock forwards its embedded input into the outbound bus.
    // Other UI-originated command sources (e.g. transcript clicks) also flow through the bus.
    return outboundBus.stream();
  }

  @Override
  public boolean confirmMultilineSplitFallback(
      TargetRef target, int lineCount, long payloadUtf8Bytes, String reason) {
    return onEdtCall(
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
  public Flowable<String> quasselSetupRequests() {
    return serverTree.quasselSetupRequests();
  }

  @Override
  public Flowable<String> quasselNetworkManagerRequests() {
    return Flowable.mergeArray(
            serverTree.quasselNetworkManagerRequests(),
            quasselNetworkManagerRequestsFromApp.onBackpressureLatest())
        .onBackpressureBuffer();
  }

  @Override
  public void openQuasselNetworkManager(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    quasselNetworkManagerRequestsFromApp.onNext(sid);
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
  public void ensureTargetExists(TargetRef target) {
    onEdt(
        () -> {
          transcripts.ensureTargetExists(target);
          serverTree.ensureNode(target);
        });
  }

  @Override
  public void selectTarget(TargetRef target) {
    onEdt(() -> serverTree.selectTarget(target));
  }

  @Override
  public void closeTarget(TargetRef target) {
    onEdt(
        () -> {
          serverTree.removeTarget(target);
          chat.clearTopic(target);
          transcripts.closeTarget(target);
          chat.onTargetClosed(target);
        });
  }

  @Override
  public void setChannelDisconnected(TargetRef target, boolean detached) {
    onEdt(() -> serverTree.setChannelDisconnected(target, detached));
  }

  @Override
  public void setChannelDisconnected(TargetRef target, boolean detached, String warningReason) {
    onEdt(() -> serverTree.setChannelDisconnected(target, detached, warningReason));
  }

  @Override
  public boolean isChannelDisconnected(TargetRef target) {
    return onEdtCall(() -> serverTree.isChannelDisconnected(target), false);
  }

  @Override
  public boolean isChannelMuted(TargetRef target) {
    return onEdtCall(() -> serverTree.isChannelMuted(target), false);
  }

  @Override
  public void markUnread(TargetRef target) {
    onEdt(() -> serverTree.markUnread(target));
  }

  @Override
  public void markHighlight(TargetRef target) {
    onEdt(() -> serverTree.markHighlight(target));
  }

  @Override
  public void recordHighlight(TargetRef target, String fromNick) {
    recordHighlight(target, fromNick, "");
  }

  @Override
  public void recordHighlight(TargetRef target, String fromNick, String snippet) {
    // Not a UI action; no need to marshal to the EDT.
    notificationStore.recordHighlight(target, fromNick, snippet);
  }

  @Override
  public void recordRuleMatch(TargetRef target, String fromNick, String ruleLabel, String snippet) {
    // Not a UI action; no need to marshal to the EDT.
    notificationStore.recordRuleMatch(target, fromNick, ruleLabel, snippet);
  }

  @Override
  public void clearUnread(TargetRef target) {
    onEdt(() -> serverTree.clearUnread(target));
  }

  @Override
  public void clearTranscript(TargetRef target) {
    onEdt(() -> transcripts.clearTarget(target));
  }

  @Override
  public void setChatActiveTarget(TargetRef target) {
    onEdt(() -> chat.setActiveTarget(target));
  }

  @Override
  public void setChatCurrentNick(String serverId, String nick) {
    onEdt(() -> mentions.setCurrentNick(serverId, nick));
  }

  @Override
  public void setChannelTopic(TargetRef target, String topic) {
    onEdt(() -> chat.setTopic(target, topic));
  }

  @Override
  public void setUsersChannel(TargetRef target) {
    onEdt(() -> users.setChannel(target));
  }

  @Override
  public void setUsersNicks(List<NickInfo> nicks) {
    onEdt(
        () -> {
          users.setNicks(nicks);

          // Avoid streams here: in very large channels this runs on the EDT and can noticeably
          // stall the UI.
          java.util.List<String> names;
          int hash = 1;
          int size = 0;
          if (nicks == null || nicks.isEmpty()) {
            names = java.util.List.of();
          } else {
            java.util.ArrayList<String> tmp = new java.util.ArrayList<>(nicks.size());
            for (NickInfo ni : nicks) {
              if (ni == null) continue;
              String nick = ni.nick();
              if (nick == null) continue;
              tmp.add(nick);
              String lower = nick.toLowerCase(Locale.ROOT);
              hash = 31 * hash + lower.hashCode();
              size++;
            }
            names = java.util.List.copyOf(tmp);
          }

          boolean sameNickSet =
              (size == lastNickCompletionSize) && (hash == lastNickCompletionHash);
          if (!sameNickSet) {
            lastNickCompletionSize = size;
            lastNickCompletionHash = hash;

            // Route nick completions to whichever input surface is currently active (main chat or
            // pinned).
            if (activeInputRouter != null && activeInputRouter.active() != null) {
              activeInputRouter.setNickCompletionsForActive(names);
            } else {
              chat.setNickCompletions(names);
            }
          }
        });
  }

  @Override
  public void beginChannelList(String serverId, String banner) {
    onEdt(
        () -> {
          flushPendingChannelListEntriesOnEdt();
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isEmpty()) return;
          serverTree.ensureNode(TargetRef.channelList(sid));
          chat.beginChannelList(sid, banner);
        });
  }

  @Override
  public void appendChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {
    enqueueChannelListEntry(serverId, channel, visibleUsers, topic);
  }

  @Override
  public void endChannelList(String serverId, String summary) {
    onEdt(
        () -> {
          flushPendingChannelListEntriesOnEdt();
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isEmpty()) return;
          serverTree.ensureNode(TargetRef.channelList(sid));
          chat.endChannelList(sid, summary);
        });
  }

  @Override
  public void beginChannelBanList(String serverId, String channel) {
    onEdt(() -> chat.beginChannelBanList(serverId, channel));
  }

  @Override
  public void appendChannelBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {
    onEdt(() -> chat.appendChannelBanListEntry(serverId, channel, mask, setBy, setAtEpochSeconds));
  }

  @Override
  public void endChannelBanList(String serverId, String channel, String summary) {
    onEdt(() -> chat.endChannelBanList(serverId, channel, summary));
  }

  @Override
  public void setChannelModeSnapshot(
      String serverId, String channel, String rawModes, String friendlySummary) {
    onEdt(() -> chat.setChannelModeSnapshot(serverId, channel, rawModes, friendlySummary));
  }

  @Override
  public void setStatusBarChannel(String channel) {
    onEdt(() -> statusBar.setChannel(channel));
  }

  @Override
  public void setStatusBarCounts(int users, int ops) {
    onEdt(() -> statusBar.setCounts(users, ops));
  }

  @Override
  public void setStatusBarServer(String serverText) {
    onEdt(() -> statusBar.setServer(serverText));
  }

  @Override
  public void enqueueStatusNotice(String text, TargetRef clickTarget) {
    onEdt(
        () -> {
          Runnable onClick = null;
          if (clickTarget != null) {
            onClick =
                () -> {
                  transcripts.ensureTargetExists(clickTarget);
                  serverTree.ensureNode(clickTarget);
                  serverTree.selectTarget(clickTarget);
                };
          }
          statusBar.enqueueNotification(text, onClick);
        });
  }

  @Override
  public Optional<cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreSetupRequest>
      promptQuasselCoreSetup(
          String serverId,
          cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreSetupPrompt prompt) {
    return onEdtCall(
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
          adminUserField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "admin");
          javax.swing.JPasswordField adminPasswordField = new javax.swing.JPasswordField(20);
          adminPasswordField.putClientProperty(
              FlatClientProperties.PLACEHOLDER_TEXT, "admin password");
          adminPasswordField.putClientProperty("JPasswordField.showRevealButton", true);
          adminPasswordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton:true");

          javax.swing.JComboBox<String> storageCombo =
              new javax.swing.JComboBox<>(storageOptions.toArray(String[]::new));
          javax.swing.JComboBox<String> authCombo =
              new javax.swing.JComboBox<>(authOptions.toArray(String[]::new));
          storageCombo.setEditable(storageOptions.size() <= 1);
          authCombo.setEditable(authOptions.size() <= 1);

          javax.swing.JPanel panel =
              new javax.swing.JPanel(
                  new net.miginfocom.swing.MigLayout(
                      "insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
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
                javax.swing.JOptionPane.showConfirmDialog(
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
              javax.swing.JOptionPane.showMessageDialog(
                  chat,
                  "Admin user is required.",
                  "Quassel setup",
                  javax.swing.JOptionPane.ERROR_MESSAGE);
              continue;
            }
            if (adminPassword.isBlank()) {
              javax.swing.JOptionPane.showMessageDialog(
                  chat,
                  "Admin password is required.",
                  "Quassel setup",
                  javax.swing.JOptionPane.ERROR_MESSAGE);
              continue;
            }
            if (storage.isEmpty()) {
              javax.swing.JOptionPane.showMessageDialog(
                  chat,
                  "Storage backend is required.",
                  "Quassel setup",
                  javax.swing.JOptionPane.ERROR_MESSAGE);
              continue;
            }
            if (auth.isEmpty()) {
              javax.swing.JOptionPane.showMessageDialog(
                  chat,
                  "Authenticator is required.",
                  "Quassel setup",
                  javax.swing.JOptionPane.ERROR_MESSAGE);
              continue;
            }

            if (!detail.isEmpty()) {
              statusBar.enqueueNotification("Submitting Quassel setup: " + detail, null);
            }
            return Optional.of(
                new cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreSetupRequest(
                    adminUser, adminPassword, storage, auth, Map.of(), Map.of()));
          }
        },
        Optional.empty());
  }

  @Override
  public Optional<QuasselNetworkManagerAction> promptQuasselNetworkManagerAction(
      String serverId,
      List<cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    return onEdtCall(
        () -> {
          String sid = Objects.toString(serverId, "").trim();
          List<cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkSummary>
              safeNetworks = networks == null ? List.of() : List.copyOf(networks);

          while (true) {
            javax.swing.DefaultListModel<QuasselNetworkChoice> model =
                new javax.swing.DefaultListModel<>();
            for (cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkSummary summary :
                safeNetworks) {
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
                    new net.miginfocom.swing.MigLayout(
                        "insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]"));
            panel.add(new javax.swing.JLabel("Select a network and choose an action."), "growx");
            panel.add(scroll, "grow, push");

            Object[] options = {
              "Connect", "Disconnect", "Add...", "Edit...", "Remove", "Refresh", "Close"
            };
            String title =
                sid.isEmpty() ? "Quassel Network Manager" : ("Quassel Network Manager - " + sid);
            int choice =
                javax.swing.JOptionPane.showOptionDialog(
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
              Optional<
                      cafe.woden.ircclient.irc.QuasselCoreControlPort
                          .QuasselCoreNetworkCreateRequest>
                  addRequest = promptQuasselNetworkCreateRequest();
              if (addRequest.isEmpty()) {
                continue;
              }
              return Optional.of(QuasselNetworkManagerAction.add(addRequest.orElseThrow()));
            }

            QuasselNetworkChoice selected = list.getSelectedValue();
            if (selected == null) {
              javax.swing.JOptionPane.showMessageDialog(
                  chat,
                  "Select a network first.",
                  "Quassel network manager",
                  javax.swing.JOptionPane.WARNING_MESSAGE);
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
              Optional<
                      cafe.woden.ircclient.irc.QuasselCoreControlPort
                          .QuasselCoreNetworkUpdateRequest>
                  updateRequest = promptQuasselNetworkUpdateRequest(selected.summary());
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

  private Optional<cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest>
      promptQuasselNetworkCreateRequest() {
    javax.swing.JTextField nameField = new javax.swing.JTextField(28);
    javax.swing.JTextField hostField = new javax.swing.JTextField(28);
    javax.swing.JTextField portField = new javax.swing.JTextField("6697", 8);
    javax.swing.JCheckBox tlsCheck = new javax.swing.JCheckBox("Use TLS", true);
    javax.swing.JCheckBox enabledCheck = new javax.swing.JCheckBox("Enabled", true);

    javax.swing.JPanel panel =
        new javax.swing.JPanel(
            new net.miginfocom.swing.MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
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
          javax.swing.JOptionPane.showConfirmDialog(
              chat,
              panel,
              "Add Quassel Network",
              javax.swing.JOptionPane.OK_CANCEL_OPTION,
              javax.swing.JOptionPane.PLAIN_MESSAGE);
      if (result != javax.swing.JOptionPane.OK_OPTION) {
        return Optional.empty();
      }

      String networkName = Objects.toString(nameField.getText(), "").trim();
      String serverHost = Objects.toString(hostField.getText(), "").trim();
      int serverPort = parseQuasselPortOrDefault(portField.getText(), tlsCheck.isSelected());
      if (networkName.isEmpty()) {
        javax.swing.JOptionPane.showMessageDialog(
            chat,
            "Network name is required.",
            "Quassel network manager",
            javax.swing.JOptionPane.ERROR_MESSAGE);
        continue;
      }
      if (serverHost.isEmpty()) {
        javax.swing.JOptionPane.showMessageDialog(
            chat,
            "Server host is required.",
            "Quassel network manager",
            javax.swing.JOptionPane.ERROR_MESSAGE);
        continue;
      }
      if (serverPort <= 0 || serverPort > 65535) {
        javax.swing.JOptionPane.showMessageDialog(
            chat,
            "Server port must be 1-65535.",
            "Quassel network manager",
            javax.swing.JOptionPane.ERROR_MESSAGE);
        continue;
      }
      return Optional.of(
          new cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
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

  private Optional<cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest>
      promptQuasselNetworkUpdateRequest(QuasselNetworkChoiceSummary summary) {
    String defaultName = summary == null ? "" : summary.networkName();
    String defaultHost = summary == null ? "" : summary.serverHost();
    int defaultPort = summary == null ? 0 : summary.serverPort();
    boolean defaultTls = summary != null && summary.useTls();
    boolean defaultEnabled = summary == null || summary.enabled();

    javax.swing.JTextField nameField = new javax.swing.JTextField(defaultName, 28);
    nameField.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, "(blank keeps existing name)");
    javax.swing.JTextField hostField = new javax.swing.JTextField(defaultHost, 28);
    javax.swing.JTextField portField =
        new javax.swing.JTextField(
            defaultPort > 0 ? Integer.toString(defaultPort) : (defaultTls ? "6697" : "6667"), 8);
    javax.swing.JCheckBox tlsCheck = new javax.swing.JCheckBox("Use TLS", defaultTls);
    javax.swing.JCheckBox enabledCheck = new javax.swing.JCheckBox("Enabled", defaultEnabled);

    javax.swing.JPanel panel =
        new javax.swing.JPanel(
            new net.miginfocom.swing.MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]6[]6[]6[]"));
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
          javax.swing.JOptionPane.showConfirmDialog(
              chat,
              panel,
              "Edit Quassel Network",
              javax.swing.JOptionPane.OK_CANCEL_OPTION,
              javax.swing.JOptionPane.PLAIN_MESSAGE);
      if (result != javax.swing.JOptionPane.OK_OPTION) {
        return Optional.empty();
      }

      String networkName = Objects.toString(nameField.getText(), "").trim();
      String serverHost = Objects.toString(hostField.getText(), "").trim();
      int serverPort = parseQuasselPortOrDefault(portField.getText(), tlsCheck.isSelected());
      if (serverHost.isEmpty()) {
        javax.swing.JOptionPane.showMessageDialog(
            chat,
            "Server host is required.",
            "Quassel network manager",
            javax.swing.JOptionPane.ERROR_MESSAGE);
        continue;
      }
      if (serverPort <= 0 || serverPort > 65535) {
        javax.swing.JOptionPane.showMessageDialog(
            chat,
            "Server port must be 1-65535.",
            "Quassel network manager",
            javax.swing.JOptionPane.ERROR_MESSAGE);
        continue;
      }
      return Optional.of(
          new cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
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
    QuasselNetworkChoice(
        cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkSummary s) {
      this(
          new QuasselNetworkChoiceSummary(
              s.networkId(),
              Objects.toString(s.networkName(), "").trim(),
              Objects.toString(s.serverHost(), "").trim(),
              s.serverPort(),
              s.useTls(),
              s.enabled(),
              s.connected()),
          networkChoiceToken(s),
          renderQuasselNetworkChoiceLabel(s));
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

  private static String renderQuasselNetworkChoiceLabel(
      cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkSummary summary) {
    if (summary == null) return "(unknown network)";
    String name = Objects.toString(summary.networkName(), "").trim();
    if (name.isEmpty()) name = "network-" + summary.networkId();
    String host = Objects.toString(summary.serverHost(), "").trim();
    int port = summary.serverPort();
    StringBuilder line = new StringBuilder();
    line.append("[").append(summary.networkId()).append("] ").append(name);
    line.append(" - ").append(summary.connected() ? "connected" : "disconnected");
    if (!summary.enabled()) line.append(", disabled");
    if (!host.isEmpty() && port > 0) {
      line.append(" @ ").append(host).append(":").append(port);
      line.append(summary.useTls() ? " tls" : " plain");
    }
    return line.toString();
  }

  private static String networkChoiceToken(
      cafe.woden.ircclient.irc.QuasselCoreControlPort.QuasselCoreNetworkSummary summary) {
    if (summary == null) return "";
    String token =
        summary.networkId() >= 0
            ? Integer.toString(summary.networkId())
            : Objects.toString(summary.networkName(), "").trim();
    return token.isEmpty() ? ("network-" + summary.networkId()) : token;
  }

  @Override
  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    onEdt(() -> serverTree.setConnectionControlsEnabled(connectEnabled, disconnectEnabled));
  }

  @Override
  public void setConnectionStatusText(String text) {
    onEdt(() -> serverTree.setStatusText(text));
  }

  @Override
  public void setServerConnectionState(String serverId, ConnectionState state) {
    onEdt(() -> serverTree.setServerConnectionState(serverId, state));
  }

  @Override
  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    onEdt(() -> serverTree.setServerDesiredOnline(serverId, desiredOnline));
  }

  @Override
  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    onEdt(() -> serverTree.setServerConnectionDiagnostics(serverId, lastError, nextRetryEpochMs));
  }

  @Override
  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    onEdt(
        () -> {
          serverTree.setPrivateMessageOnlineState(serverId, nick, online);
          chat.setPrivateMessageOnlineState(serverId, nick, online);
        });
  }

  @Override
  public void clearPrivateMessageOnlineStates(String serverId) {
    onEdt(
        () -> {
          serverTree.clearPrivateMessageOnlineStates(serverId);
          chat.clearPrivateMessageOnlineStates(serverId);
        });
  }

  @Override
  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    onEdt(
        () ->
            serverTree.setServerConnectedIdentity(
                serverId, connectedHost, connectedPort, nick, at));
  }

  @Override
  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    onEdt(() -> serverTree.setServerIrcv3Capability(serverId, capability, subcommand, enabled));
  }

  @Override
  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    onEdt(() -> serverTree.setServerIsupportToken(serverId, tokenName, tokenValue));
  }

  @Override
  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    onEdt(
        () ->
            serverTree.setServerVersionDetails(
                serverId, serverName, serverVersion, userModes, channelModes));
  }

  @Override
  public void setInputEnabled(boolean enabled) {
    onEdt(
        () -> {
          chat.setInputEnabled(enabled);
          if (chatDockManager != null) {
            chatDockManager.setPinnedInputsEnabled(enabled);
          }
        });
  }

  @Override
  public void appendChat(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendChat(target, from, text, false));
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    onEdt(() -> transcripts.appendChat(target, from, text, outgoingLocalEcho));
  }

  @Override
  public void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendChatAt(target, from, text, outgoingLocalEcho, ts));
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(
        () ->
            transcripts.appendChatAt(
                target, from, text, outgoingLocalEcho, ts, messageId, ircv3Tags));
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(
        () ->
            transcripts.appendChatAt(
                target,
                from,
                text,
                outgoingLocalEcho,
                ts,
                messageId,
                ircv3Tags,
                notificationRuleHighlightColor));
  }

  @Override
  public void appendPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendPendingOutgoingChat(target, pendingId, from, text, ts));
  }

  @Override
  public boolean resolvePendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    if (SwingUtilities.isEventDispatchThread()) {
      return transcripts.resolvePendingOutgoingChat(
          target, pendingId, from, text, ts, messageId, ircv3Tags);
    }
    final boolean[] out = new boolean[] {false};
    try {
      SwingUtilities.invokeAndWait(
          () ->
              out[0] =
                  transcripts.resolvePendingOutgoingChat(
                      target, pendingId, from, text, ts, messageId, ircv3Tags));
    } catch (Exception ignored) {
      out[0] = false;
    }
    return out[0];
  }

  @Override
  public void failPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text, String reason) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.failPendingOutgoingChat(target, pendingId, from, text, ts, reason));
  }

  @Override
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendSpoilerChat(target, from, text));
  }

  @Override
  public void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendSpoilerChatFromHistory(target, from, text, ts));
  }

  @Override
  public void appendAction(TargetRef target, String from, String action) {
    onEdt(() -> transcripts.appendAction(target, from, action, false));
  }

  @Override
  public void appendAction(
      TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    onEdt(() -> transcripts.appendAction(target, from, action, outgoingLocalEcho));
  }

  @Override
  public void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendActionAt(target, from, action, outgoingLocalEcho, ts));
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(
        () ->
            transcripts.appendActionAt(
                target, from, action, outgoingLocalEcho, ts, messageId, ircv3Tags));
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(
        () ->
            transcripts.appendActionAt(
                target,
                from,
                action,
                outgoingLocalEcho,
                ts,
                messageId,
                ircv3Tags,
                notificationRuleHighlightColor));
  }

  @Override
  public void appendPresence(TargetRef target, cafe.woden.ircclient.app.api.PresenceEvent event) {
    onEdt(() -> transcripts.appendPresence(target, event));
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendNotice(target, from, text));
  }

  @Override
  public void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendNoticeAt(target, from, text, ts));
  }

  @Override
  public void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendNoticeAt(target, from, text, ts, messageId, ircv3Tags));
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendStatus(target, from, text));
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendStatusAt(target, from, text, ts));
  }

  @Override
  public void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendStatusAt(target, from, text, ts, messageId, ircv3Tags));
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendError(target, from, text));
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendErrorAt(target, from, text, ts));
  }

  @Override
  public void showTypingIndicator(TargetRef target, String nick, String state) {
    onEdt(
        () -> {
          if (chat != null) {
            chat.showTypingIndicator(target, nick, state);
          }
          if (chatDockManager != null) {
            chatDockManager.showTypingIndicator(target, nick, state);
          }
        });
  }

  @Override
  public void showTypingActivity(TargetRef target, String state) {
    onEdt(
        () -> {
          if (serverTree != null) {
            serverTree.markTypingActivity(target, state);
          }
        });
  }

  @Override
  public void showUsersTypingIndicator(TargetRef target, String nick, String state) {
    onEdt(
        () -> {
          if (users != null) {
            users.showTypingIndicator(target, nick, state);
          }
        });
  }

  @Override
  public void setReadMarker(TargetRef target, long markerEpochMs) {
    onEdt(() -> transcripts.updateReadMarker(target, markerEpochMs));
  }

  @Override
  public void applyMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.applyMessageReaction(target, targetMessageId, reaction, fromNick, ts));
  }

  @Override
  public void removeMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.removeMessageReaction(target, targetMessageId, reaction, fromNick, ts));
  }

  @Override
  public boolean isOwnMessage(TargetRef target, String targetMessageId) {
    if (SwingUtilities.isEventDispatchThread()) {
      return transcripts.isOwnMessage(target, targetMessageId);
    }
    final boolean[] out = new boolean[] {false};
    try {
      SwingUtilities.invokeAndWait(
          () -> out[0] = transcripts.isOwnMessage(target, targetMessageId));
    } catch (Exception ignored) {
      out[0] = false;
    }
    return out[0];
  }

  @Override
  public boolean applyMessageEdit(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String editedText,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    if (SwingUtilities.isEventDispatchThread()) {
      return transcripts.applyMessageEdit(
          target,
          targetMessageId,
          editedText,
          fromNick,
          ts,
          replacementMessageId,
          replacementIrcv3Tags);
    }
    final boolean[] out = new boolean[] {false};
    try {
      SwingUtilities.invokeAndWait(
          () ->
              out[0] =
                  transcripts.applyMessageEdit(
                      target,
                      targetMessageId,
                      editedText,
                      fromNick,
                      ts,
                      replacementMessageId,
                      replacementIrcv3Tags));
    } catch (Exception ignored) {
      out[0] = false;
    }
    return out[0];
  }

  @Override
  public boolean applyMessageRedaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    if (SwingUtilities.isEventDispatchThread()) {
      return transcripts.applyMessageRedaction(
          target, targetMessageId, fromNick, ts, replacementMessageId, replacementIrcv3Tags);
    }
    final boolean[] out = new boolean[] {false};
    try {
      SwingUtilities.invokeAndWait(
          () ->
              out[0] =
                  transcripts.applyMessageRedaction(
                      target,
                      targetMessageId,
                      fromNick,
                      ts,
                      replacementMessageId,
                      replacementIrcv3Tags));
    } catch (Exception ignored) {
      out[0] = false;
    }
    return out[0];
  }

  @Override
  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    onEdt(
        () -> {
          if (chat != null) {
            chat.normalizeIrcv3CapabilityUiState(serverId, capability);
          }
          if (chatDockManager != null) {
            chatDockManager.normalizeIrcv3CapabilityUiState(serverId, capability);
          }
        });
  }
}
