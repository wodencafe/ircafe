package cafe.woden.ircclient.ui.logviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.logging.viewer.ChatLogViewerQuery;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerResult;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerRow;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class LogViewerPanelFunctionalTest {

  @TempDir Path tempDir;

  @Test
  void searchAndResetDriveExpectedQueries() throws Exception {
    ChatLogViewerService service = mock(ChatLogViewerService.class);
    when(service.enabled()).thenReturn(true);
    when(service.listUniqueChannels(anyString(), anyInt())).thenReturn(List.of("#ircafe"));
    when(service.search(any()))
        .thenReturn(
            new ChatLogViewerResult(
                List.of(row(1L, "#ircafe", "alice", "initial")), 1, false, false));

    LogViewerPanel panel = onEdtCall(() -> new LogViewerPanel(service, sid -> List.of("#open")));
    JTable table = readField(panel, "table", JTable.class);
    JTextField nickField = readField(panel, "nickField", JTextField.class);
    JTextField messageField = readField(panel, "messageField", JTextField.class);
    JButton searchButton = readField(panel, "searchButton", JButton.class);
    JButton resetButton = readField(panel, "resetButton", JButton.class);

    try {
      onEdt(() -> panel.setServerId("libera"));
      waitFor(() -> onEdtBoolean(() -> table.getRowCount() == 1), Duration.ofSeconds(3));

      onEdt(
          () -> {
            nickField.setText("alice");
            messageField.setText("hello");
            searchButton.doClick();
          });
      verify(service, timeout(3_000).atLeast(2)).search(any(ChatLogViewerQuery.class));

      onEdt(resetButton::doClick);
      verify(service, timeout(3_000).atLeast(3)).search(any(ChatLogViewerQuery.class));

      ArgumentCaptor<ChatLogViewerQuery> captor = ArgumentCaptor.forClass(ChatLogViewerQuery.class);
      verify(service, atLeast(3)).search(captor.capture());

      List<ChatLogViewerQuery> queries = captor.getAllValues();
      assertTrue(
          queries.stream()
              .anyMatch(
                  q -> "alice".equals(q.nickPattern()) && "hello".equals(q.messagePattern())));

      ChatLogViewerQuery last = queries.getLast();
      assertEquals("", last.nickPattern());
      assertEquals("", last.messagePattern());
      onEdt(
          () -> {
            assertEquals("", nickField.getText());
            assertEquals("", messageField.getText());
          });
    } finally {
      onEdt(panel::close);
      flushEdt();
    }
  }

  @Test
  void columnVisibilityAndCsvExportFlowWork() throws Exception {
    ChatLogViewerService service = mock(ChatLogViewerService.class);
    when(service.enabled()).thenReturn(true);
    when(service.listUniqueChannels(anyString(), anyInt())).thenReturn(List.of("#ircafe"));
    when(service.search(any()))
        .thenReturn(
            new ChatLogViewerResult(
                List.of(row(42L, "#ircafe", "alice", "hello from logs")), 1, false, false));

    LogViewerPanel panel = onEdtCall(() -> new LogViewerPanel(service, sid -> List.of()));
    JTable table = readField(panel, "table", JTable.class);

    try {
      onEdt(() -> panel.setServerId("libera"));
      waitFor(() -> onEdtBoolean(() -> table.getRowCount() == 1), Duration.ofSeconds(3));

      int hostmaskColumn = readStaticInt(LogViewerPanel.class, "COL_HOSTMASK");
      assertFalse(onEdtCall(() -> hasModelColumn(table, hostmaskColumn)));

      onEdt(() -> showColumn(panel, hostmaskColumn));
      assertTrue(onEdtCall(() -> hasModelColumn(table, hostmaskColumn)));

      onEdt(() -> hideColumn(panel, hostmaskColumn));
      assertFalse(onEdtCall(() -> hasModelColumn(table, hostmaskColumn)));

      Object snapshot = onEdtCall(() -> captureExportSnapshot(panel));
      Path out = tempDir.resolve("log-viewer.csv");
      writeCsv(out, snapshot);

      List<String> lines = Files.readAllLines(out);
      assertTrue(lines.size() >= 2, "csv should contain header and data");
      assertTrue(lines.getFirst().contains("Time"));
      assertTrue(lines.stream().anyMatch(line -> line.contains("hello from logs")));
    } finally {
      onEdt(panel::close);
      flushEdt();
    }
  }

  private static ChatLogViewerRow row(long id, String channel, String nick, String message) {
    return new ChatLogViewerRow(
        id,
        "libera",
        channel,
        Instant.parse("2026-02-20T12:34:00Z").toEpochMilli(),
        LogDirection.IN,
        LogKind.CHAT,
        nick,
        nick + "!user@host",
        message,
        "msg-" + id,
        Map.of("time", "123"),
        "{}");
  }

  private static Object captureExportSnapshot(LogViewerPanel panel) {
    try {
      Method m = LogViewerPanel.class.getDeclaredMethod("captureExportSnapshot");
      m.setAccessible(true);
      return m.invoke(panel);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void writeCsv(Path out, Object snapshot) {
    try {
      Method m =
          LogViewerPanel.class.getDeclaredMethod("writeCsv", Path.class, snapshot.getClass());
      m.setAccessible(true);
      m.invoke(null, out, snapshot);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new RuntimeException(cause);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void hideColumn(LogViewerPanel panel, int modelIndex) {
    try {
      Method m = LogViewerPanel.class.getDeclaredMethod("hideColumn", int.class);
      m.setAccessible(true);
      m.invoke(panel, modelIndex);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void showColumn(LogViewerPanel panel, int modelIndex) {
    try {
      Method m = LogViewerPanel.class.getDeclaredMethod("showColumn", int.class);
      m.setAccessible(true);
      m.invoke(panel, modelIndex);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean hasModelColumn(JTable table, int modelIndex) {
    TableColumnModel columnModel = table.getColumnModel();
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      if (columnModel.getColumn(i).getModelIndex() == modelIndex) return true;
    }
    return false;
  }

  private static int readStaticInt(Class<?> type, String fieldName) throws Exception {
    Field field = type.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.getInt(null);
  }

  private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
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
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    AtomicReference<T> out = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  private static boolean onEdtBoolean(ThrowingBooleanSupplier supplier) {
    try {
      return onEdtCall(supplier::getAsBoolean);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }
}
