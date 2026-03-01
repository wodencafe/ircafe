package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.ArrayList;
import javax.swing.tree.DefaultMutableTreeNode;

/** Applies built-in layout and root-sibling ordering to a server subtree. */
public final class ServerTreeLayoutApplier {

  public interface BuiltInNodeResolver {
    DefaultMutableTreeNode resolve(RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind);
  }

  public interface RootSiblingNodeResolver {
    DefaultMutableTreeNode resolve(RuntimeConfigStore.ServerTreeRootSiblingNode nodeKind);
  }

  public interface RootSiblingKindResolver {
    RuntimeConfigStore.ServerTreeRootSiblingNode resolve(DefaultMutableTreeNode node);
  }

  public interface StructureUpdater {
    void nodeStructureChanged(DefaultMutableTreeNode node);
  }

  public int rootBuiltInInsertIndex(
      DefaultMutableTreeNode serverNode,
      DefaultMutableTreeNode otherNode,
      DefaultMutableTreeNode privateMessagesNode,
      DefaultMutableTreeNode channelListNode,
      DefaultMutableTreeNode dccTransfersNode,
      int desiredIndex) {
    if (serverNode == null) return 0;

    int min = 0;
    if (channelListNode != null && channelListNode.getParent() == serverNode) {
      int idx = serverNode.getIndex(channelListNode);
      if (idx >= 0) min = Math.max(min, idx + 1);
    }
    if (dccTransfersNode != null && dccTransfersNode.getParent() == serverNode) {
      int idx = serverNode.getIndex(dccTransfersNode);
      if (idx >= 0) min = Math.max(min, idx + 1);
    }

    int max = serverNode.getChildCount();
    if (otherNode != null && otherNode.getParent() == serverNode) {
      int idx = serverNode.getIndex(otherNode);
      if (idx >= 0) max = Math.min(max, idx);
    } else if (privateMessagesNode != null && privateMessagesNode.getParent() == serverNode) {
      int idx = serverNode.getIndex(privateMessagesNode);
      if (idx >= 0) max = Math.min(max, idx);
    }

    return Math.max(min, Math.min(max, desiredIndex));
  }

  public void applyBuiltInLayout(
      DefaultMutableTreeNode serverNode,
      DefaultMutableTreeNode otherNode,
      DefaultMutableTreeNode privateMessagesNode,
      DefaultMutableTreeNode channelListNode,
      DefaultMutableTreeNode dccTransfersNode,
      RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout,
      BuiltInNodeResolver nodeResolver,
      StructureUpdater structureUpdater) {
    if (serverNode == null
        || otherNode == null
        || nodeResolver == null
        || structureUpdater == null) {
      return;
    }

    RuntimeConfigStore.ServerTreeBuiltInLayout layout =
        ServerTreeBuiltInLayoutCoordinator.normalizeLayout(requestedLayout);
    boolean changed = false;

    if (otherNode.getParent() != serverNode) {
      int pmIdx = privateMessagesNode == null ? -1 : serverNode.getIndex(privateMessagesNode);
      int insertIdx = pmIdx >= 0 ? pmIdx : serverNode.getChildCount();
      serverNode.insert(otherNode, Math.max(0, Math.min(insertIdx, serverNode.getChildCount())));
      changed = true;
    }

    for (RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind : layout.rootOrder()) {
      DefaultMutableTreeNode node = nodeResolver.resolve(nodeKind);
      if (node == null) continue;
      DefaultMutableTreeNode currentParent = (DefaultMutableTreeNode) node.getParent();
      if (currentParent != null) {
        currentParent.remove(node);
        changed = true;
      }
      int idx =
          rootBuiltInInsertIndex(
              serverNode,
              otherNode,
              privateMessagesNode,
              channelListNode,
              dccTransfersNode,
              serverNode.getIndex(otherNode));
      serverNode.insert(node, idx);
      changed = true;
    }

    for (RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind : layout.otherOrder()) {
      DefaultMutableTreeNode node = nodeResolver.resolve(nodeKind);
      if (node == null) continue;
      DefaultMutableTreeNode currentParent = (DefaultMutableTreeNode) node.getParent();
      if (currentParent != null) {
        currentParent.remove(node);
        changed = true;
      }
      otherNode.add(node);
      changed = true;
    }

    if (changed) {
      structureUpdater.nodeStructureChanged(serverNode);
    }
  }

  public void applyRootSiblingOrder(
      DefaultMutableTreeNode serverNode,
      RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder,
      RootSiblingNodeResolver nodeResolver,
      RootSiblingKindResolver kindResolver,
      StructureUpdater structureUpdater) {
    if (serverNode == null
        || nodeResolver == null
        || kindResolver == null
        || structureUpdater == null) {
      return;
    }

    RuntimeConfigStore.ServerTreeRootSiblingOrder order =
        ServerTreeRootSiblingOrderCoordinator.normalizeOrder(requestedOrder);

    ArrayList<DefaultMutableTreeNode> current = new ArrayList<>();
    ArrayList<Integer> slots = new ArrayList<>();
    for (int i = 0; i < serverNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) serverNode.getChildAt(i);
      if (kindResolver.resolve(child) == null) continue;
      current.add(child);
      slots.add(i);
    }
    if (current.size() <= 1) return;

    ArrayList<DefaultMutableTreeNode> desired = new ArrayList<>();
    for (RuntimeConfigStore.ServerTreeRootSiblingNode nodeKind : order.order()) {
      DefaultMutableTreeNode node = nodeResolver.resolve(nodeKind);
      if (node == null || node.getParent() != serverNode || desired.contains(node)) continue;
      desired.add(node);
    }
    for (DefaultMutableTreeNode node : current) {
      if (node == null || desired.contains(node)) continue;
      desired.add(node);
    }

    if (desired.equals(current)) return;

    for (DefaultMutableTreeNode node : current) {
      serverNode.remove(node);
    }
    for (int i = 0; i < desired.size() && i < slots.size(); i++) {
      serverNode.insert(desired.get(i), slots.get(i));
    }
    structureUpdater.nodeStructureChanged(serverNode);
  }
}
