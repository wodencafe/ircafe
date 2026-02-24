package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.util.TreeNodeReorderPolicy;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Move/close rules for {@link ServerTreeDockable}.
 *
 * <p>Protects server root nodes, the status leaf, and the "Private messages" group node. Only
 * channel leaves (under a server) and PM leaves (under the group) can be moved/closed.
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
    if (nd.ref == null || nd.ref.isStatus() || nd.ref.isUiOnly()) return null;

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
    return !nd.ref.isStatus() && !nd.ref.isUiOnly();
  }

  private int minMovableIndex(boolean parentIsServer, DefaultMutableTreeNode parent) {
    if (!parentIsServer) return 0;
    // Keep fixed leaves at the top of the server node, if present.
    // Today that's: status + UI-only utility leaves + Monitor/Interceptors groups.
    int min = 0;
    int count = parent.getChildCount();
    while (min < count) {
      Object uo = ((DefaultMutableTreeNode) parent.getChildAt(min)).getUserObject();
      if (uo instanceof ServerTreeDockable.NodeData nd) {
        if (nd.ref == null || nd.ref.isStatus() || nd.ref.isUiOnly()) {
          min++;
          continue;
        }
      }
      break;
    }
    return min;
  }

  private int maxMovableIndex(boolean parentIsServer, DefaultMutableTreeNode parent) {
    int count = parent.getChildCount();
    if (count == 0) return -1;

    int idx = count - 1;
    if (parentIsServer) {
      // Keep reserved group nodes at the bottom of the server node.
      while (idx >= 0) {
        DefaultMutableTreeNode tail = (DefaultMutableTreeNode) parent.getChildAt(idx);
        if (isReservedServerTailNode(tail)) {
          idx--;
          continue;
        }
        break;
      }
    }
    return idx;
  }

  private boolean isReservedServerTailNode(DefaultMutableTreeNode node) {
    return isPrivateMessagesGroupNode(node)
        || isSojuNetworksGroupNode(node)
        || isZncNetworksGroupNode(node);
  }

  private boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String s)) return false;
    return s.trim().equalsIgnoreCase("Soju Networks");
  }

  private boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String s)) return false;
    return s.trim().equalsIgnoreCase("ZNC Networks");
  }

  private boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {

    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String s)) return false;
    String label = s.trim();
    return label.equalsIgnoreCase("Private messages") || label.equalsIgnoreCase("Private Messages");
  }
}
