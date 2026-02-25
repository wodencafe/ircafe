package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.function.Consumer;

public interface ChatHistoryService {

  /**
   * Called when the user selects a chat target.
   *
   * <p>Implementations should return quickly (no blocking on the EDT).
   */
  void onTargetSelected(TargetRef target);

  boolean isEnabled();

  /** Whether older history can be loaded for the given target. */
  boolean canLoadOlder(TargetRef target);

  /**
   * Load older history for the given target.
   *
   * <p>The callback is always invoked on the Swing EDT.
   */
  void loadOlder(TargetRef target, int limit, Consumer<LoadOlderResult> callback);

  /** Reset any per-target paging state/cursors. */
  void reset(TargetRef target);

  /**
   * Whether "Reload recent history" is available for this target.
   *
   * <p>Default: same as {@link #isEnabled()}.
   */
  default boolean canReloadRecent(TargetRef target) {
    if (target == null) return false;
    if (target.isUiOnly()) return false;
    if (target.isStatus()) return false;
    return isEnabled();
  }

  /**
   * Reload the most recent history slice for this target.
   *
   * <p>Default: {@link #reset(TargetRef)} then {@link #onTargetSelected(TargetRef)}.
   */
  default void reloadRecent(TargetRef target) {
    reset(target);
    onTargetSelected(target);
  }
}
