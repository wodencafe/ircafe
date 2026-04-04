package cafe.woden.ircclient.ui.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.notifications.NotificationStore.HighlightEvent;
import cafe.woden.ircclient.notifications.NotificationStore.IrcEventRuleEvent;
import cafe.woden.ircclient.notifications.NotificationStore.RuleMatchEvent;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotificationsPanelFunctionalTest {

  @TempDir Path tempDir;

  @Test
  void contextMenuActionsAndCsvExportWorkForNotificationRows() throws Exception {
    String serverId = "libera";
    HighlightEvent highlight =
        new HighlightEvent(
            serverId,
            "#ircafe",
            "alice",
            "alice: ping",
            Instant.parse("2026-03-01T10:15:00Z"),
            "msg-highlight");
    RuleMatchEvent ruleMatch =
        new RuleMatchEvent(
            serverId,
            "#ircafe",
            "bob",
            "Rule A",
            "deploy now",
            Instant.parse("2026-03-01T10:16:00Z"),
            "msg-rule");
    IrcEventRuleEvent ircEvent =
        new IrcEventRuleEvent(
            serverId,
            "status",
            "server",
            "Topic changed",
            "new topic",
            Instant.parse("2026-03-01T10:17:00Z"),
            "");

    NotificationStore store = mock(NotificationStore.class);
    when(store.changes()).thenReturn(Flowable.never());
    when(store.listAll(serverId)).thenReturn(List.of(highlight));
    when(store.listAllRuleMatches(serverId)).thenReturn(List.of(ruleMatch));
    when(store.listAllIrcEventRules(serverId)).thenReturn(List.of(ircEvent));

    NotificationsPanel panel = onEdtCall(() -> new NotificationsPanel(store, serverId, null));
    JTable table = readField(panel, "table", JTable.class);
    JMenuItem jumpToMessageMenuItem = readField(panel, "jumpToMessageMenuItem", JMenuItem.class);
    JMenuItem clearSelectedMenuItem = readField(panel, "clearSelectedMenuItem", JMenuItem.class);
    JMenuItem clearAllMenuItem = readField(panel, "clearAllMenuItem", JMenuItem.class);
    AtomicReference<TargetRef> jumpedTarget = new AtomicReference<>();
    AtomicReference<String> jumpedMessageId = new AtomicReference<>();

    try {
      onEdt(
          () ->
              panel.setOnJumpToMessage(
                  (target, messageId) -> {
                    jumpedTarget.set(target);
                    jumpedMessageId.set(messageId);
                  }));

      waitFor(() -> onEdtBoolean(() -> table.getRowCount() == 3), Duration.ofSeconds(2));
      onEdt(
          () -> {
            assertEquals("Topic changed", String.valueOf(table.getValueAt(0, 3)));
            assertEquals("Rule A", String.valueOf(table.getValueAt(1, 3)));
            assertEquals("(mention)", String.valueOf(table.getValueAt(2, 3)));
          });

      onEdt(() -> table.setRowSelectionInterval(0, 0));
      waitFor(() -> onEdtBoolean(() -> !jumpToMessageMenuItem.isEnabled()), Duration.ofSeconds(2));

      onEdt(() -> table.setRowSelectionInterval(1, 1));
      waitFor(() -> onEdtBoolean(jumpToMessageMenuItem::isEnabled), Duration.ofSeconds(2));
      onEdt(jumpToMessageMenuItem::doClick);
      assertEquals(new TargetRef(serverId, "#ircafe"), jumpedTarget.get());
      assertEquals("msg-rule", jumpedMessageId.get());

      onEdt(() -> table.setRowSelectionInterval(1, 2));
      waitFor(() -> onEdtBoolean(clearSelectedMenuItem::isEnabled), Duration.ofSeconds(2));
      onEdt(clearSelectedMenuItem::doClick);
      verify(store)
          .clearSelected(
              eq(serverId),
              argThat(
                  events ->
                      events != null
                          && events.size() == 2
                          && events.contains(ruleMatch)
                          && events.contains(highlight)));

      assertTrue(onEdtBoolean(clearAllMenuItem::isEnabled));
      onEdt(clearAllMenuItem::doClick);
      verify(store).clearServer(serverId);

      Path selectedOut = tempDir.resolve("notifications-selected.csv");
      onEdt(() -> writeCsv(panel, selectedOut, List.of(1)));
      List<String> selectedLines = Files.readAllLines(selectedOut);
      assertEquals(2, selectedLines.size());
      assertTrue(selectedLines.getFirst().contains("Time"));
      assertTrue(selectedLines.getLast().contains("Rule A"));
      assertTrue(selectedLines.getLast().contains("deploy now"));

      Path allOut = tempDir.resolve("notifications-all.csv");
      onEdt(() -> writeCsv(panel, allOut, List.of(0, 1, 2)));
      List<String> allLines = Files.readAllLines(allOut);
      assertEquals(4, allLines.size());
      assertTrue(allLines.stream().anyMatch(line -> line.contains("Topic changed")));
      assertTrue(allLines.stream().anyMatch(line -> line.contains("alice: ping")));
    } finally {
      onEdt(panel::close);
      flushEdt();
    }
  }

  private static void writeCsv(NotificationsPanel panel, Path path, List<Integer> viewRows) {
    try {
      Method method =
          NotificationsPanel.class.getDeclaredMethod("writeCsv", Path.class, List.class);
      method.setAccessible(true);
      method.invoke(panel, path, viewRows);
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

  private static <T> T readField(Object target, String name, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
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
