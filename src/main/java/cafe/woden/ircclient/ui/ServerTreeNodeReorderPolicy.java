package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.util.TreeNodeReorderPolicy;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Move/close rules for {@link ServerTreeDockable}.
 *
 * <p>Protects server root nodes, the status leaf, and the "Private messages" group node.
 * Only channel leaves (under a server) and PM leaves (under the group) can be moved/closed.
 */
public final class ServerTreeNodeReorderPolicy implements TreeNodeReorderPolicy {

  private final Predicate<DefaultMutableTreeNode> isServerNode;

  public ServerTreeNodeReorderPolicy(Predicate<DefaultMutableTreeNode> isServerNode) {
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
  }

  @Override
  public MovePlan planMove(DefaultMutableTreeNode node, int dir) {
    if (node == null) return null;
    if (dir == 0) return null;

    // Only allow moving leaf nodes (channels / PMs). Never move root/server/group/status.
    Object uo = node.getUserObject();
    if (!(uo instanceof ServerTreeDockable.NodeData nd)) return null;
    if (nd.ref == null || nd.ref.isStatus()) return null;

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    if (parent == null) return null;

    boolean parentIsServer = isServerNode.test(parent);
    boolean parentIsPmGroup = isPrivateMessagesGroupNode(parent);
    if (!parentIsServer && !parentIsPmGroup) return null;

    int idx = parent.getIndex(node);
    int min = minMovableIndex(parentIsServer, parent);
    int max = maxMovableIndex(parentIsServer, parent);
    if (idx < min || idx > max) return null;

    int next = idx + (dir > 0 ? 1 : -1);
    next = Math.max(min, Math.min(max, next));
    if (next == idx) return null;

    return new MovePlan(parent, idx, next);
  }

  @Override
  public boolean canClose(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof ServerTreeDockable.NodeData nd)) return false;
    if (nd.ref == null) return false;
    return !nd.ref.isStatus();
  }

  private int minMovableIndex(boolean parentIsServer, DefaultMutableTreeNode parent) {
    if (!parentIsServer) return 0;
    // Keep status (index 0) fixed, if present.
    if (parent.getChildCount() > 0) {
      Object first = ((DefaultMutableTreeNode) parent.getChildAt(0)).getUserObject();
      if (first instanceof ServerTreeDockable.NodeData nd && nd.ref != null && nd.ref.isStatus()) {
        return 1;
      }
    }
    return 0;
  }

  private int maxMovableIndex(boolean parentIsServer, DefaultMutableTreeNode parent) {
    int count = parent.getChildCount();
    if (count == 0) return -1;

    int max = count - 1;
    if (parentIsServer) {
      // Keep the "Private messages" group as the last child, if present.
      DefaultMutableTreeNode last = (DefaultMutableTreeNode) parent.getChildAt(count - 1);
      if (isPrivateMessagesGroupNode(last)) {
        max = count - 2;
      }
    }
    return max;
  }

  private boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String s)) return false;
    String label = s.trim();
    return label.equalsIgnoreCase("Private messages") || label.equalsIgnoreCase("Private Messages");
  }
}
