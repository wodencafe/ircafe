package cafe.woden.ircclient.ui.channellist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Cursor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChannelListPanelManagedOrderTest {

  @Test
  void buildAlisCommandUsesRawPrivmsgAndLiberaTopicSyntax() {
    assertEquals(
        "/quote PRIVMSG ALIS :LIST * -topic test", ChannelListPanel.buildAlisCommand("test", true));
    assertEquals(
        "/quote PRIVMSG ALIS :LIST * -topic *", ChannelListPanel.buildAlisCommand("", true));
    assertEquals(
        "/quote PRIVMSG ALIS :LIST test", ChannelListPanel.buildAlisCommand("test", false));
    assertEquals("/quote PRIVMSG ALIS :LIST *", ChannelListPanel.buildAlisCommand("", false));
  }

  @Test
  void buildAlisCommandSupportsMinMaxAndDisplayFlags() {
    ChannelListPanel.AlisSearchOptions options =
        new ChannelListPanel.AlisSearchOptions(
            true, 25, 400, 10, true, true, ChannelListPanel.AlisRegistrationFilter.REGISTERED_ONLY);
    assertEquals(
        "/quote PRIVMSG ALIS :LIST * -topic java -min 25 -max 400 -skip 10 -show mt -show r",
        ChannelListPanel.buildAlisCommand("java", options));
  }

  @Test
  void managedSortModeIsScopedPerServer() throws Exception {
    onEdt(
        () -> {
          try {
            ChannelListPanel panel = new ChannelListPanel();
            List<ChannelListPanel.ManagedChannelRow> rows =
                List.of(
                    new ChannelListPanel.ManagedChannelRow("#beta", false, true),
                    new ChannelListPanel.ManagedChannelRow("#alpha", true, false));

            panel.setManagedChannels("libera", rows, ChannelListPanel.ManagedSortMode.CUSTOM);
            panel.setManagedChannels("oftc", rows, ChannelListPanel.ManagedSortMode.ALPHABETICAL);

            JComboBox<?> sortModeCombo = field(panel, "sortModeCombo", JComboBox.class);
            panel.setServerId("libera");
            assertEquals(ChannelListPanel.ManagedSortMode.CUSTOM, sortModeCombo.getSelectedItem());

            panel.setServerId("oftc");
            assertEquals(
                ChannelListPanel.ManagedSortMode.ALPHABETICAL, sortModeCombo.getSelectedItem());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void customOrderSupportsMoveButtonsAndDragDropInManagedTable() throws Exception {
    onEdt(
        () -> {
          try {
            ChannelListPanel panel = new ChannelListPanel();
            panel.setServerId("libera");
            panel.setManagedChannels(
                "libera",
                List.of(
                    new ChannelListPanel.ManagedChannelRow("#alpha", false, true),
                    new ChannelListPanel.ManagedChannelRow("#beta", false, true)),
                ChannelListPanel.ManagedSortMode.CUSTOM);

            AtomicReference<List<String>> order = new AtomicReference<>();
            panel.setOnManagedCustomOrderChanged(order::set);

            JTable managedTable = field(panel, "managedTable", JTable.class);
            JButton moveDownButton = field(panel, "moveDownButton", JButton.class);

            managedTable.getSelectionModel().setSelectionInterval(0, 0);
            assertTrue(moveDownButton.isEnabled());
            moveDownButton.doClick();

            assertEquals(List.of("#beta", "#alpha"), order.get());
            assertNotNull(managedTable.getToolTipText());
            assertTrue(managedTable.getToolTipText().toLowerCase(Locale.ROOT).contains("drag"));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void appendEntriesPopulatesServerListTable() throws Exception {
    onEdt(
        () -> {
          try {
            ChannelListPanel panel = new ChannelListPanel();
            panel.setServerId("libera");
            panel.beginList("libera", "Loading channel list...");
            panel.appendEntries(
                "libera",
                List.of(
                    new ChannelListPanel.ListEntryRow("#alpha", 12, "Alpha topic"),
                    new ChannelListPanel.ListEntryRow("#beta", 4, "Beta topic")));

            JTable listTable = field(panel, "listTable", JTable.class);
            assertEquals(2, listTable.getRowCount());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void managedModesFallbackUsesUnknownLabel() throws Exception {
    onEdt(
        () -> {
          try {
            ChannelListPanel panel = new ChannelListPanel();
            panel.setServerId("libera");
            panel.setManagedChannels(
                "libera",
                List.of(new ChannelListPanel.ManagedChannelRow("#alpha", true, true)),
                ChannelListPanel.ManagedSortMode.CUSTOM);

            JTable managedTable = field(panel, "managedTable", JTable.class);
            assertEquals("(Unknown)", managedTable.getValueAt(0, 4));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void listLoadingDisablesActionsAndUsesAlisButtonActivityIconUntilCompletion() throws Exception {
    ChannelListPanel panel = new ChannelListPanel();
    onEdt(() -> panel.setServerId("libera"));

    JButton runListButton = field(panel, "runListButton", JButton.class);
    JButton runAlisButton = field(panel, "runAlisButton", JButton.class);
    JTable listTable = field(panel, "listTable", JTable.class);
    Icon defaultAlisIcon = onEdtCall(runAlisButton::getIcon);

    onEdt(() -> panel.beginList("libera", "Loading ALIS search results..."));
    onEdt(
        () -> {
          assertFalse(runListButton.isEnabled());
          assertFalse(runAlisButton.isEnabled());
          assertNotEquals(defaultAlisIcon, runAlisButton.getIcon());
          assertEquals(Cursor.WAIT_CURSOR, listTable.getCursor().getType());
        });

    onEdt(() -> panel.endList("libera", "End of output."));
    onEdt(
        () -> {
          assertTrue(runListButton.isEnabled());
          assertTrue(runAlisButton.isEnabled());
          assertNotEquals(defaultAlisIcon, runAlisButton.getIcon());
          assertEquals(Cursor.DEFAULT_CURSOR, listTable.getCursor().getType());
        });

    waitFor(
        () -> onEdtCall(() -> runAlisButton.getIcon() == defaultAlisIcon), Duration.ofSeconds(3));
    onEdt(() -> assertSame(defaultAlisIcon, runAlisButton.getIcon()));
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    final java.util.concurrent.atomic.AtomicReference<T> out =
        new java.util.concurrent.atomic.AtomicReference<>();
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

  private static void waitFor(ThrowingBooleanSupplier condition, Duration timeout)
      throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
