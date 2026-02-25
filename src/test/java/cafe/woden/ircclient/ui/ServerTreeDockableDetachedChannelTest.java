package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeDockableDetachedChannelTest {

  @Test
  void channelContextMenuSwitchesBetweenDetachAndJoin() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);

            JPopupMenu attachedMenu = buildPopupMenuForTarget(dockable, chan);
            assertNotNull(attachedMenu);
            assertNotNull(findMenuItem(attachedMenu, "Detach \"#ircafe\""));
            assertNotNull(findMenuItem(attachedMenu, "Close Channel \"#ircafe\""));
            assertFalse(dockable.isChannelDetached(chan));
            assertNull(findMenuItem(attachedMenu, "Join \"#ircafe\""));

            dockable.setChannelDetached(chan, true);
            assertTrue(dockable.isChannelDetached(chan));

            JPopupMenu detachedMenu = buildPopupMenuForTarget(dockable, chan);
            assertNotNull(detachedMenu);
            assertNotNull(findMenuItem(detachedMenu, "Join \"#ircafe\""));
            assertNotNull(findMenuItem(detachedMenu, "Close Channel \"#ircafe\""));
            assertNull(findMenuItem(detachedMenu, "Detach \"#ircafe\""));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void channelNodeIsNestedUnderChannelListNode() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef channelList = TargetRef.channelList("libera");
            TargetRef chan = new TargetRef("libera", "#nested");
            dockable.ensureNode(chan);

            DefaultMutableTreeNode channelNode = findLeafNode(dockable, chan);
            assertNotNull(channelNode);
            DefaultMutableTreeNode channelListNode = findLeafNode(dockable, channelList);
            assertNotNull(channelListNode);
            assertEquals(channelListNode, channelNode.getParent());
            assertTrue(dockable.isChannelListNodesVisible());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void channelContextMenuShowsAutoReattachCheckbox() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);

            JPopupMenu menu = buildPopupMenuForTarget(dockable, chan);
            assertNotNull(menu);
            JCheckBoxMenuItem autoReattach = findCheckBoxMenuItem(menu, "Auto-reattach on startup");
            assertNotNull(autoReattach);
            assertTrue(autoReattach.isSelected());

            autoReattach.doClick();
            assertFalse(dockable.isChannelAutoReattach(chan));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void channelContextMenuEmitsDetachAndJoinRequests() throws Exception {
    onEdt(
        () -> {
          Disposable detachSub = null;
          Disposable joinSub = null;
          Disposable closeSub = null;
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);

            AtomicReference<TargetRef> detached = new AtomicReference<>();
            AtomicReference<TargetRef> joined = new AtomicReference<>();
            AtomicReference<TargetRef> closed = new AtomicReference<>();
            detachSub = dockable.detachChannelRequests().subscribe(detached::set);
            joinSub = dockable.joinChannelRequests().subscribe(joined::set);
            closeSub = dockable.closeChannelRequests().subscribe(closed::set);

            JMenuItem detachItem =
                findMenuItem(
                    Objects.requireNonNull(buildPopupMenuForTarget(dockable, chan)),
                    "Detach \"#ircafe\"");
            assertNotNull(detachItem);
            detachItem.doClick();
            assertEquals(chan, detached.get());

            JMenuItem closeItem =
                findMenuItem(
                    Objects.requireNonNull(buildPopupMenuForTarget(dockable, chan)),
                    "Close Channel \"#ircafe\"");
            assertNotNull(closeItem);
            closeItem.doClick();
            assertEquals(chan, closed.get());

            dockable.setChannelDetached(chan, true);
            JMenuItem joinItem =
                findMenuItem(
                    Objects.requireNonNull(buildPopupMenuForTarget(dockable, chan)),
                    "Join \"#ircafe\"");
            assertNotNull(joinItem);
            joinItem.doClick();
            assertEquals(chan, joined.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            if (detachSub != null) detachSub.dispose();
            if (joinSub != null) joinSub.dispose();
            if (closeSub != null) closeSub.dispose();
          }
        });
  }

  @Test
  void detachedChannelRendererUsesItalicAndMutedForeground() throws Exception {
    onEdt(
        () -> {
          Color prevLabelDisabled = UIManager.getColor("Label.disabledForeground");
          Color prevComponentDisabled = UIManager.getColor("Component.disabledForeground");
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);
            dockable.setChannelDetached(chan, true);

            UIManager.put("Label.disabledForeground", new Color(12, 34, 56));
            UIManager.put("Component.disabledForeground", new Color(65, 76, 87));

            JTree tree = getTree(dockable);
            DefaultMutableTreeNode node = findLeafNode(dockable, chan);
            assertNotNull(node);

            Component rendered =
                tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, node, false, false, true, 0, false);

            assertTrue((rendered.getFont().getStyle() & Font.ITALIC) != 0);
            assertEquals(new Color(12, 34, 56), rendered.getForeground());
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            UIManager.put("Label.disabledForeground", prevLabelDisabled);
            UIManager.put("Component.disabledForeground", prevComponentDisabled);
          }
        });
  }

  @Test
  void attachedChannelRendererDoesNotForceItalic() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);
            dockable.setChannelDetached(chan, false);

            JTree tree = getTree(dockable);
            DefaultMutableTreeNode node = findLeafNode(dockable, chan);
            assertNotNull(node);

            Component rendered =
                tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, node, false, false, true, 0, false);

            assertEquals(0, rendered.getFont().getStyle() & Font.ITALIC);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void channelRendererReservesLeftInsetForTypingIndicatorSlot() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            TargetRef status = new TargetRef("libera", "status");
            dockable.ensureNode(chan);
            dockable.ensureNode(status);

            JTree tree = getTree(dockable);
            DefaultMutableTreeNode chanNode = findLeafNode(dockable, chan);
            DefaultMutableTreeNode statusNode = findLeafNode(dockable, status);
            assertNotNull(chanNode);
            assertNotNull(statusNode);

            Component renderedChannel =
                tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, chanNode, false, false, true, 0, false);
            assertTrue(renderedChannel instanceof JComponent);
            int channelLeftInset = ((JComponent) renderedChannel).getInsets().left;

            Component renderedStatus =
                tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, statusNode, false, false, true, 0, false);
            assertTrue(renderedStatus instanceof JComponent);
            int statusLeftInset = ((JComponent) renderedStatus).getInsets().left;
            assertTrue(
                channelLeftInset > statusLeftInset,
                "Channel rows should reserve extra left inset for typing indicator slot.");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void detachingChannelClearsTypingIndicatorImmediately() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);
            dockable.markTypingActivity(chan, "active");

            DefaultMutableTreeNode node = findLeafNode(dockable, chan);
            assertNotNull(node);
            assertTrue(node.getUserObject() instanceof ServerTreeDockable.NodeData);
            ServerTreeDockable.NodeData nd = (ServerTreeDockable.NodeData) node.getUserObject();
            assertTrue(nd.hasTypingActivity());
            assertTrue(typingActivityNodes(dockable).contains(node));

            dockable.setChannelDetached(chan, true);
            assertTrue(dockable.isChannelDetached(chan));
            assertFalse(nd.hasTypingActivity());
            assertFalse(typingActivityNodes(dockable).contains(node));

            dockable.markTypingActivity(chan, "active");
            assertFalse(nd.hasTypingActivity());
            assertFalse(typingActivityNodes(dockable).contains(node));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void keyboardCloseOnDetachedChannelDoesNotEmitRequests() throws Exception {
    onEdt(
        () -> {
          Disposable detachSub = null;
          Disposable closeSub = null;
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);
            dockable.setChannelDetached(chan, true);
            dockable.selectTarget(chan);

            AtomicReference<TargetRef> detached = new AtomicReference<>();
            AtomicReference<TargetRef> closed = new AtomicReference<>();
            detachSub = dockable.detachChannelRequests().subscribe(detached::set);
            closeSub = dockable.closeTargetRequests().subscribe(closed::set);

            triggerKeyboardClose(dockable);

            assertNull(detached.get());
            assertNull(closed.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            if (detachSub != null) detachSub.dispose();
            if (closeSub != null) closeSub.dispose();
          }
        });
  }

  @Test
  void keyboardCloseOnPrivateMessageEmitsCloseTargetRequest() throws Exception {
    onEdt(
        () -> {
          Disposable detachSub = null;
          Disposable closeSub = null;
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef pm = new TargetRef("libera", "alice");
            dockable.ensureNode(pm);
            dockable.selectTarget(pm);

            AtomicReference<TargetRef> detached = new AtomicReference<>();
            AtomicReference<TargetRef> closed = new AtomicReference<>();
            detachSub = dockable.detachChannelRequests().subscribe(detached::set);
            closeSub = dockable.closeTargetRequests().subscribe(closed::set);

            triggerKeyboardClose(dockable);

            assertNull(detached.get());
            assertEquals(pm, closed.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            if (detachSub != null) detachSub.dispose();
            if (closeSub != null) closeSub.dispose();
          }
        });
  }

  @Test
  void channelSortModeAndCustomOrderAreServerScoped() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");
            invokeAddServerRoot(dockable, "oftc");

            dockable.ensureNode(new TargetRef("libera", "#beta"));
            dockable.ensureNode(new TargetRef("libera", "#alpha"));
            dockable.ensureNode(new TargetRef("oftc", "#beta"));
            dockable.ensureNode(new TargetRef("oftc", "#alpha"));

            dockable.setChannelSortModeForServer(
                "libera", ServerTreeDockable.ChannelSortMode.CUSTOM);
            dockable.setChannelCustomOrderForServer("libera", List.of("#beta", "#alpha"));
            dockable.setChannelSortModeForServer(
                "oftc", ServerTreeDockable.ChannelSortMode.ALPHABETICAL);

            List<String> liberaOrder =
                dockable.managedChannelsForServer("libera").stream()
                    .map(ServerTreeDockable.ManagedChannelEntry::channel)
                    .toList();
            List<String> oftcOrder =
                dockable.managedChannelsForServer("oftc").stream()
                    .map(ServerTreeDockable.ManagedChannelEntry::channel)
                    .toList();

            assertEquals(
                ServerTreeDockable.ChannelSortMode.CUSTOM,
                dockable.channelSortModeForServer("libera"));
            assertEquals(
                ServerTreeDockable.ChannelSortMode.ALPHABETICAL,
                dockable.channelSortModeForServer("oftc"));
            assertEquals(List.of("#beta", "#alpha"), liberaOrder);
            assertEquals(List.of("#alpha", "#beta"), oftcOrder);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
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

  private static void invokeAddServerRoot(ServerTreeDockable dockable, String serverId)
      throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("addServerRoot", String.class);
    m.setAccessible(true);
    m.invoke(dockable, serverId);
  }

  @SuppressWarnings("unchecked")
  private static JPopupMenu buildPopupMenuForTarget(ServerTreeDockable dockable, TargetRef ref)
      throws Exception {
    DefaultMutableTreeNode node = findLeafNode(dockable, ref);
    if (node == null) return null;
    TreePath path = new TreePath(node.getPath());

    Method m = ServerTreeDockable.class.getDeclaredMethod("buildPopupMenu", TreePath.class);
    m.setAccessible(true);
    return (JPopupMenu) m.invoke(dockable, path);
  }

  private static JMenuItem findMenuItem(JPopupMenu menu, String text) {
    if (menu == null || text == null) return null;
    for (java.awt.Component c : menu.getComponents()) {
      if (!(c instanceof JMenuItem item)) continue;
      if (text.equals(item.getText())) return item;
    }
    return null;
  }

  private static JCheckBoxMenuItem findCheckBoxMenuItem(JPopupMenu menu, String text) {
    if (menu == null || text == null) return null;
    for (java.awt.Component c : menu.getComponents()) {
      if (!(c instanceof JCheckBoxMenuItem item)) continue;
      if (text.equals(item.getText())) return item;
    }
    return null;
  }

  private static void triggerKeyboardClose(ServerTreeDockable dockable) throws Exception {
    JTree tree = getTree(dockable);
    tree.getActionMap()
        .get("ircafe.tree.closeNode")
        .actionPerformed(new ActionEvent(tree, ActionEvent.ACTION_PERFORMED, "test-close"));
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
  private static java.util.Set<DefaultMutableTreeNode> typingActivityNodes(
      ServerTreeDockable dockable) throws Exception {
    Field field = ServerTreeDockable.class.getDeclaredField("typingActivityNodes");
    field.setAccessible(true);
    return (java.util.Set<DefaultMutableTreeNode>) field.get(dockable);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
