package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.app.TargetRef;
import java.util.List;
import java.util.function.Consumer;

/** No-op implementation used when logging is disabled. */
public final class NoOpChatHistoryService implements ChatHistoryService {
  @Override
  public void onTargetSelected(TargetRef target) {
    // no-op
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public boolean canLoadOlder(TargetRef target) {
    return false;
  }

  @Override
  public void loadOlder(TargetRef target, int limit, Consumer<LoadOlderResult> callback) {
    if (callback == null) return;
    callback.accept(new LoadOlderResult(List.of(), new LogCursor(0, 0), false));
  }

  @Override
  public void reset(TargetRef target) {
    // no-op
  }
}
