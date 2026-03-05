package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
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

/** Handles server-root add/remove lifecycle and associated state synchronization. */
public final class ServerTreeServerRootLifecycleManager {

  public interface Context {
    String normalizeServerId(String serverId);

    ServerNodes server(String serverId);

    ServerNodes removeServer(String serverId);

    void putServer(String serverId, ServerNodes serverNodes);

    void markServerKnown(String serverId);

    void loadChannelStateForServer(String serverId);

    DefaultMutableTreeNode resolveParentForServer(String serverId);

    ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId);

    boolean showDccTransfersNodes();

    String statusLeafLabelForServer(String serverId);

    int notificationsCount(String serverId);

    int interceptorHitCount(String serverId);

    List<InterceptorDefinition> interceptorDefinitions(String serverId);

    void putLeaves(Map<TargetRef, DefaultMutableTreeNode> leavesByTarget);

    RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout(String serverId);

    RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder(String serverId);

    void applyBuiltInLayoutToTree(
        ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout);

    void applyRootSiblingOrderToTree(
        ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder);

    void reloadRootModel();

    void expandPath(DefaultMutableTreeNode node);

    void collapsePath(DefaultMutableTreeNode node);

    void refreshNotificationsCount(String serverId);

    void refreshInterceptorGroupCount(String serverId);

    void cleanupServerState(String serverId);

    void removeEmptyGroupIfNeeded(DefaultMutableTreeNode groupNode);
  }

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

  public static Context context(
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
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(servers, "servers");
    Objects.requireNonNull(markServerKnown, "markServerKnown");
    Objects.requireNonNull(loadChannelStateForServer, "loadChannelStateForServer");
    Objects.requireNonNull(resolveParentForServer, "resolveParentForServer");
    Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    Objects.requireNonNull(showDccTransfersNodes, "showDccTransfersNodes");
    Objects.requireNonNull(statusLeafLabelForServer, "statusLeafLabelForServer");
    Objects.requireNonNull(notificationsCount, "notificationsCount");
    Objects.requireNonNull(interceptorHitCount, "interceptorHitCount");
    Objects.requireNonNull(interceptorDefinitions, "interceptorDefinitions");
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(builtInLayout, "builtInLayout");
    Objects.requireNonNull(rootSiblingOrder, "rootSiblingOrder");
    Objects.requireNonNull(applyBuiltInLayoutToTree, "applyBuiltInLayoutToTree");
    Objects.requireNonNull(applyRootSiblingOrderToTree, "applyRootSiblingOrderToTree");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(refreshNotificationsCount, "refreshNotificationsCount");
    Objects.requireNonNull(refreshInterceptorGroupCount, "refreshInterceptorGroupCount");
    Objects.requireNonNull(cleanupServerState, "cleanupServerState");
    Objects.requireNonNull(removeEmptyGroupIfNeeded, "removeEmptyGroupIfNeeded");
    return new Context() {
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
    };
  }

  private final ServerTreeServerNodeBuilder serverNodeBuilder;
  private final String channelListLabel;
  private final String filtersLabel;
  private final String ignoresLabel;
  private final String dccTransfersLabel;
  private final String logViewerLabel;
  private final String monitorGroupLabel;
  private final String interceptorsGroupLabel;
  private final Context context;

  public ServerTreeServerRootLifecycleManager(
      ServerTreeServerNodeBuilder serverNodeBuilder,
      String channelListLabel,
      String filtersLabel,
      String ignoresLabel,
      String dccTransfersLabel,
      String logViewerLabel,
      String monitorGroupLabel,
      String interceptorsGroupLabel,
      Context context) {
    this.serverNodeBuilder = Objects.requireNonNull(serverNodeBuilder, "serverNodeBuilder");
    this.channelListLabel = Objects.requireNonNull(channelListLabel, "channelListLabel");
    this.filtersLabel = Objects.requireNonNull(filtersLabel, "filtersLabel");
    this.ignoresLabel = Objects.requireNonNull(ignoresLabel, "ignoresLabel");
    this.dccTransfersLabel = Objects.requireNonNull(dccTransfersLabel, "dccTransfersLabel");
    this.logViewerLabel = Objects.requireNonNull(logViewerLabel, "logViewerLabel");
    this.monitorGroupLabel = Objects.requireNonNull(monitorGroupLabel, "monitorGroupLabel");
    this.interceptorsGroupLabel =
        Objects.requireNonNull(interceptorsGroupLabel, "interceptorsGroupLabel");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void removeServerRoot(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerNodes serverNodes = context.removeServer(sid);
    if (serverNodes == null) return;
    context.cleanupServerState(sid);

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) serverNodes.serverNode.getParent();
    if (parent == null) return;
    parent.remove(serverNodes.serverNode);
    context.removeEmptyGroupIfNeeded(parent);
  }

  public ServerNodes addServerRoot(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();
    if (id.isEmpty()) id = "(server)";
    ServerNodes existing = context.server(id);
    if (existing != null) return existing;

    context.markServerKnown(id);
    context.loadChannelStateForServer(id);

    DefaultMutableTreeNode parent = context.resolveParentForServer(id);
    ServerBuiltInNodesVisibility visibility = context.builtInNodesVisibility(id);

    ServerTreeServerNodeBuilder.BuildResult built =
        serverNodeBuilder.build(
            new ServerTreeServerNodeBuilder.BuildSpec(
                id,
                context.statusLeafLabelForServer(id),
                channelListLabel,
                filtersLabel,
                ignoresLabel,
                dccTransfersLabel,
                logViewerLabel,
                monitorGroupLabel,
                interceptorsGroupLabel,
                "Other",
                visibility.server(),
                visibility.notifications(),
                visibility.logViewer(),
                context.showDccTransfersNodes(),
                context.notificationsCount(id),
                context.interceptorHitCount(id),
                context.interceptorDefinitions(id)));

    parent.add(built.serverNode());
    context.putLeaves(built.leavesByTarget());

    ServerNodes serverNodes =
        new ServerNodes(
            built.serverNode(),
            built.privateMessagesNode(),
            built.otherNode(),
            built.monitorNode(),
            built.interceptorsNode(),
            built.statusRef(),
            built.notificationsRef(),
            built.logViewerRef(),
            built.channelListRef(),
            built.weechatFiltersRef(),
            built.ignoresRef(),
            built.dccTransfersRef());
    context.putServer(id, serverNodes);

    context.applyBuiltInLayoutToTree(serverNodes, context.builtInLayout(id));
    context.applyRootSiblingOrderToTree(serverNodes, context.rootSiblingOrder(id));

    context.reloadRootModel();
    context.expandPath(built.serverNode());
    context.collapsePath(built.otherNode());
    context.refreshNotificationsCount(id);
    context.refreshInterceptorGroupCount(id);
    return serverNodes;
  }
}
