package cafe.woden.ircclient.ui.servertree.mutation;

import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Removes target tree nodes and publishes precise model removal events. */
public final class ServerTreeTargetNodeRemovalMutator {

  private final Set<DefaultMutableTreeNode> typingActivityNodes;
  private final DefaultTreeModel model;

  public ServerTreeTargetNodeRemovalMutator(
      Set<DefaultMutableTreeNode> typingActivityNodes, DefaultTreeModel model) {
    this.typingActivityNodes = Objects.requireNonNull(typingActivityNodes, "typingActivityNodes");
    this.model = Objects.requireNonNull(model, "model");
  }

  public boolean removeNodes(Set<DefaultMutableTreeNode> nodesToRemove) {
    if (nodesToRemove == null || nodesToRemove.isEmpty()) return false;
    boolean removedAny = false;
    for (DefaultMutableTreeNode node : nodesToRemove) {
      if (node == null) continue;
      typingActivityNodes.remove(node);
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
      if (parent == null) continue;
      int idx = parent.getIndex(node);
      if (idx < 0) continue;
      Object[] removed = new Object[] {node};
      parent.remove(node);
      model.nodesWereRemoved(parent, new int[] {idx}, removed);
      removedAny = true;
    }
    return removedAny;
  }
}
