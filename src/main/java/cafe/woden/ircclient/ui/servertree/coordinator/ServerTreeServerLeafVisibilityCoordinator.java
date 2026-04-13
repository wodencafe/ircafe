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
import org.springframework.stereotype.Component;

/** Applies per-server built-in leaf/group visibility and reapplies persisted layout/order. */
@Component
public final class ServerTreeServerLeafVisibilityCoordinator {

  public interface Context {
    String channelListLabel();

    String weechatFiltersLabel();

    String ignoresLabel();

    String dccTransfersLabel();

    String notificationsLabel();

    String logViewerLabel();

    ServerTreeNodeVisibilityMutator nodeVisibilityMutator();

    String normalizeServerId(String serverId);

    ServerNodes serverNodesForServer(String serverId);

    ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId);

    ServerTreeBuiltInLayout builtInLayout(String serverId);

    ServerTreeRootSiblingOrder rootSiblingOrder(String serverId);

    void applyBuiltInLayoutToTree(ServerNodes serverNodes, ServerTreeBuiltInLayout builtInLayout);

    void applyRootSiblingOrderToTree(
        ServerNodes serverNodes, ServerTreeRootSiblingOrder rootSiblingOrder);

    String statusLeafLabelForServer(String serverId);

    boolean isQuasselServer(String serverId);

    boolean showDccTransfersNodes();

    DefaultMutableTreeNode leafForTarget(TargetRef ref);
  }

  public static Context context(
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
    Objects.requireNonNull(channelListLabel, "channelListLabel");
    Objects.requireNonNull(weechatFiltersLabel, "weechatFiltersLabel");
    Objects.requireNonNull(ignoresLabel, "ignoresLabel");
    Objects.requireNonNull(dccTransfersLabel, "dccTransfersLabel");
    Objects.requireNonNull(notificationsLabel, "notificationsLabel");
    Objects.requireNonNull(logViewerLabel, "logViewerLabel");
    Objects.requireNonNull(nodeVisibilityMutator, "nodeVisibilityMutator");
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(serverNodesForServer, "serverNodesForServer");
    Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    Objects.requireNonNull(builtInLayout, "builtInLayout");
    Objects.requireNonNull(rootSiblingOrder, "rootSiblingOrder");
    Objects.requireNonNull(applyBuiltInLayoutToTree, "applyBuiltInLayoutToTree");
    Objects.requireNonNull(applyRootSiblingOrderToTree, "applyRootSiblingOrderToTree");
    Objects.requireNonNull(statusLeafLabelForServer, "statusLeafLabelForServer");
    Objects.requireNonNull(isQuasselServer, "isQuasselServer");
    Objects.requireNonNull(showDccTransfersNodes, "showDccTransfersNodes");
    Objects.requireNonNull(leafForTarget, "leafForTarget");
    return new Context() {
      @Override
      public String channelListLabel() {
        return channelListLabel;
      }

      @Override
      public String weechatFiltersLabel() {
        return weechatFiltersLabel;
      }

      @Override
      public String ignoresLabel() {
        return ignoresLabel;
      }

      @Override
      public String dccTransfersLabel() {
        return dccTransfersLabel;
      }

      @Override
      public String notificationsLabel() {
        return notificationsLabel;
      }

      @Override
      public String logViewerLabel() {
        return logViewerLabel;
      }

      @Override
      public ServerTreeNodeVisibilityMutator nodeVisibilityMutator() {
        return nodeVisibilityMutator;
      }

      @Override
      public String normalizeServerId(String serverId) {
        return normalizeServerId.apply(serverId);
      }

      @Override
      public ServerNodes serverNodesForServer(String serverId) {
        return serverNodesForServer.apply(serverId);
      }

      @Override
      public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
        return builtInNodesVisibility.apply(serverId);
      }

      @Override
      public ServerTreeBuiltInLayout builtInLayout(String serverId) {
        return builtInLayout.apply(serverId);
      }

      @Override
      public ServerTreeRootSiblingOrder rootSiblingOrder(String serverId) {
        return rootSiblingOrder.apply(serverId);
      }

      @Override
      public void applyBuiltInLayoutToTree(
          ServerNodes serverNodes, ServerTreeBuiltInLayout builtInLayout) {
        applyBuiltInLayoutToTree.accept(serverNodes, builtInLayout);
      }

      @Override
      public void applyRootSiblingOrderToTree(
          ServerNodes serverNodes, ServerTreeRootSiblingOrder rootSiblingOrder) {
        applyRootSiblingOrderToTree.accept(serverNodes, rootSiblingOrder);
      }

      @Override
      public String statusLeafLabelForServer(String serverId) {
        return statusLeafLabelForServer.apply(serverId);
      }

      @Override
      public boolean isQuasselServer(String serverId) {
        return isQuasselServer.test(serverId);
      }

      @Override
      public boolean showDccTransfersNodes() {
        return showDccTransfersNodes.getAsBoolean();
      }

      @Override
      public DefaultMutableTreeNode leafForTarget(TargetRef ref) {
        return leafForTarget.apply(ref);
      }
    };
  }

  public void syncUiLeafVisibilityForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalize(in, serverId);
    if (sid.isEmpty()) return;

    ServerNodes serverNodes = in.serverNodesForServer(sid);
    if (serverNodes == null || serverNodes.serverNode == null) return;

    ServerBuiltInNodesVisibility visibility = in.builtInNodesVisibility(sid);
    boolean quasselServer = in.isQuasselServer(sid);
    ensureMovableBuiltInLeafVisible(
        in,
        serverNodes,
        serverNodes.statusRef,
        in.statusLeafLabelForServer(sid),
        visibility.server());
    ensureMovableBuiltInLeafVisible(
        in,
        serverNodes,
        serverNodes.notificationsRef,
        in.notificationsLabel(),
        visibility.notifications());
    ensureMovableBuiltInLeafVisible(
        in, serverNodes, serverNodes.logViewerRef, in.logViewerLabel(), visibility.logViewer());
    if (quasselServer) {
      suppressRootChannelListForQuassel(in, serverNodes);
      ensurePrivateMessagesGroupVisible(in, serverNodes, false);
    } else {
      ensureUiLeafVisible(in, serverNodes, serverNodes.channelListRef, in.channelListLabel(), true);
    }
    ensureMovableBuiltInLeafVisible(
        in, serverNodes, serverNodes.weechatFiltersRef, in.weechatFiltersLabel(), true);
    ensureMovableBuiltInLeafVisible(
        in, serverNodes, serverNodes.ignoresRef, in.ignoresLabel(), true);
    ensureUiLeafVisible(
        in,
        serverNodes,
        serverNodes.dccTransfersRef,
        in.dccTransfersLabel(),
        in.showDccTransfersNodes());
    ensureMonitorGroupVisible(in, serverNodes, !quasselServer && visibility.monitor());
    ensureInterceptorsGroupVisible(in, serverNodes, !quasselServer && visibility.interceptors());
    in.applyBuiltInLayoutToTree(serverNodes, in.builtInLayout(sid));
    if (quasselServer) {
      ensureMonitorGroupVisible(in, serverNodes, false);
      ensureInterceptorsGroupVisible(in, serverNodes, false);
      ensurePrivateMessagesGroupVisible(in, serverNodes, false);
    }
    in.applyRootSiblingOrderToTree(serverNodes, in.rootSiblingOrder(sid));
  }

  private void suppressRootChannelListForQuassel(Context context, ServerNodes serverNodes) {
    if (serverNodes == null
        || serverNodes.serverNode == null
        || serverNodes.channelListRef == null) {
      return;
    }
    DefaultMutableTreeNode channelListNode = context.leafForTarget(serverNodes.channelListRef);
    if (channelListNode == null || channelListNode.getParent() != serverNodes.serverNode) return;
    ensureUiLeafVisible(
        context, serverNodes, serverNodes.channelListRef, context.channelListLabel(), false);
  }

  private boolean ensureUiLeafVisible(
      Context context, ServerNodes serverNodes, TargetRef ref, String label, boolean visible) {
    if (serverNodes == null || serverNodes.serverNode == null || ref == null) return false;
    return context
        .nodeVisibilityMutator()
        .ensureLeafVisible(
            serverNodes.serverNode,
            ref,
            label,
            visible,
            true,
            ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
                serverNodes, ref, context::leafForTarget));
  }

  private boolean ensureMovableBuiltInLeafVisible(
      Context context, ServerNodes serverNodes, TargetRef ref, String label, boolean visible) {
    if (serverNodes == null || serverNodes.serverNode == null || ref == null) return false;
    return context
        .nodeVisibilityMutator()
        .ensureLeafVisible(serverNodes.serverNode, ref, label, visible, false, 0);
  }

  private boolean ensureInterceptorsGroupVisible(
      Context context, ServerNodes serverNodes, boolean visible) {
    if (serverNodes == null) return false;
    return context
        .nodeVisibilityMutator()
        .ensureGroupVisible(
            serverNodes.serverNode, serverNodes.otherNode, serverNodes.interceptorsNode, visible);
  }

  private boolean ensureMonitorGroupVisible(
      Context context, ServerNodes serverNodes, boolean visible) {
    if (serverNodes == null) return false;
    return context
        .nodeVisibilityMutator()
        .ensureGroupVisible(
            serverNodes.serverNode, serverNodes.otherNode, serverNodes.monitorNode, visible);
  }

  private boolean ensurePrivateMessagesGroupVisible(
      Context context, ServerNodes serverNodes, boolean visible) {
    if (serverNodes == null) return false;
    return context
        .nodeVisibilityMutator()
        .ensureGroupVisible(
            serverNodes.serverNode, serverNodes.otherNode, serverNodes.pmNode, visible);
  }

  private String normalize(Context context, String serverId) {
    return Objects.toString(context.normalizeServerId(serverId), "").trim();
  }
}
