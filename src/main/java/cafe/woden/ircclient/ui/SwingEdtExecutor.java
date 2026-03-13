package cafe.woden.ircclient.ui;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Small helper to centralize EDT handoff for Swing-facing adapters. */
final class SwingEdtExecutor {

  void run(Runnable task) {
    if (SwingUtilities.isEventDispatchThread()) {
      task.run();
    } else {
      SwingUtilities.invokeLater(task);
    }
  }

  <T> T call(Supplier<T> supplier, T fallback) {
    if (supplier == null) return fallback;
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception ignored) {
        return fallback;
      }
    }

    AtomicReference<T> out = new AtomicReference<>(fallback);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              out.set(supplier.get());
            } catch (Exception ignored) {
              out.set(fallback);
            }
          });
    } catch (Exception ignored) {
      return fallback;
    }
    return out.get();
  }
}
