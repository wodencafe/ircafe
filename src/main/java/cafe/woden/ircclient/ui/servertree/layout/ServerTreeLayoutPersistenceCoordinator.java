package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Persists per-server built-in layout and root-sibling order from the current tree structure. */
public final class ServerTreeLayoutPersistenceCoordinator {

  public interface Context {
    RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
        DefaultMutableTreeNode node);

    RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
        DefaultMutableTreeNode node);

    RuntimeConfigStore.ServerTreeRootSiblingOrder currentRootSiblingOrder(String serverId);

    RuntimeConfigStore.ServerTreeBuiltInLayout currentBuiltInLayout(String serverId);

    void persistRootSiblingOrder(
        String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order);

    void persistBuiltInLayout(String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout);
  }

  public static Context context(
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
          rootSiblingNodeKindForNode,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
          builtInLayoutNodeKindForNode,
      Function<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> currentRootSiblingOrder,
      Function<String, RuntimeConfigStore.ServerTreeBuiltInLayout> currentBuiltInLayout,
      BiConsumer<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> persistRootSiblingOrder,
      BiConsumer<String, RuntimeConfigStore.ServerTreeBuiltInLayout> persistBuiltInLayout) {
    Objects.requireNonNull(rootSiblingNodeKindForNode, "rootSiblingNodeKindForNode");
    Objects.requireNonNull(builtInLayoutNodeKindForNode, "builtInLayoutNodeKindForNode");
    Objects.requireNonNull(currentRootSiblingOrder, "currentRootSiblingOrder");
    Objects.requireNonNull(currentBuiltInLayout, "currentBuiltInLayout");
    Objects.requireNonNull(persistRootSiblingOrder, "persistRootSiblingOrder");
    Objects.requireNonNull(persistBuiltInLayout, "persistBuiltInLayout");
    return new Context() {
      @Override
      public RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
          DefaultMutableTreeNode node) {
        return rootSiblingNodeKindForNode.apply(node);
      }

      @Override
      public RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
          DefaultMutableTreeNode node) {
        return builtInLayoutNodeKindForNode.apply(node);
      }

      @Override
      public RuntimeConfigStore.ServerTreeRootSiblingOrder currentRootSiblingOrder(
          String serverId) {
        return currentRootSiblingOrder.apply(serverId);
      }

      @Override
      public RuntimeConfigStore.ServerTreeBuiltInLayout currentBuiltInLayout(String serverId) {
        return currentBuiltInLayout.apply(serverId);
      }

      @Override
      public void persistRootSiblingOrder(
          String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
        persistRootSiblingOrder.accept(serverId, order);
      }

      @Override
      public void persistBuiltInLayout(
          String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
        persistBuiltInLayout.accept(serverId, layout);
      }
    };
  }

  private final Context context;

  public ServerTreeLayoutPersistenceCoordinator(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public void persistRootSiblingOrderFromTree(String serverId, DefaultMutableTreeNode serverNode) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || serverNode == null) return;

    ArrayList<RuntimeConfigStore.ServerTreeRootSiblingNode> order = new ArrayList<>();
    EnumSet<RuntimeConfigStore.ServerTreeRootSiblingNode> seen =
        EnumSet.noneOf(RuntimeConfigStore.ServerTreeRootSiblingNode.class);

    for (int i = 0; i < serverNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) serverNode.getChildAt(i);
      RuntimeConfigStore.ServerTreeRootSiblingNode nodeKind =
          context.rootSiblingNodeKindForNode(child);
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      order.add(nodeKind);
      seen.add(nodeKind);
    }

    RuntimeConfigStore.ServerTreeRootSiblingOrder current = context.currentRootSiblingOrder(sid);
    for (RuntimeConfigStore.ServerTreeRootSiblingNode nodeKind : current.order()) {
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      order.add(nodeKind);
      seen.add(nodeKind);
    }

    RuntimeConfigStore.ServerTreeRootSiblingOrder next =
        ServerTreeRootSiblingOrderCoordinator.normalizeOrder(
            new RuntimeConfigStore.ServerTreeRootSiblingOrder(List.copyOf(order)));
    context.persistRootSiblingOrder(sid, next);
  }

  public void persistBuiltInLayoutFromTree(
      String serverId, DefaultMutableTreeNode serverNode, DefaultMutableTreeNode otherNode) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || serverNode == null || otherNode == null) return;

    ArrayList<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> rootOrder = new ArrayList<>();
    ArrayList<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> otherOrder = new ArrayList<>();
    EnumSet<RuntimeConfigStore.ServerTreeBuiltInLayoutNode> seen =
        EnumSet.noneOf(RuntimeConfigStore.ServerTreeBuiltInLayoutNode.class);

    for (int i = 0; i < serverNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) serverNode.getChildAt(i);
      RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind =
          context.builtInLayoutNodeKindForNode(child);
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      rootOrder.add(nodeKind);
      seen.add(nodeKind);
    }

    for (int i = 0; i < otherNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) otherNode.getChildAt(i);
      RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind =
          context.builtInLayoutNodeKindForNode(child);
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      otherOrder.add(nodeKind);
      seen.add(nodeKind);
    }

    RuntimeConfigStore.ServerTreeBuiltInLayout current = context.currentBuiltInLayout(sid);
    for (RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind : current.rootOrder()) {
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      rootOrder.add(nodeKind);
      seen.add(nodeKind);
    }
    for (RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind : current.otherOrder()) {
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      otherOrder.add(nodeKind);
      seen.add(nodeKind);
    }

    RuntimeConfigStore.ServerTreeBuiltInLayout next =
        ServerTreeBuiltInLayoutCoordinator.normalizeLayout(
            new RuntimeConfigStore.ServerTreeBuiltInLayout(
                List.copyOf(rootOrder), List.copyOf(otherOrder)));
    context.persistBuiltInLayout(sid, next);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
