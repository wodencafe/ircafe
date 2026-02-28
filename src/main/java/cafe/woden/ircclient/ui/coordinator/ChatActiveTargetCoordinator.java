package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.input.MessageInputPanel;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.text.StyledDocument;

/** Owns active-target switching flow orchestration for {@link ChatDockable}. */
public final class ChatActiveTargetCoordinator {

  private final Supplier<TargetRef> activeTargetSupplier;
  private final Consumer<TargetRef> activeTargetSetter;
  private final MessageInputPanel inputPanel;
  private final Map<TargetRef, String> draftByTarget;
  private final Runnable updateScrollStateFromBar;
  private final Runnable dockTitleUpdater;
  private final Runnable typingAvailabilityRefresher;
  private final BiConsumer<TargetRef, TargetRef> interceptorTargetChangeHandler;
  private final Function<TargetRef, ChatTargetViewRouter.TargetViewType> targetViewRouter;
  private final ChatTranscriptStore transcripts;
  private final Consumer<StyledDocument> transcriptDocumentSetter;
  private final Runnable topicPanelUpdater;
  private final BiConsumer<TargetRef, Integer> readMarkerViewStateApplier;
  private final Consumer<Runnable> laterInvoker;
  private final Runnable revalidateUi;
  private final Runnable repaintUi;

  public ChatActiveTargetCoordinator(
      Supplier<TargetRef> activeTargetSupplier,
      Consumer<TargetRef> activeTargetSetter,
      MessageInputPanel inputPanel,
      Map<TargetRef, String> draftByTarget,
      Runnable updateScrollStateFromBar,
      Runnable dockTitleUpdater,
      Runnable typingAvailabilityRefresher,
      BiConsumer<TargetRef, TargetRef> interceptorTargetChangeHandler,
      Function<TargetRef, ChatTargetViewRouter.TargetViewType> targetViewRouter,
      ChatTranscriptStore transcripts,
      Consumer<StyledDocument> transcriptDocumentSetter,
      Runnable topicPanelUpdater,
      BiConsumer<TargetRef, Integer> readMarkerViewStateApplier,
      Consumer<Runnable> laterInvoker,
      Runnable revalidateUi,
      Runnable repaintUi) {
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.activeTargetSetter = Objects.requireNonNull(activeTargetSetter, "activeTargetSetter");
    this.inputPanel = Objects.requireNonNull(inputPanel, "inputPanel");
    this.draftByTarget = Objects.requireNonNull(draftByTarget, "draftByTarget");
    this.updateScrollStateFromBar =
        Objects.requireNonNull(updateScrollStateFromBar, "updateScrollStateFromBar");
    this.dockTitleUpdater = Objects.requireNonNull(dockTitleUpdater, "dockTitleUpdater");
    this.typingAvailabilityRefresher =
        Objects.requireNonNull(typingAvailabilityRefresher, "typingAvailabilityRefresher");
    this.interceptorTargetChangeHandler =
        Objects.requireNonNull(interceptorTargetChangeHandler, "interceptorTargetChangeHandler");
    this.targetViewRouter = Objects.requireNonNull(targetViewRouter, "targetViewRouter");
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
    this.transcriptDocumentSetter =
        Objects.requireNonNull(transcriptDocumentSetter, "transcriptDocumentSetter");
    this.topicPanelUpdater = Objects.requireNonNull(topicPanelUpdater, "topicPanelUpdater");
    this.readMarkerViewStateApplier =
        Objects.requireNonNull(readMarkerViewStateApplier, "readMarkerViewStateApplier");
    this.laterInvoker = Objects.requireNonNull(laterInvoker, "laterInvoker");
    this.revalidateUi = Objects.requireNonNull(revalidateUi, "revalidateUi");
    this.repaintUi = Objects.requireNonNull(repaintUi, "repaintUi");
  }

  public void setActiveTarget(TargetRef target) {
    if (target == null) return;

    TargetRef previousTarget = activeTargetSupplier.get();
    if (Objects.equals(previousTarget, target)) return;

    // Incoming typing indicators are per-target. Clear before switching buffers.
    inputPanel.clearRemoteTypingIndicator();

    // Persist state + draft of the previous target before swapping.
    if (previousTarget != null) {
      inputPanel.flushTypingForBufferSwitch();
      draftByTarget.put(previousTarget, inputPanel.getDraftText());
      updateScrollStateFromBar.run();
    }

    activeTargetSetter.accept(target);
    dockTitleUpdater.run();
    typingAvailabilityRefresher.run();
    setInputPanelVisibleForTarget(target);
    interceptorTargetChangeHandler.accept(previousTarget, target);

    ChatTargetViewRouter.TargetViewType targetView = targetViewRouter.apply(target);
    if (targetView == ChatTargetViewRouter.TargetViewType.UI_ONLY) {
      // UI-only views do not accept input. Clear draft to avoid stale text confusion.
      inputPanel.setDraftText("");
      topicPanelUpdater.run();
      return;
    }

    transcripts.ensureTargetExists(target);
    transcriptDocumentSetter.accept(transcripts.document(target));
    int unreadJumpOffset = transcripts.readMarkerJumpOffset(target);

    // Restore any saved draft for this target.
    inputPanel.setDraftText(draftByTarget.getOrDefault(target, ""));

    topicPanelUpdater.run();

    laterInvoker.accept(() -> readMarkerViewStateApplier.accept(target, unreadJumpOffset));

    // UX: selecting a different buffer should let the user immediately start typing.
    // (No-op when input is disabled.)
    inputPanel.focusInput();
  }

  private void setInputPanelVisibleForTarget(TargetRef target) {
    boolean visible = target != null && !target.isUiOnly();
    if (inputPanel.isVisible() == visible) return;
    inputPanel.setVisible(visible);
    revalidateUi.run();
    repaintUi.run();
  }
}
