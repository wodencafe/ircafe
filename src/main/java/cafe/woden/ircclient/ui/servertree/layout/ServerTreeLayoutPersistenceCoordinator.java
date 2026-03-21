package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
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
    ServerTreeRootSiblingNode rootSiblingNodeKindForNode(DefaultMutableTreeNode node);

    ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(DefaultMutableTreeNode node);

    ServerTreeRootSiblingOrder currentRootSiblingOrder(String serverId);

    ServerTreeBuiltInLayout currentBuiltInLayout(String serverId);

    void persistRootSiblingOrder(String serverId, ServerTreeRootSiblingOrder order);

    void persistBuiltInLayout(String serverId, ServerTreeBuiltInLayout layout);
  }

  public static Context context(
      Function<DefaultMutableTreeNode, ServerTreeRootSiblingNode> rootSiblingNodeKindForNode,
      Function<DefaultMutableTreeNode, ServerTreeBuiltInLayoutNode> builtInLayoutNodeKindForNode,
      Function<String, ServerTreeRootSiblingOrder> currentRootSiblingOrder,
      Function<String, ServerTreeBuiltInLayout> currentBuiltInLayout,
      BiConsumer<String, ServerTreeRootSiblingOrder> persistRootSiblingOrder,
      BiConsumer<String, ServerTreeBuiltInLayout> persistBuiltInLayout) {
    Objects.requireNonNull(rootSiblingNodeKindForNode, "rootSiblingNodeKindForNode");
    Objects.requireNonNull(builtInLayoutNodeKindForNode, "builtInLayoutNodeKindForNode");
    Objects.requireNonNull(currentRootSiblingOrder, "currentRootSiblingOrder");
    Objects.requireNonNull(currentBuiltInLayout, "currentBuiltInLayout");
    Objects.requireNonNull(persistRootSiblingOrder, "persistRootSiblingOrder");
    Objects.requireNonNull(persistBuiltInLayout, "persistBuiltInLayout");
    return new Context() {
      @Override
      public ServerTreeRootSiblingNode rootSiblingNodeKindForNode(DefaultMutableTreeNode node) {
        return rootSiblingNodeKindForNode.apply(node);
      }

      @Override
      public ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(DefaultMutableTreeNode node) {
        return builtInLayoutNodeKindForNode.apply(node);
      }

      @Override
      public ServerTreeRootSiblingOrder currentRootSiblingOrder(String serverId) {
        return currentRootSiblingOrder.apply(serverId);
      }

      @Override
      public ServerTreeBuiltInLayout currentBuiltInLayout(String serverId) {
        return currentBuiltInLayout.apply(serverId);
      }

      @Override
      public void persistRootSiblingOrder(String serverId, ServerTreeRootSiblingOrder order) {
        persistRootSiblingOrder.accept(serverId, order);
      }

      @Override
      public void persistBuiltInLayout(String serverId, ServerTreeBuiltInLayout layout) {
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

    ArrayList<ServerTreeRootSiblingNode> order = new ArrayList<>();
    EnumSet<ServerTreeRootSiblingNode> seen = EnumSet.noneOf(ServerTreeRootSiblingNode.class);

    for (int i = 0; i < serverNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) serverNode.getChildAt(i);
      ServerTreeRootSiblingNode nodeKind = context.rootSiblingNodeKindForNode(child);
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      order.add(nodeKind);
      seen.add(nodeKind);
    }

    ServerTreeRootSiblingOrder current = context.currentRootSiblingOrder(sid);
    for (ServerTreeRootSiblingNode nodeKind : current.order()) {
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      order.add(nodeKind);
      seen.add(nodeKind);
    }

    ServerTreeRootSiblingOrder next =
        ServerTreeRootSiblingOrderCoordinator.normalizeOrder(
            new ServerTreeRootSiblingOrder(List.copyOf(order)));
    context.persistRootSiblingOrder(sid, next);
  }

  public void persistBuiltInLayoutFromTree(
      String serverId, DefaultMutableTreeNode serverNode, DefaultMutableTreeNode otherNode) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || serverNode == null || otherNode == null) return;

    ArrayList<ServerTreeBuiltInLayoutNode> rootOrder = new ArrayList<>();
    ArrayList<ServerTreeBuiltInLayoutNode> otherOrder = new ArrayList<>();
    EnumSet<ServerTreeBuiltInLayoutNode> seen = EnumSet.noneOf(ServerTreeBuiltInLayoutNode.class);

    for (int i = 0; i < serverNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) serverNode.getChildAt(i);
      ServerTreeBuiltInLayoutNode nodeKind = context.builtInLayoutNodeKindForNode(child);
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      rootOrder.add(nodeKind);
      seen.add(nodeKind);
    }

    for (int i = 0; i < otherNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) otherNode.getChildAt(i);
      ServerTreeBuiltInLayoutNode nodeKind = context.builtInLayoutNodeKindForNode(child);
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      otherOrder.add(nodeKind);
      seen.add(nodeKind);
    }

    ServerTreeBuiltInLayout current = context.currentBuiltInLayout(sid);
    for (ServerTreeBuiltInLayoutNode nodeKind : current.rootOrder()) {
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      rootOrder.add(nodeKind);
      seen.add(nodeKind);
    }
    for (ServerTreeBuiltInLayoutNode nodeKind : current.otherOrder()) {
      if (nodeKind == null || seen.contains(nodeKind)) continue;
      otherOrder.add(nodeKind);
      seen.add(nodeKind);
    }

    ServerTreeBuiltInLayout next =
        ServerTreeBuiltInLayoutCoordinator.normalizeLayout(
            new ServerTreeBuiltInLayout(List.copyOf(rootOrder), List.copyOf(otherOrder)));
    context.persistBuiltInLayout(sid, next);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
