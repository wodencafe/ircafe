package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeDockableWeechatFiltersNodeTest {

  @Test
  void weechatFiltersLeafIsPlacedUnderOtherGroup() throws Exception {
    onEdt(
        () -> {
          try {
            ServerTreeDockable dockable = newDockable();
            invokeAddServerRoot(dockable, "libera");

            TargetRef channelListRef = TargetRef.channelList("libera");
            TargetRef filtersRef = TargetRef.weechatFilters("libera");

            DefaultMutableTreeNode channelListNode = findLeafNode(dockable, channelListRef);
            DefaultMutableTreeNode filtersNode = findLeafNode(dockable, filtersRef);

            assertNotNull(channelListNode);
            assertNotNull(filtersNode);

            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) filtersNode.getParent();
            assertTrue(isOtherGroup(parent));
            assertEquals(channelListNode.getParent(), parent.getParent());

            TargetRef ignoresRef = TargetRef.ignores("libera");
            DefaultMutableTreeNode ignoresNode = findLeafNode(dockable, ignoresRef);
            assertNotNull(ignoresNode);
            int filtersIdx = parent.getIndex(filtersNode);
            int ignoresIdx = parent.getIndex(ignoresNode);
            assertEquals(filtersIdx + 1, ignoresIdx);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static boolean isOtherGroup(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof ServerTreeDockable.NodeData nd)) return false;
    return nd.ref == null && "Other".equals(nd.label);
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

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
