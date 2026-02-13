package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.model.UserListStore;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.app.UserActionRequest;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import cafe.woden.ircclient.ui.notifications.NotificationsPanel;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import javax.swing.*;
import javax.swing.text.DefaultStyledDocument;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The main chat dockable.
 *
 * <p>Displays a single transcript at a time (selected via the server tree),
 * but keeps per-target scroll state so switching targets feels natural.
 *
 * <p>Transcripts themselves live in {@link ChatTranscriptStore} so other views
 * (e.g., pinned chat docks) can share them.
 */
@Component
@Lazy
public class ChatDockable extends ChatViewPanel implements Dockable {

  public static final String ID = "chat";

  private final ChatTranscriptStore transcripts;
  private final ServerTreeDockable serverTree;
  private final NotificationStore notificationStore;
  private final TargetActivationBus activationBus;
  private final OutboundLineBus outboundBus;

  private final ChatHistoryService chatHistoryService;

  private final ActiveInputRouter activeInputRouter;

  private final MessageInputPanel inputPanel;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final IgnoreListService ignoreListService;
  private final IgnoreStatusService ignoreStatusService;
  private final UserListStore userListStore;

  private final FlowableProcessor<UserActionRequest> userActions =
      PublishProcessor.<UserActionRequest>create().toSerialized();

  private final NickContextMenuFactory.NickContextMenu nickContextMenu;

  private final ServerProxyResolver proxyResolver;

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();

  private final Map<TargetRef, ViewState> stateByTarget = new HashMap<>();
  private final ViewState fallbackState = new ViewState();

  private final Map<TargetRef, String> topicByTarget = new HashMap<>();
  private final Map<TargetRef, String> draftByTarget = new HashMap<>();

  private final TopicPanel topicPanel = new TopicPanel();
  private final JSplitPane topicSplit;

  // Center content swaps between the normal transcript view and the per-server notifications view.
  private static final String CARD_TRANSCRIPT = "transcript";
  private static final String CARD_NOTIFICATIONS = "notifications";
  private final JPanel centerCards = new JPanel(new CardLayout());
  private final NotificationsPanel notificationsPanel;

  private static final int TOPIC_DIVIDER_SIZE = 6;
  private int lastTopicHeightPx = 58;
  private boolean topicVisible = false;

  private TargetRef activeTarget;

  public ChatDockable(ChatTranscriptStore transcripts,
                     ServerTreeDockable serverTree,
                     NotificationStore notificationStore,
                     TargetActivationBus activationBus,
                     OutboundLineBus outboundBus,
                     ActiveInputRouter activeInputRouter,
                     IgnoreListService ignoreListService,
                     IgnoreStatusService ignoreStatusService,
                     UserListStore userListStore,
                     NickContextMenuFactory nickContextMenuFactory,
                     ServerProxyResolver proxyResolver,
                     ChatHistoryService chatHistoryService,
                     UiSettingsBus settingsBus,
                     CommandHistoryStore commandHistoryStore) {
    super(settingsBus);
    this.transcripts = transcripts;
    this.serverTree = serverTree;
    this.notificationStore = notificationStore;
    this.activationBus = activationBus;
    this.outboundBus = outboundBus;
    this.activeInputRouter = activeInputRouter;
    this.ignoreListService = ignoreListService;
    this.ignoreStatusService = ignoreStatusService;
    this.userListStore = userListStore;
    this.proxyResolver = proxyResolver;
    this.chatHistoryService = chatHistoryService;

    this.nickContextMenu = nickContextMenuFactory.create(new NickContextMenuFactory.Callbacks() {
      @Override
      public void openQuery(TargetRef ctx, String nick) {
        if (ctx == null) return;
        if (nick == null || nick.isBlank()) return;
        openPrivate.onNext(new PrivateMessageRequest(ctx.serverId(), nick));
      }

      @Override
      public void emitUserAction(TargetRef ctx, String nick, UserActionRequest.Action action) {
        if (ctx == null) return;
        if (nick == null || nick.isBlank()) return;
        if (action == null) return;
        userActions.onNext(new UserActionRequest(ctx, nick, action));
      }

      @Override
      public void promptIgnore(TargetRef ctx, String nick, boolean removing, boolean soft) {
        // Qualify to avoid resolving to the callback method itself.
        ChatDockable.this.promptIgnore(ctx, nick, findNickInfo(ctx, nick), removing, soft);
      }
    });

    // Show something harmless on startup; first selection will swap it.
    setDocument(new DefaultStyledDocument());

    // Insert an optional topic panel above the transcript.
    // We use a vertical split so the user can shrink/expand the topic area.
    remove(scroll);
    topicSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topicPanel, scroll);
    topicSplit.setResizeWeight(0.0);
    topicSplit.setBorder(null);
    topicSplit.setOneTouchExpandable(true);
    topicPanel.setMinimumSize(new Dimension(0, 0));
    topicPanel.setPreferredSize(new Dimension(10, lastTopicHeightPx));

    // Track the user's preferred topic height when the panel is visible.
    topicSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
      if (!topicVisible) return;
      Object v = evt.getNewValue();
      if (v instanceof Integer i) {
        // Divider location == height of the top component for VERTICAL_SPLIT.
        lastTopicHeightPx = Math.max(0, i);
      }
    });

    // Notifications panel is a UI-only target view (selected from the server tree).
    this.notificationsPanel = new NotificationsPanel(
        java.util.Objects.requireNonNull(notificationStore, "notificationStore"));
    this.notificationsPanel.setOnSelectTarget(ref -> {
      // Clickable channel names in the notifications list should navigate like a tree selection.
      if (ChatDockable.this.serverTree != null) {
        ChatDockable.this.serverTree.selectTarget(ref);
      }
    });

    centerCards.add(topicSplit, CARD_TRANSCRIPT);
    centerCards.add(notificationsPanel, CARD_NOTIFICATIONS);
    add(centerCards, BorderLayout.CENTER);
    showTranscriptCard();
    hideTopicPanel();

    // Input panel is embedded in the main chat dock so input is always coupled with the transcript.
    this.inputPanel = new MessageInputPanel(settingsBus, commandHistoryStore);
    add(inputPanel, BorderLayout.SOUTH);
    // Context menu: Clear (buffer only) + Reload recent history (clear + reload from DB/bouncer).
    setTranscriptContextMenuActions(
        () -> {
          try {
            TargetRef t = activeTarget;
            if (t == null || t.isUiOnly()) return;
            transcripts.clearTarget(t);
          } catch (Exception ignored) {
          }
        },
        () -> {
          try {
            TargetRef t = activeTarget;
            if (t == null || t.isUiOnly()) return;
            if (chatHistoryService != null && chatHistoryService.canReloadRecent(t)) {
              chatHistoryService.reloadRecent(t);
            }
          } catch (Exception ignored) {
          }
        }
    );
    if (this.activeInputRouter != null) {
      // Default active typing surface is the main chat input.
      this.activeInputRouter.activate(inputPanel);
      inputPanel.setOnActivated(() -> {
        this.activeInputRouter.activate(inputPanel);
        if (activeTarget != null) {
          activationBus.activate(activeTarget);
        }
      });
    }
    disposables.add(inputPanel.outboundMessages().subscribe(line -> {
      armTailPinOnNextAppendIfAtBottom();
      outboundBus.emit(line);
    }, err -> {
      // Never crash the UI because an outbound subscriber failed.
    }));

    // Keep an initial view state so the first auto-scroll behaves.
    this.activeTarget = new TargetRef("default", "status");
    stateByTarget.put(activeTarget, new ViewState());
  }

  @Override
  protected ProxyPlan currentProxyPlan() {
    try {
      if (proxyResolver == null) return null;
      if (activeTarget == null) return null;
      return proxyResolver.planForServer(activeTarget.serverId());
    } catch (Exception ignored) {
      return null;
    }
  }

  public void setActiveTarget(TargetRef target) {
    if (target == null) return;
    if (Objects.equals(activeTarget, target)) return;

    // Persist state + draft of the current target before swapping.
    if (activeTarget != null) {
      draftByTarget.put(activeTarget, inputPanel.getDraftText());
      updateScrollStateFromBar();
    }

    activeTarget = target;

    // UI-only targets (e.g. Notifications) do not have a transcript.
    if (target.isNotifications()) {
      showNotificationsCard(target.serverId());
      // Notifications doesn't accept input; clear any draft to avoid confusion.
      inputPanel.setDraftText("");
      updateTopicPanelForActiveTarget();
      return;
    }

    showTranscriptCard();
    transcripts.ensureTargetExists(target);
    setDocument(transcripts.document(target));

    // Restore any saved draft for this target.
    inputPanel.setDraftText(draftByTarget.getOrDefault(target, ""));

    updateTopicPanelForActiveTarget();
  }

  private void showTranscriptCard() {
    try {
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_TRANSCRIPT);
    } catch (Exception ignored) {
    }
  }

  private void showNotificationsCard(String serverId) {
    try {
      notificationsPanel.setServerId(serverId);
      CardLayout cl = (CardLayout) centerCards.getLayout();
      cl.show(centerCards, CARD_NOTIFICATIONS);
    } catch (Exception ignored) {
    }
  }

  /**
   * Enable/disable the embedded input bar.
   *
   * <p>We intentionally preserve any draft text when disabling so the user
   * doesn't lose what they were typing during connect/disconnect transitions.
   */
  public void setInputEnabled(boolean enabled) {
    inputPanel.setInputEnabled(enabled);
  }

  /**
   * Update the nick completion list used by the embedded input bar.
   */
  public void setNickCompletions(java.util.List<String> nicks) {
    inputPanel.setNickCompletions(nicks);
  }

  public void setTopic(TargetRef target, String topic) {
    if (target == null) return;
    if (!target.isChannel()) return;

    String sanitized = sanitizeTopic(topic);
    if (sanitized.isBlank()) {
      topicByTarget.remove(target);
    } else {
      topicByTarget.put(target, sanitized);
    }

    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget();
    }
  }

  public void clearTopic(TargetRef target) {
    if (target == null) return;
    topicByTarget.remove(target);
    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget();
    }
  }

  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return openPrivate.onBackpressureBuffer();
  }

  public Flowable<UserActionRequest> userActionRequests() {
    return userActions.onBackpressureBuffer();
  }



  private NickInfo findNickInfo(TargetRef ctx, String nick) {
    if (ctx == null) return null;
    if (nick == null || nick.isBlank()) return null;
    if (!ctx.isChannel()) return null;
    if (userListStore == null) return null;

    try {
      for (NickInfo ni : userListStore.get(ctx.serverId(), ctx.target())) {
        if (ni == null) continue;
        if (ni.nick() == null) continue;
        if (ni.nick().equalsIgnoreCase(nick)) return ni;
      }
    } catch (Exception ignored) {
      // Defensive: userListStore should never throw, but context menus should never crash the UI.
    }

    return null;
  }

  private void promptIgnore(TargetRef ctx, String nick, NickInfo ni, boolean removing, boolean soft) {
    if (ignoreListService == null) return;
    if (ctx == null) return;
    String sid = Objects.toString(ctx.serverId(), "").trim();
    if (sid.isEmpty()) return;

    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return;

    String hm = Objects.toString(ni == null ? "" : ni.hostmask(), "").trim();
    String seedBase = (ignoreStatusService == null)
        ? n
        : ignoreStatusService.bestSeedForMask(sid, n, hm);
    String seed = IgnoreListService.normalizeMaskOrNickToHostmask(seedBase);

    String title;
    String message;
    if (soft) {
      title = removing ? "Remove soft ignore" : "Soft ignore";
      message = removing
          ? "Remove soft ignore for <b>" + escapeHtml(n) + "</b>?" + "<br><br><b>Mask</b>:" + "<br>"
              + escapeHtml(seed)
          : "Soft ignore <b>" + escapeHtml(n) + "</b>?" + "<br><br><b>Mask</b>:" + "<br>"
              + escapeHtml(seed);
    } else {
      title = removing ? "Remove ignore" : "Ignore";
      message = removing
          ? "Remove ignore for <b>" + escapeHtml(n) + "</b>?" + "<br><br><b>Mask</b>:" + "<br>" + escapeHtml(seed)
          : "Ignore <b>" + escapeHtml(n) + "</b>?" + "<br><br><b>Mask</b>:" + "<br>" + escapeHtml(seed);
    }

    int res = JOptionPane.showConfirmDialog(
        SwingUtilities.getWindowAncestor(this),
        "<html>" + message + "</html>",
        title,
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE
    );

    if (res != JOptionPane.OK_OPTION) return;

    boolean changed;
    if (soft) {
      changed = removing ? ignoreListService.removeSoftMask(sid, seed) : ignoreListService.addSoftMask(sid, seed);
    } else {
      changed = removing ? ignoreListService.removeMask(sid, seed) : ignoreListService.addMask(sid, seed);
    }

    if (!changed) {
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "Nothing changed — the ignore list already contained that mask.",
          "No change",
          JOptionPane.INFORMATION_MESSAGE
      );
    }
  }

  private static String escapeHtml(String s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        case '\'': sb.append("&#39;"); break;
        default: sb.append(c);
      }
    }
    return sb.toString();
  }
  @Override
  protected JPopupMenu nickContextMenuFor(String nick) {
    if (nick == null || nick.isBlank()) return null;
    if (activeTarget == null) return null;

    String sid = Objects.toString(activeTarget.serverId(), "").trim();
    if (sid.isEmpty()) return null;

    String n = nick.trim();
    if (n.isEmpty()) return null;

    NickInfo ni = findNickInfo(activeTarget, n);
    String hm = Objects.toString(ni == null ? "" : ni.hostmask(), "").trim();

    IgnoreStatusService.Status st = (ignoreStatusService == null)
    ? new IgnoreStatusService.Status(false, false, false, "")
    : ignoreStatusService.status(sid, n, hm);

    return nickContextMenu.forNick(activeTarget, n, new NickContextMenuFactory.IgnoreMark(st.hard(), st.soft()));
  }

  @Override
  protected void onTranscriptClicked() {
    // Clicking the main transcript should make it the active target for input/status.
    // This does NOT change what the main chat is already displaying.
    if (activeTarget == null) return;
    activationBus.activate(activeTarget);
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    inputPanel.focusInput();
  }

  @Override
  protected boolean onNickClicked(String nick) {
    if (activeTarget == null || !activeTarget.isChannel()) return false;
    if (nick == null || nick.isBlank()) return false;

    openPrivate.onNext(new PrivateMessageRequest(activeTarget.serverId(), nick));
    return true;
  }

  @Override
  protected boolean onChannelClicked(String channel) {
    if (activeTarget == null) return false;
    if (channel == null || channel.isBlank()) return false;

    // Ensure the app's "active target" (server context) matches the transcript we clicked in.
    activationBus.activate(activeTarget);

    // Delegate join to the normal command pipeline so config/auto-join behavior stays consistent.
    outboundBus.emit("/join " + channel.trim());
    return true;
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return "Chat";
  }

  @Override
  protected boolean isFollowTail() {
    return state().followTail;
  }

  @Override
  protected void setFollowTail(boolean followTail) {
    state().followTail = followTail;
  }

  @Override
  protected int getSavedScrollValue() {
    return state().scrollValue;
  }

  @Override
  protected void setSavedScrollValue(int value) {
    state().scrollValue = value;
  }
  private ViewState state() {
    if (activeTarget == null) return fallbackState;
    return stateByTarget.computeIfAbsent(activeTarget, t -> new ViewState());
  }

  private void updateTopicPanelForActiveTarget() {
    if (activeTarget == null || !activeTarget.isChannel()) {
      topicPanel.setTopic("", "");
      hideTopicPanel();
      return;
    }

    String topic = topicByTarget.getOrDefault(activeTarget, "");
    if (topic == null) topic = "";
    topic = topic.trim();

    if (topic.isEmpty()) {
      topicPanel.setTopic(activeTarget.target(), "");
      hideTopicPanel();
      return;
    }

    topicPanel.setTopic(activeTarget.target(), topic);
    showTopicPanel();
  }

  private void showTopicPanel() {
    topicVisible = true;
    topicPanel.setVisible(true);
    topicSplit.setDividerSize(TOPIC_DIVIDER_SIZE);
    int targetHeight = Math.max(28, Math.min(lastTopicHeightPx, 200));
    topicSplit.setDividerLocation(targetHeight);
    revalidate();
    repaint();
  }

  private void hideTopicPanel() {
    topicVisible = false;
    topicPanel.setVisible(false);
    topicSplit.setDividerSize(0);
    topicSplit.setDividerLocation(0);
    revalidate();
    repaint();
  }

  private static String sanitizeTopic(String topic) {
    if (topic == null) return "";
    // Strip IRC formatting control chars and other low ASCII controls.
    // (Color codes, bold, reset, etc.)
    return topic.replaceAll("[\\x00-\\x1F\\x7F]", "");
  }

  private static class ViewState {
    boolean followTail = true;
    int scrollValue = 0;
  }

  private static final class TopicPanel extends JPanel {
    private final JLabel header = new JLabel();
    private final JTextArea text = new JTextArea();

    private TopicPanel() {
      super(new BorderLayout(8, 6));

      header.setFont(header.getFont().deriveFont(Font.BOLD));

      text.setEditable(false);
      text.setLineWrap(true);
      text.setWrapStyleWord(true);
      text.setOpaque(false);
      text.setBorder(null);

      JPanel top = new JPanel(new BorderLayout());
      top.setOpaque(false);
      top.add(header, BorderLayout.WEST);

      setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
      setOpaque(true);

      add(top, BorderLayout.NORTH);
      add(text, BorderLayout.CENTER);
    }

    void setTopic(String channelName, String topic) {
      String ch = (channelName == null) ? "" : channelName.trim();
      header.setText(ch.isEmpty() ? "Topic" : "Topic — " + ch);
      text.setText(topic == null ? "" : topic);
      text.setCaretPosition(0);
    }
  }

  @PreDestroy
  void shutdown() {
    try {
      disposables.dispose();
    } catch (Exception ignored) {
    }
    try {
      notificationsPanel.close();
    } catch (Exception ignored) {
    }
    // Ensure decorator listeners/subscriptions are removed when Spring disposes this dock.
    closeDecorators();
  }
}
