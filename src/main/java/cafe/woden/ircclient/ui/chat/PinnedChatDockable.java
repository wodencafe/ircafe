package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.MessageInputPanel;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.ActiveInputRouter;
import cafe.woden.ircclient.ui.OutboundLineBus;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * A pinned chat view which shares the transcript document with the main chat,
 * but can be split/undocked into its own window.
 *
 *
 * <p>We are intentionally NOT doing the "context-aware outbound" refactor yet.
 * For now, sending from a pinned dock activates the target first, then emits
 * the raw line into the shared outbound bus.
 */
public class PinnedChatDockable extends ChatViewPanel implements Dockable, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(PinnedChatDockable.class);

  private final java.util.concurrent.atomic.AtomicBoolean typingUnavailableWarned =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  private static final long READ_MARKER_SEND_COOLDOWN_MS = 3000L;

  private final TargetRef target;
  private final ChatTranscriptStore transcripts;
  private final ChatHistoryService chatHistoryService;
  private final String persistentId;

  private boolean followTail = true;
  private int savedScrollValue = 0;
  private long lastReadMarkerSentAtMs = 0L;

  private final Consumer<TargetRef> activate;
  private final OutboundLineBus outboundBus;
  private final IrcClientService irc;
  private final BiConsumer<TargetRef, String> onDraftChanged;
  private final BiConsumer<TargetRef, String> onClosed;

  private final ActiveInputRouter activeInputRouter;

  private final MessageInputPanel inputPanel;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final TopicPanel topicPanel = new TopicPanel();
  private final JSplitPane topicSplit;
  private static final int TOPIC_DIVIDER_SIZE = 6;
  private int lastTopicHeightPx = 58;
  private boolean topicVisible = false;

  public PinnedChatDockable(TargetRef target,
                           ChatTranscriptStore transcripts,
                           UiSettingsBus settingsBus,
                           ChatHistoryService chatHistoryService,
                           CommandHistoryStore historyStore,
                           Consumer<TargetRef> activate,
                           OutboundLineBus outboundBus,
                           IrcClientService irc,
                           ActiveInputRouter activeInputRouter,
                           BiConsumer<TargetRef, String> onDraftChanged,
                           BiConsumer<TargetRef, String> onClosed) {
    super(settingsBus);
    this.target = target;
    this.transcripts = transcripts;
    this.chatHistoryService = chatHistoryService;
    this.activate = activate;
    this.outboundBus = outboundBus;
    this.irc = irc;
    this.activeInputRouter = activeInputRouter;
    this.onDraftChanged = onDraftChanged;
    this.onClosed = onClosed;
    // Use a folded key so "##Llamas" and "##llamas" resolve to the same persistent dock id.
    this.persistentId = "chat-pinned:" + b64(target.serverId()) + ":" + b64(target.key());

    setName(getTabText());
    setDocument(transcripts.document(target));
    remove(scroll);
    topicSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topicPanel, scroll);
    topicSplit.setResizeWeight(0.0);
    topicSplit.setBorder(null);
    topicSplit.setOneTouchExpandable(true);
    topicPanel.setMinimumSize(new Dimension(0, 0));
    topicPanel.setPreferredSize(new Dimension(10, lastTopicHeightPx));
    topicSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
      if (!topicVisible) return;
      Object v = evt.getNewValue();
      if (v instanceof Integer i) {
        lastTopicHeightPx = Math.max(0, i);
      }
    });
    add(topicSplit, BorderLayout.CENTER);
    hideTopicPanel();

    // Context menu: Clear (buffer only) + Reload recent history (clear + reload from DB/bouncer).
    setTranscriptContextMenuActions(
        () -> {
          try {
            if (this.target == null || this.target.isUiOnly()) return;
            transcripts.clearTarget(this.target);
          } catch (Exception ignored) {
          }
        },
        () -> {
          try {
            if (this.target == null || this.target.isUiOnly()) return;
            if (chatHistoryService != null && chatHistoryService.canReloadRecent(this.target)) {
              chatHistoryService.reloadRecent(this.target);
            }
          } catch (Exception ignored) {
          }
        }
    );

    // Input panel embedded in the pinned view.
    this.inputPanel = new MessageInputPanel(settingsBus, historyStore);
    add(inputPanel, BorderLayout.SOUTH);

    // Persist draft text continuously so closing/undocking doesn't lose the latest draft.
    inputPanel.setOnDraftChanged(draft -> {
      try {
        if (this.onDraftChanged != null) {
          this.onDraftChanged.accept(this.target, draft == null ? "" : draft);
        }
      } catch (Exception ignored) {
      }
    });
    inputPanel.setOnTypingStateChanged(this::onLocalTypingStateChanged);

    if (this.activeInputRouter != null) {
      inputPanel.setOnActivated(() -> {
        this.activeInputRouter.activate(inputPanel);
        if (activate != null) {
          activate.accept(target);
        }
      });
    }

    // Forward outbound lines into the shared bus, but first activate this target so
    // existing "active target" based app logic continues to work.
    disposables.add(
        inputPanel.outboundMessages().subscribe(line -> {
          armTailPinOnNextAppendIfAtBottom();
          if (activeInputRouter != null) {
            activeInputRouter.activate(inputPanel);
          }
          if (activate != null) {
            activate.accept(target);
          }
          if (outboundBus != null) {
            outboundBus.emit(line);
          }
        }, err -> {
          // Never crash UI because outbound stream had an error.
        })
    );

    SwingUtilities.invokeLater(this::applyReadMarkerViewState);
  }

  /**
   * Enable/disable the embedded input bar.
   */
  public void setInputEnabled(boolean enabled) {
    inputPanel.setInputEnabled(enabled);
  }

  /**
   * Update the nick completion list for the embedded input bar.
   */
  public void setNickCompletions(List<String> nicks) {
    inputPanel.setNickCompletions(nicks);
  }

  /**
   * Restore draft text for this pinned dock (e.g., when reopening).
   */
  public void setDraftText(String text) {
    inputPanel.setDraftText(text);
  }

  /**
   * Read current draft text for persistence.
   */
  public String getDraftText() {
    return inputPanel.getDraftText();
  }

  public void setTopic(String topic) {
    if (target == null || !target.isChannel()) {
      topicPanel.setTopic("", "");
      hideTopicPanel();
      return;
    }

    String sanitized = sanitizeTopic(topic).trim();
    if (sanitized.isEmpty()) {
      topicPanel.setTopic(target.target(), "");
      hideTopicPanel();
      return;
    }

    topicPanel.setTopic(target.target(), sanitized);
    showTopicPanel();
  }

  @Override
  protected void onTranscriptClicked() {
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    inputPanel.focusInput();
  }

  public void showTypingIndicator(String nick, String state) {
    if (nick == null || nick.isBlank()) return;
    inputPanel.showRemoteTypingIndicator(nick, state);
  }

  public void clearTypingIndicator() {
    inputPanel.clearRemoteTypingIndicator();
  }

  public boolean normalizeIrcv3DraftForCapabilities(boolean replySupported, boolean reactSupported) {
    return inputPanel.normalizeIrcv3DraftForCapabilities(replySupported, reactSupported);
  }

  @Override
  protected boolean replyContextActionVisible() {
    if (target == null || target.isStatus() || target.isUiOnly()) return false;
    if (irc == null) return false;
    try {
      return irc.isDraftReplyAvailable(target.serverId());
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  protected boolean reactContextActionVisible() {
    if (target == null || target.isStatus() || target.isUiOnly()) return false;
    if (irc == null) return false;
    try {
      return irc.isDraftReactAvailable(target.serverId());
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  protected boolean editContextActionVisible() {
    if (target == null || target.isStatus() || target.isUiOnly()) return false;
    return isMessageEditSupportedForServer(target.serverId());
  }

  @Override
  protected boolean redactContextActionVisible() {
    if (target == null || target.isStatus() || target.isUiOnly()) return false;
    return isMessageRedactionSupportedForServer(target.serverId());
  }

  @Override
  protected boolean loadNewerHistoryContextActionVisible() {
    if (target == null || target.isStatus() || target.isUiOnly()) return false;
    return isLoadNewerHistorySupportedForServer(target.serverId());
  }

  @Override
  protected boolean loadAroundMessageContextActionVisible() {
    if (target == null || target.isStatus() || target.isUiOnly()) return false;
    return isChatHistorySupportedForServer(target.serverId());
  }

  @Override
  protected void onLoadNewerHistoryRequested() {
    if (!loadNewerHistoryContextActionVisible()) return;
    if (isChatHistorySupportedForServer(target.serverId())) {
      emitHistoryCommand(buildChatHistoryLatestCommand());
      return;
    }
    if (chatHistoryService != null && chatHistoryService.canReloadRecent(target)) {
      chatHistoryService.reloadRecent(target);
    }
  }

  @Override
  protected void onLoadContextAroundMessageRequested(String messageId) {
    if (!loadAroundMessageContextActionVisible()) return;
    String line = buildChatHistoryAroundByMsgIdCommand(messageId);
    if (line.isBlank()) return;
    emitHistoryCommand(line);
  }

  @Override
  protected void onReplyToMessageRequested(String messageId) {
    if (!replyContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    inputPanel.beginReplyCompose(target.target(), msgId);
    inputPanel.focusInput();
  }

  @Override
  protected void onReactToMessageRequested(String messageId) {
    if (!reactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    inputPanel.openQuickReactionPicker(target.target(), msgId);
    inputPanel.focusInput();
  }

  @Override
  protected void onEditMessageRequested(String messageId) {
    if (!editContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    inputPanel.setDraftText("/edit " + msgId + " ");
    inputPanel.focusInput();
  }

  @Override
  protected void onRedactMessageRequested(String messageId) {
    if (!redactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    emitHistoryCommand("/redact " + msgId);
  }

  private void emitHistoryCommand(String line) {
    String cmd = Objects.toString(line, "").trim();
    if (cmd.isEmpty()) return;
    if (target == null || target.isStatus() || target.isUiOnly()) return;
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    armTailPinOnNextAppendIfAtBottom();
    if (outboundBus != null) {
      outboundBus.emit(cmd);
    }
  }

  @Override
  protected boolean onChannelClicked(String channel) {
    if (channel == null || channel.isBlank()) return false;
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    if (outboundBus != null) {
      outboundBus.emit("/join " + channel.trim());
      return true;
    }
    return false;
  }

  @Override
  protected boolean onMessageReferenceClicked(String messageId) {
    int off = transcripts.messageOffsetById(target, messageId);
    if (off >= 0) {
      setFollowTail(false);
      scrollToTranscriptOffset(off);
      return true;
    }

    if (!loadAroundMessageContextActionVisible()) return false;
    String line = buildChatHistoryAroundByMsgIdCommand(messageId);
    if (line.isBlank()) return false;
    emitHistoryCommand(line);
    return true;
  }

  @Override
  public String getPersistentID() {
    return persistentId;
  }

  @Override
  public String getTabText() {
    return target.target();
  }

  @Override
  protected boolean isFollowTail() {
    return followTail;
  }

  @Override
  protected void setFollowTail(boolean followTail) {
    boolean was = this.followTail;
    this.followTail = followTail;
    if (!was && followTail) {
      maybeSendReadMarker();
    }
  }

  @Override
  protected int getSavedScrollValue() {
    return savedScrollValue;
  }

  @Override
  protected void setSavedScrollValue(int value) {
    this.savedScrollValue = value;
  }

  public TargetRef target() {
    return target;
  }

  public void onShown() {
    SwingUtilities.invokeLater(this::applyReadMarkerViewState);
  }

  @Override
  public void close() {
    try {
      inputPanel.flushTypingDone();
    } catch (Exception ignored) {
    }
    try {
      disposables.dispose();
    } catch (Exception ignored) {
    }
    try {
      if (onClosed != null) {
        onClosed.accept(target, inputPanel.getDraftText());
      }
    } catch (Exception ignored) {
    }
    closeDecorators();
  }

  private static String b64(String s) {
    if (s == null) s = "";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
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
      String ch = (channelName == null) ? "" : channelName.trim();
      header.setText(ch.isEmpty() ? "Topic" : "Topic - " + ch);
      text.setText(topic == null ? "" : topic);
      text.setCaretPosition(0);
    }
  }

  private void onLocalTypingStateChanged(String state) {
    if (target == null || target.isStatus() || target.isUiOnly()) return;
    if (irc == null) return;
    if (!irc.isTypingAvailable(target.serverId())) {
      String s = normalizeTypingState(state);
      if (!"done".equals(s) && typingUnavailableWarned.compareAndSet(false, true)) {
        String reason = Objects.toString(irc.typingAvailabilityReason(target.serverId()), "").trim();
        if (reason.isEmpty()) reason = "not negotiated / not allowed";
        log.info("[{}] typing indicators are enabled, but unavailable on this server ({})", target.serverId(), reason);
      }
      return;
    }
    typingUnavailableWarned.set(false);
    String s = normalizeTypingState(state);
    if (s.isEmpty()) return;
    irc.sendTyping(target.serverId(), target.target(), s).subscribe(
        () -> {},
        err -> {
          if (log.isDebugEnabled()) {
            log.debug(
                "[{}] typing send failed (target={} state={}): {}",
                target.serverId(),
                target.target(),
                s,
                err.toString());
          }
        });
  }

  private static String normalizeTypingState(String state) {
    String s = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return "";
    return switch (s) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }

  private boolean isLoadNewerHistorySupportedForServer(String serverId) {
    return isChatHistorySupportedForServer(serverId) || isZncPlaybackSupportedForServer(serverId);
  }

  private boolean isChatHistorySupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isChatHistoryAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isZncPlaybackSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isZncPlaybackAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isMessageEditSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isMessageEditAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isMessageRedactionSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isMessageRedactionAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private void applyReadMarkerViewState() {
    int unreadJumpOffset = transcripts.readMarkerJumpOffset(target);
    if (unreadJumpOffset >= 0) {
      setFollowTail(false);
      scrollToTranscriptOffset(unreadJumpOffset);
      updateScrollStateFromBar();
      return;
    }

    if (isTranscriptAtBottom()) {
      maybeSendReadMarker();
    }
  }

  private void maybeSendReadMarker() {
    if (target == null || target.isStatus() || target.isUiOnly()) return;
    if (irc == null || !irc.isReadMarkerAvailable(target.serverId())) return;

    long now = System.currentTimeMillis();
    if ((now - lastReadMarkerSentAtMs) < READ_MARKER_SEND_COOLDOWN_MS) return;
    lastReadMarkerSentAtMs = now;

    transcripts.updateReadMarker(target, now);
    irc.sendReadMarker(target.serverId(), target.target(), Instant.ofEpochMilli(now)).subscribe(() -> {}, err -> {});
  }
}
