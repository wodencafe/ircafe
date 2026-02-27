package cafe.woden.ircclient.ui.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.interceptors.InterceptorStore;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class InterceptorPanelExecutorLifecycleTest {

  @Test
  void injectedExecutorIsNotShutdownOnClose() throws Exception {
    InterceptorStore store = mockStore();
    ExecutorService refreshExecutor = mock(ExecutorService.class);
    InterceptorPanel panel = onEdtCall(() -> new InterceptorPanel(store, refreshExecutor));

    onEdt(panel::close);
    flushEdt();

    verify(refreshExecutor, never()).shutdownNow();
  }

  @Test
  void defaultConstructorOwnsExecutorAndShutsItDown() throws Exception {
    InterceptorStore store = mockStore();
    InterceptorPanel panel = onEdtCall(() -> new InterceptorPanel(store));
    ExecutorService refreshExecutor = readField(panel, "refreshExecutor", ExecutorService.class);
    assertFalse(refreshExecutor.isShutdown());

    onEdt(panel::close);
    flushEdt();

    assertTrue(refreshExecutor.isShutdown());
  }

  @Test
  void rejectsShutdownExecutor() {
    InterceptorStore store = mockStore();
    ExecutorService refreshExecutor = mock(ExecutorService.class);
    when(refreshExecutor.isShutdown()).thenReturn(true);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new InterceptorPanel(store, refreshExecutor));
    assertEquals("refreshExecutor must be active", ex.getMessage());
  }

  private static InterceptorStore mockStore() {
    InterceptorStore store = mock(InterceptorStore.class);
    when(store.changes()).thenReturn(Flowable.never());
    return store;
  }

  private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void onEdt(ThrowingRunnable runnable) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
      return;
    }
    AtomicReference<Throwable> err = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Throwable t) {
            err.set(t);
          }
        });
    rethrowIfNeeded(err.get());
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    AtomicReference<T> out = new AtomicReference<>();
    AtomicReference<Throwable> err = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Throwable t) {
            err.set(t);
          }
        });
    rethrowIfNeeded(err.get());
    return out.get();
  }

  private static void rethrowIfNeeded(Throwable t) throws Exception {
    if (t == null) return;
    if (t instanceof Exception e) throw e;
    if (t instanceof Error e) throw e;
    throw new RuntimeException(t);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}

