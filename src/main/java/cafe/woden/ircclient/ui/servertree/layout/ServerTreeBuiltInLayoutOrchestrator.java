package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates built-in layout/root-sibling apply and persistence against the tree model. */
public final class ServerTreeBuiltInLayoutOrchestrator {

  public interface Context {
    String normalizeServerId(String serverId);

    ServerNodes serverNodes(String serverId);

    DefaultMutableTreeNode leafNode(TargetRef ref);

    ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId);

    RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
        DefaultMutableTreeNode node);

    void nodeStructureChanged(DefaultMutableTreeNode node);
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
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout) {
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
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder) {
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
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingNode nodeKind) {
    if (serverNodes == null || serverNodes.serverNode == null || nodeKind == null) return null;
    return switch (nodeKind) {
      case CHANNEL_LIST -> context.leafNode(serverNodes.channelListRef);
      case NOTIFICATIONS -> context.leafNode(serverNodes.notificationsRef);
      case OTHER -> serverNodes.otherNode;
      case PRIVATE_MESSAGES -> serverNodes.pmNode;
    };
  }

  private DefaultMutableTreeNode treeNodeForBuiltInLayoutKind(
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind) {
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
