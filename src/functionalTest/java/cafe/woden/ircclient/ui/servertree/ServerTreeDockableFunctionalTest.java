package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.testutil.FunctionalTestWiringSupport;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeDockableFunctionalTest {

  @Test
  void ensureAndSelectTargetPublishesSelection() throws Exception {
    ServerTreeDockable dockable = newDockable();
    CopyOnWriteArrayList<TargetRef> selectedTargets = new CopyOnWriteArrayList<>();
    Disposable sub = dockable.selectionStream().subscribe(selectedTargets::add);

    try {
      TargetRef target = new TargetRef("libera", "#functional");

      onEdt(
          () -> {
            dockable.ensureNode(target);
            dockable.selectTarget(target);
          });
      flushEdt();

      waitFor(() -> !selectedTargets.isEmpty(), Duration.ofSeconds(2));
      assertEquals(target, selectedTargets.getLast());
    } finally {
      sub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void switchingTargetsPublishesSelectionSequence() throws Exception {
    ServerTreeDockable dockable = newDockable();
    CopyOnWriteArrayList<TargetRef> selectedTargets = new CopyOnWriteArrayList<>();
    Disposable sub = dockable.selectionStream().subscribe(selectedTargets::add);

    try {
      TargetRef first = new TargetRef("libera", "#one");
      TargetRef second = new TargetRef("libera", "#two");

      onEdt(
          () -> {
            dockable.ensureNode(first);
            dockable.ensureNode(second);
            dockable.selectTarget(first);
            dockable.selectTarget(second);
          });
      flushEdt();

      waitFor(() -> selectedTargets.size() >= 2, Duration.ofSeconds(2));
      assertEquals(first, selectedTargets.get(selectedTargets.size() - 2));
      assertEquals(second, selectedTargets.getLast());
    } finally {
      sub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void selectingIgnoresBuiltInNodePublishesIgnoresTarget() throws Exception {
    ServerTreeDockable dockable = newDockable();
    CopyOnWriteArrayList<TargetRef> selectedTargets = new CopyOnWriteArrayList<>();
    Disposable sub = dockable.selectionStream().subscribe(selectedTargets::add);

    try {
      TargetRef ignores = TargetRef.ignores("libera");

      onEdt(
          () -> {
            dockable.ensureNode(ignores);
            dockable.selectTarget(ignores);
          });
      flushEdt();

      waitFor(() -> !selectedTargets.isEmpty(), Duration.ofSeconds(2));
      assertEquals(ignores, selectedTargets.getLast());
    } finally {
      sub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void interceptorChildNodesShowTheirOwnHitCountsInsteadOfParentGroupBadge() throws Exception {
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.changes()).thenReturn(Flowable.never());
    when(interceptorStore.totalHitCount("libera")).thenReturn(5);
    when(interceptorStore.listInterceptorRefsForBaseServer("libera"))
        .thenReturn(
            List.of(
                new InterceptorStore.ScopedInterceptorRef("libera", "audit"),
                new InterceptorStore.ScopedInterceptorRef("libera", "watcher")));
    when(interceptorStore.interceptorName("libera", "audit")).thenReturn("Audit");
    when(interceptorStore.interceptorName("libera", "watcher")).thenReturn("Watcher");
    when(interceptorStore.hitCount("libera", "audit")).thenReturn(2);
    when(interceptorStore.hitCount("libera", "watcher")).thenReturn(3);

    ServerTreeDockable dockable = newDockable(interceptorStore);
    try {
      onEdt(() -> invokeAddServerRoot(dockable, "libera"));
      flushEdt();

      @SuppressWarnings("unchecked")
      Map<TargetRef, DefaultMutableTreeNode> leaves =
          onEdtCall(
              () -> {
                Field leavesField = ServerTreeDockable.class.getDeclaredField("leaves");
                leavesField.setAccessible(true);
                return (Map<TargetRef, DefaultMutableTreeNode>) leavesField.get(dockable);
              });

      ServerTreeNodeData groupData =
          (ServerTreeNodeData) leaves.get(TargetRef.interceptorsGroup("libera")).getUserObject();
      ServerTreeNodeData auditData =
          (ServerTreeNodeData) leaves.get(TargetRef.interceptor("libera", "audit")).getUserObject();
      ServerTreeNodeData watcherData =
          (ServerTreeNodeData)
              leaves.get(TargetRef.interceptor("libera", "watcher")).getUserObject();

      assertEquals(0, groupData.unread);
      assertEquals(0, groupData.highlightUnread);
      assertEquals(2, auditData.unread);
      assertEquals(0, auditData.highlightUnread);
      assertEquals(3, watcherData.unread);
      assertEquals(0, watcherData.highlightUnread);
    } finally {
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void closeNodeActionOnChannelPublishesDetachRequest() throws Exception {
    ServerTreeDockable dockable = newDockable();
    CopyOnWriteArrayList<TargetRef> detached = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<TargetRef> closed = new CopyOnWriteArrayList<>();
    Disposable detachSub = dockable.disconnectChannelRequests().subscribe(detached::add);
    Disposable closeSub = dockable.closeTargetRequests().subscribe(closed::add);

    try {
      TargetRef channel = new TargetRef("libera", "#functional-detach-action");

      onEdt(
          () -> {
            dockable.ensureNode(channel);
            dockable.selectTarget(channel);
            dockable.closeNodeAction().actionPerformed(new ActionEvent(dockable, 0, "close"));
          });
      flushEdt();

      waitFor(() -> !detached.isEmpty(), Duration.ofSeconds(2));
      assertEquals(channel, detached.getLast());
      assertTrue(closed.isEmpty(), "channel close action should detach, not close");
    } finally {
      detachSub.dispose();
      closeSub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void channelContextMenuSwitchesDisconnectAndReconnectAndPublishesRequests() throws Exception {
    ServerTreeDockable dockable = newDockable();
    CopyOnWriteArrayList<TargetRef> detached = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<TargetRef> joined = new CopyOnWriteArrayList<>();
    Disposable detachSub = dockable.disconnectChannelRequests().subscribe(detached::add);
    Disposable joinSub = dockable.joinChannelRequests().subscribe(joined::add);

    try {
      TargetRef channel = new TargetRef("libera", "#functional-menu");

      onEdt(() -> dockable.ensureNode(channel));
      flushEdt();

      JPopupMenu attachedMenu = onEdtCall(() -> popupForTarget(dockable, channel));
      JMenuItem detachItem = findMenuItem(attachedMenu, "Disconnect \"#functional-menu\"");
      assertNotNull(detachItem, "connected channel should show Disconnect action");

      onEdt(detachItem::doClick);
      flushEdt();
      waitFor(() -> !detached.isEmpty(), Duration.ofSeconds(2));
      assertEquals(channel, detached.getLast());

      onEdt(() -> dockable.setChannelDisconnected(channel, true));
      flushEdt();

      JPopupMenu detachedMenu = onEdtCall(() -> popupForTarget(dockable, channel));
      JMenuItem joinItem = findMenuItem(detachedMenu, "Reconnect \"#functional-menu\"");
      assertNotNull(joinItem, "disconnected channel should show Reconnect action");

      onEdt(joinItem::doClick);
      flushEdt();
      waitFor(() -> !joined.isEmpty(), Duration.ofSeconds(2));
      assertEquals(channel, joined.getLast());
    } finally {
      detachSub.dispose();
      joinSub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void quasselServerContextMenuShowsManageActionAndPublishesRequest() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(serverCatalog.entries()).thenReturn(List.of());
    when(serverCatalog.updates()).thenReturn(Flowable.never());
    when(serverCatalog.findEntry("quassel"))
        .thenReturn(
            Optional.of(
                ServerEntry.persistent(
                    server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE))));

    ServerTreeDockable dockable = newDockable(serverCatalog, runtimeConfig);
    AtomicReference<String> requestedServer = new AtomicReference<>();
    Disposable requestSub =
        dockable.quasselNetworkManagerRequests().subscribe(requestedServer::set);

    try {
      onEdt(() -> invokeAddServerRoot(dockable, "quassel"));
      flushEdt();

      JPopupMenu menu = onEdtCall(() -> popupForServerRoot(dockable, "quassel"));
      JMenuItem manage = findMenuItem(menu, "Manage Quassel Networks...");
      assertNotNull(manage, "quassel backend server should expose manager action");

      onEdt(manage::doClick);
      flushEdt();
      waitFor(() -> "quassel".equals(requestedServer.get()), Duration.ofSeconds(2));
    } finally {
      requestSub.dispose();
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  @Test
  void regularIrcServerContextMenuOmitsManageQuasselAction() throws Exception {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(serverCatalog.entries()).thenReturn(List.of());
    when(serverCatalog.updates()).thenReturn(Flowable.never());
    when(serverCatalog.findEntry("libera"))
        .thenReturn(
            Optional.of(
                ServerEntry.persistent(server("libera", IrcProperties.Server.Backend.IRC))));

    ServerTreeDockable dockable = newDockable(serverCatalog, runtimeConfig);
    try {
      onEdt(() -> invokeAddServerRoot(dockable, "libera"));
      flushEdt();

      JPopupMenu menu = onEdtCall(() -> popupForServerRoot(dockable, "libera"));
      assertNull(
          findMenuItem(menu, "Manage Quassel Networks..."),
          "regular IRC backend server should not expose quassel manager action");
    } finally {
      onEdt(dockable::shutdown);
      flushEdt();
    }
  }

  private static ServerTreeDockable newDockable() {
    return FunctionalTestWiringSupport.newServerTreeDockable(
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
        null);
  }

  private static ServerTreeDockable newDockable(
      ServerCatalog serverCatalog, RuntimeConfigStore runtimeConfig) {
    return FunctionalTestWiringSupport.newServerTreeDockable(
        serverCatalog,
        runtimeConfig,
        null,
        null,
        null,
        new ConnectButton(),
        new DisconnectButton(),
        null,
        null,
        null,
        null);
  }

  private static ServerTreeDockable newDockable(InterceptorStore interceptorStore) {
    return FunctionalTestWiringSupport.newServerTreeDockable(
        null,
        null,
        null,
        null,
        null,
        new ConnectButton(),
        new DisconnectButton(),
        null,
        interceptorStore,
        null,
        null);
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

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
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

  @SuppressWarnings("unchecked")
  private static JPopupMenu popupForTarget(ServerTreeDockable dockable, TargetRef ref)
      throws Exception {
    Field leavesField = ServerTreeDockable.class.getDeclaredField("leaves");
    leavesField.setAccessible(true);
    var leaves = (java.util.Map<TargetRef, DefaultMutableTreeNode>) leavesField.get(dockable);
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return null;

    Field contextMenuBuilderField = ServerTreeDockable.class.getDeclaredField("contextMenuBuilder");
    contextMenuBuilderField.setAccessible(true);
    Function<TreePath, JPopupMenu> contextMenuBuilder =
        (Function<TreePath, JPopupMenu>) contextMenuBuilderField.get(dockable);
    return contextMenuBuilder.apply(new TreePath(node.getPath()));
  }

  @SuppressWarnings("unchecked")
  private static JPopupMenu popupForServerRoot(ServerTreeDockable dockable, String serverId)
      throws Exception {
    Field serversField = ServerTreeDockable.class.getDeclaredField("servers");
    serversField.setAccessible(true);
    Map<String, ?> servers = (Map<String, ?>) serversField.get(dockable);
    Object serverNodes = servers.get(serverId);
    if (serverNodes == null) return null;

    Field serverNodeField = serverNodes.getClass().getDeclaredField("serverNode");
    serverNodeField.setAccessible(true);
    DefaultMutableTreeNode serverNode = (DefaultMutableTreeNode) serverNodeField.get(serverNodes);

    Field contextMenuBuilderField = ServerTreeDockable.class.getDeclaredField("contextMenuBuilder");
    contextMenuBuilderField.setAccessible(true);
    Function<TreePath, JPopupMenu> contextMenuBuilder =
        (Function<TreePath, JPopupMenu>) contextMenuBuilderField.get(dockable);
    return contextMenuBuilder.apply(new TreePath(serverNode.getPath()));
  }

  private static void invokeAddServerRoot(ServerTreeDockable dockable, String serverId)
      throws Exception {
    Method method = ServerTreeDockable.class.getDeclaredMethod("addServerRoot", String.class);
    method.setAccessible(true);
    method.invoke(dockable, serverId);
  }

  private static IrcProperties.Server server(String id, IrcProperties.Server.Backend backend) {
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
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }

  private static JMenuItem findMenuItem(JPopupMenu menu, String text) {
    if (menu == null || text == null) return null;
    for (java.awt.Component comp : menu.getComponents()) {
      if (comp instanceof JMenuItem item && text.equals(item.getText())) return item;
    }
    return null;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
