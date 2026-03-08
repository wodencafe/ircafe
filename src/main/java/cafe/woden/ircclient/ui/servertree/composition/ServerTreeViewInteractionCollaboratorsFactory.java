package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.bouncer.BouncerAutoConnectStore;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
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
import cafe.woden.ircclient.ui.servertree.view.ServerTreeQuasselNetworkNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTargetNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.LinkedHashSet;
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
            node -> backendIdForNetworksGroupNode(in, node),
            Objects.requireNonNull(in.nodeClassifier(), "nodeClassifier")::isInterceptorsGroupNode,
            in.nodeClassifier()::isMonitorGroupNode,
            in.nodeClassifier()::isOtherGroupNode,
            in.uiHooks()::isServerNode,
            in.uiHooks()::connectionStateForServer,
            Objects.requireNonNull(in.runtimeState(), "runtimeState")::desiredOnlineForServer,
            in.runtimeState()::connectionDiagnosticsTipForServer,
            serverId -> backendIdForEphemeralServer(in, serverId),
            (backendId, serverId) -> originForServer(in, backendId, serverId),
            serverId ->
                Objects.requireNonNull(in.serverDisplayNames(), "serverDisplayNames")
                    .getOrDefault(serverId, serverId),
            (backendId, originId, networkKey) ->
                isAutoConnectEnabled(in, backendId, originId, networkKey),
            Objects.requireNonNull(in.isApplicationJfrActive(), "isApplicationJfrActive"),
            nodeData -> isBouncerControlStatusNode(nodeData, in)));
  }

  private static ServerTreeContextMenuBuilder createContextMenuBuilder(
      Inputs in, ServerTreeRequestStreams requestStreams) {
    return new ServerTreeContextMenuBuilder(
        ServerTreeContextMenuBuilder.routingContext(
            in.uiHooks()::isServerNode,
            in.nodeClassifier()::isInterceptorsGroupNode,
            in.isQuasselNetworkNode(),
            in.isQuasselEmptyStateNode()),
        createServerNodeMenuContext(in),
        createTargetNodeMenuContext(in, requestStreams),
        createQuasselNetworkNodeMenuContext(in));
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
        serverId -> backendIdForEphemeralServer(in, serverId),
        (backendId, serverId) -> originForServer(in, backendId, serverId),
        (backendId, originId, networkKey) ->
            isAutoConnectEnabled(in, backendId, originId, networkKey),
        serverId -> in.serverDisplayNames().getOrDefault(serverId, serverId),
        (backendId, originId, networkKey, enabled) ->
            setAutoConnectEnabled(in, backendId, originId, networkKey, enabled),
        backendId -> refreshAutoConnectBadges(in, backendId),
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

  private static ServerTreeQuasselNetworkNodeMenuBuilder.Context
      createQuasselNetworkNodeMenuContext(Inputs in) {
    return ServerTreeQuasselNetworkNodeMenuBuilder.context(
        in.uiHooks()::openPinnedChat,
        Objects.requireNonNull(in.requestEmitter(), "requestEmitter")::emitOpenQuasselSetup,
        in.requestEmitter()::emitOpenQuasselNetworkManager);
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

  private static String originFromServerId(String backendId, String serverId) {
    String prefix = ServerTreeBouncerBackends.prefixFor(backendId);
    if (prefix == null || prefix.isBlank()) return null;
    return ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId(serverId, prefix);
  }

  private static String originForServer(Inputs inputs, String backendId, String serverId) {
    if (inputs == null) {
      return originFromServerId(backendId, serverId);
    }
    if (inputs.serverLabelPolicy() != null) {
      String resolved = inputs.serverLabelPolicy().originForServer(backendId, serverId);
      if (resolved != null && !resolved.isBlank()) {
        return resolved;
      }
    }
    Map<String, String> originsByServerId = originMapForBackend(inputs, backendId);
    String mapped = originsByServerId.get(serverId);
    return mapped == null || mapped.isBlank() ? originFromServerId(backendId, serverId) : mapped;
  }

  private static String backendIdForNetworksGroupNode(
      Inputs inputs, javax.swing.tree.DefaultMutableTreeNode node) {
    if (inputs == null || inputs.networkGroupManager() == null || node == null) {
      return null;
    }
    for (String backendId : orderedBackendIds(inputs)) {
      if (inputs.networkGroupManager().isNetworksGroupNode(backendId, node)) {
        return backendId;
      }
    }
    return null;
  }

  private static String backendIdForEphemeralServer(Inputs inputs, String serverId) {
    if (inputs == null || inputs.serverLabelPolicy() == null) {
      return null;
    }
    return inputs.serverLabelPolicy().backendIdForEphemeralServer(serverId);
  }

  private static boolean isAutoConnectEnabled(
      Inputs inputs, String backendId, String originId, String networkKey) {
    if (inputs != null && inputs.serverLabelPolicy() != null) {
      return inputs.serverLabelPolicy().isAutoConnectEnabled(backendId, originId, networkKey);
    }
    String backend = normalizeBackendId(backendId);
    String origin = Objects.toString(originId, "").trim();
    String network = Objects.toString(networkKey, "").trim();
    if (backend.isEmpty() || origin.isEmpty() || network.isEmpty()) return false;
    BouncerAutoConnectStore store = autoConnectStoreForBackend(inputs, backend);
    return store != null && store.isEnabled(origin, network);
  }

  private static void setAutoConnectEnabled(
      Inputs inputs, String backendId, String originId, String networkKey, boolean enabled) {
    String backend = normalizeBackendId(backendId);
    if (backend.isEmpty()) return;
    BouncerAutoConnectStore store = autoConnectStoreForBackend(inputs, backend);
    if (store == null) return;
    String origin = Objects.toString(originId, "").trim();
    String network = Objects.toString(networkKey, "").trim();
    if (origin.isEmpty() || network.isEmpty()) return;
    store.setEnabled(origin, network, enabled);
  }

  private static void refreshAutoConnectBadges(Inputs inputs, String backendId) {
    if (inputs == null || inputs.nodeBadgeUpdater() == null) return;
    inputs.nodeBadgeUpdater().refreshAutoConnectBadges(backendId);
  }

  private static Set<String> orderedBackendIds(Inputs inputs) {
    LinkedHashSet<String> backendIds = new LinkedHashSet<>(ServerTreeBouncerBackends.orderedIds());
    if (inputs != null && inputs.bouncerControlServerIdsByBackendId() != null) {
      backendIds.addAll(inputs.bouncerControlServerIdsByBackendId().keySet());
    }
    if (inputs != null && inputs.originByServerIdByBackendId() != null) {
      backendIds.addAll(inputs.originByServerIdByBackendId().keySet());
    }
    return backendIds;
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static BouncerAutoConnectStore autoConnectStoreForBackend(
      Inputs inputs, String backendId) {
    if (inputs == null || inputs.autoConnectStoreByBackendId() == null) {
      return null;
    }
    String backend = normalizeBackendId(backendId);
    if (backend.isEmpty()) return null;
    return inputs.autoConnectStoreByBackendId().get(backend);
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
    return isBouncerControlServer(inputs, serverId);
  }

  private static boolean isBouncerControlServer(Inputs inputs, String serverId) {
    if (inputs == null || inputs.bouncerControlServerIdsByBackendId() == null) {
      return false;
    }
    for (Set<String> serverIds : inputs.bouncerControlServerIdsByBackendId().values()) {
      if (serverIds != null && serverIds.contains(serverId)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, String> originMapForBackend(Inputs inputs, String backendId) {
    if (inputs == null || inputs.originByServerIdByBackendId() == null) {
      return Map.of();
    }
    Map<String, String> origins =
        inputs.originByServerIdByBackendId().getOrDefault(backendId, Map.of());
    return origins == null ? Map.of() : origins;
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
      Map<String, Set<String>> bouncerControlServerIdsByBackendId,
      Map<String, Map<String, String>> originByServerIdByBackendId,
      Map<String, BouncerAutoConnectStore> autoConnectStoreByBackendId,
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
      Predicate<TargetRef> canEditChannelModes,
      Predicate<javax.swing.tree.DefaultMutableTreeNode> isQuasselNetworkNode,
      Predicate<javax.swing.tree.DefaultMutableTreeNode> isQuasselEmptyStateNode) {}
}
