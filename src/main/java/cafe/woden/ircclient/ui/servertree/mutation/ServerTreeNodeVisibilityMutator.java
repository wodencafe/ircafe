package cafe.woden.ircclient.ui.servertree.mutation;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Applies visibility toggles for leaves/groups while keeping the tree model in sync. */
public final class ServerTreeNodeVisibilityMutator {

  private final DefaultTreeModel model;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Set<DefaultMutableTreeNode> typingActivityNodes;

  public ServerTreeNodeVisibilityMutator(
      DefaultTreeModel model,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Set<DefaultMutableTreeNode> typingActivityNodes) {
    this.model = Objects.requireNonNull(model, "model");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.typingActivityNodes = Objects.requireNonNull(typingActivityNodes, "typingActivityNodes");
  }

  public boolean ensureLeafVisible(
      DefaultMutableTreeNode serverNode,
      TargetRef ref,
      String label,
      boolean visible,
      boolean attachWhenVisible,
      int insertIndex) {
    if (serverNode == null || ref == null) return false;

    DefaultMutableTreeNode existing = leaves.get(ref);
    if (!visible) {
      if (existing == null) return false;
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode) existing.getParent();
      int idx = parent == null ? -1 : parent.getIndex(existing);
      leaves.remove(ref);
      typingActivityNodes.remove(existing);
      if (parent != null) {
        Object[] removed = new Object[] {existing};
        if (idx < 0) {
          parent.remove(existing);
          model.nodeStructureChanged(parent);
        } else {
          parent.remove(existing);
          model.nodesWereRemoved(parent, new int[] {idx}, removed);
        }
      }
      return true;
    }

    if (existing != null) return false;
    DefaultMutableTreeNode leaf =
        new DefaultMutableTreeNode(new ServerTreeNodeData(ref, Objects.toString(label, "")));
    leaves.put(ref, leaf);
    if (attachWhenVisible) {
      int idx = Math.max(0, Math.min(insertIndex, serverNode.getChildCount()));
      serverNode.insert(leaf, idx);
      model.nodesWereInserted(serverNode, new int[] {idx});
    }
    return true;
  }

  public boolean ensureGroupVisible(
      DefaultMutableTreeNode serverNode,
      DefaultMutableTreeNode otherNode,
      DefaultMutableTreeNode groupNode,
      boolean visible) {
    if (serverNode == null || otherNode == null || groupNode == null) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) groupNode.getParent();

    if (!visible) {
      if (parent != serverNode && parent != otherNode) return false;
      int idx = parent.getIndex(groupNode);
      if (idx < 0) return false;
      parent.remove(groupNode);
      model.nodesWereRemoved(parent, new int[] {idx}, new Object[] {groupNode});
      return true;
    }

    return parent != serverNode && parent != otherNode;
  }
}
