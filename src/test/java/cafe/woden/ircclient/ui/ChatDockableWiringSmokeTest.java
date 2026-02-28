package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeService;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.util.VirtualThreads;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import org.junit.jupiter.api.Test;

class ChatDockableWiringSmokeTest {

  @Test
  void setActiveTargetRoutesUiOnlyAndTranscriptViews() throws Exception {
    Fixture fixture = createFixture();
    try {
      TargetRef channelTarget = new TargetRef("libera", "#ircafe");
      when(fixture.transcripts.document(channelTarget)).thenReturn(new DefaultStyledDocument());
      when(fixture.transcripts.readMarkerJumpOffset(channelTarget)).thenReturn(-1);

      onEdt(() -> fixture.chat.setActiveTarget(TargetRef.channelList("libera")));
      flushEdt();

      assertEquals("Channel List", onEdtCall(fixture.chat::getTabText));
      verify(fixture.serverTree).managedChannelsForServer("libera");

      onEdt(() -> fixture.chat.setActiveTarget(TargetRef.ignores("libera")));
      flushEdt();

      assertEquals("Ignores", onEdtCall(fixture.chat::getTabText));

      onEdt(() -> fixture.chat.setActiveTarget(channelTarget));
      flushEdt();

      assertEquals("#ircafe", onEdtCall(fixture.chat::getTabText));
      verify(fixture.transcripts).ensureTargetExists(channelTarget);
      verify(fixture.transcripts).document(channelTarget);
    } finally {
      fixture.shutdown();
    }
  }

  @Test
  void transcriptInteractionsPublishThroughWiredBuses() throws Exception {
    Fixture fixture = createFixture();
    try {
      TargetRef channelTarget = new TargetRef("libera", "#ircafe");
      when(fixture.transcripts.document(channelTarget)).thenReturn(new DefaultStyledDocument());
      when(fixture.transcripts.readMarkerJumpOffset(channelTarget)).thenReturn(-1);

      TestSubscriber<TargetRef> activations = fixture.activationBus.stream().test();
      TestSubscriber<String> outbound = fixture.outboundBus.stream().test();
      TestSubscriber<PrivateMessageRequest> privateMessages =
          fixture.chat.privateMessageRequests().test();

      AtomicReference<Boolean> channelHandled = new AtomicReference<>(false);
      AtomicReference<Boolean> nickHandled = new AtomicReference<>(false);
      onEdt(
          () -> {
            fixture.chat.setActiveTarget(channelTarget);
            fixture.chat.onTranscriptClicked();
            channelHandled.set(fixture.chat.onChannelClicked("#kotlin"));
            nickHandled.set(fixture.chat.onNickClicked("alice"));
          });
      flushEdt();

      assertTrue(Boolean.TRUE.equals(channelHandled.get()));
      assertTrue(Boolean.TRUE.equals(nickHandled.get()));
      activations.assertValues(channelTarget, channelTarget);
      outbound.assertValue("/join #kotlin");
      privateMessages.assertValue(new PrivateMessageRequest("libera", "alice"));
    } finally {
      fixture.shutdown();
    }
  }

  @Test
  void monitorTargetSelectionAndPresenceUpdatesRefreshMonitorRows() throws Exception {
    Fixture fixture = createFixture();
    try {
      when(fixture.monitorListService.listNicks("libera")).thenReturn(List.of("alice", "bob"));

      onEdt(() -> fixture.chat.setActiveTarget(TargetRef.monitorGroup("libera")));
      flushEdt();

      assertEquals("Monitor", onEdtCall(fixture.chat::getTabText));
      JTable monitorTable =
          onEdtCall(() -> findByName(fixture.chat, JTable.class, "monitor.table"));
      assertNotNull(monitorTable);
      assertEquals(2, onEdtCall(monitorTable::getRowCount));
      assertEquals("Unknown", onEdtCall(() -> statusForNick(monitorTable, "alice")));

      onEdt(() -> fixture.chat.setPrivateMessageOnlineState("libera", "alice", true));
      flushEdt();

      assertEquals("Online", onEdtCall(() -> statusForNick(monitorTable, "alice")));
      assertEquals("Unknown", onEdtCall(() -> statusForNick(monitorTable, "bob")));
    } finally {
      fixture.shutdown();
    }
  }

  @Test
  void onTargetClosedRemovesDraftCacheEntry() throws Exception {
    Fixture fixture = createFixture();
    try {
      TargetRef first = new TargetRef("libera", "#first");
      TargetRef second = new TargetRef("libera", "#second");
      when(fixture.transcripts.document(first)).thenReturn(new DefaultStyledDocument());
      when(fixture.transcripts.readMarkerJumpOffset(first)).thenReturn(-1);
      when(fixture.transcripts.document(second)).thenReturn(new DefaultStyledDocument());
      when(fixture.transcripts.readMarkerJumpOffset(second)).thenReturn(-1);

      onEdt(
          () -> {
            fixture.chat.setActiveTarget(first);
            fixture.chat.setActiveTarget(second);
          });
      flushEdt();

      Map<?, ?> before = onEdtCall(() -> draftMapSnapshot(fixture.chat));
      assertTrue(before.containsKey(first));

      onEdt(() -> fixture.chat.onTargetClosed(first));
      flushEdt();

      Map<?, ?> after = onEdtCall(() -> draftMapSnapshot(fixture.chat));
      assertTrue(!after.containsKey(first));
    } finally {
      fixture.shutdown();
    }
  }

  private static Fixture createFixture() throws Exception {
    return createFixture(
        VirtualThreads.newSingleThreadExecutor("test-chat-dockable-wiring-log-viewer"),
        VirtualThreads.newSingleThreadExecutor("test-chat-dockable-wiring-interceptor"));
  }

  @Test
  void shutdownDoesNotCloseSharedPanelExecutors() throws Exception {
    ExecutorService logViewerExecutor = mock(ExecutorService.class);
    ExecutorService interceptorRefreshExecutor = mock(ExecutorService.class);
    Fixture fixture = createFixture(logViewerExecutor, interceptorRefreshExecutor);
    try {
      fixture.shutdown(false);
      verify(logViewerExecutor, never()).shutdownNow();
      verify(interceptorRefreshExecutor, never()).shutdownNow();
    } finally {
      fixture.shutdown(false);
    }
  }

  private static Fixture createFixture(
      ExecutorService logViewerExecutor, ExecutorService interceptorRefreshExecutor)
      throws Exception {
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    when(serverTree.managedChannelsChangedByServer()).thenReturn(Flowable.never());
    when(serverTree.openChannelsForServer(anyString())).thenReturn(List.of());
    when(serverTree.managedChannelsForServer(anyString())).thenReturn(List.of());
    when(serverTree.channelSortModeForServer(anyString()))
        .thenReturn(ServerTreeDockable.ChannelSortMode.CUSTOM);

    NotificationStore notificationStore = new NotificationStore();
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    IrcClientService irc = mock(IrcClientService.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    IgnoreListDialog ignoreListDialog = mock(IgnoreListDialog.class);
    MonitorListService monitorListService = mock(MonitorListService.class);
    when(monitorListService.changes()).thenReturn(Flowable.never());
    when(monitorListService.listNicks(anyString())).thenReturn(List.of());
    UserListStore userListStore = mock(UserListStore.class);
    when(userListStore.get(anyString(), anyString())).thenReturn(List.of());
    UserListDockable usersDock = mock(UserListDockable.class);
    NickContextMenuFactory nickContextMenuFactory = new NickContextMenuFactory();
    ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
    ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    ChatLogViewerService chatLogViewerService = mock(ChatLogViewerService.class);
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.changes()).thenReturn(Flowable.never());
    DccTransferStore dccTransferStore = new DccTransferStore();
    TerminalDockable terminalDockable = new TerminalDockable(mock(ConsoleTeeService.class));
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    SpellcheckSettingsBus spellcheckSettingsBus = mock(SpellcheckSettingsBus.class);
    CommandHistoryStore commandHistoryStore = mock(CommandHistoryStore.class);

    AtomicReference<ChatDockable> holder = new AtomicReference<>();
    onEdt(
        () ->
            holder.set(
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
                    usersDock,
                    nickContextMenuFactory,
                    proxyResolver,
                    chatHistoryService,
                    chatLogViewerService,
                    interceptorStore,
                    dccTransferStore,
                    terminalDockable,
                    null,
                    null,
                    null,
                    settingsBus,
                    spellcheckSettingsBus,
                    commandHistoryStore,
                    logViewerExecutor,
                    interceptorRefreshExecutor)));

    return new Fixture(
        holder.get(),
        transcripts,
        serverTree,
        activationBus,
        outboundBus,
        monitorListService,
        logViewerExecutor,
        interceptorRefreshExecutor);
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

  private static void flushEdt() throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
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

  private static Map<?, ?> draftMapSnapshot(ChatDockable chat) throws Exception {
    Field field = ChatDockable.class.getDeclaredField("draftByTarget");
    field.setAccessible(true);
    Object value = field.get(chat);
    if (!(value instanceof Map<?, ?> map)) return Map.of();
    return Map.copyOf(map);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private record Fixture(
      ChatDockable chat,
      ChatTranscriptStore transcripts,
      ServerTreeDockable serverTree,
      TargetActivationBus activationBus,
      OutboundLineBus outboundBus,
      MonitorListService monitorListService,
      ExecutorService logViewerExecutor,
      ExecutorService interceptorRefreshExecutor) {
    void shutdown() throws Exception {
      shutdown(true);
    }

    void shutdown(boolean shutdownSharedExecutors) throws Exception {
      onEdt(chat::shutdown);
      flushEdt();
      if (!shutdownSharedExecutors) return;
      try {
        logViewerExecutor.shutdownNow();
      } catch (Exception ignored) {
      }
      try {
        interceptorRefreshExecutor.shutdownNow();
      } catch (Exception ignored) {
      }
    }
  }
}
