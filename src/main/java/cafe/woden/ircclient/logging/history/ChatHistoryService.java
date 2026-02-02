package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.TargetRef;
import java.util.function.Consumer;

/**
 * Loads recent chat history from the logging store into the transcript when a target is opened/selected.
 */
public interface ChatHistoryService {

  /**
   * Called when the user selects a chat target.
   *
   * <p>Implementations should return quickly (no blocking on the EDT).
   */
  void onTargetSelected(TargetRef target);

  /** True if this service is backed by a persistent store (i.e., logging is enabled). */
  boolean isEnabled();

  /**
   * Whether older history can be loaded for the given target.
   *
   * <p>This is based on the currently-known oldest loaded cursor for the target and whether the
   * repository has additional rows older than that cursor.
   */
  boolean canLoadOlder(TargetRef target);

  /**
   * Load older history for the given target.
   *
   * <p>The callback is always invoked on the Swing EDT.
   */
  void loadOlder(TargetRef target, int limit, Consumer<LoadOlderResult> callback);

  /**
   * Clear any cached paging state for a target (e.g., after the underlying log store is purged).
   */
  void reset(TargetRef target);
}
