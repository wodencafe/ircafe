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
import java.time.Instant;
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
    onEdt(() -> transcripts.appendChatFromHistory(target, from, text, outgoingLocalEcho, ts));
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
    onEdt(() -> transcripts.appendActionFromHistory(target, from, action, outgoingLocalEcho, ts));
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
    onEdt(() -> transcripts.appendNoticeFromHistory(target, from, text, ts));
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendStatus(target, from, text));
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendStatusFromHistory(target, from, text, ts));
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendError(target, from, text));
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    onEdt(() -> transcripts.appendErrorFromHistory(target, from, text, ts));
  }
}
