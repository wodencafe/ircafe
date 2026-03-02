package cafe.woden.ircclient.ui.servertree;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Small helper for marshaling reads/writes onto the Swing EDT. */
public final class ServerTreeEdtExecutor {

  public <T> T read(Supplier<T> supplier, T fallback, Consumer<Exception> onFailure) {
    Objects.requireNonNull(supplier, "supplier");
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }

    AtomicReference<T> out = new AtomicReference<>(fallback);
    try {
      SwingUtilities.invokeAndWait(() -> out.set(supplier.get()));
      return out.get();
    } catch (Exception ex) {
      if (onFailure != null) {
        onFailure.accept(ex);
      }
      return fallback;
    }
  }

  public void write(Runnable task) {
    Objects.requireNonNull(task, "task");
    if (SwingUtilities.isEventDispatchThread()) {
      task.run();
    } else {
      SwingUtilities.invokeLater(task);
    }
  }
}
