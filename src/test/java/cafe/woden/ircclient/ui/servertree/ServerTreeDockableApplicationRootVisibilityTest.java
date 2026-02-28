package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeDockableApplicationRootVisibilityTest {

  @Test
  void hidingApplicationRootDoesNotCollapseIrcRoot() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();

            invokeAddServerRoot(dockable, "libera");

            JTree tree = getTree(dockable);
            DefaultMutableTreeNode ircRoot = getIrcRoot(dockable);
            TreePath ircPath = new TreePath(ircRoot.getPath());

            tree.expandPath(ircPath);
            assertTrue(tree.isExpanded(ircPath), "precondition: IRC root should start expanded");

            dockable.setApplicationRootVisible(false);

            assertFalse(dockable.isApplicationRootVisible());
            assertTrue(
                tree.isExpanded(ircPath),
                "IRC root must remain expanded when hiding Application root");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void hidingNotificationsNodeFallsBackToStatusAndCanBeRestored() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            String serverId = "libera";

            invokeAddServerRoot(dockable, serverId);
            TargetRef notificationsRef = TargetRef.notifications(serverId);
            TargetRef statusRef = new TargetRef(serverId, "status");

            assertTrue(
                hasLeaf(dockable, notificationsRef),
                "precondition: notifications node should exist");

            dockable.selectTarget(notificationsRef);
            assertEquals(
                notificationsRef,
                selectedTargetRef(dockable),
                "precondition: notifications should be selected");

            dockable.setNotificationsNodesVisible(false);
            assertFalse(dockable.isNotificationsNodesVisible());
            assertFalse(
                hasLeaf(dockable, notificationsRef), "notifications node should be removed");
            assertEquals(
                statusRef,
                selectedTargetRef(dockable),
                "selection should fall back to server status");

            dockable.setNotificationsNodesVisible(true);
            assertTrue(dockable.isNotificationsNodesVisible());
            assertTrue(
                hasLeaf(dockable, notificationsRef), "notifications node should be restored");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void hidingMonitorNodeFallsBackToStatusAndCanBeRestored() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            String serverId = "libera";

            invokeAddServerRoot(dockable, serverId);
            TargetRef monitorRef = TargetRef.monitorGroup(serverId);
            TargetRef statusRef = new TargetRef(serverId, "status");

            assertTrue(
                hasMonitorGroupAttached(dockable, serverId),
                "precondition: monitor group should exist");

            dockable.selectTarget(monitorRef);
            assertTrue(
                isMonitorGroupSelected(dockable), "precondition: monitor group should be selected");

            dockable.setMonitorNodesVisible(false);
            assertFalse(dockable.isMonitorNodesVisible());
            assertFalse(
                hasMonitorGroupAttached(dockable, serverId), "monitor group should be removed");
            assertEquals(
                statusRef,
                selectedTargetRef(dockable),
                "selection should fall back to server status");

            dockable.setMonitorNodesVisible(true);
            assertTrue(dockable.isMonitorNodesVisible());
            assertTrue(
                hasMonitorGroupAttached(dockable, serverId), "monitor group should be restored");
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
  private static boolean hasLeaf(ServerTreeDockable dockable, TargetRef ref) throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("leaves");
    f.setAccessible(true);
    Map<TargetRef, DefaultMutableTreeNode> leaves =
        (Map<TargetRef, DefaultMutableTreeNode>) f.get(dockable);
    return leaves.containsKey(ref);
  }

  @SuppressWarnings("unchecked")
  private static boolean hasMonitorGroupAttached(ServerTreeDockable dockable, String serverId)
      throws Exception {
    Field serversField = ServerTreeDockable.class.getDeclaredField("servers");
    serversField.setAccessible(true);
    Map<String, Object> servers = (Map<String, Object>) serversField.get(dockable);
    Object serverNodes = servers.get(serverId);
    if (serverNodes == null) return false;

    Field serverNodeField = serverNodes.getClass().getDeclaredField("serverNode");
    serverNodeField.setAccessible(true);
    DefaultMutableTreeNode serverNode = (DefaultMutableTreeNode) serverNodeField.get(serverNodes);

    Field monitorNodeField = serverNodes.getClass().getDeclaredField("monitorNode");
    monitorNodeField.setAccessible(true);
    DefaultMutableTreeNode monitorNode = (DefaultMutableTreeNode) monitorNodeField.get(serverNodes);

    return monitorNode != null && monitorNode.getParent() == serverNode;
  }

  private static boolean isMonitorGroupSelected(ServerTreeDockable dockable) throws Exception {
    JTree tree = getTree(dockable);
    Object selected = tree.getLastSelectedPathComponent();
    if (!(selected instanceof DefaultMutableTreeNode node)) return false;
    Method m =
        ServerTreeDockable.class.getDeclaredMethod(
            "isMonitorGroupNode", DefaultMutableTreeNode.class);
    m.setAccessible(true);
    return Boolean.TRUE.equals(m.invoke(dockable, node));
  }

  private static TargetRef selectedTargetRef(ServerTreeDockable dockable) throws Exception {
    Method m = ServerTreeDockable.class.getDeclaredMethod("selectedTargetRef");
    m.setAccessible(true);
    return (TargetRef) m.invoke(dockable);
  }

  private static JTree getTree(ServerTreeDockable dockable) throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("tree");
    f.setAccessible(true);
    return (JTree) f.get(dockable);
  }

  private static DefaultMutableTreeNode getIrcRoot(ServerTreeDockable dockable) throws Exception {
    Field f = ServerTreeDockable.class.getDeclaredField("ircRoot");
    f.setAccessible(true);
    return (DefaultMutableTreeNode) f.get(dockable);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
