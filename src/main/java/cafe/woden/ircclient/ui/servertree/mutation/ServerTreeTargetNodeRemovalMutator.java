package cafe.woden.ircclient.ui.servertree.mutation;

import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.springframework.stereotype.Component;

/** Removes target tree nodes and publishes precise model removal events. */
@Component
public final class ServerTreeTargetNodeRemovalMutator {

  public interface Context {
    void removeTypingActivityNode(DefaultMutableTreeNode node);

    void removeNode(DefaultMutableTreeNode parent, DefaultMutableTreeNode node, int index);
  }

  public static Context context(
      Set<DefaultMutableTreeNode> typingActivityNodes, DefaultTreeModel model) {
    java.util.Objects.requireNonNull(typingActivityNodes, "typingActivityNodes");
    java.util.Objects.requireNonNull(model, "model");
    return new Context() {
      @Override
      public void removeTypingActivityNode(DefaultMutableTreeNode node) {
        typingActivityNodes.remove(node);
      }

      @Override
      public void removeNode(
          DefaultMutableTreeNode parent, DefaultMutableTreeNode node, int index) {
        Object[] removed = new Object[] {node};
        parent.remove(node);
        model.nodesWereRemoved(parent, new int[] {index}, removed);
      }
    };
  }

  public boolean removeNodes(Context context, Set<DefaultMutableTreeNode> nodesToRemove) {
    Context in = java.util.Objects.requireNonNull(context, "context");
    if (nodesToRemove == null || nodesToRemove.isEmpty()) return false;
    boolean removedAny = false;
    for (DefaultMutableTreeNode node : nodesToRemove) {
      if (node == null) continue;
      in.removeTypingActivityNode(node);
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
      if (parent == null) continue;
      int idx = parent.getIndex(node);
      if (idx < 0) continue;
      in.removeNode(parent, node, idx);
      removedAny = true;
    }
    return removedAny;
  }
}
