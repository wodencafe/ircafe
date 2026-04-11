package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.bouncer.BouncerAutoConnectStore;
import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.config.api.ServerTreeRuntimeConfigPort;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.interceptors.InterceptorScope;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.backend.BackendUiProfileProvider;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeChannelInteractionCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeChannelInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeCompositionAssembler;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLifecycleSettingsCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLifecycleSettingsCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeTargetLifecycleCoordinatorFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeTreeInteractionBindingsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeViewInteractionCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeViewInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeApplicationRootVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelInteractionApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelTargetOperations;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreePrivateMessageOnlineStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRequestApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRuntimeHeaderApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeSelectionBroadcastCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLeafVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLifecycleFacade;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetLifecycleCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetSelectionCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTypingActivityManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiRefreshCoordinator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeDragReorderSupport;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionSetupCoordinator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutOrchestrator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutVisibilityFacade;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutPersistenceCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeChannelListNodeEnsurer;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeNodeVisibilityMutator;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionFallbackPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionPersistencePolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeStartupSelectionRestorer;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeNodeAccess;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeServerNodeResolver;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestStreams;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeQuasselNetworkParentResolver;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerRuntimeMetadata;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeApplicationNodes;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilitySettings;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeExpansionStateManager;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeVisibilityApi;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeSettingsSynchronizer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeCellPresentationPolicy;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeCellRenderer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeHeaderControls;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTypingIndicatorStyle;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.util.TreeNodeActions;
import cafe.woden.ircclient.ui.util.TreeWheelSelectionDecorator;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;

@org.springframework.stereotype.Component
@InterfaceLayer
@Lazy
public class ServerTreeDockable extends JPanel implements Dockable, Scrollable {
  // UI label for the per-server "status" transcript target.
  // The target id remains "status" internally; this is just what the user sees in the tree.
  private static final String STATUS_LABEL = "Server";
  private static final String CHANNEL_LIST_LABEL = "Channel List";
  private static final String PRIVATE_MESSAGES_LABEL = "Private Messages";
  private static final String WEECHAT_FILTERS_LABEL = "Filters";
  private static final String IGNORES_LABEL = "Ignores";
  private static final String DCC_TRANSFERS_LABEL = "DCC Transfers";
  private static final String LOG_VIEWER_LABEL = "Log Viewer";
  private static final String MONITOR_GROUP_LABEL = "Monitor";
  private static final String INTERCEPTORS_GROUP_LABEL = "Interceptors";
  private static final String OTHER_GROUP_LABEL = "Other";
  private static final String BOUNCER_CONTROL_LABEL = "Bouncer Control";
  private static final String IRC_ROOT_LABEL = "IRC";
  private static final String APPLICATION_ROOT_LABEL = "Application";
  private static final int SERVER_ACTION_BUTTON_SIZE = 16;
  private static final int SERVER_ACTION_BUTTON_ICON_SIZE = 12;
  private static final int SERVER_ACTION_BUTTON_MARGIN = 6;
  private static final int TYPING_ACTIVITY_HOLD_MS = 8000;
  private static final int TYPING_ACTIVITY_FADE_MS = 900;
  private static final int TYPING_ACTIVITY_TICK_MS = 100;
  private static final int TREE_BADGE_SCALE_PERCENT_DEFAULT = 100;
  public static final String PROP_CHANNEL_LIST_NODES_VISIBLE = "channelListNodesVisible";
  public static final String PROP_DCC_TRANSFERS_NODES_VISIBLE = "dccTransfersNodesVisible";
  public static final String PROP_LOG_VIEWER_NODES_VISIBLE = "logViewerNodesVisible";
  public static final String PROP_NOTIFICATIONS_NODES_VISIBLE = "notificationsNodesVisible";
  public static final String PROP_MONITOR_NODES_VISIBLE = "monitorNodesVisible";
  public static final String PROP_INTERCEPTORS_NODES_VISIBLE = "interceptorsNodesVisible";
  public static final String PROP_APPLICATION_ROOT_VISIBLE = "applicationRootVisible";

  public enum ChannelSortMode {
    ALPHABETICAL,
    MOST_RECENT_ACTIVITY,
    MOST_UNREAD_MESSAGES,
    MOST_UNREAD_NOTIFICATIONS,
    CUSTOM
  }

  public record ManagedChannelEntry(
      String channel, boolean detached, boolean autoReattach, int notifications) {}

  public record ChannelModeSetRequest(TargetRef target, String modeSpec) {
    public ChannelModeSetRequest {
      modeSpec = Objects.toString(modeSpec, "").trim();
    }
  }

  private final CompositeDisposable disposables = new CompositeDisposable();
  public static final String ID = "server-tree";

  private AutoCloseable treeWheelSelectionDecorator;

  private final TreeNodeActions<TargetRef> nodeActions;
  private final ServerTreeSelectionBroadcastCoordinator selectionBroadcastCoordinator =
      new ServerTreeSelectionBroadcastCoordinator();
  private final ServerTreeRequestStreams requestStreams = new ServerTreeRequestStreams();
  private final ServerTreeRequestEmitter requestEmitter = requestStreams.requestEmitter();

  // Hidden top-level container. Visible top-level nodes are siblings: IRC + Application.
  private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("(root)");
  private final DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode(IRC_ROOT_LABEL);
  private final DefaultMutableTreeNode applicationRoot =
      new DefaultMutableTreeNode(APPLICATION_ROOT_LABEL);
  private final DefaultTreeModel model = new DefaultTreeModel(root);

  private final JTree tree =
      new JTree(model) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          if (ServerTreeDockable.this.dragReorderSupport != null) {
            ServerTreeDockable.this.dragReorderSupport.paintInsertionLine(g);
          }
          if (ServerTreeDockable.this.serverActionOverlay != null) {
            ServerTreeDockable.this.serverActionOverlay.paint(g);
          }
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
          JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
          if (vp == null) return false;
          return vp.getWidth() > getPreferredSize().width;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
          return ServerTreeDockable.this.tooltipResolver == null
              ? null
              : ServerTreeDockable.this.tooltipResolver.apply(event);
        }
      };
  private final JScrollPane treeScroll = new JScrollPane(tree);

  private final ServerTreeCellRenderer treeCellRenderer;
  private final ServerTreeServerActionOverlay serverActionOverlay;

  private final ServerTreeRowInteractionHandler rowInteractionHandler;
  private final ServerTreeDragReorderSupport dragReorderSupport;

  private final ServerTreeHeaderControls headerControls;
  private final ServerTreeNodeAccess nodeAccess;
  private final ServerTreeApplicationNodes applicationNodes;
  private final ServerTreeServerNodeResolver serverNodeResolver;
  private final ServerTreeServerNodeResolver.Context serverNodeResolverContext;

  private final Map<String, ServerNodes> servers = new HashMap<>();
  private final Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
  private final ServerTreeTargetSnapshotProvider targetSnapshotProvider =
      new ServerTreeTargetSnapshotProvider(leaves, root);
  private final ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore =
      new ServerTreePrivateMessageOnlineStateStore();
  private final ServerTreePrivateMessageOnlineStateCoordinator privateMessageOnlineStateCoordinator;
  private final ServerTreeChannelStateStore channelStateStore = new ServerTreeChannelStateStore();

  private final ServerTreeUiHooks uiHooks;
  private final ServerTreeRuntimeState runtimeState;

  private final Timer typingActivityTimer;
  private final Set<DefaultMutableTreeNode> typingActivityNodes = new HashSet<>();
  private final ServerTreeTypingActivityManager typingActivityManager;

  private static final int CAPABILITY_TRANSITION_LOG_LIMIT = 200;

  private final ServerTreeRuntimeConfigPort runtimeConfig;
  private final LogProperties logProps;

  private final Map<String, String> serverDisplayNames = new HashMap<>();
  private final Set<String> ephemeralServerIds = new HashSet<>();
  private final Map<String, Set<String>> bouncerControlServerIdsByBackendId =
      createBouncerControlServerIdsByBackendId();
  private final Map<String, Map<String, String>> originByServerIdByBackendId =
      createOriginByServerIdByBackendId();
  private final Map<String, Map<String, DefaultMutableTreeNode>> networksGroupByOriginByBackendId =
      createNetworksGroupByOriginByBackendId();
  private final Map<String, String> networksGroupLabelByBackendId =
      createNetworksGroupLabelByBackendId();

  private final ServerTreeNodeClassifier.Context nodeClassifierContext;

  private final InterceptorStore interceptorStore;
  private final JfrRuntimeEventsService jfrRuntimeEventsService;

  private final ServerTreeNetworkInfoDialogBuilder networkInfoDialogBuilder;
  private final ServerTreeInterceptorActions interceptorActions;
  private final ServerTreeInterceptorActions.Context interceptorActionsContext;
  private final ServerTreeServerCatalogSynchronizer serverCatalogSynchronizer;

  private final ServerTreeStatusLabelManager.Context statusLabelManagerContext;
  private final ServerTreeNetworkGroupManager networkGroupManager;
  private final ServerTreeServerStateCleaner serverStateCleaner;
  private final ServerTreeServerStateCleaner.Context serverStateCleanerContext;
  private final ServerTreeServerLifecycleFacade serverLifecycleFacade;

  private final ServerTreeServerParentResolver.Context serverParentResolverContext;
  private final ServerTreeServerLabelPolicy serverLabelPolicy;
  private final ServerTreeServerLabelPolicy.Context serverLabelPolicyContext;

  private final ServerTreeBouncerDetachPolicy.Context bouncerDetachPolicyContext;

  private final ServerTreeNodeBadgeUpdater.Context nodeBadgeUpdaterContext;
  private final ServerTreeSelectionFallbackPolicy selectionFallbackPolicy;
  private final ServerTreeSelectionFallbackPolicy.Context selectionFallbackContext;
  private final ServerTreeSelectionPersistencePolicy selectionPersistencePolicy;
  private final ServerTreeSelectionPersistencePolicy.Context selectionPersistenceContext;
  private final ServerTreeStartupSelectionRestorer startupSelectionRestorer;
  private final ServerTreeServerLeafVisibilityCoordinator serverLeafVisibilityCoordinator;
  private final ServerTreeUiLeafVisibilitySynchronizer uiLeafVisibilitySynchronizer;
  private final ServerTreeExpansionStateManager expansionStateManager;
  private final ServerTreeApplicationRootVisibilityCoordinator applicationRootVisibilityCoordinator;

  private final ServerTreeBuiltInLayoutVisibilityFacade builtInLayoutVisibilityFacade;
  private final ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings;
  private final ServerTreeNodeVisibilityApi nodeVisibilityApi;
  private final ServerTreeTargetNodePolicy targetNodePolicy;
  private final ServerTreeChannelStateCoordinator channelStateCoordinator;
  private final ServerTreeChannelQueryService channelQueryService;
  private final ServerTreeChannelTargetOperations channelTargetOperations;
  private final ServerTreeChannelInteractionApi channelInteractionApi;
  private final ServerTreeEnsureNodeParentResolver ensureNodeParentResolver;
  private final ServerTreeQuasselNetworkParentResolver quasselNetworkParentResolver;
  private final ServerTreeChannelListNodeEnsurer channelListNodeEnsurer;
  private final ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter;

  private final ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator;
  private final ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator;
  private final ServerTreeTargetLifecycleCoordinator targetLifecycleCoordinator;
  private final ServerTreeTargetSelectionCoordinator targetSelectionCoordinator;

  private final ServerTreeInteractionSetupCoordinator interactionSetupCoordinator;
  private final ServerTreeRequestApi requestApi;
  private final ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater;
  private final ServerTreeServerRuntimeUiUpdater.Context serverRuntimeUiUpdaterContext;
  private final ServerTreeRuntimeHeaderApi runtimeHeaderApi;
  private final ServerTreeUiRefreshCoordinator uiRefreshCoordinator;

  private final Function<MouseEvent, String> tooltipResolver;
  private final Function<TreePath, JPopupMenu> contextMenuBuilder;
  private final ServerTreeExternalStreamBinder externalStreamBinder;

  private final ServerTreeSettingsSynchronizer settingsSynchronizer;
  private final ServerCatalog serverCatalog;
  private final BackendUiProfileProvider backendUiProfileProvider;
  private volatile ServerTreeTypingIndicatorStyle typingIndicatorStyle =
      ServerTreeTypingIndicatorStyle.DOTS;
  private volatile boolean typingIndicatorsTreeEnabled = true;
  private volatile int unreadBadgeScalePercent = TREE_BADGE_SCALE_PERCENT_DEFAULT;
  private volatile boolean serverTreeNotificationBadgesEnabled = true;
  private volatile Color unreadChannelTextColor = null;
  private volatile Color highlightChannelTextColor = null;
  private volatile BiFunction<String, String, String> quasselNetworkTooltipProvider =
      (serverId, networkToken) -> "";
  private boolean startupSelectionCompleted = false;

  @org.springframework.beans.factory.annotation.Autowired
  public ServerTreeDockable(
      ServerCatalog serverCatalog,
      ServerTreeRuntimeConfigPort runtimeConfig,
      LogProperties logProps,
      GenericBouncerAutoConnectStore genericAutoConnect,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      UiSettingsBus settingsBus,
      ServerDialogs serverDialogs,
      BackendUiProfileProvider backendUiProfileProvider,
      JfrRuntimeEventsService jfrRuntimeEventsService,
      ServerTreeNetworkInfoDialogBuilder networkInfoDialogBuilder,
      ServerTreeCellPresentationPolicy cellPresentationPolicy,
      ServerTreeServerLabelPolicy serverLabelPolicy,
      ServerTreeBouncerDetachPolicy bouncerDetachPolicy,
      ServerTreeSelectionFallbackPolicy selectionFallbackPolicy,
      ServerTreeSelectionPersistencePolicy selectionPersistencePolicy,
      ServerTreeTargetNodePolicy targetNodePolicy,
      ServerTreeServerNodeResolver serverNodeResolver,
      ServerTreeNodeClassifier nodeClassifier,
      ServerTreeServerParentResolver serverParentResolver,
      ServerTreeStatusLabelManager statusLabelManager,
      ServerTreeNodeBadgeUpdater nodeBadgeUpdater,
      ServerTreeInterceptorActions interceptorActions,
      ServerTreeEdtExecutor edtExecutor,
      ServerTreeCompositionAssembler compositionAssembler) {
    super(new BorderLayout());

    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;
    this.serverCatalog = serverCatalog;
    this.backendUiProfileProvider = backendUiProfileProvider;

    this.interceptorStore = interceptorStore;
    this.jfrRuntimeEventsService = jfrRuntimeEventsService;

    this.runtimeState =
        new ServerTreeRuntimeState(
            CAPABILITY_TRANSITION_LOG_LIMIT, this::clearPrivateMessageOnlineStates);
    this.uiHooks =
        new ServerTreeDockableUiHooks(
            this,
            servers,
            leaves,
            runtimeState::connectionStateForServer,
            this::isChannelDisconnected,
            requestEmitter);
    this.nodeAccess =
        new ServerTreeNodeAccess(tree, root, ircRoot, applicationRoot, uiHooks::isServerNode);
    this.applicationNodes = new ServerTreeApplicationNodes(applicationRoot, leaves);
    this.serverNodeResolver = Objects.requireNonNull(serverNodeResolver, "serverNodeResolver");
    this.serverNodeResolverContext =
        ServerTreeServerNodeResolver.context(
            servers, leaves, ServerTreeDockable::normalizeServerId);

    this.nodeClassifierContext =
        ServerTreeNodeClassifier.context(
            PRIVATE_MESSAGES_LABEL,
            INTERCEPTORS_GROUP_LABEL,
            MONITOR_GROUP_LABEL,
            OTHER_GROUP_LABEL,
            uiHooks::isServerNode);

    ServerTreeLayoutCollaborators layoutCollaborators =
        compositionAssembler.createLayoutCollaborators(
            runtimeConfig,
            ServerTreeBuiltInVisibilityCoordinator.context(
                ServerTreeDockable::normalizeServerId, servers::keySet, this::syncUiLeafVisibility),
            ServerTreeLayoutPersistenceCoordinator.context(
                this::rootSiblingNodeKindForNode,
                this::builtInLayoutNodeKindForNode,
                this::rootSiblingOrder,
                this::builtInLayout,
                this::persistRootSiblingOrderForServer,
                this::persistBuiltInLayoutForServer),
            ServerTreeBuiltInLayoutOrchestrator.context(
                ServerTreeDockable::normalizeServerId,
                serverId -> servers.get(ServerTreeDockable.normalizeServerId(serverId)),
                leaves::get,
                this::builtInNodesVisibility,
                this::rootSiblingNodeKindForNode,
                model::nodeStructureChanged));
    this.builtInLayoutVisibilityFacade =
        new ServerTreeBuiltInLayoutVisibilityFacade(
            layoutCollaborators.builtInVisibilityCoordinator(),
            layoutCollaborators.builtInLayoutCoordinator(),
            layoutCollaborators.rootSiblingOrderCoordinator(),
            layoutCollaborators.builtInLayoutOrchestrator(),
            node -> nodeClassifier.isMonitorGroupNode(nodeClassifierContext, node),
            node -> nodeClassifier.isInterceptorsGroupNode(nodeClassifierContext, node),
            node -> nodeClassifier.isOtherGroupNode(nodeClassifierContext, node),
            node -> nodeClassifier.isPrivateMessagesGroupNode(nodeClassifierContext, node),
            nodeAccess::targetRefForNode);
    this.builtInVisibilitySettings =
        new ServerTreeBuiltInVisibilitySettings(
            new ServerTreeBuiltInVisibilitySettings.Context() {
              @Override
              public String normalizeServerId(String serverId) {
                return ServerTreeDockable.normalizeServerId(serverId);
              }

              @Override
              public ServerBuiltInNodesVisibility defaultVisibility() {
                return builtInLayoutVisibilityFacade.defaultVisibility();
              }

              @Override
              public void setDefaultVisibility(ServerBuiltInNodesVisibility next) {
                builtInLayoutVisibilityFacade.setDefaultVisibility(next);
              }

              @Override
              public ServerBuiltInNodesVisibility visibilityForServer(String serverId) {
                return builtInNodesVisibility(serverId);
              }

              @Override
              public void applyVisibilityForServer(
                  String serverId,
                  ServerBuiltInNodesVisibility next,
                  boolean persist,
                  boolean syncUi) {
                builtInLayoutVisibilityFacade.applyBuiltInNodesVisibilityForServer(
                    serverId, next, persist, syncUi);
              }

              @Override
              public void applyVisibilityGlobally(
                  java.util.function.UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
                builtInLayoutVisibilityFacade.applyBuiltInNodesVisibilityGlobally(mutator);
              }

              @Override
              public void firePropertyChange(
                  String propertyName, boolean oldValue, boolean newValue) {
                ServerTreeDockable.this.firePropertyChange(propertyName, oldValue, newValue);
              }
            });
    this.nodeVisibilityApi =
        new ServerTreeNodeVisibilityApi(
            builtInVisibilitySettings,
            this::syncUiLeafVisibility,
            this::syncApplicationRootVisibility,
            this::firePropertyChange,
            PROP_CHANNEL_LIST_NODES_VISIBLE,
            PROP_DCC_TRANSFERS_NODES_VISIBLE,
            PROP_LOG_VIEWER_NODES_VISIBLE,
            PROP_NOTIFICATIONS_NODES_VISIBLE,
            PROP_MONITOR_NODES_VISIBLE,
            PROP_INTERCEPTORS_NODES_VISIBLE,
            PROP_APPLICATION_ROOT_VISIBLE);
    Map<String, BouncerAutoConnectStore> autoConnectStoreByBackendId =
        createAutoConnectStoreByBackendId(genericAutoConnect, sojuAutoConnect, zncAutoConnect);
    this.serverLabelPolicy = Objects.requireNonNull(serverLabelPolicy, "serverLabelPolicy");
    this.serverLabelPolicyContext =
        ServerTreeServerLabelPolicy.context(
            serverDisplayNames,
            ephemeralServerIds,
            originByServerIdByBackendId,
            autoConnectStoreByBackendId);

    this.networkInfoDialogBuilder =
        Objects.requireNonNull(networkInfoDialogBuilder, "networkInfoDialogBuilder");
    this.interceptorActions = Objects.requireNonNull(interceptorActions, "interceptorActions");
    this.interceptorActionsContext =
        ServerTreeInterceptorActions.context(
            this,
            interceptorStore,
            INTERCEPTORS_GROUP_LABEL,
            this::ensureNode,
            this::selectTarget,
            this::removeTarget,
            leaves::get,
            serverId ->
                serverNodeResolver.interceptorsNodeForServer(serverNodeResolverContext, serverId),
            model::nodeChanged);
    this.serverCatalogSynchronizer =
        new ServerTreeServerCatalogSynchronizer(
            serverDisplayNames,
            ephemeralServerIds,
            bouncerControlServerIdsByBackendId,
            originByServerIdByBackendId,
            ServerTreeServerCatalogSynchronizer.context(
                tree,
                servers,
                leaves,
                model,
                root,
                () -> startupSelectionCompleted,
                () -> startupSelectionCompleted = true,
                this::selectedTargetRef,
                this::addServerRoot,
                this::removeServerRoot,
                this::updateBouncerControlLabels,
                this::snapshotExpandedTreePaths,
                this::restoreExpandedTreePaths,
                nodeAccess::hasValidTreeSelection,
                this::selectTarget,
                this::firstServerIdOrEmpty,
                this::selectStartupDefaultForServer,
                this::defaultSelectionPath));

    this.statusLabelManagerContext =
        ServerTreeStatusLabelManager.context(
            STATUS_LABEL,
            BOUNCER_CONTROL_LABEL,
            bouncerControlServerIdsByBackendId,
            leaves,
            model::nodeChanged);
    this.networkGroupManager =
        new ServerTreeNetworkGroupManager(
            networksGroupLabelByBackendId,
            networksGroupByOriginByBackendId,
            ServerTreeNetworkGroupManager.context(
                serverId ->
                    serverNodeResolver.serverNodeForServer(serverNodeResolverContext, serverId),
                serverId ->
                    serverNodeResolver.privateMessagesNodeForServer(
                        serverNodeResolverContext, serverId)));
    this.dragReorderSupport =
        new ServerTreeDragReorderSupport(
            tree,
            servers,
            nodeClassifier,
            nodeClassifierContext,
            uiHooks::isServerNode,
            nodeAccess::isChannelListLeafNode,
            this::builtInLayoutNodeKindForNode,
            this::rootSiblingNodeKindForNode,
            networkGroupManager::backendIdForNetworksGroupNode);
    ServerTreeStateInteractionCollaborators stateInteractionCollaborators =
        compositionAssembler.createStateInteractionCollaborators(
            new ServerTreeStateInteractionCollaboratorsFactory.Inputs(
                tree,
                model,
                runtimeConfig,
                runtimeConfig,
                channelStateStore,
                interceptorStore,
                runtimeState,
                servers,
                leaves,
                typingActivityNodes,
                privateMessageOnlineStateStore,
                this::isPrivateMessageTarget,
                uiHooks::isServerNode,
                this::clearChannelDisconnectedWarning,
                () -> ServerTreeCellRenderer.typingSlotWidthForStyle(typingIndicatorStyle),
                this::clearPrivateMessageOnlineStates,
                ServerTreeServerActionOverlay.context(uiHooks),
                ServerTreeChannelStateCoordinator.context(
                    ServerTreeDockable::normalizeServerId,
                    serverId ->
                        serverNodeResolver.channelListNodeForServer(
                            serverNodeResolverContext, serverId),
                    this::snapshotExpandedTreePaths,
                    this::restoreExpandedTreePaths,
                    this::emitManagedChannelsChanged),
                ServerTreeTargetRemovalStateCoordinator.context(
                    this::isPrivateMessageTarget,
                    this::shouldPersistPrivateMessageList,
                    ServerTreeDockable::foldChannelKey,
                    this::emitManagedChannelsChanged),
                SERVER_ACTION_BUTTON_SIZE,
                SERVER_ACTION_BUTTON_ICON_SIZE,
                SERVER_ACTION_BUTTON_MARGIN));
    this.serverActionOverlay = stateInteractionCollaborators.serverActionOverlay();
    this.serverRuntimeUiUpdater = stateInteractionCollaborators.serverRuntimeUiUpdater();
    this.serverRuntimeUiUpdaterContext =
        stateInteractionCollaborators.serverRuntimeUiUpdaterContext();
    this.serverStateCleaner = stateInteractionCollaborators.serverStateCleaner();
    this.serverStateCleanerContext = stateInteractionCollaborators.serverStateCleanerContext();
    this.serverParentResolverContext =
        ServerTreeServerParentResolver.context(
            originByServerIdByBackendId,
            serverId -> this.serverNodeResolver.hasServer(serverNodeResolverContext, serverId),
            this::addServerRoot,
            () -> ircRoot,
            networkGroupManager::getOrCreateNetworksGroupNode);

    this.bouncerDetachPolicyContext =
        ServerTreeBouncerDetachPolicy.context(
            bouncerControlServerIdsByBackendId,
            uiHooks::connectionStateForServer,
            serverId ->
                serverLabelPolicy.backendIdForEphemeralServer(serverLabelPolicyContext, serverId),
            (serverId, capability) -> {
              String sid = normalizeServerId(serverId);
              if (sid.isBlank()) return false;
              ServerRuntimeMetadata metadata = runtimeState.metadataForServerIfPresent(sid);
              if (metadata == null) return false;
              String cap =
                  Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
              if (cap.isEmpty()) return false;
              ServerRuntimeMetadata.CapabilityState state = metadata.ircv3Caps.get(cap);
              return state == ServerRuntimeMetadata.CapabilityState.ENABLED
                  || state == ServerRuntimeMetadata.CapabilityState.AVAILABLE
                  || state == ServerRuntimeMetadata.CapabilityState.DISABLED;
            });

    this.nodeBadgeUpdaterContext =
        ServerTreeNodeBadgeUpdater.context(
            notificationStore,
            ephemeralServerIds,
            leaves,
            model::nodeChanged,
            serverId ->
                serverNodeResolver.serverNodeForServer(serverNodeResolverContext, serverId));
    this.externalStreamBinder =
        new ServerTreeExternalStreamBinder(
            disposables,
            serverCatalogSynchronizer::syncServers,
            serverId ->
                nodeBadgeUpdater.refreshNotificationsCount(nodeBadgeUpdaterContext, serverId),
            (serverId, interceptorId) ->
                interceptorActions.refreshInterceptorNodeLabel(
                    interceptorActionsContext, serverId, interceptorId),
            serverId ->
                interceptorActions.refreshInterceptorGroupCount(
                    interceptorActionsContext, serverId),
            backendId ->
                nodeBadgeUpdater.refreshAutoConnectBadges(nodeBadgeUpdaterContext, backendId));
    this.quasselNetworkParentResolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            this::isQuasselServer,
            CHANNEL_LIST_LABEL,
            PRIVATE_MESSAGES_LABEL,
            OTHER_GROUP_LABEL,
            MONITOR_GROUP_LABEL,
            INTERCEPTORS_GROUP_LABEL,
            IGNORES_LABEL);
    this.selectionFallbackPolicy =
        Objects.requireNonNull(selectionFallbackPolicy, "selectionFallbackPolicy");
    this.selectionFallbackContext =
        ServerTreeSelectionFallbackPolicy.context(
            ServerTreeDockable::normalizeServerId,
            servers,
            this::builtInNodesVisibility,
            leaves,
            this::selectTarget,
            tree);
    this.selectionPersistencePolicy =
        Objects.requireNonNull(selectionPersistencePolicy, "selectionPersistencePolicy");
    this.selectionPersistenceContext =
        ServerTreeSelectionPersistencePolicy.context(
            selectionBroadcastCoordinator::lastBroadcastSelectionRef,
            this::selectedTargetRef,
            () -> (DefaultMutableTreeNode) tree.getLastSelectedPathComponent(),
            node -> nodeClassifier.owningServerIdForNode(nodeClassifierContext, node),
            node -> nodeClassifier.isMonitorGroupNode(nodeClassifierContext, node),
            node -> nodeClassifier.isInterceptorsGroupNode(nodeClassifierContext, node),
            quasselNetworkParentResolver::channelListRefForNetworkNode);
    this.startupSelectionRestorer =
        new ServerTreeStartupSelectionRestorer(
            ServerTreeStartupSelectionRestorer.readRememberedSelection(runtimeConfig),
            ServerTreeStartupSelectionRestorer.context(
                ServerTreeDockable::normalizeServerId,
                ref -> ref != null && leaves.containsKey(ref),
                serverId ->
                    isServerGroupNodeSelectable(
                        serverNodeResolver, serverNodeResolverContext, serverId, true),
                serverId ->
                    isServerGroupNodeSelectable(
                        serverNodeResolver, serverNodeResolverContext, serverId, false),
                this::selectTarget));
    ServerTreeNodeVisibilityMutator nodeVisibilityMutator =
        new ServerTreeNodeVisibilityMutator(model, leaves, typingActivityNodes);
    this.serverLeafVisibilityCoordinator =
        new ServerTreeServerLeafVisibilityCoordinator(
            CHANNEL_LIST_LABEL,
            WEECHAT_FILTERS_LABEL,
            IGNORES_LABEL,
            DCC_TRANSFERS_LABEL,
            "Notifications",
            LOG_VIEWER_LABEL,
            nodeVisibilityMutator,
            ServerTreeDockable::normalizeServerId,
            serverId ->
                serverNodeResolver.serverNodesForServer(serverNodeResolverContext, serverId),
            this::builtInNodesVisibility,
            this::builtInLayout,
            this::rootSiblingOrder,
            this::applyBuiltInLayoutToTree,
            this::applyRootSiblingOrderToTree,
            serverId ->
                statusLabelManager.statusLeafLabelForServer(statusLabelManagerContext, serverId),
            this::isQuasselServer,
            nodeVisibilityApi::isDccTransfersNodesVisible,
            leaves::get);
    this.uiLeafVisibilitySynchronizer =
        new ServerTreeUiLeafVisibilitySynchronizer(
            ServerTreeUiLeafVisibilitySynchronizer.context(
                this::selectedTargetRef,
                () -> (DefaultMutableTreeNode) tree.getLastSelectedPathComponent(),
                node -> nodeClassifier.isMonitorGroupNode(nodeClassifierContext, node),
                node -> nodeClassifier.isInterceptorsGroupNode(nodeClassifierContext, node),
                node -> nodeClassifier.owningServerIdForNode(nodeClassifierContext, node),
                () -> List.copyOf(servers.keySet()),
                serverLeafVisibilityCoordinator::syncUiLeafVisibilityForServer,
                serverId -> builtInNodesVisibility(serverId).server(),
                serverId -> builtInNodesVisibility(serverId).notifications(),
                serverId -> builtInNodesVisibility(serverId).logViewer(),
                serverId -> builtInNodesVisibility(serverId).monitor(),
                serverId -> builtInNodesVisibility(serverId).interceptors(),
                nodeVisibilityApi::isDccTransfersNodesVisible,
                this::selectBestFallbackForServer));
    this.expansionStateManager =
        new ServerTreeExpansionStateManager(tree, root, ircRoot, applicationRoot);
    this.applicationRootVisibilityCoordinator =
        new ServerTreeApplicationRootVisibilityCoordinator(
            ServerTreeApplicationRootVisibilityCoordinator.context(
                this::snapshotExpandedTreePaths,
                this::restoreExpandedTreePaths,
                nodeVisibilityApi::isApplicationRootVisible,
                () -> applicationRoot.getParent() == root,
                root::getChildCount,
                index ->
                    root.insert(
                        applicationRoot, Math.max(0, Math.min(index, root.getChildCount()))),
                () -> root.remove(applicationRoot),
                () -> model.nodeStructureChanged(root),
                () -> tree.expandPath(new TreePath(applicationRoot.getPath())),
                this::selectedTargetRef,
                () -> serverNodeResolver.firstServerStatusRefOrNull(serverNodeResolverContext),
                ref -> {
                  if (ref != null) {
                    selectTarget(ref);
                  }
                },
                () -> tree.setSelectionPath(defaultSelectionPath())));
    this.targetNodePolicy = Objects.requireNonNull(targetNodePolicy, "targetNodePolicy");
    this.privateMessageOnlineStateCoordinator =
        new ServerTreePrivateMessageOnlineStateCoordinator(
            privateMessageOnlineStateStore,
            leaves,
            model::nodeChanged,
            this::isPrivateMessageTarget);
    this.channelStateCoordinator = stateInteractionCollaborators.channelStateCoordinator();
    this.channelQueryService =
        new ServerTreeChannelQueryService(
            edtExecutor,
            targetSnapshotProvider,
            channelStateCoordinator,
            ServerTreeDockable::normalizeServerId);
    this.channelTargetOperations =
        new ServerTreeChannelTargetOperations(
            edtExecutor, channelStateCoordinator, requestEmitter, (ref, muted) -> {});
    this.ensureNodeParentResolver = stateInteractionCollaborators.ensureNodeParentResolver();
    this.channelListNodeEnsurer =
        new ServerTreeChannelListNodeEnsurer(CHANNEL_LIST_LABEL, leaves, model::nodesWereInserted);
    this.ensureNodeLeafInserter = stateInteractionCollaborators.ensureNodeLeafInserter();
    this.targetNodeRemovalMutator = stateInteractionCollaborators.targetNodeRemovalMutator();
    this.targetRemovalStateCoordinator =
        stateInteractionCollaborators.targetRemovalStateCoordinator();

    this.rowInteractionHandler = stateInteractionCollaborators.rowInteractionHandler();
    ServerTreeViewInteractionCollaborators viewInteractionCollaborators =
        compositionAssembler.createViewInteractionCollaborators(
            new ServerTreeViewInteractionCollaboratorsFactory.Inputs(
                tree,
                rowInteractionHandler,
                uiHooks,
                nodeAccess,
                networkGroupManager,
                nodeClassifier,
                nodeClassifierContext,
                runtimeState,
                serverLabelPolicy,
                serverLabelPolicyContext,
                serverDisplayNames,
                serverId ->
                    backendUiProfileProvider == null
                        ? ""
                        : backendUiProfileProvider.backendDisplayNameForServer(serverId),
                bouncerControlServerIdsByBackendId,
                originByServerIdByBackendId,
                autoConnectStoreByBackendId,
                this::isApplicationJfrActive,
                serverActionOverlay,
                serverCatalog,
                this::moveNodeUpAction,
                this::moveNodeDownAction,
                this::openServerInfoDialog,
                requestEmitter,
                interceptorStore,
                interceptorActions,
                interceptorActionsContext,
                serverDialogs,
                this,
                runtimeConfig,
                nodeBadgeUpdater,
                nodeBadgeUpdaterContext,
                bouncerDetachPolicy,
                bouncerDetachPolicyContext,
                this::isChannelDisconnected,
                this::isChannelAutoReattach,
                this::setChannelAutoReattach,
                this::isChannelPinned,
                this::setChannelPinned,
                this::isChannelMuted,
                this::setChannelMuted,
                requestStreams,
                channelTargetOperations::canEditChannelModesForTarget,
                this::isQuasselServer,
                quasselNetworkParentResolver::isQuasselNetworkNode,
                quasselNetworkParentResolver::isQuasselEmptyStateNode,
                this::quasselNetworkTooltip));

    this.tooltipResolver = viewInteractionCollaborators.tooltipResolver();
    this.contextMenuBuilder = viewInteractionCollaborators.contextMenuBuilder();
    ServerTreeLifecycleSettingsCollaborators lifecycleSettingsCollaborators =
        compositionAssembler.createLifecycleSettingsCollaborators(
            new ServerTreeLifecycleSettingsCollaboratorsFactory.Inputs(
                CHANNEL_LIST_LABEL,
                WEECHAT_FILTERS_LABEL,
                IGNORES_LABEL,
                DCC_TRANSFERS_LABEL,
                LOG_VIEWER_LABEL,
                MONITOR_GROUP_LABEL,
                INTERCEPTORS_GROUP_LABEL,
                ServerTreeDockable::normalizeServerId,
                servers,
                runtimeState,
                channelStateCoordinator,
                serverParentResolver,
                serverParentResolverContext,
                this::builtInNodesVisibility,
                nodeVisibilityApi::isDccTransfersNodesVisible,
                statusLabelManager,
                statusLabelManagerContext,
                notificationStore,
                interceptorStore,
                leaves,
                this::builtInLayout,
                this::rootSiblingOrder,
                this::applyBuiltInLayoutToTree,
                this::applyRootSiblingOrderToTree,
                model,
                root,
                tree,
                nodeBadgeUpdater,
                nodeBadgeUpdaterContext,
                interceptorActions,
                interceptorActionsContext,
                serverStateCleaner,
                serverStateCleanerContext,
                networkGroupManager,
                settingsBus,
                jfrRuntimeEventsService,
                runtimeConfig,
                () -> typingIndicatorsTreeEnabled,
                enabled -> typingIndicatorsTreeEnabled = enabled,
                this::clearTypingIndicatorsIfReady,
                style -> typingIndicatorStyle = style,
                enabled -> serverTreeNotificationBadgesEnabled = enabled,
                percent -> unreadBadgeScalePercent = percent,
                color -> unreadChannelTextColor = color,
                color -> highlightChannelTextColor = color,
                this::refreshTreeLayoutAfterUiChange,
                this::refreshApplicationJfrNode,
                TREE_BADGE_SCALE_PERCENT_DEFAULT));

    this.serverLifecycleFacade = lifecycleSettingsCollaborators.serverLifecycleFacade();
    this.settingsSynchronizer = lifecycleSettingsCollaborators.settingsSynchronizer();
    this.targetLifecycleCoordinator =
        compositionAssembler.createTargetLifecycleCoordinator(
            new ServerTreeTargetLifecycleCoordinatorFactory.Inputs(
                servers,
                leaves,
                serverCatalog,
                ensureNodeParentResolver,
                ensureNodeLeafInserter,
                targetNodePolicy,
                targetSnapshotProvider,
                targetRemovalStateCoordinator,
                targetNodeRemovalMutator,
                nodeVisibilityApi::isApplicationRootVisible,
                this::setApplicationRootVisible,
                applicationNodes::labelFor,
                applicationNodes::addLeaf,
                () -> model.nodeStructureChanged(applicationRoot),
                nodeVisibilityApi::isDccTransfersNodesVisible,
                this::setDccTransfersNodesVisible,
                this::builtInNodesVisibility,
                this::addServerRoot,
                this::builtInLayoutNodeKindForRef,
                this::builtInLayout,
                this::rootSiblingOrder,
                this::backendSpecificParent,
                this::ensureChannelListNodeForEnsureNode,
                this::applyBuiltInLayoutToTree,
                this::applyRootSiblingOrderToTree,
                this::persistBuiltInLayoutFromTree,
                this::isPrivateMessageTarget,
                this::shouldPersistPrivateMessageList,
                (serverId, target) -> {
                  if (runtimeConfig == null) return;
                  runtimeConfig.rememberPrivateMessageTarget(serverId, target);
                },
                channelStateCoordinator::ensureChannelKnownInConfig,
                channelStateCoordinator::sortChannelsUnderChannelList,
                this::emitManagedChannelsChanged,
                ServerTreeDockable::normalizeServerId,
                parentNode -> tree.expandPath(new TreePath(parentNode.getPath())),
                () -> model.reload(root)));
    builtInLayoutVisibilityFacade.loadPersistedBuiltInNodesVisibility();
    settingsSynchronizer.applyInitialSettings();
    this.treeCellRenderer =
        new ServerTreeCellRenderer(
            IRC_ROOT_LABEL,
            APPLICATION_ROOT_LABEL,
            cellPresentationPolicy,
            ServerTreeCellPresentationPolicy.context(
                () -> serverTreeNotificationBadgesEnabled,
                () -> unreadBadgeScalePercent,
                () -> typingIndicatorStyle,
                () -> typingIndicatorsTreeEnabled,
                this::isPrivateMessageTarget,
                privateMessageOnlineStateStore::isOnline,
                this::isChannelPinned,
                this::isChannelMuted,
                () -> unreadChannelTextColor,
                () -> highlightChannelTextColor,
                this::isApplicationJfrActive,
                this::isInterceptorEnabled,
                node -> nodeClassifier.isMonitorGroupNode(nodeClassifierContext, node),
                node -> nodeClassifier.isInterceptorsGroupNode(nodeClassifierContext, node),
                node -> nodeClassifier.isOtherGroupNode(nodeClassifierContext, node),
                uiHooks::isServerNode,
                this::serverNodeDisplayLabel,
                ephemeralServerIds::contains,
                uiHooks::connectionStateForServer,
                nodeAccess::isIrcRootNode,
                nodeAccess::isApplicationRootNode,
                node -> nodeClassifier.isPrivateMessagesGroupNode(nodeClassifierContext, node),
                networkGroupManager::backendIdForNetworksGroupNode,
                quasselNetworkParentResolver::isQuasselNetworkNode,
                quasselNetworkParentResolver::isQuasselEmptyStateNode));
    this.uiRefreshCoordinator =
        new ServerTreeUiRefreshCoordinator(
            tree,
            model,
            root,
            treeCellRenderer,
            this::snapshotExpandedTreePaths,
            this::restoreExpandedTreePaths);

    this.headerControls =
        new ServerTreeHeaderControls(this, connectBtn, disconnectBtn, serverDialogs);
    this.runtimeHeaderApi =
        new ServerTreeRuntimeHeaderApi(
            serverRuntimeUiUpdater, serverRuntimeUiUpdaterContext, headerControls);
    JPanel header = headerControls.panel();

    root.add(ircRoot);
    applicationNodes.initialize();
    if (nodeVisibilityApi.isApplicationRootVisible()) {
      root.add(applicationRoot);
    }

    add(header, BorderLayout.NORTH);
    setConnectionControlsEnabled(true, false);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(0);
    ServerTreeUiRefreshCoordinator.applyTreeFontFromUiDefaults(tree);

    tree.setCellRenderer(treeCellRenderer);
    ServerTreeChannelInteractionCollaborators channelInteractionCollaborators =
        compositionAssembler.createChannelInteractionCollaborators(
            new ServerTreeChannelInteractionCollaboratorsFactory.Inputs(
                tree,
                model,
                leaves,
                typingActivityNodes,
                TYPING_ACTIVITY_TICK_MS,
                TYPING_ACTIVITY_HOLD_MS,
                TYPING_ACTIVITY_FADE_MS,
                this::onTypingActivityAnimationTick,
                () -> typingIndicatorsTreeEnabled,
                () -> ServerTreeDockable.this.isShowing() && tree.isShowing(),
                this::repaintTreeNode,
                this::ensureNode,
                leaves::get,
                this::emitManagedChannelsChanged,
                serverId ->
                    serverNodeResolver.monitorNodeForServer(serverNodeResolverContext, serverId),
                serverId ->
                    serverNodeResolver.interceptorsNodeForServer(
                        serverNodeResolverContext, serverId),
                serverId ->
                    serverNodeResolver.serverNodesForServer(serverNodeResolverContext, serverId),
                this::isChannelMuted,
                channelStateCoordinator::noteChannelActivity,
                channelStateCoordinator::onChannelUnreadCountsChanged,
                channelQueryService,
                channelTargetOperations));
    this.typingActivityTimer = channelInteractionCollaborators.typingActivityTimer();
    this.typingActivityManager = channelInteractionCollaborators.typingActivityManager();

    this.targetSelectionCoordinator = channelInteractionCollaborators.targetSelectionCoordinator();

    this.channelInteractionApi = channelInteractionCollaborators.channelInteractionApi();
    this.nodeActions =
        compositionAssembler.createTreeInteractionBindings(
            new ServerTreeTreeInteractionBindingsFactory.Inputs(
                tree,
                model,
                uiHooks::isServerNode,
                nodeAccess::isChannelListLeafNode,
                this::isChannelPinned,
                nodeAccess::targetRefForNode,
                nodeAccess::nodeLabelForNode,
                networkGroupManager::backendIdForNetworksGroupNode,
                this::isChannelDisconnected,
                requestEmitter::emitDisconnectChannel,
                requestEmitter::emitCloseTarget,
                dragReorderSupport::isRootSiblingReorderableNode,
                dragReorderSupport::isMovableBuiltInNode,
                node -> nodeClassifier.owningServerIdForNode(nodeClassifierContext, node),
                channelStateCoordinator::persistOrderAndResortAfterManualMove,
                this::persistRootSiblingOrderFromTree,
                this::persistBuiltInLayoutFromTree,
                this::refreshTreeLayoutAfterUiChange,
                this::openSelectedNodeInChatDock));

    treeScroll.setPreferredSize(new Dimension(260, 400));
    treeScroll.setMinimumSize(new Dimension(0, 0));
    enforceTreeScrollPanePolicies();
    treeWheelSelectionDecorator = TreeWheelSelectionDecorator.decorate(tree, treeScroll);
    add(treeScroll, BorderLayout.CENTER);
    externalStreamBinder.bind(
        serverCatalog,
        notificationStore,
        interceptorStore,
        sojuAutoConnect,
        zncAutoConnect,
        genericAutoConnect);

    settingsSynchronizer.bindListeners();
    this.interactionSetupCoordinator =
        ServerTreeInteractionSetupCoordinator.create(
            new ServerTreeInteractionSetupCoordinator.Inputs(
                tree,
                model,
                dragReorderSupport,
                nodeAccess::isChannelListLeafNode,
                node -> nodeClassifier.owningServerIdForNode(nodeClassifierContext, node),
                serverId -> servers.get(ServerTreeDockable.normalizeServerId(serverId)),
                this::rootSiblingNodeKindForNode,
                this::builtInLayoutNodeKindForNode,
                builtInLayoutVisibilityFacade::rootBuiltInInsertIndex,
                channelStateCoordinator::persistOrderAndResortAfterManualMove,
                this::persistBuiltInLayoutFromTree,
                this::persistRootSiblingOrderFromTree,
                selectionBroadcastCoordinator::withSuppressedSelectionBroadcast,
                nodeActions::refreshEnabledState,
                (x, y) -> channelTargetForTreePath(rowInteractionHandler.treePathForRowHit(x, y)),
                serverActionOverlay,
                showing -> {
                  if (showing) {
                    typingActivityManager.startTypingActivityTimerIfNeeded();
                    tree.repaint();
                    return;
                  }
                  typingActivityTimer.stop();
                },
                selectionBroadcastCoordinator::suppressSelectionBroadcast,
                selectionBroadcastCoordinator::publishSelection,
                node -> nodeClassifier.isMonitorGroupNode(nodeClassifierContext, node),
                node -> nodeClassifier.isInterceptorsGroupNode(nodeClassifierContext, node),
                rowInteractionHandler::maybeHandleDisconnectedWarningClick,
                rowInteractionHandler::maybeSelectRowFromLeftClick,
                rowInteractionHandler::treePathForRowHit,
                contextMenuBuilder,
                () -> startupSelectionCompleted,
                () -> startupSelectionCompleted = true,
                this::isPathInCurrentTreeModel,
                this::firstServerIdOrEmpty,
                this::selectStartupDefaultForServer,
                this::defaultSelectionPath));
    this.interactionSetupCoordinator.install();
    this.requestApi =
        new ServerTreeRequestApi(
            selectionBroadcastCoordinator, requestStreams, interactionSetupCoordinator);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    SwingUtilities.invokeLater(this::enforceTreeScrollPanePolicies);
  }

  private void enforceTreeScrollPanePolicies() {
    treeScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    treeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    if (treeScroll.getVerticalScrollBar() != null) {
      treeScroll.getVerticalScrollBar().setUnitIncrement(16);
    }
  }

  private static String normalizeServerId(String serverId) {
    return ServerTreeConventions.normalizeServerId(serverId);
  }

  private static boolean isServerGroupNodeSelectable(
      ServerTreeServerNodeResolver serverNodeResolver,
      ServerTreeServerNodeResolver.Context serverNodeResolverContext,
      String serverId,
      boolean monitorGroup) {
    if (serverNodeResolver == null) return false;
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    ServerNodes nodes = serverNodeResolver.serverNodesForServer(serverNodeResolverContext, sid);
    DefaultMutableTreeNode node =
        monitorGroup
            ? serverNodeResolver.monitorNodeForServer(serverNodeResolverContext, sid)
            : serverNodeResolver.interceptorsNodeForServer(serverNodeResolverContext, sid);
    if (nodes == null || node == null) return false;
    return node.getParent() == nodes.serverNode || node.getParent() == nodes.otherNode;
  }

  private ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    return builtInLayoutVisibilityFacade.builtInNodesVisibility(serverId);
  }

  private ServerTreeBuiltInLayout builtInLayout(String serverId) {
    return builtInLayoutVisibilityFacade.builtInLayout(serverId);
  }

  private void persistBuiltInLayoutForServer(String serverId, ServerTreeBuiltInLayout layout) {
    builtInLayoutVisibilityFacade.rememberBuiltInLayout(serverId, layout);
  }

  private ServerTreeRootSiblingOrder rootSiblingOrder(String serverId) {
    return builtInLayoutVisibilityFacade.rootSiblingOrder(serverId);
  }

  private void persistRootSiblingOrderForServer(String serverId, ServerTreeRootSiblingOrder order) {
    builtInLayoutVisibilityFacade.rememberRootSiblingOrder(serverId, order);
  }

  private TargetRef channelTargetForTreePath(TreePath path) {
    if (path == null) return null;
    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return null;
    TargetRef ref = nodeAccess.targetRefForNode(node);
    if (ref == null || !ref.isChannel()) return null;
    return ref;
  }

  private String serverNodeDisplayLabel(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return sid;
    String base = serverLabelPolicy.prettyServerLabel(serverLabelPolicyContext, sid);
    ConnectionState state = runtimeState.connectionStateForServer(sid);
    boolean desired = runtimeState.desiredOnlineForServer(sid);
    String badge = ServerTreeConnectionStateViewModel.desiredBadge(state, desired);
    return badge.isEmpty() ? base : (base + badge);
  }

  private String firstServerIdOrEmpty() {
    return serverNodeResolver.firstServerIdOrEmpty(
        serverNodeResolverContext, startupSelectionRestorer::rememberedSelection);
  }

  private ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForRef(TargetRef ref) {
    return builtInLayoutVisibilityFacade.builtInLayoutNodeKindForRef(ref);
  }

  private ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(DefaultMutableTreeNode node) {
    return builtInLayoutVisibilityFacade.builtInLayoutNodeKindForNode(node);
  }

  private ServerTreeRootSiblingNode rootSiblingNodeKindForNode(DefaultMutableTreeNode node) {
    return builtInLayoutVisibilityFacade.rootSiblingNodeKindForNode(node);
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return "Servers";
  }

  // If the docking framework wraps this Dockable in an outer JScrollPane, keep that wrapper
  // passive and let our inner tree scrollpane own scrolling behavior.
  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 16;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (visibleRect == null) return 64;
    return orientation == SwingConstants.VERTICAL
        ? Math.max(32, visibleRect.height - 24)
        : Math.max(32, visibleRect.width - 24);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return true;
  }

  /**
   * Best-effort current selection suitable for persistence.
   *
   * <p>Includes synthetic group selections (monitor/interceptors) that do not have direct leaf
   * target refs.
   */
  public TargetRef selectedTargetForPersistence() {
    return selectionPersistencePolicy.selectedTargetForPersistence(selectionPersistenceContext);
  }

  public Flowable<TargetRef> selectionStream() {
    return requestApi.selectionStream();
  }

  public Flowable<String> connectServerRequests() {
    return requestApi.connectServerRequests();
  }

  public Flowable<String> disconnectServerRequests() {
    return requestApi.disconnectServerRequests();
  }

  public Flowable<TargetRef> closeTargetRequests() {
    return requestApi.closeTargetRequests();
  }

  public Flowable<TargetRef> joinChannelRequests() {
    return requestApi.joinChannelRequests();
  }

  public Flowable<TargetRef> disconnectChannelRequests() {
    return requestApi.disconnectChannelRequests();
  }

  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return requestApi.bouncerDetachChannelRequests();
  }

  public Flowable<TargetRef> closeChannelRequests() {
    return requestApi.closeChannelRequests();
  }

  public Flowable<String> managedChannelsChangedByServer() {
    return requestApi.managedChannelsChangedByServer();
  }

  public Flowable<TargetRef> clearLogRequests() {
    return requestApi.clearLogRequests();
  }

  public Flowable<TargetRef> openPinnedChatRequests() {
    return requestApi.openPinnedChatRequests();
  }

  public Flowable<String> quasselSetupRequests() {
    return requestApi.quasselSetupRequests();
  }

  public Flowable<String> quasselNetworkManagerRequests() {
    return requestApi.quasselNetworkManagerRequests();
  }

  public void setQuasselNetworkTooltipProvider(
      BiFunction<String, String, String> quasselNetworkTooltipProvider) {
    this.quasselNetworkTooltipProvider =
        quasselNetworkTooltipProvider == null
            ? (serverId, networkToken) -> ""
            : quasselNetworkTooltipProvider;
  }

  public void syncQuasselNetworks(
      String serverId, List<ServerTreeQuasselNetworkParentResolver.NetworkPresentation> networks) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    List<ServerTreeQuasselNetworkParentResolver.NetworkPresentation> safeNetworks =
        networks == null ? List.of() : List.copyOf(networks);

    Runnable sync =
        () -> {
          ServerNodes serverNodes =
              serverNodeResolver.serverNodesForServer(serverNodeResolverContext, sid);
          if (serverNodes == null) return;
          boolean connected =
              runtimeState.connectionStateForServer(sid) == ConnectionState.CONNECTED;
          quasselNetworkParentResolver.syncServerNetworks(
              sid, serverNodes, safeNetworks, connected);
          tree.repaint();
        };
    if (SwingUtilities.isEventDispatchThread()) {
      sync.run();
    } else {
      SwingUtilities.invokeLater(sync);
    }
  }

  public void setPinnedDockableProvider(Function<TargetRef, Dockable> provider) {
    requestApi.setPinnedDockableProvider(provider);
  }

  public Flowable<TargetRef> channelModeDetailsRequests() {
    return requestApi.channelModeDetailsRequests();
  }

  public Flowable<TargetRef> channelModeRefreshRequests() {
    return requestApi.channelModeRefreshRequests();
  }

  public Flowable<ChannelModeSetRequest> channelModeSetRequests() {
    return requestApi.channelModeSetRequests();
  }

  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return requestApi.ircv3CapabilityToggleRequests();
  }

  /**
   * Returns currently open channel targets for a server.
   *
   * <p>Safe to call from any thread.
   */
  public List<String> openChannelsForServer(String serverId) {
    return channelInteractionApi.openChannelsForServer(serverId);
  }

  public List<ManagedChannelEntry> managedChannelsForServer(String serverId) {
    return channelInteractionApi.managedChannelsForServer(serverId);
  }

  public ChannelSortMode channelSortModeForServer(String serverId) {
    return channelInteractionApi.channelSortModeForServer(serverId);
  }

  public void setChannelSortModeForServer(String serverId, ChannelSortMode mode) {
    channelInteractionApi.setChannelSortModeForServer(serverId, mode);
  }

  public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
    channelInteractionApi.setChannelCustomOrderForServer(serverId, channels);
  }

  public void setCanEditChannelModes(BiPredicate<String, String> canEditChannelModes) {
    channelInteractionApi.setCanEditChannelModes(canEditChannelModes);
  }

  public void requestJoinChannel(TargetRef target) {
    channelInteractionApi.requestJoinChannel(target);
  }

  public void requestDisconnectChannel(TargetRef target) {
    channelInteractionApi.requestDisconnectChannel(target);
  }

  public void requestCloseChannel(TargetRef target) {
    channelInteractionApi.requestCloseChannel(target);
  }

  public Action moveNodeUpAction() {
    return nodeActions.moveUpAction();
  }

  public Action moveNodeDownAction() {
    return nodeActions.moveDownAction();
  }

  public Action closeNodeAction() {
    return nodeActions.closeAction();
  }

  public void setServerConnectionState(String serverId, ConnectionState state) {
    runtimeHeaderApi.setServerConnectionState(serverId, state);
  }

  public boolean isServerConnected(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return runtimeState.connectionStateForServer(sid) == ConnectionState.CONNECTED;
  }

  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    runtimeHeaderApi.setServerDesiredOnline(serverId, desiredOnline);
  }

  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    runtimeHeaderApi.setServerConnectionDiagnostics(serverId, lastError, nextRetryEpochMs);
  }

  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    runtimeHeaderApi.setServerConnectedIdentity(serverId, connectedHost, connectedPort, nick, at);
  }

  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    runtimeHeaderApi.setServerIrcv3Capability(serverId, capability, subcommand, enabled);
  }

  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    runtimeHeaderApi.setServerIsupportToken(serverId, tokenName, tokenValue);
  }

  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    runtimeHeaderApi.setServerVersionDetails(
        serverId, serverName, serverVersion, userModes, channelModes);
  }

  private void openServerInfoDialog(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    networkInfoDialogBuilder.open(
        this,
        ServerTreeNetworkInfoDialogBuilder.context(
            uiHooks::connectionStateForServer,
            runtimeState::desiredOnlineForServer,
            server ->
                backendUiProfileProvider == null
                    ? ""
                    : backendUiProfileProvider.backendIdForServer(server),
            server ->
                backendUiProfileProvider == null
                    ? ""
                    : backendUiProfileProvider.backendDisplayNameForServer(server),
            server -> serverLabelPolicy.prettyServerLabel(serverLabelPolicyContext, server),
            runtimeState::connectionDiagnosticsTipForServer,
            (server, capability, enable) ->
                requestEmitter.emitIrcv3CapabilityToggle(
                    new Ircv3CapabilityToggleRequest(server, capability, enable))),
        sid,
        runtimeState.metadataForServer(sid));
  }

  public void setStatusText(String text) {
    runtimeHeaderApi.setStatusText(text);
  }

  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    runtimeHeaderApi.setConnectionControlsEnabled(connectEnabled, disconnectEnabled);
  }

  /**
   * Back-compat convenience: historically we used a single boolean to toggle the buttons.
   *
   * @deprecated Prefer {@link #setConnectionControlsEnabled(boolean, boolean)}.
   */
  @Deprecated
  public void setConnectedUi(boolean connected) {
    runtimeHeaderApi.setConnectedUi(connected);
  }

  public boolean isChannelListNodesVisible() {
    return nodeVisibilityApi.isChannelListNodesVisible();
  }

  public boolean isDccTransfersNodesVisible() {
    return nodeVisibilityApi.isDccTransfersNodesVisible();
  }

  public boolean isServerNodesVisible() {
    return nodeVisibilityApi.isServerNodesVisible();
  }

  public boolean isLogViewerNodesVisible() {
    return nodeVisibilityApi.isLogViewerNodesVisible();
  }

  public boolean isNotificationsNodesVisible() {
    return nodeVisibilityApi.isNotificationsNodesVisible();
  }

  public boolean isMonitorNodesVisible() {
    return nodeVisibilityApi.isMonitorNodesVisible();
  }

  public boolean isInterceptorsNodesVisible() {
    return nodeVisibilityApi.isInterceptorsNodesVisible();
  }

  public boolean isServerNodeVisibleForServer(String serverId) {
    return nodeVisibilityApi.isServerNodeVisibleForServer(serverId);
  }

  public boolean isLogViewerNodeVisibleForServer(String serverId) {
    return nodeVisibilityApi.isLogViewerNodeVisibleForServer(serverId);
  }

  public boolean isNotificationsNodeVisibleForServer(String serverId) {
    return nodeVisibilityApi.isNotificationsNodeVisibleForServer(serverId);
  }

  public boolean isMonitorNodeVisibleForServer(String serverId) {
    return nodeVisibilityApi.isMonitorNodeVisibleForServer(serverId);
  }

  public boolean isInterceptorsNodeVisibleForServer(String serverId) {
    return nodeVisibilityApi.isInterceptorsNodeVisibleForServer(serverId);
  }

  public void setServerNodeVisibleForServer(String serverId, boolean visible) {
    nodeVisibilityApi.setServerNodeVisibleForServer(serverId, visible);
  }

  public void setLogViewerNodeVisibleForServer(String serverId, boolean visible) {
    nodeVisibilityApi.setLogViewerNodeVisibleForServer(serverId, visible);
  }

  public void setNotificationsNodeVisibleForServer(String serverId, boolean visible) {
    nodeVisibilityApi.setNotificationsNodeVisibleForServer(serverId, visible);
  }

  public void setMonitorNodeVisibleForServer(String serverId, boolean visible) {
    nodeVisibilityApi.setMonitorNodeVisibleForServer(serverId, visible);
  }

  public void setInterceptorsNodeVisibleForServer(String serverId, boolean visible) {
    nodeVisibilityApi.setInterceptorsNodeVisibleForServer(serverId, visible);
  }

  public boolean isApplicationRootVisible() {
    return nodeVisibilityApi.isApplicationRootVisible();
  }

  public void setChannelListNodesVisible(boolean visible) {
    nodeVisibilityApi.setChannelListNodesVisible(visible);
  }

  public void setDccTransfersNodesVisible(boolean visible) {
    nodeVisibilityApi.setDccTransfersNodesVisible(visible);
  }

  /**
   * Back-compat/global toggle for all current and future servers.
   *
   * <p>Per-server callers should use {@link #setServerNodeVisibleForServer(String, boolean)}.
   */
  public void setServerNodesVisible(boolean visible) {
    nodeVisibilityApi.setServerNodesVisible(visible);
  }

  public void setLogViewerNodesVisible(boolean visible) {
    nodeVisibilityApi.setLogViewerNodesVisible(visible);
  }

  public void setNotificationsNodesVisible(boolean visible) {
    nodeVisibilityApi.setNotificationsNodesVisible(visible);
  }

  public void setMonitorNodesVisible(boolean visible) {
    nodeVisibilityApi.setMonitorNodesVisible(visible);
  }

  public void setInterceptorsNodesVisible(boolean visible) {
    nodeVisibilityApi.setInterceptorsNodesVisible(visible);
  }

  public void setApplicationRootVisible(boolean visible) {
    nodeVisibilityApi.setApplicationRootVisible(visible);
  }

  public boolean canOpenSelectedNodeInChatDock() {
    return selectedTargetRef() != null;
  }

  public void openSelectedNodeInChatDock() {
    TargetRef ref = selectedTargetRef();
    if (ref == null) return;
    requestEmitter.emitOpenPinnedChat(ref);
  }

  private boolean isPrivateMessageTarget(TargetRef ref) {
    return targetNodePolicy.isPrivateMessageTarget(ref);
  }

  private boolean isInterceptorEnabled(TargetRef ref) {
    if (ref == null || !ref.isInterceptor()) return true;
    if (interceptorStore == null) return true;
    String sid = InterceptorScope.scopedServerIdForTarget(ref);
    String iid = Objects.toString(ref.interceptorId(), "").trim();
    if (sid.isEmpty() || iid.isEmpty()) return true;
    InterceptorDefinition def = interceptorStore.interceptor(sid, iid);
    return def == null || def.enabled();
  }

  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    privateMessageOnlineStateCoordinator.setPrivateMessageOnlineState(serverId, nick, online);
  }

  public void clearPrivateMessageOnlineStates(String serverId) {
    privateMessageOnlineStateCoordinator.clearPrivateMessageOnlineStates(serverId);
  }

  private void syncApplicationRootVisibility() {
    applicationRootVisibilityCoordinator.syncApplicationRootVisibility();
  }

  private Set<TreePath> snapshotExpandedTreePaths() {
    return expansionStateManager.snapshotExpandedTreePaths();
  }

  private void restoreExpandedTreePaths(Set<TreePath> expanded) {
    expansionStateManager.restoreExpandedTreePaths(expanded);
  }

  private boolean isPathInCurrentTreeModel(TreePath path) {
    return expansionStateManager.isPathInCurrentTreeModel(path);
  }

  private TreePath defaultSelectionPath() {
    return expansionStateManager.defaultSelectionPath();
  }

  private String quasselNetworkTooltip(String serverId, String networkToken) {
    BiFunction<String, String, String> provider = quasselNetworkTooltipProvider;
    if (provider == null) return "";
    String sid = Objects.toString(serverId, "").trim();
    String token = Objects.toString(networkToken, "").trim();
    if (sid.isEmpty() || token.isEmpty()) return "";
    String tip = provider.apply(sid, token);
    return Objects.toString(tip, "").trim();
  }

  private boolean isApplicationJfrActive() {
    if (jfrRuntimeEventsService == null) return true;
    return jfrRuntimeEventsService.isEnabled();
  }

  private void refreshApplicationJfrNode() {
    DefaultMutableTreeNode node = leaves.get(applicationNodes.jfrRef());
    if (node != null) {
      model.nodeChanged(node);
      return;
    }
    tree.repaint();
  }

  private void syncUiLeafVisibility() {
    uiLeafVisibilitySynchronizer.syncUiLeafVisibility();
  }

  private void selectBestFallbackForServer(String serverId) {
    selectionFallbackPolicy.selectBestFallbackForServer(selectionFallbackContext, serverId);
  }

  private void selectStartupDefaultForServer(String serverId) {
    if (startupSelectionRestorer.tryRestoreForServer(serverId)) {
      return;
    }
    selectionFallbackPolicy.selectStartupDefaultForServer(selectionFallbackContext, serverId);
  }

  private void applyBuiltInLayoutToTree(ServerNodes sn, ServerTreeBuiltInLayout requestedLayout) {
    builtInLayoutVisibilityFacade.applyBuiltInLayoutToTree(sn, requestedLayout);
  }

  private void applyRootSiblingOrderToTree(
      ServerNodes sn, ServerTreeRootSiblingOrder requestedOrder) {
    builtInLayoutVisibilityFacade.applyRootSiblingOrderToTree(sn, requestedOrder);
  }

  private void persistRootSiblingOrderFromTree(String serverId) {
    builtInLayoutVisibilityFacade.persistRootSiblingOrderFromTree(serverId);
  }

  private void persistBuiltInLayoutFromTree(String serverId) {
    builtInLayoutVisibilityFacade.persistBuiltInLayoutFromTree(serverId);
  }

  private TargetRef selectedTargetRef() {
    return nodeAccess.selectedTargetRef();
  }

  public void ensureNode(TargetRef ref) {
    targetLifecycleCoordinator.ensureNode(ref);
    startupSelectionRestorer.tryRestoreAfterEnsure(ref);
  }

  public boolean hasTarget(TargetRef ref) {
    return ref != null && leaves.containsKey(ref);
  }

  private DefaultMutableTreeNode ensureChannelListNodeForEnsureNode(ServerNodes sn) {
    return channelListNodeEnsurer.ensureChannelListNode(sn);
  }

  private DefaultMutableTreeNode backendSpecificParent(TargetRef ref, ServerNodes serverNodes) {
    if (quasselNetworkParentResolver == null) return null;
    return quasselNetworkParentResolver.resolveParent(ref, serverNodes);
  }

  public void selectTarget(TargetRef ref) {
    targetSelectionCoordinator.selectTarget(ref);
  }

  public void removeTarget(TargetRef ref) {
    targetLifecycleCoordinator.removeTarget(ref);
  }

  public void setChannelDisconnected(TargetRef ref, boolean detached) {
    setChannelDisconnected(ref, detached, null);
  }

  public void setChannelDisconnected(TargetRef ref, boolean detached, String warningReason) {
    channelInteractionApi.setChannelDisconnected(ref, detached, warningReason);
  }

  public void clearChannelDisconnectedWarning(TargetRef ref) {
    channelInteractionApi.clearChannelDisconnectedWarning(ref);
  }

  public boolean isChannelDisconnected(TargetRef ref) {
    return channelInteractionApi.isChannelDisconnected(ref);
  }

  public boolean isChannelAutoReattach(TargetRef ref) {
    return channelInteractionApi.isChannelAutoReattach(ref);
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    channelInteractionApi.setChannelAutoReattach(ref, autoReattach);
  }

  public boolean isChannelPinned(TargetRef ref) {
    return channelInteractionApi.isChannelPinned(ref);
  }

  public void setChannelPinned(TargetRef ref, boolean pinned) {
    channelInteractionApi.setChannelPinned(ref, pinned);
  }

  public boolean isChannelMuted(TargetRef ref) {
    return channelInteractionApi.isChannelMuted(ref);
  }

  public void setChannelMuted(TargetRef ref, boolean muted) {
    channelInteractionApi.setChannelMuted(ref, muted);
  }

  private void emitManagedChannelsChanged(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    requestEmitter.emitManagedChannelsChanged(sid);
  }

  private static String foldChannelKey(String channel) {
    return ServerTreeConventions.foldChannelKey(channel);
  }

  public void markUnread(TargetRef ref) {
    channelInteractionApi.markUnread(ref);
  }

  public void markHighlight(TargetRef ref) {
    channelInteractionApi.markHighlight(ref);
  }

  public void clearUnread(TargetRef ref) {
    channelInteractionApi.clearUnread(ref);
  }

  public void markTypingActivity(TargetRef ref, String state) {
    channelInteractionApi.markTypingActivity(ref, state);
  }

  private void onTypingActivityAnimationTick() {
    typingActivityManager.onTypingActivityAnimationTick();
  }

  private void clearTypingIndicatorsIfReady() {
    if (typingActivityManager == null) return;
    typingActivityManager.clearTypingIndicatorsFromTree();
  }

  private void repaintTreeNode(DefaultMutableTreeNode node) {
    if (node == null) return;
    TreePath path = new TreePath(node.getPath());
    Rectangle r = tree.getPathBounds(path);
    if (r == null) return;
    Rectangle visible = tree.getVisibleRect();
    if (visible == null || visible.isEmpty()) return;
    Rectangle dirty = r.intersection(visible);
    if (dirty.isEmpty()) return;
    tree.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
  }

  private Set<String> bouncerControlServerIdsForBackend(String backendId) {
    String normalized = normalizeBackendId(backendId);
    return bouncerControlServerIdsByBackendId.computeIfAbsent(
        normalized, ignored -> new HashSet<>());
  }

  private Map<String, String> originByServerIdForBackend(String backendId) {
    String normalized = normalizeBackendId(backendId);
    return originByServerIdByBackendId.computeIfAbsent(normalized, ignored -> new HashMap<>());
  }

  private Map<String, DefaultMutableTreeNode> networksGroupByOriginForBackend(String backendId) {
    String normalized = normalizeBackendId(backendId);
    return networksGroupByOriginByBackendId.computeIfAbsent(normalized, ignored -> new HashMap<>());
  }

  private static Map<String, Set<String>> createBouncerControlServerIdsByBackendId() {
    Map<String, Set<String>> state = new LinkedHashMap<>();
    for (String backendId : ServerTreeBouncerBackends.orderedIds()) {
      state.put(backendId, new HashSet<>());
    }
    return state;
  }

  private static Map<String, Map<String, String>> createOriginByServerIdByBackendId() {
    Map<String, Map<String, String>> state = new LinkedHashMap<>();
    for (String backendId : ServerTreeBouncerBackends.orderedIds()) {
      state.put(backendId, new HashMap<>());
    }
    return state;
  }

  private static Map<String, Map<String, DefaultMutableTreeNode>>
      createNetworksGroupByOriginByBackendId() {
    Map<String, Map<String, DefaultMutableTreeNode>> state = new LinkedHashMap<>();
    for (String backendId : ServerTreeBouncerBackends.orderedIds()) {
      state.put(backendId, new HashMap<>());
    }
    return state;
  }

  private static Map<String, String> createNetworksGroupLabelByBackendId() {
    Map<String, String> labels = new LinkedHashMap<>();
    for (String backendId : ServerTreeBouncerBackends.orderedIds()) {
      labels.put(backendId, ServerTreeBouncerBackends.defaultNetworksGroupLabel(backendId));
    }
    return labels;
  }

  private static Map<String, BouncerAutoConnectStore> createAutoConnectStoreByBackendId(
      GenericBouncerAutoConnectStore genericAutoConnect,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect) {
    Map<String, BouncerAutoConnectStore> stores = new LinkedHashMap<>();
    if (sojuAutoConnect != null) {
      stores.put(ServerTreeBouncerBackends.SOJU, sojuAutoConnect);
    }
    if (zncAutoConnect != null) {
      stores.put(ServerTreeBouncerBackends.ZNC, zncAutoConnect);
    }
    if (genericAutoConnect != null) {
      stores.put(ServerTreeBouncerBackends.GENERIC, genericAutoConnect);
    }
    return stores;
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(java.util.Locale.ROOT);
  }

  private boolean shouldPersistPrivateMessageList() {
    return runtimeConfig != null
        && logProps != null
        && Boolean.TRUE.equals(logProps.savePrivateMessageList());
  }

  private void removeServerRoot(String serverId) {
    quasselNetworkParentResolver.forgetServer(serverId);
    serverLifecycleFacade.removeServerRoot(serverId);
  }

  private ServerNodes addServerRoot(String serverId) {
    ServerNodes serverNodes = serverLifecycleFacade.addServerRoot(serverId);
    String sid = normalizeServerId(serverId);
    boolean connected = runtimeState.connectionStateForServer(sid) == ConnectionState.CONNECTED;
    quasselNetworkParentResolver.initializeServer(serverId, serverNodes, connected);
    serverLeafVisibilityCoordinator.syncUiLeafVisibilityForServer(sid);
    ensureInterceptorNodesForServer(serverId);
    return serverNodes;
  }

  private void ensureInterceptorNodesForServer(String serverId) {
    if (interceptorStore == null || targetLifecycleCoordinator == null) return;
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    Set<String> scopeServerIds = new HashSet<>();
    for (InterceptorStore.ScopedInterceptorRef scoped :
        interceptorStore.listInterceptorRefsForBaseServer(sid)) {
      if (scoped == null) continue;
      String scopeServerId = InterceptorScope.normalizeScopeServerId(scoped.serverId());
      String interceptorId = Objects.toString(scoped.interceptorId(), "").trim();
      if (scopeServerId.isEmpty() || interceptorId.isEmpty()) continue;

      TargetRef ref = InterceptorScope.interceptorRef(scopeServerId, interceptorId);
      if (ref == null) continue;
      targetLifecycleCoordinator.ensureNode(ref);
      interceptorActions.refreshInterceptorNodeLabel(
          interceptorActionsContext, scopeServerId, interceptorId);
      scopeServerIds.add(scopeServerId);
    }

    if (scopeServerIds.isEmpty()) {
      interceptorActions.refreshInterceptorGroupCount(interceptorActionsContext, sid);
      return;
    }
    for (String scopeServerId : scopeServerIds) {
      interceptorActions.refreshInterceptorGroupCount(interceptorActionsContext, scopeServerId);
    }
  }

  private void updateBouncerControlLabels(Map<String, Set<String>> nextBouncerControlByBackendId) {
    serverLifecycleFacade.updateBouncerControlLabels(nextBouncerControlByBackendId);
  }

  private void refreshTreeLayoutAfterUiChange() {
    uiRefreshCoordinator.refreshTreeLayoutAfterUiChange();
  }

  private boolean isQuasselServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    if (backendUiProfileProvider != null) {
      return backendUiProfileProvider.supportsQuasselCoreCommands(sid);
    }
    if (serverCatalog == null) return false;
    return serverCatalog
        .findEntry(sid)
        .map(ServerEntry::server)
        .map(server -> IrcProperties.Server.Backend.QUASSEL_CORE.token().equals(server.backendId()))
        .orElse(false);
  }

  @PreDestroy
  public void shutdown() {
    try {
      interactionSetupCoordinator.clearPreparedChannelDockDrag();
      settingsSynchronizer.shutdown();
      if (typingActivityTimer != null) typingActivityTimer.stop();
      if (treeWheelSelectionDecorator != null) treeWheelSelectionDecorator.close();
      nodeActions.close();
    } catch (Exception ignored) {
    }
    disposables.dispose();
  }
}
