package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.TargetRef;
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
                     UiSettingsBus settingsBus) {
    super(settingsBus);
    this.transcripts = transcripts;
    this.activationBus = activationBus;
    this.outboundBus = outboundBus;

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

  @Override
  protected void onTranscriptClicked() {
    // Clicking the main transcript should make it the active target for input/status.
    // This does NOT change what the main chat is already displaying.
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
