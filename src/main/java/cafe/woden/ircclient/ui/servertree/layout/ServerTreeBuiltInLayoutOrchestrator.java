package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates built-in layout/root-sibling apply and persistence against the tree model. */
public final class ServerTreeBuiltInLayoutOrchestrator {

  public interface Context {
    String normalizeServerId(String serverId);

    ServerNodes serverNodes(String serverId);

    DefaultMutableTreeNode leafNode(TargetRef ref);

    ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId);

    ServerTreeRootSiblingNode rootSiblingNodeKindForNode(DefaultMutableTreeNode node);

    void nodeStructureChanged(DefaultMutableTreeNode node);
  }

  public static Context context(
      Function<String, String> normalizeServerId,
      Function<String, ServerNodes> serverNodes,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Function<DefaultMutableTreeNode, ServerTreeRootSiblingNode> rootSiblingNodeKindForNode,
      Consumer<DefaultMutableTreeNode> nodeStructureChanged) {
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(serverNodes, "serverNodes");
    Objects.requireNonNull(leafNode, "leafNode");
    Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    Objects.requireNonNull(rootSiblingNodeKindForNode, "rootSiblingNodeKindForNode");
    Objects.requireNonNull(nodeStructureChanged, "nodeStructureChanged");
    return new Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return normalizeServerId.apply(serverId);
      }

      @Override
      public ServerNodes serverNodes(String serverId) {
        return serverNodes.apply(serverId);
      }

      @Override
      public DefaultMutableTreeNode leafNode(TargetRef ref) {
        return leafNode.apply(ref);
      }

      @Override
      public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
        return builtInNodesVisibility.apply(serverId);
      }

      @Override
      public ServerTreeRootSiblingNode rootSiblingNodeKindForNode(DefaultMutableTreeNode node) {
        return rootSiblingNodeKindForNode.apply(node);
      }

      @Override
      public void nodeStructureChanged(DefaultMutableTreeNode node) {
        nodeStructureChanged.accept(node);
      }
    };
  }

  private final ServerTreeLayoutApplier layoutApplier;
  private final ServerTreeLayoutPersistenceCoordinator layoutPersistenceCoordinator;
  private final Context context;

  public ServerTreeBuiltInLayoutOrchestrator(
      ServerTreeLayoutApplier layoutApplier,
      ServerTreeLayoutPersistenceCoordinator layoutPersistenceCoordinator,
      Context context) {
    this.layoutApplier = Objects.requireNonNull(layoutApplier, "layoutApplier");
    this.layoutPersistenceCoordinator =
        Objects.requireNonNull(layoutPersistenceCoordinator, "layoutPersistenceCoordinator");
    this.context = Objects.requireNonNull(context, "context");
  }

  public int rootBuiltInInsertIndex(ServerNodes serverNodes, int desiredIndex) {
    if (serverNodes == null) return 0;
    return layoutApplier.rootBuiltInInsertIndex(
        serverNodes.serverNode,
        serverNodes.otherNode,
        serverNodes.pmNode,
        context.leafNode(serverNodes.channelListRef),
        context.leafNode(serverNodes.dccTransfersRef),
        desiredIndex);
  }

  public void applyBuiltInLayoutToTree(
      ServerNodes serverNodes, ServerTreeBuiltInLayout requestedLayout) {
    if (serverNodes == null) return;
    layoutApplier.applyBuiltInLayout(
        serverNodes.serverNode,
        serverNodes.otherNode,
        serverNodes.pmNode,
        context.leafNode(serverNodes.channelListRef),
        context.leafNode(serverNodes.dccTransfersRef),
        requestedLayout,
        nodeKind -> treeNodeForBuiltInLayoutKind(serverNodes, nodeKind),
        context::nodeStructureChanged);
  }

  public void applyRootSiblingOrderToTree(
      ServerNodes serverNodes, ServerTreeRootSiblingOrder requestedOrder) {
    if (serverNodes == null) return;
    layoutApplier.applyRootSiblingOrder(
        serverNodes.serverNode,
        requestedOrder,
        nodeKind -> treeNodeForRootSiblingKind(serverNodes, nodeKind),
        context::rootSiblingNodeKindForNode,
        context::nodeStructureChanged);
  }

  public void persistRootSiblingOrderFromTree(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerNodes serverNodes = context.serverNodes(sid);
    if (serverNodes == null || serverNodes.serverNode == null) return;
    layoutPersistenceCoordinator.persistRootSiblingOrderFromTree(sid, serverNodes.serverNode);
  }

  public void persistBuiltInLayoutFromTree(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerNodes serverNodes = context.serverNodes(sid);
    if (serverNodes == null || serverNodes.serverNode == null || serverNodes.otherNode == null) {
      return;
    }
    layoutPersistenceCoordinator.persistBuiltInLayoutFromTree(
        sid, serverNodes.serverNode, serverNodes.otherNode);
  }

  private DefaultMutableTreeNode treeNodeForRootSiblingKind(
      ServerNodes serverNodes, ServerTreeRootSiblingNode nodeKind) {
    if (serverNodes == null || serverNodes.serverNode == null || nodeKind == null) return null;
    return switch (nodeKind) {
      case CHANNEL_LIST -> context.leafNode(serverNodes.channelListRef);
      case NOTIFICATIONS -> context.leafNode(serverNodes.notificationsRef);
      case OTHER -> serverNodes.otherNode;
      case PRIVATE_MESSAGES -> serverNodes.pmNode;
    };
  }

  private DefaultMutableTreeNode treeNodeForBuiltInLayoutKind(
      ServerNodes serverNodes, ServerTreeBuiltInLayoutNode nodeKind) {
    if (serverNodes == null || nodeKind == null) return null;
    String sid = context.normalizeServerId(serverNodes.statusRef.serverId());
    ServerBuiltInNodesVisibility visibility = context.builtInNodesVisibility(sid);
    return switch (nodeKind) {
      case SERVER -> visibility.server() ? context.leafNode(serverNodes.statusRef) : null;
      case NOTIFICATIONS ->
          visibility.notifications() ? context.leafNode(serverNodes.notificationsRef) : null;
      case LOG_VIEWER -> visibility.logViewer() ? context.leafNode(serverNodes.logViewerRef) : null;
      case FILTERS -> context.leafNode(serverNodes.weechatFiltersRef);
      case IGNORES -> context.leafNode(serverNodes.ignoresRef);
      case MONITOR -> visibility.monitor() ? serverNodes.monitorNode : null;
      case INTERCEPTORS -> visibility.interceptors() ? serverNodes.interceptorsNode : null;
    };
  }
}
