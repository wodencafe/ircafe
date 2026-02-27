package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
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
  void closeNodeActionOnChannelPublishesDetachRequest() throws Exception {
    ServerTreeDockable dockable = newDockable();
    CopyOnWriteArrayList<TargetRef> detached = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<TargetRef> closed = new CopyOnWriteArrayList<>();
    Disposable detachSub = dockable.detachChannelRequests().subscribe(detached::add);
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
  void channelContextMenuSwitchesDetachAndJoinAndPublishesRequests() throws Exception {
    ServerTreeDockable dockable = newDockable();
    CopyOnWriteArrayList<TargetRef> detached = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<TargetRef> joined = new CopyOnWriteArrayList<>();
    Disposable detachSub = dockable.detachChannelRequests().subscribe(detached::add);
    Disposable joinSub = dockable.joinChannelRequests().subscribe(joined::add);

    try {
      TargetRef channel = new TargetRef("libera", "#functional-menu");

      onEdt(() -> dockable.ensureNode(channel));
      flushEdt();

      JPopupMenu attachedMenu = onEdtCall(() -> popupForTarget(dockable, channel));
      JMenuItem detachItem = findMenuItem(attachedMenu, "Detach \"#functional-menu\"");
      assertNotNull(detachItem, "attached channel should show Detach action");

      onEdt(detachItem::doClick);
      flushEdt();
      waitFor(() -> !detached.isEmpty(), Duration.ofSeconds(2));
      assertEquals(channel, detached.getLast());

      onEdt(() -> dockable.setChannelDetached(channel, true));
      flushEdt();

      JPopupMenu detachedMenu = onEdtCall(() -> popupForTarget(dockable, channel));
      JMenuItem joinItem = findMenuItem(detachedMenu, "Join \"#functional-menu\"");
      assertNotNull(joinItem, "detached channel should show Join action");

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

  private static ServerTreeDockable newDockable() {
    return new ServerTreeDockable(
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

    Method buildPopupMenu =
        ServerTreeDockable.class.getDeclaredMethod("buildPopupMenu", TreePath.class);
    buildPopupMenu.setAccessible(true);
    return (JPopupMenu) buildPopupMenu.invoke(dockable, new TreePath(node.getPath()));
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
