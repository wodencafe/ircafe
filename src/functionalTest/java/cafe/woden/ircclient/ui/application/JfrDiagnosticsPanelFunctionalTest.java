package cafe.woden.ircclient.ui.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JfrDiagnosticsPanelFunctionalTest {

  @Test
  void timerRefreshKeepsSelectedRowStable() throws Exception {
    JfrRuntimeEventsService service = mock(JfrRuntimeEventsService.class);
    AtomicReference<PropertyChangeListener> stateListenerRef = new AtomicReference<>();
    doAnswer(
            invocation -> {
              stateListenerRef.set(invocation.getArgument(0, PropertyChangeListener.class));
              return null;
            })
        .when(service)
        .addStateListener(org.mockito.ArgumentMatchers.any(PropertyChangeListener.class));
    AtomicInteger snapshotCalls = new AtomicInteger();
    when(service.statusSnapshot())
        .thenAnswer(
            invocation -> {
              snapshotCalls.incrementAndGet();
              return snapshot(0.25d, 0.10d, 0.35d, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L);
            });
    when(service.recentEvents(800))
        .thenReturn(List.of(event("jdk.CPULoad", "cpu"), event("jdk.RecordingStream", "stream")));

    Holder holder = new Holder();
    onEdt(
        () -> {
          holder.panel = new JfrDiagnosticsPanel(service);
          holder.table = field(holder.panel, "table", JTable.class);
          holder.table.setRowSelectionInterval(1, 1);
          holder.initialSnapshotCalls = snapshotCalls.get();
        });

    PropertyChangeListener listener = stateListenerRef.get();
    assertTrue(listener != null, "panel should subscribe to JFR runtime state updates");
    listener.propertyChange(
        new PropertyChangeEvent(service, JfrRuntimeEventsService.PROP_STATE, null, null));

    waitFor(() -> snapshotCalls.get() > holder.initialSnapshotCalls, Duration.ofSeconds(3));
    onEdt(() -> assertEquals(1, holder.table.getSelectedRow()));
  }

  @Test
  void clickingRefreshRequestsSampleAndUpdatesStatusFields() throws Exception {
    JfrRuntimeEventsService service = mock(JfrRuntimeEventsService.class);
    AtomicReference<JfrRuntimeEventsService.StatusSnapshot> snapshotRef =
        new AtomicReference<>(
            snapshot(0.20d, 0.08d, 0.28d, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L));
    when(service.statusSnapshot()).thenAnswer(invocation -> snapshotRef.get());
    when(service.recentEvents(800)).thenReturn(List.of(event("jdk.CPULoad", "cpu")));
    doAnswer(
            invocation -> {
              snapshotRef.set(
                  snapshot(0.50d, 0.12d, 0.62d, 3_500_000_000L, 4_000_000_000L, 8_000_000_000L));
              return null;
            })
        .when(service)
        .requestImmediateRefresh();

    Holder holder = new Holder();
    onEdt(
        () -> {
          holder.panel = new JfrDiagnosticsPanel(service);
          holder.refreshButton = field(holder.panel, "refreshButton", JButton.class);
          holder.cpuMachine = field(holder.panel, "cpuMachineValue", JTextField.class);
          holder.heapUsed = field(holder.panel, "heapUsedValue", JTextField.class);
        });

    onEdt(() -> holder.refreshButton.doClick());
    onEdt(
        () -> {
          assertEquals("50.0%", holder.cpuMachine.getText());
          assertTrue(
              holder.heapUsed.getText().endsWith("GB"), "heap values should be rendered in GB");
        });
    verify(service, times(1)).requestImmediateRefresh();
  }

  private static JfrRuntimeEventsService.StatusSnapshot snapshot(
      double machineTotalRatio,
      double jvmUserRatio,
      double jvmSystemRatio,
      long usedBytes,
      long committedBytes,
      long maxBytes) {
    Instant now = Instant.parse("2026-02-25T12:00:00Z");
    return new JfrRuntimeEventsService.StatusSnapshot(
        true,
        false,
        true,
        now,
        jvmUserRatio,
        jvmSystemRatio,
        machineTotalRatio,
        now,
        usedBytes,
        committedBytes,
        maxBytes,
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

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25L);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
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

  private static final class Holder {
    private JfrDiagnosticsPanel panel;
    private JTable table;
    private int initialSnapshotCalls;
    private JButton refreshButton;
    private JTextField cpuMachine;
    private JTextField heapUsed;
  }
}
