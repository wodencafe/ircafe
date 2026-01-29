package cafe.woden.ircclient.ui.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Composite AutoCloseable.
 *
 * <p>Tracks resources (listeners, subscriptions, decorators) and closes them in reverse order.
 * Safe to close multiple times.
 */
public final class CloseableScope implements AutoCloseable {

  private final Deque<AutoCloseable> stack = new ArrayDeque<>();
  private boolean closed = false;

  /**
   * Adds a closeable to this scope.
   * If the scope is already closed, the closeable is closed immediately.
   */
  public synchronized <T extends AutoCloseable> T add(T closeable) {
    if (closeable == null) return null;
    if (closed) {
      try {
        closeable.close();
      } catch (Exception ignored) {
      }
      return closeable;
    }
    stack.push(closeable);
    return closeable;
  }

  /** Convenience for adding a Runnable cleanup action. */
  public void addCleanup(Runnable cleanup) {
    if (cleanup == null) return;
    add((AutoCloseable) cleanup::run);
  }

  public synchronized boolean isClosed() {
    return closed;
  }

  @Override
  public void close() throws Exception {
    Deque<AutoCloseable> toClose;
    synchronized (this) {
      if (closed) return;
      closed = true;
      toClose = new ArrayDeque<>(stack);
      stack.clear();
    }

    Exception first = null;
    while (!toClose.isEmpty()) {
      AutoCloseable c = toClose.pop();
      try {
        c.close();
      } catch (Exception e) {
        if (first == null) {
          first = e;
        } else {
          first.addSuppressed(e);
        }
      }
    }

    if (first != null) throw first;
  }

  /** Close the scope and ignore any exceptions. */
  public void closeQuietly() {
    try {
      close();
    } catch (Exception ignored) {
    }
  }
}
