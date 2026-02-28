package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.notifications.NotificationStore.HighlightEvent;
import cafe.woden.ircclient.notifications.NotificationStore.IrcEventRuleEvent;
import cafe.woden.ircclient.notifications.NotificationStore.RuleMatchEvent;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

/** Owns topic state, topic panel rendering, and topic update events for {@link ChatDockable}. */
public final class ChatTopicCoordinator {

  private static final int TOPIC_DIVIDER_SIZE = 6;
  private static final int NOTIFICATION_PREVIEW_LIMIT = 8;
  private static final DateTimeFormatter NOTIFICATION_TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

  private final Map<TargetRef, String> topicByTarget = new HashMap<>();
  private final FlowableProcessor<ChatDockable.TopicUpdate> topicUpdates =
      PublishProcessor.<ChatDockable.TopicUpdate>create().toSerialized();
  private final TopicPanel topicPanel = new TopicPanel();
  private final JSplitPane topicSplit;
  private final ChannelListPanel channelListPanel;
  private final NotificationStore notificationStore;
  private final Consumer<TargetRef> targetSelector;
  private final Runnable uiRefresh;

  private int lastTopicHeightPx = 58;
  private boolean topicVisible = false;
  private boolean topicCompact = false;
  private TargetRef activeTarget;

  public ChatTopicCoordinator(
      JScrollPane transcriptScroll,
      ChannelListPanel channelListPanel,
      NotificationStore notificationStore,
      Consumer<TargetRef> targetSelector,
      Runnable uiRefresh) {
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
    this.notificationStore = Objects.requireNonNull(notificationStore, "notificationStore");
    this.targetSelector = targetSelector != null ? targetSelector : unused -> {};
    this.uiRefresh = Objects.requireNonNull(uiRefresh, "uiRefresh");
    JScrollPane scroll = Objects.requireNonNull(transcriptScroll, "transcriptScroll");

    topicSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topicPanel, scroll);
    topicSplit.setResizeWeight(0.0);
    topicSplit.setBorder(null);
    topicSplit.setOneTouchExpandable(true);
    topicPanel.setMinimumSize(new Dimension(0, 0));
    topicPanel.setPreferredSize(new Dimension(10, lastTopicHeightPx));

    // Divider location == height of the top component for VERTICAL_SPLIT.
    topicSplit.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        evt -> {
          if (!topicVisible || topicCompact) return;
          Object value = evt.getNewValue();
          if (value instanceof Integer location) {
            lastTopicHeightPx = Math.max(0, location);
          }
        });

    topicPanel.setOnNotificationsClick(this::showNotificationsPopupForActiveChannel);
    hideTopicPanel();
  }

  public JComponent topicSplit() {
    return topicSplit;
  }

  public void setTopic(TargetRef target, String topic, TargetRef activeTarget) {
    if (target == null || !target.isChannel()) return;

    String sanitized = sanitizeTopic(topic);
    String normalized = sanitized.isBlank() ? "" : sanitized;
    String before = topicByTarget.getOrDefault(target, "");
    if (Objects.equals(before, normalized)) {
      if (target.equals(activeTarget)) {
        updateTopicPanelForActiveTarget(activeTarget);
      }
      return;
    }

    if (normalized.isBlank()) {
      topicByTarget.remove(target);
    } else {
      topicByTarget.put(target, normalized);
    }

    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget(activeTarget);
    }
    channelListPanel.refreshOpenChannelDetails(target.serverId(), target.target());
    topicUpdates.onNext(new ChatDockable.TopicUpdate(target, normalized));
  }

  public void clearTopic(TargetRef target, TargetRef activeTarget) {
    if (target == null) return;

    String removed = topicByTarget.remove(target);
    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget(activeTarget);
    }
    if (target.isChannel() && removed != null && !removed.isBlank()) {
      channelListPanel.refreshOpenChannelDetails(target.serverId(), target.target());
      topicUpdates.onNext(new ChatDockable.TopicUpdate(target, ""));
    }
  }

  public String topicFor(TargetRef target) {
    if (target == null) return "";
    return topicByTarget.getOrDefault(target, "");
  }

  public Flowable<ChatDockable.TopicUpdate> topicUpdates() {
    return topicUpdates.onBackpressureLatest();
  }

  public void updateTopicPanelForActiveTarget(TargetRef activeTarget) {
    this.activeTarget = activeTarget;
    if (activeTarget == null || !activeTarget.isChannel()) {
      topicPanel.setTopic("", "");
      topicPanel.setNotificationState(false, 0);
      topicPanel.setNotificationTooltip("No recent channel notifications.");
      hideTopicPanel();
      return;
    }

    NotificationSummary summary = summarizeChannelNotifications(activeTarget);
    topicPanel.setNotificationState(true, summary.totalCount());
    topicPanel.setNotificationTooltip(buildNotificationTooltip(summary));

    String topic = Objects.toString(topicByTarget.getOrDefault(activeTarget, ""), "").trim();
    topicPanel.setTopic(activeTarget.target(), topic);

    boolean hasTopic = !topic.isEmpty();
    if (hasTopic) {
      showTopicPanel(false);
    } else if (summary.totalCount() > 0) {
      showTopicPanel(true);
    } else {
      hideTopicPanel();
    }
  }

  private void showTopicPanel(boolean compact) {
    topicVisible = true;
    topicCompact = compact;
    topicPanel.setVisible(true);
    topicSplit.setDividerSize(TOPIC_DIVIDER_SIZE);
    int minHeight = compact ? 28 : 40;
    int maxHeight = compact ? 72 : 200;
    int targetHeight = Math.max(minHeight, Math.min(lastTopicHeightPx, maxHeight));
    topicSplit.setDividerLocation(targetHeight);
    uiRefresh.run();
  }

  private void hideTopicPanel() {
    topicVisible = false;
    topicCompact = false;
    topicPanel.setVisible(false);
    topicSplit.setDividerSize(0);
    topicSplit.setDividerLocation(0);
    uiRefresh.run();
  }

  private void showNotificationsPopupForActiveChannel() {
    TargetRef target = activeTarget;
    if (target == null || !target.isChannel()) return;

    NotificationSummary summary = summarizeChannelNotifications(target);
    JPopupMenu menu = new JPopupMenu();

    JMenuItem header =
        new JMenuItem(
            "Recent notifications for " + target.target() + " (" + summary.totalCount() + ")");
    header.setEnabled(false);
    menu.add(header);

    List<NotificationEntry> previews = summary.previews();
    if (previews.isEmpty()) {
      JMenuItem none = new JMenuItem("(none)");
      none.setEnabled(false);
      menu.add(none);
    } else {
      for (NotificationEntry entry : previews) {
        JMenuItem line = new JMenuItem(formatPreviewLine(entry));
        line.setEnabled(false);
        menu.add(line);
      }
    }

    menu.addSeparator();

    JMenuItem openNotifications = new JMenuItem("Open Notifications view");
    openNotifications.addActionListener(
        e -> targetSelector.accept(TargetRef.notifications(target.serverId())));
    menu.add(openNotifications);

    JMenuItem clearChannel = new JMenuItem("Clear");
    clearChannel.setEnabled(summary.totalCount() > 0);
    clearChannel.addActionListener(
        e -> {
          notificationStore.clearChannel(target);
          updateTopicPanelForActiveTarget(activeTarget);
        });
    menu.add(clearChannel);

    JButton anchor = topicPanel.notificationsButton();
    menu.show(anchor, 0, anchor.getHeight());
  }

  private NotificationSummary summarizeChannelNotifications(TargetRef channelTarget) {
    if (channelTarget == null || !channelTarget.isChannel()) {
      return NotificationSummary.EMPTY;
    }
    String serverId = Objects.toString(channelTarget.serverId(), "").trim();
    String channel = Objects.toString(channelTarget.target(), "").trim();
    if (serverId.isEmpty() || channel.isEmpty()) {
      return NotificationSummary.EMPTY;
    }

    List<NotificationEntry> entries = new ArrayList<>();

    for (HighlightEvent ev : notificationStore.listAll(serverId)) {
      if (ev == null || !channel.equalsIgnoreCase(Objects.toString(ev.channel(), "").trim()))
        continue;
      String fromNick = Objects.toString(ev.fromNick(), "").trim();
      String title = fromNick.isEmpty() ? "(mention)" : "(mention) " + fromNick;
      entries.add(new NotificationEntry(ev.at(), title, ev.snippet()));
    }

    for (RuleMatchEvent ev : notificationStore.listAllRuleMatches(serverId)) {
      if (ev == null || !channel.equalsIgnoreCase(Objects.toString(ev.channel(), "").trim()))
        continue;
      String label = Objects.toString(ev.ruleLabel(), "").trim();
      String fromNick = Objects.toString(ev.fromNick(), "").trim();
      String title;
      if (!label.isEmpty() && !fromNick.isEmpty()) {
        title = label + " (" + fromNick + ")";
      } else if (!label.isEmpty()) {
        title = label;
      } else if (!fromNick.isEmpty()) {
        title = "(rule) " + fromNick;
      } else {
        title = "(rule)";
      }
      entries.add(new NotificationEntry(ev.at(), title, ev.snippet()));
    }

    for (IrcEventRuleEvent ev : notificationStore.listAllIrcEventRules(serverId)) {
      if (ev == null || !channel.equalsIgnoreCase(Objects.toString(ev.channel(), "").trim()))
        continue;
      String title = Objects.toString(ev.title(), "").trim();
      String fromNick = Objects.toString(ev.fromNick(), "").trim();
      if (!fromNick.isEmpty()) {
        title = title.isEmpty() ? fromNick : (title + " (" + fromNick + ")");
      }
      if (title.isEmpty()) title = "(event)";
      entries.add(new NotificationEntry(ev.at(), title, ev.body()));
    }

    if (entries.isEmpty()) return NotificationSummary.EMPTY;
    entries.sort(ChatTopicCoordinator::compareEntriesNewestFirst);
    int total = entries.size();
    List<NotificationEntry> previews =
        total > NOTIFICATION_PREVIEW_LIMIT
            ? List.copyOf(entries.subList(0, NOTIFICATION_PREVIEW_LIMIT))
            : List.copyOf(entries);
    return new NotificationSummary(total, previews);
  }

  private static int compareEntriesNewestFirst(NotificationEntry a, NotificationEntry b) {
    Instant aa = a != null ? a.at() : null;
    Instant bb = b != null ? b.at() : null;
    if (aa == null && bb == null) return 0;
    if (aa == null) return 1;
    if (bb == null) return -1;
    return bb.compareTo(aa);
  }

  private static String formatPreviewLine(NotificationEntry entry) {
    if (entry == null) return "";
    String time = entry.at() != null ? NOTIFICATION_TIME_FMT.format(entry.at()) : "--:--:--";
    String title = Objects.toString(entry.title(), "").trim();
    String detail = Objects.toString(entry.detail(), "").trim();
    String line = detail.isEmpty() ? (time + "  " + title) : (time + "  " + title + " - " + detail);
    if (line.length() > 160) {
      line = line.substring(0, 159) + "…";
    }
    return line;
  }

  private static String buildNotificationTooltip(NotificationSummary summary) {
    if (summary == null || summary.totalCount() <= 0) {
      return "No recent channel notifications.";
    }
    StringBuilder html = new StringBuilder(512);
    html.append("<html><b>Recent channel notifications (")
        .append(summary.totalCount())
        .append(")</b>");
    for (NotificationEntry entry : summary.previews()) {
      String line = escapeHtml(formatPreviewLine(entry));
      if (line.isBlank()) continue;
      html.append("<br>").append(line);
    }
    if (summary.totalCount() > summary.previews().size()) {
      html.append("<br>…");
    }
    html.append("</html>");
    return html.toString();
  }

  private static String escapeHtml(String raw) {
    String s = Objects.toString(raw, "");
    if (s.isEmpty()) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private record NotificationEntry(Instant at, String title, String detail) {}

  private record NotificationSummary(int totalCount, List<NotificationEntry> previews) {
    private static final NotificationSummary EMPTY = new NotificationSummary(0, List.of());
  }

  private static String sanitizeTopic(String topic) {
    if (topic == null) return "";
    // Strip IRC formatting control chars and other low ASCII controls.
    return topic.replaceAll("[\\x00-\\x1F\\x7F]", "");
  }

  private static final class TopicPanel extends JPanel {
    private final JLabel header = new JLabel();
    private final JTextArea text = new JTextArea();
    private final JButton notificationsButton = new JButton();
    private final Color activeNotificationGlow = new Color(255, 223, 128, 170);

    private Runnable onNotificationsClick;

    private TopicPanel() {
      super(new BorderLayout(8, 6));

      header.setFont(header.getFont().deriveFont(Font.BOLD));
      text.setEditable(false);
      text.setLineWrap(true);
      text.setWrapStyleWord(true);
      text.setOpaque(false);
      text.setBorder(null);

      notificationsButton.setIcon(SvgIcons.quiet("lightbulb", 14));
      notificationsButton.setBorderPainted(false);
      notificationsButton.setContentAreaFilled(false);
      notificationsButton.setOpaque(false);
      notificationsButton.setFocusable(false);
      notificationsButton.setFocusPainted(false);
      notificationsButton.setMargin(new java.awt.Insets(1, 4, 1, 4));
      notificationsButton.setPreferredSize(new Dimension(26, 20));
      notificationsButton.setToolTipText("No recent channel notifications.");
      notificationsButton.addActionListener(
          e -> {
            if (onNotificationsClick != null) {
              onNotificationsClick.run();
            }
          });

      JPanel top = new JPanel(new BorderLayout());
      top.setOpaque(false);
      top.add(header, BorderLayout.WEST);
      top.add(notificationsButton, BorderLayout.EAST);

      setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
      setOpaque(true);
      add(top, BorderLayout.NORTH);
      add(text, BorderLayout.CENTER);
    }

    public void setTopic(String channelName, String topic) {
      String channel = Objects.toString(channelName, "").trim();
      String raw = Objects.toString(topic, "").trim();
      boolean hasTopic = !raw.isEmpty();
      header.setText(
          channel.isEmpty() ? "Topic" : (hasTopic ? "Topic — " + channel : "Channel — " + channel));
      text.setVisible(hasTopic);
      text.setText(hasTopic ? raw : "");
      if (hasTopic) text.setCaretPosition(0);
    }

    public void setOnNotificationsClick(Runnable onNotificationsClick) {
      this.onNotificationsClick = onNotificationsClick;
    }

    public void setNotificationState(boolean visible, int count) {
      notificationsButton.setVisible(visible);
      if (!visible) {
        return;
      }
      if (count > 0) {
        notificationsButton.setIcon(SvgIcons.action("lightbulb", 14));
        notificationsButton.setText("");
        notificationsButton.setOpaque(true);
        notificationsButton.setContentAreaFilled(true);
        notificationsButton.setBorderPainted(true);
        notificationsButton.setBackground(activeNotificationGlow);
      } else {
        notificationsButton.setIcon(SvgIcons.quiet("lightbulb", 14));
        notificationsButton.setText("");
        notificationsButton.setOpaque(false);
        notificationsButton.setContentAreaFilled(false);
        notificationsButton.setBorderPainted(false);
        notificationsButton.setBackground(null);
      }
    }

    public void setNotificationTooltip(String tooltip) {
      notificationsButton.setToolTipText(
          Objects.toString(tooltip, "No recent channel notifications."));
    }

    public JButton notificationsButton() {
      return notificationsButton;
    }
  }
}
