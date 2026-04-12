package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.Set;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeExpansionStateManagerTest {

  @Test
  void restoreExpandedPathsMatchesEquivalentRebuiltNodes() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode("irc");
    DefaultMutableTreeNode appRoot = new DefaultMutableTreeNode("application");
    root.add(ircRoot);
    root.add(appRoot);
    DefaultMutableTreeNode oldServerNode = new DefaultMutableTreeNode("quassel");
    DefaultMutableTreeNode oldNetworkNode =
        new DefaultMutableTreeNode(
            ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera"));
    oldNetworkNode.add(new DefaultMutableTreeNode("channel-list"));
    oldServerNode.add(oldNetworkNode);
    ircRoot.add(oldServerNode);
    JTree tree = new JTree(new DefaultTreeModel(root));
    ServerTreeExpansionStateManager manager = new ServerTreeExpansionStateManager();
    ServerTreeExpansionStateManager.Context context =
        ServerTreeExpansionStateManager.context(tree, root, ircRoot, appRoot);

    TreePath stalePath = new TreePath(new Object[] {root, ircRoot, oldServerNode, oldNetworkNode});
    assertTrue(manager.isPathInCurrentTreeModel(context, stalePath));

    ircRoot.remove(oldServerNode);
    DefaultMutableTreeNode newServerNode = new DefaultMutableTreeNode("quassel");
    DefaultMutableTreeNode newNetworkNode =
        new DefaultMutableTreeNode(
            ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera"));
    newNetworkNode.add(new DefaultMutableTreeNode("channel-list"));
    newServerNode.add(newNetworkNode);
    ircRoot.add(newServerNode);
    ((DefaultTreeModel) tree.getModel()).reload(ircRoot);

    assertTrue(manager.isPathInCurrentTreeModel(context, stalePath));
    manager.restoreExpandedTreePaths(context, Set.of(stalePath));

    TreePath resolved = new TreePath(new Object[] {root, ircRoot, newServerNode, newNetworkNode});
    assertTrue(tree.isExpanded(resolved));
  }

  @Test
  void reportsMissingPathWhenTreeDiffers() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode("irc");
    DefaultMutableTreeNode appRoot = new DefaultMutableTreeNode("application");
    root.add(ircRoot);
    root.add(appRoot);
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("quassel");
    ircRoot.add(serverNode);
    JTree tree = new JTree(new DefaultTreeModel(root));
    ServerTreeExpansionStateManager manager = new ServerTreeExpansionStateManager();
    ServerTreeExpansionStateManager.Context context =
        ServerTreeExpansionStateManager.context(tree, root, ircRoot, appRoot);

    TreePath missingPath =
        new TreePath(
            new Object[] {
              root,
              ircRoot,
              serverNode,
              new DefaultMutableTreeNode(
                  ServerTreeQuasselNetworkNodeData.network("quassel", "oftc", "Oftc"))
            });
    assertFalse(manager.isPathInCurrentTreeModel(context, missingPath));
  }
}
