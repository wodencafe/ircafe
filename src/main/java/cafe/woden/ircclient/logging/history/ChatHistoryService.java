package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.TargetRef;
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

  void reset(TargetRef target);
}
