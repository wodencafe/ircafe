package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.util.TreeNodeReorderPolicy;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Move/close rules for the server tree UI.
 *
 * <p>Protects server root nodes and reserved group boundaries while allowing:
 *
 * <ul>
 *   <li>channel/PM leaf reordering within their existing parent region
 *   <li>movable built-in server nodes to reorder within server-root or Other-group regions
 * </ul>
 */
public final class ServerTreeNodeReorderPolicy implements TreeNodeReorderPolicy {

  private final Predicate<DefaultMutableTreeNode> isServerNode;
  private final Predicate<DefaultMutableTreeNode> isChannelListNode;
  private final Predicate<TargetRef> isChannelPinned;
  private final Function<DefaultMutableTreeNode, TargetRef> targetRefForNode;
  private final Function<DefaultMutableTreeNode, String> nodeLabelForNode;

  public ServerTreeNodeReorderPolicy(
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isChannelListNode,
      Predicate<TargetRef> isChannelPinned,
      Function<DefaultMutableTreeNode, TargetRef> targetRefForNode,
      Function<DefaultMutableTreeNode, String> nodeLabelForNode) {
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
    this.isChannelListNode = Objects.requireNonNull(isChannelListNode, "isChannelListNode");
    this.isChannelPinned = Objects.requireNonNull(isChannelPinned, "isChannelPinned");
    this.targetRefForNode = Objects.requireNonNull(targetRefForNode, "targetRefForNode");
    this.nodeLabelForNode = Objects.requireNonNull(nodeLabelForNode, "nodeLabelForNode");
  }

  @Override
  public MovePlan planMove(DefaultMutableTreeNode node, int dir) {
    if (node == null) return null;
    if (dir == 0) return null;

    // Allow moving the movable built-in nodes within their current parent region.
    TargetRef nodeRef = targetRefForNode.apply(node);
    String nodeLabel = normalizeLabel(nodeLabelForNode.apply(node));
    if (nodeRef == null && nodeLabel.isBlank()) return null;

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    if (parent == null) return null;

    boolean parentIsServer = isServerNode.test(parent);
    boolean parentIsChannelList = isChannelListNode.test(parent);
    boolean parentIsPmGroup = isPrivateMessagesGroupNode(parent);
    boolean parentIsOther = isOtherGroupNode(parent);

    if (isMovableBuiltInNode(nodeRef, nodeLabel)) {
      if (!parentIsServer && !parentIsOther) return null;

      int idx = parent.getIndex(node);
      int min = parentIsOther ? 0 : minMovableBuiltInServerIndex(parent);
      int max = parentIsOther ? (parent.getChildCount() - 1) : maxMovableBuiltInServerIndex(parent);
      if (idx < min || idx > max) return null;

      int next = idx + (dir > 0 ? 1 : -1);
      next = Math.max(min, Math.min(max, next));
      if (next == idx) return null;
      return new MovePlan(parent, idx, next);
    }

    // Channel/PM move policy (existing behavior).
    if (nodeRef == null || nodeRef.isStatus() || nodeRef.isUiOnly()) return null;
    if (!parentIsServer && !parentIsPmGroup && !parentIsChannelList) return null;

    int idx = parent.getIndex(node);
    int min = minMovableIndex(parentIsServer, parent);
    int max = maxMovableIndex(parentIsServer, parent);
    if (idx < min || idx > max) return null;

    int step = dir > 0 ? 1 : -1;
    int next = idx + step;
    if (parentIsChannelList && nodeRef.isChannel()) {
      int firstUnpinned = firstUnpinnedChannelIndex(parent);
      boolean pinned = isChannelPinned.test(nodeRef);
      if (pinned && next >= firstUnpinned) return null;
      if (!pinned && next < firstUnpinned) return null;
    }
    next = Math.max(min, Math.min(max, next));
    if (next == idx) return null;

    return new MovePlan(parent, idx, next);
  }

  @Override
  public boolean canClose(DefaultMutableTreeNode node) {
    if (node == null) return false;
    TargetRef ref = targetRefForNode.apply(node);
    if (ref == null) return false;
    return !ref.isStatus() && !ref.isUiOnly();
  }

  private int minMovableIndex(boolean parentIsServer, DefaultMutableTreeNode parent) {
    if (!parentIsServer) return 0;
    // Keep fixed leaves at the top of the server node, if present.
    // Today that's: status + UI-only utility leaves + Monitor/Interceptors groups.
    int min = 0;
    int count = parent.getChildCount();
    while (min < count) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(min);
      TargetRef ref = targetRefForNode.apply(child);
      if (ref == null || ref.isStatus() || ref.isUiOnly()) {
        min++;
        continue;
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

  private int firstUnpinnedChannelIndex(DefaultMutableTreeNode channelListNode) {
    if (channelListNode == null) return 0;
    int count = channelListNode.getChildCount();
    for (int i = 0; i < count; i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
      TargetRef ref = targetRefForNode.apply(child);
      if (ref == null || !ref.isChannel()) continue;
      if (!isChannelPinned.test(ref)) return i;
    }
    return count;
  }

  private int minMovableBuiltInServerIndex(DefaultMutableTreeNode parent) {
    int min = 0;
    int count = parent.getChildCount();
    while (min < count) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(min);
      if (isFixedServerHeadNode(child)) {
        min++;
        continue;
      }
      break;
    }
    return min;
  }

  private int maxMovableBuiltInServerIndex(DefaultMutableTreeNode parent) {
    int count = parent.getChildCount();
    if (count == 0) return -1;

    int otherIdx = indexOfOtherGroup(parent);
    if (otherIdx >= 0) {
      return Math.max(-1, otherIdx - 1);
    }

    int idx = count - 1;
    while (idx >= 0) {
      DefaultMutableTreeNode tail = (DefaultMutableTreeNode) parent.getChildAt(idx);
      if (isReservedServerTailNode(tail)) {
        idx--;
        continue;
      }
      break;
    }
    return idx;
  }

  private int indexOfOtherGroup(DefaultMutableTreeNode parent) {
    int count = parent.getChildCount();
    for (int i = 0; i < count; i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
      if (isOtherGroupNode(child)) return i;
    }
    return -1;
  }

  private boolean isFixedServerHeadNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    TargetRef ref = targetRefForNode.apply(node);
    if (ref == null) return false;
    return ref.isChannelList() || ref.isDccTransfers();
  }

  private boolean isMovableBuiltInNode(TargetRef ref, String label) {
    if (ref != null) {
      return ref.isStatus()
          || ref.isNotifications()
          || ref.isLogViewer()
          || ref.isWeechatFilters()
          || ref.isIgnores();
    }
    return label.equalsIgnoreCase("Monitor") || label.equalsIgnoreCase("Interceptors");
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

  private boolean isOtherGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (uo instanceof String s) {
      return s.trim().equalsIgnoreCase("Other");
    }
    TargetRef ref = targetRefForNode.apply(node);
    if (ref != null) return false;
    String label = normalizeLabel(nodeLabelForNode.apply(node));
    return label.equalsIgnoreCase("Other");
  }

  private static String normalizeLabel(String value) {
    return Objects.toString(value, "").trim();
  }
}
