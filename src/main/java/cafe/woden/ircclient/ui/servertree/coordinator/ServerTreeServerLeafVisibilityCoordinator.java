package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeNodeVisibilityMutator;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLeafInsertPolicy;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Applies per-server built-in leaf/group visibility and reapplies persisted layout/order. */
public final class ServerTreeServerLeafVisibilityCoordinator {

  private final String channelListLabel;
  private final String weechatFiltersLabel;
  private final String ignoresLabel;
  private final String dccTransfersLabel;
  private final String notificationsLabel;
  private final String logViewerLabel;

  private final ServerTreeNodeVisibilityMutator nodeVisibilityMutator;
  private final Function<String, String> normalizeServerId;
  private final Function<String, ServerNodes> serverNodesForServer;
  private final Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility;
  private final Function<String, ServerTreeBuiltInLayout> builtInLayout;
  private final Function<String, ServerTreeRootSiblingOrder> rootSiblingOrder;
  private final BiConsumer<ServerNodes, ServerTreeBuiltInLayout> applyBuiltInLayoutToTree;
  private final BiConsumer<ServerNodes, ServerTreeRootSiblingOrder> applyRootSiblingOrderToTree;
  private final Function<String, String> statusLeafLabelForServer;
  private final Predicate<String> isQuasselServer;
  private final BooleanSupplier showDccTransfersNodes;
  private final Function<TargetRef, DefaultMutableTreeNode> leafForTarget;

  public ServerTreeServerLeafVisibilityCoordinator(
      String channelListLabel,
      String weechatFiltersLabel,
      String ignoresLabel,
      String dccTransfersLabel,
      String notificationsLabel,
      String logViewerLabel,
      ServerTreeNodeVisibilityMutator nodeVisibilityMutator,
      Function<String, String> normalizeServerId,
      Function<String, ServerNodes> serverNodesForServer,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Function<String, ServerTreeBuiltInLayout> builtInLayout,
      Function<String, ServerTreeRootSiblingOrder> rootSiblingOrder,
      BiConsumer<ServerNodes, ServerTreeBuiltInLayout> applyBuiltInLayoutToTree,
      BiConsumer<ServerNodes, ServerTreeRootSiblingOrder> applyRootSiblingOrderToTree,
      Function<String, String> statusLeafLabelForServer,
      Predicate<String> isQuasselServer,
      BooleanSupplier showDccTransfersNodes,
      Function<TargetRef, DefaultMutableTreeNode> leafForTarget) {
    this.channelListLabel = Objects.requireNonNull(channelListLabel, "channelListLabel");
    this.weechatFiltersLabel = Objects.requireNonNull(weechatFiltersLabel, "weechatFiltersLabel");
    this.ignoresLabel = Objects.requireNonNull(ignoresLabel, "ignoresLabel");
    this.dccTransfersLabel = Objects.requireNonNull(dccTransfersLabel, "dccTransfersLabel");
    this.notificationsLabel = Objects.requireNonNull(notificationsLabel, "notificationsLabel");
    this.logViewerLabel = Objects.requireNonNull(logViewerLabel, "logViewerLabel");
    this.nodeVisibilityMutator =
        Objects.requireNonNull(nodeVisibilityMutator, "nodeVisibilityMutator");
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.serverNodesForServer =
        Objects.requireNonNull(serverNodesForServer, "serverNodesForServer");
    this.builtInNodesVisibility =
        Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    this.builtInLayout = Objects.requireNonNull(builtInLayout, "builtInLayout");
    this.rootSiblingOrder = Objects.requireNonNull(rootSiblingOrder, "rootSiblingOrder");
    this.applyBuiltInLayoutToTree =
        Objects.requireNonNull(applyBuiltInLayoutToTree, "applyBuiltInLayoutToTree");
    this.applyRootSiblingOrderToTree =
        Objects.requireNonNull(applyRootSiblingOrderToTree, "applyRootSiblingOrderToTree");
    this.statusLeafLabelForServer =
        Objects.requireNonNull(statusLeafLabelForServer, "statusLeafLabelForServer");
    this.isQuasselServer = Objects.requireNonNull(isQuasselServer, "isQuasselServer");
    this.showDccTransfersNodes =
        Objects.requireNonNull(showDccTransfersNodes, "showDccTransfersNodes");
    this.leafForTarget = Objects.requireNonNull(leafForTarget, "leafForTarget");
  }

  public void syncUiLeafVisibilityForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    ServerNodes serverNodes = serverNodesForServer.apply(sid);
    if (serverNodes == null || serverNodes.serverNode == null) return;

    ServerBuiltInNodesVisibility visibility = builtInNodesVisibility.apply(sid);
    boolean quasselServer = isQuasselServer.test(sid);
    ensureMovableBuiltInLeafVisible(
        serverNodes,
        serverNodes.statusRef,
        statusLeafLabelForServer.apply(sid),
        visibility.server());
    ensureMovableBuiltInLeafVisible(
        serverNodes, serverNodes.notificationsRef, notificationsLabel, visibility.notifications());
    ensureMovableBuiltInLeafVisible(
        serverNodes, serverNodes.logViewerRef, logViewerLabel, visibility.logViewer());
    if (quasselServer) {
      suppressRootChannelListForQuassel(serverNodes);
      ensurePrivateMessagesGroupVisible(serverNodes, false);
    } else {
      ensureUiLeafVisible(serverNodes, serverNodes.channelListRef, channelListLabel, true);
    }
    ensureMovableBuiltInLeafVisible(
        serverNodes, serverNodes.weechatFiltersRef, weechatFiltersLabel, true);
    ensureMovableBuiltInLeafVisible(serverNodes, serverNodes.ignoresRef, ignoresLabel, true);
    ensureUiLeafVisible(
        serverNodes,
        serverNodes.dccTransfersRef,
        dccTransfersLabel,
        showDccTransfersNodes.getAsBoolean());
    ensureMonitorGroupVisible(serverNodes, !quasselServer && visibility.monitor());
    ensureInterceptorsGroupVisible(serverNodes, !quasselServer && visibility.interceptors());
    applyBuiltInLayoutToTree.accept(serverNodes, builtInLayout.apply(sid));
    if (quasselServer) {
      ensureMonitorGroupVisible(serverNodes, false);
      ensureInterceptorsGroupVisible(serverNodes, false);
      ensurePrivateMessagesGroupVisible(serverNodes, false);
    }
    applyRootSiblingOrderToTree.accept(serverNodes, rootSiblingOrder.apply(sid));
  }

  private void suppressRootChannelListForQuassel(ServerNodes serverNodes) {
    if (serverNodes == null
        || serverNodes.serverNode == null
        || serverNodes.channelListRef == null) {
      return;
    }
    DefaultMutableTreeNode channelListNode = leafForTarget.apply(serverNodes.channelListRef);
    if (channelListNode == null || channelListNode.getParent() != serverNodes.serverNode) return;
    ensureUiLeafVisible(serverNodes, serverNodes.channelListRef, channelListLabel, false);
  }

  private boolean ensureUiLeafVisible(
      ServerNodes serverNodes, TargetRef ref, String label, boolean visible) {
    if (serverNodes == null || serverNodes.serverNode == null || ref == null) return false;
    return nodeVisibilityMutator.ensureLeafVisible(
        serverNodes.serverNode,
        ref,
        label,
        visible,
        true,
        ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
            serverNodes, ref, leafForTarget));
  }

  private boolean ensureMovableBuiltInLeafVisible(
      ServerNodes serverNodes, TargetRef ref, String label, boolean visible) {
    if (serverNodes == null || serverNodes.serverNode == null || ref == null) return false;
    return nodeVisibilityMutator.ensureLeafVisible(
        serverNodes.serverNode, ref, label, visible, false, 0);
  }

  private boolean ensureInterceptorsGroupVisible(ServerNodes serverNodes, boolean visible) {
    if (serverNodes == null) return false;
    return nodeVisibilityMutator.ensureGroupVisible(
        serverNodes.serverNode, serverNodes.otherNode, serverNodes.interceptorsNode, visible);
  }

  private boolean ensureMonitorGroupVisible(ServerNodes serverNodes, boolean visible) {
    if (serverNodes == null) return false;
    return nodeVisibilityMutator.ensureGroupVisible(
        serverNodes.serverNode, serverNodes.otherNode, serverNodes.monitorNode, visible);
  }

  private boolean ensurePrivateMessagesGroupVisible(ServerNodes serverNodes, boolean visible) {
    if (serverNodes == null) return false;
    return nodeVisibilityMutator.ensureGroupVisible(
        serverNodes.serverNode, serverNodes.otherNode, serverNodes.pmNode, visible);
  }

  private String normalize(String serverId) {
    return Objects.toString(normalizeServerId.apply(serverId), "").trim();
  }
}
