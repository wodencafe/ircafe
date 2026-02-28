package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
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
  void channelContextMenuSwitchesBetweenDisconnectAndReconnect() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);

            JPopupMenu attachedMenu = buildPopupMenuForTarget(dockable, chan);
            assertNotNull(attachedMenu);
            assertNotNull(findMenuItem(attachedMenu, "Disconnect \"#ircafe\""));
            assertNotNull(findMenuItem(attachedMenu, "Close and PART \"#ircafe\""));
            assertFalse(dockable.isChannelDisconnected(chan));
            assertNull(findMenuItem(attachedMenu, "Reconnect \"#ircafe\""));
            assertNull(findMenuItem(attachedMenu, "Detach (Bouncer) \"#ircafe\""));

            dockable.setChannelDisconnected(chan, true);
            assertTrue(dockable.isChannelDisconnected(chan));

            JPopupMenu detachedMenu = buildPopupMenuForTarget(dockable, chan);
            assertNotNull(detachedMenu);
            assertNotNull(findMenuItem(detachedMenu, "Reconnect \"#ircafe\""));
            assertNotNull(findMenuItem(detachedMenu, "Close Channel \"#ircafe\""));
            assertNull(findMenuItem(detachedMenu, "Disconnect \"#ircafe\""));
            assertNull(findMenuItem(detachedMenu, "Close and PART \"#ircafe\""));
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
            JCheckBoxMenuItem autoReattach =
                findCheckBoxMenuItem(menu, "Auto-reconnect on startup");
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
  void channelContextMenuEmitsDisconnectAndReconnectRequests() throws Exception {
    onEdt(
        () -> {
          Disposable detachSub = null;
          Disposable bouncerDetachSub = null;
          Disposable joinSub = null;
          Disposable closeSub = null;
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);

            AtomicReference<TargetRef> detached = new AtomicReference<>();
            AtomicReference<TargetRef> bouncerDetached = new AtomicReference<>();
            AtomicReference<TargetRef> joined = new AtomicReference<>();
            AtomicReference<TargetRef> closed = new AtomicReference<>();
            detachSub = dockable.disconnectChannelRequests().subscribe(detached::set);
            bouncerDetachSub =
                dockable.bouncerDetachChannelRequests().subscribe(bouncerDetached::set);
            joinSub = dockable.joinChannelRequests().subscribe(joined::set);
            closeSub = dockable.closeChannelRequests().subscribe(closed::set);

            JMenuItem detachItem =
                findMenuItem(
                    Objects.requireNonNull(buildPopupMenuForTarget(dockable, chan)),
                    "Disconnect \"#ircafe\"");
            assertNotNull(detachItem);
            detachItem.doClick();
            assertEquals(chan, detached.get());
            assertNull(bouncerDetached.get());

            JMenuItem closeItem =
                findMenuItem(
                    Objects.requireNonNull(buildPopupMenuForTarget(dockable, chan)),
                    "Close and PART \"#ircafe\"");
            assertNotNull(closeItem);
            closeItem.doClick();
            assertEquals(chan, closed.get());

            dockable.setChannelDisconnected(chan, true);
            JMenuItem joinItem =
                findMenuItem(
                    Objects.requireNonNull(buildPopupMenuForTarget(dockable, chan)),
                    "Reconnect \"#ircafe\"");
            assertNotNull(joinItem);
            joinItem.doClick();
            assertEquals(chan, joined.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            if (detachSub != null) detachSub.dispose();
            if (bouncerDetachSub != null) bouncerDetachSub.dispose();
            if (joinSub != null) joinSub.dispose();
            if (closeSub != null) closeSub.dispose();
          }
        });
  }

  @Test
  void channelContextMenuShowsAndEmitsBouncerDetachWhenSupported() throws Exception {
    onEdt(
        () -> {
          Disposable bouncerDetachSub = null;
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");
            dockable.setServerConnectionState("libera", ConnectionState.CONNECTED);
            dockable.setServerIrcv3Capability("libera", "znc.in/playback", "ACK", true);

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);

            AtomicReference<TargetRef> bouncerDetached = new AtomicReference<>();
            bouncerDetachSub =
                dockable.bouncerDetachChannelRequests().subscribe(bouncerDetached::set);

            JMenuItem bouncerDetach =
                findMenuItem(
                    Objects.requireNonNull(buildPopupMenuForTarget(dockable, chan)),
                    "Detach (Bouncer) \"#ircafe\"");
            assertNotNull(bouncerDetach);
            bouncerDetach.doClick();
            assertEquals(chan, bouncerDetached.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            if (bouncerDetachSub != null) bouncerDetachSub.dispose();
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
            dockable.setChannelDisconnected(chan, true);

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
            dockable.setChannelDisconnected(chan, false);

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
  void unreadAndHighlightCountsRenderAsBadgesWithoutMutatingLabelText() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);
            dockable.markUnread(chan);
            dockable.markHighlight(chan);

            JTree tree = getTree(dockable);
            DefaultMutableTreeNode node = findLeafNode(dockable, chan);
            assertNotNull(node);

            Component rendered =
                tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, node, false, false, true, 0, false);
            assertTrue(rendered instanceof JLabel);
            JLabel label = (JLabel) rendered;
            assertEquals("#ircafe", label.getText());
            assertFalse(label.getText().contains("("));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void disablingServerTreeNotificationBadgesRemovesBadgeWidthReservation() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);
            dockable.markUnread(chan);
            dockable.markHighlight(chan);

            JTree tree = getTree(dockable);
            DefaultMutableTreeNode node = findLeafNode(dockable, chan);
            assertNotNull(node);

            Component withBadges =
                tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, node, false, false, true, 0, false);
            assertTrue(withBadges instanceof JComponent);
            int widthWithBadges = ((JComponent) withBadges).getPreferredSize().width;

            setServerTreeNotificationBadgesEnabled(dockable, false);

            Component withoutBadges =
                tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, node, false, false, true, 0, false);
            assertTrue(withoutBadges instanceof JComponent);
            int widthWithoutBadges = ((JComponent) withoutBadges).getPreferredSize().width;

            assertTrue(
                widthWithoutBadges < widthWithBadges,
                "Renderer width should shrink when server-tree notification badges are hidden.");
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

            dockable.setChannelDisconnected(chan, true);
            assertTrue(dockable.isChannelDisconnected(chan));
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
  void detachedWarningTooltipShowsReasonAndClickClearsWarningOnly() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef chan = new TargetRef("libera", "#ircafe");
            dockable.ensureNode(chan);
            dockable.setChannelDisconnected(chan, true, "Kicked by ChanServ (flood)");

            JTree tree = getTree(dockable);
            DefaultMutableTreeNode node = findLeafNode(dockable, chan);
            assertNotNull(node);
            TreePath path = new TreePath(node.getPath());
            Rectangle warningBounds = disconnectedWarningBounds(dockable, path, node);
            assertNotNull(warningBounds);

            MouseEvent hover =
                new MouseEvent(
                    tree,
                    MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(),
                    0,
                    warningBounds.x + Math.max(1, warningBounds.width / 2),
                    warningBounds.y + Math.max(1, warningBounds.height / 2),
                    0,
                    false,
                    MouseEvent.NOBUTTON);
            String tip = invokeTooltip(dockable, hover);
            assertNotNull(tip);
            assertTrue(tip.contains("Kicked by ChanServ"));

            MouseEvent click =
                new MouseEvent(
                    tree,
                    MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    warningBounds.x + Math.max(1, warningBounds.width / 2),
                    warningBounds.y + Math.max(1, warningBounds.height / 2),
                    1,
                    false,
                    MouseEvent.BUTTON1);
            boolean handled = invokeMaybeHandleDisconnectedWarningClick(dockable, click);
            assertTrue(handled);
            assertTrue(dockable.isChannelDisconnected(chan));

            ServerTreeDockable.NodeData nd = (ServerTreeDockable.NodeData) node.getUserObject();
            assertFalse(nd.hasDetachedWarning());
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
            dockable.setChannelDisconnected(chan, true);
            dockable.selectTarget(chan);

            AtomicReference<TargetRef> detached = new AtomicReference<>();
            AtomicReference<TargetRef> closed = new AtomicReference<>();
            detachSub = dockable.disconnectChannelRequests().subscribe(detached::set);
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
            detachSub = dockable.disconnectChannelRequests().subscribe(detached::set);
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
                "oftc", ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY);

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
                ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY,
                dockable.channelSortModeForServer("oftc"));
            assertEquals(List.of("#beta", "#alpha"), liberaOrder);
            assertEquals(List.of("#alpha", "#beta"), oftcOrder);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void mostRecentActivitySortPromotesRecentlyActiveChannels() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");
            TargetRef alpha = new TargetRef("libera", "#alpha");
            TargetRef beta = new TargetRef("libera", "#beta");
            TargetRef gamma = new TargetRef("libera", "#gamma");

            dockable.ensureNode(alpha);
            dockable.ensureNode(beta);
            dockable.ensureNode(gamma);

            dockable.setChannelSortModeForServer(
                "libera", ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY);
            dockable.markUnread(beta);
            dockable.markHighlight(gamma);

            List<String> order =
                dockable.managedChannelsForServer("libera").stream()
                    .map(ServerTreeDockable.ManagedChannelEntry::channel)
                    .toList();

            assertEquals(List.of("#gamma", "#beta", "#alpha"), order);
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

  private static Rectangle disconnectedWarningBounds(
      ServerTreeDockable dockable, TreePath path, DefaultMutableTreeNode node) throws Exception {
    Method m =
        ServerTreeDockable.class.getDeclaredMethod(
            "disconnectedWarningIndicatorBounds", TreePath.class, DefaultMutableTreeNode.class);
    m.setAccessible(true);
    return (Rectangle) m.invoke(dockable, path, node);
  }

  private static String invokeTooltip(ServerTreeDockable dockable, MouseEvent event)
      throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("toolTipForEvent", MouseEvent.class);
    m.setAccessible(true);
    return (String) m.invoke(dockable, event);
  }

  private static boolean invokeMaybeHandleDisconnectedWarningClick(
      ServerTreeDockable dockable, MouseEvent event) throws Exception {
    Method m =
        ServerTreeDockable.class.getDeclaredMethod(
            "maybeHandleDisconnectedWarningClick", MouseEvent.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(dockable, event);
  }

  private static void setServerTreeNotificationBadgesEnabled(
      ServerTreeDockable dockable, boolean enabled) throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("serverTreeNotificationBadgesEnabled");
    f.setAccessible(true);
    f.setBoolean(dockable, enabled);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
