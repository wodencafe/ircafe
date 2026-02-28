package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeDockableIgnoresTooltipRegressionTest {

  @Test
  void ignoresLeafTooltipExplainsIgnoreManagementScope() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef ignoresRef = TargetRef.ignores("libera");
            DefaultMutableTreeNode ignoresNode = findLeafNode(dockable, ignoresRef);
            assertNotNull(ignoresNode);

            JTree tree = getTree(dockable);
            TreePath path = new TreePath(ignoresNode.getPath());
            tree.expandPath(path);
            Rectangle bounds = tree.getPathBounds(path);
            assertNotNull(bounds);

            MouseEvent event =
                new MouseEvent(
                    tree,
                    MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(),
                    0,
                    bounds.x + 4,
                    bounds.y + Math.max(1, bounds.height / 2),
                    1,
                    false);

            Method tooltip =
                ServerTreeDockable.class.getDeclaredMethod("toolTipForEvent", MouseEvent.class);
            tooltip.setAccessible(true);
            String text = (String) tooltip.invoke(dockable, event);
            assertNotNull(text);
            assertTrue(text.contains("hard and soft ignore rules"));
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
  private static DefaultMutableTreeNode findLeafNode(ServerTreeDockable dockable, TargetRef ref)
      throws Exception {
    Field leavesField = ServerTreeDockable.class.getDeclaredField("leaves");
    leavesField.setAccessible(true);
    Map<TargetRef, DefaultMutableTreeNode> leaves =
        (Map<TargetRef, DefaultMutableTreeNode>) leavesField.get(dockable);
    return leaves.get(ref);
  }

  private static JTree getTree(ServerTreeDockable dockable) throws Exception {
    Field treeField = ServerTreeDockable.class.getDeclaredField("tree");
    treeField.setAccessible(true);
    return (JTree) treeField.get(dockable);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
