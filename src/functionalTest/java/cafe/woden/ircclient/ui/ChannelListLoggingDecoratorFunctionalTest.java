package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.diagnostics.ApplicationDiagnosticsService;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.SpringRuntimeEventsService;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.logging.LogLineFactory;
import cafe.woden.ircclient.logging.LoggingUiPortDecorator;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeService;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.util.VirtualThreads;
import io.reactivex.rxjava3.core.Flowable;
import java.awt.Cursor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChannelListLoggingDecoratorFunctionalTest {

  @TempDir Path tempDir;

  @Test
  void loggingDecoratorChannelListCallsPopulateTableAndResetsBusyUiState() throws Exception {
    Fixture fixture = createFixture();
    try {
      onEdt(() -> fixture.chat.setActiveTarget(TargetRef.channelList("libera")));
      flushEdt();

      fixture.ui.beginChannelList("libera", "Loading ALIS search results...");
      waitFor(() -> onEdtCall(() -> !fixture.runAlisButton.isEnabled()), Duration.ofSeconds(2));
      waitFor(
          () -> onEdtCall(() -> fixture.listTable.getCursor().getType() == Cursor.WAIT_CURSOR),
          Duration.ofSeconds(2));
      waitFor(
          () -> onEdtCall(() -> fixture.runAlisButton.getIcon() != fixture.defaultAlisIcon),
          Duration.ofSeconds(2));

      fixture.ui.appendChannelListEntry("libera", "#ircafe", 42, "IRCafe test channel");
      waitFor(() -> onEdtCall(() -> fixture.listTable.getRowCount() == 1), Duration.ofSeconds(2));

      fixture.ui.endChannelList("libera", "End of output.");
      waitFor(() -> onEdtCall(() -> fixture.runAlisButton.isEnabled()), Duration.ofSeconds(2));
      waitFor(
          () -> onEdtCall(() -> fixture.listTable.getCursor().getType() == Cursor.DEFAULT_CURSOR),
          Duration.ofSeconds(2));
      waitFor(
          () -> onEdtCall(() -> fixture.runAlisButton.getIcon() == fixture.defaultAlisIcon),
          Duration.ofSeconds(3));

      onEdt(
          () -> {
            assertEquals(1, fixture.listTable.getRowCount());
            assertEquals("#ircafe", String.valueOf(fixture.listTable.getValueAt(0, 0)));
            assertEquals(42, Integer.parseInt(String.valueOf(fixture.listTable.getValueAt(0, 1))));
          });
    } finally {
      fixture.shutdown();
    }
  }

  private Fixture createFixture() throws Exception {
    IrcProperties props = new IrcProperties(null, List.of(server("libera")));
    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(tempDir.resolve("ircafe.yml").toString(), props);
    ServerRegistry serverRegistry = new ServerRegistry(props, runtimeConfig);
    ServerCatalog serverCatalog = new ServerCatalog(serverRegistry, new EphemeralServerRegistry());
    LogProperties logProps = new LogProperties(true, true, true, true, true, 0, null, null, null);

    NotificationStore notificationStore = new NotificationStore();
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.changes()).thenReturn(Flowable.never());

    ServerTreeDockable serverTree =
        onEdtCall(
            () ->
                new ServerTreeDockable(
                    serverCatalog,
                    runtimeConfig,
                    logProps,
                    null,
                    null,
                    new ConnectButton(),
                    new DisconnectButton(),
                    notificationStore,
                    interceptorStore,
                    null,
                    null));

    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    IrcClientService irc = mock(IrcClientService.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    IgnoreListDialog ignoreListDialog = mock(IgnoreListDialog.class);
    MonitorListService monitorListService = new MonitorListService(runtimeConfig);
    UserListStore userListStore = mock(UserListStore.class);
    when(userListStore.get(anyString(), anyString())).thenReturn(List.of());
    NickContextMenuFactory nickContextMenuFactory = new NickContextMenuFactory();
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    ChatLogViewerService chatLogViewerService = mock(ChatLogViewerService.class);
    DccTransferStore dccTransferStore = new DccTransferStore();
    TerminalDockable terminalDockable = new TerminalDockable(mock(ConsoleTeeService.class));
    ApplicationDiagnosticsService applicationDiagnosticsService =
        mock(ApplicationDiagnosticsService.class);
    JfrRuntimeEventsService jfrRuntimeEventsService = new JfrRuntimeEventsService(runtimeConfig);
    SpringRuntimeEventsService springRuntimeEventsService = new SpringRuntimeEventsService();
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    SpellcheckSettingsBus spellcheckSettingsBus = mock(SpellcheckSettingsBus.class);
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
                    ignoreListDialog,
                    monitorListService,
                    userListStore,
                    mock(UserListDockable.class),
                    nickContextMenuFactory,
                    proxyResolver,
                    chatHistoryService,
                    chatLogViewerService,
                    interceptorStore,
                    dccTransferStore,
                    terminalDockable,
                    applicationDiagnosticsService,
                    jfrRuntimeEventsService,
                    springRuntimeEventsService,
                    settingsBus,
                    spellcheckSettingsBus,
                    commandHistoryStore,
                    VirtualThreads.newSingleThreadExecutor("test-channel-list-log-viewer"),
                    VirtualThreads.newSingleThreadExecutor("test-channel-list-interceptor")));
    ChatDockable chat = holder.chat;

    SwingUiPort swingUiPort =
        new SwingUiPort(
            serverTree,
            chat,
            transcripts,
            mock(MentionPatternRegistry.class),
            notificationStore,
            mock(UserListDockable.class),
            mock(StatusBar.class),
            mock(ConnectButton.class),
            mock(DisconnectButton.class),
            activationBus,
            outboundBus,
            mock(ChatDockManager.class),
            activeInputRouter);
    UiPort loggingUiPort =
        new LoggingUiPortDecorator(swingUiPort, line -> {}, new LogLineFactory(), logProps);

    ChannelListPanel panel = field(chat, "channelListPanel", ChannelListPanel.class);
    JTable listTable = field(panel, "listTable", JTable.class);
    JButton runAlisButton = field(panel, "runAlisButton", JButton.class);
    Icon defaultAlisIcon = onEdtCall(runAlisButton::getIcon);
    return new Fixture(serverTree, chat, loggingUiPort, listTable, runAlisButton, defaultAlisIcon);
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

  private static void waitFor(ThrowingBooleanSupplier condition, Duration timeout)
      throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
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

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private static final class Holder {
    private ChatDockable chat;
  }

  private record Fixture(
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UiPort ui,
      JTable listTable,
      JButton runAlisButton,
      Icon defaultAlisIcon) {
    void shutdown() throws Exception {
      onEdt(
          () -> {
            chat.shutdown();
            serverTree.shutdown();
          });
      flushEdt();
    }
  }
}
