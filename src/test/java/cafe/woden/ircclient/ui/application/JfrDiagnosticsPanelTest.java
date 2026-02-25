package cafe.woden.ircclient.ui.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.JfrRuntimeEventsService;
import cafe.woden.ircclient.app.RuntimeDiagnosticEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

class JfrDiagnosticsPanelTest {

  @Test
  void refreshButtonRequestsImmediateRuntimeRefresh() throws Exception {
    JfrRuntimeEventsService service = mock(JfrRuntimeEventsService.class);
    when(service.statusSnapshot()).thenReturn(snapshot());
    when(service.recentEvents(800)).thenReturn(List.of(event("jdk.CPULoad", "cpu sample")));

    Holder holder = new Holder();
    onEdt(
        () -> {
          holder.panel = new JfrDiagnosticsPanel(service);
          stopRefreshTimer(holder.panel);
          JButton refresh = field(holder.panel, "refreshButton", JButton.class);
          refresh.doClick();
        });

    verify(service, times(1)).requestImmediateRefresh();
    onEdt(() -> stopRefreshTimer(holder.panel));
  }

  @Test
  void selectionIsRetainedAcrossRefreshes() throws Exception {
    JfrRuntimeEventsService service = mock(JfrRuntimeEventsService.class);
    when(service.statusSnapshot()).thenReturn(snapshot());
    when(service.recentEvents(800))
        .thenReturn(List.of(event("jdk.CPULoad", "cpu"), event("jdk.RecordingStream", "stream")));

    onEdt(
        () -> {
          JfrDiagnosticsPanel panel = new JfrDiagnosticsPanel(service);
          stopRefreshTimer(panel);
          JTable table = field(panel, "table", JTable.class);
          table.setRowSelectionInterval(1, 1);
          panel.refreshNow();
          assertEquals(1, table.getSelectedRow());
          stopRefreshTimer(panel);
        });
  }

  @Test
  void garbageCollectionRowsAreFilteredFromTableView() throws Exception {
    JfrRuntimeEventsService service = mock(JfrRuntimeEventsService.class);
    when(service.statusSnapshot()).thenReturn(snapshot());
    when(service.recentEvents(800))
        .thenReturn(
            List.of(
                event("jdk.CPULoad", "cpu"),
                event("jdk.GarbageCollection", "gc"),
                event("jdk.RecordingStream", "stream")));

    onEdt(
        () -> {
          JfrDiagnosticsPanel panel = new JfrDiagnosticsPanel(service);
          stopRefreshTimer(panel);
          panel.refreshNow();

          JTable table = field(panel, "table", JTable.class);
          assertEquals(2, table.getRowCount());
          for (int row = 0; row < table.getRowCount(); row++) {
            String type = String.valueOf(table.getValueAt(row, 2));
            assertNotEquals("jdk.GarbageCollection", type);
          }
          stopRefreshTimer(panel);
        });
  }

  private static JfrRuntimeEventsService.StatusSnapshot snapshot() {
    Instant now = Instant.parse("2026-02-25T12:00:00Z");
    return new JfrRuntimeEventsService.StatusSnapshot(
        true,
        false,
        true,
        now,
        0.20d,
        0.08d,
        0.28d,
        now,
        128L * 1024L * 1024L,
        256L * 1024L * 1024L,
        512L * 1024L * 1024L,
        25,
        2,
        1.0d,
        false,
        now);
  }

  private static RuntimeDiagnosticEvent event(String type, String summary) {
    return new RuntimeDiagnosticEvent(
        Instant.parse("2026-02-25T12:00:00Z"), "INFO", type, summary, "");
  }

  private static void stopRefreshTimer(JfrDiagnosticsPanel panel) throws Exception {
    if (panel == null) return;
    Timer timer = field(panel, "refreshTimer", Timer.class);
    timer.stop();
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    if (target == null || name == null || type == null) return null;
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static void onEdt(ThrowingRunnable r)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        r.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            r.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class Holder {
    private JfrDiagnosticsPanel panel;
  }
}
