package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.UserhostQueryService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.Component;
import java.awt.Font;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DetachedChannelLifecycleFunctionalTest {

  @TempDir Path tempDir;

  @Test
  void detachFromContextMenuKeepsChannelVisibleAndDisablesInput() throws Exception {
    Fixture fixture = createFixture(List.of());
    Disposable detachSub = fixture.serverTree.detachChannelRequests().subscribe(fixture.targetCoordinator::detachChannel);
    try {
      TargetRef channel = new TargetRef("libera", "#functional-detach");

      fixture.markConnected();
      onEdt(() -> fixture.targetCoordinator.onTargetSelected(channel));
      flushEdt();

      onEdt(
          () -> {
            JMenuItem detachItem =
                findMenuItem(popupForTarget(fixture.serverTree, channel), "Detach \"#functional-detach\"");
            assertNotNull(detachItem);
            detachItem.doClick();
          });
      flushEdt();

      assertTrue(hasLeaf(fixture.serverTree, channel), "detached channel should remain in tree");
      assertTrue(fixture.serverTree.isChannelDetached(channel), "channel should be detached");
      assertTrue(isRenderedItalic(fixture.serverTree, channel), "detached channel should render as detached");
      assertFalse(Boolean.TRUE.equals(fixture.lastInputEnabledState()), "input should be disabled");
    } finally {
      detachSub.dispose();
      fixture.shutdown();
    }
  }

  @Test
  void joinFromDetachedContextMenuReattachesAndEnablesInputAfterJoinAck() throws Exception {
    Fixture fixture = createFixture(List.of());
    Disposable joinSub = fixture.serverTree.joinChannelRequests().subscribe(fixture.targetCoordinator::joinChannel);
    try {
      TargetRef channel = new TargetRef("libera", "#functional-join");

      fixture.markConnected();
      onEdt(
          () -> {
            fixture.targetCoordinator.onTargetSelected(channel);
            fixture.targetCoordinator.detachChannel(channel);
          });
      flushEdt();
      fixture.inputEnabledStates.clear();

      onEdt(
          () -> {
            JMenuItem joinItem =
                findMenuItem(popupForTarget(fixture.serverTree, channel), "Join \"#functional-join\"");
            assertNotNull(joinItem);
            joinItem.doClick();
          });
      flushEdt();

      verify(fixture.irc).joinChannel("libera", "#functional-join");
      assertTrue(fixture.serverTree.isChannelDetached(channel), "channel stays detached until JOIN event");

      onEdt(() -> fixture.targetCoordinator.onJoinedChannel("libera", "#functional-join"));
      flushEdt();

      assertFalse(fixture.serverTree.isChannelDetached(channel), "JOIN event should attach the channel");
      assertFalse(isRenderedItalic(fixture.serverTree, channel), "attached channel should not render detached");
      onEdt(
          () ->
              assertNotNull(
                  findMenuItem(
                      popupForTarget(fixture.serverTree, channel), "Detach \"#functional-join\"")));
      assertTrue(Boolean.TRUE.equals(fixture.lastInputEnabledState()), "input should be enabled");
    } finally {
      joinSub.dispose();
      fixture.shutdown();
    }
  }

  @Test
  void kickLikeMembershipLossDetachesChannelAndSwitchesMenuToJoin() throws Exception {
    Fixture fixture = createFixture(List.of());
    try {
      TargetRef channel = new TargetRef("libera", "#functional-kick");

      fixture.markConnected();
      onEdt(
          () -> {
            fixture.targetCoordinator.onTargetSelected(channel);
            fixture.targetCoordinator.onChannelMembershipLost("libera", "#functional-kick", true);
          });
      flushEdt();

      assertTrue(fixture.serverTree.isChannelDetached(channel));
      onEdt(
          () ->
              assertNotNull(
                  findMenuItem(popupForTarget(fixture.serverTree, channel), "Join \"#functional-kick\"")));
      assertFalse(
          Boolean.TRUE.equals(fixture.lastInputEnabledState()),
          "detached active channel should disable input");
    } finally {
      fixture.shutdown();
    }
  }

  @Test
  void startupRestoredChannelsBeginDetachedUntilJoinIsObserved() throws Exception {
    Fixture fixture = createFixture(List.of("#startup-restore"));
    try {
      TargetRef channel = new TargetRef("libera", "#startup-restore");

      assertTrue(hasLeaf(fixture.serverTree, channel), "startup-restored channel should exist");
      assertTrue(fixture.serverTree.isChannelDetached(channel), "startup-restored channel should start detached");

      fixture.markConnected();
      onEdt(() -> fixture.targetCoordinator.onTargetSelected(channel));
      flushEdt();

      assertTrue(fixture.serverTree.isChannelDetached(channel), "channel should remain detached before JOIN");
      assertFalse(
          Boolean.TRUE.equals(fixture.lastInputEnabledState()),
          "input should stay disabled while detached");

      onEdt(() -> fixture.targetCoordinator.onJoinedChannel("libera", "#startup-restore"));
      flushEdt();

      assertFalse(fixture.serverTree.isChannelDetached(channel), "JOIN event should attach restored channel");
      assertTrue(Boolean.TRUE.equals(fixture.lastInputEnabledState()), "input should enable after attach");
    } finally {
      fixture.shutdown();
    }
  }

  private Fixture createFixture(List<String> startupJoinedChannels) throws Exception {
    IrcProperties props = new IrcProperties(null, List.of(server("libera")));
    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(tempDir.resolve("ircafe.yml").toString(), props);
    for (String channel : startupJoinedChannels) {
      runtimeConfig.rememberJoinedChannel("libera", channel);
    }

    ServerRegistry serverRegistry = new ServerRegistry(props, runtimeConfig);
    ServerCatalog serverCatalog = new ServerCatalog(serverRegistry, new EphemeralServerRegistry());
    LogProperties logProps = new LogProperties(null, null, null, null, null, null, null);

    ServerTreeDockable serverTree =
        onEdtCall(
            () ->
                new ServerTreeDockable(
                    null,
                    null,
                    null,
                    null,
                    null,
                    new ConnectButton(),
                    new DisconnectButton(),
                    null,
                    null,
                    null,
                    null));

    ChatDockable chat = mock(ChatDockable.class);
    ChatTranscriptStore transcripts = mock(ChatTranscriptStore.class);
    MentionPatternRegistry mentions = mock(MentionPatternRegistry.class);
    NotificationStore notificationStore = mock(NotificationStore.class);
    UserListDockable users = mock(UserListDockable.class);
    StatusBar statusBar = mock(StatusBar.class);
    ChatDockManager chatDockManager = mock(ChatDockManager.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    TargetActivationBus activationBus = new TargetActivationBus();
    OutboundLineBus outboundBus = new OutboundLineBus();
    ConnectButton connectBtn = mock(ConnectButton.class);
    DisconnectButton disconnectBtn = mock(DisconnectButton.class);

    SwingUiPort ui =
        new SwingUiPort(
            serverTree,
            chat,
            transcripts,
            mentions,
            notificationStore,
            users,
            statusBar,
            connectBtn,
            disconnectBtn,
            activationBus,
            outboundBus,
            chatDockManager,
            activeInputRouter);

    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick(anyString())).thenReturn(Optional.empty());
    when(irc.requestNames(anyString(), anyString())).thenReturn(Completable.complete());
    when(irc.joinChannel(anyString(), anyString())).thenReturn(Completable.complete());
    when(irc.partChannel(anyString(), anyString(), nullable(String.class)))
        .thenReturn(Completable.complete());
    when(irc.partChannel(anyString(), anyString())).thenReturn(Completable.complete());
    when(irc.connect(anyString())).thenReturn(Completable.complete());
    when(irc.disconnect(anyString())).thenReturn(Completable.complete());
    when(irc.disconnect(anyString(), nullable(String.class))).thenReturn(Completable.complete());

    TrayNotificationsPort tray = mock(TrayNotificationsPort.class);
    ConnectionCoordinator connectionCoordinator =
        new ConnectionCoordinator(irc, ui, serverRegistry, serverCatalog, runtimeConfig, logProps, tray);

    CopyOnWriteArrayList<Boolean> inputEnabledStates = new CopyOnWriteArrayList<>();
    doAnswer(
            invocation -> {
              inputEnabledStates.add(invocation.getArgument(0, Boolean.class));
              return null;
            })
        .when(chat)
        .setInputEnabled(anyBoolean());

    TargetCoordinator targetCoordinator =
        new TargetCoordinator(
            ui,
            new UserListStore(),
            irc,
            serverRegistry,
            runtimeConfig,
            connectionCoordinator,
            mock(IgnoreListService.class),
            mock(UserhostQueryService.class),
            mock(UserInfoEnrichmentService.class),
            mock(TargetChatHistoryPort.class),
            mock(TargetLogMaintenancePort.class),
            mock(ExecutorService.class),
            mock(ScheduledExecutorService.class));

    flushEdt();
    return new Fixture(serverTree, irc, connectionCoordinator, targetCoordinator, inputEnabledStates);
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

  private static JMenuItem findMenuItem(JPopupMenu menu, String text) {
    if (menu == null || text == null) return null;
    for (Component comp : menu.getComponents()) {
      if (comp instanceof JMenuItem item && text.equals(item.getText())) return item;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static boolean hasLeaf(ServerTreeDockable dockable, TargetRef ref) throws Exception {
    Field leavesField = ServerTreeDockable.class.getDeclaredField("leaves");
    leavesField.setAccessible(true);
    Map<TargetRef, DefaultMutableTreeNode> leaves =
        (Map<TargetRef, DefaultMutableTreeNode>) leavesField.get(dockable);
    return leaves.containsKey(ref);
  }

  private static boolean isRenderedItalic(ServerTreeDockable dockable, TargetRef ref) throws Exception {
    JTree tree = getTree(dockable);
    DefaultMutableTreeNode node = findLeafNode(dockable, ref);
    if (node == null) return false;
    Component rendered =
        tree.getCellRenderer()
            .getTreeCellRendererComponent(tree, node, false, false, true, 0, false);
    return (rendered.getFont().getStyle() & Font.ITALIC) != 0;
  }

  private static JTree getTree(ServerTreeDockable dockable) throws Exception {
    Field treeField = ServerTreeDockable.class.getDeclaredField("tree");
    treeField.setAccessible(true);
    return (JTree) treeField.get(dockable);
  }

  @SuppressWarnings("unchecked")
  private static DefaultMutableTreeNode findLeafNode(ServerTreeDockable dockable, TargetRef ref)
      throws Exception {
    Field leavesField = ServerTreeDockable.class.getDeclaredField("leaves");
    leavesField.setAccessible(true);
    Map<TargetRef, DefaultMutableTreeNode> leaves =
        (Map<TargetRef, DefaultMutableTreeNode>) leavesField.get(dockable);
    return leaves.get(ref);
  }

  @SuppressWarnings("unchecked")
  private static JPopupMenu popupForTarget(ServerTreeDockable dockable, TargetRef ref) throws Exception {
    DefaultMutableTreeNode node = findLeafNode(dockable, ref);
    if (node == null) return null;
    Method buildPopupMenu = ServerTreeDockable.class.getDeclaredMethod("buildPopupMenu", TreePath.class);
    buildPopupMenu.setAccessible(true);
    return (JPopupMenu) buildPopupMenu.invoke(dockable, new TreePath(node.getPath()));
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
    final java.util.concurrent.atomic.AtomicReference<T> out = new java.util.concurrent.atomic.AtomicReference<>();
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
      ServerTreeDockable serverTree,
      IrcClientService irc,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      CopyOnWriteArrayList<Boolean> inputEnabledStates) {
    void markConnected() {
      connectionCoordinator.handleConnectivityEvent(
          "libera",
          new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "tester"),
          null);
    }

    Boolean lastInputEnabledState() {
      if (inputEnabledStates.isEmpty()) return null;
      return inputEnabledStates.get(inputEnabledStates.size() - 1);
    }

    void shutdown() throws Exception {
      onEdt(serverTree::shutdown);
      flushEdt();
    }
  }
}
