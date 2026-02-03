package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.model.UserListStore;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.app.UserActionRequest;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
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
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.Locale;
import java.util.List;

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
  private final TargetActivationBus activationBus;
  private final OutboundLineBus outboundBus;

  private final IgnoreListService ignoreListService;
  private final UserListStore userListStore;

  private final FlowableProcessor<UserActionRequest> userActions =
      PublishProcessor.<UserActionRequest>create().toSerialized();

  private final JPopupMenu nickMenu = new JPopupMenu();
  private final JMenuItem nickOpenQueryItem = new JMenuItem("Open Query");
  private final JMenuItem nickWhoisItem = new JMenuItem("Whois");
  private final JMenuItem nickVersionItem = new JMenuItem("Version");
  private final JMenuItem nickPingItem = new JMenuItem("Ping");
  private final JMenuItem nickTimeItem = new JMenuItem("Time");
  private final JMenuItem nickIgnoreItem = new JMenuItem("Ignore...");
  private final JMenuItem nickUnignoreItem = new JMenuItem("Unignore...");
  private final JMenuItem nickSoftIgnoreItem = new JMenuItem("Soft Ignore...");
  private final JMenuItem nickSoftUnignoreItem = new JMenuItem("Soft Unignore...");

  private final ConcurrentHashMap<String, Pattern> nickGlobCache = new ConcurrentHashMap<>();

  private volatile TargetRef popupCtx;
  private volatile String popupNick;

  private final FlowableProcessor<PrivateMessageRequest> openPrivate =
      PublishProcessor.<PrivateMessageRequest>create().toSerialized();

  private final Map<TargetRef, ViewState> stateByTarget = new HashMap<>();
  private final ViewState fallbackState = new ViewState();

  private final Map<TargetRef, String> topicByTarget = new HashMap<>();

  private final TopicPanel topicPanel = new TopicPanel();
  private final JSplitPane topicSplit;

  private static final int TOPIC_DIVIDER_SIZE = 6;
  private int lastTopicHeightPx = 58;
  private boolean topicVisible = false;

  private TargetRef activeTarget;

  public ChatDockable(ChatTranscriptStore transcripts,
                     TargetActivationBus activationBus,
                     OutboundLineBus outboundBus,
                     IgnoreListService ignoreListService,
                     UserListStore userListStore,
                     UiSettingsBus settingsBus) {
    super(settingsBus);
    this.transcripts = transcripts;
    this.activationBus = activationBus;
    this.outboundBus = outboundBus;
    this.ignoreListService = ignoreListService;
    this.userListStore = userListStore;

    initNickContextMenu();

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

    add(topicSplit, BorderLayout.CENTER);
    hideTopicPanel();

    // Keep an initial view state so the first auto-scroll behaves.
    this.activeTarget = new TargetRef("default", "status");
    stateByTarget.put(activeTarget, new ViewState());
  }

  public void setActiveTarget(TargetRef target) {
    if (target == null) return;

    // Persist state of the current target before swapping.
    if (activeTarget != null) {
      updateScrollStateFromBar();
    }

    activeTarget = target;
    transcripts.ensureTargetExists(target);
    setDocument(transcripts.document(target));

    updateTopicPanelForActiveTarget();
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




  private void initNickContextMenu() {
    nickMenu.add(nickOpenQueryItem);
    nickMenu.addSeparator();
    nickMenu.add(nickWhoisItem);
    nickMenu.add(nickVersionItem);
    nickMenu.add(nickPingItem);
    nickMenu.add(nickTimeItem);
    nickMenu.addSeparator();
    nickMenu.add(nickIgnoreItem);
    nickMenu.add(nickUnignoreItem);
    nickMenu.addSeparator();
    nickMenu.add(nickSoftIgnoreItem);
    nickMenu.add(nickSoftUnignoreItem);

    nickOpenQueryItem.addActionListener(e -> {
      TargetRef ctx = popupCtx;
      String nick = popupNick;
      if (ctx == null) return;
      if (nick == null || nick.isBlank()) return;
      openPrivate.onNext(new PrivateMessageRequest(ctx.serverId(), nick));
    });

    nickWhoisItem.addActionListener(e -> emitUserAction(UserActionRequest.Action.WHOIS));
    nickVersionItem.addActionListener(e -> emitUserAction(UserActionRequest.Action.CTCP_VERSION));
    nickPingItem.addActionListener(e -> emitUserAction(UserActionRequest.Action.CTCP_PING));
    nickTimeItem.addActionListener(e -> emitUserAction(UserActionRequest.Action.CTCP_TIME));

    nickIgnoreItem.addActionListener(e -> promptIgnore(popupCtx, popupNick, findNickInfo(popupCtx, popupNick), false, false));
    nickUnignoreItem.addActionListener(e -> promptIgnore(popupCtx, popupNick, findNickInfo(popupCtx, popupNick), true, false));
    nickSoftIgnoreItem.addActionListener(e -> promptIgnore(popupCtx, popupNick, findNickInfo(popupCtx, popupNick), false, true));
    nickSoftUnignoreItem.addActionListener(e -> promptIgnore(popupCtx, popupNick, findNickInfo(popupCtx, popupNick), true, true));
  }

  private void emitUserAction(UserActionRequest.Action action) {
    TargetRef ctx = popupCtx;
    String nick = popupNick;
    if (ctx == null) return;
    if (nick == null || nick.isBlank()) return;
    userActions.onNext(new UserActionRequest(ctx, nick, action));
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

  private record IgnoreMark(boolean hard, boolean soft) {}

  private IgnoreMark ignoreMarkForNick(String serverId, String nick, NickInfo ni) {
    if (ignoreListService == null) return new IgnoreMark(false, false);
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return new IgnoreMark(false, false);

    String n = Objects.toString(nick, "").trim();
    String hostmask = Objects.toString(ni == null ? "" : ni.hostmask(), "").trim();
    if (n.isEmpty() && hostmask.isEmpty()) return new IgnoreMark(false, false);

    // Prefer full hostmask matching when we have it.
    boolean hard;
    boolean soft;
    var hardMasks = ignoreListService.listMasks(sid);
    var softMasks = ignoreListService.listSoftMasks(sid);

    if (isUsefulHostmask(hostmask)) {
      hard = hostmaskTargetedByAny(hardMasks, hostmask);
      soft = hostmaskTargetedByAny(softMasks, hostmask);
    } else {
      hard = nickTargetedByAny(hardMasks, n);
      soft = nickTargetedByAny(softMasks, n);
    }

    return new IgnoreMark(hard, soft);
  }

  private boolean hostmaskTargetedByAny(List<String> masks, String hostmask) {
    if (masks == null || masks.isEmpty()) return false;
    String hm = Objects.toString(hostmask, "").trim();
    if (hm.isEmpty()) return false;

    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      if (globMatchIgnoreMask(m, hm)) return true;
    }
    return false;
  }

  private boolean nickTargetedByAny(List<String> masks, String nick) {
    if (masks == null || masks.isEmpty()) return false;
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return false;

    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      int bang = m.indexOf('!');
      if (bang <= 0) continue;
      String nickGlob = m.substring(0, bang).trim();
      if (nickGlob.isEmpty()) continue;

      // Avoid marking everyone for host-only patterns like "*!ident@host".
      if (nickGlob.chars().allMatch(ch -> ch == '*' || ch == '?')) continue;

      if (globMatchesNick(nickGlob, n)) return true;
    }
    return false;
  }

  private boolean globMatchesNick(String glob, String nick) {
    String key = Objects.toString(glob, "").toLowerCase(Locale.ROOT);
    Pattern p = nickGlobCache.computeIfAbsent(key, k -> Pattern.compile(globToRegex(glob), Pattern.CASE_INSENSITIVE));
    return p.matcher(nick).matches();
  }

  private String globToRegex(String glob) {
    StringBuilder sb = new StringBuilder();
    sb.append('^');
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      switch (c) {
        case '*': sb.append(".*"); break;
        case '?': sb.append('.'); break;
        case '\\': sb.append("\\\\"); break;
        default:
          if (".+()^$|{}[]\\\\".indexOf(c) >= 0) sb.append('\\');
          sb.append(c);
      }
    }
    sb.append('$');
    return sb.toString();
  }

  /**
   * Glob match for ignore masks: "*" = any sequence, "?" = any char, case-insensitive.
   * Mirrors IgnoreListService matching so indicators agree with message filtering.
   */
  private static boolean globMatchIgnoreMask(String pattern, String text) {
    String ptn = Objects.toString(pattern, "").trim().toLowerCase(Locale.ROOT);
    String txt = Objects.toString(text, "").trim().toLowerCase(Locale.ROOT);
    if (ptn.isEmpty() || txt.isEmpty()) return false;

    int p = 0;
    int t = 0;
    int star = -1;
    int match = 0;

    while (t < txt.length()) {
      if (p < ptn.length() && (ptn.charAt(p) == '?' || ptn.charAt(p) == txt.charAt(t))) {
        p++;
        t++;
        continue;
      }

      if (p < ptn.length() && ptn.charAt(p) == '*') {
        star = p;
        match = t;
        p++;
        continue;
      }

      if (star != -1) {
        p = star + 1;
        match++;
        t = match;
        continue;
      }

      return false;
    }

    while (p < ptn.length() && ptn.charAt(p) == '*') p++;
    return p == ptn.length();
  }

  /**
   * Best-effort check that a hostmask is "useful" (not empty and not just a derived wildcard placeholder).
   */
  private static boolean isUsefulHostmask(String hostmask) {
    if (hostmask == null) return false;
    String hm = hostmask.trim();
    if (hm.isEmpty()) return false;

    int at = hm.indexOf('@');
    if (at <= 0 || at >= hm.length() - 1) return false;

    int bang = hm.indexOf('!');
    String user;
    if (bang >= 0) {
      if (bang == 0 || bang >= at - 1) return false;
      user = hm.substring(bang + 1, at).trim();
    } else {
      user = hm.substring(0, at).trim();
    }

    String host = hm.substring(at + 1).trim();
    boolean userUnknown = user.isEmpty() || "*".equals(user);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(userUnknown && hostUnknown);
  }

  private void promptIgnore(TargetRef ctx, String nick, NickInfo ni, boolean removing, boolean soft) {
    if (ignoreListService == null) return;
    if (ctx == null) return;
    String sid = Objects.toString(ctx.serverId(), "").trim();
    if (sid.isEmpty()) return;

    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return;

    String hm = Objects.toString(ni == null ? "" : ni.hostmask(), "").trim();
    String seed = IgnoreListService.normalizeMaskOrNickToHostmask(isUsefulHostmask(hm) ? hm : n);

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

    this.popupCtx = activeTarget;
    this.popupNick = nick.trim();

    NickInfo ni = findNickInfo(activeTarget, popupNick);
    IgnoreMark mark = ignoreMarkForNick(sid, popupNick, ni);

    boolean canAct = !popupNick.isBlank();
    nickOpenQueryItem.setEnabled(canAct);
    nickWhoisItem.setEnabled(canAct);
    nickVersionItem.setEnabled(canAct);
    nickPingItem.setEnabled(canAct);
    nickTimeItem.setEnabled(canAct);

    nickIgnoreItem.setEnabled(canAct);
    nickUnignoreItem.setEnabled(canAct && mark.hard);
    nickSoftIgnoreItem.setEnabled(canAct);
    nickSoftUnignoreItem.setEnabled(canAct && mark.soft);

    return nickMenu;
  }

  @Override
  protected void onTranscriptClicked() {
    // Clicking the main transcript should make it the active target for input/status.
    // This does NOT change what the main chat is already displaying.
    if (activeTarget == null) return;
    activationBus.activate(activeTarget);
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
    // Ensure decorator listeners/subscriptions are removed when Spring disposes this dock.
    closeDecorators();
  }
}
