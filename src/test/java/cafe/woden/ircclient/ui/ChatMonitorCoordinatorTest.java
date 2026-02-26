package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.ui.monitor.MonitorPanel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChatMonitorCoordinatorTest {

  @Test
  void setPrivateMessageOnlineStateRefreshesRowsCaseInsensitivelyForActiveMonitor()
      throws Exception {
    MonitorPanel monitorPanel = new MonitorPanel();
    MonitorListService monitorListService = mock(MonitorListService.class);
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice", "bob"));

    ChatMonitorCoordinator coordinator =
        new ChatMonitorCoordinator(
            monitorPanel, monitorListService, () -> TargetRef.monitorGroup("libera"));

    onEdt(
        () -> {
          monitorPanel.setServerId("libera");
          coordinator.setPrivateMessageOnlineState("libera", "ALICE", true);
        });

    JTable table = findByName(monitorPanel, JTable.class, "monitor.table");
    assertNotNull(table);
    assertEquals("Online", statusForNick(table, "alice"));
    assertEquals("Unknown", statusForNick(table, "bob"));
  }

  @Test
  void bindRefreshesRowsWhenMonitorListChangesForActiveServer() throws Exception {
    MonitorPanel monitorPanel = new MonitorPanel();
    MonitorListService monitorListService = mock(MonitorListService.class);
    FlowableProcessor<MonitorListService.Change> changes =
        PublishProcessor.<MonitorListService.Change>create().toSerialized();
    when(monitorListService.changes()).thenReturn(changes.onBackpressureBuffer());
    when(monitorListService.listNicks("libera")).thenReturn(List.of("carol"));

    ChatMonitorCoordinator coordinator =
        new ChatMonitorCoordinator(
            monitorPanel, monitorListService, () -> TargetRef.monitorGroup("libera"));
    CompositeDisposable disposables = new CompositeDisposable();

    onEdt(() -> monitorPanel.setServerId("libera"));
    coordinator.bind(disposables);
    changes.onNext(new MonitorListService.Change("other", MonitorListService.ChangeKind.ADDED));
    flushEdt();

    JTable table = findByName(monitorPanel, JTable.class, "monitor.table");
    assertNotNull(table);
    assertEquals(0, table.getRowCount());

    changes.onNext(new MonitorListService.Change("libera", MonitorListService.ChangeKind.ADDED));
    flushEdt();
    assertEquals(1, table.getRowCount());
    assertEquals("Unknown", statusForNick(table, "carol"));
    disposables.dispose();
  }

  @Test
  void clearPrivateMessageOnlineStatesRestoresUnknownStatuses() throws Exception {
    MonitorPanel monitorPanel = new MonitorPanel();
    MonitorListService monitorListService = mock(MonitorListService.class);
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice"));

    ChatMonitorCoordinator coordinator =
        new ChatMonitorCoordinator(
            monitorPanel, monitorListService, () -> TargetRef.monitorGroup("libera"));

    onEdt(
        () -> {
          monitorPanel.setServerId("libera");
          coordinator.setPrivateMessageOnlineState("libera", "alice", true);
          coordinator.clearPrivateMessageOnlineStates("libera");
        });

    JTable table = findByName(monitorPanel, JTable.class, "monitor.table");
    assertNotNull(table);
    assertEquals("Unknown", statusForNick(table, "alice"));
  }

  @Test
  void setPrivateMessageOnlineStateDoesNotRefreshWhenActiveTargetIsNotMonitor() {
    MonitorPanel monitorPanel = new MonitorPanel();
    MonitorListService monitorListService = mock(MonitorListService.class);
    ChatMonitorCoordinator coordinator =
        new ChatMonitorCoordinator(
            monitorPanel, monitorListService, () -> new TargetRef("libera", "status"));

    coordinator.setPrivateMessageOnlineState("libera", "alice", true);

    verify(monitorListService, never()).listNicks("libera");
  }

  private static String statusForNick(JTable table, String nick) {
    if (table == null) return "";
    String needle = Objects.toString(nick, "").trim();
    for (int row = 0; row < table.getRowCount(); row++) {
      String rowNick = Objects.toString(table.getValueAt(row, 1), "").trim();
      if (!rowNick.equalsIgnoreCase(needle)) continue;
      return Objects.toString(table.getValueAt(row, 0), "").trim();
    }
    return "";
  }

  private static void onEdt(Runnable runnable)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
      return;
    }
    SwingUtilities.invokeAndWait(runnable);
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    onEdt(() -> {});
  }

  private static <T extends Component> T findByName(Container root, Class<T> type, String name) {
    if (root == null || type == null || name == null) return null;
    if (type.isInstance(root) && name.equals(root.getName())) {
      return type.cast(root);
    }
    for (Component child : root.getComponents()) {
      if (type.isInstance(child) && name.equals(child.getName())) {
        return type.cast(child);
      }
      if (child instanceof Container container) {
        T nested = findByName(container, type, name);
        if (nested != null) return nested;
      }
    }
    return null;
  }
}
