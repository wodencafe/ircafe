package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.DccTransferStore;
import cafe.woden.ircclient.app.JfrRuntimeEventsService;
import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.app.SpringRuntimeEventsService;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.monitor.MonitorPanel;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeService;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import io.reactivex.rxjava3.core.Flowable;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatDockableMonitorFunctionalTest {

  @TempDir Path tempDir;

  @Test
  void monitorPanelReflectsRosterUpdatesAndOnlineState() throws Exception {
    Fixture fixture = createFixture();
    try {
      fixture.monitorListService.addNicks("libera", List.of("alice", "bob"));
      onEdt(() -> fixture.chat.setActiveTarget(TargetRef.monitorGroup("libera")));
      flushEdt();

      onEdt(
          () -> {
            assertEquals("Monitor", fixture.chat.getTabText());
            assertEquals("Monitor - libera", fixture.monitorTitle.getText());
            assertEquals(2, fixture.monitorTable.getRowCount());
            assertEquals("Unknown", statusForNick(fixture.monitorTable, "alice"));
            assertEquals("Unknown", statusForNick(fixture.monitorTable, "bob"));
          });

      Thread updater =
          new Thread(
              () -> fixture.monitorListService.addNicks("libera", List.of("carol")),
              "monitor-list-updater");
      updater.start();
      updater.join();
      flushEdt();

      onEdt(
          () -> {
            assertEquals(3, fixture.monitorTable.getRowCount());
            assertEquals("Unknown", statusForNick(fixture.monitorTable, "carol"));
          });

      onEdt(() -> fixture.chat.setPrivateMessageOnlineState("libera", "alice", true));
      flushEdt();

      onEdt(
          () -> {
            assertEquals("Online", statusForNick(fixture.monitorTable, "alice"));
            assertEquals("Unknown", statusForNick(fixture.monitorTable, "bob"));
            assertEquals("Unknown", statusForNick(fixture.monitorTable, "carol"));
          });
    } finally {
      onEdt(fixture.chat::shutdown);
      flushEdt();
    }
  }

  private Fixture createFixture() throws Exception {
    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(),
            new IrcProperties(null, List.of(server("libera"))));
    MonitorListService monitorListService = new MonitorListService(runtimeConfig);

    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    when(serverTree.managedChannelsChangedByServer()).thenReturn(Flowable.never());
    when(serverTree.openChannelsForServer(anyString())).thenReturn(List.of());
    when(serverTree.managedChannelsForServer(anyString())).thenReturn(List.of());
    NotificationStore notificationStore = new NotificationStore();
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    IrcClientService irc = mock(IrcClientService.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    UserListStore userListStore = mock(UserListStore.class);
    UserListDockable usersDock = mock(UserListDockable.class);
    NickContextMenuFactory nickContextMenuFactory = new NickContextMenuFactory();
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    ChatLogViewerService chatLogViewerService = mock(ChatLogViewerService.class);
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.changes()).thenReturn(Flowable.never());
    DccTransferStore dccTransferStore = new DccTransferStore();
    TerminalDockable terminalDockable = new TerminalDockable(mock(ConsoleTeeService.class));
    JfrRuntimeEventsService jfrRuntimeEventsService = new JfrRuntimeEventsService(runtimeConfig);
    SpringRuntimeEventsService springRuntimeEventsService = new SpringRuntimeEventsService();
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    CommandHistoryStore commandHistoryStore = mock(CommandHistoryStore.class);

    Holder holder = new Holder();
    onEdt(
        () ->
            holder.chat =
                new ChatDockable(
                    transcripts,
                    serverTree,
                    notificationStore,
                    activationBus,
                    outboundBus,
                    irc,
                    activeInputRouter,
                    ignoreListService,
                    ignoreStatusService,
                    monitorListService,
                    userListStore,
                    usersDock,
                    nickContextMenuFactory,
                    proxyResolver,
                    chatHistoryService,
                    chatLogViewerService,
                    interceptorStore,
                    dccTransferStore,
                    terminalDockable,
                    jfrRuntimeEventsService,
                    springRuntimeEventsService,
                    settingsBus,
                    commandHistoryStore));

    ChatDockable chat = holder.chat;
    MonitorPanel monitorPanel = findFirst(chat, MonitorPanel.class);
    assertNotNull(monitorPanel);
    JLabel title = findByName(monitorPanel, JLabel.class, "monitor.title");
    JTable table = findByName(monitorPanel, JTable.class, "monitor.table");
    assertNotNull(title);
    assertNotNull(table);
    return new Fixture(chat, monitorListService, title, table);
  }

  private static String statusForNick(JTable table, String nick) {
    String needle = (nick == null) ? "" : nick.trim();
    if (needle.isEmpty()) return "";
    int rows = table.getRowCount();
    for (int row = 0; row < rows; row++) {
      Object nickCell = table.getValueAt(row, 1);
      if (needle.equalsIgnoreCase(String.valueOf(nickCell))) {
        return String.valueOf(table.getValueAt(row, 0));
      }
    }
    return "";
  }

  private static <T extends Component> T findFirst(Component root, Class<T> type) {
    if (root == null || type == null) return null;
    if (type.isInstance(root)) return type.cast(root);
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findFirst(child, type);
      if (found != null) return found;
    }
    return null;
  }

  private static <T extends Component> T findByName(Component root, Class<T> type, String name) {
    if (root == null || type == null || name == null) return null;
    if (type.isInstance(root) && name.equals(root.getName())) {
      return type.cast(root);
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findByName(child, type, name);
      if (found != null) return found;
    }
    return null;
  }

  private static IrcProperties.Server server(String id) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        List.of(),
        List.of(),
        null);
  }

  private static void onEdt(ThrowingRunnable runnable)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
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

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class Holder {
    private ChatDockable chat;
  }

  private record Fixture(
      ChatDockable chat,
      MonitorListService monitorListService,
      JLabel monitorTitle,
      JTable monitorTable) {}
}
