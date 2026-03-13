package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.UiViewStatePort;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.shell.StatusBar;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Swing adapter for non-transcript UI state and server metadata updates. */
final class SwingUiViewStatePort implements UiViewStatePort {

  private final SwingEdtExecutor edt;
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final ChatTranscriptStore transcripts;
  private final MentionPatternRegistry mentions;
  private final NotificationStore notificationStore;
  private final UserListDockable users;
  private final StatusBar statusBar;
  private final ChatDockManager chatDockManager;
  private final ActiveInputRouter activeInputRouter;
  private final Object quasselNetworkTooltipLock = new Object();
  private final Map<String, Map<String, String>> quasselNetworkTooltipByServer = new HashMap<>();

  // Avoid rebuilding nick completions on every metadata refresh (away/account/hostmask) by
  // skipping completion updates if the nick *set* hasn't changed.
  private int lastNickCompletionSize = -1;
  private int lastNickCompletionHash = 0;

  SwingUiViewStatePort(
      SwingEdtExecutor edt,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      ChatTranscriptStore transcripts,
      MentionPatternRegistry mentions,
      NotificationStore notificationStore,
      UserListDockable users,
      StatusBar statusBar,
      ChatDockManager chatDockManager,
      ActiveInputRouter activeInputRouter) {
    this.edt = Objects.requireNonNull(edt, "edt");
    this.serverTree = Objects.requireNonNull(serverTree, "serverTree");
    this.chat = Objects.requireNonNull(chat, "chat");
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
    this.mentions = Objects.requireNonNull(mentions, "mentions");
    this.notificationStore = Objects.requireNonNull(notificationStore, "notificationStore");
    this.users = Objects.requireNonNull(users, "users");
    this.statusBar = Objects.requireNonNull(statusBar, "statusBar");
    this.chatDockManager = chatDockManager;
    this.activeInputRouter = activeInputRouter;
    this.serverTree.setQuasselNetworkTooltipProvider(this::quasselNetworkTooltip);
  }

  @Override
  public void ensureTargetExists(TargetRef target) {
    edt.run(
        () -> {
          transcripts.ensureTargetExists(target);
          serverTree.ensureNode(target);
        });
  }

  @Override
  public void selectTarget(TargetRef target) {
    edt.run(() -> serverTree.selectTarget(target));
  }

  @Override
  public void closeTarget(TargetRef target) {
    edt.run(
        () -> {
          serverTree.removeTarget(target);
          chat.clearTopic(target);
          transcripts.closeTarget(target);
          chat.onTargetClosed(target);
        });
  }

  @Override
  public void setChannelDisconnected(TargetRef target, boolean detached) {
    edt.run(
        () -> {
          serverTree.setChannelDisconnected(target, detached);
          chat.refreshDisplayedTargetInputEnabled();
          if (chatDockManager != null) {
            chatDockManager.refreshPinnedInputEnabled(target);
          }
        });
  }

  @Override
  public void setChannelDisconnected(TargetRef target, boolean detached, String warningReason) {
    edt.run(
        () -> {
          serverTree.setChannelDisconnected(target, detached, warningReason);
          chat.refreshDisplayedTargetInputEnabled();
          if (chatDockManager != null) {
            chatDockManager.refreshPinnedInputEnabled(target);
          }
        });
  }

  @Override
  public boolean isChannelDisconnected(TargetRef target) {
    return edt.call(() -> serverTree.isChannelDisconnected(target), false);
  }

  @Override
  public boolean isChannelMuted(TargetRef target) {
    return edt.call(() -> serverTree.isChannelMuted(target), false);
  }

  @Override
  public void markUnread(TargetRef target) {
    edt.run(() -> serverTree.markUnread(target));
  }

  @Override
  public void markHighlight(TargetRef target) {
    edt.run(() -> serverTree.markHighlight(target));
  }

  @Override
  public void recordHighlight(TargetRef target, String fromNick) {
    recordHighlight(target, fromNick, "");
  }

  @Override
  public void recordHighlight(TargetRef target, String fromNick, String snippet) {
    notificationStore.recordHighlight(target, fromNick, snippet);
  }

  @Override
  public void recordRuleMatch(TargetRef target, String fromNick, String ruleLabel, String snippet) {
    notificationStore.recordRuleMatch(target, fromNick, ruleLabel, snippet);
  }

  @Override
  public void clearUnread(TargetRef target) {
    edt.run(() -> serverTree.clearUnread(target));
  }

  @Override
  public void setChatActiveTarget(TargetRef target) {
    edt.run(() -> chat.setActiveTarget(target));
  }

  @Override
  public void setChatCurrentNick(String serverId, String nick) {
    edt.run(() -> mentions.setCurrentNick(serverId, nick));
  }

  @Override
  public void setChannelTopic(TargetRef target, String topic) {
    edt.run(() -> chat.setTopic(target, topic));
  }

  @Override
  public void setUsersChannel(TargetRef target) {
    edt.run(() -> users.setChannel(target));
  }

  @Override
  public void setUsersNicks(List<NickInfo> nicks) {
    edt.run(
        () -> {
          users.setNicks(nicks);

          List<String> names;
          int hash = 1;
          int size = 0;
          boolean refreshMatrixTranscriptNames = false;
          if (nicks == null || nicks.isEmpty()) {
            names = List.of();
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

              if (!refreshMatrixTranscriptNames && looksLikeMatrixUserId(nick)) {
                String realName = Objects.toString(ni.realName(), "").trim();
                if (!realName.isEmpty() && !realName.equalsIgnoreCase(nick)) {
                  refreshMatrixTranscriptNames = true;
                }
              }
            }
            names = List.copyOf(tmp);
          }

          TargetRef usersTarget = users.activeTarget();
          boolean emptyUserList = nicks == null || nicks.isEmpty();
          if ((refreshMatrixTranscriptNames || emptyUserList)
              && usersTarget != null
              && usersTarget.isChannel()) {
            transcripts.refreshMatrixDisplayNames(usersTarget);
          }

          boolean sameNickSet =
              (size == lastNickCompletionSize) && (hash == lastNickCompletionHash);
          if (!sameNickSet) {
            lastNickCompletionSize = size;
            lastNickCompletionHash = hash;

            if (activeInputRouter != null && activeInputRouter.active() != null) {
              activeInputRouter.setNickCompletionsForActive(names);
            } else {
              chat.setNickCompletions(names);
            }
          }
        });
  }

  @Override
  public void syncQuasselNetworks(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> safeNetworks =
        networks == null ? List.of() : List.copyOf(networks);
    cacheQuasselNetworkSummaries(sid, safeNetworks);
    edt.run(
        () ->
            serverTree.syncQuasselNetworks(
                sid, SwingQuasselNetworkSupport.toNetworkPresentations(safeNetworks)));
  }

  @Override
  public void setStatusBarChannel(String channel) {
    edt.run(() -> statusBar.setChannel(channel));
  }

  @Override
  public void setStatusBarCounts(int users, int ops) {
    edt.run(() -> statusBar.setCounts(users, ops));
  }

  @Override
  public void setStatusBarServer(String serverText) {
    edt.run(() -> statusBar.setServer(serverText));
  }

  @Override
  public void enqueueStatusNotice(String text, TargetRef clickTarget) {
    edt.run(
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
    edt.run(() -> serverTree.setConnectionControlsEnabled(connectEnabled, disconnectEnabled));
  }

  @Override
  public void setConnectionStatusText(String text) {
    edt.run(() -> serverTree.setStatusText(text));
  }

  @Override
  public void setServerConnectionState(String serverId, ConnectionState state) {
    edt.run(
        () -> {
          serverTree.setServerConnectionState(serverId, state);
          chat.refreshDisplayedTargetInputEnabled();
          if (chatDockManager != null) {
            chatDockManager.refreshPinnedInputEnabledForServer(serverId);
          }
        });
  }

  @Override
  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    edt.run(() -> serverTree.setServerDesiredOnline(serverId, desiredOnline));
  }

  @Override
  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    edt.run(() -> serverTree.setServerConnectionDiagnostics(serverId, lastError, nextRetryEpochMs));
  }

  @Override
  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    edt.run(
        () -> {
          serverTree.setPrivateMessageOnlineState(serverId, nick, online);
          chat.setPrivateMessageOnlineState(serverId, nick, online);
        });
  }

  @Override
  public void clearPrivateMessageOnlineStates(String serverId) {
    edt.run(
        () -> {
          serverTree.clearPrivateMessageOnlineStates(serverId);
          chat.clearPrivateMessageOnlineStates(serverId);
        });
  }

  @Override
  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    edt.run(
        () ->
            serverTree.setServerConnectedIdentity(
                serverId, connectedHost, connectedPort, nick, at));
  }

  @Override
  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    edt.run(() -> serverTree.setServerIrcv3Capability(serverId, capability, subcommand, enabled));
  }

  @Override
  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    edt.run(() -> serverTree.setServerIsupportToken(serverId, tokenName, tokenValue));
  }

  @Override
  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    edt.run(
        () ->
            serverTree.setServerVersionDetails(
                serverId, serverName, serverVersion, userModes, channelModes));
  }

  @Override
  public void setInputEnabled(boolean enabled) {
    edt.run(
        () -> {
          chat.setInputEnabled(enabled);
          chat.refreshDisplayedTargetInputEnabled();
        });
  }

  private static boolean looksLikeMatrixUserId(String token) {
    String value = Objects.toString(token, "").trim();
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private String quasselNetworkTooltip(String serverId, String networkToken) {
    String sid = Objects.toString(serverId, "").trim();
    String token = SwingQuasselNetworkSupport.normalizeNetworkToken(networkToken);
    if (sid.isEmpty() || token.isEmpty()) return "";
    synchronized (quasselNetworkTooltipLock) {
      Map<String, String> byToken = quasselNetworkTooltipByServer.get(sid);
      if (byToken == null || byToken.isEmpty()) return "";
      return Objects.toString(byToken.getOrDefault(token, ""), "");
    }
  }

  private void cacheQuasselNetworkSummaries(
      String serverId, List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    Map<String, String> byToken = new HashMap<>();
    if (networks != null) {
      for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
        if (summary == null) continue;
        String tooltip = SwingQuasselNetworkSupport.renderChoiceLabel(summary);
        if (tooltip.isBlank()) continue;
        for (String candidate : SwingQuasselNetworkSupport.networkTokenCandidates(summary)) {
          if (!candidate.isBlank()) {
            byToken.put(candidate, tooltip);
          }
        }
      }
    }
    synchronized (quasselNetworkTooltipLock) {
      if (byToken.isEmpty()) {
        quasselNetworkTooltipByServer.remove(sid);
      } else {
        quasselNetworkTooltipByServer.put(sid, Map.copyOf(byToken));
      }
    }
  }
}
