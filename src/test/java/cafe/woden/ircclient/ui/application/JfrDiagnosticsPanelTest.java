package cafe.woden.ircclient.ui.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
          JButton refresh = field(holder.panel, "refreshButton", JButton.class);
          refresh.doClick();
        });

    verify(service, times(1)).requestImmediateRefresh();
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
          JTable table = field(panel, "table", JTable.class);
          table.setRowSelectionInterval(1, 1);
          panel.refreshNow();
          assertEquals(1, table.getSelectedRow());
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
          panel.refreshNow();

          JTable table = field(panel, "table", JTable.class);
          assertEquals(2, table.getRowCount());
          for (int row = 0; row < table.getRowCount(); row++) {
            String type = String.valueOf(table.getValueAt(row, 2));
            assertNotEquals("jdk.GarbageCollection", type);
          }
        });
  }

  @Test
  void panelRefreshesRowsFromServiceStateListener() throws Exception {
    JfrRuntimeEventsService service = mock(JfrRuntimeEventsService.class);
    when(service.statusSnapshot()).thenReturn(snapshot());

    RuntimeDiagnosticEvent first = event("jdk.CPULoad", "cpu");
    RuntimeDiagnosticEvent second = event("jdk.RecordingStream", "stream");
    List<RuntimeDiagnosticEvent> rows = new ArrayList<>(List.of(first));
    when(service.recentEvents(800)).thenAnswer(__ -> List.copyOf(rows));

    Holder holder = new Holder();
    onEdt(() -> holder.panel = new JfrDiagnosticsPanel(service));

    ArgumentCaptor<PropertyChangeListener> listenerCaptor =
        ArgumentCaptor.forClass(PropertyChangeListener.class);
    verify(service, times(1)).addStateListener(listenerCaptor.capture());
    PropertyChangeListener listener = listenerCaptor.getValue();

    onEdt(
        () -> {
          JTable table = field(holder.panel, "table", JTable.class);
          assertEquals(1, table.getRowCount());

          rows.add(second);
          listener.propertyChange(new PropertyChangeEvent(this, JfrRuntimeEventsService.PROP_STATE, null, 1L));
          assertEquals(2, table.getRowCount());
        });

    onEdt(() -> holder.panel.removeNotify());
    verify(service, times(1)).removeStateListener(listener);
    verify(service, never()).requestImmediateRefresh();
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
