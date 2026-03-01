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
import javax.swing.tree.DefaultMutableTreeNode;

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
