package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.ServerTreeUiHooks;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeContextMenuContextFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTooltipContextFactory;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeNodeAccess;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeChannelModeRequestBus;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
import java.awt.Component;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JTree;

/** Factory that assembles tooltip and context-menu collaborators for the server tree UI. */
public final class ServerTreeViewInteractionCollaboratorsFactory {

  private static final String BOUNCER_CONTROL_LABEL = "Bouncer Control";

  private ServerTreeViewInteractionCollaboratorsFactory() {}

  public static ServerTreeViewInteractionCollaborators create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");
    JTree tree = Objects.requireNonNull(in.tree(), "tree");

    ServerTreeTooltipProvider tooltipProvider =
        new ServerTreeTooltipProvider(
            tree,
            ServerTreeTooltipContextFactory.create(
                new ServerTreeTooltipContextFactory.Inputs(
                    Objects.requireNonNull(in.rowInteractionHandler(), "rowInteractionHandler")
                        ::serverIdAt,
                    Objects.requireNonNull(in.uiHooks(), "uiHooks"),
                    Objects.requireNonNull(in.nodeAccess(), "nodeAccess")::isIrcRootNode,
                    in.nodeAccess()::isApplicationRootNode,
                    Objects.requireNonNull(in.networkGroupManager(), "networkGroupManager")
                        ::isSojuNetworksGroupNode,
                    in.networkGroupManager()::isZncNetworksGroupNode,
                    Objects.requireNonNull(in.nodeClassifier(), "nodeClassifier")
                        ::isInterceptorsGroupNode,
                    in.nodeClassifier()::isMonitorGroupNode,
                    in.nodeClassifier()::isOtherGroupNode,
                    Objects.requireNonNull(in.runtimeState(), "runtimeState")
                        ::desiredOnlineForServer,
                    in.runtimeState()::connectionDiagnosticsTipForServer,
                    Objects.requireNonNull(in.serverLabelPolicy(), "serverLabelPolicy")
                        ::isSojuEphemeralServer,
                    in.serverLabelPolicy()::isZncEphemeralServer,
                    in.sojuOriginByServerId()::get,
                    in.zncOriginByServerId()::get,
                    serverId ->
                        Objects.requireNonNull(in.serverDisplayNames(), "serverDisplayNames")
                            .getOrDefault(serverId, serverId),
                    (originId, networkKey) -> {
                      SojuAutoConnectStore store = in.sojuAutoConnect();
                      return store != null && store.isEnabled(originId, networkKey);
                    },
                    (originId, networkKey) -> {
                      ZncAutoConnectStore store = in.zncAutoConnect();
                      return store != null && store.isEnabled(originId, networkKey);
                    },
                    Objects.requireNonNull(in.isApplicationJfrActive(), "isApplicationJfrActive"),
                    nodeData -> isBouncerControlStatusNode(nodeData, in))));
    ServerTreeTooltipResolver tooltipResolver =
        new ServerTreeTooltipResolver(
            Objects.requireNonNull(in.serverActionOverlay(), "serverActionOverlay"),
            tooltipProvider);
    ServerTreeContextMenuBuilder contextMenuBuilder =
        new ServerTreeContextMenuBuilder(
            ServerTreeContextMenuContextFactory.create(
                new ServerTreeContextMenuContextFactory.Inputs(
                    in.uiHooks()::isServerNode,
                    in.nodeAccess()::isRootServerNode,
                    in.serverLabelPolicy()::prettyServerLabel,
                    in.uiHooks()::connectionStateForServer,
                    in.runtimeState()::connectionDiagnosticsTipForServer,
                    in.serverCatalog(),
                    Objects.requireNonNull(in.moveNodeUpAction(), "moveNodeUpAction"),
                    Objects.requireNonNull(in.moveNodeDownAction(), "moveNodeDownAction"),
                    in.uiHooks()::connectServer,
                    in.uiHooks()::disconnectServer,
                    Objects.requireNonNull(in.openServerInfoDialog(), "openServerInfoDialog"),
                    in.requestEmitter()::emitOpenQuasselSetup,
                    in.requestEmitter()::emitOpenQuasselNetworkManager,
                    in.interceptorStore(),
                    in.interceptorActions()::promptAndAddInterceptor,
                    in.serverDialogs(),
                    Objects.requireNonNull(in.ownerComponent(), "ownerComponent"),
                    in.runtimeConfig(),
                    in.serverLabelPolicy()::isSojuEphemeralServer,
                    in.serverLabelPolicy()::isZncEphemeralServer,
                    in.sojuOriginByServerId()::get,
                    in.zncOriginByServerId()::get,
                    serverId -> in.serverDisplayNames().getOrDefault(serverId, serverId),
                    in.sojuAutoConnect(),
                    in.zncAutoConnect(),
                    in.nodeBadgeUpdater()::refreshSojuAutoConnectBadges,
                    in.nodeBadgeUpdater()::refreshZncAutoConnectBadges,
                    in.nodeClassifier()::isInterceptorsGroupNode,
                    in.nodeClassifier()::owningServerIdForNode,
                    in.uiHooks()::openPinnedChat,
                    in.uiHooks()::confirmAndClearLog,
                    in.isChannelDisconnected(),
                    in.uiHooks()::joinChannel,
                    in.uiHooks()::disconnectChannel,
                    in.uiHooks()::closeChannel,
                    in.bouncerDetachPolicy()::supportsBouncerDetach,
                    in.uiHooks()::bouncerDetachChannel,
                    in.isChannelAutoReattach(),
                    in.setChannelAutoReattach(),
                    in.isChannelPinned(),
                    in.setChannelPinned(),
                    in.isChannelMuted(),
                    in.setChannelMuted(),
                    in.channelModeRequestBus()::emitDetailsRequest,
                    in.channelModeRequestBus()::emitRefreshRequest,
                    in.canEditChannelModes(),
                    in.channelModeRequestBus()::emitSetRequest,
                    in.uiHooks()::closeTarget,
                    in.interceptorActions()::setInterceptorEnabled,
                    in.interceptorActions()::promptRenameInterceptor,
                    in.interceptorActions()::confirmDeleteInterceptor)));
    return new ServerTreeViewInteractionCollaborators(
        tooltipProvider, tooltipResolver, contextMenuBuilder);
  }

  private static boolean isBouncerControlStatusNode(ServerTreeNodeData nodeData, Inputs inputs) {
    if (nodeData == null || nodeData.ref == null || !nodeData.ref.isStatus()) {
      return false;
    }
    if (!BOUNCER_CONTROL_LABEL.equals(nodeData.label)) {
      return false;
    }
    String serverId = nodeData.ref.serverId();
    return inputs.sojuBouncerControlServerIds().contains(serverId)
        || inputs.zncBouncerControlServerIds().contains(serverId);
  }

  public record Inputs(
      JTree tree,
      ServerTreeRowInteractionHandler rowInteractionHandler,
      ServerTreeUiHooks uiHooks,
      ServerTreeNodeAccess nodeAccess,
      ServerTreeNetworkGroupManager networkGroupManager,
      ServerTreeNodeClassifier nodeClassifier,
      ServerTreeRuntimeState runtimeState,
      ServerTreeServerLabelPolicy serverLabelPolicy,
      Map<String, String> serverDisplayNames,
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      Supplier<Boolean> isApplicationJfrActive,
      ServerTreeServerActionOverlay serverActionOverlay,
      ServerCatalog serverCatalog,
      Supplier<Action> moveNodeUpAction,
      Supplier<Action> moveNodeDownAction,
      java.util.function.Consumer<String> openServerInfoDialog,
      ServerTreeRequestEmitter requestEmitter,
      InterceptorStore interceptorStore,
      ServerTreeInterceptorActions interceptorActions,
      ServerDialogs serverDialogs,
      Component ownerComponent,
      RuntimeConfigStore runtimeConfig,
      ServerTreeNodeBadgeUpdater nodeBadgeUpdater,
      ServerTreeBouncerDetachPolicy bouncerDetachPolicy,
      Predicate<TargetRef> isChannelDisconnected,
      Predicate<TargetRef> isChannelAutoReattach,
      BiConsumer<TargetRef, Boolean> setChannelAutoReattach,
      Predicate<TargetRef> isChannelPinned,
      BiConsumer<TargetRef, Boolean> setChannelPinned,
      Predicate<TargetRef> isChannelMuted,
      BiConsumer<TargetRef, Boolean> setChannelMuted,
      ServerTreeChannelModeRequestBus channelModeRequestBus,
      Predicate<TargetRef> canEditChannelModes) {}
}
