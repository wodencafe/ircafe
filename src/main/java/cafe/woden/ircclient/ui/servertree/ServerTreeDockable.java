package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.SwingEdt;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaborators;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeCellRendererContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeContextMenuBuilderContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeSelectionFallbackContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerCatalogSynchronizerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeServerRootLifecycleContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeSettingsSynchronizerContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTargetLifecycleContextAdapter;
import cafe.woden.ircclient.ui.servertree.context.ServerTreeTooltipProviderContextAdapter;
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
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionMediator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeInteractionMediatorContextAdapter;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeKeyBindingsInstaller;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeMiddleDragReorderContextAdapter;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeMiddleDragReorderHandler;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutOrchestrator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutPersistenceCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeRootSiblingOrderCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeNodeVisibilityMutator;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeNodeReorderPolicy;
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
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeExpansionStateManager;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeSettingsSynchronizer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeCellRenderer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeDetachedWarningClickHandler;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
    CUSTOM
  }

  public record ManagedChannelEntry(
      String channel, boolean detached, boolean autoReattach, int notifications) {}

  private final CompositeDisposable disposables = new CompositeDisposable();
  public static final String ID = "server-tree";

  private AutoCloseable treeWheelSelectionDecorator;

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

  private volatile InsertionLine insertionLine;

  private static final class InsertionLine {
    final int x1;
    final int x2;
    final int y;

    InsertionLine(int x1, int y, int x2) {
      this.x1 = x1;
      this.x2 = x2;
      this.y = y;
    }

    Rectangle repaintRect() {
      int left = Math.min(x1, x2);
      int right = Math.max(x1, x2);
      int w = Math.max(1, right - left);
      return new Rectangle(left, Math.max(0, y - 3), w, 6);
    }
  }

  private final JTree tree =
      new JTree(model) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          ServerTreeDockable.this.paintInsertionLine(g);
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
          return ServerTreeDockable.this.toolTipForEvent(event);
        }
      };
  private final JScrollPane treeScroll = new JScrollPane(tree);

  private final ServerTreeCellRenderer treeCellRenderer;
  private final ServerTreeServerActionOverlay serverActionOverlay;
  private final ServerTreeDetachedWarningClickHandler detachedWarningClickHandler;

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
  private final Map<String, ChannelSortMode> channelSortModeByServer = new HashMap<>();
  private final Map<String, ArrayList<String>> channelCustomOrderByServer = new HashMap<>();
  private final Map<String, Map<String, Boolean>> channelAutoReattachByServer = new HashMap<>();
  private final Map<String, Map<String, Long>> channelActivityRankByServer = new HashMap<>();

  private final ServerTreeRuntimeState runtimeState;
  private final Timer typingActivityTimer;
  private final Set<DefaultMutableTreeNode> typingActivityNodes = new HashSet<>();
  private final ServerTreeTypingActivityManager typingActivityManager;

  private static final int CAPABILITY_TRANSITION_LOG_LIMIT = 200;

  private final ServerCatalog serverCatalog;
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

  private final ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator;
  private final ServerTreeTargetNodePolicy targetNodePolicy;
  private final ServerTreeChannelStateCoordinator channelStateCoordinator;
  private final ServerTreeEnsureNodeParentResolver ensureNodeParentResolver;
  private final ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter;
  private final ServerTreeChannelDisconnectStateManager channelDisconnectStateManager;
  private final ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator;
  private final ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator;
  private final ServerTreeTargetLifecycleCoordinator targetLifecycleCoordinator;
  private final ServerTreeTargetSelectionCoordinator targetSelectionCoordinator;
  private final ServerTreeInteractionMediator interactionMediator;
  private final ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater;
  private final ServerTreeTooltipProvider tooltipProvider;
  private final ServerTreeContextMenuBuilder contextMenuBuilder;
  private final UiSettingsBus settingsBus;
  private final ServerTreeSettingsSynchronizer settingsSynchronizer;
  private volatile ServerTreeTypingIndicatorStyle typingIndicatorStyle =
      ServerTreeTypingIndicatorStyle.DOTS;
  private volatile boolean typingIndicatorsTreeEnabled = true;
  private volatile int unreadBadgeScalePercent = TREE_BADGE_SCALE_PERCENT_DEFAULT;
  private volatile boolean serverTreeNotificationBadgesEnabled = true;
  private volatile boolean showChannelListNodes = true;
  private volatile boolean showDccTransfersNodes = false;
  private final ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator;
  private final ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator;
  private final ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator;
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

    this.serverCatalog = serverCatalog;
    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;

    this.interceptorStore = interceptorStore;
    this.jfrRuntimeEventsService = jfrRuntimeEventsService;
    this.settingsBus = settingsBus;
    this.runtimeState =
        new ServerTreeRuntimeState(
            CAPABILITY_TRANSITION_LOG_LIMIT, this::clearPrivateMessageOnlineStates);
    this.nodeClassifier =
        new ServerTreeNodeClassifier(
            "Private Messages",
            INTERCEPTORS_GROUP_LABEL,
            MONITOR_GROUP_LABEL,
            OTHER_GROUP_LABEL,
            this::isServerNode);
    ServerTreeLayoutCollaborators layoutCollaborators =
        ServerTreeLayoutCollaboratorsFactory.create(
            runtimeConfig,
            createBuiltInVisibilityContext(),
            createLayoutPersistenceContext(),
            createBuiltInLayoutOrchestratorContext());
    this.builtInVisibilityCoordinator = layoutCollaborators.builtInVisibilityCoordinator();

    this.builtInLayoutOrchestrator = layoutCollaborators.builtInLayoutOrchestrator();
    this.builtInLayoutCoordinator = layoutCollaborators.builtInLayoutCoordinator();
    this.rootSiblingOrderCoordinator = layoutCollaborators.rootSiblingOrderCoordinator();

    this.networkInfoDialogBuilder =
        new ServerTreeNetworkInfoDialogBuilder(runtimeConfig, createNetworkInfoDialogContext());
    this.interceptorActions =
        new ServerTreeInterceptorActions(
            this, interceptorStore, INTERCEPTORS_GROUP_LABEL, createInterceptorActionsContext());
    this.serverCatalogSynchronizer =
        new ServerTreeServerCatalogSynchronizer(
            serverDisplayNames,
            ephemeralServerIds,
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            sojuOriginByServerId,
            zncOriginByServerId,
            createServerCatalogSynchronizerContext());
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
            createNetworkGroupManagerContext());
    this.serverActionOverlay =
        new ServerTreeServerActionOverlay(
            tree,
            SERVER_ACTION_BUTTON_SIZE,
            SERVER_ACTION_BUTTON_ICON_SIZE,
            SERVER_ACTION_BUTTON_MARGIN,
            createServerActionOverlayContext());
    this.serverRuntimeUiUpdater =
        new ServerTreeServerRuntimeUiUpdater(
            runtimeState, servers, model, serverActionOverlay, tree);
    this.serverStateCleaner =
        new ServerTreeServerStateCleaner(
            interceptorStore,
            serverActionOverlay,
            runtimeState,
            channelSortModeByServer,
            channelCustomOrderByServer,
            channelAutoReattachByServer,
            channelActivityRankByServer,
            leaves,
            typingActivityNodes,
            this::clearPrivateMessageOnlineStates);
    this.serverNodeBuilder = new ServerTreeServerNodeBuilder();
    this.serverParentResolver =
        new ServerTreeServerParentResolver(
            sojuOriginByServerId, zncOriginByServerId, createServerParentResolverContext());
    this.serverLabelPolicy =
        new ServerTreeServerLabelPolicy(
            serverDisplayNames,
            ephemeralServerIds,
            sojuOriginByServerId,
            zncOriginByServerId,
            sojuAutoConnect,
            zncAutoConnect);
    this.bouncerDetachPolicy =
        new ServerTreeBouncerDetachPolicy(
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            createBouncerDetachPolicyContext());
    this.nodeBadgeUpdater =
        new ServerTreeNodeBadgeUpdater(
            notificationStore, ephemeralServerIds, leaves, createNodeBadgeUpdaterContext());
    this.selectionFallbackPolicy =
        new ServerTreeSelectionFallbackPolicy(createSelectionFallbackPolicyContext());
    this.uiLeafVisibilitySynchronizer =
        new ServerTreeUiLeafVisibilitySynchronizer(createUiLeafVisibilitySynchronizerContext());
    this.nodeVisibilityMutator =
        new ServerTreeNodeVisibilityMutator(model, leaves, typingActivityNodes);
    this.expansionStateManager =
        new ServerTreeExpansionStateManager(tree, root, ircRoot, applicationRoot);
    this.applicationRootVisibilityCoordinator =
        new ServerTreeApplicationRootVisibilityCoordinator(
            createApplicationRootVisibilityContext());
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
    this.channelStateCoordinator =
        new ServerTreeChannelStateCoordinator(
            runtimeConfig,
            channelSortModeByServer,
            channelCustomOrderByServer,
            channelAutoReattachByServer,
            channelActivityRankByServer,
            model,
            createChannelStateCoordinatorContext());
    this.ensureNodeParentResolver = new ServerTreeEnsureNodeParentResolver();
    this.ensureNodeLeafInserter =
        new ServerTreeEnsureNodeLeafInserter(
            leaves, model, privateMessageOnlineStateStore, this::isPrivateMessageTarget);
    this.targetNodeRemovalMutator =
        new ServerTreeTargetNodeRemovalMutator(typingActivityNodes, model);
    this.targetRemovalStateCoordinator =
        new ServerTreeTargetRemovalStateCoordinator(
            privateMessageOnlineStateStore,
            runtimeConfig,
            channelAutoReattachByServer,
            channelActivityRankByServer,
            channelCustomOrderByServer,
            createTargetRemovalStateCoordinatorContext());
    this.detachedWarningClickHandler =
        new ServerTreeDetachedWarningClickHandler(tree, this::clearChannelDisconnectedWarning);
    this.tooltipProvider =
        new ServerTreeTooltipProvider(
            tree, createTooltipProviderContext(sojuAutoConnect, zncAutoConnect));
    this.contextMenuBuilder =
        new ServerTreeContextMenuBuilder(
            createContextMenuBuilderContext(serverDialogs, sojuAutoConnect, zncAutoConnect));
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
            createServerRootLifecycleContext(notificationStore));
    this.settingsSynchronizer =
        new ServerTreeSettingsSynchronizer(
            createSettingsSynchronizerContext(), TREE_BADGE_SCALE_PERCENT_DEFAULT);
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
            createTargetLifecycleContext());
    loadPersistedBuiltInNodesVisibility();
    settingsSynchronizer.applyInitialSettings();
    this.treeCellRenderer = createTreeCellRenderer();

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
            createTypingActivityManagerContext());
    this.channelDisconnectStateManager =
        new ServerTreeChannelDisconnectStateManager(
            typingActivityNodes, typingActivityTimer, createChannelDisconnectStateManagerContext());
    this.targetSelectionCoordinator =
        new ServerTreeTargetSelectionCoordinator(createTargetSelectionCoordinatorContext());
    ToolTipManager.sharedInstance().registerComponent(tree);
    tree.addPropertyChangeListener(
        "UI", e -> SwingUtilities.invokeLater(this::refreshTreeLayoutAfterUiChange));
    this.nodeActions =
        new TreeNodeActions<>(
            tree,
            model,
            new ServerTreeNodeReorderPolicy(
                this::isServerNode,
                this::isChannelListLeafNode,
                this::targetRefForNode,
                this::nodeLabelForNode),
            n -> {
              Object uo = n.getUserObject();
              if (uo instanceof ServerTreeNodeData nd) return nd.ref;
              return null;
            },
            ref -> {
              if (ref == null) return;
              if (ref.isChannel()) {
                if (!isChannelDisconnected(ref)) {
                  requestEmitter.emitDisconnectChannel(ref);
                }
                return;
              }
              requestEmitter.emitCloseTarget(ref);
            },
            movedNode -> {
              if (movedNode == null) return;
              Object uo = movedNode.getUserObject();
              DefaultMutableTreeNode parent = (DefaultMutableTreeNode) movedNode.getParent();
              if (uo instanceof ServerTreeNodeData nd && nd.ref != null && nd.ref.isChannel()) {
                if (!isChannelListLeafNode(parent)) return;
                String sid = owningServerIdForNode(parent);
                channelStateCoordinator.persistCustomOrderFromTreeIfCustom(sid);
                return;
              }
              if (isRootSiblingReorderableNode(movedNode)) {
                String sid = owningServerIdForNode(movedNode);
                if (sid.isBlank()) return;
                persistRootSiblingOrderFromTree(sid);
                return;
              }
              if (!isMovableBuiltInNode(movedNode)) return;
              String sid = owningServerIdForNode(movedNode);
              if (sid.isBlank()) return;
              persistBuiltInLayoutFromTree(sid);
            });
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
    if (serverCatalog != null) {
      syncServers(serverCatalog.entries());

      disposables.add(
          serverCatalog
              .updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  this::syncServers,
                  err -> log.error("[ircafe] server catalog stream error", err)));
    }
    if (notificationStore != null) {
      disposables.add(
          notificationStore
              .changes()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  ch -> refreshNotificationsCount(ch.serverId()),
                  err -> log.error("[ircafe] notification store stream error", err)));
    }
    if (interceptorStore != null) {
      disposables.add(
          interceptorStore
              .changes()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  ch -> {
                    refreshInterceptorNodeLabel(ch.serverId(), ch.interceptorId());
                    refreshInterceptorGroupCount(ch.serverId());
                  },
                  err -> log.error("[ircafe] interceptor store stream error", err)));
    }

    if (sojuAutoConnect != null) {
      disposables.add(
          sojuAutoConnect
              .updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  __ -> refreshSojuAutoConnectBadges(),
                  err -> log.error("[ircafe] soju auto-connect store stream error", err)));
    }

    if (zncAutoConnect != null) {
      disposables.add(
          zncAutoConnect
              .updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  __ -> refreshZncAutoConnectBadges(),
                  err -> log.error("[ircafe] znc auto-connect store stream error", err)));
    }

    settingsSynchronizer.bindListeners();

    this.interactionMediator =
        new ServerTreeInteractionMediator(
            tree, serverActionOverlay, createInteractionMediatorContext());
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

  private void loadPersistedBuiltInNodesVisibility() {
    builtInVisibilityCoordinator.loadPersistedBuiltInNodesVisibility();
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    return builtInVisibilityCoordinator.builtInNodesVisibility(serverId);
  }

  private RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout(String serverId) {
    return builtInLayoutCoordinator.layoutForServer(serverId);
  }

  private void persistBuiltInLayoutForServer(
      String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    builtInLayoutCoordinator.rememberLayout(serverId, layout);
  }

  private RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder(String serverId) {
    return rootSiblingOrderCoordinator.orderForServer(serverId);
  }

  private void persistRootSiblingOrderForServer(
      String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
    rootSiblingOrderCoordinator.rememberOrder(serverId, order);
  }

  private void applyBuiltInNodesVisibilityGlobally(
      java.util.function.UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
    builtInVisibilityCoordinator.applyBuiltInNodesVisibilityGlobally(mutator);
  }

  private void applyBuiltInNodesVisibilityForServer(
      String serverId, ServerBuiltInNodesVisibility next, boolean persist, boolean syncUi) {
    builtInVisibilityCoordinator.applyBuiltInNodesVisibilityForServer(
        serverId, next, persist, syncUi);
  }

  private ConnectionState connectionStateForServer(String serverId) {
    return runtimeState.connectionStateForServer(serverId);
  }

  private boolean desiredOnlineForServer(String serverId) {
    return runtimeState.desiredOnlineForServer(serverId);
  }

  private String serverNodeDisplayLabel(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return sid;
    String base = prettyServerLabel(sid);
    ConnectionState state = connectionStateForServer(sid);
    boolean desired = desiredOnlineForServer(sid);
    String badge = ServerTreeConnectionStateViewModel.desiredBadge(state, desired);
    return badge.isEmpty() ? base : (base + badge);
  }

  private String connectionDiagnosticsTipForServer(String serverId) {
    return runtimeState.connectionDiagnosticsTipForServer(serverId);
  }

  private TreePath serverPathForId(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;
    ServerNodes sn = servers.get(sid);
    if (sn == null || sn.serverNode == null || sn.serverNode.getPath() == null) return null;
    return new TreePath(sn.serverNode.getPath());
  }

  private String serverIdAt(int x, int y) {
    TreePath path = tree.getPathForLocation(x, y);
    if (path == null) {
      TreePath closest = tree.getClosestPathForLocation(x, y);
      if (closest != null) {
        Rectangle row = tree.getPathBounds(closest);
        if (row != null && y >= row.y && y < (row.y + row.height)) {
          path = closest;
        }
      }
    }
    if (path == null) return "";
    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node) || !isServerNode(node)) return "";
    return Objects.toString(node.getUserObject(), "").trim();
  }

  private boolean maybeHandleDisconnectedWarningClick(MouseEvent event) {
    return detachedWarningClickHandler.maybeHandleClick(
        event, ServerTreeCellRenderer.typingSlotWidthForStyle(typingIndicatorStyle));
  }

  private Rectangle disconnectedWarningIndicatorBounds(TreePath path, DefaultMutableTreeNode node) {
    return detachedWarningClickHandler.disconnectedWarningIndicatorBounds(
        path, node, ServerTreeCellRenderer.typingSlotWidthForStyle(typingIndicatorStyle));
  }

  private boolean maybeSelectRowFromLeftClick(MouseEvent event) {
    if (event == null || event.isConsumed()) return false;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;

    TreePath hit = treePathForRowHit(event.getX(), event.getY());
    if (hit == null) return false;

    TreePath current = tree.getSelectionPath();
    if (!Objects.equals(current, hit)) {
      tree.setSelectionPath(hit);
    }
    return true;
  }

  private TreePath treePathForRowHit(int x, int y) {
    // Resolve by row (Y-position) so click selection works anywhere across the row,
    // not only directly over the node text/icon bounds.
    int row = tree.getClosestRowForLocation(x, y);
    if (row < 0) return null;
    Rectangle rb = tree.getRowBounds(row);
    if (rb == null) return null;
    if (y < rb.y || y >= (rb.y + rb.height)) return null;
    return tree.getPathForRow(row);
  }

  private JPopupMenu buildPopupMenu(TreePath path) {
    return contextMenuBuilder.build(path);
  }

  private void confirmAndRequestClearLog(TargetRef target, String label) {
    if (target == null) return;
    if (!(target.isChannel() || target.isStatus())) return;

    Window w = SwingUtilities.getWindowAncestor(this);
    String pretty = (label == null || label.isBlank()) ? target.target() : label;
    String scope = target.isStatus() ? "status" : "channel";

    String msg =
        "Clear log for "
            + scope
            + " \""
            + pretty
            + "\"?\n\n"
            + "This will permanently delete the persisted chat history for this target.";

    int choice =
        JOptionPane.showConfirmDialog(
            w, msg, "Clear Log", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

    if (choice == JOptionPane.YES_OPTION) {
      requestEmitter.emitClearLog(target);
    }
  }

  private void promptAndAddInterceptor(String serverId) {
    interceptorActions.promptAndAddInterceptor(serverId);
  }

  private void promptRenameInterceptor(TargetRef ref, String currentLabel) {
    interceptorActions.promptRenameInterceptor(ref, currentLabel);
  }

  private void setInterceptorEnabled(TargetRef ref, boolean enabled) {
    interceptorActions.setInterceptorEnabled(ref, enabled);
  }

  private void confirmDeleteInterceptor(TargetRef ref, String label) {
    interceptorActions.confirmDeleteInterceptor(ref, label);
  }

  private void refreshInterceptorNodeLabel(String serverId, String interceptorId) {
    interceptorActions.refreshInterceptorNodeLabel(serverId, interceptorId);
  }

  private void refreshInterceptorGroupCount(String serverId) {
    interceptorActions.refreshInterceptorGroupCount(serverId);
  }

  private DefaultMutableTreeNode interceptorsGroupNodeForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;
    ServerNodes nodes = servers.get(sid);
    if (nodes == null) return null;
    return nodes.interceptorsNode;
  }

  private boolean isServerNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String id)) return false;
    ServerNodes sn = servers.get(id);
    return sn != null && sn.serverNode == node;
  }

  private boolean isRootServerNode(DefaultMutableTreeNode node) {
    return node != null && node.getParent() == ircRoot && isServerNode(node);
  }

  private boolean isIrcRootNode(DefaultMutableTreeNode node) {
    return node != null && node == ircRoot;
  }

  private boolean isApplicationRootNode(DefaultMutableTreeNode node) {
    return node != null && node == applicationRoot;
  }

  private ServerTreeInterceptorActions.Context createInterceptorActionsContext() {
    return new ServerTreeInterceptorActions.Context() {
      @Override
      public void ensureNode(TargetRef ref) {
        ServerTreeDockable.this.ensureNode(ref);
      }

      @Override
      public void selectTarget(TargetRef ref) {
        ServerTreeDockable.this.selectTarget(ref);
      }

      @Override
      public void removeTarget(TargetRef ref) {
        ServerTreeDockable.this.removeTarget(ref);
      }

      @Override
      public DefaultMutableTreeNode leafNode(TargetRef ref) {
        return leaves.get(ref);
      }

      @Override
      public DefaultMutableTreeNode interceptorsGroupNode(String serverId) {
        return ServerTreeDockable.this.interceptorsGroupNodeForServer(serverId);
      }

      @Override
      public void nodeChanged(DefaultMutableTreeNode node) {
        model.nodeChanged(node);
      }
    };
  }

  private ServerTreeNetworkGroupManager.Context createNetworkGroupManagerContext() {
    return new ServerTreeNetworkGroupManager.Context() {
      @Override
      public DefaultMutableTreeNode serverNode(String serverId) {
        ServerNodes nodes = servers.get(Objects.toString(serverId, "").trim());
        return nodes == null ? null : nodes.serverNode;
      }

      @Override
      public DefaultMutableTreeNode privateMessagesNode(String serverId) {
        ServerNodes nodes = servers.get(Objects.toString(serverId, "").trim());
        return nodes == null ? null : nodes.pmNode;
      }
    };
  }

  private ServerTreeBouncerDetachPolicy.Context createBouncerDetachPolicyContext() {
    return new ServerTreeBouncerDetachPolicy.Context() {
      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return ServerTreeDockable.this.connectionStateForServer(serverId);
      }

      @Override
      public boolean isSojuEphemeralServer(String serverId) {
        return ServerTreeDockable.this.isSojuEphemeralServer(serverId);
      }

      @Override
      public boolean isZncEphemeralServer(String serverId) {
        return ServerTreeDockable.this.isZncEphemeralServer(serverId);
      }

      @Override
      public boolean hasBouncerCapability(String serverId, String capability) {
        String sid = normalizeServerId(serverId);
        if (sid.isBlank()) return false;
        ServerRuntimeMetadata metadata = runtimeState.metadataForServerIfPresent(sid);
        if (metadata == null) return false;
        String cap = Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (cap.isEmpty()) return false;
        ServerRuntimeMetadata.CapabilityState state = metadata.ircv3Caps.get(cap);
        return state == ServerRuntimeMetadata.CapabilityState.ENABLED
            || state == ServerRuntimeMetadata.CapabilityState.AVAILABLE
            || state == ServerRuntimeMetadata.CapabilityState.DISABLED;
      }
    };
  }

  private ServerTreeNodeBadgeUpdater.Context createNodeBadgeUpdaterContext() {
    return new ServerTreeNodeBadgeUpdater.Context() {
      @Override
      public void nodeChanged(DefaultMutableTreeNode node) {
        model.nodeChanged(node);
      }

      @Override
      public void nodeChangedForServer(String serverId) {
        ServerNodes nodes = servers.get(Objects.toString(serverId, "").trim());
        if (nodes != null) model.nodeChanged(nodes.serverNode);
      }
    };
  }

  private ServerTreeServerActionOverlay.Context createServerActionOverlayContext() {
    return new ServerTreeServerActionOverlay.Context() {
      @Override
      public boolean isServerNode(DefaultMutableTreeNode node) {
        return ServerTreeDockable.this.isServerNode(node);
      }

      @Override
      public TreePath serverPathForId(String serverId) {
        return ServerTreeDockable.this.serverPathForId(serverId);
      }

      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return ServerTreeDockable.this.connectionStateForServer(serverId);
      }

      @Override
      public void connectServer(String serverId) {
        requestEmitter.emitConnectServer(serverId);
      }

      @Override
      public void disconnectServer(String serverId) {
        requestEmitter.emitDisconnectServer(serverId);
      }
    };
  }

  private ServerTreeServerParentResolver.Context createServerParentResolverContext() {
    return new ServerTreeServerParentResolver.Context() {
      @Override
      public boolean hasServer(String serverId) {
        return servers.containsKey(Objects.toString(serverId, "").trim());
      }

      @Override
      public void ensureServerRoot(String serverId) {
        ServerTreeDockable.this.addServerRoot(serverId);
      }

      @Override
      public DefaultMutableTreeNode ircRoot() {
        return ircRoot;
      }

      @Override
      public DefaultMutableTreeNode sojuGroupNode(String originServerId) {
        return getOrCreateSojuNetworksGroupNode(originServerId);
      }

      @Override
      public DefaultMutableTreeNode zncGroupNode(String originServerId) {
        return getOrCreateZncNetworksGroupNode(originServerId);
      }
    };
  }

  private ServerTreeApplicationRootVisibilityCoordinator.Context
      createApplicationRootVisibilityContext() {
    return new ServerTreeApplicationRootVisibilityCoordinator.Context() {
      @Override
      public Set<TreePath> snapshotExpandedTreePaths() {
        return ServerTreeDockable.this.snapshotExpandedTreePaths();
      }

      @Override
      public void restoreExpandedTreePaths(Set<TreePath> expanded) {
        ServerTreeDockable.this.restoreExpandedTreePaths(expanded);
      }

      @Override
      public boolean showApplicationRoot() {
        return showApplicationRoot;
      }

      @Override
      public boolean isApplicationRootAttached() {
        return applicationRoot.getParent() == root;
      }

      @Override
      public int rootChildCount() {
        return root.getChildCount();
      }

      @Override
      public void attachApplicationRoot(int index) {
        root.insert(applicationRoot, Math.max(0, Math.min(index, root.getChildCount())));
      }

      @Override
      public void detachApplicationRoot() {
        root.remove(applicationRoot);
      }

      @Override
      public void rootStructureChanged() {
        model.nodeStructureChanged(root);
      }

      @Override
      public void expandApplicationRootPath() {
        tree.expandPath(new TreePath(applicationRoot.getPath()));
      }

      @Override
      public TargetRef selectedTargetRef() {
        return ServerTreeDockable.this.selectedTargetRef();
      }

      @Override
      public TargetRef firstServerStatusRef() {
        return servers.values().stream().findFirst().map(sn -> sn.statusRef).orElse(null);
      }

      @Override
      public void selectTarget(TargetRef ref) {
        if (ref != null) ServerTreeDockable.this.selectTarget(ref);
      }

      @Override
      public void selectDefaultPath() {
        tree.setSelectionPath(defaultSelectionPath());
      }
    };
  }

  private ServerTreeLayoutPersistenceCoordinator.Context createLayoutPersistenceContext() {
    return new ServerTreeLayoutPersistenceCoordinator.Context() {
      @Override
      public RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
          DefaultMutableTreeNode node) {
        return ServerTreeDockable.this.rootSiblingNodeKindForNode(node);
      }

      @Override
      public RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
          DefaultMutableTreeNode node) {
        return ServerTreeDockable.this.builtInLayoutNodeKindForNode(node);
      }

      @Override
      public RuntimeConfigStore.ServerTreeRootSiblingOrder currentRootSiblingOrder(
          String serverId) {
        return rootSiblingOrder(serverId);
      }

      @Override
      public RuntimeConfigStore.ServerTreeBuiltInLayout currentBuiltInLayout(String serverId) {
        return builtInLayout(serverId);
      }

      @Override
      public void persistRootSiblingOrder(
          String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
        persistRootSiblingOrderForServer(serverId, order);
      }

      @Override
      public void persistBuiltInLayout(
          String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
        persistBuiltInLayoutForServer(serverId, layout);
      }
    };
  }

  private ServerTreeChannelStateCoordinator.Context createChannelStateCoordinatorContext() {
    return new ServerTreeChannelStateCoordinator.Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return ServerTreeDockable.normalizeServerId(serverId);
      }

      @Override
      public DefaultMutableTreeNode channelListNode(String serverId) {
        String sid = normalizeServerId(serverId);
        if (sid.isEmpty()) return null;
        ServerNodes nodes = servers.get(sid);
        if (nodes == null || nodes.channelListRef == null) return null;
        return leaves.get(nodes.channelListRef);
      }

      @Override
      public Set<TreePath> snapshotExpandedTreePaths() {
        return ServerTreeDockable.this.snapshotExpandedTreePaths();
      }

      @Override
      public void restoreExpandedTreePaths(Set<TreePath> expanded) {
        ServerTreeDockable.this.restoreExpandedTreePaths(expanded);
      }

      @Override
      public void emitManagedChannelsChanged(String serverId) {
        ServerTreeDockable.this.emitManagedChannelsChanged(serverId);
      }
    };
  }

  private ServerTreeTargetRemovalStateCoordinator.Context
      createTargetRemovalStateCoordinatorContext() {
    return new ServerTreeTargetRemovalStateCoordinator.Context() {
      @Override
      public boolean isPrivateMessageTarget(TargetRef ref) {
        return ServerTreeDockable.this.isPrivateMessageTarget(ref);
      }

      @Override
      public boolean shouldPersistPrivateMessageList() {
        return ServerTreeDockable.this.shouldPersistPrivateMessageList();
      }

      @Override
      public String foldChannelKey(String channelName) {
        return ServerTreeDockable.this.foldChannelKey(channelName);
      }

      @Override
      public void emitManagedChannelsChanged(String serverId) {
        ServerTreeDockable.this.emitManagedChannelsChanged(serverId);
      }
    };
  }

  private ServerTreeTargetLifecycleCoordinator.Context createTargetLifecycleContext() {
    return new ServerTreeTargetLifecycleContextAdapter(
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
        this::ensureChannelKnownInConfig,
        this::sortChannelsUnderChannelList,
        this::emitManagedChannelsChanged,
        ServerTreeDockable::normalizeServerId,
        parentNode -> tree.expandPath(new TreePath(parentNode.getPath())),
        () -> model.reload(root));
  }

  private ServerTreeTargetSelectionCoordinator.Context createTargetSelectionCoordinatorContext() {
    return new ServerTreeTargetSelectionCoordinator.Context() {
      @Override
      public void ensureNode(TargetRef ref) {
        ServerTreeDockable.this.ensureNode(ref);
      }

      @Override
      public DefaultMutableTreeNode monitorGroupNode(String serverId) {
        ServerNodes nodes = servers.get(normalizeServerId(serverId));
        return nodes == null ? null : nodes.monitorNode;
      }

      @Override
      public DefaultMutableTreeNode interceptorsGroupNode(String serverId) {
        ServerNodes nodes = servers.get(normalizeServerId(serverId));
        return nodes == null ? null : nodes.interceptorsNode;
      }

      @Override
      public boolean isGroupNodeSelectable(String serverId, DefaultMutableTreeNode node) {
        ServerNodes nodes = servers.get(normalizeServerId(serverId));
        if (nodes == null || node == null) return false;
        return node.getParent() == nodes.serverNode || node.getParent() == nodes.otherNode;
      }

      @Override
      public DefaultMutableTreeNode leafNode(TargetRef ref) {
        return leaves.get(ref);
      }

      @Override
      public void selectNode(DefaultMutableTreeNode node) {
        if (node == null) return;
        TreePath path = new TreePath(node.getPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
      }
    };
  }

  private ServerTreeTypingActivityManager.Context createTypingActivityManagerContext() {
    return new ServerTreeTypingActivityManager.Context() {
      @Override
      public boolean supportsTypingActivity(TargetRef ref) {
        return ServerTreeTypingTargetPolicy.supportsTypingActivity(ref);
      }

      @Override
      public boolean typingIndicatorsEnabled() {
        return typingIndicatorsTreeEnabled;
      }

      @Override
      public boolean uiShowing() {
        return ServerTreeDockable.this.isShowing() && tree.isShowing();
      }

      @Override
      public void repaintTreeNode(DefaultMutableTreeNode node) {
        ServerTreeDockable.this.repaintTreeNode(node);
      }
    };
  }

  private ServerTreeChannelDisconnectStateManager.Context
      createChannelDisconnectStateManagerContext() {
    return new ServerTreeChannelDisconnectStateManager.Context() {
      @Override
      public void ensureNode(TargetRef ref) {
        ServerTreeDockable.this.ensureNode(ref);
      }

      @Override
      public DefaultMutableTreeNode leafNode(TargetRef ref) {
        return leaves.get(ref);
      }

      @Override
      public void nodeChanged(DefaultMutableTreeNode node) {
        model.nodeChanged(node);
      }

      @Override
      public void emitManagedChannelsChanged(String serverId) {
        ServerTreeDockable.this.emitManagedChannelsChanged(serverId);
      }
    };
  }

  private ServerTreeTooltipProvider.Context createTooltipProviderContext(
      SojuAutoConnectStore sojuAutoConnect, ZncAutoConnectStore zncAutoConnect) {
    return new ServerTreeTooltipProviderContextAdapter(
        this::serverIdAt,
        this::serverPathForId,
        this::isIrcRootNode,
        this::isApplicationRootNode,
        this::isSojuNetworksGroupNode,
        this::isZncNetworksGroupNode,
        this::isInterceptorsGroupNode,
        this::isMonitorGroupNode,
        this::isOtherGroupNode,
        this::isServerNode,
        this::connectionStateForServer,
        this::desiredOnlineForServer,
        this::connectionDiagnosticsTipForServer,
        this::isSojuEphemeralServer,
        this::isZncEphemeralServer,
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
        });
  }

  private ServerTreeServerCatalogSynchronizer.Context createServerCatalogSynchronizerContext() {
    return new ServerTreeServerCatalogSynchronizerContextAdapter(
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
        () -> servers.values().stream().findFirst().map(sn -> sn.statusRef.serverId()).orElse(""),
        this::selectStartupDefaultForServer,
        this::defaultSelectionPath);
  }

  private ServerTreeContextMenuBuilder.Context createContextMenuBuilderContext(
      ServerDialogs serverDialogs,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect) {
    return new ServerTreeContextMenuBuilderContextAdapter(
        this::isServerNode,
        this::isRootServerNode,
        this::prettyServerLabel,
        this::connectionStateForServer,
        serverId ->
            serverCatalog != null ? serverCatalog.findEntry(serverId) : java.util.Optional.empty(),
        this::moveNodeUpAction,
        this::moveNodeDownAction,
        requestEmitter::emitConnectServer,
        requestEmitter::emitDisconnectServer,
        this::openServerInfoDialog,
        () -> interceptorStore != null,
        this::promptAndAddInterceptor,
        () -> serverDialogs != null,
        serverId -> {
          if (serverDialogs == null) return;
          Window window = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
          serverDialogs.openSaveEphemeralServer(window, serverId);
        },
        serverId -> {
          if (serverDialogs == null) return;
          Window window = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
          serverDialogs.openEditServer(window, serverId);
        },
        () -> runtimeConfig != null,
        (serverId, defaultValue) ->
            runtimeConfig == null
                ? defaultValue
                : runtimeConfig.readServerAutoConnectOnStart(serverId, defaultValue),
        (serverId, enabled) -> {
          if (runtimeConfig == null) return;
          runtimeConfig.rememberServerAutoConnectOnStart(serverId, enabled);
        },
        this::isSojuEphemeralServer,
        this::isZncEphemeralServer,
        sojuOriginByServerId::get,
        zncOriginByServerId::get,
        serverId -> serverDisplayNames.getOrDefault(serverId, serverId),
        (originId, networkKey) ->
            sojuAutoConnect != null && sojuAutoConnect.isEnabled(originId, networkKey),
        (originId, networkKey) ->
            zncAutoConnect != null && zncAutoConnect.isEnabled(originId, networkKey),
        (originId, networkKey, enabled) -> {
          if (sojuAutoConnect == null) return;
          sojuAutoConnect.setEnabled(originId, networkKey, enabled);
        },
        (originId, networkKey, enabled) -> {
          if (zncAutoConnect == null) return;
          zncAutoConnect.setEnabled(originId, networkKey, enabled);
        },
        this::refreshSojuAutoConnectBadges,
        this::refreshZncAutoConnectBadges,
        this::isInterceptorsGroupNode,
        this::owningServerIdForNode,
        requestEmitter::emitOpenPinnedChat,
        this::confirmAndRequestClearLog,
        this::isChannelDisconnected,
        requestEmitter::emitJoinChannel,
        requestEmitter::emitDisconnectChannel,
        requestEmitter::emitCloseChannel,
        this::supportsBouncerDetach,
        requestEmitter::emitBouncerDetachChannel,
        this::isChannelAutoReattach,
        this::setChannelAutoReattach,
        requestEmitter::emitCloseTarget,
        target -> {
          if (interceptorStore == null || target == null || !target.isInterceptor()) {
            return null;
          }
          String serverId = Objects.toString(target.serverId(), "").trim();
          String interceptorId = Objects.toString(target.interceptorId(), "").trim();
          if (serverId.isEmpty() || interceptorId.isEmpty()) return null;
          return interceptorStore.interceptor(serverId, interceptorId);
        },
        this::setInterceptorEnabled,
        this::promptRenameInterceptor,
        this::confirmDeleteInterceptor);
  }

  private ServerTreeNetworkInfoDialogBuilder.Context createNetworkInfoDialogContext() {
    return new ServerTreeNetworkInfoDialogBuilder.Context() {
      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return ServerTreeDockable.this.connectionStateForServer(serverId);
      }

      @Override
      public boolean desiredOnlineForServer(String serverId) {
        return ServerTreeDockable.this.desiredOnlineForServer(serverId);
      }

      @Override
      public String prettyServerLabel(String serverId) {
        return ServerTreeDockable.this.prettyServerLabel(serverId);
      }

      @Override
      public String connectionDiagnosticsTipForServer(String serverId) {
        return ServerTreeDockable.this.connectionDiagnosticsTipForServer(serverId);
      }

      @Override
      public void requestCapabilityToggle(String serverId, String capability, boolean enable) {
        requestEmitter.emitIrcv3CapabilityToggle(
            new Ircv3CapabilityToggleRequest(serverId, capability, enable));
      }
    };
  }

  private ServerTreeSelectionFallbackPolicy.Context createSelectionFallbackPolicyContext() {
    return new ServerTreeSelectionFallbackContextAdapter(
        ServerTreeDockable::normalizeServerId,
        servers,
        this::builtInNodesVisibility,
        leaves,
        this::selectTarget,
        tree);
  }

  private ServerTreeUiLeafVisibilitySynchronizer.Context
      createUiLeafVisibilitySynchronizerContext() {
    return new ServerTreeUiLeafVisibilitySynchronizer.Context() {
      @Override
      public TargetRef selectedTargetRef() {
        return ServerTreeDockable.this.selectedTargetRef();
      }

      @Override
      public DefaultMutableTreeNode selectedTreeNode() {
        return (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
      }

      @Override
      public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
        return ServerTreeDockable.this.isMonitorGroupNode(node);
      }

      @Override
      public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
        return ServerTreeDockable.this.isInterceptorsGroupNode(node);
      }

      @Override
      public String owningServerIdForNode(DefaultMutableTreeNode node) {
        return ServerTreeDockable.this.owningServerIdForNode(node);
      }

      @Override
      public List<String> serverIdsSnapshot() {
        return List.copyOf(servers.keySet());
      }

      @Override
      public void syncServerUiLeafVisibility(String serverId) {
        ServerTreeDockable.this.syncUiLeafVisibilityForServer(serverId);
      }

      @Override
      public boolean statusVisible(String serverId) {
        return builtInNodesVisibility(serverId).server();
      }

      @Override
      public boolean notificationsVisible(String serverId) {
        return builtInNodesVisibility(serverId).notifications();
      }

      @Override
      public boolean logViewerVisible(String serverId) {
        return builtInNodesVisibility(serverId).logViewer();
      }

      @Override
      public boolean monitorVisible(String serverId) {
        return builtInNodesVisibility(serverId).monitor();
      }

      @Override
      public boolean interceptorsVisible(String serverId) {
        return builtInNodesVisibility(serverId).interceptors();
      }

      @Override
      public boolean showDccTransfersNodes() {
        return showDccTransfersNodes;
      }

      @Override
      public void selectBestFallbackForServer(String serverId) {
        ServerTreeDockable.this.selectBestFallbackForServer(serverId);
      }
    };
  }

  private ServerTreeBuiltInVisibilityCoordinator.Context createBuiltInVisibilityContext() {
    return new ServerTreeBuiltInVisibilityCoordinator.Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return ServerTreeDockable.normalizeServerId(serverId);
      }

      @Override
      public Set<String> currentServerIds() {
        return servers.keySet();
      }

      @Override
      public void syncUiLeafVisibility() {
        ServerTreeDockable.this.syncUiLeafVisibility();
      }
    };
  }

  private ServerTreeBuiltInLayoutOrchestrator.Context createBuiltInLayoutOrchestratorContext() {
    return new ServerTreeBuiltInLayoutOrchestrator.Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return ServerTreeDockable.normalizeServerId(serverId);
      }

      @Override
      public ServerNodes serverNodes(String serverId) {
        return servers.get(ServerTreeDockable.normalizeServerId(serverId));
      }

      @Override
      public DefaultMutableTreeNode leafNode(TargetRef ref) {
        return leaves.get(ref);
      }

      @Override
      public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
        return ServerTreeDockable.this.builtInNodesVisibility(serverId);
      }

      @Override
      public RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
          DefaultMutableTreeNode node) {
        return ServerTreeDockable.this.rootSiblingNodeKindForNode(node);
      }

      @Override
      public void nodeStructureChanged(DefaultMutableTreeNode node) {
        model.nodeStructureChanged(node);
      }
    };
  }

  private ServerTreeServerRootLifecycleManager.Context createServerRootLifecycleContext(
      NotificationStore notificationStore) {
    return new ServerTreeServerRootLifecycleContextAdapter(
        ServerTreeDockable::normalizeServerId,
        servers,
        runtimeState::markServerKnown,
        this::loadChannelStateForServer,
        serverParentResolver::resolveParentForServer,
        this::builtInNodesVisibility,
        () -> showDccTransfersNodes,
        this::statusLeafLabelForServer,
        serverId -> notificationStore == null ? 0 : notificationStore.count(serverId),
        serverId -> interceptorStore == null ? 0 : interceptorStore.totalHitCount(serverId),
        serverId ->
            interceptorStore == null ? List.of() : interceptorStore.listInterceptors(serverId),
        leaves,
        this::builtInLayout,
        this::rootSiblingOrder,
        this::applyBuiltInLayoutToTree,
        this::applyRootSiblingOrderToTree,
        model,
        root,
        tree,
        this::refreshNotificationsCount,
        this::refreshInterceptorGroupCount,
        serverStateCleaner::cleanupServerState,
        networkGroupManager::removeEmptyGroupIfNeeded);
  }

  private ServerTreeInteractionMediator.Context createInteractionMediatorContext() {
    return new ServerTreeInteractionMediatorContextAdapter(
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
        this::isMonitorGroupNode,
        this::isInterceptorsGroupNode,
        this::owningServerIdForNode,
        this::maybeHandleDisconnectedWarningClick,
        this::maybeSelectRowFromLeftClick,
        (x, y) -> treePathForRowHit(x, y),
        task -> {
          if (task == null) return;
          suppressSelectionBroadcast = true;
          try {
            task.run();
          } finally {
            suppressSelectionBroadcast = false;
          }
        },
        nodeActions::refreshEnabledState,
        this::buildPopupMenu,
        this::createMiddleDragReorderContext,
        () -> startupSelectionCompleted,
        () -> startupSelectionCompleted = true,
        this::isPathInCurrentTreeModel,
        () -> servers.values().stream().findFirst().map(sn -> sn.statusRef.serverId()).orElse(""),
        this::selectStartupDefaultForServer,
        this::defaultSelectionPath);
  }

  private ServerTreeSettingsSynchronizer.Context createSettingsSynchronizerContext() {
    return new ServerTreeSettingsSynchronizerContextAdapter(
        settingsBus,
        jfrRuntimeEventsService,
        runtimeConfig,
        () -> typingIndicatorsTreeEnabled,
        enabled -> typingIndicatorsTreeEnabled = enabled,
        () -> {
          if (typingActivityManager == null) return;
          typingActivityManager.clearTypingIndicatorsFromTree();
        },
        style -> typingIndicatorStyle = style == null ? ServerTreeTypingIndicatorStyle.DOTS : style,
        enabled -> serverTreeNotificationBadgesEnabled = enabled,
        percent -> unreadBadgeScalePercent = percent,
        this::refreshTreeLayoutAfterUiChange,
        this::refreshApplicationJfrNode);
  }

  private ServerTreeMiddleDragReorderHandler.Context createMiddleDragReorderContext() {
    return new ServerTreeMiddleDragReorderContextAdapter(
        tree,
        model,
        this::isDraggableChannelNode,
        this::isRootSiblingReorderableNode,
        this::isMovableBuiltInNode,
        this::owningServerIdForNode,
        serverId -> servers.get(ServerTreeDockable.normalizeServerId(serverId)),
        this::rootSiblingNodeKindForNode,
        this::builtInLayoutNodeKindForNode,
        this::minInsertIndex,
        this::maxInsertIndex,
        this::rootBuiltInInsertIndex,
        (parent, insertBeforeIndex) ->
            setInsertionLine(insertionLineForIndex(parent, insertBeforeIndex)),
        () -> setInsertionLine(null),
        this::isChannelListLeafNode,
        parentNode -> {
          String serverId = owningServerIdForNode(parentNode);
          if (serverId.isBlank()) return;
          channelStateCoordinator.persistCustomOrderFromTreeIfCustom(serverId);
        },
        this::persistBuiltInLayoutFromTree,
        this::persistRootSiblingOrderFromTree,
        task -> {
          if (task == null) return;
          suppressSelectionBroadcast = true;
          try {
            task.run();
          } finally {
            suppressSelectionBroadcast = false;
          }
        },
        nodeActions::refreshEnabledState);
  }

  private boolean isDraggableChannelNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof ServerTreeNodeData nd)) return false;
    if (nd.ref == null || !nd.ref.isChannel()) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    return parent != null && (isServerNode(parent) || isChannelListLeafNode(parent));
  }

  private boolean isMovableBuiltInNode(DefaultMutableTreeNode node) {
    RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind = builtInLayoutNodeKindForNode(node);
    if (nodeKind == null) return false;
    String sid = owningServerIdForNode(node);
    if (sid.isBlank()) return false;
    ServerNodes sn = servers.get(sid);
    if (sn == null) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    return parent == sn.serverNode || parent == sn.otherNode;
  }

  private int minInsertIndex(DefaultMutableTreeNode parentNode) {
    if (parentNode == null) return 0;
    if (isChannelListLeafNode(parentNode)) return 0;

    int min = 0;
    int count = parentNode.getChildCount();
    while (min < count) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parentNode.getChildAt(min);
      Object uo = child.getUserObject();
      if (uo instanceof ServerTreeNodeData nd) {
        if (nd.ref == null || nd.ref.isStatus() || nd.ref.isUiOnly()) {
          min++;
          continue;
        }
      } else if (isInterceptorsGroupNode(child)) {
        min++;
        continue;
      }
      break;
    }
    return min;
  }

  private int maxInsertIndex(DefaultMutableTreeNode parentNode) {
    if (parentNode == null) return 0;
    if (isChannelListLeafNode(parentNode)) return parentNode.getChildCount();

    int idx = parentNode.getChildCount();
    while (idx > 0) {
      DefaultMutableTreeNode tail = (DefaultMutableTreeNode) parentNode.getChildAt(idx - 1);
      if (isReservedServerTailNode(tail)) {
        idx--;
        continue;
      }
      break;
    }
    return idx;
  }

  private boolean isReservedServerTailNode(DefaultMutableTreeNode node) {
    return isPrivateMessagesGroupNode(node)
        || isSojuNetworksGroupNode(node)
        || isZncNetworksGroupNode(node);
  }

  private void setInsertionLine(InsertionLine line) {
    InsertionLine old = this.insertionLine;
    this.insertionLine = line;
    if (old != null) tree.repaint(old.repaintRect());
    if (line != null) tree.repaint(line.repaintRect());
  }

  private void paintInsertionLine(Graphics g) {
    InsertionLine line = this.insertionLine;
    if (line == null) return;

    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Color c = UIManager.getColor("Component.accentColor");
      if (c == null) c = UIManager.getColor("Tree.selectionBorderColor");
      if (c == null) c = UIManager.getColor("Tree.selectionForeground");
      if (c == null) c = Color.BLACK;
      g2.setColor(c);

      g2.setStroke(new BasicStroke(2f));
      g2.drawLine(line.x1, line.y, line.x2, line.y);
    } finally {
      g2.dispose();
    }
  }

  private InsertionLine insertionLineForIndex(
      DefaultMutableTreeNode parent, int insertBeforeIndex) {
    if (parent == null) return null;

    int childCount = parent.getChildCount();
    if (childCount == 0) {
      Rectangle pr = tree.getPathBounds(new TreePath(parent.getPath()));
      if (pr == null) return null;
      int x1 = Math.max(0, pr.x);
      int x2 = Math.max(x1 + 1, tree.getWidth() - 4);
      int y = pr.y + pr.height - 1;
      return new InsertionLine(x1, y, x2);
    }

    int idx = Math.max(0, Math.min(childCount, insertBeforeIndex));

    Rectangle r;
    int y;
    if (idx >= childCount) {
      DefaultMutableTreeNode last = (DefaultMutableTreeNode) parent.getChildAt(childCount - 1);
      r = tree.getPathBounds(new TreePath(last.getPath()));
      if (r == null) return null;
      y = r.y + r.height - 1;
    } else {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(idx);
      r = tree.getPathBounds(new TreePath(child.getPath()));
      if (r == null) return null;
      y = r.y;
    }

    int x1 = Math.max(0, r.x);
    int x2 = Math.max(x1 + 1, tree.getWidth() - 4);
    return new InsertionLine(x1, y, x2);
  }

  private String owningServerIdForNode(DefaultMutableTreeNode node) {
    return nodeClassifier.owningServerIdForNode(node);
  }

  private boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {
    return nodeClassifier.isPrivateMessagesGroupNode(node);
  }

  private boolean isChannelListLeafNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof ServerTreeNodeData nd)) return false;
    return nd.ref != null && nd.ref.isChannelList();
  }

  private boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
    return nodeClassifier.isInterceptorsGroupNode(node);
  }

  private boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
    return nodeClassifier.isMonitorGroupNode(node);
  }

  private boolean isOtherGroupNode(DefaultMutableTreeNode node) {
    return nodeClassifier.isOtherGroupNode(node);
  }

  private RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForRef(
      TargetRef ref) {
    return ServerTreeBuiltInLayoutCoordinator.nodeKindForRef(ref);
  }

  private RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
      DefaultMutableTreeNode node) {
    return builtInLayoutCoordinator.nodeKindForNode(
        node, this::isMonitorGroupNode, this::isInterceptorsGroupNode, this::targetRefForNode);
  }

  private RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
      DefaultMutableTreeNode node) {
    return rootSiblingOrderCoordinator.nodeKindForNode(
        node, this::isOtherGroupNode, this::isPrivateMessagesGroupNode, this::targetRefForNode);
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

  private boolean isRootSiblingReorderableNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    String sid = owningServerIdForNode(node);
    if (sid.isBlank()) return false;
    ServerNodes sn = servers.get(sid);
    if (sn == null || sn.serverNode == null) return false;
    if (node.getParent() != sn.serverNode) return false;
    return rootSiblingNodeKindForNode(node) != null;
  }

  private ServerTreeCellRenderer createTreeCellRenderer() {
    return new ServerTreeCellRenderer(
        IRC_ROOT_LABEL, APPLICATION_ROOT_LABEL, createTreeCellRendererContext());
  }

  private ServerTreeCellRenderer.Context createTreeCellRendererContext() {
    return new ServerTreeCellRendererContextAdapter(
        () -> serverTreeNotificationBadgesEnabled,
        () -> unreadBadgeScalePercent,
        () -> typingIndicatorStyle,
        () -> typingIndicatorsTreeEnabled,
        this::isPrivateMessageTarget,
        privateMessageOnlineStateStore::isOnline,
        this::isApplicationJfrActive,
        this::isInterceptorEnabled,
        this::isMonitorGroupNode,
        this::isInterceptorsGroupNode,
        this::isOtherGroupNode,
        this::isServerNode,
        this::serverNodeDisplayLabel,
        ephemeralServerIds::contains,
        this::connectionStateForServer,
        this::isIrcRootNode,
        this::isApplicationRootNode,
        this::isPrivateMessagesGroupNode,
        this::isSojuNetworksGroupNode,
        this::isZncNetworksGroupNode);
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
    if (SwingUtilities.isEventDispatchThread()) {
      return targetSnapshotProvider.snapshotOpenChannelsForServer(sid);
    }

    AtomicReference<List<String>> out = new AtomicReference<>(List.of());
    try {
      SwingUtilities.invokeAndWait(
          () -> out.set(targetSnapshotProvider.snapshotOpenChannelsForServer(sid)));
      return out.get();
    } catch (Exception ex) {
      log.debug("[ircafe] open channel snapshot failed for server={}", sid, ex);
      return List.of();
    }
  }

  public List<ManagedChannelEntry> managedChannelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    if (SwingUtilities.isEventDispatchThread()) {
      return channelStateCoordinator.snapshotManagedChannelsForServer(sid);
    }

    AtomicReference<List<ManagedChannelEntry>> out = new AtomicReference<>(List.of());
    try {
      SwingUtilities.invokeAndWait(
          () -> out.set(channelStateCoordinator.snapshotManagedChannelsForServer(sid)));
      return out.get();
    } catch (Exception ex) {
      log.debug("[ircafe] managed channel snapshot failed for server={}", sid, ex);
      return List.of();
    }
  }

  public ChannelSortMode channelSortModeForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return ChannelSortMode.CUSTOM;
    if (SwingUtilities.isEventDispatchThread()) {
      return channelStateCoordinator.channelSortModeForServer(sid);
    }

    AtomicReference<ChannelSortMode> out = new AtomicReference<>(ChannelSortMode.CUSTOM);
    try {
      SwingUtilities.invokeAndWait(
          () -> out.set(channelStateCoordinator.channelSortModeForServer(sid)));
      return out.get();
    } catch (Exception ex) {
      log.debug("[ircafe] channel sort mode snapshot failed for server={}", sid, ex);
      return ChannelSortMode.CUSTOM;
    }
  }

  public void setChannelSortModeForServer(String serverId, ChannelSortMode mode) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ChannelSortMode next = mode == null ? ChannelSortMode.CUSTOM : mode;
    Runnable apply = () -> channelStateCoordinator.setChannelSortModeForServer(sid, next);

    if (SwingUtilities.isEventDispatchThread()) {
      apply.run();
    } else {
      SwingUtilities.invokeLater(apply);
    }
  }

  public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    List<String> requested = channels == null ? List.of() : List.copyOf(channels);
    Runnable apply = () -> channelStateCoordinator.setChannelCustomOrderForServer(sid, requested);

    if (SwingUtilities.isEventDispatchThread()) {
      apply.run();
    } else {
      SwingUtilities.invokeLater(apply);
    }
  }

  public void requestJoinChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    requestEmitter.emitJoinChannel(target);
  }

  public void requestDisconnectChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    requestEmitter.emitDisconnectChannel(target);
  }

  public void requestCloseChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    requestEmitter.emitCloseChannel(target);
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

  private ServerRuntimeMetadata metadataForServer(String serverId) {
    return runtimeState.metadataForServer(serverId);
  }

  private void openServerInfoDialog(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    networkInfoDialogBuilder.open(this, sid, metadataForServer(sid));
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
    return builtInVisibilityCoordinator.defaultVisibility().server();
  }

  public boolean isLogViewerNodesVisible() {
    return builtInVisibilityCoordinator.defaultVisibility().logViewer();
  }

  public boolean isNotificationsNodesVisible() {
    return builtInVisibilityCoordinator.defaultVisibility().notifications();
  }

  public boolean isMonitorNodesVisible() {
    return builtInVisibilityCoordinator.defaultVisibility().monitor();
  }

  public boolean isInterceptorsNodesVisible() {
    return builtInVisibilityCoordinator.defaultVisibility().interceptors();
  }

  public boolean isServerNodeVisibleForServer(String serverId) {
    return builtInNodesVisibility(serverId).server();
  }

  public boolean isLogViewerNodeVisibleForServer(String serverId) {
    return builtInNodesVisibility(serverId).logViewer();
  }

  public boolean isNotificationsNodeVisibleForServer(String serverId) {
    return builtInNodesVisibility(serverId).notifications();
  }

  public boolean isMonitorNodeVisibleForServer(String serverId) {
    return builtInNodesVisibility(serverId).monitor();
  }

  public boolean isInterceptorsNodeVisibleForServer(String serverId) {
    return builtInNodesVisibility(serverId).interceptors();
  }

  public void setServerNodeVisibleForServer(String serverId, boolean visible) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    applyBuiltInNodesVisibilityForServer(sid, current.withServer(visible), true, true);
  }

  public void setLogViewerNodeVisibleForServer(String serverId, boolean visible) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    applyBuiltInNodesVisibilityForServer(sid, current.withLogViewer(visible), true, true);
  }

  public void setNotificationsNodeVisibleForServer(String serverId, boolean visible) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    applyBuiltInNodesVisibilityForServer(sid, current.withNotifications(visible), true, true);
  }

  public void setMonitorNodeVisibleForServer(String serverId, boolean visible) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    applyBuiltInNodesVisibilityForServer(sid, current.withMonitor(visible), true, true);
  }

  public void setInterceptorsNodeVisibleForServer(String serverId, boolean visible) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    applyBuiltInNodesVisibilityForServer(sid, current.withInterceptors(visible), true, true);
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
    ServerBuiltInNodesVisibility current = builtInVisibilityCoordinator.defaultVisibility();
    builtInVisibilityCoordinator.setDefaultVisibility(current.withServer(visible));
    applyBuiltInNodesVisibilityGlobally(v -> v.withServer(visible));
  }

  public void setLogViewerNodesVisible(boolean visible) {
    ServerBuiltInNodesVisibility current = builtInVisibilityCoordinator.defaultVisibility();
    boolean old = current.logViewer();
    builtInVisibilityCoordinator.setDefaultVisibility(current.withLogViewer(visible));
    applyBuiltInNodesVisibilityGlobally(v -> v.withLogViewer(visible));
    firePropertyChange(PROP_LOG_VIEWER_NODES_VISIBLE, old, visible);
  }

  public void setNotificationsNodesVisible(boolean visible) {
    ServerBuiltInNodesVisibility current = builtInVisibilityCoordinator.defaultVisibility();
    boolean old = current.notifications();
    builtInVisibilityCoordinator.setDefaultVisibility(current.withNotifications(visible));
    applyBuiltInNodesVisibilityGlobally(v -> v.withNotifications(visible));
    firePropertyChange(PROP_NOTIFICATIONS_NODES_VISIBLE, old, visible);
  }

  public void setMonitorNodesVisible(boolean visible) {
    ServerBuiltInNodesVisibility current = builtInVisibilityCoordinator.defaultVisibility();
    boolean old = current.monitor();
    builtInVisibilityCoordinator.setDefaultVisibility(current.withMonitor(visible));
    applyBuiltInNodesVisibilityGlobally(v -> v.withMonitor(visible));
    firePropertyChange(PROP_MONITOR_NODES_VISIBLE, old, visible);
  }

  public void setInterceptorsNodesVisible(boolean visible) {
    ServerBuiltInNodesVisibility current = builtInVisibilityCoordinator.defaultVisibility();
    boolean old = current.interceptors();
    builtInVisibilityCoordinator.setDefaultVisibility(current.withInterceptors(visible));
    applyBuiltInNodesVisibilityGlobally(v -> v.withInterceptors(visible));
    firePropertyChange(PROP_INTERCEPTORS_NODES_VISIBLE, old, visible);
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
    ensureMovableBuiltInLeafVisible(sn, sn.statusRef, statusLeafLabelForServer(sid), vis.server());
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

  private int rootBuiltInInsertIndex(ServerNodes sn, int desiredIndex) {
    return builtInLayoutOrchestrator.rootBuiltInInsertIndex(sn, desiredIndex);
  }

  private void applyBuiltInLayoutToTree(
      ServerNodes sn, RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout) {
    builtInLayoutOrchestrator.applyBuiltInLayoutToTree(sn, requestedLayout);
  }

  private void applyRootSiblingOrderToTree(
      ServerNodes sn, RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder) {
    builtInLayoutOrchestrator.applyRootSiblingOrderToTree(sn, requestedOrder);
  }

  private void persistRootSiblingOrderFromTree(String serverId) {
    builtInLayoutOrchestrator.persistRootSiblingOrderFromTree(serverId);
  }

  private void persistBuiltInLayoutFromTree(String serverId) {
    builtInLayoutOrchestrator.persistBuiltInLayoutFromTree(serverId);
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
    if (ref == null || !ref.isChannel()) return true;

    if (SwingUtilities.isEventDispatchThread()) {
      return channelStateCoordinator.isChannelAutoReattach(ref);
    }

    AtomicReference<Boolean> out = new AtomicReference<>(Boolean.TRUE);
    try {
      SwingUtilities.invokeAndWait(
          () -> out.set(channelStateCoordinator.isChannelAutoReattach(ref)));
      return out.get();
    } catch (Exception ex) {
      return true;
    }
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    if (ref == null || !ref.isChannel()) return;
    Runnable apply = () -> channelStateCoordinator.setChannelAutoReattach(ref, autoReattach);

    if (SwingUtilities.isEventDispatchThread()) {
      apply.run();
    } else {
      SwingUtilities.invokeLater(apply);
    }
  }

  private void emitManagedChannelsChanged(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    requestEmitter.emitManagedChannelsChanged(sid);
  }

  private void ensureChannelKnownInConfig(TargetRef ref) {
    channelStateCoordinator.ensureChannelKnownInConfig(ref);
  }

  private List<ManagedChannelEntry> snapshotManagedChannelsForServer(String serverId) {
    return channelStateCoordinator.snapshotManagedChannelsForServer(serverId);
  }

  private void sortChannelsUnderChannelList(String serverId) {
    channelStateCoordinator.sortChannelsUnderChannelList(serverId);
  }

  private void persistCustomOrderFromTree(String serverId) {
    channelStateCoordinator.persistCustomOrderFromTree(serverId);
  }

  private void loadChannelStateForServer(String serverId) {
    channelStateCoordinator.loadChannelStateForServer(serverId);
  }

  private static String foldChannelKey(String channel) {
    return Objects.toString(channel, "").trim().toLowerCase(java.util.Locale.ROOT);
  }

  private void noteChannelActivity(TargetRef ref) {
    channelStateCoordinator.noteChannelActivity(ref);
  }

  public void markUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof ServerTreeNodeData nd)) return;
    nd.unread++;
    noteChannelActivity(ref);
    model.nodeChanged(node);
    if (ref != null && ref.isChannel()) {
      emitManagedChannelsChanged(ref.serverId());
    }
  }

  public void markHighlight(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof ServerTreeNodeData nd)) return;
    nd.highlightUnread++;
    noteChannelActivity(ref);
    model.nodeChanged(node);
    if (ref != null && ref.isChannel()) {
      emitManagedChannelsChanged(ref.serverId());
    }
  }

  public void clearUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof ServerTreeNodeData nd)) return;
    if (nd.unread == 0 && nd.highlightUnread == 0) return;
    nd.unread = 0;
    nd.highlightUnread = 0;
    model.nodeChanged(node);
    if (ref != null && ref.isChannel()) {
      emitManagedChannelsChanged(ref.serverId());
    }
  }

  public void markTypingActivity(TargetRef ref, String state) {
    typingActivityManager.markTypingActivity(ref, state);
  }

  private void onTypingActivityAnimationTick() {
    typingActivityManager.onTypingActivityAnimationTick();
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

  private void syncServers(List<ServerEntry> latest) {
    serverCatalogSynchronizer.syncServers(latest);
  }

  private String prettyServerLabel(String serverId) {
    return serverLabelPolicy.prettyServerLabel(serverId);
  }

  private boolean isSojuEphemeralServer(String serverId) {
    return serverLabelPolicy.isSojuEphemeralServer(serverId);
  }

  private boolean isZncEphemeralServer(String serverId) {
    return serverLabelPolicy.isZncEphemeralServer(serverId);
  }

  private boolean supportsBouncerDetach(String serverId) {
    return bouncerDetachPolicy.supportsBouncerDetach(serverId);
  }

  private DefaultMutableTreeNode getOrCreateSojuNetworksGroupNode(String originServerId) {
    return networkGroupManager.getOrCreateSojuNetworksGroupNode(originServerId);
  }

  private boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
    return networkGroupManager.isSojuNetworksGroupNode(node);
  }

  private DefaultMutableTreeNode getOrCreateZncNetworksGroupNode(String originServerId) {
    return networkGroupManager.getOrCreateZncNetworksGroupNode(originServerId);
  }

  private boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
    return networkGroupManager.isZncNetworksGroupNode(node);
  }

  private String toolTipForEvent(MouseEvent event) {
    return tooltipProvider.toolTipForEvent(event);
  }

  private void refreshSojuAutoConnectBadges() {
    nodeBadgeUpdater.refreshSojuAutoConnectBadges();
  }

  private void refreshZncAutoConnectBadges() {
    nodeBadgeUpdater.refreshZncAutoConnectBadges();
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

  private String statusLeafLabelForServer(String serverId) {
    return statusLabelManager.statusLeafLabelForServer(serverId);
  }

  private void updateBouncerControlLabels(
      Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl) {
    statusLabelManager.updateBouncerControlLabels(nextSojuBouncerControl, nextZncBouncerControl);
  }

  private void refreshNotificationsCount(String serverId) {
    nodeBadgeUpdater.refreshNotificationsCount(serverId);
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
