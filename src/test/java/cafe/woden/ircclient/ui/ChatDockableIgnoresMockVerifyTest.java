package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeService;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.util.VirtualThreads;
import io.reactivex.rxjava3.core.Flowable;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import org.junit.jupiter.api.Test;

class ChatDockableIgnoresMockVerifyTest {

  @Test
  void openIgnoreListsButtonLaunchesDialogForActiveIgnoresServer() throws Exception {
    Fixture fixture = createFixture();
    try {
      TargetRef channelTarget = new TargetRef("libera", "#ircafe");
      when(fixture.transcripts.document(channelTarget)).thenReturn(new DefaultStyledDocument());
      when(fixture.transcripts.readMarkerJumpOffset(channelTarget)).thenReturn(-1);

      onEdt(() -> fixture.chat.setActiveTarget(TargetRef.ignores("libera")));
      flushEdt();
      assertEquals("Ignores", onEdtCall(fixture.chat::getTabText));

      JButton open = onEdtCall(() -> findButtonByText(fixture.chat, "Open Ignore Lists..."));
      assertNotNull(open);
      onEdt(open::doClick);

      verify(fixture.ignoreListDialog).open(any(), eq("libera"));
      verifyNoMoreInteractions(fixture.ignoreListDialog);
    } finally {
      fixture.shutdown();
    }
  }

  private static Fixture createFixture() throws Exception {
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
                    VirtualThreads.newSingleThreadExecutor("test-chat-dockable-ignores-log-viewer"),
                    VirtualThreads.newSingleThreadExecutor(
                        "test-chat-dockable-ignores-interceptor"))));

    return new Fixture(holder.get(), transcripts, ignoreListDialog);
  }

  private static JButton findButtonByText(Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JButton button && text.equals(button.getText())) {
      return button;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findButtonByText(child, text);
      if (found != null) return found;
    }
    return null;
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

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private record Fixture(
      ChatDockable chat, ChatTranscriptStore transcripts, IgnoreListDialog ignoreListDialog) {
    void shutdown() throws Exception {
      onEdt(chat::shutdown);
      flushEdt();
    }
  }
}
