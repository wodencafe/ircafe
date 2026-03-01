package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Coordinates adding/removing server tree targets while delegating persistence/layout details via
 * context collaborators.
 */
public final class ServerTreeTargetLifecycleCoordinator {

  public interface Context {
    boolean applicationRootVisible();

    void setApplicationRootVisible(boolean visible);

    String applicationLeafLabel(TargetRef ref);

    void addApplicationLeaf(TargetRef ref, String label);

    void nodeStructureChangedForApplicationRoot();

    boolean dccTransfersNodesVisible();

    void setDccTransfersNodesVisible(boolean visible);

    ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId);

    ServerNodes addServerRoot(String serverId);

    RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForRef(TargetRef ref);

    RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout(String serverId);

    RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder(String serverId);

    DefaultMutableTreeNode ensureChannelListNode(ServerNodes serverNodes);

    void applyBuiltInLayoutToTree(
        ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayout layout);

    void applyRootSiblingOrderToTree(
        ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder order);

    void persistBuiltInLayoutFromTree(String serverId);

    boolean isPrivateMessageTarget(TargetRef ref);

    boolean shouldPersistPrivateMessageList();

    void rememberPrivateMessageTarget(String serverId, String target);

    void ensureChannelKnownInConfig(TargetRef ref);

    void sortChannelsUnderChannelList(String serverId);

    void emitManagedChannelsChanged(String serverId);

    String normalizeServerId(String serverId);

    void expandPath(DefaultMutableTreeNode parentNode);

    void reloadRoot();
  }

  private final Map<String, ServerNodes> servers;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final ServerCatalog serverCatalog;
  private final ServerTreeEnsureNodeParentResolver ensureNodeParentResolver;
  private final ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter;
  private final ServerTreeTargetNodePolicy targetNodePolicy;
  private final ServerTreeTargetSnapshotProvider targetSnapshotProvider;
  private final ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator;
  private final ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator;
  private final Context context;

  public ServerTreeTargetLifecycleCoordinator(
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      ServerCatalog serverCatalog,
      ServerTreeEnsureNodeParentResolver ensureNodeParentResolver,
      ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter,
      ServerTreeTargetNodePolicy targetNodePolicy,
      ServerTreeTargetSnapshotProvider targetSnapshotProvider,
      ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator,
      ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator,
      Context context) {
    this.servers = Objects.requireNonNull(servers, "servers");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.serverCatalog = serverCatalog;
    this.ensureNodeParentResolver =
        Objects.requireNonNull(ensureNodeParentResolver, "ensureNodeParentResolver");
    this.ensureNodeLeafInserter =
        Objects.requireNonNull(ensureNodeLeafInserter, "ensureNodeLeafInserter");
    this.targetNodePolicy = Objects.requireNonNull(targetNodePolicy, "targetNodePolicy");
    this.targetSnapshotProvider =
        Objects.requireNonNull(targetSnapshotProvider, "targetSnapshotProvider");
    this.targetRemovalStateCoordinator =
        Objects.requireNonNull(targetRemovalStateCoordinator, "targetRemovalStateCoordinator");
    this.targetNodeRemovalMutator =
        Objects.requireNonNull(targetNodeRemovalMutator, "targetNodeRemovalMutator");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void ensureNode(TargetRef ref) {
    Objects.requireNonNull(ref, "ref");
    if (ref.isApplicationUi()) {
      ensureApplicationLeaf(ref);
      return;
    }
    if (ref.isDccTransfers() && !context.dccTransfersNodesVisible()) {
      context.setDccTransfersNodesVisible(true);
    }
    if (ref.isMonitorGroup() || ref.isInterceptorsGroup()) {
      ensureServerForBuiltInGroup(ref);
      return;
    }

    ServerBuiltInNodesVisibility vis = context.builtInNodesVisibility(ref.serverId());
    if (ref.isStatus() && !vis.server()) return;
    if (ref.isNotifications() && !vis.notifications()) return;
    if (ref.isLogViewer() && !vis.logViewer()) return;
    if (ref.isMonitorGroup() && !vis.monitor()) return;
    if ((ref.isInterceptorsGroup() || ref.isInterceptor()) && !vis.interceptors()) return;
    if (leaves.containsKey(ref)) return;

    String serverId = ref.serverId();
    ServerNodes sn = servers.get(serverId);
    if (sn == null) {
      if (!canCreateMissingServerRoot(serverId)) {
        return;
      }
      sn = context.addServerRoot(serverId);
      if (sn == null) return;
    }

    RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInKind =
        context.builtInLayoutNodeKindForRef(ref);
    RuntimeConfigStore.ServerTreeBuiltInLayout layout = context.builtInLayout(serverId);
    ServerNodes resolvedServerNodes = sn;
    DefaultMutableTreeNode parent =
        ensureNodeParentResolver.resolveParent(
            ref,
            new ServerTreeEnsureNodeParentResolver.ParentNodes(
                resolvedServerNodes.serverNode,
                resolvedServerNodes.pmNode,
                resolvedServerNodes.otherNode,
                resolvedServerNodes.monitorNode,
                resolvedServerNodes.interceptorsNode),
            builtInKind,
            layout,
            () -> context.ensureChannelListNode(resolvedServerNodes));

    String leafLabel = targetNodePolicy.leafLabel(ref);
    ensureNodeLeafInserter.insertLeaf(parent, ref, leafLabel);
    if (context.isPrivateMessageTarget(ref) && context.shouldPersistPrivateMessageList()) {
      context.rememberPrivateMessageTarget(serverId, ref.target());
    }
    if (builtInKind != null) {
      context.applyBuiltInLayoutToTree(sn, context.builtInLayout(serverId));
      context.applyRootSiblingOrderToTree(sn, context.rootSiblingOrder(serverId));
      context.persistBuiltInLayoutFromTree(serverId);
    }
    if (ref.isChannel()) {
      String sid = context.normalizeServerId(serverId);
      if (!sid.isEmpty()) {
        context.ensureChannelKnownInConfig(ref);
        context.sortChannelsUnderChannelList(sid);
        context.emitManagedChannelsChanged(sid);
      }
    }
    context.expandPath(parent);
  }

  public void removeTarget(TargetRef ref) {
    if (ref == null || ref.isStatus()) return;
    if (ref.isUiOnly() && !ref.isInterceptor()) return;

    DefaultMutableTreeNode mappedNode = leaves.remove(ref);
    Set<DefaultMutableTreeNode> nodesToRemove = new HashSet<>();
    if (mappedNode != null) {
      nodesToRemove.add(mappedNode);
    }
    nodesToRemove.addAll(targetSnapshotProvider.findTreeNodesByTarget(ref));
    if (nodesToRemove.isEmpty()) return;

    targetRemovalStateCoordinator.cleanupForRemovedTarget(ref);
    boolean removedAny = targetNodeRemovalMutator.removeNodes(nodesToRemove);
    if (!removedAny) {
      context.reloadRoot();
    }
  }

  private void ensureApplicationLeaf(TargetRef ref) {
    if (!context.applicationRootVisible()) {
      context.setApplicationRootVisible(true);
    }
    if (leaves.containsKey(ref)) return;
    context.addApplicationLeaf(ref, context.applicationLeafLabel(ref));
    context.nodeStructureChangedForApplicationRoot();
  }

  private void ensureServerForBuiltInGroup(TargetRef ref) {
    String serverId = Objects.toString(ref.serverId(), "").trim();
    if (serverId.isEmpty()) return;
    if (servers.containsKey(serverId)) return;
    if (canCreateMissingServerRoot(serverId)) {
      context.addServerRoot(serverId);
    }
  }

  private boolean canCreateMissingServerRoot(String serverId) {
    return serverCatalog == null || serverCatalog.containsId(serverId) || servers.isEmpty();
  }
}
