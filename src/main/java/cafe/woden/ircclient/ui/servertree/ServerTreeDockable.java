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
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeApplicationRootVisibilityContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeBouncerDetachPolicyContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeBuiltInLayoutOrchestratorContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeBuiltInVisibilityContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeCellRendererContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeChannelDisconnectStateManagerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeChannelStateCoordinatorContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeContextMenuContextFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeInterceptorActionsContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeLayoutPersistenceContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeNetworkGroupManagerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeNetworkInfoDialogContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeNodeBadgeUpdaterContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeSelectionFallbackContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerActionOverlayContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerCatalogSynchronizerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerParentResolverContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerRootLifecycleContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeSettingsSynchronizerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTargetLifecycleContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTargetRemovalStateCoordinatorContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTargetSelectionCoordinatorContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTooltipContextFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTypingActivityManagerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeUiLeafVisibilitySynchronizerContextAdapter;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeApplicationRootVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelDisconnectStateManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerRootLifecycleManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetLifecycleCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetSelectionCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTypingActivityManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUnreadStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeDragReorderSupport;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionMediator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionWiringFactory;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeKeyBindingsInstaller;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeMiddleDragReorderHandler;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeNodeActionsFactory;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutVisibilityFacade;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeNodeVisibilityMutator;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionFallbackPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTypingTargetPolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeProcessorRequestEmitter;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestLoggingDecorator;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerRuntimeMetadata;
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
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
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
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

@org.springframework.stereotype.Component
@Lazy
public class ServerTreeDockable extends JPanel implements Dockable, Scrollable {
  private static final Logger log = LoggerFactory.getLogger(ServerTreeDockable.class);

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
  private static final String APP_UNHANDLED_ERRORS_LABEL = "Unhandled Errors";
  private static final String APP_ASSERTJ_SWING_LABEL = "AssertJ Swing";
  private static final String APP_JHICCUP_LABEL = "jHiccup";
  private static final String APP_INBOUND_DEDUP_LABEL = "Inbound Dedup";
  private static final String APP_JFR_LABEL = "JFR";
  private static final String APP_SPRING_LABEL = "Spring";
  private static final String APP_TERMINAL_LABEL = "Terminal";
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

  private final FlowableProcessor<TargetRef> selections =
      PublishProcessor.<TargetRef>create().toSerialized();

  /** Suppress broadcasting selection changes when selection is set for context menus. */
  private boolean suppressSelectionBroadcast = false;

  private final FlowableProcessor<String> connectServerRequests =
      PublishProcessor.<String>create().toSerialized();
  private final FlowableProcessor<String> disconnectServerRequests =
      PublishProcessor.<String>create().toSerialized();

  private final FlowableProcessor<TargetRef> closeTargetRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> joinChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> disconnectChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> bouncerDetachChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> closeChannelRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<String> managedChannelsChangedByServer =
      PublishProcessor.<String>create().toSerialized();

  private final FlowableProcessor<TargetRef> clearLogRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> openPinnedChatRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> channelModeDetailsRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> channelModeRefreshRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<ChannelModeSetRequest> channelModeSetRequests =
      PublishProcessor.<ChannelModeSetRequest>create().toSerialized();

  private final FlowableProcessor<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests =
      PublishProcessor.<Ircv3CapabilityToggleRequest>create().toSerialized();
  private final ServerTreeRequestEmitter requestEmitter =
      new ServerTreeRequestLoggingDecorator(
          new ServerTreeProcessorRequestEmitter(
              connectServerRequests,
              disconnectServerRequests,
              closeTargetRequests,
              joinChannelRequests,
              disconnectChannelRequests,
              bouncerDetachChannelRequests,
              closeChannelRequests,
              managedChannelsChangedByServer,
              clearLogRequests,
              openPinnedChatRequests,
              ircv3CapabilityToggleRequests));

  // Hidden top-level container. Visible top-level nodes are siblings: IRC + Application.
  private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("(root)");
  private final DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode(IRC_ROOT_LABEL);
  private final DefaultMutableTreeNode applicationRoot =
      new DefaultMutableTreeNode(APPLICATION_ROOT_LABEL);
  private final TargetRef applicationUnhandledErrorsRef = TargetRef.applicationUnhandledErrors();
  private final TargetRef applicationAssertjSwingRef = TargetRef.applicationAssertjSwing();
  private final TargetRef applicationJhiccupRef = TargetRef.applicationJhiccup();
  private final TargetRef applicationInboundDedupRef = TargetRef.applicationInboundDedup();
  private final TargetRef applicationJfrRef = TargetRef.applicationJfr();
  private final TargetRef applicationSpringRef = TargetRef.applicationSpring();
  private final TargetRef applicationTerminalRef = TargetRef.applicationTerminal();
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

  private final JLabel statusLabel = new JLabel("Disconnected");

  private final JButton addServerBtn = new JButton();
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;

  private final Map<String, ServerNodes> servers = new HashMap<>();
  private final Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
  private final ServerTreeTargetSnapshotProvider targetSnapshotProvider =
      new ServerTreeTargetSnapshotProvider(leaves, root);
  private final ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore =
      new ServerTreePrivateMessageOnlineStateStore();
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
  private final ServerTreeServerParentResolver serverParentResolver;
  private final ServerTreeServerLabelPolicy serverLabelPolicy;
  private final ServerTreeBouncerDetachPolicy bouncerDetachPolicy;
  private final ServerTreeNodeBadgeUpdater nodeBadgeUpdater;
  private final ServerTreeSelectionFallbackPolicy selectionFallbackPolicy;
  private final ServerTreeUiLeafVisibilitySynchronizer uiLeafVisibilitySynchronizer;
  private final ServerTreeNodeVisibilityMutator nodeVisibilityMutator;
  private final ServerTreeExpansionStateManager expansionStateManager;
  private final ServerTreeApplicationRootVisibilityCoordinator applicationRootVisibilityCoordinator;

  private final ServerTreeBuiltInLayoutVisibilityFacade builtInLayoutVisibilityFacade;
  private final ServerTreeTargetNodePolicy targetNodePolicy;
  private final ServerTreeChannelStateCoordinator channelStateCoordinator;
  private final ServerTreeEnsureNodeParentResolver ensureNodeParentResolver;
  private final ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter;
  private final ServerTreeChannelDisconnectStateManager channelDisconnectStateManager;
  private final ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator;
  private final ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator;
  private final ServerTreeTargetLifecycleCoordinator targetLifecycleCoordinator;
  private final ServerTreeTargetSelectionCoordinator targetSelectionCoordinator;
  private final ServerTreeUnreadStateCoordinator unreadStateCoordinator;
  private final ServerTreeInteractionMediator interactionMediator;
  private final ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater;
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
  private volatile BiPredicate<String, String> canEditChannelModes = (serverId, channel) -> false;

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
            new ServerTreeBuiltInVisibilityContextAdapter(
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
            this::targetRefForNode);
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
            new ServerTreeNetworkInfoDialogContextAdapter(
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
            new ServerTreeInterceptorActionsContextAdapter(
                this::ensureNode,
                this::selectTarget,
                this::removeTarget,
                leaves::get,
                this::interceptorsGroupNodeForServer,
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
                this::hasValidTreeSelection,
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
            new ServerTreeNetworkGroupManagerContextAdapter(
                serverId -> {
                  ServerNodes nodes = servers.get(Objects.toString(serverId, "").trim());
                  return nodes == null ? null : nodes.serverNode;
                },
                serverId -> {
                  ServerNodes nodes = servers.get(Objects.toString(serverId, "").trim());
                  return nodes == null ? null : nodes.pmNode;
                }));
    this.dragReorderSupport =
        new ServerTreeDragReorderSupport(
            tree,
            servers,
            nodeClassifier,
            uiHooks::isServerNode,
            this::isChannelListLeafNode,
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
                new ServerTreeServerActionOverlayContextAdapter(uiHooks),
                new ServerTreeChannelStateCoordinatorContextAdapter(
                    ServerTreeDockable::normalizeServerId,
                    serverId -> {
                      String sid = normalizeServerId(serverId);
                      if (sid.isEmpty()) return null;
                      ServerNodes nodes = servers.get(sid);
                      if (nodes == null || nodes.channelListRef == null) return null;
                      return leaves.get(nodes.channelListRef);
                    },
                    this::snapshotExpandedTreePaths,
                    this::restoreExpandedTreePaths,
                    this::emitManagedChannelsChanged),
                new ServerTreeTargetRemovalStateCoordinatorContextAdapter(
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
            new ServerTreeServerParentResolverContextAdapter(
                serverId -> servers.containsKey(Objects.toString(serverId, "").trim()),
                this::addServerRoot,
                () -> ircRoot,
                networkGroupManager::getOrCreateSojuNetworksGroupNode,
                networkGroupManager::getOrCreateZncNetworksGroupNode));
    this.bouncerDetachPolicy =
        new ServerTreeBouncerDetachPolicy(
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            new ServerTreeBouncerDetachPolicyContextAdapter(
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
            new ServerTreeNodeBadgeUpdaterContextAdapter(
                model::nodeChanged,
                serverId -> {
                  ServerNodes nodes = servers.get(Objects.toString(serverId, "").trim());
                  return nodes == null ? null : nodes.serverNode;
                }));
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
            new ServerTreeSelectionFallbackContextAdapter(
                ServerTreeDockable::normalizeServerId,
                servers,
                this::builtInNodesVisibility,
                leaves,
                this::selectTarget,
                tree));
    this.uiLeafVisibilitySynchronizer =
        new ServerTreeUiLeafVisibilitySynchronizer(
            new ServerTreeUiLeafVisibilitySynchronizerContextAdapter(
                this::selectedTargetRef,
                () -> (DefaultMutableTreeNode) tree.getLastSelectedPathComponent(),
                nodeClassifier::isMonitorGroupNode,
                nodeClassifier::isInterceptorsGroupNode,
                nodeClassifier::owningServerIdForNode,
                () -> List.copyOf(servers.keySet()),
                this::syncUiLeafVisibilityForServer,
                serverId -> builtInNodesVisibility(serverId).server(),
                serverId -> builtInNodesVisibility(serverId).notifications(),
                serverId -> builtInNodesVisibility(serverId).logViewer(),
                serverId -> builtInNodesVisibility(serverId).monitor(),
                serverId -> builtInNodesVisibility(serverId).interceptors(),
                () -> showDccTransfersNodes,
                this::selectBestFallbackForServer));
    this.nodeVisibilityMutator =
        new ServerTreeNodeVisibilityMutator(model, leaves, typingActivityNodes);
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
                this::firstServerStatusRefOrNull,
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
    this.channelStateCoordinator = stateInteractionCollaborators.channelStateCoordinator();
    this.ensureNodeParentResolver = stateInteractionCollaborators.ensureNodeParentResolver();
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
                    this::isIrcRootNode,
                    this::isApplicationRootNode,
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
                    this::isRootServerNode,
                    serverLabelPolicy::prettyServerLabel,
                    uiHooks::connectionStateForServer,
                    serverCatalog,
                    this::moveNodeUpAction,
                    this::moveNodeDownAction,
                    uiHooks::connectServer,
                    uiHooks::disconnectServer,
                    this::openServerInfoDialog,
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
                    this::emitChannelModeDetailsRequest,
                    this::emitChannelModeRefreshRequest,
                    this::canEditChannelModesForTarget,
                    this::emitChannelModeSetRequest,
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
    this.settingsSynchronizer =
        new ServerTreeSettingsSynchronizer(
            new ServerTreeSettingsSynchronizerContextAdapter(
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
                ServerTreeDockable::applicationLeafLabel,
                this::addApplicationLeaf,
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
                this::isIrcRootNode,
                this::isApplicationRootNode,
                nodeClassifier::isPrivateMessagesGroupNode,
                networkGroupManager::isSojuNetworksGroupNode,
                networkGroupManager::isZncNetworksGroupNode));

    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;
    configureHeaderButtons(serverDialogs);
    JPanel header = buildHeaderPanel();

    root.add(ircRoot);
    initializeApplicationTreeNodes();
    if (showApplicationRoot) {
      root.add(applicationRoot);
    }

    add(header, BorderLayout.NORTH);
    setConnectionControlsEnabled(true, false);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(0);
    applyTreeFontFromUiDefaults();

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
            new ServerTreeTypingActivityManagerContextAdapter(
                ServerTreeTypingTargetPolicy::supportsTypingActivity,
                () -> typingIndicatorsTreeEnabled,
                () -> ServerTreeDockable.this.isShowing() && tree.isShowing(),
                this::repaintTreeNode));
    this.channelDisconnectStateManager =
        new ServerTreeChannelDisconnectStateManager(
            typingActivityNodes,
            typingActivityTimer,
            new ServerTreeChannelDisconnectStateManagerContextAdapter(
                this::ensureNode,
                leaves::get,
                model::nodeChanged,
                this::emitManagedChannelsChanged));
    this.targetSelectionCoordinator =
        new ServerTreeTargetSelectionCoordinator(
            new ServerTreeTargetSelectionCoordinatorContextAdapter(
                this::ensureNode,
                serverId -> {
                  ServerNodes nodes = servers.get(normalizeServerId(serverId));
                  return nodes == null ? null : nodes.monitorNode;
                },
                serverId -> {
                  ServerNodes nodes = servers.get(normalizeServerId(serverId));
                  return nodes == null ? null : nodes.interceptorsNode;
                },
                (serverId, node) -> {
                  ServerNodes nodes = servers.get(normalizeServerId(serverId));
                  if (nodes == null || node == null) return false;
                  return node.getParent() == nodes.serverNode
                      || node.getParent() == nodes.otherNode;
                },
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
                this::isChannelListLeafNode,
                this::isChannelPinned,
                this::targetRefForNode,
                this::nodeLabelForNode,
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
                this::isChannelListLeafNode,
                parentNode -> {
                  String serverId = nodeClassifier.owningServerIdForNode(parentNode);
                  if (serverId.isBlank()) return;
                  channelStateCoordinator.persistOrderAndResortAfterManualMove(serverId);
                },
                this::persistBuiltInLayoutFromTree,
                this::persistRootSiblingOrderFromTree,
                this::withSuppressedSelectionBroadcast,
                nodeActions::refreshEnabledState));
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
                () -> suppressSelectionBroadcast,
                ref -> {
                  if (ref == null) return;
                  selections.onNext(ref);
                },
                nodeClassifier::isMonitorGroupNode,
                nodeClassifier::isInterceptorsGroupNode,
                nodeClassifier::owningServerIdForNode,
                rowInteractionHandler::maybeHandleDisconnectedWarningClick,
                rowInteractionHandler::maybeSelectRowFromLeftClick,
                (x, y) -> rowInteractionHandler.treePathForRowHit(x, y),
                this::withSuppressedSelectionBroadcast,
                nodeActions::refreshEnabledState,
                contextMenuBuilder::build,
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
    return Objects.toString(serverId, "").trim();
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

  private void applyBuiltInNodesVisibilityGlobally(
      java.util.function.UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
    builtInLayoutVisibilityFacade.applyBuiltInNodesVisibilityGlobally(mutator);
  }

  private void applyBuiltInNodesVisibilityForServer(
      String serverId, ServerBuiltInNodesVisibility next, boolean persist, boolean syncUi) {
    builtInLayoutVisibilityFacade.applyBuiltInNodesVisibilityForServer(
        serverId, next, persist, syncUi);
  }

  private void withSuppressedSelectionBroadcast(Runnable task) {
    if (task == null) return;
    suppressSelectionBroadcast = true;
    try {
      task.run();
    } finally {
      suppressSelectionBroadcast = false;
    }
  }

  private static boolean isChannelTarget(TargetRef ref) {
    return ref != null && ref.isChannel();
  }

  private boolean readChannelState(
      TargetRef ref, boolean fallback, Function<TargetRef, Boolean> reader) {
    if (!isChannelTarget(ref)) return fallback;
    return edtExecutor.read(() -> reader.apply(ref), fallback, null);
  }

  private void writeChannelState(TargetRef ref, Runnable writer) {
    if (!isChannelTarget(ref)) return;
    edtExecutor.write(writer);
  }

  private void emitChannelRequest(TargetRef target, Consumer<TargetRef> emitter) {
    if (!isChannelTarget(target)) return;
    emitter.accept(target);
  }

  private void emitChannelModeDetailsRequest(TargetRef target) {
    if (!isChannelTarget(target)) return;
    channelModeDetailsRequests.onNext(target);
  }

  private void emitChannelModeRefreshRequest(TargetRef target) {
    if (!isChannelTarget(target)) return;
    channelModeRefreshRequests.onNext(target);
  }

  private void emitChannelModeSetRequest(TargetRef target, String modeSpec) {
    if (!isChannelTarget(target)) return;
    String spec = Objects.toString(modeSpec, "").trim();
    if (spec.isEmpty()) return;
    channelModeSetRequests.onNext(new ChannelModeSetRequest(target, spec));
  }

  private boolean canEditChannelModesForTarget(TargetRef target) {
    if (!isChannelTarget(target)) return false;
    BiPredicate<String, String> predicate = canEditChannelModes;
    if (predicate == null) return false;
    try {
      return predicate.test(target.serverId(), target.target());
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean defaultBuiltInVisibility(Function<ServerBuiltInNodesVisibility, Boolean> getter) {
    return getter.apply(builtInLayoutVisibilityFacade.defaultVisibility());
  }

  private boolean serverBuiltInVisibility(
      String serverId, Function<ServerBuiltInNodesVisibility, Boolean> getter) {
    return getter.apply(builtInNodesVisibility(serverId));
  }

  private void setBuiltInVisibilityForServer(
      String serverId,
      boolean visible,
      BiFunction<ServerBuiltInNodesVisibility, Boolean, ServerBuiltInNodesVisibility> updater) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    applyBuiltInNodesVisibilityForServer(sid, updater.apply(current, visible), true, true);
  }

  private void setDefaultBuiltInVisibility(
      boolean visible,
      Function<ServerBuiltInNodesVisibility, Boolean> getter,
      BiFunction<ServerBuiltInNodesVisibility, Boolean, ServerBuiltInNodesVisibility> updater,
      String propertyName) {
    ServerBuiltInNodesVisibility current = builtInLayoutVisibilityFacade.defaultVisibility();
    boolean old = getter.apply(current);
    builtInLayoutVisibilityFacade.setDefaultVisibility(updater.apply(current, visible));
    applyBuiltInNodesVisibilityGlobally(v -> updater.apply(v, visible));
    if (propertyName != null && !propertyName.isBlank()) {
      firePropertyChange(propertyName, old, visible);
    }
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

  private DefaultMutableTreeNode interceptorsGroupNodeForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;
    ServerNodes nodes = servers.get(sid);
    if (nodes == null) return null;
    return nodes.interceptorsNode;
  }

  private String firstServerIdOrEmpty() {
    return servers.values().stream().findFirst().map(sn -> sn.statusRef.serverId()).orElse("");
  }

  private TargetRef firstServerStatusRefOrNull() {
    return servers.values().stream().findFirst().map(sn -> sn.statusRef).orElse(null);
  }

  private boolean isRootServerNode(DefaultMutableTreeNode node) {
    return node != null && node.getParent() == ircRoot && uiHooks.isServerNode(node);
  }

  private boolean isIrcRootNode(DefaultMutableTreeNode node) {
    return node != null && node == ircRoot;
  }

  private boolean isApplicationRootNode(DefaultMutableTreeNode node) {
    return node != null && node == applicationRoot;
  }

  private boolean isChannelListLeafNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof ServerTreeNodeData nd)) return false;
    return nd.ref != null && nd.ref.isChannelList();
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

  private TargetRef targetRefForNode(DefaultMutableTreeNode node) {
    if (node == null) return null;
    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeNodeData nd) return nd.ref;
    return null;
  }

  private String nodeLabelForNode(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeNodeData nd) return Objects.toString(nd.label, "");
    if (uo instanceof String s) return s;
    return "";
  }

  private void configureHeaderButtons(ServerDialogs serverDialogs) {
    addServerBtn.setText("");
    addServerBtn.setIcon(SvgIcons.action("plus", 16));
    addServerBtn.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    addServerBtn.setToolTipText("Add server");
    addServerBtn.setFocusable(false);
    addServerBtn.setPreferredSize(new Dimension(26, 26));
    addServerBtn.setEnabled(serverDialogs != null);
    addServerBtn.addActionListener(
        ev -> {
          if (serverDialogs == null) return;
          Window w = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
          serverDialogs.openAddServer(w);
        });
    connectBtn.setText("");
    connectBtn.setIcon(SvgIcons.action("check", 16));
    connectBtn.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    connectBtn.setToolTipText("Connect all disconnected servers");
    connectBtn.setFocusable(false);
    connectBtn.setPreferredSize(new Dimension(26, 26));
    disconnectBtn.setText("");
    disconnectBtn.setIcon(SvgIcons.action("exit", 16));
    disconnectBtn.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
    disconnectBtn.setToolTipText("Disconnect connected/connecting servers");
    disconnectBtn.setFocusable(false);
    disconnectBtn.setPreferredSize(new Dimension(26, 26));
  }

  private JPanel buildHeaderPanel() {
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    header.add(addServerBtn);
    header.add(Box.createHorizontalStrut(6));
    header.add(connectBtn);
    header.add(Box.createHorizontalStrut(6));
    header.add(disconnectBtn);
    header.add(Box.createHorizontalGlue());
    return header;
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

  public Flowable<TargetRef> selectionStream() {
    return selections.onBackpressureLatest();
  }

  public Flowable<String> connectServerRequests() {
    return connectServerRequests.onBackpressureLatest();
  }

  public Flowable<String> disconnectServerRequests() {
    return disconnectServerRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> closeTargetRequests() {
    return closeTargetRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> joinChannelRequests() {
    return joinChannelRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> disconnectChannelRequests() {
    return disconnectChannelRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> bouncerDetachChannelRequests() {
    return bouncerDetachChannelRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> closeChannelRequests() {
    return closeChannelRequests.onBackpressureLatest();
  }

  public Flowable<String> managedChannelsChangedByServer() {
    return managedChannelsChangedByServer.onBackpressureLatest();
  }

  public Flowable<TargetRef> clearLogRequests() {
    return clearLogRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> openPinnedChatRequests() {
    return openPinnedChatRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> channelModeDetailsRequests() {
    return channelModeDetailsRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> channelModeRefreshRequests() {
    return channelModeRefreshRequests.onBackpressureLatest();
  }

  public Flowable<ChannelModeSetRequest> channelModeSetRequests() {
    return channelModeSetRequests.onBackpressureLatest();
  }

  public Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return ircv3CapabilityToggleRequests.onBackpressureLatest();
  }

  /**
   * Returns currently open channel targets for a server.
   *
   * <p>Safe to call from any thread.
   */
  public List<String> openChannelsForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return List.of();
    return edtExecutor.read(
        () -> targetSnapshotProvider.snapshotOpenChannelsForServer(sid),
        List.of(),
        ex -> log.debug("[ircafe] open channel snapshot failed for server={}", sid, ex));
  }

  public List<ManagedChannelEntry> managedChannelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    return edtExecutor.read(
        () -> channelStateCoordinator.snapshotManagedChannelsForServer(sid),
        List.of(),
        ex -> log.debug("[ircafe] managed channel snapshot failed for server={}", sid, ex));
  }

  public ChannelSortMode channelSortModeForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return ChannelSortMode.CUSTOM;
    return edtExecutor.read(
        () -> channelStateCoordinator.channelSortModeForServer(sid),
        ChannelSortMode.CUSTOM,
        ex -> log.debug("[ircafe] channel sort mode snapshot failed for server={}", sid, ex));
  }

  public void setChannelSortModeForServer(String serverId, ChannelSortMode mode) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ChannelSortMode next = mode == null ? ChannelSortMode.CUSTOM : mode;
    edtExecutor.write(() -> channelStateCoordinator.setChannelSortModeForServer(sid, next));
  }

  public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    List<String> requested = channels == null ? List.of() : List.copyOf(channels);
    edtExecutor.write(() -> channelStateCoordinator.setChannelCustomOrderForServer(sid, requested));
  }

  public void setCanEditChannelModes(BiPredicate<String, String> canEditChannelModes) {
    this.canEditChannelModes =
        canEditChannelModes == null ? (serverId, channel) -> false : canEditChannelModes;
  }

  public void requestJoinChannel(TargetRef target) {
    emitChannelRequest(target, requestEmitter::emitJoinChannel);
  }

  public void requestDisconnectChannel(TargetRef target) {
    emitChannelRequest(target, requestEmitter::emitDisconnectChannel);
  }

  public void requestCloseChannel(TargetRef target) {
    emitChannelRequest(target, requestEmitter::emitCloseChannel);
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
    String t = Objects.toString(text, "").trim();
    statusLabel.setText(t);
    String suffix = t.isEmpty() ? "" : (" Current: " + t);
    connectBtn.setToolTipText("Connect all disconnected servers." + suffix);
    disconnectBtn.setToolTipText("Disconnect connected/connecting servers." + suffix);
  }

  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    connectBtn.setEnabled(connectEnabled);
    disconnectBtn.setEnabled(disconnectEnabled);
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
    return defaultBuiltInVisibility(ServerBuiltInNodesVisibility::server);
  }

  public boolean isLogViewerNodesVisible() {
    return defaultBuiltInVisibility(ServerBuiltInNodesVisibility::logViewer);
  }

  public boolean isNotificationsNodesVisible() {
    return defaultBuiltInVisibility(ServerBuiltInNodesVisibility::notifications);
  }

  public boolean isMonitorNodesVisible() {
    return defaultBuiltInVisibility(ServerBuiltInNodesVisibility::monitor);
  }

  public boolean isInterceptorsNodesVisible() {
    return defaultBuiltInVisibility(ServerBuiltInNodesVisibility::interceptors);
  }

  public boolean isServerNodeVisibleForServer(String serverId) {
    return serverBuiltInVisibility(serverId, ServerBuiltInNodesVisibility::server);
  }

  public boolean isLogViewerNodeVisibleForServer(String serverId) {
    return serverBuiltInVisibility(serverId, ServerBuiltInNodesVisibility::logViewer);
  }

  public boolean isNotificationsNodeVisibleForServer(String serverId) {
    return serverBuiltInVisibility(serverId, ServerBuiltInNodesVisibility::notifications);
  }

  public boolean isMonitorNodeVisibleForServer(String serverId) {
    return serverBuiltInVisibility(serverId, ServerBuiltInNodesVisibility::monitor);
  }

  public boolean isInterceptorsNodeVisibleForServer(String serverId) {
    return serverBuiltInVisibility(serverId, ServerBuiltInNodesVisibility::interceptors);
  }

  public void setServerNodeVisibleForServer(String serverId, boolean visible) {
    setBuiltInVisibilityForServer(serverId, visible, ServerBuiltInNodesVisibility::withServer);
  }

  public void setLogViewerNodeVisibleForServer(String serverId, boolean visible) {
    setBuiltInVisibilityForServer(serverId, visible, ServerBuiltInNodesVisibility::withLogViewer);
  }

  public void setNotificationsNodeVisibleForServer(String serverId, boolean visible) {
    setBuiltInVisibilityForServer(
        serverId, visible, ServerBuiltInNodesVisibility::withNotifications);
  }

  public void setMonitorNodeVisibleForServer(String serverId, boolean visible) {
    setBuiltInVisibilityForServer(serverId, visible, ServerBuiltInNodesVisibility::withMonitor);
  }

  public void setInterceptorsNodeVisibleForServer(String serverId, boolean visible) {
    setBuiltInVisibilityForServer(
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
    setDefaultBuiltInVisibility(
        visible,
        ServerBuiltInNodesVisibility::server,
        ServerBuiltInNodesVisibility::withServer,
        null);
  }

  public void setLogViewerNodesVisible(boolean visible) {
    setDefaultBuiltInVisibility(
        visible,
        ServerBuiltInNodesVisibility::logViewer,
        ServerBuiltInNodesVisibility::withLogViewer,
        PROP_LOG_VIEWER_NODES_VISIBLE);
  }

  public void setNotificationsNodesVisible(boolean visible) {
    setDefaultBuiltInVisibility(
        visible,
        ServerBuiltInNodesVisibility::notifications,
        ServerBuiltInNodesVisibility::withNotifications,
        PROP_NOTIFICATIONS_NODES_VISIBLE);
  }

  public void setMonitorNodesVisible(boolean visible) {
    setDefaultBuiltInVisibility(
        visible,
        ServerBuiltInNodesVisibility::monitor,
        ServerBuiltInNodesVisibility::withMonitor,
        PROP_MONITOR_NODES_VISIBLE);
  }

  public void setInterceptorsNodesVisible(boolean visible) {
    setDefaultBuiltInVisibility(
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
    String sid = Objects.toString(serverId, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (sid.isEmpty() || n.isEmpty()) return;

    TargetRef pm;
    try {
      pm = new TargetRef(sid, n);
    } catch (Exception ignored) {
      return;
    }
    if (!isPrivateMessageTarget(pm)) return;

    privateMessageOnlineStateStore.put(pm, online);
    DefaultMutableTreeNode node = leaves.get(pm);
    if (node != null) {
      model.nodeChanged(node);
    }
  }

  public void clearPrivateMessageOnlineStates(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    for (TargetRef ref : privateMessageOnlineStateStore.clearServer(sid)) {
      DefaultMutableTreeNode node = leaves.get(ref);
      if (node != null) {
        model.nodeChanged(node);
      }
    }
  }

  private void initializeApplicationTreeNodes() {
    applicationRoot.removeAllChildren();
    addApplicationLeaf(applicationUnhandledErrorsRef, APP_UNHANDLED_ERRORS_LABEL);
    addApplicationLeaf(applicationAssertjSwingRef, APP_ASSERTJ_SWING_LABEL);
    addApplicationLeaf(applicationJhiccupRef, APP_JHICCUP_LABEL);
    addApplicationLeaf(applicationInboundDedupRef, APP_INBOUND_DEDUP_LABEL);
    addApplicationLeaf(applicationJfrRef, APP_JFR_LABEL);
    addApplicationLeaf(applicationSpringRef, APP_SPRING_LABEL);
    addApplicationLeaf(applicationTerminalRef, APP_TERMINAL_LABEL);
  }

  private void addApplicationLeaf(TargetRef ref, String label) {
    if (ref == null) return;
    DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new ServerTreeNodeData(ref, label));
    leaves.put(ref, leaf);
    applicationRoot.add(leaf);
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

  private static String applicationLeafLabel(TargetRef ref) {
    if (ref == null) return "";
    if (ref.isApplicationUnhandledErrors()) return APP_UNHANDLED_ERRORS_LABEL;
    if (ref.isApplicationAssertjSwing()) return APP_ASSERTJ_SWING_LABEL;
    if (ref.isApplicationJhiccup()) return APP_JHICCUP_LABEL;
    if (ref.isApplicationInboundDedup()) return APP_INBOUND_DEDUP_LABEL;
    if (ref.isApplicationJfr()) return APP_JFR_LABEL;
    if (ref.isApplicationSpring()) return APP_SPRING_LABEL;
    if (ref.isApplicationTerminal()) return APP_TERMINAL_LABEL;
    return ref.target();
  }

  private boolean isApplicationJfrActive() {
    if (jfrRuntimeEventsService == null) return true;
    return jfrRuntimeEventsService.isEnabled();
  }

  private void refreshApplicationJfrNode() {
    DefaultMutableTreeNode node = leaves.get(applicationJfrRef);
    if (node != null) {
      model.nodeChanged(node);
      return;
    }
    tree.repaint();
  }

  private void syncUiLeafVisibility() {
    uiLeafVisibilitySynchronizer.syncUiLeafVisibility();
  }

  private void syncUiLeafVisibilityForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerNodes sn = servers.get(sid);
    if (sn == null || sn.serverNode == null) return;

    ServerBuiltInNodesVisibility vis = builtInNodesVisibility(sid);
    ensureMovableBuiltInLeafVisible(
        sn, sn.statusRef, statusLabelManager.statusLeafLabelForServer(sid), vis.server());
    ensureMovableBuiltInLeafVisible(sn, sn.notificationsRef, "Notifications", vis.notifications());
    ensureMovableBuiltInLeafVisible(sn, sn.logViewerRef, LOG_VIEWER_LABEL, vis.logViewer());
    ensureUiLeafVisible(sn, sn.channelListRef, CHANNEL_LIST_LABEL, true);
    ensureMovableBuiltInLeafVisible(sn, sn.weechatFiltersRef, WEECHAT_FILTERS_LABEL, true);
    ensureMovableBuiltInLeafVisible(sn, sn.ignoresRef, IGNORES_LABEL, true);
    ensureUiLeafVisible(sn, sn.dccTransfersRef, DCC_TRANSFERS_LABEL, showDccTransfersNodes);
    ensureMonitorGroupVisible(sn, vis.monitor());
    ensureInterceptorsGroupVisible(sn, vis.interceptors());
    applyBuiltInLayoutToTree(sn, builtInLayout(sid));
    applyRootSiblingOrderToTree(sn, rootSiblingOrder(sid));
  }

  private void selectBestFallbackForServer(String serverId) {
    selectionFallbackPolicy.selectBestFallbackForServer(serverId);
  }

  private void selectStartupDefaultForServer(String serverId) {
    selectionFallbackPolicy.selectStartupDefaultForServer(serverId);
  }

  private boolean ensureUiLeafVisible(
      ServerNodes sn, TargetRef ref, String label, boolean visible) {
    if (sn == null || sn.serverNode == null || ref == null) return false;
    return nodeVisibilityMutator.ensureLeafVisible(
        sn.serverNode, ref, label, visible, true, fixedServerLeafInsertIndexFor(sn, ref));
  }

  private boolean ensureMovableBuiltInLeafVisible(
      ServerNodes sn, TargetRef ref, String label, boolean visible) {
    if (sn == null || sn.serverNode == null || ref == null) return false;
    return nodeVisibilityMutator.ensureLeafVisible(sn.serverNode, ref, label, visible, false, 0);
  }

  private boolean ensureInterceptorsGroupVisible(ServerNodes sn, boolean visible) {
    if (sn == null) return false;
    return nodeVisibilityMutator.ensureGroupVisible(
        sn.serverNode, sn.otherNode, sn.interceptorsNode, visible);
  }

  private boolean ensureMonitorGroupVisible(ServerNodes sn, boolean visible) {
    if (sn == null) return false;
    return nodeVisibilityMutator.ensureGroupVisible(
        sn.serverNode, sn.otherNode, sn.monitorNode, visible);
  }

  private int fixedServerLeafInsertIndexFor(ServerNodes sn, TargetRef ref) {
    if (sn == null || sn.serverNode == null || ref == null) return 0;
    if (ref.equals(sn.channelListRef)) {
      return 0;
    }
    if (ref.equals(sn.dccTransfersRef)) {
      DefaultMutableTreeNode channelListNode = leaves.get(sn.channelListRef);
      int idx = 0;
      if (channelListNode != null && channelListNode.getParent() == sn.serverNode) {
        int channelIdx = sn.serverNode.getIndex(channelListNode);
        if (channelIdx >= 0) idx = channelIdx + 1;
      }
      return Math.max(0, Math.min(idx, sn.serverNode.getChildCount()));
    }
    return sn.serverNode.getChildCount();
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
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
    if (node == null) return null;
    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeNodeData nd) return nd.ref;
    return null;
  }

  private boolean hasValidTreeSelection() {
    TreePath sel = tree.getSelectionPath();
    if (sel == null) return false;
    Object last = sel.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return false;
    return node.getPath() != null && node.getRoot() == root;
  }

  public void ensureNode(TargetRef ref) {
    targetLifecycleCoordinator.ensureNode(ref);
  }

  private DefaultMutableTreeNode ensureChannelListNodeForEnsureNode(ServerNodes sn) {
    if (sn == null || sn.serverNode == null || sn.channelListRef == null) return null;
    DefaultMutableTreeNode channelListNode = leaves.get(sn.channelListRef);
    if (channelListNode != null) return channelListNode;

    DefaultMutableTreeNode channelListLeaf =
        new DefaultMutableTreeNode(new ServerTreeNodeData(sn.channelListRef, CHANNEL_LIST_LABEL));
    int channelListIdx = fixedServerLeafInsertIndexFor(sn, sn.channelListRef);
    sn.serverNode.insert(channelListLeaf, channelListIdx);
    leaves.put(sn.channelListRef, channelListLeaf);
    model.nodesWereInserted(sn.serverNode, new int[] {channelListIdx});
    return channelListLeaf;
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
    return readChannelState(ref, true, channelStateCoordinator::isChannelAutoReattach);
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    writeChannelState(ref, () -> channelStateCoordinator.setChannelAutoReattach(ref, autoReattach));
  }

  public boolean isChannelPinned(TargetRef ref) {
    return readChannelState(ref, false, channelStateCoordinator::isChannelPinned);
  }

  public void setChannelPinned(TargetRef ref, boolean pinned) {
    writeChannelState(ref, () -> channelStateCoordinator.setChannelPinned(ref, pinned));
  }

  public boolean isChannelMuted(TargetRef ref) {
    return readChannelState(ref, false, channelStateCoordinator::isChannelMuted);
  }

  public void setChannelMuted(TargetRef ref, boolean muted) {
    writeChannelState(
        ref,
        () -> {
          channelStateCoordinator.setChannelMuted(ref, muted);
          unreadStateCoordinator.onChannelMutedStateChanged(ref, muted);
        });
  }

  private void emitManagedChannelsChanged(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    requestEmitter.emitManagedChannelsChanged(sid);
  }

  private static String foldChannelKey(String channel) {
    return Objects.toString(channel, "").trim().toLowerCase(java.util.Locale.ROOT);
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
    serverRootLifecycleManager.removeServerRoot(serverId);
  }

  private ServerNodes addServerRoot(String serverId) {
    return serverRootLifecycleManager.addServerRoot(serverId);
  }

  private void updateBouncerControlLabels(
      Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl) {
    statusLabelManager.updateBouncerControlLabels(nextSojuBouncerControl, nextZncBouncerControl);
  }

  private void refreshTreeLayoutAfterUiChange() {
    try {
      applyTreeFontFromUiDefaults();
      Set<TreePath> expanded = snapshotExpandedTreePaths();
      tree.setRowHeight(0);
      try {
        treeCellRenderer.updateUI();
        treeCellRenderer.setOpenIcon(UIManager.getIcon("Tree.openIcon"));
        treeCellRenderer.setClosedIcon(UIManager.getIcon("Tree.closedIcon"));
        treeCellRenderer.setLeafIcon(UIManager.getIcon("Tree.leafIcon"));
      } catch (Exception ignored) {
      }
      tree.setCellRenderer(treeCellRenderer);
      ToolTipManager.sharedInstance().registerComponent(tree);
      model.reload(root);
      restoreExpandedTreePaths(expanded);

      tree.revalidate();
      tree.repaint();
    } catch (Exception ignored) {
    }
  }

  private void applyTreeFontFromUiDefaults() {
    Font next = UIManager.getFont("Tree.font");
    if (next == null) next = UIManager.getFont("defaultFont");
    if (next == null) return;
    Font cur = tree.getFont();
    if (!next.equals(cur)) {
      tree.setFont(next);
    }
  }

  @PreDestroy
  public void shutdown() {
    try {
      settingsSynchronizer.shutdown();
      if (typingActivityTimer != null) typingActivityTimer.stop();
      if (treeWheelSelectionDecorator != null) treeWheelSelectionDecorator.close();
      nodeActions.close();
    } catch (Exception ignored) {
    }
    disposables.dispose();
  }
}
