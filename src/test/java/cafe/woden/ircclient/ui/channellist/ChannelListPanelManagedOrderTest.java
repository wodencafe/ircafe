package cafe.woden.ircclient.ui.channellist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.state.ServerIsupportState;
import cafe.woden.ircclient.ui.backend.BackendUiContext;
import cafe.woden.ircclient.ui.backend.BackendUiProfile;
import java.awt.Cursor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
  void buildMatrixListCommandSupportsSearchSinceAndLimit() {
    ChannelListPanel.MatrixListOptions options =
        new ChannelListPanel.MatrixListOptions("rust lang", "s123", 75);

    assertEquals(
        "/list rust lang since s123 limit 75", ChannelListPanel.buildMatrixListCommand(options));
  }

  @Test
  void matrixNextPageButtonUsesNextBatchFromLastSummary() throws Exception {
    ChannelListPanel panel = new ChannelListPanel();
    AtomicReference<String> command = new AtomicReference<>("");

    onEdt(
        () -> {
          panel.setBackendUiProfile(
              new BackendUiProfile(
                  "",
                  BackendUiContext.fromMatrixServerPredicate(
                      sid -> "matrix".equalsIgnoreCase(sid))));
          panel.setServerId("matrix");
          panel.setOnRunAlisRequest(command::set);
          panel.beginList("matrix", "Loading channel list (rust limit 30)...");
          panel.endList("matrix", "Listed 30 Matrix room(s). next_batch=nxt-42");
        });

    JButton nextPageButton = field(panel, "runMatrixNextButton", JButton.class);
    onEdt(() -> assertTrue(nextPageButton.isVisible()));
    onEdt(() -> assertTrue(nextPageButton.isEnabled()));

    onEdt(nextPageButton::doClick);
    assertEquals("/list rust since nxt-42 limit 30", command.get());
    onEdt(() -> assertFalse(nextPageButton.isEnabled()));
  }

  @Test
  void matrixSpecificListControlsOnlyShowForMatrixServers() throws Exception {
    ChannelListPanel panel = new ChannelListPanel();

    JButton runAlisButton = field(panel, "runAlisButton", JButton.class);
    JButton runMatrixNextButton = field(panel, "runMatrixNextButton", JButton.class);

    onEdt(
        () -> {
          panel.setBackendUiProfile(
              new BackendUiProfile(
                  "",
                  BackendUiContext.fromMatrixServerPredicate(
                      sid -> "matrix".equalsIgnoreCase(sid))));
          panel.setServerId("libera");
          assertFalse(runMatrixNextButton.isVisible());
          assertTrue(runAlisButton.getToolTipText().toLowerCase(Locale.ROOT).contains("alis"));
        });

    onEdt(
        () -> {
          panel.setServerId("matrix");
          assertTrue(runMatrixNextButton.isVisible());
          assertTrue(runAlisButton.getToolTipText().toLowerCase(Locale.ROOT).contains("matrix"));
        });

    onEdt(
        () -> {
          panel.setServerId("libera");
          assertFalse(runMatrixNextButton.isVisible());
          assertTrue(runAlisButton.getToolTipText().toLowerCase(Locale.ROOT).contains("alis"));
        });
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
  void mostRecentActivityModeDisablesManualMoveButtons() throws Exception {
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
                ChannelListPanel.ManagedSortMode.MOST_RECENT_ACTIVITY);

            JTable managedTable = field(panel, "managedTable", JTable.class);
            JButton moveUpButton = field(panel, "moveUpButton", JButton.class);
            JButton moveDownButton = field(panel, "moveDownButton", JButton.class);
            managedTable.getSelectionModel().setSelectionInterval(1, 1);

            assertFalse(moveUpButton.isEnabled());
            assertFalse(moveDownButton.isEnabled());
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
  void clearListButtonClearsCurrentServerRows() throws Exception {
    onEdt(
        () -> {
          try {
            ChannelListPanel panel = new ChannelListPanel();
            panel.setServerId("libera");
            panel.beginList("libera", "Loaded channel list.");
            panel.appendEntries(
                "libera",
                List.of(
                    new ChannelListPanel.ListEntryRow("#alpha", 12, "Alpha topic"),
                    new ChannelListPanel.ListEntryRow("#beta", 4, "Beta topic")));
            panel.endList("libera", "Loaded channel list.");

            JButton clearListButton = field(panel, "clearListButton", JButton.class);
            JTable listTable = field(panel, "listTable", JTable.class);
            assertTrue(clearListButton.getToolTipText().toLowerCase(Locale.ROOT).contains("clear"));
            assertEquals(2, listTable.getRowCount());

            clearListButton.doClick();

            assertEquals(0, listTable.getRowCount());
            assertFalse(clearListButton.isEnabled());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void listContextMenuUsesSelectReconnectAndJoinLabelsByManagedState() throws Exception {
    onEdt(
        () -> {
          try {
            ChannelListPanel panel = new ChannelListPanel();
            panel.setServerId("libera");
            JMenuItem joinItem = field(panel, "listJoinSelectMenuItem", JMenuItem.class);

            panel.setManagedChannels(
                "libera",
                List.of(new ChannelListPanel.ManagedChannelRow("#alpha", false, true)),
                ChannelListPanel.ManagedSortMode.CUSTOM);
            invokeOneArg(panel, "prepareListContextMenuForChannel", String.class, "#alpha");
            assertEquals("Select Channel", joinItem.getText());

            panel.setManagedChannels(
                "libera",
                List.of(new ChannelListPanel.ManagedChannelRow("#beta", true, true)),
                ChannelListPanel.ManagedSortMode.CUSTOM);
            invokeOneArg(panel, "prepareListContextMenuForChannel", String.class, "#beta");
            assertEquals("Reconnect Channel", joinItem.getText());

            panel.setManagedChannels("libera", List.of(), ChannelListPanel.ManagedSortMode.CUSTOM);
            invokeOneArg(panel, "prepareListContextMenuForChannel", String.class, "#gamma");
            assertEquals("Join Channel", joinItem.getText());
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
  void managedModesPreferLatestChannelModeSnapshot() throws Exception {
    onEdt(
        () -> {
          try {
            ChannelListPanel panel = new ChannelListPanel();
            panel.setServerId("libera");
            panel.setManagedChannels(
                "libera",
                List.of(new ChannelListPanel.ManagedChannelRow("#alpha", true, true)),
                ChannelListPanel.ManagedSortMode.CUSTOM);
            panel.setChannelModeSnapshot(
                "libera", "#alpha", "+nt", "Channel modes: topic locked, no outside messages");

            JTable managedTable = field(panel, "managedTable", JTable.class);
            assertEquals("+nt", managedTable.getValueAt(0, 4));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void refreshOpenDetailsDialogDoesNotClearPendingDialogStateBeforeDialogBecomesDisplayable()
      throws Exception {
    ChannelListPanel panel = new ChannelListPanel();

    onEdt(
        () -> {
          try {
            JDialog dialog = Mockito.mock(JDialog.class);
            Mockito.when(dialog.isDisplayable()).thenReturn(false);
            Object state =
                newChannelDetailsDialogState(
                    dialog, "libera", "#ircafe", ChannelListPanel.ChannelDetailsSource.MANAGED);
            setPrivateField(panel, "channelDetailsDialog", state);

            invokeNoArg(panel, "refreshOpenDetailsDialog");

            assertSame(state, privateFieldValue(panel, "channelDetailsDialog"));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void friendlyModeSummaryUsesNegotiatedVocabularyForQuietLists() throws Exception {
    ChannelListPanel panel = new ChannelListPanel();
    ServerIsupportState isupportState = new ServerIsupportState();
    isupportState.applyIsupportToken("libera", "PREFIX", "(ov)@+");
    isupportState.applyIsupportToken("libera", "CHANMODES", "bq,,,");
    onEdt(() -> panel.setModeVocabularyProvider(isupportState::vocabularyForServer));

    assertEquals("+q quiet rule alice", invokeFriendlyModeSummary(panel, "libera", "+q alice"));
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

  @Test
  void repaintIfSizedSkipsZeroSizedButtons() throws Exception {
    TrackingButton button = new TrackingButton();

    onEdt(
        () -> {
          try {
            int initialRepaintCount = button.repaintCount;
            invokeStaticRepaintIfSized(button);
            assertEquals(initialRepaintCount, button.repaintCount);

            button.setSize(28, 28);
            int sizedRepaintCount = button.repaintCount;
            invokeStaticRepaintIfSized(button);
            assertEquals(sizedRepaintCount + 1, button.repaintCount);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static Object privateFieldValue(Object target, String name) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  private static void setPrivateField(Object target, String name, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
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

  private static String invokeFriendlyModeSummary(
      ChannelListPanel panel, String serverId, String rawModes) throws Exception {
    Method method =
        ChannelListPanel.class.getDeclaredMethod(
            "friendlyModeSummaryFromRaw", String.class, String.class);
    method.setAccessible(true);
    return (String) method.invoke(panel, serverId, rawModes);
  }

  private static void invokeNoArg(Object target, String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(target);
  }

  private static void invokeOneArg(
      Object target, String methodName, Class<?> parameterType, Object argument) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterType);
    method.setAccessible(true);
    method.invoke(target, argument);
  }

  private static void invokeStaticRepaintIfSized(JButton button) throws Exception {
    Method method = ChannelListPanel.class.getDeclaredMethod("repaintIfSized", javax.swing.JComponent.class);
    method.setAccessible(true);
    method.invoke(null, button);
  }

  private static Object newChannelDetailsDialogState(
      JDialog dialog, String serverId, String channel, ChannelListPanel.ChannelDetailsSource source)
      throws Exception {
    Class<?> type =
        Class.forName(
            "cafe.woden.ircclient.ui.channellist.ChannelListPanel$ChannelDetailsDialogState");
    var constructor =
        type.getDeclaredConstructor(
            JDialog.class,
            String.class,
            String.class,
            ChannelListPanel.ChannelDetailsSource.class,
            JTextField.class,
            JTextField.class,
            JTextField.class,
            JTextField.class,
            JTextArea.class,
            JButton.class,
            JTextField.class,
            JTextArea.class,
            JTextArea.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        dialog,
        serverId,
        channel,
        source,
        new JTextField(),
        new JTextField(),
        new JTextField(),
        new JTextField(),
        new JTextArea(),
        new JButton(),
        new JTextField(),
        new JTextArea(),
        new JTextArea());
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private static final class TrackingButton extends JButton {
    private int repaintCount;

    @Override
    public void repaint() {
      repaintCount++;
    }
  }
}
