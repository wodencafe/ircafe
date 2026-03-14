package cafe.woden.ircclient.ui.servertree.mutation;

import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Removes target tree nodes and publishes precise model removal events. */
@RequiredArgsConstructor
public final class ServerTreeTargetNodeRemovalMutator {

  @NonNull private final Set<DefaultMutableTreeNode> typingActivityNodes;
  @NonNull private final DefaultTreeModel model;

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
