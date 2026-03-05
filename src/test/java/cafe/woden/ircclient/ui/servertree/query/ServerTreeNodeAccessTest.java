package cafe.woden.ircclient.ui.servertree.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeNodeAccessTest {

  @Test
  void identifiesRootAndChannelListNodes() {
    TreeFixture fixture = TreeFixture.create();

    assertTrue(fixture.access.isIrcRootNode(fixture.ircRoot));
    assertTrue(fixture.access.isApplicationRootNode(fixture.applicationRoot));
    assertTrue(fixture.access.isRootServerNode(fixture.serverNode));
    assertTrue(fixture.access.isChannelListLeafNode(fixture.channelListNode));
    assertFalse(fixture.access.isChannelListLeafNode(fixture.serverNode));
  }

  @Test
  void readsSelectionAndValidSelectionState() {
    TreeFixture fixture = TreeFixture.create();
    TreePath selectedPath = new TreePath(fixture.channelListNode.getPath());
    fixture.tree.setSelectionPath(selectedPath);

    assertEquals(TargetRef.channelList("libera"), fixture.access.selectedTargetRef());
    assertTrue(fixture.access.hasValidTreeSelection());
  }

  private static final class TreeFixture {
    private final JTree tree;
    private final DefaultMutableTreeNode ircRoot;
    private final DefaultMutableTreeNode applicationRoot;
    private final DefaultMutableTreeNode serverNode;
    private final DefaultMutableTreeNode channelListNode;
    private final ServerTreeNodeAccess access;

    private TreeFixture(
        JTree tree,
        DefaultMutableTreeNode ircRoot,
        DefaultMutableTreeNode applicationRoot,
        DefaultMutableTreeNode serverNode,
        DefaultMutableTreeNode channelListNode,
        ServerTreeNodeAccess access) {
      this.tree = tree;
      this.ircRoot = ircRoot;
      this.applicationRoot = applicationRoot;
      this.serverNode = serverNode;
      this.channelListNode = channelListNode;
      this.access = access;
    }

    private static TreeFixture create() {
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("(root)");
      DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode("IRC");
      DefaultMutableTreeNode applicationRoot = new DefaultMutableTreeNode("Application");
      root.add(ircRoot);
      root.add(applicationRoot);

      DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("libera");
      ircRoot.add(serverNode);
      TargetRef channelListRef = TargetRef.channelList("libera");
      DefaultMutableTreeNode channelListNode =
          new DefaultMutableTreeNode(new ServerTreeNodeData(channelListRef, "Channel List"));
      serverNode.add(channelListNode);

      JTree tree = new JTree(new DefaultTreeModel(root));
      ServerTreeNodeAccess access =
          new ServerTreeNodeAccess(
              tree,
              root,
              ircRoot,
              applicationRoot,
              node -> node != null && node.getParent() == ircRoot);
      return new TreeFixture(tree, ircRoot, applicationRoot, serverNode, channelListNode, access);
    }
  }
}
