package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
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
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
import java.awt.GraphicsEnvironment;
import java.awt.Component;
import java.awt.Window;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.JTree;

/** Factory that assembles tooltip and context-menu collaborators for the server tree UI. */
public final class ServerTreeViewInteractionCollaboratorsFactory {

  private static final String BOUNCER_CONTROL_LABEL = "Bouncer Control";

  private ServerTreeViewInteractionCollaboratorsFactory() {}

  public static ServerTreeViewInteractionCollaborators create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");
    JTree tree = Objects.requireNonNull(in.tree(), "tree");
    ServerTreeRequestStreams requestStreams =
        Objects.requireNonNull(in.requestStreams(), "requestStreams");

    ServerTreeTooltipProvider tooltipProvider =
        new ServerTreeTooltipProvider(
            tree,
            ServerTreeTooltipProvider.context(
                Objects.requireNonNull(in.rowInteractionHandler(), "rowInteractionHandler")
                    ::serverIdAt,
                Objects.requireNonNull(in.uiHooks(), "uiHooks")::serverPathForId,
                Objects.requireNonNull(in.nodeAccess(), "nodeAccess")::isIrcRootNode,
                in.nodeAccess()::isApplicationRootNode,
                Objects.requireNonNull(in.networkGroupManager(), "networkGroupManager")
                    ::isSojuNetworksGroupNode,
                in.networkGroupManager()::isZncNetworksGroupNode,
                Objects.requireNonNull(in.nodeClassifier(), "nodeClassifier")
                    ::isInterceptorsGroupNode,
                in.nodeClassifier()::isMonitorGroupNode,
                in.nodeClassifier()::isOtherGroupNode,
                in.uiHooks()::isServerNode,
                in.uiHooks()::connectionStateForServer,
                Objects.requireNonNull(in.runtimeState(), "runtimeState")::desiredOnlineForServer,
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
                nodeData -> isBouncerControlStatusNode(nodeData, in)));
    ServerTreeTooltipResolver tooltipResolver =
        new ServerTreeTooltipResolver(
            Objects.requireNonNull(in.serverActionOverlay(), "serverActionOverlay"),
            tooltipProvider);
    ServerTreeContextMenuBuilder contextMenuBuilder =
        new ServerTreeContextMenuBuilder(
            ServerTreeContextMenuBuilder.context(
                in.uiHooks()::isServerNode,
                in.nodeAccess()::isRootServerNode,
                in.serverLabelPolicy()::prettyServerLabel,
                in.uiHooks()::connectionStateForServer,
                in.runtimeState()::connectionDiagnosticsTipForServer,
                serverId -> {
                  ServerCatalog catalog = in.serverCatalog();
                  return catalog != null ? catalog.findEntry(serverId) : java.util.Optional.empty();
                },
                Objects.requireNonNull(in.moveNodeUpAction(), "moveNodeUpAction"),
                Objects.requireNonNull(in.moveNodeDownAction(), "moveNodeDownAction"),
                in.uiHooks()::connectServer,
                in.uiHooks()::disconnectServer,
                Objects.requireNonNull(in.openServerInfoDialog(), "openServerInfoDialog"),
                in.requestEmitter()::emitOpenQuasselSetup,
                in.requestEmitter()::emitOpenQuasselNetworkManager,
                () -> in.interceptorStore() != null,
                in.interceptorActions()::promptAndAddInterceptor,
                () -> in.serverDialogs() != null,
                serverId -> {
                  ServerDialogs dialogs = in.serverDialogs();
                  if (dialogs == null) return;
                  Window window = SwingUtilities.getWindowAncestor(in.ownerComponent());
                  dialogs.openSaveEphemeralServer(window, serverId);
                },
                serverId -> {
                  ServerDialogs dialogs = in.serverDialogs();
                  if (dialogs == null) return;
                  Window window = SwingUtilities.getWindowAncestor(in.ownerComponent());
                  dialogs.openEditServer(window, serverId);
                },
                () -> in.runtimeConfig() != null,
                (serverId, defaultValue) -> {
                  RuntimeConfigStore config = in.runtimeConfig();
                  return config == null
                      ? defaultValue
                      : config.readServerAutoConnectOnStart(serverId, defaultValue);
                },
                (serverId, enabled) -> {
                  RuntimeConfigStore config = in.runtimeConfig();
                  if (config == null) return;
                  config.rememberServerAutoConnectOnStart(serverId, enabled);
                },
                in.serverLabelPolicy()::isSojuEphemeralServer,
                in.serverLabelPolicy()::isZncEphemeralServer,
                in.sojuOriginByServerId()::get,
                in.zncOriginByServerId()::get,
                serverId -> in.serverDisplayNames().getOrDefault(serverId, serverId),
                (originId, networkKey) -> {
                  SojuAutoConnectStore store = in.sojuAutoConnect();
                  return store != null && store.isEnabled(originId, networkKey);
                },
                (originId, networkKey) -> {
                  ZncAutoConnectStore store = in.zncAutoConnect();
                  return store != null && store.isEnabled(originId, networkKey);
                },
                (originId, networkKey, enabled) -> {
                  SojuAutoConnectStore store = in.sojuAutoConnect();
                  if (store == null) return;
                  store.setEnabled(originId, networkKey, enabled);
                },
                (originId, networkKey, enabled) -> {
                  ZncAutoConnectStore store = in.zncAutoConnect();
                  if (store == null) return;
                  store.setEnabled(originId, networkKey, enabled);
                },
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
                requestStreams::emitChannelModeDetailsRequest,
                requestStreams::emitChannelModeRefreshRequest,
                in.canEditChannelModes(),
                (target, channelLabel) -> {
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
                                  "Enter channel mode changes for "
                                      + pretty
                                      + " (examples: +m, -m, +o nick):",
                                  ""),
                              "")
                          .trim();
                  if (modeSpec.isEmpty()) return;
                  requestStreams.emitChannelModeSetRequest(target, modeSpec);
                },
                in.uiHooks()::closeTarget,
                target -> interceptorDefinition(in.interceptorStore(), target),
                in.interceptorActions()::setInterceptorEnabled,
                in.interceptorActions()::promptRenameInterceptor,
                in.interceptorActions()::confirmDeleteInterceptor));
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
      ServerTreeRequestStreams requestStreams,
      Predicate<TargetRef> canEditChannelModes) {}
}
