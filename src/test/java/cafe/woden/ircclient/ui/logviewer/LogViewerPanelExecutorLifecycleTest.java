package cafe.woden.ircclient.ui.logviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LogViewerPanelExecutorLifecycleTest {

  @Test
  void injectedExecutorIsNotShutdownOnClose() throws Exception {
    ChatLogViewerService service = mock(ChatLogViewerService.class);
    when(service.enabled()).thenReturn(false);
    ExecutorService exec = mock(ExecutorService.class);
    LogViewerPanel panel = onEdtCall(() -> new LogViewerPanel(service, sid -> List.of(), exec));

    onEdt(panel::close);
    flushEdt();

    verify(exec, never()).shutdownNow();
  }

  @Test
  void closeCancelsRunningTask() throws Exception {
    ChatLogViewerService service = mock(ChatLogViewerService.class);
    when(service.enabled()).thenReturn(false);
    ExecutorService exec = mock(ExecutorService.class);
    @SuppressWarnings("unchecked")
    Future<Object> runningTask = mock(Future.class);
    LogViewerPanel panel = onEdtCall(() -> new LogViewerPanel(service, sid -> List.of(), exec));
    setField(panel, "runningTask", runningTask);

    onEdt(panel::close);
    flushEdt();

    verify(runningTask).cancel(true);
    verify(exec, never()).shutdownNow();
  }

  @Test
  void defaultConstructorOwnsExecutorAndShutsItDown() throws Exception {
    ChatLogViewerService service = mock(ChatLogViewerService.class);
    when(service.enabled()).thenReturn(false);
    LogViewerPanel panel = onEdtCall(() -> new LogViewerPanel(service, sid -> List.of()));
    ExecutorService exec = readField(panel, "exec", ExecutorService.class);
    assertFalse(exec.isShutdown());

    onEdt(panel::close);
    flushEdt();

    assertTrue(exec.isShutdown());
  }

  @Test
  void rejectsShutdownExecutor() {
    ChatLogViewerService service = mock(ChatLogViewerService.class);
    when(service.enabled()).thenReturn(false);
    ExecutorService shutdownExec = mock(ExecutorService.class);
    when(shutdownExec.isShutdown()).thenReturn(true);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new LogViewerPanel(service, sid -> List.of(), shutdownExec));
    assertEquals("exec must be active", ex.getMessage());
  }

  private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
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

