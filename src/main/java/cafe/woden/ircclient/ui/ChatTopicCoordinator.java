package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

/** Owns topic state, topic panel rendering, and topic update events for {@link ChatDockable}. */
final class ChatTopicCoordinator {

  private static final int TOPIC_DIVIDER_SIZE = 6;

  private final Map<TargetRef, String> topicByTarget = new HashMap<>();
  private final FlowableProcessor<ChatDockable.TopicUpdate> topicUpdates =
      PublishProcessor.<ChatDockable.TopicUpdate>create().toSerialized();
  private final TopicPanel topicPanel = new TopicPanel();
  private final JSplitPane topicSplit;
  private final ChannelListPanel channelListPanel;
  private final Runnable uiRefresh;

  private int lastTopicHeightPx = 58;
  private boolean topicVisible = false;

  ChatTopicCoordinator(
      JScrollPane transcriptScroll, ChannelListPanel channelListPanel, Runnable uiRefresh) {
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
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
          if (!topicVisible) return;
          Object value = evt.getNewValue();
          if (value instanceof Integer location) {
            lastTopicHeightPx = Math.max(0, location);
          }
        });

    hideTopicPanel();
  }

  JComponent topicSplit() {
    return topicSplit;
  }

  void setTopic(TargetRef target, String topic, TargetRef activeTarget) {
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

  void clearTopic(TargetRef target, TargetRef activeTarget) {
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

  String topicFor(TargetRef target) {
    if (target == null) return "";
    return topicByTarget.getOrDefault(target, "");
  }

  Flowable<ChatDockable.TopicUpdate> topicUpdates() {
    return topicUpdates.onBackpressureLatest();
  }

  void updateTopicPanelForActiveTarget(TargetRef activeTarget) {
    if (activeTarget == null || !activeTarget.isChannel()) {
      topicPanel.setTopic("", "");
      hideTopicPanel();
      return;
    }

    String topic = Objects.toString(topicByTarget.getOrDefault(activeTarget, ""), "").trim();
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
    uiRefresh.run();
  }

  private void hideTopicPanel() {
    topicVisible = false;
    topicPanel.setVisible(false);
    topicSplit.setDividerSize(0);
    topicSplit.setDividerLocation(0);
    uiRefresh.run();
  }

  private static String sanitizeTopic(String topic) {
    if (topic == null) return "";
    // Strip IRC formatting control chars and other low ASCII controls.
    return topic.replaceAll("[\\x00-\\x1F\\x7F]", "");
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
      String channel = Objects.toString(channelName, "").trim();
      header.setText(channel.isEmpty() ? "Topic" : "Topic — " + channel);
      text.setText(topic == null ? "" : topic);
      text.setCaretPosition(0);
    }
  }
}
