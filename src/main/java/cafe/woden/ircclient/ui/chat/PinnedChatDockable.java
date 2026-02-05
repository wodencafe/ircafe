package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.MessageInputPanel;
import cafe.woden.ircclient.ui.ActiveInputRouter;
import cafe.woden.ircclient.ui.OutboundLineBus;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A pinned chat view which shares the transcript document with the main chat,
 * but can be split/undocked into its own window.
 *
 * <p>Step 4: embed a MessageInputPanel so the pinned view has its own input bar.
 *
 * <p>We are intentionally NOT doing the "context-aware outbound" refactor yet.
 * For now, sending from a pinned dock activates the target first, then emits
 * the raw line into the shared outbound bus.
 */
public class PinnedChatDockable extends ChatViewPanel implements Dockable, AutoCloseable {

  private final TargetRef target;
  private final String persistentId;

  private boolean followTail = true;
  private int savedScrollValue = 0;

  private final Consumer<TargetRef> activate;
  private final OutboundLineBus outboundBus;
  private final BiConsumer<TargetRef, String> onDraftChanged;
  private final BiConsumer<TargetRef, String> onClosed;

  private final ActiveInputRouter activeInputRouter;

  private final MessageInputPanel inputPanel;
  private final CompositeDisposable disposables = new CompositeDisposable();

  public PinnedChatDockable(TargetRef target,
                           ChatTranscriptStore transcripts,
                           UiSettingsBus settingsBus,
                           Consumer<TargetRef> activate,
                           OutboundLineBus outboundBus,
                           ActiveInputRouter activeInputRouter,
                           BiConsumer<TargetRef, String> onDraftChanged,
                           BiConsumer<TargetRef, String> onClosed) {
    super(settingsBus);
    this.target = target;
    this.activate = activate;
    this.outboundBus = outboundBus;
    this.activeInputRouter = activeInputRouter;
    this.onDraftChanged = onDraftChanged;
    this.onClosed = onClosed;
    this.persistentId = "chat-pinned:" + b64(target.serverId()) + ":" + b64(target.target());

    setName(getTabText());
    setDocument(transcripts.document(target));

    // Input panel embedded in the pinned view.
    this.inputPanel = new MessageInputPanel(settingsBus);
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
    this.followTail = followTail;
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

  @Override
  public void close() {
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
}
