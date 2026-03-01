package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerRootLifecycleManager;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Adapter for {@link ServerTreeServerRootLifecycleManager.Context}. */
public final class ServerTreeServerRootLifecycleContextAdapter
    implements ServerTreeServerRootLifecycleManager.Context {

  private final Function<String, String> normalizeServerId;
  private final Map<String, ServerNodes> servers;
  private final Consumer<String> markServerKnown;
  private final Consumer<String> loadChannelStateForServer;
  private final Function<String, DefaultMutableTreeNode> resolveParentForServer;
  private final Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility;
  private final Supplier<Boolean> showDccTransfersNodes;
  private final Function<String, String> statusLeafLabelForServer;
  private final Function<String, Integer> notificationsCount;
  private final Function<String, Integer> interceptorHitCount;
  private final Function<String, List<InterceptorDefinition>> interceptorDefinitions;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Function<String, RuntimeConfigStore.ServerTreeBuiltInLayout> builtInLayout;
  private final Function<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> rootSiblingOrder;
  private final BiLayoutConsumer applyBuiltInLayoutToTree;
  private final BiRootOrderConsumer applyRootSiblingOrderToTree;
  private final DefaultTreeModel model;
  private final DefaultMutableTreeNode root;
  private final JTree tree;
  private final Consumer<String> refreshNotificationsCount;
  private final Consumer<String> refreshInterceptorGroupCount;
  private final Consumer<String> cleanupServerState;
  private final Consumer<DefaultMutableTreeNode> removeEmptyGroupIfNeeded;

  @FunctionalInterface
  public interface BiLayoutConsumer {
    void accept(
        ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout);
  }

  @FunctionalInterface
  public interface BiRootOrderConsumer {
    void accept(
        ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder);
  }

  public ServerTreeServerRootLifecycleContextAdapter(
      Function<String, String> normalizeServerId,
      Map<String, ServerNodes> servers,
      Consumer<String> markServerKnown,
      Consumer<String> loadChannelStateForServer,
      Function<String, DefaultMutableTreeNode> resolveParentForServer,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Supplier<Boolean> showDccTransfersNodes,
      Function<String, String> statusLeafLabelForServer,
      Function<String, Integer> notificationsCount,
      Function<String, Integer> interceptorHitCount,
      Function<String, List<InterceptorDefinition>> interceptorDefinitions,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Function<String, RuntimeConfigStore.ServerTreeBuiltInLayout> builtInLayout,
      Function<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> rootSiblingOrder,
      BiLayoutConsumer applyBuiltInLayoutToTree,
      BiRootOrderConsumer applyRootSiblingOrderToTree,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      JTree tree,
      Consumer<String> refreshNotificationsCount,
      Consumer<String> refreshInterceptorGroupCount,
      Consumer<String> cleanupServerState,
      Consumer<DefaultMutableTreeNode> removeEmptyGroupIfNeeded) {
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.servers = Objects.requireNonNull(servers, "servers");
    this.markServerKnown = Objects.requireNonNull(markServerKnown, "markServerKnown");
    this.loadChannelStateForServer =
        Objects.requireNonNull(loadChannelStateForServer, "loadChannelStateForServer");
    this.resolveParentForServer =
        Objects.requireNonNull(resolveParentForServer, "resolveParentForServer");
    this.builtInNodesVisibility =
        Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    this.showDccTransfersNodes =
        Objects.requireNonNull(showDccTransfersNodes, "showDccTransfersNodes");
    this.statusLeafLabelForServer =
        Objects.requireNonNull(statusLeafLabelForServer, "statusLeafLabelForServer");
    this.notificationsCount = Objects.requireNonNull(notificationsCount, "notificationsCount");
    this.interceptorHitCount = Objects.requireNonNull(interceptorHitCount, "interceptorHitCount");
    this.interceptorDefinitions =
        Objects.requireNonNull(interceptorDefinitions, "interceptorDefinitions");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.builtInLayout = Objects.requireNonNull(builtInLayout, "builtInLayout");
    this.rootSiblingOrder = Objects.requireNonNull(rootSiblingOrder, "rootSiblingOrder");
    this.applyBuiltInLayoutToTree =
        Objects.requireNonNull(applyBuiltInLayoutToTree, "applyBuiltInLayoutToTree");
    this.applyRootSiblingOrderToTree =
        Objects.requireNonNull(applyRootSiblingOrderToTree, "applyRootSiblingOrderToTree");
    this.model = Objects.requireNonNull(model, "model");
    this.root = Objects.requireNonNull(root, "root");
    this.tree = Objects.requireNonNull(tree, "tree");
    this.refreshNotificationsCount =
        Objects.requireNonNull(refreshNotificationsCount, "refreshNotificationsCount");
    this.refreshInterceptorGroupCount =
        Objects.requireNonNull(refreshInterceptorGroupCount, "refreshInterceptorGroupCount");
    this.cleanupServerState = Objects.requireNonNull(cleanupServerState, "cleanupServerState");
    this.removeEmptyGroupIfNeeded =
        Objects.requireNonNull(removeEmptyGroupIfNeeded, "removeEmptyGroupIfNeeded");
  }

  @Override
  public String normalizeServerId(String serverId) {
    return normalizeServerId.apply(serverId);
  }

  @Override
  public ServerNodes server(String serverId) {
    return servers.get(serverId);
  }

  @Override
  public ServerNodes removeServer(String serverId) {
    return servers.remove(serverId);
  }

  @Override
  public void putServer(String serverId, ServerNodes serverNodes) {
    servers.put(serverId, serverNodes);
  }

  @Override
  public void markServerKnown(String serverId) {
    markServerKnown.accept(serverId);
  }

  @Override
  public void loadChannelStateForServer(String serverId) {
    loadChannelStateForServer.accept(serverId);
  }

  @Override
  public DefaultMutableTreeNode resolveParentForServer(String serverId) {
    return resolveParentForServer.apply(serverId);
  }

  @Override
  public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    return builtInNodesVisibility.apply(serverId);
  }

  @Override
  public boolean showDccTransfersNodes() {
    return showDccTransfersNodes.get();
  }

  @Override
  public String statusLeafLabelForServer(String serverId) {
    return statusLeafLabelForServer.apply(serverId);
  }

  @Override
  public int notificationsCount(String serverId) {
    return notificationsCount.apply(serverId);
  }

  @Override
  public int interceptorHitCount(String serverId) {
    return interceptorHitCount.apply(serverId);
  }

  @Override
  public List<InterceptorDefinition> interceptorDefinitions(String serverId) {
    return interceptorDefinitions.apply(serverId);
  }

  @Override
  public void putLeaves(Map<TargetRef, DefaultMutableTreeNode> leavesByTarget) {
    leaves.putAll(leavesByTarget);
  }

  @Override
  public RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout(String serverId) {
    return builtInLayout.apply(serverId);
  }

  @Override
  public RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder(String serverId) {
    return rootSiblingOrder.apply(serverId);
  }

  @Override
  public void applyBuiltInLayoutToTree(
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout) {
    applyBuiltInLayoutToTree.accept(serverNodes, requestedLayout);
  }

  @Override
  public void applyRootSiblingOrderToTree(
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder) {
    applyRootSiblingOrderToTree.accept(serverNodes, requestedOrder);
  }

  @Override
  public void reloadRootModel() {
    model.reload(root);
  }

  @Override
  public void expandPath(DefaultMutableTreeNode node) {
    if (node == null || node.getPath() == null) return;
    tree.expandPath(new TreePath(node.getPath()));
  }

  @Override
  public void collapsePath(DefaultMutableTreeNode node) {
    if (node == null || node.getPath() == null) return;
    tree.collapsePath(new TreePath(node.getPath()));
  }

  @Override
  public void refreshNotificationsCount(String serverId) {
    refreshNotificationsCount.accept(serverId);
  }

  @Override
  public void refreshInterceptorGroupCount(String serverId) {
    refreshInterceptorGroupCount.accept(serverId);
  }

  @Override
  public void cleanupServerState(String serverId) {
    cleanupServerState.accept(serverId);
  }

  @Override
  public void removeEmptyGroupIfNeeded(DefaultMutableTreeNode groupNode) {
    removeEmptyGroupIfNeeded.accept(groupNode);
  }
}
