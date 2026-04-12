package cafe.woden.ircclient.ui.servertree.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeTargetNodeRemovalMutatorTest {

  @Test
  void removeNodesPrunesTypingStateAndRemovesChildrenFromTreeModel() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode parent = new DefaultMutableTreeNode("parent");
    DefaultMutableTreeNode left = new DefaultMutableTreeNode("left");
    DefaultMutableTreeNode right = new DefaultMutableTreeNode("right");
    root.add(parent);
    parent.add(left);
    parent.add(right);

    Set<DefaultMutableTreeNode> typingActivityNodes = new LinkedHashSet<>();
    typingActivityNodes.add(left);
    typingActivityNodes.add(right);

    ServerTreeTargetNodeRemovalMutator mutator = new ServerTreeTargetNodeRemovalMutator();
    ServerTreeTargetNodeRemovalMutator.Context context =
        ServerTreeTargetNodeRemovalMutator.context(typingActivityNodes, new DefaultTreeModel(root));

    boolean removed = mutator.removeNodes(context, Set.of(left, right));

    assertTrue(removed);
    assertTrue(typingActivityNodes.isEmpty());
    assertEquals(0, parent.getChildCount());
  }

  @Test
  void removeNodesReturnsFalseWhenNothingIsRemoved() {
    ServerTreeTargetNodeRemovalMutator mutator = new ServerTreeTargetNodeRemovalMutator();
    ServerTreeTargetNodeRemovalMutator.Context context =
        ServerTreeTargetNodeRemovalMutator.context(
            new LinkedHashSet<>(), new DefaultTreeModel(new DefaultMutableTreeNode("root")));

    assertFalse(mutator.removeNodes(context, Set.of()));
    assertFalse(mutator.removeNodes(context, null));
  }
}
