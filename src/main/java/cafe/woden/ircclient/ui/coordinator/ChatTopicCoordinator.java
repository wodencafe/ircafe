package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.api.HighlightEvent;
import cafe.woden.ircclient.notifications.api.IrcEventRuleEvent;
import cafe.woden.ircclient.notifications.api.NotificationStorePort;
import cafe.woden.ircclient.notifications.api.RuleMatchEvent;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.localization.UiMessages;
import com.formdev.flatlaf.extras.FlatSVGIcon;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns topic state, topic panel rendering, and topic update events for {@link ChatDockable}. */
public final class ChatTopicCoordinator {

  private static final Logger log = LoggerFactory.getLogger(ChatTopicCoordinator.class);
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();
  private static final int TOPIC_DIVIDER_SIZE = 6;
  private static final int DEFAULT_TOPIC_HEIGHT_PX = 58;
  private static final int MIN_TOPIC_HEIGHT_PX = 40;
  private static final int MAX_TOPIC_HEIGHT_PX = 200;
  private static final int NOTIFICATION_PREVIEW_LIMIT = 8;
  private static final DateTimeFormatter NOTIFICATION_TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

  private final Map<TargetRef, String> topicByTarget = new HashMap<>();
  private final Map<TargetRef, String> channelModesByTarget = new HashMap<>();
  private final FlowableProcessor<ChatDockable.TopicUpdate> topicUpdates =
      PublishProcessor.<ChatDockable.TopicUpdate>create().toSerialized();
  private final TopicPanel topicPanel = new TopicPanel();
  private final JSplitPane topicSplit;
  private final ChannelListPanel channelListPanel;
  private final NotificationStorePort notificationStore;
  private final Consumer<TargetRef> targetSelector;
  private final Runnable uiRefresh;
  private final Function<TargetRef, String> persistedTopicLookup;
  private final BiConsumer<TargetRef, String> persistedTopicSink;
  private final Function<TargetRef, Integer> persistedTopicHeightLookup;
  private final BiConsumer<TargetRef, Integer> persistedTopicHeightSink;
  private final Map<TargetRef, Integer> topicHeightByTarget = new HashMap<>();

  private int lastTopicHeightPx = DEFAULT_TOPIC_HEIGHT_PX;
  private boolean topicVisible = false;
  private boolean topicCompact = false;
  private TargetRef activeTarget;

  public ChatTopicCoordinator(
      JScrollPane transcriptScroll,
      ChannelListPanel channelListPanel,
      NotificationStorePort notificationStore,
      Consumer<TargetRef> targetSelector,
      Runnable uiRefresh) {
    this(
        transcriptScroll,
        channelListPanel,
        notificationStore,
        targetSelector,
        uiRefresh,
        target -> "",
        (target, topic) -> {},
        target -> DEFAULT_TOPIC_HEIGHT_PX,
        (target, heightPx) -> {});
  }

  public ChatTopicCoordinator(
      JScrollPane transcriptScroll,
      ChannelListPanel channelListPanel,
      NotificationStorePort notificationStore,
      Consumer<TargetRef> targetSelector,
      Runnable uiRefresh,
      Function<TargetRef, String> persistedTopicLookup,
      BiConsumer<TargetRef, String> persistedTopicSink,
      Function<TargetRef, Integer> persistedTopicHeightLookup,
      BiConsumer<TargetRef, Integer> persistedTopicHeightSink) {
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
    this.notificationStore = Objects.requireNonNull(notificationStore, "notificationStore");
    this.targetSelector = targetSelector != null ? targetSelector : unused -> {};
    this.uiRefresh = Objects.requireNonNull(uiRefresh, "uiRefresh");
    this.persistedTopicLookup = persistedTopicLookup != null ? persistedTopicLookup : target -> "";
    this.persistedTopicSink =
        persistedTopicSink != null ? persistedTopicSink : (target, topic) -> {};
    this.persistedTopicHeightLookup =
        persistedTopicHeightLookup != null
            ? persistedTopicHeightLookup
            : target -> DEFAULT_TOPIC_HEIGHT_PX;
    this.persistedTopicHeightSink =
        persistedTopicHeightSink != null ? persistedTopicHeightSink : (target, topicHeightPx) -> {};
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
            int normalized = normalizeTopicHeightPx(location);
            if (lastTopicHeightPx == normalized) return;
            lastTopicHeightPx = normalized;
            persistTopicPanelHeight(activeTarget, normalized);
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
    persistTopicSnapshot(target, normalized);

    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget(activeTarget);
    }
    channelListPanel.refreshOpenChannelDetails(target.serverId(), target.target());
    topicUpdates.onNext(new ChatDockable.TopicUpdate(target, normalized));
  }

  public void clearTopic(TargetRef target, TargetRef activeTarget) {
    if (target == null) return;

    String removed = topicByTarget.remove(target);
    if (removed != null && !removed.isBlank()) {
      persistTopicSnapshot(target, "");
    }
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
    String inMemory = topicByTarget.getOrDefault(target, "");
    if (!inMemory.isBlank()) return inMemory;
    if (!target.isChannel()) return "";

    String persisted = Objects.toString(persistedTopicLookup.apply(target), "").trim();
    if (persisted.isEmpty()) return "";
    topicByTarget.put(target, persisted);
    return persisted;
  }

  public Flowable<ChatDockable.TopicUpdate> topicUpdates() {
    return topicUpdates.onBackpressureLatest();
  }

  public int topicPanelHeightPx() {
    return lastTopicHeightPx;
  }

  public int topicPanelHeightPxFor(TargetRef target) {
    if (target == null || !target.isChannel()) return DEFAULT_TOPIC_HEIGHT_PX;
    Integer inMemory = topicHeightByTarget.get(target);
    if (inMemory != null) return normalizeTopicHeightPx(inMemory);

    Integer persisted = persistedTopicHeightLookup.apply(target);
    int resolved = normalizeTopicHeightPx(persisted == null ? DEFAULT_TOPIC_HEIGHT_PX : persisted);
    topicHeightByTarget.put(target, resolved);
    return resolved;
  }

  public void setTopicPanelHeightPx(int heightPx) {
    int normalized = normalizeTopicHeightPx(heightPx);
    TargetRef target = activeTarget;
    if (target != null && target.isChannel()) {
      setTopicPanelHeightPxFor(target, normalized);
      return;
    }
    lastTopicHeightPx = normalized;
    if (!topicVisible || topicCompact) return;
    topicSplit.setDividerLocation(lastTopicHeightPx);
    uiRefresh.run();
  }

  public void setTopicPanelHeightPxFor(TargetRef target, int heightPx) {
    if (target == null || !target.isChannel()) return;
    int normalized = normalizeTopicHeightPx(heightPx);
    topicHeightByTarget.put(target, normalized);
    persistTopicPanelHeight(target, normalized);
    if (!target.equals(activeTarget)) return;
    lastTopicHeightPx = normalized;
    if (!topicVisible || topicCompact) return;
    topicSplit.setDividerLocation(lastTopicHeightPx);
    uiRefresh.run();
  }

  public void updateTopicPanelForActiveTarget(TargetRef activeTarget) {
    this.activeTarget = activeTarget;
    if (activeTarget == null || !activeTarget.isChannel()) {
      topicPanel.setTopic("", "", "");
      topicPanel.setNotificationState(false, 0);
      topicPanel.setNotificationTooltip(message("chatTopic.notifications.none"));
      hideTopicPanel();
      return;
    }

    NotificationSummary summary = summarizeChannelNotifications(activeTarget);
    topicPanel.setNotificationState(true, summary.totalCount());
    topicPanel.setNotificationTooltip(buildNotificationTooltip(summary));
    lastTopicHeightPx = topicPanelHeightPxFor(activeTarget);

    String topic = topicFor(activeTarget).trim();
    String channelModes = channelModesFor(activeTarget).trim();
    topicPanel.setTopic(activeTarget.target(), channelModes, topic);

    boolean hasTopic = !topic.isEmpty();
    boolean hasChannelModes = !channelModes.isEmpty();
    if (hasTopic || hasChannelModes) {
      showTopicPanel(false);
    } else if (summary.totalCount() > 0) {
      showTopicPanel(true);
    } else {
      hideTopicPanel();
    }
  }

  public void setChannelModeSnapshot(
      String serverId, String channel, String rawModes, TargetRef activeTarget) {
    String sid = Objects.toString(serverId, "").trim();
    String targetName = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || targetName.isEmpty()) return;

    TargetRef target;
    try {
      target = new TargetRef(sid, targetName);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (!target.isChannel()) return;

    String normalized = sanitizeChannelModes(rawModes);
    String before = channelModesByTarget.getOrDefault(target, "");
    if (Objects.equals(before, normalized)) {
      if (target.equals(activeTarget)) {
        updateTopicPanelForActiveTarget(activeTarget);
      }
      return;
    }

    if (normalized.isBlank()) {
      channelModesByTarget.remove(target);
    } else {
      channelModesByTarget.put(target, normalized);
    }
    if (target.equals(activeTarget)) {
      updateTopicPanelForActiveTarget(activeTarget);
    }
  }

  public String channelModesFor(TargetRef target) {
    if (target == null || !target.isChannel()) return "";
    return channelModesByTarget.getOrDefault(target, "");
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
            message("chatTopic.notifications.popup.header", target.target(), summary.totalCount()));
    header.setEnabled(false);
    menu.add(header);

    List<NotificationEntry> previews = summary.previews();
    if (previews.isEmpty()) {
      JMenuItem none = new JMenuItem(message("preferences.notifications.ircEvents.summary.none"));
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

    JMenuItem openNotifications = new JMenuItem(message("chatTopic.notifications.popup.openView"));
    openNotifications.addActionListener(
        e -> targetSelector.accept(TargetRef.notifications(target.serverId())));
    menu.add(openNotifications);

    JMenuItem clearChannel = new JMenuItem(message("common.button.clear"));
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
      String title =
          fromNick.isEmpty()
              ? message("chatTopic.notifications.kind.mention")
              : message("chatTopic.notifications.kind.mention.withNick", fromNick);
      entries.add(new NotificationEntry(ev.at(), title, ev.snippet()));
    }

    for (RuleMatchEvent ev : notificationStore.listAllRuleMatches(serverId)) {
      if (ev == null || !channel.equalsIgnoreCase(Objects.toString(ev.channel(), "").trim()))
        continue;
      String label = Objects.toString(ev.ruleLabel(), "").trim();
      String fromNick = Objects.toString(ev.fromNick(), "").trim();
      String title;
      if (!label.isEmpty() && !fromNick.isEmpty()) {
        title = message("chatTopic.notifications.label.withNick", label, fromNick);
      } else if (!label.isEmpty()) {
        title = label;
      } else if (!fromNick.isEmpty()) {
        title = message("chatTopic.notifications.kind.rule.withNick", fromNick);
      } else {
        title = message("chatTopic.notifications.kind.rule");
      }
      entries.add(new NotificationEntry(ev.at(), title, ev.snippet()));
    }

    for (IrcEventRuleEvent ev : notificationStore.listAllIrcEventRules(serverId)) {
      if (ev == null || !channel.equalsIgnoreCase(Objects.toString(ev.channel(), "").trim()))
        continue;
      String title = Objects.toString(ev.title(), "").trim();
      String fromNick = Objects.toString(ev.fromNick(), "").trim();
      if (!fromNick.isEmpty()) {
        title =
            title.isEmpty()
                ? fromNick
                : message("chatTopic.notifications.label.withNick", title, fromNick);
      }
      if (title.isEmpty()) title = message("chatTopic.notifications.kind.event");
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
    String time =
        entry.at() != null
            ? NOTIFICATION_TIME_FMT.format(entry.at())
            : message("chatTopic.notifications.preview.timeUnknown");
    String title = Objects.toString(entry.title(), "").trim();
    String detail = Objects.toString(entry.detail(), "").trim();
    String line =
        detail.isEmpty()
            ? message("chatTopic.notifications.preview.title", time, title)
            : message("chatTopic.notifications.preview.titleAndDetail", time, title, detail);
    if (line.length() > 160) {
      line = line.substring(0, 159) + "…";
    }
    return line;
  }

  private static String buildNotificationTooltip(NotificationSummary summary) {
    if (summary == null || summary.totalCount() <= 0) {
      return message("chatTopic.notifications.none");
    }
    StringBuilder html = new StringBuilder(512);
    html.append("<html><b>")
        .append(escapeHtml(message("chatTopic.notifications.tooltip.header", summary.totalCount())))
        .append("</b>");
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

  private static String message(String code, Object... args) {
    return MESSAGES.text(code, args);
  }

  private static String escapeHtml(String raw) {
    String s = Objects.toString(raw, "");
    if (s.isEmpty()) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private void persistTopicSnapshot(TargetRef target, String topic) {
    if (target == null || !target.isChannel()) return;
    try {
      persistedTopicSink.accept(target, Objects.toString(topic, ""));
    } catch (Exception ex) {
      log.debug("[ircafe] failed to persist channel topic snapshot for {}", target, ex);
    }
  }

  private void persistTopicPanelHeight(TargetRef target, int heightPx) {
    if (target == null || !target.isChannel()) return;
    int normalized = normalizeTopicHeightPx(heightPx);
    topicHeightByTarget.put(target, normalized);
    try {
      persistedTopicHeightSink.accept(target, normalized);
    } catch (Exception ex) {
      log.debug("[ircafe] failed to persist channel topic panel height for {}", target, ex);
    }
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

  private static String sanitizeChannelModes(String rawModes) {
    if (rawModes == null) return "";
    return rawModes.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
  }

  private static int normalizeTopicHeightPx(int heightPx) {
    return Math.max(MIN_TOPIC_HEIGHT_PX, Math.min(MAX_TOPIC_HEIGHT_PX, heightPx));
  }

  private static final class TopicPanel extends JPanel {
    private final JLabel header = new JLabel();
    private final JTextArea text = new JTextArea();
    private final JButton notificationsButton = new JButton();
    private final Color activeNotificationBackground = new Color(255, 223, 128);
    // The highlighted button uses a light background even in dark themes.
    private final FlatSVGIcon activeNotificationIcon =
        new FlatSVGIcon("icons/svg/bell.svg", 16, 16)
            .setColorFilter(new FlatSVGIcon.ColorFilter(color -> new Color(68, 50, 0)));

    private Runnable onNotificationsClick;

    private TopicPanel() {
      super(new BorderLayout(8, 6));

      header.setFont(header.getFont().deriveFont(Font.BOLD));
      text.setEditable(false);
      text.setLineWrap(true);
      text.setWrapStyleWord(true);
      text.setOpaque(false);
      text.setBorder(null);

      notificationsButton.setIcon(SvgIcons.quiet("bell", 16));
      notificationsButton.setBorderPainted(false);
      notificationsButton.setContentAreaFilled(false);
      notificationsButton.setOpaque(false);
      notificationsButton.setFocusable(false);
      notificationsButton.setFocusPainted(false);
      notificationsButton.setMargin(new java.awt.Insets(1, 4, 1, 4));
      notificationsButton.setPreferredSize(new Dimension(26, 20));
      notificationsButton.setToolTipText(message("chatTopic.notifications.none"));
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

    public void setTopic(String channelName, String channelModes, String topic) {
      String channel = Objects.toString(channelName, "").trim();
      String modes = Objects.toString(channelModes, "").trim();
      String raw = Objects.toString(topic, "").trim();
      boolean hasTopic = !raw.isEmpty();
      String suffix = modes.isEmpty() ? "" : message("chatTopic.header.modeSuffix", modes);
      header.setText(
          channel.isEmpty()
              ? message("chatTopic.header.topic")
              : (hasTopic
                  ? message("chatTopic.header.topic.channel", channel, suffix)
                  : message("chatTopic.header.channel", channel, suffix)));
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
        notificationsButton.setIcon(activeNotificationIcon);
        notificationsButton.setText("");
        notificationsButton.setOpaque(true);
        notificationsButton.setContentAreaFilled(true);
        notificationsButton.setBorderPainted(true);
        notificationsButton.setBackground(activeNotificationBackground);
      } else {
        notificationsButton.setIcon(SvgIcons.quiet("bell", 16));
        notificationsButton.setText("");
        notificationsButton.setOpaque(false);
        notificationsButton.setContentAreaFilled(false);
        notificationsButton.setBorderPainted(false);
        notificationsButton.setBackground(null);
      }
    }

    public void setNotificationTooltip(String tooltip) {
      notificationsButton.setToolTipText(
          Objects.toString(tooltip, message("chatTopic.notifications.none")));
    }

    public JButton notificationsButton() {
      return notificationsButton;
    }
  }
}
