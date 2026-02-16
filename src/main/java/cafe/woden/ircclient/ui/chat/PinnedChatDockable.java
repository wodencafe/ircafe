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

import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
  protected void onReplyToMessageRequested(String messageId) {
    if (!replyContextActionVisible()) return;
    String draft = buildReplyPrefillDraft(target.target(), messageId);
    if (draft.isBlank()) return;
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    inputPanel.setDraftText(draft);
    inputPanel.focusInput();
  }

  @Override
  protected void onReactToMessageRequested(String messageId) {
    if (!reactContextActionVisible()) return;
    String draft = buildReactPrefillDraft(target.target(), messageId);
    if (draft.isBlank()) return;
    if (activeInputRouter != null) {
      activeInputRouter.activate(inputPanel);
    }
    if (activate != null) {
      activate.accept(target);
    }
    inputPanel.setDraftText(draft);
    inputPanel.focusInput();
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
    if (off < 0) return false;
    setFollowTail(false);
    scrollToTranscriptOffset(off);
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

  private void onLocalTypingStateChanged(String state) {
    if (target == null || target.isStatus() || target.isUiOnly()) return;
    if (irc == null || !irc.isTypingAvailable(target.serverId())) return;
    String s = normalizeTypingState(state);
    if (s.isEmpty()) return;
    irc.sendTyping(target.serverId(), target.target(), s).subscribe(() -> {}, err -> {});
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
