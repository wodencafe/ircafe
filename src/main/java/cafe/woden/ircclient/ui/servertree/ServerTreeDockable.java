package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeApplicationRootVisibilityContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeBuiltInLayoutOrchestratorContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeCellRendererContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeContextMenuContextFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeLayoutPersistenceContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeSelectionPersistenceContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerCatalogSynchronizerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerRootLifecycleContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeStartupSelectionRestorerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTargetLifecycleContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTargetSelectionContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTooltipContextFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeUiLeafVisibilitySynchronizerContextAdapter;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeApplicationRootVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelDisconnectStateManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelTargetOperations;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreePrivateMessageOnlineStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeSelectionBroadcastCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLeafVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLifecycleFacade;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerRootLifecycleManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetLifecycleCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetSelectionCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTypingActivityManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiRefreshCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUnreadStateCoordinator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeDragReorderSupport;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionMediator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionWiringFactory;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeKeyBindingsInstaller;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeMiddleDragReorderHandler;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeNodeActionsFactory;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreePinnedDockDragController;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutVisibilityFacade;
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
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTypingTargetPolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeNodeAccess;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeServerNodeResolver;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeChannelModeRequestBus;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestStreams;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerRuntimeMetadata;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeApplicationNodes;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilitySettings;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeExpansionStateManager;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeSettingsSynchronizer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeCellRenderer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeHeaderControls;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.springframework.context.annotation.Lazy;

@org.springframework.stereotype.Component
@Lazy
public class ServerTreeDockable extends JPanel implements Dockable, Scrollable {
  // UI label for the per-server "status" transcript target.
  // The target id remains "status" internally; this is just what the user sees in the tree.
  private static final String STATUS_LABEL = "Server";
  private static final String CHANNEL_LIST_LABEL = "Channel List";
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
  private static final String SOJU_NETWORKS_GROUP_LABEL = "Soju Networks";
  private static final String ZNC_NETWORKS_GROUP_LABEL = "ZNC Networks";
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

  private final ServerTreeNodeActionsFactory nodeActionsFactory =
      new ServerTreeNodeActionsFactory();
  private final ServerTreeInteractionWiringFactory interactionWiringFactory =
      new ServerTreeInteractionWiringFactory();
  private final TreeNodeActions<TargetRef> nodeActions;
  private final ServerTreeSelectionBroadcastCoordinator selectionBroadcastCoordinator =
      new ServerTreeSelectionBroadcastCoordinator();
  private final ServerTreeRequestStreams requestStreams = new ServerTreeRequestStreams();
  private final ServerTreeRequestEmitter requestEmitter = requestStreams.requestEmitter();

  private final ServerTreeChannelModeRequestBus channelModeRequestBus =
      new ServerTreeChannelModeRequestBus();

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
              : ServerTreeDockable.this.tooltipResolver.toolTipForEvent(event);
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
  private final ServerTreeEdtExecutor edtExecutor = new ServerTreeEdtExecutor();
  private final Timer typingActivityTimer;
  private final Set<DefaultMutableTreeNode> typingActivityNodes = new HashSet<>();
  private final ServerTreeTypingActivityManager typingActivityManager;

  private static final int CAPABILITY_TRANSITION_LOG_LIMIT = 200;

  private final RuntimeConfigStore runtimeConfig;
  private final LogProperties logProps;

  private final Map<String, String> serverDisplayNames = new HashMap<>();
  private final Set<String> ephemeralServerIds = new HashSet<>();
  private final Set<String> sojuBouncerControlServerIds = new HashSet<>();
  private final Set<String> zncBouncerControlServerIds = new HashSet<>();
  private final Map<String, String> sojuOriginByServerId = new HashMap<>();
  private final Map<String, String> zncOriginByServerId = new HashMap<>();
  private final Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin = new HashMap<>();
  private final Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin = new HashMap<>();
  private final ServerTreeNodeClassifier nodeClassifier;

  private final InterceptorStore interceptorStore;
  private final JfrRuntimeEventsService jfrRuntimeEventsService;

  private final ServerTreeNetworkInfoDialogBuilder networkInfoDialogBuilder;
  private final ServerTreeInterceptorActions interceptorActions;
  private final ServerTreeServerCatalogSynchronizer serverCatalogSynchronizer;
  private final ServerTreeStatusLabelManager statusLabelManager;
  private final ServerTreeNetworkGroupManager networkGroupManager;
  private final ServerTreeServerStateCleaner serverStateCleaner;
  private final ServerTreeServerNodeBuilder serverNodeBuilder;
  private final ServerTreeServerRootLifecycleManager serverRootLifecycleManager;
  private final ServerTreeServerLifecycleFacade serverLifecycleFacade;
  private final ServerTreeServerParentResolver serverParentResolver;
  private final ServerTreeServerLabelPolicy serverLabelPolicy;
  private final ServerTreeBouncerDetachPolicy bouncerDetachPolicy;
  private final ServerTreeNodeBadgeUpdater nodeBadgeUpdater;
  private final ServerTreeSelectionFallbackPolicy selectionFallbackPolicy;
  private final ServerTreeSelectionPersistencePolicy selectionPersistencePolicy;
  private final ServerTreeStartupSelectionRestorer startupSelectionRestorer;
  private final ServerTreeServerLeafVisibilityCoordinator serverLeafVisibilityCoordinator;
  private final ServerTreeUiLeafVisibilitySynchronizer uiLeafVisibilitySynchronizer;
  private final ServerTreeExpansionStateManager expansionStateManager;
  private final ServerTreeApplicationRootVisibilityCoordinator applicationRootVisibilityCoordinator;

  private final ServerTreeBuiltInLayoutVisibilityFacade builtInLayoutVisibilityFacade;
  private final ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings;
  private final ServerTreeTargetNodePolicy targetNodePolicy;
  private final ServerTreeChannelStateCoordinator channelStateCoordinator;
  private final ServerTreeChannelQueryService channelQueryService;
  private final ServerTreeChannelTargetOperations channelTargetOperations;
  private final ServerTreeEnsureNodeParentResolver ensureNodeParentResolver;
  private final ServerTreeChannelListNodeEnsurer channelListNodeEnsurer;
  private final ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter;
  private final ServerTreeChannelDisconnectStateManager channelDisconnectStateManager;
  private final ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator;
  private final ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator;
  private final ServerTreeTargetLifecycleCoordinator targetLifecycleCoordinator;
  private final ServerTreeTargetSelectionCoordinator targetSelectionCoordinator;
  private final ServerTreeUnreadStateCoordinator unreadStateCoordinator;
  private final ServerTreeInteractionMediator interactionMediator;
  private final ServerTreePinnedDockDragController pinnedDockDragController;
  private final ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater;
  private final ServerTreeUiRefreshCoordinator uiRefreshCoordinator;
  private final ServerTreeTooltipProvider tooltipProvider;
  private final ServerTreeTooltipResolver tooltipResolver;
  private final ServerTreeContextMenuBuilder contextMenuBuilder;
  private final ServerTreeExternalStreamBinder externalStreamBinder;

  private final ServerTreeSettingsSynchronizer settingsSynchronizer;
  private volatile ServerTreeTypingIndicatorStyle typingIndicatorStyle =
      ServerTreeTypingIndicatorStyle.DOTS;
  private volatile boolean typingIndicatorsTreeEnabled = true;
  private volatile int unreadBadgeScalePercent = TREE_BADGE_SCALE_PERCENT_DEFAULT;
  private volatile boolean serverTreeNotificationBadgesEnabled = true;
  private volatile Color unreadChannelTextColor = null;
  private volatile Color highlightChannelTextColor = null;
  private volatile boolean showChannelListNodes = true;
  private volatile boolean showDccTransfersNodes = false;
  private boolean startupSelectionCompleted = false;
  private volatile boolean showApplicationRoot = true;

  public ServerTreeDockable(
      ServerCatalog serverCatalog,
      RuntimeConfigStore runtimeConfig,
      LogProperties logProps,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      UiSettingsBus settingsBus,
      ServerDialogs serverDialogs) {
    this(
        serverCatalog,
        runtimeConfig,
        logProps,
        sojuAutoConnect,
        zncAutoConnect,
        connectBtn,
        disconnectBtn,
        notificationStore,
        interceptorStore,
        settingsBus,
        serverDialogs,
        null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ServerTreeDockable(
      ServerCatalog serverCatalog,
      RuntimeConfigStore runtimeConfig,
      LogProperties logProps,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      UiSettingsBus settingsBus,
      ServerDialogs serverDialogs,
      JfrRuntimeEventsService jfrRuntimeEventsService) {
    super(new BorderLayout());

    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;

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
    this.serverNodeResolver =
        new ServerTreeServerNodeResolver(servers, leaves, ServerTreeDockable::normalizeServerId);
    this.nodeClassifier =
        new ServerTreeNodeClassifier(
            "Private Messages",
            INTERCEPTORS_GROUP_LABEL,
            MONITOR_GROUP_LABEL,
            OTHER_GROUP_LABEL,
            uiHooks::isServerNode);
    ServerTreeLayoutCollaborators layoutCollaborators =
        ServerTreeLayoutCollaboratorsFactory.create(
            runtimeConfig,
            ServerTreeBuiltInVisibilityCoordinator.context(
                ServerTreeDockable::normalizeServerId, servers::keySet, this::syncUiLeafVisibility),
            new ServerTreeLayoutPersistenceContextAdapter(
                this::rootSiblingNodeKindForNode,
                this::builtInLayoutNodeKindForNode,
                this::rootSiblingOrder,
                this::builtInLayout,
                this::persistRootSiblingOrderForServer,
                this::persistBuiltInLayoutForServer),
            new ServerTreeBuiltInLayoutOrchestratorContextAdapter(
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
            nodeClassifier::isMonitorGroupNode,
            nodeClassifier::isInterceptorsGroupNode,
            nodeClassifier::isOtherGroupNode,
            nodeClassifier::isPrivateMessagesGroupNode,
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
    this.serverLabelPolicy =
        new ServerTreeServerLabelPolicy(
            serverDisplayNames,
            ephemeralServerIds,
            sojuOriginByServerId,
            zncOriginByServerId,
            sojuAutoConnect,
            zncAutoConnect);

    this.networkInfoDialogBuilder =
        new ServerTreeNetworkInfoDialogBuilder(
            runtimeConfig,
            ServerTreeNetworkInfoDialogBuilder.context(
                uiHooks::connectionStateForServer,
                runtimeState::desiredOnlineForServer,
                serverLabelPolicy::prettyServerLabel,
                runtimeState::connectionDiagnosticsTipForServer,
                (serverId, capability, enable) ->
                    requestEmitter.emitIrcv3CapabilityToggle(
                        new Ircv3CapabilityToggleRequest(serverId, capability, enable))));
    this.interceptorActions =
        new ServerTreeInterceptorActions(
            this,
            interceptorStore,
            INTERCEPTORS_GROUP_LABEL,
            ServerTreeInterceptorActions.context(
                this::ensureNode,
                this::selectTarget,
                this::removeTarget,
                leaves::get,
                serverNodeResolver::interceptorsNodeForServer,
                model::nodeChanged));
    this.serverCatalogSynchronizer =
        new ServerTreeServerCatalogSynchronizer(
            serverDisplayNames,
            ephemeralServerIds,
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            sojuOriginByServerId,
            zncOriginByServerId,
            new ServerTreeServerCatalogSynchronizerContextAdapter(
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
    this.statusLabelManager =
        new ServerTreeStatusLabelManager(
            STATUS_LABEL,
            BOUNCER_CONTROL_LABEL,
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            leaves,
            model::nodeChanged);
    this.networkGroupManager =
        new ServerTreeNetworkGroupManager(
            SOJU_NETWORKS_GROUP_LABEL,
            ZNC_NETWORKS_GROUP_LABEL,
            sojuNetworksGroupByOrigin,
            zncNetworksGroupByOrigin,
            ServerTreeNetworkGroupManager.context(
                serverNodeResolver::serverNodeForServer,
                serverNodeResolver::privateMessagesNodeForServer));
    this.dragReorderSupport =
        new ServerTreeDragReorderSupport(
            tree,
            servers,
            nodeClassifier,
            uiHooks::isServerNode,
            nodeAccess::isChannelListLeafNode,
            this::builtInLayoutNodeKindForNode,
            this::rootSiblingNodeKindForNode,
            networkGroupManager::isSojuNetworksGroupNode,
            networkGroupManager::isZncNetworksGroupNode);
    ServerTreeStateInteractionCollaborators stateInteractionCollaborators =
        ServerTreeStateInteractionCollaboratorsFactory.create(
            new ServerTreeStateInteractionCollaboratorsFactory.Inputs(
                tree,
                model,
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
                    serverNodeResolver::channelListNodeForServer,
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
    this.serverStateCleaner = stateInteractionCollaborators.serverStateCleaner();
    this.serverNodeBuilder = new ServerTreeServerNodeBuilder();
    this.serverParentResolver =
        new ServerTreeServerParentResolver(
            sojuOriginByServerId,
            zncOriginByServerId,
            ServerTreeServerParentResolver.context(
                serverNodeResolver::hasServer,
                this::addServerRoot,
                () -> ircRoot,
                networkGroupManager::getOrCreateSojuNetworksGroupNode,
                networkGroupManager::getOrCreateZncNetworksGroupNode));
    this.bouncerDetachPolicy =
        new ServerTreeBouncerDetachPolicy(
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            ServerTreeBouncerDetachPolicy.context(
                uiHooks::connectionStateForServer,
                serverLabelPolicy::isSojuEphemeralServer,
                serverLabelPolicy::isZncEphemeralServer,
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
                }));
    this.nodeBadgeUpdater =
        new ServerTreeNodeBadgeUpdater(
            notificationStore,
            ephemeralServerIds,
            leaves,
            ServerTreeNodeBadgeUpdater.context(
                model::nodeChanged, serverNodeResolver::serverNodeForServer));
    this.externalStreamBinder =
        new ServerTreeExternalStreamBinder(
            disposables,
            serverCatalogSynchronizer::syncServers,
            nodeBadgeUpdater::refreshNotificationsCount,
            interceptorActions::refreshInterceptorNodeLabel,
            interceptorActions::refreshInterceptorGroupCount,
            nodeBadgeUpdater::refreshSojuAutoConnectBadges,
            nodeBadgeUpdater::refreshZncAutoConnectBadges);
    this.selectionFallbackPolicy =
        new ServerTreeSelectionFallbackPolicy(
            ServerTreeSelectionFallbackPolicy.context(
                ServerTreeDockable::normalizeServerId,
                servers,
                this::builtInNodesVisibility,
                leaves,
                this::selectTarget,
                tree));
    this.selectionPersistencePolicy =
        new ServerTreeSelectionPersistencePolicy(
            new ServerTreeSelectionPersistenceContextAdapter(
                selectionBroadcastCoordinator::lastBroadcastSelectionRef,
                this::selectedTargetRef,
                () -> (DefaultMutableTreeNode) tree.getLastSelectedPathComponent(),
                nodeClassifier::owningServerIdForNode,
                nodeClassifier::isMonitorGroupNode,
                nodeClassifier::isInterceptorsGroupNode));
    this.startupSelectionRestorer =
        new ServerTreeStartupSelectionRestorer(
            ServerTreeStartupSelectionRestorer.readRememberedSelection(runtimeConfig),
            new ServerTreeStartupSelectionRestorerContextAdapter(
                ServerTreeDockable::normalizeServerId,
                ref -> ref != null && leaves.containsKey(ref),
                serverNodeResolver::monitorNodeForServer,
                serverNodeResolver::interceptorsNodeForServer,
                serverNodeResolver::serverNodesForServer,
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
            serverNodeResolver::serverNodesForServer,
            this::builtInNodesVisibility,
            this::builtInLayout,
            this::rootSiblingOrder,
            this::applyBuiltInLayoutToTree,
            this::applyRootSiblingOrderToTree,
            statusLabelManager::statusLeafLabelForServer,
            () -> showDccTransfersNodes,
            leaves::get);
    this.uiLeafVisibilitySynchronizer =
        new ServerTreeUiLeafVisibilitySynchronizer(
            new ServerTreeUiLeafVisibilitySynchronizerContextAdapter(
                this::selectedTargetRef,
                () -> (DefaultMutableTreeNode) tree.getLastSelectedPathComponent(),
                nodeClassifier::isMonitorGroupNode,
                nodeClassifier::isInterceptorsGroupNode,
                nodeClassifier::owningServerIdForNode,
                () -> List.copyOf(servers.keySet()),
                serverLeafVisibilityCoordinator::syncUiLeafVisibilityForServer,
                serverId -> builtInNodesVisibility(serverId).server(),
                serverId -> builtInNodesVisibility(serverId).notifications(),
                serverId -> builtInNodesVisibility(serverId).logViewer(),
                serverId -> builtInNodesVisibility(serverId).monitor(),
                serverId -> builtInNodesVisibility(serverId).interceptors(),
                () -> showDccTransfersNodes,
                this::selectBestFallbackForServer));
    this.expansionStateManager =
        new ServerTreeExpansionStateManager(tree, root, ircRoot, applicationRoot);
    this.applicationRootVisibilityCoordinator =
        new ServerTreeApplicationRootVisibilityCoordinator(
            new ServerTreeApplicationRootVisibilityContextAdapter(
                this::snapshotExpandedTreePaths,
                this::restoreExpandedTreePaths,
                () -> showApplicationRoot,
                () -> applicationRoot.getParent() == root,
                root::getChildCount,
                index ->
                    root.insert(
                        applicationRoot, Math.max(0, Math.min(index, root.getChildCount()))),
                () -> root.remove(applicationRoot),
                () -> model.nodeStructureChanged(root),
                () -> tree.expandPath(new TreePath(applicationRoot.getPath())),
                this::selectedTargetRef,
                serverNodeResolver::firstServerStatusRefOrNull,
                ref -> {
                  if (ref != null) {
                    selectTarget(ref);
                  }
                },
                () -> tree.setSelectionPath(defaultSelectionPath())));
    this.targetNodePolicy =
        new ServerTreeTargetNodePolicy(
            interceptorStore,
            "Notifications",
            "Interceptor",
            LOG_VIEWER_LABEL,
            CHANNEL_LIST_LABEL,
            WEECHAT_FILTERS_LABEL,
            IGNORES_LABEL,
            DCC_TRANSFERS_LABEL);
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
            edtExecutor,
            channelStateCoordinator,
            requestEmitter,
            this::onChannelMutedStateChangedFromChannelTarget);
    this.ensureNodeParentResolver = stateInteractionCollaborators.ensureNodeParentResolver();
    this.channelListNodeEnsurer =
        new ServerTreeChannelListNodeEnsurer(CHANNEL_LIST_LABEL, leaves, model::nodesWereInserted);
    this.ensureNodeLeafInserter = stateInteractionCollaborators.ensureNodeLeafInserter();
    this.targetNodeRemovalMutator = stateInteractionCollaborators.targetNodeRemovalMutator();
    this.targetRemovalStateCoordinator =
        stateInteractionCollaborators.targetRemovalStateCoordinator();

    this.rowInteractionHandler = stateInteractionCollaborators.rowInteractionHandler();
    this.tooltipProvider =
        new ServerTreeTooltipProvider(
            tree,
            ServerTreeTooltipContextFactory.create(
                new ServerTreeTooltipContextFactory.Inputs(
                    rowInteractionHandler::serverIdAt,
                    uiHooks,
                    nodeAccess::isIrcRootNode,
                    nodeAccess::isApplicationRootNode,
                    networkGroupManager::isSojuNetworksGroupNode,
                    networkGroupManager::isZncNetworksGroupNode,
                    nodeClassifier::isInterceptorsGroupNode,
                    nodeClassifier::isMonitorGroupNode,
                    nodeClassifier::isOtherGroupNode,
                    runtimeState::desiredOnlineForServer,
                    runtimeState::connectionDiagnosticsTipForServer,
                    serverLabelPolicy::isSojuEphemeralServer,
                    serverLabelPolicy::isZncEphemeralServer,
                    sojuOriginByServerId::get,
                    zncOriginByServerId::get,
                    serverId -> serverDisplayNames.getOrDefault(serverId, serverId),
                    (originId, networkKey) ->
                        sojuAutoConnect != null && sojuAutoConnect.isEnabled(originId, networkKey),
                    (originId, networkKey) ->
                        zncAutoConnect != null && zncAutoConnect.isEnabled(originId, networkKey),
                    this::isApplicationJfrActive,
                    nodeData -> {
                      if (nodeData == null || nodeData.ref == null || !nodeData.ref.isStatus()) {
                        return false;
                      }
                      if (!BOUNCER_CONTROL_LABEL.equals(nodeData.label)) {
                        return false;
                      }
                      String serverId = nodeData.ref.serverId();
                      return sojuBouncerControlServerIds.contains(serverId)
                          || zncBouncerControlServerIds.contains(serverId);
                    })));
    this.tooltipResolver = new ServerTreeTooltipResolver(serverActionOverlay, tooltipProvider);
    this.contextMenuBuilder =
        new ServerTreeContextMenuBuilder(
            ServerTreeContextMenuContextFactory.create(
                new ServerTreeContextMenuContextFactory.Inputs(
                    uiHooks::isServerNode,
                    nodeAccess::isRootServerNode,
                    serverLabelPolicy::prettyServerLabel,
                    uiHooks::connectionStateForServer,
                    runtimeState::connectionDiagnosticsTipForServer,
                    serverCatalog,
                    this::moveNodeUpAction,
                    this::moveNodeDownAction,
                    uiHooks::connectServer,
                    uiHooks::disconnectServer,
                    this::openServerInfoDialog,
                    requestEmitter::emitOpenQuasselSetup,
                    requestEmitter::emitOpenQuasselNetworkManager,
                    interceptorStore,
                    interceptorActions::promptAndAddInterceptor,
                    serverDialogs,
                    this,
                    runtimeConfig,
                    serverLabelPolicy::isSojuEphemeralServer,
                    serverLabelPolicy::isZncEphemeralServer,
                    sojuOriginByServerId::get,
                    zncOriginByServerId::get,
                    serverId -> serverDisplayNames.getOrDefault(serverId, serverId),
                    sojuAutoConnect,
                    zncAutoConnect,
                    nodeBadgeUpdater::refreshSojuAutoConnectBadges,
                    nodeBadgeUpdater::refreshZncAutoConnectBadges,
                    nodeClassifier::isInterceptorsGroupNode,
                    nodeClassifier::owningServerIdForNode,
                    uiHooks::openPinnedChat,
                    uiHooks::confirmAndClearLog,
                    this::isChannelDisconnected,
                    uiHooks::joinChannel,
                    uiHooks::disconnectChannel,
                    uiHooks::closeChannel,
                    bouncerDetachPolicy::supportsBouncerDetach,
                    uiHooks::bouncerDetachChannel,
                    this::isChannelAutoReattach,
                    this::setChannelAutoReattach,
                    this::isChannelPinned,
                    this::setChannelPinned,
                    this::isChannelMuted,
                    this::setChannelMuted,
                    channelModeRequestBus::emitDetailsRequest,
                    channelModeRequestBus::emitRefreshRequest,
                    channelTargetOperations::canEditChannelModesForTarget,
                    channelModeRequestBus::emitSetRequest,
                    uiHooks::closeTarget,
                    interceptorActions::setInterceptorEnabled,
                    interceptorActions::promptRenameInterceptor,
                    interceptorActions::confirmDeleteInterceptor)));
    this.serverRootLifecycleManager =
        new ServerTreeServerRootLifecycleManager(
            serverNodeBuilder,
            CHANNEL_LIST_LABEL,
            WEECHAT_FILTERS_LABEL,
            IGNORES_LABEL,
            DCC_TRANSFERS_LABEL,
            LOG_VIEWER_LABEL,
            MONITOR_GROUP_LABEL,
            INTERCEPTORS_GROUP_LABEL,
            new ServerTreeServerRootLifecycleContextAdapter(
                ServerTreeDockable::normalizeServerId,
                servers,
                runtimeState::markServerKnown,
                channelStateCoordinator::loadChannelStateForServer,
                serverParentResolver::resolveParentForServer,
                this::builtInNodesVisibility,
                () -> showDccTransfersNodes,
                statusLabelManager::statusLeafLabelForServer,
                serverId -> notificationStore == null ? 0 : notificationStore.count(serverId),
                serverId -> interceptorStore == null ? 0 : interceptorStore.totalHitCount(serverId),
                serverId ->
                    interceptorStore == null
                        ? List.of()
                        : interceptorStore.listInterceptors(serverId),
                leaves,
                this::builtInLayout,
                this::rootSiblingOrder,
                this::applyBuiltInLayoutToTree,
                this::applyRootSiblingOrderToTree,
                model,
                root,
                tree,
                nodeBadgeUpdater::refreshNotificationsCount,
                interceptorActions::refreshInterceptorGroupCount,
                serverStateCleaner::cleanupServerState,
                networkGroupManager::removeEmptyGroupIfNeeded));
    this.serverLifecycleFacade =
        new ServerTreeServerLifecycleFacade(serverRootLifecycleManager, statusLabelManager);
    this.settingsSynchronizer =
        new ServerTreeSettingsSynchronizer(
            ServerTreeSettingsSynchronizer.context(
                settingsBus,
                jfrRuntimeEventsService,
                runtimeConfig,
                () -> typingIndicatorsTreeEnabled,
                enabled -> typingIndicatorsTreeEnabled = enabled,
                this::clearTypingIndicatorsIfReady,
                style ->
                    typingIndicatorStyle =
                        style == null ? ServerTreeTypingIndicatorStyle.DOTS : style,
                enabled -> serverTreeNotificationBadgesEnabled = enabled,
                percent -> unreadBadgeScalePercent = percent,
                color -> unreadChannelTextColor = color,
                color -> highlightChannelTextColor = color,
                this::refreshTreeLayoutAfterUiChange,
                this::refreshApplicationJfrNode),
            TREE_BADGE_SCALE_PERCENT_DEFAULT);
    this.targetLifecycleCoordinator =
        new ServerTreeTargetLifecycleCoordinator(
            servers,
            leaves,
            serverCatalog,
            ensureNodeParentResolver,
            ensureNodeLeafInserter,
            targetNodePolicy,
            targetSnapshotProvider,
            targetRemovalStateCoordinator,
            targetNodeRemovalMutator,
            new ServerTreeTargetLifecycleContextAdapter(
                () -> showApplicationRoot,
                this::setApplicationRootVisible,
                applicationNodes::labelFor,
                applicationNodes::addLeaf,
                () -> model.nodeStructureChanged(applicationRoot),
                () -> showDccTransfersNodes,
                this::setDccTransfersNodesVisible,
                this::builtInNodesVisibility,
                this::addServerRoot,
                this::builtInLayoutNodeKindForRef,
                this::builtInLayout,
                this::rootSiblingOrder,
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
            new ServerTreeCellRendererContextAdapter(
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
                nodeClassifier::isMonitorGroupNode,
                nodeClassifier::isInterceptorsGroupNode,
                nodeClassifier::isOtherGroupNode,
                uiHooks::isServerNode,
                this::serverNodeDisplayLabel,
                ephemeralServerIds::contains,
                uiHooks::connectionStateForServer,
                nodeAccess::isIrcRootNode,
                nodeAccess::isApplicationRootNode,
                nodeClassifier::isPrivateMessagesGroupNode,
                networkGroupManager::isSojuNetworksGroupNode,
                networkGroupManager::isZncNetworksGroupNode));
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
    JPanel header = headerControls.panel();

    root.add(ircRoot);
    applicationNodes.initialize();
    if (showApplicationRoot) {
      root.add(applicationRoot);
    }

    add(header, BorderLayout.NORTH);
    setConnectionControlsEnabled(true, false);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(0);
    ServerTreeUiRefreshCoordinator.applyTreeFontFromUiDefaults(tree);

    tree.setCellRenderer(treeCellRenderer);
    this.typingActivityTimer =
        new Timer(TYPING_ACTIVITY_TICK_MS, e -> onTypingActivityAnimationTick());
    this.typingActivityTimer.setRepeats(true);
    this.typingActivityManager =
        new ServerTreeTypingActivityManager(
            leaves,
            typingActivityNodes,
            typingActivityTimer,
            TYPING_ACTIVITY_HOLD_MS,
            TYPING_ACTIVITY_FADE_MS,
            ServerTreeTypingActivityManager.context(
                ServerTreeTypingTargetPolicy::supportsTypingActivity,
                () -> typingIndicatorsTreeEnabled,
                () -> ServerTreeDockable.this.isShowing() && tree.isShowing(),
                this::repaintTreeNode));
    this.channelDisconnectStateManager =
        new ServerTreeChannelDisconnectStateManager(
            typingActivityNodes,
            typingActivityTimer,
            ServerTreeChannelDisconnectStateManager.context(
                this::ensureNode,
                leaves::get,
                model::nodeChanged,
                this::emitManagedChannelsChanged));
    this.targetSelectionCoordinator =
        new ServerTreeTargetSelectionCoordinator(
            new ServerTreeTargetSelectionContextAdapter(
                this::ensureNode,
                serverNodeResolver::monitorNodeForServer,
                serverNodeResolver::interceptorsNodeForServer,
                serverNodeResolver::serverNodesForServer,
                leaves::get,
                node -> {
                  if (node == null) return;
                  TreePath path = new TreePath(node.getPath());
                  tree.setSelectionPath(path);
                  tree.scrollPathToVisible(path);
                }));
    this.unreadStateCoordinator =
        new ServerTreeUnreadStateCoordinator(
            leaves,
            model,
            this::isChannelMuted,
            channelStateCoordinator::noteChannelActivity,
            channelStateCoordinator::onChannelUnreadCountsChanged,
            this::emitManagedChannelsChanged);
    ToolTipManager.sharedInstance().registerComponent(tree);
    tree.addPropertyChangeListener(
        "UI", e -> SwingUtilities.invokeLater(this::refreshTreeLayoutAfterUiChange));
    this.nodeActions =
        nodeActionsFactory.create(
            new ServerTreeNodeActionsFactory.Inputs(
                tree,
                model,
                uiHooks::isServerNode,
                nodeAccess::isChannelListLeafNode,
                this::isChannelPinned,
                nodeAccess::targetRefForNode,
                nodeAccess::nodeLabelForNode,
                this::isChannelDisconnected,
                requestEmitter::emitDisconnectChannel,
                requestEmitter::emitCloseTarget,
                dragReorderSupport::isRootSiblingReorderableNode,
                dragReorderSupport::isMovableBuiltInNode,
                nodeClassifier::owningServerIdForNode,
                channelStateCoordinator::persistOrderAndResortAfterManualMove,
                this::persistRootSiblingOrderFromTree,
                this::persistBuiltInLayoutFromTree));
    ServerTreeKeyBindingsInstaller.install(
        tree,
        this::moveNodeUpAction,
        this::moveNodeDownAction,
        this::closeNodeAction,
        this::openSelectedNodeInChatDock);

    treeScroll.setPreferredSize(new Dimension(260, 400));
    treeScroll.setMinimumSize(new Dimension(0, 0));
    enforceTreeScrollPanePolicies();
    treeWheelSelectionDecorator = TreeWheelSelectionDecorator.decorate(tree, treeScroll);
    add(treeScroll, BorderLayout.CENTER);
    externalStreamBinder.bind(
        serverCatalog, notificationStore, interceptorStore, sojuAutoConnect, zncAutoConnect);

    settingsSynchronizer.bindListeners();

    ServerTreeMiddleDragReorderHandler.Context middleDragReorderContext =
        interactionWiringFactory.createMiddleDragReorderContext(
            new ServerTreeInteractionWiringFactory.MiddleDragInputs(
                tree,
                model,
                dragReorderSupport::isDraggableChannelNode,
                dragReorderSupport::isRootSiblingReorderableNode,
                dragReorderSupport::isMovableBuiltInNode,
                nodeClassifier::owningServerIdForNode,
                serverId -> servers.get(ServerTreeDockable.normalizeServerId(serverId)),
                this::rootSiblingNodeKindForNode,
                this::builtInLayoutNodeKindForNode,
                dragReorderSupport::minInsertIndex,
                dragReorderSupport::maxInsertIndex,
                builtInLayoutVisibilityFacade::rootBuiltInInsertIndex,
                dragReorderSupport::setInsertionLineForIndex,
                dragReorderSupport::clearInsertionLine,
                nodeAccess::isChannelListLeafNode,
                parentNode -> {
                  String serverId = nodeClassifier.owningServerIdForNode(parentNode);
                  if (serverId.isBlank()) return;
                  channelStateCoordinator.persistOrderAndResortAfterManualMove(serverId);
                },
                this::persistBuiltInLayoutFromTree,
                this::persistRootSiblingOrderFromTree,
                selectionBroadcastCoordinator::withSuppressedSelectionBroadcast,
                nodeActions::refreshEnabledState));
    this.pinnedDockDragController =
        interactionWiringFactory.createPinnedDockDragController(
            new ServerTreeInteractionWiringFactory.PinnedDockDragInputs(
                tree,
                (x, y) -> channelTargetForTreePath(rowInteractionHandler.treePathForRowHit(x, y))));
    this.interactionMediator =
        interactionWiringFactory.createInteractionMediator(
            new ServerTreeInteractionWiringFactory.MediatorInputs(
                tree,
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
                nodeClassifier::isMonitorGroupNode,
                nodeClassifier::isInterceptorsGroupNode,
                nodeClassifier::owningServerIdForNode,
                rowInteractionHandler::maybeHandleDisconnectedWarningClick,
                rowInteractionHandler::maybeSelectRowFromLeftClick,
                (x, y) -> rowInteractionHandler.treePathForRowHit(x, y),
                selectionBroadcastCoordinator::withSuppressedSelectionBroadcast,
                nodeActions::refreshEnabledState,
                contextMenuBuilder::build,
                pinnedDockDragController::prepareChannelDockDrag,
                pinnedDockDragController::clearPreparedChannelDockDrag,
                () -> middleDragReorderContext,
                () -> startupSelectionCompleted,
                () -> startupSelectionCompleted = true,
                this::isPathInCurrentTreeModel,
                this::firstServerIdOrEmpty,
                this::selectStartupDefaultForServer,
                this::defaultSelectionPath));
    this.interactionMediator.install();
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

  private ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    return builtInLayoutVisibilityFacade.builtInNodesVisibility(serverId);
  }

  private RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout(String serverId) {
    return builtInLayoutVisibilityFacade.builtInLayout(serverId);
  }

  private void persistBuiltInLayoutForServer(
      String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    builtInLayoutVisibilityFacade.rememberBuiltInLayout(serverId, layout);
  }

  private RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder(String serverId) {
    return builtInLayoutVisibilityFacade.rootSiblingOrder(serverId);
  }

  private void persistRootSiblingOrderForServer(
      String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
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
    String base = serverLabelPolicy.prettyServerLabel(sid);
    ConnectionState state = runtimeState.connectionStateForServer(sid);
    boolean desired = runtimeState.desiredOnlineForServer(sid);
    String badge = ServerTreeConnectionStateViewModel.desiredBadge(state, desired);
    return badge.isEmpty() ? base : (base + badge);
  }

  private String firstServerIdOrEmpty() {
    return serverNodeResolver.firstServerIdOrEmpty(startupSelectionRestorer::rememberedSelection);
  }

  private RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForRef(
      TargetRef ref) {
    return builtInLayoutVisibilityFacade.builtInLayoutNodeKindForRef(ref);
  }

  private RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
      DefaultMutableTreeNode node) {
    return builtInLayoutVisibilityFacade.builtInLayoutNodeKindForNode(node);
  }

  private RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
      DefaultMutableTreeNode node) {
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
    return selectionPersistencePolicy.selectedTargetForPersistence();
  }

  public Flowable<TargetRef> selectionStream() {
    return selectionBroadcastCoordinator.selectionStream();
  }

  public Flowable<String> connectServerRequests() {
    return requestStreams.connectServerRequests();
  }

  public Flowable<String> disconnectServerRequests() {
    return requestStreams.disconnectServerRequests();
  }

  public Flowable<TargetRef> closeTargetRequests() {
    return requestStreams.closeTargetRequests();
  }

  public Flowable<TargetRef> joinChannelRequests() {
    return requestStreams.joinChannelRequests();
  }

  public Flowable<TargetRef> disconnectChannelRequests() {
    return requestStreams.disconnectChannelRequests();
  }

  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return requestStreams.bouncerDetachChannelRequests();
  }

  public Flowable<TargetRef> closeChannelRequests() {
    return requestStreams.closeChannelRequests();
  }

  public Flowable<String> managedChannelsChangedByServer() {
    return requestStreams.managedChannelsChangedByServer();
  }

  public Flowable<TargetRef> clearLogRequests() {
    return requestStreams.clearLogRequests();
  }

  public Flowable<TargetRef> openPinnedChatRequests() {
    return requestStreams.openPinnedChatRequests();
  }

  public Flowable<String> quasselSetupRequests() {
    return requestStreams.openQuasselSetupRequests();
  }

  public Flowable<String> quasselNetworkManagerRequests() {
    return requestStreams.openQuasselNetworkManagerRequests();
  }

  public void setPinnedDockableProvider(Function<TargetRef, Dockable> provider) {
    pinnedDockDragController.setPinnedDockableProvider(provider);
  }

  public Flowable<TargetRef> channelModeDetailsRequests() {
    return channelModeRequestBus.detailsRequests();
  }

  public Flowable<TargetRef> channelModeRefreshRequests() {
    return channelModeRequestBus.refreshRequests();
  }

  public Flowable<ChannelModeSetRequest> channelModeSetRequests() {
    return channelModeRequestBus.setRequests();
  }

  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return requestStreams.ircv3CapabilityToggleRequests();
  }

  /**
   * Returns currently open channel targets for a server.
   *
   * <p>Safe to call from any thread.
   */
  public List<String> openChannelsForServer(String serverId) {
    return channelQueryService.openChannelsForServer(serverId);
  }

  public List<ManagedChannelEntry> managedChannelsForServer(String serverId) {
    return channelQueryService.managedChannelsForServer(serverId);
  }

  public ChannelSortMode channelSortModeForServer(String serverId) {
    return channelQueryService.channelSortModeForServer(serverId);
  }

  public void setChannelSortModeForServer(String serverId, ChannelSortMode mode) {
    channelQueryService.setChannelSortModeForServer(serverId, mode);
  }

  public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
    channelQueryService.setChannelCustomOrderForServer(serverId, channels);
  }

  public void setCanEditChannelModes(BiPredicate<String, String> canEditChannelModes) {
    channelTargetOperations.setCanEditChannelModes(canEditChannelModes);
  }

  public void requestJoinChannel(TargetRef target) {
    channelTargetOperations.requestJoinChannel(target);
  }

  public void requestDisconnectChannel(TargetRef target) {
    channelTargetOperations.requestDisconnectChannel(target);
  }

  public void requestCloseChannel(TargetRef target) {
    channelTargetOperations.requestCloseChannel(target);
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
    serverRuntimeUiUpdater.setServerConnectionState(serverId, state);
  }

  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    serverRuntimeUiUpdater.setServerDesiredOnline(serverId, desiredOnline);
  }

  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    serverRuntimeUiUpdater.setServerConnectionDiagnostics(serverId, lastError, nextRetryEpochMs);
  }

  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    serverRuntimeUiUpdater.setServerConnectedIdentity(
        serverId, connectedHost, connectedPort, nick, at);
  }

  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    serverRuntimeUiUpdater.setServerIrcv3Capability(serverId, capability, subcommand, enabled);
  }

  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    serverRuntimeUiUpdater.setServerIsupportToken(serverId, tokenName, tokenValue);
  }

  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    serverRuntimeUiUpdater.setServerVersionDetails(
        serverId, serverName, serverVersion, userModes, channelModes);
  }

  private void openServerInfoDialog(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    networkInfoDialogBuilder.open(this, sid, runtimeState.metadataForServer(sid));
  }

  public void setStatusText(String text) {
    headerControls.setStatusText(text);
  }

  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    headerControls.setConnectionControlsEnabled(connectEnabled, disconnectEnabled);
  }

  /**
   * Back-compat convenience: historically we used a single boolean to toggle the buttons.
   *
   * @deprecated Prefer {@link #setConnectionControlsEnabled(boolean, boolean)}.
   */
  @Deprecated
  public void setConnectedUi(boolean connected) {
    setConnectionControlsEnabled(!connected, connected);
  }

  public boolean isChannelListNodesVisible() {
    return true;
  }

  public boolean isDccTransfersNodesVisible() {
    return showDccTransfersNodes;
  }

  public boolean isServerNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(ServerBuiltInNodesVisibility::server);
  }

  public boolean isLogViewerNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(ServerBuiltInNodesVisibility::logViewer);
  }

  public boolean isNotificationsNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(ServerBuiltInNodesVisibility::notifications);
  }

  public boolean isMonitorNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(ServerBuiltInNodesVisibility::monitor);
  }

  public boolean isInterceptorsNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(ServerBuiltInNodesVisibility::interceptors);
  }

  public boolean isServerNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        serverId, ServerBuiltInNodesVisibility::server);
  }

  public boolean isLogViewerNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        serverId, ServerBuiltInNodesVisibility::logViewer);
  }

  public boolean isNotificationsNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        serverId, ServerBuiltInNodesVisibility::notifications);
  }

  public boolean isMonitorNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        serverId, ServerBuiltInNodesVisibility::monitor);
  }

  public boolean isInterceptorsNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        serverId, ServerBuiltInNodesVisibility::interceptors);
  }

  public void setServerNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        serverId, visible, ServerBuiltInNodesVisibility::withServer);
  }

  public void setLogViewerNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        serverId, visible, ServerBuiltInNodesVisibility::withLogViewer);
  }

  public void setNotificationsNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        serverId, visible, ServerBuiltInNodesVisibility::withNotifications);
  }

  public void setMonitorNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        serverId, visible, ServerBuiltInNodesVisibility::withMonitor);
  }

  public void setInterceptorsNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        serverId, visible, ServerBuiltInNodesVisibility::withInterceptors);
  }

  public boolean isApplicationRootVisible() {
    return showApplicationRoot;
  }

  public void setChannelListNodesVisible(boolean visible) {
    // Channel List is always visible for each server.
    if (!visible) return;
    boolean old = showChannelListNodes;
    showChannelListNodes = true;
    if (!old) {
      syncUiLeafVisibility();
      firePropertyChange(PROP_CHANNEL_LIST_NODES_VISIBLE, false, true);
    }
  }

  public void setDccTransfersNodesVisible(boolean visible) {
    boolean old = showDccTransfersNodes;
    boolean next = visible;
    if (old == next) return;
    showDccTransfersNodes = next;
    syncUiLeafVisibility();
    firePropertyChange(PROP_DCC_TRANSFERS_NODES_VISIBLE, old, next);
  }

  /**
   * Back-compat/global toggle for all current and future servers.
   *
   * <p>Per-server callers should use {@link #setServerNodeVisibleForServer(String, boolean)}.
   */
  public void setServerNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        visible,
        ServerBuiltInNodesVisibility::server,
        ServerBuiltInNodesVisibility::withServer,
        null);
  }

  public void setLogViewerNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        visible,
        ServerBuiltInNodesVisibility::logViewer,
        ServerBuiltInNodesVisibility::withLogViewer,
        PROP_LOG_VIEWER_NODES_VISIBLE);
  }

  public void setNotificationsNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        visible,
        ServerBuiltInNodesVisibility::notifications,
        ServerBuiltInNodesVisibility::withNotifications,
        PROP_NOTIFICATIONS_NODES_VISIBLE);
  }

  public void setMonitorNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        visible,
        ServerBuiltInNodesVisibility::monitor,
        ServerBuiltInNodesVisibility::withMonitor,
        PROP_MONITOR_NODES_VISIBLE);
  }

  public void setInterceptorsNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        visible,
        ServerBuiltInNodesVisibility::interceptors,
        ServerBuiltInNodesVisibility::withInterceptors,
        PROP_INTERCEPTORS_NODES_VISIBLE);
  }

  public void setApplicationRootVisible(boolean visible) {
    boolean old = showApplicationRoot;
    boolean next = visible;
    if (old == next) return;
    showApplicationRoot = next;
    syncApplicationRootVisibility();
    firePropertyChange(PROP_APPLICATION_ROOT_VISIBLE, old, next);
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
    String sid = Objects.toString(ref.serverId(), "").trim();
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
    selectionFallbackPolicy.selectBestFallbackForServer(serverId);
  }

  private void selectStartupDefaultForServer(String serverId) {
    if (startupSelectionRestorer.tryRestoreForServer(serverId)) {
      return;
    }
    selectionFallbackPolicy.selectStartupDefaultForServer(serverId);
  }

  private void applyBuiltInLayoutToTree(
      ServerNodes sn, RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout) {
    builtInLayoutVisibilityFacade.applyBuiltInLayoutToTree(sn, requestedLayout);
  }

  private void applyRootSiblingOrderToTree(
      ServerNodes sn, RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder) {
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

  private DefaultMutableTreeNode ensureChannelListNodeForEnsureNode(ServerNodes sn) {
    return channelListNodeEnsurer.ensureChannelListNode(sn);
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
    channelDisconnectStateManager.setChannelDisconnected(ref, detached, warningReason);
  }

  public void clearChannelDisconnectedWarning(TargetRef ref) {
    channelDisconnectStateManager.clearChannelDisconnectedWarning(ref);
  }

  public boolean isChannelDisconnected(TargetRef ref) {
    return channelDisconnectStateManager.isChannelDisconnected(ref);
  }

  public boolean isChannelAutoReattach(TargetRef ref) {
    return channelTargetOperations.isChannelAutoReattach(ref);
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    channelTargetOperations.setChannelAutoReattach(ref, autoReattach);
  }

  public boolean isChannelPinned(TargetRef ref) {
    return channelTargetOperations.isChannelPinned(ref);
  }

  public void setChannelPinned(TargetRef ref, boolean pinned) {
    channelTargetOperations.setChannelPinned(ref, pinned);
  }

  public boolean isChannelMuted(TargetRef ref) {
    return channelTargetOperations.isChannelMuted(ref);
  }

  public void setChannelMuted(TargetRef ref, boolean muted) {
    channelTargetOperations.setChannelMuted(ref, muted);
  }

  private void onChannelMutedStateChangedFromChannelTarget(TargetRef ref, boolean muted) {
    if (unreadStateCoordinator == null) return;
    unreadStateCoordinator.onChannelMutedStateChanged(ref, muted);
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
    unreadStateCoordinator.markUnread(ref);
  }

  public void markHighlight(TargetRef ref) {
    unreadStateCoordinator.markHighlight(ref);
  }

  public void clearUnread(TargetRef ref) {
    unreadStateCoordinator.clearUnread(ref);
  }

  public void markTypingActivity(TargetRef ref, String state) {
    typingActivityManager.markTypingActivity(ref, state);
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

  private boolean shouldPersistPrivateMessageList() {
    return runtimeConfig != null
        && logProps != null
        && Boolean.TRUE.equals(logProps.savePrivateMessageList());
  }

  private void removeServerRoot(String serverId) {
    serverLifecycleFacade.removeServerRoot(serverId);
  }

  private ServerNodes addServerRoot(String serverId) {
    return serverLifecycleFacade.addServerRoot(serverId);
  }

  private void updateBouncerControlLabels(
      Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl) {
    serverLifecycleFacade.updateBouncerControlLabels(nextSojuBouncerControl, nextZncBouncerControl);
  }

  private void refreshTreeLayoutAfterUiChange() {
    uiRefreshCoordinator.refreshTreeLayoutAfterUiChange();
  }

  @PreDestroy
  public void shutdown() {
    try {
      pinnedDockDragController.clearPreparedChannelDockDrag();
      settingsSynchronizer.shutdown();
      if (typingActivityTimer != null) typingActivityTimer.stop();
      if (treeWheelSelectionDecorator != null) treeWheelSelectionDecorator.close();
      nodeActions.close();
    } catch (Exception ignored) {
    }
    disposables.dispose();
  }
}
