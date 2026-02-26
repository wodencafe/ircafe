package cafe.woden.ircclient.ui.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

class RuntimeEventsPanelTest {

  @Test
  void refreshRetainsSelectionWhenSelectedEventStillExists() throws Exception {
    RuntimeDiagnosticEvent a = event("INFO", "alpha");
    RuntimeDiagnosticEvent b = event("WARN", "beta");
    AtomicReference<List<RuntimeDiagnosticEvent>> rowsRef =
        new AtomicReference<>(List.of(a, b, event("ERROR", "gamma")));

    onEdt(
        () -> {
          RuntimeEventsPanel panel =
              new RuntimeEventsPanel(
                  "Spring Events", "events", rowsRef::get, null, "spring-events");
          stopRefreshTimer(panel);
          JTable table = field(panel, "table", JTable.class);
          table.setRowSelectionInterval(1, 1);

          rowsRef.set(new ArrayList<>(rowsRef.get()));
          panel.refreshNow();

          assertEquals(1, table.getSelectedRow());
          stopRefreshTimer(panel);
        });
  }

  @Test
  void clearButtonInvokesCallbackAndRefreshesRows() throws Exception {
    AtomicBoolean cleared = new AtomicBoolean(false);
    AtomicReference<List<RuntimeDiagnosticEvent>> rowsRef =
        new AtomicReference<>(List.of(event("INFO", "one"), event("INFO", "two")));

    onEdt(
        () -> {
          RuntimeEventsPanel panel =
              new RuntimeEventsPanel(
                  "AssertJ Swing",
                  "events",
                  rowsRef::get,
                  () -> {
                    cleared.set(true);
                    rowsRef.set(List.of());
                  },
                  "assertj-swing");
          stopRefreshTimer(panel);

          JButton clear = field(panel, "clearButton", JButton.class);
          JTable table = field(panel, "table", JTable.class);
          assertEquals(2, table.getRowCount());
          clear.doClick();

          assertTrue(cleared.get());
          assertEquals(0, table.getRowCount());
          stopRefreshTimer(panel);
        });
  }

  @Test
  void reactiveRefreshTriggerUpdatesRowsWithoutPollingTimer() throws Exception {
    PublishProcessor<Object> trigger = PublishProcessor.create();
    AtomicReference<List<RuntimeDiagnosticEvent>> rowsRef =
        new AtomicReference<>(List.of(event("INFO", "one")));

    onEdt(
        () -> {
          RuntimeEventsPanel panel =
              new RuntimeEventsPanel(
                  "Spring Events",
                  "events",
                  rowsRef::get,
                  null,
                  "spring-events",
                  trigger.onBackpressureLatest());
          Timer timer = field(panel, "refreshTimer", Timer.class);
          assertFalse(timer.isRunning());

          JTable table = field(panel, "table", JTable.class);
          assertEquals(1, table.getRowCount());

          rowsRef.set(List.of(event("INFO", "one"), event("WARN", "two")));
          trigger.onNext(new Object());

          assertEquals(2, table.getRowCount());
        });
  }

  private static RuntimeDiagnosticEvent event(String level, String summary) {
    return new RuntimeDiagnosticEvent(
        Instant.parse("2026-02-26T12:00:00Z"), level, "Event", summary, "");
  }

  private static void stopRefreshTimer(RuntimeEventsPanel panel) throws Exception {
    if (panel == null) return;
    Timer timer = field(panel, "refreshTimer", Timer.class);
    timer.stop();
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static void onEdt(ThrowingRunnable r) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
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
}
