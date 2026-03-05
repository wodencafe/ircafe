package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.ServerTreeUiHooks;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeNodeAccess;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestStreams;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTargetNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

/** Factory that assembles tooltip and context-menu collaborators for the server tree UI. */
public final class ServerTreeViewInteractionCollaboratorsFactory {

  private static final String BOUNCER_CONTROL_LABEL = "Bouncer Control";

  private ServerTreeViewInteractionCollaboratorsFactory() {}

  public static ServerTreeViewInteractionCollaborators create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");
    JTree tree = Objects.requireNonNull(in.tree(), "tree");
    ServerTreeRequestStreams requestStreams =
        Objects.requireNonNull(in.requestStreams(), "requestStreams");

    ServerTreeTooltipProvider tooltipProvider = createTooltipProvider(in, tree);
    ServerTreeTooltipResolver tooltipResolver =
        new ServerTreeTooltipResolver(
            Objects.requireNonNull(in.serverActionOverlay(), "serverActionOverlay"),
            tooltipProvider);
    ServerTreeContextMenuBuilder contextMenuBuilder = createContextMenuBuilder(in, requestStreams);

    return new ServerTreeViewInteractionCollaborators(
        tooltipProvider, tooltipResolver, contextMenuBuilder);
  }

  private static ServerTreeTooltipProvider createTooltipProvider(Inputs in, JTree tree) {
    return new ServerTreeTooltipProvider(
        tree,
        ServerTreeTooltipProvider.context(
            Objects.requireNonNull(in.rowInteractionHandler(), "rowInteractionHandler")::serverIdAt,
            Objects.requireNonNull(in.uiHooks(), "uiHooks")::serverPathForId,
            Objects.requireNonNull(in.nodeAccess(), "nodeAccess")::isIrcRootNode,
            in.nodeAccess()::isApplicationRootNode,
            Objects.requireNonNull(in.networkGroupManager(), "networkGroupManager")
                ::isSojuNetworksGroupNode,
            in.networkGroupManager()::isZncNetworksGroupNode,
            in.networkGroupManager()::isGenericNetworksGroupNode,
            Objects.requireNonNull(in.nodeClassifier(), "nodeClassifier")::isInterceptorsGroupNode,
            in.nodeClassifier()::isMonitorGroupNode,
            in.nodeClassifier()::isOtherGroupNode,
            in.uiHooks()::isServerNode,
            in.uiHooks()::connectionStateForServer,
            Objects.requireNonNull(in.runtimeState(), "runtimeState")::desiredOnlineForServer,
            in.runtimeState()::connectionDiagnosticsTipForServer,
            Objects.requireNonNull(in.serverLabelPolicy(), "serverLabelPolicy")
                ::isSojuEphemeralServer,
            in.serverLabelPolicy()::isZncEphemeralServer,
            in.serverLabelPolicy()::isGenericEphemeralServer,
            in.sojuOriginByServerId()::get,
            in.zncOriginByServerId()::get,
            serverId -> genericOriginForServer(in, serverId),
            serverId ->
                Objects.requireNonNull(in.serverDisplayNames(), "serverDisplayNames")
                    .getOrDefault(serverId, serverId),
            (originId, networkKey) ->
                isSojuAutoConnectEnabled(in.sojuAutoConnect(), originId, networkKey),
            (originId, networkKey) ->
                isZncAutoConnectEnabled(in.zncAutoConnect(), originId, networkKey),
            (originId, networkKey) ->
                isGenericAutoConnectEnabled(in.genericAutoConnect(), originId, networkKey),
            Objects.requireNonNull(in.isApplicationJfrActive(), "isApplicationJfrActive"),
            nodeData -> isBouncerControlStatusNode(nodeData, in)));
  }

  private static ServerTreeContextMenuBuilder createContextMenuBuilder(
      Inputs in, ServerTreeRequestStreams requestStreams) {
    return new ServerTreeContextMenuBuilder(
        ServerTreeContextMenuBuilder.routingContext(
            in.uiHooks()::isServerNode, in.nodeClassifier()::isInterceptorsGroupNode),
        createServerNodeMenuContext(in),
        createTargetNodeMenuContext(in, requestStreams));
  }

  private static ServerTreeServerNodeMenuBuilder.Context createServerNodeMenuContext(Inputs in) {
    return ServerTreeServerNodeMenuBuilder.context(
        in.nodeAccess()::isRootServerNode,
        in.serverLabelPolicy()::prettyServerLabel,
        in.uiHooks()::connectionStateForServer,
        in.runtimeState()::connectionDiagnosticsTipForServer,
        serverId -> serverEntry(in.serverCatalog(), serverId),
        Objects.requireNonNull(in.moveNodeUpAction(), "moveNodeUpAction"),
        Objects.requireNonNull(in.moveNodeDownAction(), "moveNodeDownAction"),
        in.uiHooks()::connectServer,
        in.uiHooks()::disconnectServer,
        Objects.requireNonNull(in.openServerInfoDialog(), "openServerInfoDialog"),
        Objects.requireNonNull(in.requestEmitter(), "requestEmitter")::emitOpenQuasselSetup,
        in.requestEmitter()::emitOpenQuasselNetworkManager,
        () -> in.interceptorStore() != null,
        in.interceptorActions()::promptAndAddInterceptor,
        () -> in.serverDialogs() != null,
        serverId -> openSaveEphemeralServer(in, serverId),
        serverId -> openEditServer(in, serverId),
        () -> in.runtimeConfig() != null,
        (serverId, defaultValue) ->
            readServerAutoConnectOnStart(in.runtimeConfig(), serverId, defaultValue),
        (serverId, enabled) ->
            rememberServerAutoConnectOnStart(in.runtimeConfig(), serverId, enabled),
        in.serverLabelPolicy()::isSojuEphemeralServer,
        in.serverLabelPolicy()::isZncEphemeralServer,
        in.serverLabelPolicy()::isGenericEphemeralServer,
        in.sojuOriginByServerId()::get,
        in.zncOriginByServerId()::get,
        serverId -> genericOriginForServer(in, serverId),
        serverId -> in.serverDisplayNames().getOrDefault(serverId, serverId),
        (originId, networkKey) ->
            isSojuAutoConnectEnabled(in.sojuAutoConnect(), originId, networkKey),
        (originId, networkKey) ->
            isZncAutoConnectEnabled(in.zncAutoConnect(), originId, networkKey),
        (originId, networkKey) ->
            isGenericAutoConnectEnabled(in.genericAutoConnect(), originId, networkKey),
        (originId, networkKey, enabled) ->
            setSojuAutoConnectEnabled(in.sojuAutoConnect(), originId, networkKey, enabled),
        (originId, networkKey, enabled) ->
            setZncAutoConnectEnabled(in.zncAutoConnect(), originId, networkKey, enabled),
        (originId, networkKey, enabled) ->
            setGenericAutoConnectEnabled(in.genericAutoConnect(), originId, networkKey, enabled),
        in.nodeBadgeUpdater()::refreshSojuAutoConnectBadges,
        in.nodeBadgeUpdater()::refreshZncAutoConnectBadges,
        in.nodeBadgeUpdater()::refreshGenericAutoConnectBadges,
        in.nodeClassifier()::owningServerIdForNode);
  }

  private static ServerTreeTargetNodeMenuBuilder.Context createTargetNodeMenuContext(
      Inputs in, ServerTreeRequestStreams requestStreams) {
    return ServerTreeTargetNodeMenuBuilder.context(
        in.uiHooks()::openPinnedChat,
        Objects.requireNonNull(in.moveNodeUpAction(), "moveNodeUpAction"),
        Objects.requireNonNull(in.moveNodeDownAction(), "moveNodeDownAction"),
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
        requestStreams::emitChannelModeDetailsRequest,
        requestStreams::emitChannelModeRefreshRequest,
        in.canEditChannelModes(),
        (target, channelLabel) ->
            promptAndRequestChannelModeSet(in, requestStreams, target, channelLabel),
        in.uiHooks()::closeTarget,
        target -> interceptorDefinition(in.interceptorStore(), target),
        () -> in.interceptorStore() != null,
        in.interceptorActions()::setInterceptorEnabled,
        in.interceptorActions()::promptRenameInterceptor,
        in.interceptorActions()::confirmDeleteInterceptor);
  }

  private static java.util.Optional<cafe.woden.ircclient.config.ServerEntry> serverEntry(
      ServerCatalog serverCatalog, String serverId) {
    return serverCatalog == null ? java.util.Optional.empty() : serverCatalog.findEntry(serverId);
  }

  private static void openSaveEphemeralServer(Inputs in, String serverId) {
    ServerDialogs dialogs = in.serverDialogs();
    if (dialogs == null) return;
    Window window = SwingUtilities.getWindowAncestor(in.ownerComponent());
    dialogs.openSaveEphemeralServer(window, serverId);
  }

  private static void openEditServer(Inputs in, String serverId) {
    ServerDialogs dialogs = in.serverDialogs();
    if (dialogs == null) return;
    Window window = SwingUtilities.getWindowAncestor(in.ownerComponent());
    dialogs.openEditServer(window, serverId);
  }

  private static boolean readServerAutoConnectOnStart(
      RuntimeConfigStore runtimeConfig, String serverId, boolean defaultValue) {
    return runtimeConfig == null
        ? defaultValue
        : runtimeConfig.readServerAutoConnectOnStart(serverId, defaultValue);
  }

  private static void rememberServerAutoConnectOnStart(
      RuntimeConfigStore runtimeConfig, String serverId, boolean enabled) {
    if (runtimeConfig == null) return;
    runtimeConfig.rememberServerAutoConnectOnStart(serverId, enabled);
  }

  private static boolean isSojuAutoConnectEnabled(
      SojuAutoConnectStore store, String originId, String networkKey) {
    return store != null && store.isEnabled(originId, networkKey);
  }

  private static boolean isZncAutoConnectEnabled(
      ZncAutoConnectStore store, String originId, String networkKey) {
    return store != null && store.isEnabled(originId, networkKey);
  }

  private static boolean isGenericAutoConnectEnabled(
      GenericBouncerAutoConnectStore store, String originId, String networkKey) {
    return store != null && store.isEnabled(originId, networkKey);
  }

  private static void setSojuAutoConnectEnabled(
      SojuAutoConnectStore store, String originId, String networkKey, boolean enabled) {
    if (store == null) return;
    store.setEnabled(originId, networkKey, enabled);
  }

  private static void setZncAutoConnectEnabled(
      ZncAutoConnectStore store, String originId, String networkKey, boolean enabled) {
    if (store == null) return;
    store.setEnabled(originId, networkKey, enabled);
  }

  private static void setGenericAutoConnectEnabled(
      GenericBouncerAutoConnectStore store, String originId, String networkKey, boolean enabled) {
    if (store == null) return;
    store.setEnabled(originId, networkKey, enabled);
  }

  private static String genericOriginFromServerId(String serverId) {
    return ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId(serverId, "bouncer:");
  }

  private static String genericOriginForServer(Inputs inputs, String serverId) {
    if (inputs == null || inputs.genericOriginByServerId() == null) {
      return genericOriginFromServerId(serverId);
    }
    String mapped = inputs.genericOriginByServerId().get(serverId);
    return mapped == null || mapped.isBlank() ? genericOriginFromServerId(serverId) : mapped;
  }

  private static void promptAndRequestChannelModeSet(
      Inputs in, ServerTreeRequestStreams requestStreams, TargetRef target, String channelLabel) {
    if (target == null || !target.isChannel()) return;
    if (!in.canEditChannelModes().test(target)) return;
    if (GraphicsEnvironment.isHeadless()) return;

    Window owner = SwingUtilities.getWindowAncestor(in.ownerComponent());
    String pretty =
        Objects.toString(channelLabel, "").isBlank()
            ? target.target()
            : Objects.toString(channelLabel, "");
    String modeSpec =
        Objects.toString(
                JOptionPane.showInputDialog(
                    owner,
                    "Enter channel mode changes for " + pretty + " (examples: +m, -m, +o nick):",
                    ""),
                "")
            .trim();
    if (modeSpec.isEmpty()) return;
    requestStreams.emitChannelModeSetRequest(target, modeSpec);
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
        || inputs.zncBouncerControlServerIds().contains(serverId)
        || inputs.genericBouncerControlServerIds().contains(serverId);
  }

  private static InterceptorDefinition interceptorDefinition(
      InterceptorStore interceptorStore, TargetRef target) {
    if (interceptorStore == null || target == null || !target.isInterceptor()) return null;
    String serverId = Objects.toString(target.serverId(), "").trim();
    String interceptorId = Objects.toString(target.interceptorId(), "").trim();
    if (serverId.isEmpty() || interceptorId.isEmpty()) return null;
    return interceptorStore.interceptor(serverId, interceptorId);
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
      Set<String> genericBouncerControlServerIds,
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      Map<String, String> genericOriginByServerId,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      GenericBouncerAutoConnectStore genericAutoConnect,
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
      ServerTreeRequestStreams requestStreams,
      Predicate<TargetRef> canEditChannelModes) {}
}
