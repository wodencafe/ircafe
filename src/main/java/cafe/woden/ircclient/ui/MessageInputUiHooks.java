package cafe.woden.ircclient.ui;

/**
 * Small mediator-style hook surface used by message-input support classes.
 *
 * <p>This keeps support constructors from taking a pile of loosely-related callbacks. It is
 * intentionally small and UI-focused.
 */
public interface MessageInputUiHooks {

  /** Refresh the small input hint popup (e.g. "Tab -> nick"). */
  void updateHint();

  /** Mark completion popup UI as dirty (used when theme/overlay UI changes). */
  void markCompletionUiDirty();

  /** Run a block as a programmatic edit (suppresses user-driven listeners). */
  void runProgrammaticEdit(Runnable r);

  /** Focus the input field (best-effort). */
  void focusInput();

  /** Emit typing "done" (best-effort). */
  void flushTypingDone();

  /** Notify draft listeners that the draft text changed (best-effort). */
  void fireDraftChanged();

  /** Push an outbound command/message line (best-effort). */
  void sendOutbound(String line);

  /** Convenience for the common case where both hint + completion should refresh. */
  default void refreshHintAndCompletion() {
    updateHint();
    markCompletionUiDirty();
  }
}
