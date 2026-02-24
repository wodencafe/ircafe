package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.ConnectionState;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.UserActionRequest;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Adapter that exposes the Swing UI to the application layer.
 *
 */
@Component
@Lazy
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

  // Avoid rebuilding nick completions on every metadata refresh (away/account/hostmask) by
  // skipping completion updates if the nick *set* hasn't changed.
  private int lastNickCompletionSize = -1;
  private int lastNickCompletionHash = 0;

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
      SwingUtilities.invokeAndWait(() -> {
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
      ActiveInputRouter activeInputRouter
  ) {
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
    return Flowable.merge(
        users.privateMessageRequests(),
        chat.privateMessageRequests()
    );
  }

  @Override
    public Flowable<UserActionRequest> userActionRequests() {
    return Flowable.mergeArray(
        users.userActionRequests(),
        chat.userActionRequests()
    ).onBackpressureBuffer();
  }

  @Override
  public Flowable<String> outboundLines() {
    // The main chat dock forwards its embedded input into the outbound bus.
    // Other UI-originated command sources (e.g. transcript clicks) also flow through the bus.
    return outboundBus.stream();
  }

  @Override
  public boolean confirmMultilineSplitFallback(
      TargetRef target,
      int lineCount,
      long payloadUtf8Bytes,
      String reason
  ) {
    return onEdtCall(() -> {
      String where = (target == null) ? "this target" : target.target();
      String why = Objects.toString(reason, "").trim();
      StringBuilder body = new StringBuilder();
      body.append("This message cannot be sent using IRCv3 multiline for ").append(where).append(".\n\n");
      if (!why.isEmpty()) {
        body.append("Reason: ").append(why).append("\n\n");
      }
      body.append("Message size: ")
          .append(Math.max(0, lineCount))
          .append(" lines, ")
          .append(Math.max(0L, payloadUtf8Bytes))
          .append(" UTF-8 bytes.\n\n")
          .append("Send as separate lines instead?");

      Object[] options = {
          "Send " + Math.max(0, lineCount) + " Lines",
          "Cancel"
      };
      int choice = JOptionPane.showOptionDialog(
          chat,
          body.toString(),
          "Multiline Fallback",
          JOptionPane.DEFAULT_OPTION,
          JOptionPane.WARNING_MESSAGE,
          null,
          options,
          options[0]);
      return choice == 0;
    }, false);
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
  public Flowable<TargetRef> closeTargetRequests() {
    return serverTree.closeTargetRequests();
  }

  @Override
  public Flowable<TargetRef> clearLogRequests() {
    return serverTree.clearLogRequests();
  }

  @Override
  public void ensureTargetExists(TargetRef target) {
    onEdt(() -> {
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
    onEdt(() -> {
      serverTree.removeTarget(target);
      chat.clearTopic(target);
      transcripts.closeTarget(target);
    });
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
    // Not a UI action; no need to marshal to the EDT.
    notificationStore.recordHighlight(target, fromNick);
  }

  @Override
  public void recordRuleMatch(TargetRef target, String fromNick, String ruleLabel, String snippet) {
    // Not a UI action; no need to marshal to the EDT.
    notificationStore.recordRuleMatch(target, fromNick, ruleLabel, snippet);
  }

  @Override
  public void clearUnread(TargetRef target) {
    // Visiting a channel clears any stored highlight notifications for that channel.
    if (target != null && target.isChannel()) {
      // Not a UI action; no need to marshal to the EDT.
      notificationStore.clearChannel(target);
    }
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
    onEdt(() -> {
      users.setNicks(nicks);

      // Avoid streams here: in very large channels this runs on the EDT and can noticeably stall the UI.
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

      boolean sameNickSet = (size == lastNickCompletionSize) && (hash == lastNickCompletionHash);
      if (!sameNickSet) {
        lastNickCompletionSize = size;
        lastNickCompletionHash = hash;

        // Route nick completions to whichever input surface is currently active (main chat or pinned).
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
    onEdt(() -> {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return;
      serverTree.ensureNode(TargetRef.channelList(sid));
      chat.beginChannelList(sid, banner);
    });
  }

  @Override
  public void appendChannelListEntry(String serverId, String channel, int visibleUsers, String topic) {
    onEdt(() -> {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return;
      serverTree.ensureNode(TargetRef.channelList(sid));
      chat.appendChannelListEntry(sid, channel, visibleUsers, topic);
    });
  }

  @Override
  public void endChannelList(String serverId, String summary) {
    onEdt(() -> {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return;
      serverTree.ensureNode(TargetRef.channelList(sid));
      chat.endChannelList(sid, summary);
    });
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
  public void setServerConnectionDiagnostics(String serverId, String lastError, Long nextRetryEpochMs) {
    onEdt(() -> serverTree.setServerConnectionDiagnostics(serverId, lastError, nextRetryEpochMs));
  }

  @Override
  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    onEdt(() -> {
      serverTree.setPrivateMessageOnlineState(serverId, nick, online);
      chat.setPrivateMessageOnlineState(serverId, nick, online);
    });
  }

  @Override
  public void clearPrivateMessageOnlineStates(String serverId) {
    onEdt(() -> {
      serverTree.clearPrivateMessageOnlineStates(serverId);
      chat.clearPrivateMessageOnlineStates(serverId);
    });
  }

  @Override
  public void setServerConnectedIdentity(String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    onEdt(() -> serverTree.setServerConnectedIdentity(serverId, connectedHost, connectedPort, nick, at));
  }

  @Override
  public void setServerIrcv3Capability(String serverId, String capability, String subcommand, boolean enabled) {
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
      String channelModes
  ) {
    onEdt(() -> serverTree.setServerVersionDetails(serverId, serverName, serverVersion, userModes, channelModes));
  }

  @Override
  public void setInputEnabled(boolean enabled) {
    onEdt(() -> {
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
  public void appendChatAt(TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
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
      Map<String, String> ircv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendChatAt(target, from, text, outgoingLocalEcho, ts, messageId, ircv3Tags));
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
      String notificationRuleHighlightColor
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendChatAt(
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
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text
  ) {
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
      Map<String, String> ircv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    if (SwingUtilities.isEventDispatchThread()) {
      return transcripts.resolvePendingOutgoingChat(target, pendingId, from, text, ts, messageId, ircv3Tags);
    }
    final boolean[] out = new boolean[] {false};
    try {
      SwingUtilities.invokeAndWait(
          () -> out[0] = transcripts.resolvePendingOutgoingChat(target, pendingId, from, text, ts, messageId, ircv3Tags));
    } catch (Exception ignored) {
      out[0] = false;
    }
    return out[0];
  }

  @Override
  public void failPendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String reason
  ) {
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
  public void appendAction(TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    onEdt(() -> transcripts.appendAction(target, from, action, outgoingLocalEcho));
  }

  @Override
  public void appendActionAt(TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
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
      Map<String, String> ircv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendActionAt(target, from, action, outgoingLocalEcho, ts, messageId, ircv3Tags));
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
      String notificationRuleHighlightColor
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendActionAt(
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
  public void appendPresence(TargetRef target, cafe.woden.ircclient.app.PresenceEvent event) {
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
      Map<String, String> ircv3Tags
  ) {
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
      Map<String, String> ircv3Tags
  ) {
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
    onEdt(() -> {
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
    onEdt(() -> {
      if (serverTree != null) {
        serverTree.markTypingActivity(target, state);
      }
    });
  }

  @Override
  public void showUsersTypingIndicator(TargetRef target, String nick, String state) {
    onEdt(() -> {
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
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String reaction
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.applyMessageReaction(target, targetMessageId, reaction, fromNick, ts));
  }

  @Override
  public boolean isOwnMessage(TargetRef target, String targetMessageId) {
    if (SwingUtilities.isEventDispatchThread()) {
      return transcripts.isOwnMessage(target, targetMessageId);
    }
    final boolean[] out = new boolean[] {false};
    try {
      SwingUtilities.invokeAndWait(() -> out[0] = transcripts.isOwnMessage(target, targetMessageId));
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
      Map<String, String> replacementIrcv3Tags
  ) {
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
          () -> out[0] = transcripts.applyMessageEdit(
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
      Map<String, String> replacementIrcv3Tags
  ) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    if (SwingUtilities.isEventDispatchThread()) {
      return transcripts.applyMessageRedaction(
          target,
          targetMessageId,
          fromNick,
          ts,
          replacementMessageId,
          replacementIrcv3Tags);
    }
    final boolean[] out = new boolean[] {false};
    try {
      SwingUtilities.invokeAndWait(
          () -> out[0] = transcripts.applyMessageRedaction(
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
    onEdt(() -> {
      if (chat != null) {
        chat.normalizeIrcv3CapabilityUiState(serverId, capability);
      }
      if (chatDockManager != null) {
        chatDockManager.normalizeIrcv3CapabilityUiState(serverId, capability);
      }
    });
  }
}
