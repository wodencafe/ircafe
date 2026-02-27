package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.PircbotxBotFactory;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import cafe.woden.ircclient.ui.util.TreeNodeActions;
import cafe.woden.ircclient.ui.util.TreeWheelSelectionDecorator;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.miginfocom.swing.MigLayout;
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
  private static final String BOUNCER_CONTROL_LABEL = "Bouncer Control";
  private static final String IRC_ROOT_LABEL = "IRC";
  private static final String APPLICATION_ROOT_LABEL = "Application";
  private static final String APP_UNHANDLED_ERRORS_LABEL = "Unhandled Errors";
  private static final String APP_ASSERTJ_SWING_LABEL = "AssertJ Swing";
  private static final String APP_JHICCUP_LABEL = "jHiccup";
  private static final String APP_JFR_LABEL = "JFR";
  private static final String APP_SPRING_LABEL = "Spring";
  private static final String APP_TERMINAL_LABEL = "Terminal";
  private static final String SOJU_NETWORKS_GROUP_LABEL = "Soju Networks";
  private static final String ZNC_NETWORKS_GROUP_LABEL = "ZNC Networks";
  private static final int TREE_NODE_ICON_SIZE = 13;
  private static final int SERVER_ACTION_BUTTON_SIZE = 16;
  private static final int SERVER_ACTION_BUTTON_ICON_SIZE = 12;
  private static final int SERVER_ACTION_BUTTON_MARGIN = 6;
  private static final int TYPING_ACTIVITY_HOLD_MS = 8000;
  private static final int TYPING_ACTIVITY_FADE_MS = 900;
  private static final int TYPING_ACTIVITY_PULSE_MS = 1200;
  private static final int TYPING_ACTIVITY_TICK_MS = 100;
  private static final int TYPING_ACTIVITY_DOT_COUNT = 3;
  private static final int TYPING_ACTIVITY_DOT_SIZE = 3;
  private static final int TYPING_ACTIVITY_DOT_GAP = 2;
  private static final int TYPING_ACTIVITY_DOT_FRAME_MS = 220;
  private static final int TYPING_ACTIVITY_LEFT_SLOT_WIDTH = 12;
  private static final Color TYPING_ACTIVITY_GLOW_DOT = new Color(65, 210, 108);
  private static final Color TYPING_ACTIVITY_GLOW_HALO = new Color(120, 255, 150);
  private static final Color TYPING_ACTIVITY_INDICATOR_FALLBACK = new Color(90, 150, 235);
  private static final Color DETACHED_WARNING_FILL = new Color(230, 164, 39);
  private static final Color DETACHED_WARNING_STROKE = new Color(152, 94, 0);
  private static final Color DETACHED_WARNING_TEXT = Color.WHITE;
  private static final int TREE_BADGE_HORIZONTAL_PADDING = 4;
  private static final int TREE_BADGE_VERTICAL_PADDING = 1;
  private static final int TREE_BADGE_MIN_WIDTH = 14;
  private static final int TREE_BADGE_MIN_HEIGHT = 12;
  private static final int TREE_BADGE_GAP = 3;
  private static final int TREE_BADGE_ARC = 8;
  private static final int TREE_BADGE_SCALE_PERCENT_DEFAULT = 100;
  private static final Color TREE_UNREAD_BADGE_BG = new Color(31, 111, 255);
  private static final Color TREE_HIGHLIGHT_BADGE_BG = new Color(205, 54, 54);
  private static final Color TREE_BADGE_FG = Color.WHITE;
  public static final String PROP_CHANNEL_LIST_NODES_VISIBLE = "channelListNodesVisible";
  public static final String PROP_DCC_TRANSFERS_NODES_VISIBLE = "dccTransfersNodesVisible";
  public static final String PROP_LOG_VIEWER_NODES_VISIBLE = "logViewerNodesVisible";
  public static final String PROP_NOTIFICATIONS_NODES_VISIBLE = "notificationsNodesVisible";
  public static final String PROP_MONITOR_NODES_VISIBLE = "monitorNodesVisible";
  public static final String PROP_INTERCEPTORS_NODES_VISIBLE = "interceptorsNodesVisible";
  public static final String PROP_APPLICATION_ROOT_VISIBLE = "applicationRootVisible";

  public enum ChannelSortMode {
    ALPHABETICAL,
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

  private final FlowableProcessor<TargetRef> detachChannelRequests =
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

  // Hidden top-level container. Visible top-level nodes are siblings: IRC + Application.
  private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("(root)");
  private final DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode(IRC_ROOT_LABEL);
  private final DefaultMutableTreeNode applicationRoot =
      new DefaultMutableTreeNode(APPLICATION_ROOT_LABEL);
  private final TargetRef applicationUnhandledErrorsRef = TargetRef.applicationUnhandledErrors();
  private final TargetRef applicationAssertjSwingRef = TargetRef.applicationAssertjSwing();
  private final TargetRef applicationJhiccupRef = TargetRef.applicationJhiccup();
  private final TargetRef applicationJfrRef = TargetRef.applicationJfr();
  private final TargetRef applicationSpringRef = TargetRef.applicationSpring();
  private final TargetRef applicationTerminalRef = TargetRef.applicationTerminal();
  private final DefaultTreeModel model = new DefaultTreeModel(root);

  private volatile InsertionLine insertionLine;
  private String hoveredServerActionServerId = "";

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
          ServerTreeDockable.this.paintVisibleServerActions(g);
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

  private final ServerTreeCellRenderer treeCellRenderer = new ServerTreeCellRenderer();

  private final JLabel statusLabel = new JLabel("Disconnected");

  private final JButton addServerBtn = new JButton();
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;

  private final Map<String, ServerNodes> servers = new HashMap<>();
  private final Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
  private final Map<TargetRef, Boolean> privateMessageOnlineByTarget = new HashMap<>();
  private final Map<String, ChannelSortMode> channelSortModeByServer = new HashMap<>();
  private final Map<String, ArrayList<String>> channelCustomOrderByServer = new HashMap<>();
  private final Map<String, Map<String, Boolean>> channelAutoReattachByServer = new HashMap<>();

  private final Map<String, ConnectionState> serverStates = new HashMap<>();
  private final Map<String, Boolean> serverDesiredOnline = new HashMap<>();
  private final Map<String, String> serverLastError = new HashMap<>();
  private final Map<String, Long> serverNextRetryAtEpochMs = new HashMap<>();
  private final Map<String, ServerRuntimeMetadata> serverRuntimeMetadata = new HashMap<>();
  private final Timer typingActivityTimer;
  private final Set<DefaultMutableTreeNode> typingActivityNodes = new HashSet<>();

  private static final DateTimeFormatter SERVER_META_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter CAP_TRANSITION_TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final int CAPABILITY_TRANSITION_LOG_LIMIT = 200;

  private final ServerCatalog serverCatalog;
  private final RuntimeConfigStore runtimeConfig;
  private final LogProperties logProps;

  private final SojuAutoConnectStore sojuAutoConnect;

  private final ZncAutoConnectStore zncAutoConnect;

  private final Map<String, String> serverDisplayNames = new HashMap<>();
  private final Set<String> ephemeralServerIds = new HashSet<>();
  private final Set<String> sojuBouncerControlServerIds = new HashSet<>();
  private final Set<String> zncBouncerControlServerIds = new HashSet<>();
  private final Map<String, String> sojuOriginByServerId = new HashMap<>();
  private final Map<String, String> zncOriginByServerId = new HashMap<>();
  private final Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin = new HashMap<>();
  private final Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin = new HashMap<>();

  private final NotificationStore notificationStore;
  private final InterceptorStore interceptorStore;
  private final JfrRuntimeEventsService jfrRuntimeEventsService;
  private final ServerDialogs serverDialogs;
  private final UiSettingsBus settingsBus;
  private PropertyChangeListener settingsListener;
  private PropertyChangeListener jfrStateListener;
  private volatile TreeTypingIndicatorStyle typingIndicatorStyle = TreeTypingIndicatorStyle.DOTS;
  private volatile int unreadBadgeScalePercent = TREE_BADGE_SCALE_PERCENT_DEFAULT;
  private volatile boolean showChannelListNodes = true;
  private volatile boolean showDccTransfersNodes = false;
  private volatile ServerBuiltInNodesVisibility defaultBuiltInNodesVisibility =
      ServerBuiltInNodesVisibility.defaults();
  private final Map<String, ServerBuiltInNodesVisibility> builtInNodesVisibilityByServer =
      new HashMap<>();
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
    this.sojuAutoConnect = sojuAutoConnect;
    this.zncAutoConnect = zncAutoConnect;
    this.notificationStore = notificationStore;
    this.interceptorStore = interceptorStore;
    this.jfrRuntimeEventsService = jfrRuntimeEventsService;
    this.settingsBus = settingsBus;
    this.serverDialogs = serverDialogs;
    loadPersistedBuiltInNodesVisibility();
    syncTypingIndicatorStyleFromSettings();
    syncUnreadBadgeScaleFromRuntimeConfig();

    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;
    this.addServerBtn.setText("");
    this.addServerBtn.setIcon(SvgIcons.action("plus", 16));
    this.addServerBtn.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    this.addServerBtn.setToolTipText("Add server");
    this.addServerBtn.setFocusable(false);
    this.addServerBtn.setPreferredSize(new Dimension(26, 26));
    this.addServerBtn.setEnabled(serverDialogs != null);
    this.addServerBtn.addActionListener(
        ev -> {
          if (serverDialogs == null) return;
          Window w = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
          serverDialogs.openAddServer(w);
        });
    this.connectBtn.setText("");
    this.connectBtn.setIcon(SvgIcons.action("check", 16));
    this.connectBtn.setDisabledIcon(SvgIcons.actionDisabled("check", 16));
    this.connectBtn.setToolTipText("Connect all disconnected servers");
    this.connectBtn.setFocusable(false);
    this.connectBtn.setPreferredSize(new Dimension(26, 26));
    this.disconnectBtn.setText("");
    this.disconnectBtn.setIcon(SvgIcons.action("exit", 16));
    this.disconnectBtn.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
    this.disconnectBtn.setToolTipText("Disconnect connected/connecting servers");
    this.disconnectBtn.setFocusable(false);
    this.disconnectBtn.setPreferredSize(new Dimension(26, 26));
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

    header.add(addServerBtn);
    header.add(Box.createHorizontalStrut(6));
    header.add(connectBtn);
    header.add(Box.createHorizontalStrut(6));
    header.add(disconnectBtn);
    header.add(Box.createHorizontalGlue());

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
    tree.addHierarchyListener(
        e -> {
          if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
          if (tree.isShowing()) {
            startTypingActivityTimerIfNeeded();
            tree.repaint();
            return;
          }
          typingActivityTimer.stop();
        });
    ToolTipManager.sharedInstance().registerComponent(tree);
    tree.addPropertyChangeListener(
        "UI", e -> SwingUtilities.invokeLater(this::refreshTreeLayoutAfterUiChange));
    this.nodeActions =
        new TreeNodeActions<>(
            tree,
            model,
            new ServerTreeNodeReorderPolicy(this::isServerNode, this::isChannelListLeafNode),
            n -> {
              Object uo = n.getUserObject();
              if (uo instanceof NodeData nd) return nd.ref;
              return null;
            },
            ref -> {
              if (ref == null) return;
              if (ref.isChannel()) {
                if (!isChannelDetached(ref)) {
                  detachChannelRequests.onNext(ref);
                }
                return;
              }
              closeTargetRequests.onNext(ref);
            },
            movedNode -> {
              if (movedNode == null) return;
              Object uo = movedNode.getUserObject();
              if (!(uo instanceof NodeData nd) || nd.ref == null || !nd.ref.isChannel()) return;
              DefaultMutableTreeNode parent = (DefaultMutableTreeNode) movedNode.getParent();
              if (!isChannelListLeafNode(parent)) return;
              String sid = owningServerIdForNode(parent);
              if (channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM)
                  == ChannelSortMode.CUSTOM) {
                persistCustomOrderFromTree(sid);
              }
            });
    installTreeKeyBindings();

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

    if (this.settingsBus != null) {
      settingsListener =
          evt -> {
            if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
            syncTypingIndicatorStyleFromSettings();
            syncUnreadBadgeScaleFromRuntimeConfig();
            SwingUtilities.invokeLater(this::refreshTreeLayoutAfterUiChange);
          };
      this.settingsBus.addListener(settingsListener);
    }
    if (this.jfrRuntimeEventsService != null) {
      jfrStateListener =
          evt -> {
            if (!JfrRuntimeEventsService.PROP_STATE.equals(evt.getPropertyName())) return;
            SwingUtilities.invokeLater(this::refreshApplicationJfrNode);
          };
      this.jfrRuntimeEventsService.addStateListener(jfrStateListener);
    }

    TreeSelectionListener tsl =
        e -> {
          DefaultMutableTreeNode node =
              (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
          if (!suppressSelectionBroadcast && node != null) {
            Object uo = node.getUserObject();
            if (uo instanceof NodeData nd) {
              if (nd.ref != null) {
                selections.onNext(nd.ref);
              } else if (isMonitorGroupNode(node)) {
                String serverId = owningServerIdForNode(node);
                if (!serverId.isBlank()) selections.onNext(TargetRef.monitorGroup(serverId));
              } else if (isInterceptorsGroupNode(node)) {
                String serverId = owningServerIdForNode(node);
                if (!serverId.isBlank()) selections.onNext(TargetRef.interceptorsGroup(serverId));
              }
            } else if (isMonitorGroupNode(node)) {
              String serverId = owningServerIdForNode(node);
              if (!serverId.isBlank()) selections.onNext(TargetRef.monitorGroup(serverId));
            } else if (isInterceptorsGroupNode(node)) {
              String serverId = owningServerIdForNode(node);
              if (!serverId.isBlank()) selections.onNext(TargetRef.interceptorsGroup(serverId));
            }
          }
          tree.repaint();
        };
    tree.addTreeSelectionListener(tsl);

    MouseAdapter hoverServerActionListener =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            updateHoveredServerAction(e);
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            updateHoveredServerAction(null);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            updateHoveredServerAction(null);
          }

          @Override
          public void mousePressed(MouseEvent e) {
            if (maybeHandleHoveredServerActionClick(e)) return;
            if (maybeHandleDetachedWarningClick(e)) return;
            maybeSelectRowFromLeftClick(e);
            updateHoveredServerAction(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            updateHoveredServerAction(e);
          }
        };
    tree.addMouseMotionListener(hoverServerActionListener);
    tree.addMouseListener(hoverServerActionListener);

    MouseAdapter popupListener =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
          }

          private void maybeShowPopup(MouseEvent e) {
            if (!e.isPopupTrigger()) return;

            int x = e.getX();
            int y = e.getY();
            TreePath path = treePathForRowHit(x, y);
            if (path == null) return;
            suppressSelectionBroadcast = true;
            try {
              tree.setSelectionPath(path);
            } finally {
              suppressSelectionBroadcast = false;
            }
            nodeActions.refreshEnabledState();

            JPopupMenu menu = buildPopupMenu(path);
            if (menu == null || menu.getComponentCount() == 0) return;
            PopupMenuThemeSupport.prepareForDisplay(menu);
            menu.show(tree, x, y);
          }
        };
    tree.addMouseListener(popupListener);
    MouseAdapter middleDragReorder =
        new MouseAdapter() {
          private DefaultMutableTreeNode dragNode;
          private DefaultMutableTreeNode dragParent;
          private int dragFromIndex = -1;
          private boolean dragging = false;
          private boolean draggedWasSelected = false;
          private Cursor oldCursor;

          @Override
          public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isMiddleMouseButton(e)) return;

            TreePath path = tree.getPathForLocation(e.getX(), e.getY());
            if (path == null) return;

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!isDraggableChannelNode(node)) return;

            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent == null) return;

            dragNode = node;
            dragParent = parent;
            dragFromIndex = parent.getIndex(node);
            dragging = true;

            TreePath sel = tree.getSelectionPath();
            draggedWasSelected = sel != null && sel.getLastPathComponent() == dragNode;

            oldCursor = tree.getCursor();
            tree.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            updateInsertionLine(e);
            e.consume();
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            if (!dragging) return;

            TreePath p = tree.getClosestPathForLocation(e.getX(), e.getY());
            tree.setLeadSelectionPath(p);

            int row = tree.getRowForLocation(e.getX(), e.getY());
            if (row >= 0) tree.scrollRowToVisible(row);
            updateInsertionLine(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (!dragging) return;
            try {
              performDrop(e);
            } finally {
              cleanup();
            }
          }

          private void updateInsertionLine(MouseEvent e) {
            ServerTreeDockable.this.setInsertionLine(computeInsertionLine(e));
          }

          private InsertionLine computeInsertionLine(MouseEvent e) {
            if (dragNode == null || dragParent == null) return null;

            TreePath targetPath = tree.getClosestPathForLocation(e.getX(), e.getY());
            DefaultMutableTreeNode targetNode = null;
            if (targetPath != null) {
              Object o = targetPath.getLastPathComponent();
              if (o instanceof DefaultMutableTreeNode n) targetNode = n;
            }

            int desiredInsertBeforeRemoval =
                computeDesiredInsertBeforeRemoval(e, targetPath, targetNode);
            if (desiredInsertBeforeRemoval < 0) return null;

            desiredInsertBeforeRemoval =
                Math.max(
                    minInsertIndex(dragParent),
                    Math.min(maxInsertIndex(dragParent), desiredInsertBeforeRemoval));

            return ServerTreeDockable.this.insertionLineForIndex(
                dragParent, desiredInsertBeforeRemoval);
          }

          private int computeDesiredInsertBeforeRemoval(
              MouseEvent e, TreePath targetPath, DefaultMutableTreeNode targetNode) {
            if (dragNode == null || dragParent == null) return -1;

            if (targetNode == null) {
              return dragFromIndex;
            }

            if (targetNode == dragNode) {
              return -1;
            }

            if (targetNode == dragParent) {
              return minInsertIndex(dragParent);
            }

            DefaultMutableTreeNode targetParent = (DefaultMutableTreeNode) targetNode.getParent();
            if (targetParent != dragParent) {
              return -1;
            }

            int idx = dragParent.getIndex(targetNode);
            Rectangle r = targetPath == null ? null : tree.getPathBounds(targetPath);
            boolean after = r != null && e.getY() > (r.y + (r.height / 2));
            return idx + (after ? 1 : 0);
          }

          private void cleanup() {
            dragging = false;
            dragNode = null;
            dragParent = null;
            dragFromIndex = -1;
            draggedWasSelected = false;
            tree.setLeadSelectionPath(null);
            ServerTreeDockable.this.setInsertionLine(null);
            if (oldCursor != null) tree.setCursor(oldCursor);
            oldCursor = null;
          }

          private void performDrop(MouseEvent e) {
            if (dragNode == null || dragParent == null) return;

            TreePath targetPath = tree.getClosestPathForLocation(e.getX(), e.getY());
            DefaultMutableTreeNode targetNode = null;
            if (targetPath != null) {
              Object o = targetPath.getLastPathComponent();
              if (o instanceof DefaultMutableTreeNode n) targetNode = n;
            }

            int desiredInsertBeforeRemoval = dragFromIndex;

            if (targetNode == null) {
              desiredInsertBeforeRemoval = dragFromIndex;
            } else if (targetNode == dragNode) {
              return;
            } else if (targetNode == dragParent) {
              desiredInsertBeforeRemoval = minInsertIndex(dragParent);
            } else {
              DefaultMutableTreeNode targetParent = (DefaultMutableTreeNode) targetNode.getParent();
              if (targetParent != dragParent) {
                return;
              }

              int idx = dragParent.getIndex(targetNode);
              Rectangle r = tree.getPathBounds(targetPath);
              boolean after = r != null && e.getY() > (r.y + (r.height / 2));
              desiredInsertBeforeRemoval = idx + (after ? 1 : 0);
            }
            desiredInsertBeforeRemoval =
                Math.max(
                    minInsertIndex(dragParent),
                    Math.min(maxInsertIndex(dragParent), desiredInsertBeforeRemoval));
            int desiredAfterRemoval = desiredInsertBeforeRemoval;
            if (desiredAfterRemoval > dragFromIndex) desiredAfterRemoval--;
            if (desiredAfterRemoval == dragFromIndex) return;
            model.removeNodeFromParent(dragNode);
            desiredAfterRemoval =
                Math.max(
                    minInsertIndex(dragParent),
                    Math.min(maxInsertIndex(dragParent), desiredAfterRemoval));

            model.insertNodeInto(dragNode, dragParent, desiredAfterRemoval);
            if (draggedWasSelected) {
              suppressSelectionBroadcast = true;
              try {
                TreePath np = new TreePath(dragNode.getPath());
                tree.setSelectionPath(np);
              } finally {
                suppressSelectionBroadcast = false;
              }
            }

            TreePath moved = new TreePath(dragNode.getPath());
            tree.scrollPathToVisible(moved);
            nodeActions.refreshEnabledState();
            if (isChannelListLeafNode(dragParent)) {
              String sid = owningServerIdForNode(dragParent);
              if (channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM)
                  == ChannelSortMode.CUSTOM) {
                persistCustomOrderFromTree(sid);
              }
            }
          }
        };

    tree.addMouseListener(middleDragReorder);
    tree.addMouseMotionListener(middleDragReorder);
    SwingUtilities.invokeLater(
        () -> {
          String firstServerId =
              servers.values().stream().findFirst().map(sn -> sn.statusRef.serverId()).orElse("");
          if (!firstServerId.isBlank()) {
            selectBestFallbackForServer(firstServerId);
          } else {
            tree.setSelectionPath(defaultSelectionPath());
          }
        });
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
    if (runtimeConfig == null) return;
    try {
      Map<String, RuntimeConfigStore.ServerTreeBuiltInNodesVisibility> persisted =
          runtimeConfig.readServerTreeBuiltInNodesVisibility();
      if (persisted == null || persisted.isEmpty()) return;
      for (Map.Entry<String, RuntimeConfigStore.ServerTreeBuiltInNodesVisibility> entry :
          persisted.entrySet()) {
        String sid = normalizeServerId(entry.getKey());
        if (sid.isEmpty()) continue;
        RuntimeConfigStore.ServerTreeBuiltInNodesVisibility raw = entry.getValue();
        ServerBuiltInNodesVisibility parsed =
            (raw == null)
                ? defaultBuiltInNodesVisibility
                : new ServerBuiltInNodesVisibility(
                    raw.server(),
                    raw.notifications(),
                    raw.logViewer(),
                    raw.monitor(),
                    raw.interceptors());
        if (parsed.equals(defaultBuiltInNodesVisibility)) {
          builtInNodesVisibilityByServer.remove(sid);
        } else {
          builtInNodesVisibilityByServer.put(sid, parsed);
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return defaultBuiltInNodesVisibility;
    return builtInNodesVisibilityByServer.getOrDefault(sid, defaultBuiltInNodesVisibility);
  }

  private void applyBuiltInNodesVisibilityGlobally(
      java.util.function.UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
    if (mutator == null) return;
    Set<String> allServerIds = new HashSet<>(servers.keySet());
    allServerIds.addAll(builtInNodesVisibilityByServer.keySet());
    for (String sid : allServerIds) {
      if (sid == null || sid.isBlank()) continue;
      ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
      ServerBuiltInNodesVisibility next =
          Objects.requireNonNullElse(mutator.apply(current), current);
      applyBuiltInNodesVisibilityForServer(sid, next, true, false);
    }
    syncUiLeafVisibility();
  }

  private void applyBuiltInNodesVisibilityForServer(
      String serverId, ServerBuiltInNodesVisibility next, boolean persist, boolean syncUi) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || next == null) return;

    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    if (current.equals(next)) return;

    if (next.equals(defaultBuiltInNodesVisibility)) {
      builtInNodesVisibilityByServer.remove(sid);
    } else {
      builtInNodesVisibilityByServer.put(sid, next);
    }

    if (persist && runtimeConfig != null) {
      runtimeConfig.rememberServerTreeBuiltInNodesVisibility(sid, next.toRuntimeVisibility());
    }

    if (syncUi) {
      syncUiLeafVisibility();
    }
  }

  private ConnectionState connectionStateForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return ConnectionState.DISCONNECTED;
    return serverStates.getOrDefault(sid, ConnectionState.DISCONNECTED);
  }

  private boolean desiredOnlineForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return Boolean.TRUE.equals(serverDesiredOnline.get(sid));
  }

  private static boolean canConnectServer(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return st == ConnectionState.DISCONNECTED;
  }

  private static boolean canDisconnectServer(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return st == ConnectionState.CONNECTED || st == ConnectionState.RECONNECTING;
  }

  private static String serverStateLabel(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case CONNECTED -> "Connected";
      case CONNECTING -> "Connecting";
      case RECONNECTING -> "Reconnecting";
      case DISCONNECTING -> "Disconnecting";
      case DISCONNECTED -> "Disconnected";
    };
  }

  private static String serverDesiredIntentLabel(boolean desiredOnline) {
    return desiredOnline ? "Online" : "Offline";
  }

  private static boolean isOnlineState(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return st == ConnectionState.CONNECTED
        || st == ConnectionState.CONNECTING
        || st == ConnectionState.RECONNECTING;
  }

  private static String serverDesiredBadge(ConnectionState state, boolean desiredOnline) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    boolean online = isOnlineState(st);
    if (desiredOnline && !online) {
      if (st == ConnectionState.DISCONNECTING) return " [connect queued]";
      return " [wanted online]";
    }
    if (!desiredOnline && online) {
      return " [disconnect queued]";
    }
    return "";
  }

  private static String serverIntentQueueTip(ConnectionState state, boolean desiredOnline) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    boolean online = isOnlineState(st);
    if (desiredOnline && st == ConnectionState.DISCONNECTING) {
      return "Connect is queued until the current disconnect finishes.";
    }
    if (desiredOnline && st == ConnectionState.DISCONNECTED) {
      return "Wanted online; waiting for a successful connect attempt.";
    }
    if (!desiredOnline && online) {
      return "Disconnect is queued.";
    }
    return "";
  }

  private static String serverActionHint(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return canConnectServer(st)
        ? "Click the row action to connect."
        : canDisconnectServer(st)
            ? "Click the row action to disconnect."
            : "Connection state is changing.";
  }

  private String serverNodeDisplayLabel(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return sid;
    String base = prettyServerLabel(sid);
    ConnectionState state = connectionStateForServer(sid);
    boolean desired = desiredOnlineForServer(sid);
    String badge = serverDesiredBadge(state, desired);
    return badge.isEmpty() ? base : (base + badge);
  }

  private String connectionDiagnosticsTipForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return "";

    String err = Objects.toString(serverLastError.getOrDefault(sid, ""), "").trim();
    Long retryAt = serverNextRetryAtEpochMs.get(sid);

    StringBuilder out = new StringBuilder();
    if (!err.isEmpty()) {
      out.append(" Last error: ").append(err).append(".");
    }
    if (retryAt != null && retryAt > 0) {
      long deltaMs = retryAt - System.currentTimeMillis();
      if (deltaMs <= 0) {
        out.append(" Next retry: imminent.");
      } else {
        long sec = Math.max(1L, deltaMs / 1000L);
        out.append(" Next retry in ").append(sec).append("s.");
      }
    }
    return out.toString();
  }

  private static String serverNodeIconName(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case CONNECTED -> "check";
      case CONNECTING, RECONNECTING, DISCONNECTING -> "refresh";
      case DISCONNECTED -> "terminal";
    };
  }

  private static Palette serverNodeIconPalette(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case CONNECTED, CONNECTING, RECONNECTING -> Palette.TREE;
      case DISCONNECTED, DISCONNECTING -> Palette.TREE_DISABLED;
    };
  }

  private static String serverActionIconName(ConnectionState state) {
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    return switch (st) {
      case DISCONNECTED -> "plus";
      case CONNECTED, RECONNECTING -> "exit";
      case CONNECTING, DISCONNECTING -> "refresh";
    };
  }

  private String selectedServerActionServerId() {
    TreePath selected = tree.getSelectionPath();
    if (selected == null) return "";
    Object last = selected.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node) || !isServerNode(node)) return "";
    return Objects.toString(node.getUserObject(), "").trim();
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

  private TreePath serverPathForId(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;
    ServerNodes sn = servers.get(sid);
    if (sn == null || sn.serverNode == null || sn.serverNode.getPath() == null) return null;
    return new TreePath(sn.serverNode.getPath());
  }

  private Rectangle serverActionButtonBoundsForPath(TreePath path) {
    if (path == null) return null;
    Rectangle row = tree.getPathBounds(path);
    if (row == null) return null;
    Rectangle vr = tree.getVisibleRect();
    if (vr == null || vr.isEmpty()) return null;
    if (row.y + row.height < vr.y || row.y > vr.y + vr.height) return null;

    int size = SERVER_ACTION_BUTTON_SIZE;
    int x = vr.x + vr.width - SERVER_ACTION_BUTTON_MARGIN - size;
    int y = row.y + Math.max(0, (row.height - size) / 2);
    if (x < vr.x + SERVER_ACTION_BUTTON_MARGIN) x = vr.x + SERVER_ACTION_BUTTON_MARGIN;
    return new Rectangle(x, y, size, size);
  }

  private Rectangle serverActionButtonBoundsForServer(String serverId) {
    return serverActionButtonBoundsForPath(serverPathForId(serverId));
  }

  private void updateHoveredServerAction(MouseEvent event) {
    String next = "";
    if (event != null) {
      next = serverIdAt(event.getX(), event.getY());
      if (next.isEmpty()) {
        String current = Objects.toString(hoveredServerActionServerId, "").trim();
        if (!current.isEmpty()) {
          Rectangle btn = serverActionButtonBoundsForServer(current);
          if (btn != null && btn.contains(event.getPoint())) {
            next = current;
          }
        }
      }
    }

    if (Objects.equals(next, hoveredServerActionServerId)) {
      if (!next.isEmpty()) {
        Rectangle btn = serverActionButtonBoundsForServer(next);
        if (btn != null) tree.repaint(btn);
      }
      return;
    }

    hoveredServerActionServerId = next;
    tree.repaint();
  }

  private String serverActionServerIdAtPoint(MouseEvent event) {
    if (event == null) return "";

    String sid = serverIdAt(event.getX(), event.getY());
    if (!sid.isEmpty()) {
      Rectangle btn = serverActionButtonBoundsForServer(sid);
      if (btn != null && btn.contains(event.getPoint())) {
        return sid;
      }
    }

    String selected = selectedServerActionServerId();
    if (!selected.isEmpty()) {
      Rectangle btn = serverActionButtonBoundsForServer(selected);
      if (btn != null && btn.contains(event.getPoint())) {
        return selected;
      }
    }
    return "";
  }

  private boolean maybeHandleHoveredServerActionClick(MouseEvent event) {
    if (event == null) return false;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;

    String sid = serverActionServerIdAtPoint(event);
    if (sid.isEmpty()) return false;

    Rectangle btn = serverActionButtonBoundsForServer(sid);
    if (btn == null || !btn.contains(event.getPoint())) return false;

    ConnectionState state = connectionStateForServer(sid);
    if (canConnectServer(state)) {
      connectServerRequests.onNext(sid);
    } else if (canDisconnectServer(state)) {
      disconnectServerRequests.onNext(sid);
    }

    event.consume();
    tree.repaint(btn);
    return true;
  }

  private boolean maybeHandleDetachedWarningClick(MouseEvent event) {
    if (event == null || event.isConsumed()) return false;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;

    TreePath path = treePathForRowHit(event.getX(), event.getY());
    if (path == null) return false;
    Object comp = path.getLastPathComponent();
    if (!(comp instanceof DefaultMutableTreeNode node)) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd) || nd.ref == null || !nd.hasDetachedWarning()) return false;

    Rectangle warningBounds = detachedWarningIndicatorBounds(path, node);
    if (warningBounds == null || !warningBounds.contains(event.getPoint())) return false;

    clearChannelDetachedWarning(nd.ref);
    event.consume();
    tree.repaint(warningBounds);
    return true;
  }

  private Rectangle detachedWarningIndicatorBounds(TreePath path, DefaultMutableTreeNode node) {
    if (path == null || node == null) return null;
    Rectangle rowBounds = tree.getPathBounds(path);
    if (rowBounds == null) return null;

    TreePath selectedPath = tree.getSelectionPath();
    boolean selected = Objects.equals(selectedPath, path);
    boolean expanded = tree.isExpanded(path);
    boolean leaf = node.isLeaf();
    int row = tree.getRowForPath(path);
    java.awt.Component rendered =
        tree.getCellRenderer()
            .getTreeCellRendererComponent(tree, node, selected, expanded, leaf, row, false);
    if (!(rendered instanceof JComponent jc)) return null;

    java.awt.Insets insets = jc.getInsets();
    int leftInset = insets != null ? insets.left : 0;
    int slotWidth = typingSlotWidthForStyle(typingIndicatorStyle);
    int slotLeft = rowBounds.x + Math.max(0, leftInset - slotWidth - 1);
    return new Rectangle(slotLeft, rowBounds.y, slotWidth, rowBounds.height);
  }

  private static int typingSlotWidthForStyle(TreeTypingIndicatorStyle style) {
    return Math.max(TYPING_ACTIVITY_LEFT_SLOT_WIDTH, typingIndicatorWidthForStyle(style) + 2);
  }

  private static int typingIndicatorWidthForStyle(TreeTypingIndicatorStyle style) {
    return switch (style) {
      case KEYBOARD -> 10;
      case GLOW_DOT -> 8;
      case DOTS ->
          TYPING_ACTIVITY_DOT_COUNT * TYPING_ACTIVITY_DOT_SIZE
              + (TYPING_ACTIVITY_DOT_COUNT - 1) * TYPING_ACTIVITY_DOT_GAP;
    };
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

  private void paintVisibleServerActions(Graphics g) {
    String selected = selectedServerActionServerId();
    if (!selected.isEmpty()) {
      paintServerAction(g, selected);
    }
    String hovered = Objects.toString(hoveredServerActionServerId, "").trim();
    if (!hovered.isEmpty() && !Objects.equals(hovered, selected)) {
      paintServerAction(g, hovered);
    }
  }

  private void paintServerAction(Graphics g, String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    Rectangle btn = serverActionButtonBoundsForServer(sid);
    if (btn == null) return;

    ConnectionState state = connectionStateForServer(sid);
    boolean enabled = canConnectServer(state) || canDisconnectServer(state);

    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Color base = UIManager.getColor("Button.background");
      if (base == null) base = UIManager.getColor("Panel.background");
      if (base == null) base = Color.LIGHT_GRAY;

      Color border = UIManager.getColor("Component.borderColor");
      if (border == null) border = UIManager.getColor("Separator.foreground");
      if (border == null) border = Color.GRAY;

      java.awt.Point mp = null;
      try {
        mp = tree.getMousePosition();
      } catch (Exception ignored) {
      }
      boolean hot = mp != null && btn.contains(mp);

      Color fill = withAlpha(base, enabled ? 220 : 170);
      if (hot && enabled) {
        Color accent = UIManager.getColor("@accentColor");
        if (accent == null) accent = UIManager.getColor("Component.focusColor");
        if (accent != null) {
          fill = withAlpha(accent, 64);
          border = withAlpha(accent, 185);
        } else {
          fill = withAlpha(base, 240);
        }
      }

      g2.setColor(fill);
      g2.fillRoundRect(btn.x, btn.y, btn.width, btn.height, 8, 8);
      g2.setColor(withAlpha(border, 200));
      g2.drawRoundRect(btn.x, btn.y, btn.width - 1, btn.height - 1, 8, 8);

      Icon actionIcon =
          SvgIcons.icon(
              serverActionIconName(state),
              SERVER_ACTION_BUTTON_ICON_SIZE,
              enabled ? Palette.ACTION : Palette.ACTION_DISABLED);
      if (actionIcon != null) {
        int ix = btn.x + (btn.width - actionIcon.getIconWidth()) / 2;
        int iy = btn.y + (btn.height - actionIcon.getIconHeight()) / 2;
        actionIcon.paintIcon(tree, g2, ix, iy);
      }
    } finally {
      g2.dispose();
    }
  }

  private static Color withAlpha(Color c, int alpha) {
    Color base = c == null ? Color.GRAY : c;
    int a = Math.max(0, Math.min(255, alpha));
    return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
  }

  private JPopupMenu buildPopupMenu(TreePath path) {
    if (path == null) return null;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (node == null) return null;
    if (isServerNode(node)) {
      String serverId = Objects.toString(node.getUserObject(), "").trim();
      if (serverId.isEmpty()) return null;

      String pretty = prettyServerLabel(serverId);

      ConnectionState state = connectionStateForServer(serverId);
      JPopupMenu menu = new JPopupMenu();
      boolean canReorder = isRootServerNode(node);
      if (canReorder) {
        menu.add(new JMenuItem(moveNodeUpAction()));
        menu.add(new JMenuItem(moveNodeDownAction()));
        menu.addSeparator();
      }

      JMenuItem connectOne = new JMenuItem("Connect \"" + pretty + "\"");
      connectOne.setIcon(SvgIcons.action("plus", 16));
      connectOne.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
      connectOne.setEnabled(canConnectServer(state));
      connectOne.addActionListener(ev -> connectServerRequests.onNext(serverId));
      menu.add(connectOne);

      JMenuItem disconnectOne = new JMenuItem("Disconnect \"" + pretty + "\"");
      disconnectOne.setIcon(SvgIcons.action("exit", 16));
      disconnectOne.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
      disconnectOne.setEnabled(canDisconnectServer(state));
      disconnectOne.addActionListener(ev -> disconnectServerRequests.onNext(serverId));
      menu.add(disconnectOne);

      JMenuItem networkInfo = new JMenuItem("View Network Info...");
      networkInfo.setIcon(SvgIcons.action("info", 16));
      networkInfo.setDisabledIcon(SvgIcons.actionDisabled("info", 16));
      networkInfo.addActionListener(ev -> openServerInfoDialog(serverId));
      menu.add(networkInfo);

      menu.addSeparator();
      JMenuItem addInterceptor = new JMenuItem("Add Interceptor...");
      addInterceptor.setIcon(SvgIcons.action("plus", 16));
      addInterceptor.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
      addInterceptor.setEnabled(interceptorStore != null);
      addInterceptor.addActionListener(ev -> promptAndAddInterceptor(serverId));
      menu.add(addInterceptor);

      // Ephemeral servers can be promoted to persisted servers. This is especially useful for
      // bouncer-discovered networks that would otherwise disappear when the bouncer disconnects.
      boolean ephemeral =
          serverCatalog != null
              && serverCatalog.findEntry(serverId).map(ServerEntry::ephemeral).orElse(false);
      if (ephemeral) {
        menu.addSeparator();
        JMenuItem save = new JMenuItem("Save \"" + pretty + "\"…");
        save.setIcon(SvgIcons.action("plus", 16));
        save.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
        save.setEnabled(serverDialogs != null);
        save.addActionListener(
            ev -> {
              if (serverDialogs == null) return;
              Window w = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
              serverDialogs.openSaveEphemeralServer(w, serverId);
            });
        menu.add(save);
      }

      // Only show server editing for the primary, configured server entries directly under the IRC
      // branch.
      if (canReorder) {
        boolean editable =
            serverDialogs != null
                && serverCatalog != null
                && serverCatalog.findEntry(serverId).map(se -> !se.ephemeral()).orElse(false);

        menu.addSeparator();
        JMenuItem edit = new JMenuItem("Edit \"" + pretty + "\"…");
        edit.setIcon(SvgIcons.action("edit", 16));
        edit.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
        edit.setEnabled(editable);
        edit.addActionListener(
            ev -> {
              Window w = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
              serverDialogs.openEditServer(w, serverId);
            });
        menu.add(edit);
      }

      if (isSojuEphemeralServer(serverId)) {
        String originId = sojuOriginByServerId.get(serverId);
        String networkKey = serverDisplayNames.getOrDefault(serverId, serverId);
        boolean enabled =
            originId != null
                && sojuAutoConnect != null
                && sojuAutoConnect.isEnabled(originId, networkKey);

        menu.addSeparator();
        JCheckBoxMenuItem auto =
            new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
        auto.setSelected(enabled);
        auto.setEnabled(originId != null && !originId.isBlank() && sojuAutoConnect != null);
        auto.addActionListener(
            ev -> {
              if (originId == null || originId.isBlank() || sojuAutoConnect == null) return;
              boolean en = auto.isSelected();
              sojuAutoConnect.setEnabled(originId, networkKey, en);
              refreshSojuAutoConnectBadges();
            });
        menu.add(auto);
      }

      if (isZncEphemeralServer(serverId)) {
        String originId = zncOriginByServerId.get(serverId);
        String networkKey = serverDisplayNames.getOrDefault(serverId, serverId);
        boolean enabled =
            originId != null
                && zncAutoConnect != null
                && zncAutoConnect.isEnabled(originId, networkKey);

        menu.addSeparator();
        JCheckBoxMenuItem auto =
            new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
        auto.setSelected(enabled);
        auto.setEnabled(originId != null && !originId.isBlank() && zncAutoConnect != null);
        auto.addActionListener(
            ev -> {
              if (originId == null || originId.isBlank() || zncAutoConnect == null) return;
              boolean en = auto.isSelected();
              zncAutoConnect.setEnabled(originId, networkKey, en);
              refreshZncAutoConnectBadges();
            });
        menu.add(auto);
      }

      return menu;
    }

    if (isInterceptorsGroupNode(node)) {
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
      if (!isServerNode(parent)) return null;
      String serverId = Objects.toString(parent.getUserObject(), "").trim();
      if (serverId.isEmpty()) return null;

      JPopupMenu menu = new JPopupMenu();
      JMenuItem addInterceptor = new JMenuItem("Add Interceptor...");
      addInterceptor.setIcon(SvgIcons.action("plus", 16));
      addInterceptor.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
      addInterceptor.setEnabled(interceptorStore != null);
      addInterceptor.addActionListener(ev -> promptAndAddInterceptor(serverId));
      menu.add(addInterceptor);
      return menu;
    }

    Object uo = node.getUserObject();
    if (uo instanceof NodeData nd) {
      if (nd.ref != null) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openDock = new JMenuItem("Open chat dock");
        openDock.addActionListener(ev -> openPinnedChatRequests.onNext(nd.ref));
        menu.add(openDock);

        menu.addSeparator();
        menu.add(new JMenuItem(moveNodeUpAction()));
        menu.add(new JMenuItem(moveNodeDownAction()));

        if (nd.ref.isChannel() || nd.ref.isStatus()) {
          menu.addSeparator();
          JMenuItem clearLog = new JMenuItem("Clear Log…");
          clearLog.addActionListener(ev -> confirmAndRequestClearLog(nd.ref, nd.label));
          menu.add(clearLog);
        }

        if (!nd.ref.isStatus() && !nd.ref.isUiOnly()) {
          menu.addSeparator();
          if (nd.ref.isChannel()) {
            boolean detached = isChannelDetached(nd.ref);
            if (detached) {
              JMenuItem join = new JMenuItem("Join \"" + nd.label + "\"");
              join.addActionListener(ev -> joinChannelRequests.onNext(nd.ref));
              menu.add(join);
            } else {
              JMenuItem detach = new JMenuItem("Detach \"" + nd.label + "\"");
              detach.addActionListener(ev -> detachChannelRequests.onNext(nd.ref));
              menu.add(detach);
            }
            JCheckBoxMenuItem autoReattach = new JCheckBoxMenuItem("Auto-reattach on startup");
            autoReattach.setSelected(isChannelAutoReattach(nd.ref));
            autoReattach.addActionListener(
                ev -> setChannelAutoReattach(nd.ref, autoReattach.isSelected()));
            menu.add(autoReattach);
            JMenuItem closeChannel = new JMenuItem("Close Channel \"" + nd.label + "\"");
            closeChannel.addActionListener(ev -> closeChannelRequests.onNext(nd.ref));
            menu.add(closeChannel);
          } else {
            JMenuItem close = new JMenuItem("Close \"" + nd.label + "\"");
            close.addActionListener(ev -> closeTargetRequests.onNext(nd.ref));
            menu.add(close);
          }
        }

        if (nd.ref.isInterceptor()) {
          menu.addSeparator();
          String sid = Objects.toString(nd.ref.serverId(), "").trim();
          String iid = Objects.toString(nd.ref.interceptorId(), "").trim();
          InterceptorDefinition def =
              interceptorStore != null && !sid.isEmpty() && !iid.isEmpty()
                  ? interceptorStore.interceptor(sid, iid)
                  : null;
          boolean currentlyEnabled = def == null || def.enabled();

          JMenuItem toggleEnabled =
              new JMenuItem(currentlyEnabled ? "Disable Interceptor" : "Enable Interceptor");
          toggleEnabled.setIcon(SvgIcons.action(currentlyEnabled ? "pause" : "check", 16));
          toggleEnabled.setDisabledIcon(
              SvgIcons.actionDisabled(currentlyEnabled ? "pause" : "check", 16));
          toggleEnabled.setEnabled(interceptorStore != null && def != null);
          toggleEnabled.addActionListener(ev -> setInterceptorEnabled(nd.ref, !currentlyEnabled));
          menu.add(toggleEnabled);

          JMenuItem rename = new JMenuItem("Rename Interceptor...");
          rename.setIcon(SvgIcons.action("edit", 16));
          rename.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
          rename.setEnabled(interceptorStore != null);
          rename.addActionListener(ev -> promptRenameInterceptor(nd.ref, nd.label));
          menu.add(rename);

          JMenuItem delete = new JMenuItem("Delete Interceptor...");
          delete.setIcon(SvgIcons.action("exit", 16));
          delete.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
          delete.setEnabled(interceptorStore != null);
          delete.addActionListener(ev -> confirmDeleteInterceptor(nd.ref, nd.label));
          menu.add(delete);
        }

        return menu;
      }
    }

    return null;
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
      clearLogRequests.onNext(target);
    }
  }

  private void promptAndAddInterceptor(String serverId) {
    if (interceptorStore == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    Window w = SwingUtilities.getWindowAncestor(this);
    Object input =
        JOptionPane.showInputDialog(
            w,
            "Interceptor name:",
            "Add Interceptor",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            "Interceptor");
    if (input == null) return;

    String requested = Objects.toString(input, "").trim();
    if (requested.isEmpty()) return;

    try {
      InterceptorDefinition def = interceptorStore.createInterceptor(sid, requested);
      TargetRef ref = TargetRef.interceptor(sid, def.id());
      ensureNode(ref);
      refreshInterceptorNodeLabel(sid, def.id());
      refreshInterceptorGroupCount(sid);
      selectTarget(ref);
    } catch (Exception ex) {
      log.warn("[ircafe] could not add interceptor for server {}", sid, ex);
    }
  }

  private void promptRenameInterceptor(TargetRef ref, String currentLabel) {
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = Objects.toString(ref.serverId(), "").trim();
    String iid = Objects.toString(ref.interceptorId(), "").trim();
    if (sid.isEmpty() || iid.isEmpty()) return;

    Window w = SwingUtilities.getWindowAncestor(this);
    String before = Objects.toString(currentLabel, "").trim();
    if (before.isEmpty()) before = interceptorStore.interceptorName(sid, iid);
    if (before.isEmpty()) before = "Interceptor";

    Object input =
        JOptionPane.showInputDialog(
            w,
            "Interceptor name:",
            "Rename Interceptor",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            before);
    if (input == null) return;
    String next = Objects.toString(input, "").trim();
    if (next.isEmpty()) return;

    try {
      if (interceptorStore.renameInterceptor(sid, iid, next)) {
        refreshInterceptorNodeLabel(sid, iid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not rename interceptor {} on {}", iid, sid, ex);
    }
  }

  private void setInterceptorEnabled(TargetRef ref, boolean enabled) {
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = Objects.toString(ref.serverId(), "").trim();
    String iid = Objects.toString(ref.interceptorId(), "").trim();
    if (sid.isEmpty() || iid.isEmpty()) return;

    try {
      if (interceptorStore.setInterceptorEnabled(sid, iid, enabled)) {
        refreshInterceptorNodeLabel(sid, iid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not set interceptor enabled={} for {} on {}", enabled, iid, sid, ex);
    }
  }

  private void confirmDeleteInterceptor(TargetRef ref, String label) {
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;
    String sid = Objects.toString(ref.serverId(), "").trim();
    String iid = Objects.toString(ref.interceptorId(), "").trim();
    if (sid.isEmpty() || iid.isEmpty()) return;

    String pretty = Objects.toString(label, "").trim();
    if (pretty.isEmpty()) pretty = interceptorStore.interceptorName(sid, iid);
    if (pretty.isEmpty()) pretty = "Interceptor";

    Window w = SwingUtilities.getWindowAncestor(this);
    int choice =
        JOptionPane.showConfirmDialog(
            w,
            "Delete interceptor \"" + pretty + "\"?",
            "Delete Interceptor",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) return;

    try {
      if (interceptorStore.removeInterceptor(sid, iid)) {
        selectTarget(new TargetRef(sid, "status"));
        removeTarget(ref);
        refreshInterceptorGroupCount(sid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not delete interceptor {} on {}", iid, sid, ex);
    }
  }

  private void refreshInterceptorNodeLabel(String serverId, String interceptorId) {
    if (interceptorStore == null) return;
    String sid = Objects.toString(serverId, "").trim();
    String iid = Objects.toString(interceptorId, "").trim();
    if (sid.isEmpty() || iid.isEmpty()) return;

    TargetRef ref = TargetRef.interceptor(sid, iid);
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData prev) || prev.ref == null) return;

    String nextLabel = Objects.toString(interceptorStore.interceptorName(sid, iid), "").trim();
    if (nextLabel.isEmpty()) nextLabel = "Interceptor";

    NodeData next = new NodeData(prev.ref, nextLabel);
    next.unread = prev.unread;
    next.highlightUnread = prev.highlightUnread;
    next.detached = prev.detached;
    next.detachedWarning = prev.detachedWarning;
    next.copyTypingFrom(prev);
    if (!Objects.equals(prev.label, nextLabel)) {
      node.setUserObject(next);
    }
    model.nodeChanged(node);
  }

  private void refreshInterceptorGroupCount(String serverId) {
    if (interceptorStore == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    ServerNodes sn = servers.get(sid);
    if (sn == null || sn.interceptorsNode == null) return;

    Object uo = sn.interceptorsNode.getUserObject();
    NodeData nd;
    if (uo instanceof NodeData existing) {
      nd = existing;
    } else {
      nd = new NodeData(null, INTERCEPTORS_GROUP_LABEL);
      sn.interceptorsNode.setUserObject(nd);
    }

    int total = Math.max(0, interceptorStore.totalHitCount(sid));
    if (nd.unread == total && nd.highlightUnread == 0) return;
    nd.unread = total;
    nd.highlightUnread = 0;
    model.nodeChanged(sn.interceptorsNode);
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

  private boolean isDraggableChannelNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd)) return false;
    if (nd.ref == null || !nd.ref.isChannel()) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    return parent != null && (isServerNode(parent) || isChannelListLeafNode(parent));
  }

  private int minInsertIndex(DefaultMutableTreeNode parentNode) {
    if (parentNode == null) return 0;
    if (isChannelListLeafNode(parentNode)) return 0;

    int min = 0;
    int count = parentNode.getChildCount();
    while (min < count) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parentNode.getChildAt(min);
      Object uo = child.getUserObject();
      if (uo instanceof NodeData nd) {
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
    DefaultMutableTreeNode cur = node;
    while (cur != null) {
      if (isServerNode(cur)) {
        Object uo = cur.getUserObject();
        if (uo instanceof String sid) return sid.trim();
      }
      javax.swing.tree.TreeNode parent = cur.getParent();
      cur = (parent instanceof DefaultMutableTreeNode dmtn) ? dmtn : null;
    }
    return "";
  }

  private boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String s)) return false;
    String label = s.trim();
    return label.equalsIgnoreCase("Private messages") || label.equalsIgnoreCase("Private Messages");
  }

  private boolean isChannelListLeafNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd)) return false;
    return nd.ref != null && nd.ref.isChannelList();
  }

  private boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (uo instanceof String s) {
      return INTERCEPTORS_GROUP_LABEL.equalsIgnoreCase(s.trim());
    }
    if (uo instanceof NodeData nd) {
      if (nd.ref != null) return false;
      return INTERCEPTORS_GROUP_LABEL.equalsIgnoreCase(Objects.toString(nd.label, "").trim());
    }
    return false;
  }

  private boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (uo instanceof String s) {
      return MONITOR_GROUP_LABEL.equalsIgnoreCase(s.trim());
    }
    if (uo instanceof NodeData nd) {
      if (nd.ref != null) return false;
      return MONITOR_GROUP_LABEL.equalsIgnoreCase(Objects.toString(nd.label, "").trim());
    }
    return false;
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

  public Flowable<TargetRef> detachChannelRequests() {
    return detachChannelRequests.onBackpressureLatest();
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
      return snapshotOpenChannelsForServer(sid);
    }

    AtomicReference<List<String>> out = new AtomicReference<>(List.of());
    try {
      SwingUtilities.invokeAndWait(() -> out.set(snapshotOpenChannelsForServer(sid)));
      return out.get();
    } catch (Exception ex) {
      log.debug("[ircafe] open channel snapshot failed for server={}", sid, ex);
      return List.of();
    }
  }

  private List<String> snapshotOpenChannelsForServer(String serverId) {
    LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
    for (TargetRef ref : leaves.keySet()) {
      if (ref == null) continue;
      if (!Objects.equals(serverId, ref.serverId())) continue;
      if (!ref.isChannel()) continue;
      String target = Objects.toString(ref.target(), "").trim();
      if (target.isEmpty()) continue;
      String key = target.toLowerCase(java.util.Locale.ROOT);
      byKey.putIfAbsent(key, target);
    }
    if (byKey.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(byKey.values());
    out.sort(String.CASE_INSENSITIVE_ORDER);
    return List.copyOf(out);
  }

  public List<ManagedChannelEntry> managedChannelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    if (SwingUtilities.isEventDispatchThread()) {
      return snapshotManagedChannelsForServer(sid);
    }

    AtomicReference<List<ManagedChannelEntry>> out = new AtomicReference<>(List.of());
    try {
      SwingUtilities.invokeAndWait(() -> out.set(snapshotManagedChannelsForServer(sid)));
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
      return channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM);
    }

    AtomicReference<ChannelSortMode> out = new AtomicReference<>(ChannelSortMode.CUSTOM);
    try {
      SwingUtilities.invokeAndWait(
          () -> out.set(channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM)));
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

    Runnable apply =
        () -> {
          ChannelSortMode prev = channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM);
          if (prev == next) return;
          channelSortModeByServer.put(sid, next);
          if (runtimeConfig != null) {
            runtimeConfig.rememberServerTreeChannelSortMode(
                sid,
                next == ChannelSortMode.ALPHABETICAL
                    ? RuntimeConfigStore.ServerTreeChannelSortMode.ALPHABETICAL
                    : RuntimeConfigStore.ServerTreeChannelSortMode.CUSTOM);
          }
          sortChannelsUnderChannelList(sid);
          emitManagedChannelsChanged(sid);
        };

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

    Runnable apply =
        () -> {
          ArrayList<String> normalized = normalizeCustomOrderList(requested);
          channelCustomOrderByServer.put(sid, normalized);
          if (runtimeConfig != null) {
            runtimeConfig.rememberServerTreeChannelCustomOrder(sid, normalized);
          }
          if (channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM)
              == ChannelSortMode.CUSTOM) {
            sortChannelsUnderChannelList(sid);
          }
          emitManagedChannelsChanged(sid);
        };

    if (SwingUtilities.isEventDispatchThread()) {
      apply.run();
    } else {
      SwingUtilities.invokeLater(apply);
    }
  }

  public void requestJoinChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    joinChannelRequests.onNext(target);
  }

  public void requestDetachChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    detachChannelRequests.onNext(target);
  }

  public void requestCloseChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return;
    closeChannelRequests.onNext(target);
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
    if (serverId == null) return;
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    if (st == ConnectionState.DISCONNECTED) {
      serverStates.remove(serverId);
      clearPrivateMessageOnlineStates(serverId);
    } else {
      serverStates.put(serverId, st);
    }

    ServerNodes sn = servers.get(serverId);
    if (sn != null && sn.serverNode != null) {
      model.nodeChanged(sn.serverNode);
    }
    if (Objects.equals(hoveredServerActionServerId, serverId)) {
      tree.repaint();
    }
  }

  public void setServerDesiredOnline(String serverId, boolean desiredOnline) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    boolean prev = Boolean.TRUE.equals(serverDesiredOnline.get(sid));
    if (desiredOnline == prev) return;
    if (desiredOnline) {
      serverDesiredOnline.put(sid, Boolean.TRUE);
    } else {
      serverDesiredOnline.remove(sid);
    }

    ServerNodes sn = servers.get(sid);
    if (sn != null && sn.serverNode != null) {
      model.nodeChanged(sn.serverNode);
    }
    if (Objects.equals(hoveredServerActionServerId, sid)) {
      tree.repaint();
    }
  }

  public void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    String nextError = Objects.toString(lastError, "").trim();
    Long nextRetry = (nextRetryEpochMs == null || nextRetryEpochMs <= 0L) ? null : nextRetryEpochMs;

    String prevError = Objects.toString(serverLastError.getOrDefault(sid, ""), "").trim();
    Long prevRetry = serverNextRetryAtEpochMs.get(sid);
    if (Objects.equals(prevError, nextError) && Objects.equals(prevRetry, nextRetry)) return;

    if (nextError.isEmpty()) {
      serverLastError.remove(sid);
    } else {
      serverLastError.put(sid, nextError);
    }
    if (nextRetry == null) {
      serverNextRetryAtEpochMs.remove(sid);
    } else {
      serverNextRetryAtEpochMs.put(sid, nextRetry);
    }

    ServerNodes sn = servers.get(sid);
    if (sn != null && sn.serverNode != null) {
      model.nodeChanged(sn.serverNode);
    }
    tree.repaint();
  }

  public void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    ServerRuntimeMetadata meta = metadataForServer(sid);
    boolean changed = false;

    String host = Objects.toString(connectedHost, "").trim();
    if (!host.isEmpty() && !Objects.equals(meta.connectedHost, host)) {
      meta.connectedHost = host;
      changed = true;
    }

    if (connectedPort > 0 && meta.connectedPort != connectedPort) {
      meta.connectedPort = connectedPort;
      changed = true;
    }

    String n = Objects.toString(nick, "").trim();
    if (!n.isEmpty() && !Objects.equals(meta.nick, n)) {
      meta.nick = n;
      changed = true;
    }

    Instant connectedAt =
        at != null ? at : (meta.connectedAt == null ? Instant.now() : meta.connectedAt);
    if (!Objects.equals(meta.connectedAt, connectedAt)) {
      meta.connectedAt = connectedAt;
      changed = true;
    }

    if (changed) {
      ServerNodes sn = servers.get(sid);
      if (sn != null && sn.serverNode != null) {
        model.nodeChanged(sn.serverNode);
      }
    }
  }

  public void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {
    String sid = Objects.toString(serverId, "").trim();
    String cap = Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (sid.isEmpty() || cap.isEmpty()) return;

    String sub = Objects.toString(subcommand, "").trim().toUpperCase(java.util.Locale.ROOT);
    ServerRuntimeMetadata meta = metadataForServer(sid);
    boolean changed = false;

    CapabilityState next = null;
    if ("DEL".equals(sub)) {
      next = CapabilityState.REMOVED;
    } else if ("NEW".equals(sub) || "LS".equals(sub)) {
      next = CapabilityState.AVAILABLE;
    } else {
      next = enabled ? CapabilityState.ENABLED : CapabilityState.DISABLED;
    }

    CapabilityState prev = meta.ircv3Caps.put(cap, next);
    String effectiveSub = sub.isEmpty() ? "(unknown)" : sub;
    String prevSub = meta.ircv3CapLastSubcommand.put(cap, effectiveSub);
    boolean stateChanged = !Objects.equals(prev, next);
    boolean subChanged = !Objects.equals(prevSub, effectiveSub);
    if (stateChanged || subChanged) {
      meta.ircv3CapTransitions.add(
          new CapabilityTransition(Instant.now(), effectiveSub, cap, next));
      int overflow = meta.ircv3CapTransitions.size() - CAPABILITY_TRANSITION_LOG_LIMIT;
      if (overflow > 0) {
        meta.ircv3CapTransitions.subList(0, overflow).clear();
      }
      changed = true;
    }

    if (changed) {
      ServerNodes sn = servers.get(sid);
      if (sn != null && sn.serverNode != null) {
        model.nodeChanged(sn.serverNode);
      }
    }
  }

  public void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {
    String sid = Objects.toString(serverId, "").trim();
    String key = Objects.toString(tokenName, "").trim().toUpperCase(java.util.Locale.ROOT);
    if (sid.isEmpty() || key.isEmpty()) return;

    ServerRuntimeMetadata meta = metadataForServer(sid);
    String val = tokenValue != null ? tokenValue.trim() : null;
    if (val != null && val.isEmpty()) val = "";

    String prev;
    if (val == null) {
      prev = meta.isupport.remove(key);
    } else {
      prev = meta.isupport.put(key, val);
    }
    if (Objects.equals(prev, val)) return;

    ServerNodes sn = servers.get(sid);
    if (sn != null && sn.serverNode != null) {
      model.nodeChanged(sn.serverNode);
    }
  }

  public void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    ServerRuntimeMetadata meta = metadataForServer(sid);
    boolean changed = false;

    String srv = Objects.toString(serverName, "").trim();
    if (!srv.isEmpty() && !Objects.equals(meta.serverName, srv)) {
      meta.serverName = srv;
      changed = true;
    }

    String ver = Objects.toString(serverVersion, "").trim();
    if (!ver.isEmpty() && !Objects.equals(meta.serverVersion, ver)) {
      meta.serverVersion = ver;
      changed = true;
    }

    String um = Objects.toString(userModes, "").trim();
    if (!um.isEmpty() && !Objects.equals(meta.userModes, um)) {
      meta.userModes = um;
      changed = true;
    }

    String cm = Objects.toString(channelModes, "").trim();
    if (!cm.isEmpty() && !Objects.equals(meta.channelModes, cm)) {
      meta.channelModes = cm;
      changed = true;
    }

    if (changed) {
      ServerNodes sn = servers.get(sid);
      if (sn != null && sn.serverNode != null) {
        model.nodeChanged(sn.serverNode);
      }
    }
  }

  private ServerRuntimeMetadata metadataForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) sid = "(server)";
    return serverRuntimeMetadata.computeIfAbsent(sid, __ -> new ServerRuntimeMetadata());
  }

  private void openServerInfoDialog(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    ServerRuntimeMetadata meta = metadataForServer(sid);
    Window owner = SwingUtilities.getWindowAncestor(this);
    String title = "Network Info - " + prettyServerLabel(sid);

    JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

    JPanel body =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[][grow,fill]"));
    body.add(buildNetworkSummaryPanel(sid, meta), "growx");

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Overview", buildOverviewTab(sid, meta));
    tabs.addTab(
        "Capabilities (" + meta.ircv3Caps.size() + ")", buildCapabilitiesInfoPanel(sid, meta));
    tabs.addTab("ISUPPORT (" + meta.isupport.size() + ")", buildIsupportInfoPanel(meta));
    body.add(tabs, "grow, push");

    JScrollPane bodyScroll =
        new JScrollPane(
            body,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    bodyScroll.setBorder(BorderFactory.createEmptyBorder());
    bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
    bodyScroll.setPreferredSize(new Dimension(820, 470));

    JButton close = new JButton("Close");
    close.addActionListener(ev -> dialog.dispose());
    JPanel actions = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill][]", "[]"));
    actions.add(new JLabel(""), "growx");
    actions.add(close, "tag ok");

    JPanel content =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[grow,fill][]"));
    content.add(bodyScroll, "grow, push");
    content.add(actions, "growx");

    dialog.setContentPane(content);
    dialog.getRootPane().setDefaultButton(close);
    dialog.pack();
    int width = Math.max(820, dialog.getWidth());
    int height = Math.max(500, Math.min(560, dialog.getHeight()));
    dialog.setSize(width, height);
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private JPanel buildNetworkSummaryPanel(String serverId, ServerRuntimeMetadata meta) {
    JPanel panel =
        new JPanel(new MigLayout("insets 8, fillx, wrap 2", "[grow,fill][right]", "[]4[]"));
    panel.setBorder(BorderFactory.createTitledBorder("Summary"));

    ConnectionState state = connectionStateForServer(serverId);
    boolean desired = desiredOnlineForServer(serverId);

    JLabel title = new JLabel(prettyServerLabel(serverId));
    Font base = title.getFont();
    if (base != null) {
      title.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 1.5f));
    }
    panel.add(title, "growx");
    panel.add(new JLabel("State: " + serverStateLabel(state)));

    String endpoint = formatConnectedEndpoint(meta.connectedHost, meta.connectedPort);
    String nick = fallbackInfoValue(meta.nick);
    panel.add(new JLabel("Network ID: " + serverId), "span 2, growx");
    panel.add(
        new JLabel(
            "Endpoint: "
                + endpoint
                + "    Nick: "
                + nick
                + "    Intent: "
                + serverDesiredIntentLabel(desired)),
        "span 2, growx");
    return panel;
  }

  private JComponent buildOverviewTab(String serverId, ServerRuntimeMetadata meta) {
    JPanel overview =
        new JPanel(new MigLayout("insets 8, fill, wrap 2", "[grow,fill]12[grow,fill]", "[top]"));
    overview.add(buildConnectionInfoPanel(serverId, meta), "grow");
    overview.add(buildServerInfoPanel(meta), "grow");
    return overview;
  }

  private JPanel buildConnectionInfoPanel(String serverId, ServerRuntimeMetadata meta) {
    JPanel panel = new JPanel(new MigLayout("insets 8, fillx, wrap 2", "[right][grow,fill]"));
    panel.setBorder(BorderFactory.createTitledBorder("Connection"));

    ConnectionState state = connectionStateForServer(serverId);
    boolean desired = desiredOnlineForServer(serverId);

    addInfoRow(panel, "Network ID", serverId);
    addInfoRow(panel, "Display", prettyServerLabel(serverId));
    addInfoRow(panel, "State", serverStateLabel(state));
    addInfoRow(panel, "Intent", serverDesiredIntentLabel(desired));
    addInfoRow(
        panel,
        "Connected endpoint",
        formatConnectedEndpoint(meta.connectedHost, meta.connectedPort));
    addInfoRow(panel, "Current nick", fallbackInfoValue(meta.nick));
    addInfoRow(
        panel,
        "Connected at",
        meta.connectedAt == null ? "(unknown)" : SERVER_META_TIME_FMT.format(meta.connectedAt));

    String diagnostics = connectionDiagnosticsTipForServer(serverId).trim();
    if (!diagnostics.isEmpty()) {
      addInfoRow(panel, "Diagnostics", diagnostics);
    }
    return panel;
  }

  private JPanel buildServerInfoPanel(ServerRuntimeMetadata meta) {
    JPanel panel = new JPanel(new MigLayout("insets 8, fillx, wrap 2", "[right][grow,fill]"));
    panel.setBorder(BorderFactory.createTitledBorder("Server"));
    addInfoRow(panel, "Server name", fallbackInfoValue(meta.serverName));
    addInfoRow(panel, "Version", fallbackInfoValue(meta.serverVersion));
    addInfoRow(panel, "User modes", fallbackInfoValue(meta.userModes));
    addInfoRow(panel, "Channel modes", fallbackInfoValue(meta.channelModes));
    return panel;
  }

  private JPanel buildCapabilitiesInfoPanel(String serverId, ServerRuntimeMetadata meta) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 8, fill, wrap 1", "[grow,fill]", "[]6[]6[]6[grow,fill]6[grow,fill]"));
    panel.add(buildCapabilityCountsRow(meta), "growx");
    panel.add(new JLabel(capabilityStatusSummary(meta)), "growx");
    panel.add(
        new JLabel("Toggle Requested to send CAP REQ now and persist the startup preference."),
        "growx");

    TreeMap<String, CapabilityState> sortedObserved = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    sortedObserved.putAll(meta.ircv3Caps);
    java.util.TreeSet<String> allCapabilities =
        new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    allCapabilities.addAll(sortedObserved.keySet());
    for (String cap : PircbotxBotFactory.requestableCapabilities()) {
      String normalized = Objects.toString(cap, "").trim().toLowerCase(java.util.Locale.ROOT);
      if (!normalized.isEmpty()) allCapabilities.add(normalized);
    }

    if (allCapabilities.isEmpty()) {
      panel.add(new JLabel("No IRCv3 capabilities observed yet."), "grow");
      panel.add(buildCapabilityTransitionsPanel(meta), "grow");
      return panel;
    }

    Object[][] rows = new Object[allCapabilities.size()][4];
    int idx = 0;
    for (String capName : allCapabilities) {
      CapabilityState st = sortedObserved.get(capName);
      rows[idx][0] = capName;
      rows[idx][1] = st == null ? "(not seen)" : st.label;
      rows[idx][2] = isCapabilityRequested(capName);
      rows[idx][3] = fallbackInfoValue(meta.ircv3CapLastSubcommand.get(capName));
      idx++;
    }

    DefaultTableModel model =
        new DefaultTableModel(rows, new String[] {"Capability", "State", "Requested", "Last CAP"}) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return column == 2;
          }

          @Override
          public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 2) return Boolean.class;
            return String.class;
          }
        };
    model.addTableModelListener(
        e -> {
          if (e == null || e.getType() != TableModelEvent.UPDATE) return;
          if (e.getColumn() != 2 && e.getColumn() != TableModelEvent.ALL_COLUMNS) return;
          int from = Math.max(0, e.getFirstRow());
          int to = Math.max(from, e.getLastRow());
          for (int row = from; row <= to; row++) {
            String cap =
                Objects.toString(model.getValueAt(row, 0), "")
                    .trim()
                    .toLowerCase(java.util.Locale.ROOT);
            if (cap.isEmpty()) continue;
            boolean enable = Boolean.TRUE.equals(model.getValueAt(row, 2));
            ircv3CapabilityToggleRequests.onNext(
                new Ircv3CapabilityToggleRequest(serverId, cap, enable));
          }
        });

    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(true);
    table.setColumnSelectionAllowed(false);
    table.getTableHeader().setReorderingAllowed(false);
    JScrollPane scroll = new JScrollPane(table);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    panel.add(scroll, "grow");
    panel.add(buildCapabilityTransitionsPanel(meta), "grow");
    return panel;
  }

  private JComponent buildCapabilityTransitionsPanel(ServerRuntimeMetadata meta) {
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[][grow,fill]"));
    panel.setBorder(BorderFactory.createTitledBorder("Recent CAP transitions"));
    if (meta.ircv3CapTransitions.isEmpty()) {
      panel.add(new JLabel("No CAP transitions observed yet."), "growx");
      return panel;
    }

    int size = meta.ircv3CapTransitions.size();
    int start = Math.max(0, size - 80);
    Object[][] rows = new Object[size - start][4];
    int out = 0;
    for (int i = size - 1; i >= start; i--) {
      CapabilityTransition t = meta.ircv3CapTransitions.get(i);
      rows[out][0] = CAP_TRANSITION_TIME_FMT.format(t.at());
      rows[out][1] = t.subcommand();
      rows[out][2] = t.capability();
      rows[out][3] = t.state().label;
      out++;
    }

    JTable table = buildReadOnlyTable(new String[] {"Time", "CAP", "Capability", "State"}, rows);
    JScrollPane scroll = new JScrollPane(table);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    panel.add(scroll, "grow");
    return panel;
  }

  private JPanel buildIsupportInfoPanel(ServerRuntimeMetadata meta) {
    JPanel panel =
        new JPanel(new MigLayout("insets 8, fill, wrap 1", "[grow,fill]", "[grow,fill]"));
    if (meta.isupport.isEmpty()) {
      panel.add(new JLabel("No ISUPPORT tokens observed yet."), "grow");
      return panel;
    }

    TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    sorted.putAll(meta.isupport);
    Object[][] rows = new Object[sorted.size()][2];
    int idx = 0;
    for (Map.Entry<String, String> e : sorted.entrySet()) {
      String val = Objects.toString(e.getValue(), "");
      rows[idx][0] = e.getKey();
      rows[idx][1] = val.isBlank() ? "(present)" : val;
      idx++;
    }

    JTable table = buildReadOnlyTable(new String[] {"Token", "Value"}, rows);
    JScrollPane scroll = new JScrollPane(table);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    panel.add(scroll, "grow");
    return panel;
  }

  private JPanel buildCapabilityCountsRow(ServerRuntimeMetadata meta) {
    Map<CapabilityState, Integer> counts = new java.util.EnumMap<>(CapabilityState.class);
    for (CapabilityState state : CapabilityState.values()) {
      counts.put(state, 0);
    }
    for (CapabilityState state : meta.ircv3Caps.values()) {
      if (state == null) continue;
      counts.put(state, counts.getOrDefault(state, 0) + 1);
    }

    JPanel row =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 4",
                "[grow,fill]8[grow,fill]8[grow,fill]8[grow,fill]",
                "[]"));
    row.add(buildCountChip("Enabled", counts.getOrDefault(CapabilityState.ENABLED, 0)), "growx");
    row.add(
        buildCountChip("Available", counts.getOrDefault(CapabilityState.AVAILABLE, 0)), "growx");
    row.add(buildCountChip("Disabled", counts.getOrDefault(CapabilityState.DISABLED, 0)), "growx");
    row.add(buildCountChip("Removed", counts.getOrDefault(CapabilityState.REMOVED, 0)), "growx");
    return row;
  }

  private boolean isCapabilityRequested(String capability) {
    String cap = Objects.toString(capability, "").trim().toLowerCase(java.util.Locale.ROOT);
    if (cap.isEmpty()) return false;
    boolean requestable = false;
    for (String candidate : PircbotxBotFactory.requestableCapabilities()) {
      if (cap.equalsIgnoreCase(Objects.toString(candidate, "").trim())) {
        requestable = true;
        break;
      }
    }
    if (!requestable) return false;
    if (runtimeConfig == null) return true;
    try {
      return runtimeConfig.isIrcv3CapabilityEnabled(cap, true);
    } catch (Exception ignored) {
      return true;
    }
  }

  private String capabilityStatusSummary(ServerRuntimeMetadata meta) {
    if (meta == null || meta.ircv3Caps.isEmpty()) {
      return "Requested but not enabled: (none)";
    }

    List<String> pending = new ArrayList<>();
    TreeMap<String, CapabilityState> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    sorted.putAll(meta.ircv3Caps);
    for (Map.Entry<String, CapabilityState> e : sorted.entrySet()) {
      String cap = e.getKey();
      CapabilityState state = e.getValue();
      if (!isCapabilityRequested(cap)) continue;
      if (CapabilityState.ENABLED.equals(state)) continue;
      String label = state == null ? "unknown" : state.label;
      pending.add(cap + " [" + label + "]");
    }

    if (pending.isEmpty()) return "Requested but not enabled: (none)";
    int limit = Math.min(8, pending.size());
    String joined = String.join(", ", pending.subList(0, limit));
    if (pending.size() > limit) {
      joined = joined + ", +" + (pending.size() - limit) + " more";
    }
    return "Requested but not enabled: " + joined;
  }

  private static JPanel buildCountChip(String label, int count) {
    JPanel chip = new JPanel(new MigLayout("insets 6, wrap 1", "[grow,fill]", "[]0[]"));
    Color border = UIManager.getColor("Separator.foreground");
    if (border == null) border = UIManager.getColor("Component.borderColor");
    if (border == null) border = Color.GRAY;
    chip.setBorder(BorderFactory.createLineBorder(withAlpha(border, 180)));

    JLabel countLabel = new JLabel(Integer.toString(Math.max(0, count)));
    Font f = countLabel.getFont();
    if (f != null) {
      countLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D() + 1f));
    }
    JLabel textLabel = new JLabel(label);
    Color muted = UIManager.getColor("Label.disabledForeground");
    if (muted != null) textLabel.setForeground(muted);

    chip.add(countLabel, "alignx center");
    chip.add(textLabel, "alignx center");
    return chip;
  }

  private static JTable buildReadOnlyTable(String[] columns, Object[][] rows) {
    DefaultTableModel model =
        new DefaultTableModel(rows, columns) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };

    JTable table = new JTable(model);
    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(true);
    table.setRowSelectionAllowed(false);
    table.setColumnSelectionAllowed(false);
    table.getTableHeader().setReorderingAllowed(false);
    return table;
  }

  private static void addInfoRow(JPanel panel, String key, String value) {
    panel.add(new JLabel(key + ":"), "aligny top");
    JLabel valueLabel = new JLabel(fallbackInfoValue(value));
    valueLabel.setToolTipText(fallbackInfoValue(value));
    panel.add(valueLabel, "growx, wrap");
  }

  private static String fallbackInfoValue(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? "(unknown)" : v;
  }

  private static String formatConnectedEndpoint(String host, int port) {
    String h = Objects.toString(host, "").trim();
    if (h.isEmpty() && port <= 0) return "(unknown)";
    if (h.isEmpty()) return ":" + port;
    if (port <= 0) return h;
    return h + ":" + port;
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
    return defaultBuiltInNodesVisibility.server();
  }

  public boolean isLogViewerNodesVisible() {
    return defaultBuiltInNodesVisibility.logViewer();
  }

  public boolean isNotificationsNodesVisible() {
    return defaultBuiltInNodesVisibility.notifications();
  }

  public boolean isMonitorNodesVisible() {
    return defaultBuiltInNodesVisibility.monitor();
  }

  public boolean isInterceptorsNodesVisible() {
    return defaultBuiltInNodesVisibility.interceptors();
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
    defaultBuiltInNodesVisibility = defaultBuiltInNodesVisibility.withServer(visible);
    applyBuiltInNodesVisibilityGlobally(v -> v.withServer(visible));
  }

  public void setLogViewerNodesVisible(boolean visible) {
    boolean old = defaultBuiltInNodesVisibility.logViewer();
    defaultBuiltInNodesVisibility = defaultBuiltInNodesVisibility.withLogViewer(visible);
    applyBuiltInNodesVisibilityGlobally(v -> v.withLogViewer(visible));
    firePropertyChange(PROP_LOG_VIEWER_NODES_VISIBLE, old, visible);
  }

  public void setNotificationsNodesVisible(boolean visible) {
    boolean old = defaultBuiltInNodesVisibility.notifications();
    defaultBuiltInNodesVisibility = defaultBuiltInNodesVisibility.withNotifications(visible);
    applyBuiltInNodesVisibilityGlobally(v -> v.withNotifications(visible));
    firePropertyChange(PROP_NOTIFICATIONS_NODES_VISIBLE, old, visible);
  }

  public void setMonitorNodesVisible(boolean visible) {
    boolean old = defaultBuiltInNodesVisibility.monitor();
    defaultBuiltInNodesVisibility = defaultBuiltInNodesVisibility.withMonitor(visible);
    applyBuiltInNodesVisibilityGlobally(v -> v.withMonitor(visible));
    firePropertyChange(PROP_MONITOR_NODES_VISIBLE, old, visible);
  }

  public void setInterceptorsNodesVisible(boolean visible) {
    boolean old = defaultBuiltInNodesVisibility.interceptors();
    defaultBuiltInNodesVisibility = defaultBuiltInNodesVisibility.withInterceptors(visible);
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
    openPinnedChatRequests.onNext(ref);
  }

  private void installTreeKeyBindings() {
    // Legacy/alternate move bindings.
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "ircafe.tree.nodeMoveUp");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "ircafe.tree.nodeMoveDown");
    // Primary move bindings: Alt + Up/Down.
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK),
            "ircafe.tree.nodeMoveUp");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK),
            "ircafe.tree.nodeMoveDown");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            "ircafe.tree.closeNode");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "ircafe.tree.closeNode");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "ircafe.tree.openPinnedDock");

    tree.getActionMap()
        .put(
            "ircafe.tree.nodeMoveUp",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                moveNodeUpAction().actionPerformed(e);
              }
            });
    tree.getActionMap()
        .put(
            "ircafe.tree.nodeMoveDown",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                moveNodeDownAction().actionPerformed(e);
              }
            });
    tree.getActionMap()
        .put(
            "ircafe.tree.closeNode",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                closeNodeAction().actionPerformed(e);
              }
            });
    tree.getActionMap()
        .put(
            "ircafe.tree.openPinnedDock",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelectedNodeInChatDock();
              }
            });
  }

  private static boolean isPrivateMessageTarget(TargetRef ref) {
    if (ref == null) return false;
    return !ref.isStatus() && !ref.isChannel() && !ref.isUiOnly();
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

    privateMessageOnlineByTarget.put(pm, online);
    DefaultMutableTreeNode node = leaves.get(pm);
    if (node != null) {
      model.nodeChanged(node);
    }
  }

  public void clearPrivateMessageOnlineStates(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    java.util.ArrayList<TargetRef> changed = new java.util.ArrayList<>();
    java.util.Iterator<Map.Entry<TargetRef, Boolean>> it =
        privateMessageOnlineByTarget.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<TargetRef, Boolean> e = it.next();
      TargetRef ref = e.getKey();
      if (ref != null && Objects.equals(ref.serverId(), sid)) {
        changed.add(ref);
        it.remove();
      }
    }

    for (TargetRef ref : changed) {
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
    addApplicationLeaf(applicationJfrRef, APP_JFR_LABEL);
    addApplicationLeaf(applicationSpringRef, APP_SPRING_LABEL);
    addApplicationLeaf(applicationTerminalRef, APP_TERMINAL_LABEL);
  }

  private void addApplicationLeaf(TargetRef ref, String label) {
    if (ref == null) return;
    DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new NodeData(ref, label));
    leaves.put(ref, leaf);
    applicationRoot.add(leaf);
  }

  private void syncApplicationRootVisibility() {
    Set<TreePath> expandedBefore = snapshotExpandedTreePaths();
    boolean structureChanged = false;

    if (showApplicationRoot) {
      if (applicationRoot.getParent() != root) {
        root.insert(applicationRoot, Math.min(1, root.getChildCount()));
        model.nodeStructureChanged(root);
        structureChanged = true;
      }
      if (structureChanged) restoreExpandedTreePaths(expandedBefore);
      tree.expandPath(new TreePath(applicationRoot.getPath()));
      return;
    }

    TargetRef selected = selectedTargetRef();
    if (selected != null && selected.isApplicationUi()) {
      TargetRef first = servers.values().stream().findFirst().map(sn -> sn.statusRef).orElse(null);
      if (first != null) {
        selectTarget(first);
      } else {
        tree.setSelectionPath(defaultSelectionPath());
      }
    }

    if (applicationRoot.getParent() == root) {
      root.remove(applicationRoot);
      model.nodeStructureChanged(root);
      structureChanged = true;
    }
    if (structureChanged) restoreExpandedTreePaths(expandedBefore);
  }

  private Set<TreePath> snapshotExpandedTreePaths() {
    TreePath rootPath = new TreePath(root.getPath());
    Set<TreePath> expanded = new HashSet<>();
    Enumeration<TreePath> en = tree.getExpandedDescendants(rootPath);
    if (en != null) {
      while (en.hasMoreElements()) {
        expanded.add(en.nextElement());
      }
    }
    return expanded;
  }

  private void restoreExpandedTreePaths(Set<TreePath> expanded) {
    if (expanded == null || expanded.isEmpty()) return;
    for (TreePath p : expanded) {
      if (isPathInCurrentTreeModel(p)) {
        tree.expandPath(p);
      }
    }
  }

  private boolean isPathInCurrentTreeModel(TreePath path) {
    if (path == null) return false;
    Object[] nodes = path.getPath();
    if (nodes.length == 0 || nodes[0] != root) return false;

    DefaultMutableTreeNode cursor = root;
    for (int i = 1; i < nodes.length; i++) {
      Object next = nodes[i];
      DefaultMutableTreeNode matched = null;
      for (int c = 0; c < cursor.getChildCount(); c++) {
        Object child = cursor.getChildAt(c);
        if (child == next && child instanceof DefaultMutableTreeNode dmtn) {
          matched = dmtn;
          break;
        }
      }
      if (matched == null) return false;
      cursor = matched;
    }
    return true;
  }

  private TreePath defaultSelectionPath() {
    if (ircRoot.getParent() == root) {
      return new TreePath(ircRoot.getPath());
    }
    if (applicationRoot.getParent() == root) {
      return new TreePath(applicationRoot.getPath());
    }
    return new TreePath(root.getPath());
  }

  private static String applicationLeafLabel(TargetRef ref) {
    if (ref == null) return "";
    if (ref.isApplicationUnhandledErrors()) return APP_UNHANDLED_ERRORS_LABEL;
    if (ref.isApplicationAssertjSwing()) return APP_ASSERTJ_SWING_LABEL;
    if (ref.isApplicationJhiccup()) return APP_JHICCUP_LABEL;
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
    TargetRef selected = selectedTargetRef();
    DefaultMutableTreeNode selectedNode =
        (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
    boolean selectedMonitorGroup = selectedNode != null && isMonitorGroupNode(selectedNode);
    boolean selectedInterceptorsGroup =
        selectedNode != null && isInterceptorsGroupNode(selectedNode);
    String selectedGroupServerId =
        (selectedMonitorGroup || selectedInterceptorsGroup)
            ? owningServerIdForNode(selectedNode)
            : "";

    for (ServerNodes sn : servers.values()) {
      if (sn == null || sn.serverNode == null) continue;
      String sid = normalizeServerId(sn.statusRef.serverId());
      ServerBuiltInNodesVisibility vis = builtInNodesVisibility(sid);

      ensureUiLeafVisible(sn, sn.statusRef, statusLeafLabelForServer(sid), vis.server());
      ensureUiLeafVisible(sn, sn.notificationsRef, "Notifications", vis.notifications());
      ensureUiLeafVisible(sn, sn.logViewerRef, LOG_VIEWER_LABEL, vis.logViewer());
      ensureUiLeafVisible(sn, sn.channelListRef, CHANNEL_LIST_LABEL, true);
      ensureUiLeafVisible(sn, sn.weechatFiltersRef, WEECHAT_FILTERS_LABEL, true);
      ensureUiLeafVisible(sn, sn.ignoresRef, IGNORES_LABEL, true);
      ensureUiLeafVisible(sn, sn.dccTransfersRef, DCC_TRANSFERS_LABEL, showDccTransfersNodes);
      ensureMonitorGroupVisible(sn, vis.monitor());
      ensureInterceptorsGroupVisible(sn, vis.interceptors());
    }

    if (selected != null) {
      String sid = normalizeServerId(selected.serverId());
      ServerBuiltInNodesVisibility vis = builtInNodesVisibility(sid);
      if (selected.isStatus() && !vis.server()) {
        selectBestFallbackForServer(sid);
      } else if (selected.isNotifications() && !vis.notifications()) {
        selectBestFallbackForServer(sid);
      } else if (selected.isLogViewer() && !vis.logViewer()) {
        selectBestFallbackForServer(sid);
      } else if (selected.isDccTransfers() && !showDccTransfersNodes) {
        selectBestFallbackForServer(sid);
      } else if (selected.isMonitorGroup() && !vis.monitor()) {
        selectBestFallbackForServer(sid);
      } else if ((selected.isInterceptorsGroup() || selected.isInterceptor())
          && !vis.interceptors()) {
        selectBestFallbackForServer(sid);
      }
    } else if (selectedMonitorGroup && !builtInNodesVisibility(selectedGroupServerId).monitor()) {
      if (!selectedGroupServerId.isBlank()) {
        selectBestFallbackForServer(selectedGroupServerId);
      }
    } else if (selectedInterceptorsGroup
        && !builtInNodesVisibility(selectedGroupServerId).interceptors()) {
      if (!selectedGroupServerId.isBlank()) {
        selectBestFallbackForServer(selectedGroupServerId);
      }
    }
  }

  private void selectBestFallbackForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerNodes sn = servers.get(sid);
    if (sn == null) return;

    ServerBuiltInNodesVisibility vis = builtInNodesVisibility(sid);
    if (vis.server() && leaves.containsKey(sn.statusRef)) {
      selectTarget(sn.statusRef);
      return;
    }
    if (vis.notifications() && leaves.containsKey(sn.notificationsRef)) {
      selectTarget(sn.notificationsRef);
      return;
    }
    if (vis.logViewer() && leaves.containsKey(sn.logViewerRef)) {
      selectTarget(sn.logViewerRef);
      return;
    }
    if (leaves.containsKey(sn.channelListRef)) {
      selectTarget(sn.channelListRef);
      return;
    }
    if (leaves.containsKey(sn.weechatFiltersRef)) {
      selectTarget(sn.weechatFiltersRef);
      return;
    }
    if (leaves.containsKey(sn.ignoresRef)) {
      selectTarget(sn.ignoresRef);
      return;
    }
    if (vis.monitor() && sn.monitorNode != null && sn.monitorNode.getParent() == sn.serverNode) {
      selectTarget(TargetRef.monitorGroup(sid));
      return;
    }
    if (vis.interceptors()
        && sn.interceptorsNode != null
        && sn.interceptorsNode.getParent() == sn.serverNode) {
      selectTarget(TargetRef.interceptorsGroup(sid));
    }
  }

  private boolean ensureUiLeafVisible(
      ServerNodes sn, TargetRef ref, String label, boolean visible) {
    if (sn == null || ref == null) return false;
    DefaultMutableTreeNode existing = leaves.get(ref);
    if (!visible) {
      if (existing == null) return false;
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode) existing.getParent();
      int idx = parent == null ? -1 : parent.getIndex(existing);
      leaves.remove(ref);
      typingActivityNodes.remove(existing);
      if (parent != null) {
        Object[] removed = new Object[] {existing};
        if (idx < 0) {
          parent.remove(existing);
          model.nodeStructureChanged(parent);
        } else {
          parent.remove(existing);
          model.nodesWereRemoved(parent, new int[] {idx}, removed);
        }
      }
      return true;
    }

    if (existing != null) return false;
    DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new NodeData(ref, label));
    leaves.put(ref, leaf);
    int idx = fixedLeafInsertIndexFor(sn, ref);
    sn.serverNode.insert(leaf, idx);
    model.nodesWereInserted(sn.serverNode, new int[] {idx});
    return true;
  }

  private boolean ensureInterceptorsGroupVisible(ServerNodes sn, boolean visible) {
    if (sn == null || sn.serverNode == null || sn.interceptorsNode == null) return false;
    DefaultMutableTreeNode group = sn.interceptorsNode;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) group.getParent();

    if (!visible) {
      if (parent != sn.serverNode) return false;
      int idx = sn.serverNode.getIndex(group);
      if (idx < 0) return false;
      sn.serverNode.remove(group);
      model.nodesWereRemoved(sn.serverNode, new int[] {idx}, new Object[] {group});
      return true;
    }

    if (parent == sn.serverNode) return false;
    int idx = interceptorsGroupInsertIndex(sn);
    sn.serverNode.insert(group, idx);
    model.nodesWereInserted(sn.serverNode, new int[] {idx});
    return true;
  }

  private boolean ensureMonitorGroupVisible(ServerNodes sn, boolean visible) {
    if (sn == null || sn.serverNode == null || sn.monitorNode == null) return false;
    DefaultMutableTreeNode group = sn.monitorNode;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) group.getParent();

    if (!visible) {
      if (parent != sn.serverNode) return false;
      int idx = sn.serverNode.getIndex(group);
      if (idx < 0) return false;
      sn.serverNode.remove(group);
      model.nodesWereRemoved(sn.serverNode, new int[] {idx}, new Object[] {group});
      return true;
    }

    if (parent == sn.serverNode) return false;
    int idx = monitorGroupInsertIndex(sn);
    sn.serverNode.insert(group, idx);
    model.nodesWereInserted(sn.serverNode, new int[] {idx});
    return true;
  }

  private int monitorGroupInsertIndex(ServerNodes sn) {
    if (sn == null || sn.serverNode == null) return 0;

    int idx = sn.serverNode.getChildCount();
    int pmIdx = sn.serverNode.getIndex(sn.pmNode);
    if (pmIdx >= 0) idx = Math.min(idx, pmIdx);

    if (sn.interceptorsNode != null && sn.interceptorsNode.getParent() == sn.serverNode) {
      int interceptorsIdx = sn.serverNode.getIndex(sn.interceptorsNode);
      if (interceptorsIdx >= 0) idx = Math.min(idx, interceptorsIdx);
    }

    return Math.max(0, Math.min(idx, sn.serverNode.getChildCount()));
  }

  private int interceptorsGroupInsertIndex(ServerNodes sn) {
    if (sn == null || sn.serverNode == null) return 0;

    int pmIdx = sn.serverNode.getIndex(sn.pmNode);
    if (pmIdx >= 0) {
      int idx = pmIdx;
      if (sn.monitorNode != null && sn.monitorNode.getParent() == sn.serverNode) {
        int monitorIdx = sn.serverNode.getIndex(sn.monitorNode);
        if (monitorIdx >= 0) idx = monitorIdx + 1;
      }
      return Math.max(0, Math.min(idx, sn.serverNode.getChildCount()));
    }

    return sn.serverNode.getChildCount();
  }

  private int fixedLeafInsertIndexFor(ServerNodes sn, TargetRef ref) {
    int idx = 0;
    if (leaves.containsKey(sn.statusRef)) idx++;
    if (leaves.containsKey(sn.notificationsRef)) idx++;

    boolean hasLogViewer = leaves.containsKey(sn.logViewerRef);
    boolean hasChannelList = leaves.containsKey(sn.channelListRef);
    boolean hasWeechatFilters = leaves.containsKey(sn.weechatFiltersRef);
    boolean hasIgnores = leaves.containsKey(sn.ignoresRef);
    boolean hasDccTransfers = leaves.containsKey(sn.dccTransfersRef);

    if (ref.equals(sn.logViewerRef)) {
      return idx;
    }

    if (hasLogViewer) idx++;
    if (ref.equals(sn.channelListRef)) {
      return idx;
    }

    if (hasChannelList) idx++;
    if (ref.equals(sn.weechatFiltersRef)) {
      return idx;
    }

    if (hasWeechatFilters) idx++;
    if (ref.equals(sn.ignoresRef)) {
      return idx;
    }

    if (hasIgnores) idx++;
    if (ref.equals(sn.dccTransfersRef)) {
      return idx;
    }

    if (hasDccTransfers) idx++;
    return Math.min(idx, sn.serverNode.getChildCount());
  }

  private TargetRef selectedTargetRef() {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
    if (node == null) return null;
    Object uo = node.getUserObject();
    if (uo instanceof NodeData nd) return nd.ref;
    return null;
  }

  public void ensureNode(TargetRef ref) {
    Objects.requireNonNull(ref, "ref");
    if (ref.isApplicationUi()) {
      if (!showApplicationRoot) {
        setApplicationRootVisible(true);
      }
      if (!leaves.containsKey(ref)) {
        addApplicationLeaf(ref, applicationLeafLabel(ref));
        model.nodeStructureChanged(applicationRoot);
      }
      return;
    }
    if (ref.isDccTransfers() && !showDccTransfersNodes) {
      setDccTransfersNodesVisible(true);
    }
    if (ref.isMonitorGroup() || ref.isInterceptorsGroup()) {
      // This is a built-in grouping node (not a leaf/PM/channel). Selecting it should not
      // create a synthetic leaf (for example "__monitor_group__") under private messages.
      if (servers.containsKey(ref.serverId())) return;
      if (serverCatalog == null || serverCatalog.containsId(ref.serverId()) || servers.isEmpty()) {
        addServerRoot(ref.serverId());
      }
      return;
    }

    ServerBuiltInNodesVisibility vis = builtInNodesVisibility(ref.serverId());
    if (ref.isStatus() && !vis.server()) return;
    if (ref.isNotifications() && !vis.notifications()) return;
    if (ref.isLogViewer() && !vis.logViewer()) return;
    if (ref.isMonitorGroup() && !vis.monitor()) return;
    if ((ref.isInterceptorsGroup() || ref.isInterceptor()) && !vis.interceptors()) return;
    if (leaves.containsKey(ref)) return;

    ServerNodes sn = servers.get(ref.serverId());
    if (sn == null) {
      if (serverCatalog == null || serverCatalog.containsId(ref.serverId()) || servers.isEmpty()) {
        sn = addServerRoot(ref.serverId());
      } else {
        return;
      }
    }

    DefaultMutableTreeNode parent;
    if (ref.isStatus()) {
      parent = sn.serverNode;
    } else if (ref.isNotifications()) {
      parent = sn.serverNode;
    } else if (ref.isMonitorGroup()) {
      parent = sn.monitorNode != null ? sn.monitorNode : sn.serverNode;
    } else if (ref.isInterceptor()) {
      parent = sn.interceptorsNode != null ? sn.interceptorsNode : sn.serverNode;
    } else if (ref.isChannelList()) {
      parent = sn.serverNode;
    } else if (ref.isWeechatFilters()) {
      parent = sn.serverNode;
    } else if (ref.isIgnores()) {
      parent = sn.serverNode;
    } else if (ref.isDccTransfers()) {
      parent = sn.serverNode;
    } else if (ref.isLogViewer()) {
      parent = sn.serverNode;
    } else if (ref.isChannel()) {
      DefaultMutableTreeNode channelListNode = leaves.get(sn.channelListRef);
      if (channelListNode == null) {
        DefaultMutableTreeNode channelListLeaf =
            new DefaultMutableTreeNode(new NodeData(sn.channelListRef, CHANNEL_LIST_LABEL));
        int channelListIdx = fixedLeafInsertIndexFor(sn, sn.channelListRef);
        sn.serverNode.insert(channelListLeaf, channelListIdx);
        leaves.put(sn.channelListRef, channelListLeaf);
        model.nodesWereInserted(sn.serverNode, new int[] {channelListIdx});
        channelListNode = channelListLeaf;
      }
      parent = channelListNode;
    } else {
      parent = sn.pmNode;
    }

    String leafLabel = ref.target();
    if (ref.isNotifications()) {
      leafLabel = "Notifications";
    } else if (ref.isInterceptor()) {
      String name =
          interceptorStore != null
              ? interceptorStore.interceptorName(ref.serverId(), ref.interceptorId())
              : "";
      leafLabel = (name == null || name.isBlank()) ? "Interceptor" : name;
    } else if (ref.isLogViewer()) {
      leafLabel = LOG_VIEWER_LABEL;
    } else if (ref.isChannelList()) {
      leafLabel = CHANNEL_LIST_LABEL;
    } else if (ref.isWeechatFilters()) {
      leafLabel = WEECHAT_FILTERS_LABEL;
    } else if (ref.isIgnores()) {
      leafLabel = IGNORES_LABEL;
    } else if (ref.isDccTransfers()) {
      leafLabel = DCC_TRANSFERS_LABEL;
    }
    DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new NodeData(ref, leafLabel));
    leaves.put(ref, leaf);
    if (isPrivateMessageTarget(ref)) {
      privateMessageOnlineByTarget.putIfAbsent(ref, Boolean.FALSE);
      if (shouldPersistPrivateMessageList()) {
        runtimeConfig.rememberPrivateMessageTarget(ref.serverId(), ref.target());
      }
    }
    int idx;
    idx = parent.getChildCount();
    parent.insert(leaf, idx);

    model.nodesWereInserted(parent, new int[] {idx});
    if (ref.isChannel()) {
      String sid = normalizeServerId(ref.serverId());
      ensureChannelKnownInConfig(ref);
      sortChannelsUnderChannelList(sid);
      emitManagedChannelsChanged(sid);
    }
    tree.expandPath(new TreePath(parent.getPath()));
  }

  public void selectTarget(TargetRef ref) {
    if (ref == null) return;
    if (ref.isMonitorGroup()) {
      ensureNode(ref);
      ServerNodes sn = servers.get(ref.serverId());
      DefaultMutableTreeNode node = (sn == null) ? null : sn.monitorNode;
      if (node == null || node.getParent() != sn.serverNode) return;
      TreePath path = new TreePath(node.getPath());
      tree.setSelectionPath(path);
      tree.scrollPathToVisible(path);
      return;
    }
    if (ref.isInterceptorsGroup()) {
      ensureNode(ref);
      ServerNodes sn = servers.get(ref.serverId());
      DefaultMutableTreeNode node = (sn == null) ? null : sn.interceptorsNode;
      if (node == null || node.getParent() != sn.serverNode) return;
      TreePath path = new TreePath(node.getPath());
      tree.setSelectionPath(path);
      tree.scrollPathToVisible(path);
      return;
    }
    ensureNode(ref);
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    TreePath path = new TreePath(node.getPath());
    tree.setSelectionPath(path);
    tree.scrollPathToVisible(path);
  }

  public void removeTarget(TargetRef ref) {
    if (ref == null || ref.isStatus()) return;
    if (ref.isUiOnly() && !ref.isInterceptor()) return;
    DefaultMutableTreeNode mappedNode = leaves.remove(ref);
    java.util.Set<DefaultMutableTreeNode> nodesToRemove = new HashSet<>();
    if (mappedNode != null) {
      nodesToRemove.add(mappedNode);
    }
    nodesToRemove.addAll(findTreeNodesByTarget(ref));
    if (nodesToRemove.isEmpty()) return;

    if (isPrivateMessageTarget(ref)) {
      privateMessageOnlineByTarget.remove(ref);
      if (shouldPersistPrivateMessageList()) {
        runtimeConfig.forgetPrivateMessageTarget(ref.serverId(), ref.target());
      }
    }
    if (ref.isChannel()) {
      String sid = normalizeServerId(ref.serverId());
      String key = foldChannelKey(ref.target());
      if (!sid.isEmpty() && !key.isEmpty()) {
        Map<String, Boolean> autoByChannel = channelAutoReattachByServer.get(sid);
        if (autoByChannel != null) {
          autoByChannel.remove(key);
        }
        ArrayList<String> customOrder = channelCustomOrderByServer.get(sid);
        if (customOrder != null) {
          customOrder.removeIf(c -> foldChannelKey(c).equals(key));
        }
        emitManagedChannelsChanged(sid);
      }
    }

    boolean removedAny = false;
    for (DefaultMutableTreeNode node : nodesToRemove) {
      if (node == null) continue;
      typingActivityNodes.remove(node);
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
      if (parent == null) continue;
      int idx = parent.getIndex(node);
      if (idx < 0) continue;
      Object[] removed = new Object[] {node};
      parent.remove(node);
      model.nodesWereRemoved(parent, new int[] {idx}, removed);
      removedAny = true;
    }

    if (!removedAny) {
      model.reload(root);
    }
  }

  public void setChannelDetached(TargetRef ref, boolean detached) {
    setChannelDetached(ref, detached, null);
  }

  public void setChannelDetached(TargetRef ref, boolean detached, String warningReason) {
    if (ref == null || !ref.isChannel()) return;
    ensureNode(ref);
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd)) return;
    String normalizedReason = warningReason == null ? null : warningReason.trim();
    String nextWarning;
    if (!detached) {
      nextWarning = "";
    } else if (normalizedReason != null) {
      nextWarning = normalizedReason;
    } else if (!nd.detached) {
      nextWarning = "";
    } else {
      nextWarning = Objects.toString(nd.detachedWarning, "");
    }
    boolean detachedChanged = nd.detached != detached;
    boolean warningChanged = !Objects.equals(Objects.toString(nd.detachedWarning, ""), nextWarning);
    if (!detachedChanged && !warningChanged) return;

    nd.detached = detached;
    nd.detachedWarning = nextWarning;
    if (detached) {
      nd.clearTypingActivityNow();
      typingActivityNodes.remove(node);
      if (typingActivityNodes.isEmpty()) {
        typingActivityTimer.stop();
      }
    }
    model.nodeChanged(node);
    emitManagedChannelsChanged(ref.serverId());
  }

  public void clearChannelDetachedWarning(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return;
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd) || !nd.hasDetachedWarning()) return;
    setChannelDetached(ref, true, "");
  }

  public boolean isChannelDetached(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return false;
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd)) return false;
    return nd.detached;
  }

  public boolean isChannelAutoReattach(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return true;
    String sid = normalizeServerId(ref.serverId());
    String key = foldChannelKey(ref.target());
    if (sid.isEmpty() || key.isEmpty()) return true;

    if (SwingUtilities.isEventDispatchThread()) {
      return channelAutoReattachByServer
          .getOrDefault(sid, Map.of())
          .getOrDefault(key, Boolean.TRUE);
    }

    AtomicReference<Boolean> out = new AtomicReference<>(Boolean.TRUE);
    try {
      SwingUtilities.invokeAndWait(
          () ->
              out.set(
                  channelAutoReattachByServer
                      .getOrDefault(sid, Map.of())
                      .getOrDefault(key, Boolean.TRUE)));
      return out.get();
    } catch (Exception ex) {
      return true;
    }
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    if (ref == null || !ref.isChannel()) return;
    String sid = normalizeServerId(ref.serverId());
    String channel = Objects.toString(ref.target(), "").trim();
    if (sid.isEmpty() || channel.isEmpty()) return;

    Runnable apply =
        () -> {
          Map<String, Boolean> byChannel =
              channelAutoReattachByServer.computeIfAbsent(sid, __ -> new HashMap<>());
          byChannel.put(foldChannelKey(channel), autoReattach);
          if (runtimeConfig != null) {
            runtimeConfig.rememberServerTreeChannelAutoReattach(sid, channel, autoReattach);
          }
          emitManagedChannelsChanged(sid);
        };

    if (SwingUtilities.isEventDispatchThread()) {
      apply.run();
    } else {
      SwingUtilities.invokeLater(apply);
    }
  }

  private void emitManagedChannelsChanged(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    managedChannelsChangedByServer.onNext(sid);
  }

  private void ensureChannelKnownInConfig(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return;
    String sid = normalizeServerId(ref.serverId());
    String channel = Objects.toString(ref.target(), "").trim();
    if (sid.isEmpty() || channel.isEmpty()) return;

    Map<String, Boolean> autoByChannel =
        channelAutoReattachByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    String key = foldChannelKey(channel);
    boolean known = autoByChannel.containsKey(key);
    if (!known) {
      boolean autoReattach =
          runtimeConfig == null
              ? true
              : runtimeConfig.readServerTreeChannelAutoReattach(sid, channel, true);
      autoByChannel.put(key, autoReattach);
      if (runtimeConfig != null) {
        runtimeConfig.rememberServerTreeChannel(sid, channel);
      }
    }

    ArrayList<String> customOrder =
        channelCustomOrderByServer.computeIfAbsent(sid, __ -> new ArrayList<>());
    if (!containsIgnoreCase(customOrder, channel)) {
      customOrder.add(channel);
      if (runtimeConfig != null) {
        runtimeConfig.rememberServerTreeChannelCustomOrder(sid, customOrder);
      }
    }
  }

  private List<ManagedChannelEntry> snapshotManagedChannelsForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    ServerNodes sn = servers.get(sid);
    if (sn == null) return List.of();
    DefaultMutableTreeNode channelListNode = leaves.get(sn.channelListRef);
    if (channelListNode == null) return List.of();

    Map<String, Boolean> autoByChannel = channelAutoReattachByServer.getOrDefault(sid, Map.of());
    ArrayList<ManagedChannelEntry> out = new ArrayList<>();

    for (int i = 0; i < channelListNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
      Object uo = child.getUserObject();
      if (!(uo instanceof NodeData nd)) continue;
      if (nd.ref == null || !nd.ref.isChannel()) continue;
      String channel = Objects.toString(nd.ref.target(), "").trim();
      if (channel.isEmpty()) continue;
      boolean autoReattach = autoByChannel.getOrDefault(foldChannelKey(channel), Boolean.TRUE);
      int notifications = Math.max(0, nd.unread) + Math.max(0, nd.highlightUnread);
      out.add(new ManagedChannelEntry(channel, nd.detached, autoReattach, notifications));
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private ArrayList<String> normalizeCustomOrderList(List<String> channels) {
    ArrayList<String> out = new ArrayList<>();
    if (channels == null || channels.isEmpty()) return out;
    for (String channel : channels) {
      String c = Objects.toString(channel, "").trim();
      if (!(c.startsWith("#") || c.startsWith("&"))) continue;
      if (containsIgnoreCase(out, c)) continue;
      out.add(c);
    }
    return out;
  }

  private void sortChannelsUnderChannelList(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerNodes sn = servers.get(sid);
    if (sn == null) return;
    DefaultMutableTreeNode channelListNode = leaves.get(sn.channelListRef);
    if (channelListNode == null) return;

    ArrayList<DefaultMutableTreeNode> channelNodes = new ArrayList<>();
    for (int i = 0; i < channelListNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
      Object uo = child.getUserObject();
      if (!(uo instanceof NodeData nd) || nd.ref == null || !nd.ref.isChannel()) continue;
      channelNodes.add(child);
    }
    if (channelNodes.size() <= 1) {
      if (channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM)
          == ChannelSortMode.CUSTOM) {
        persistCustomOrderFromTree(sid);
      }
      return;
    }

    ChannelSortMode sortMode = channelSortModeByServer.getOrDefault(sid, ChannelSortMode.CUSTOM);

    ArrayList<DefaultMutableTreeNode> sorted = new ArrayList<>(channelNodes);
    if (sortMode == ChannelSortMode.ALPHABETICAL) {
      sorted.sort(
          (a, b) -> {
            String ac = channelLabelForNode(a);
            String bc = channelLabelForNode(b);
            int cmp = ac.compareToIgnoreCase(bc);
            if (cmp != 0) return cmp;
            return ac.compareTo(bc);
          });
    } else {
      ArrayList<String> customOrder =
          channelCustomOrderByServer.getOrDefault(sid, new ArrayList<>());
      Map<String, Integer> byKey = new HashMap<>();
      for (int i = 0; i < customOrder.size(); i++) {
        String c = Objects.toString(customOrder.get(i), "").trim();
        if (c.isEmpty()) continue;
        byKey.putIfAbsent(foldChannelKey(c), i);
      }
      sorted.sort(
          (a, b) -> {
            String ac = channelLabelForNode(a);
            String bc = channelLabelForNode(b);
            int ai = byKey.getOrDefault(foldChannelKey(ac), Integer.MAX_VALUE);
            int bi = byKey.getOrDefault(foldChannelKey(bc), Integer.MAX_VALUE);
            if (ai != bi) return Integer.compare(ai, bi);
            int cmp = ac.compareToIgnoreCase(bc);
            if (cmp != 0) return cmp;
            return ac.compareTo(bc);
          });
    }

    boolean changed = false;
    for (int i = 0; i < channelNodes.size(); i++) {
      if (channelNodes.get(i) != sorted.get(i)) {
        changed = true;
        break;
      }
    }

    if (changed) {
      Set<TreePath> expanded = snapshotExpandedTreePaths();
      for (DefaultMutableTreeNode node : channelNodes) {
        model.removeNodeFromParent(node);
      }
      for (int i = 0; i < sorted.size(); i++) {
        model.insertNodeInto(sorted.get(i), channelListNode, i);
      }
      restoreExpandedTreePaths(expanded);
    }

    if (sortMode == ChannelSortMode.CUSTOM) {
      persistCustomOrderFromTree(sid);
    }
  }

  private String channelLabelForNode(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd) || nd.ref == null) return "";
    return Objects.toString(nd.ref.target(), "").trim();
  }

  private void persistCustomOrderFromTree(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerNodes sn = servers.get(sid);
    if (sn == null) return;
    DefaultMutableTreeNode channelListNode = leaves.get(sn.channelListRef);
    if (channelListNode == null) return;

    ArrayList<String> customOrder = new ArrayList<>();
    for (int i = 0; i < channelListNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
      Object uo = child.getUserObject();
      if (!(uo instanceof NodeData nd)) continue;
      if (nd.ref == null || !nd.ref.isChannel()) continue;
      String channel = Objects.toString(nd.ref.target(), "").trim();
      if (channel.isEmpty()) continue;
      if (containsIgnoreCase(customOrder, channel)) continue;
      customOrder.add(channel);
    }
    channelCustomOrderByServer.put(sid, customOrder);
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelCustomOrder(sid, customOrder);
    }
  }

  private void loadChannelStateForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    ChannelSortMode sortMode = ChannelSortMode.CUSTOM;
    ArrayList<String> customOrder = new ArrayList<>();
    Map<String, Boolean> autoByChannel = new HashMap<>();

    if (runtimeConfig != null) {
      RuntimeConfigStore.ServerTreeChannelState state =
          runtimeConfig.readServerTreeChannelState(sid);
      if (state != null && state.sortMode() != null) {
        sortMode =
            state.sortMode() == RuntimeConfigStore.ServerTreeChannelSortMode.ALPHABETICAL
                ? ChannelSortMode.ALPHABETICAL
                : ChannelSortMode.CUSTOM;
      }
      if (state != null && state.customOrder() != null) {
        customOrder.addAll(normalizeCustomOrderList(state.customOrder()));
      }
      if (state != null && state.channels() != null) {
        for (RuntimeConfigStore.ServerTreeChannelPreference pref : state.channels()) {
          if (pref == null) continue;
          String channel = Objects.toString(pref.channel(), "").trim();
          if (channel.isEmpty()) continue;
          autoByChannel.put(foldChannelKey(channel), pref.autoReattach());
        }
      }
    }

    channelSortModeByServer.put(sid, sortMode);
    channelCustomOrderByServer.put(sid, customOrder);
    channelAutoReattachByServer.put(sid, autoByChannel);
  }

  private static String foldChannelKey(String channel) {
    return Objects.toString(channel, "").trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static boolean containsIgnoreCase(List<String> values, String needle) {
    if (values == null || values.isEmpty()) return false;
    String n = Objects.toString(needle, "").trim();
    if (n.isEmpty()) return false;
    for (String value : values) {
      if (value == null) continue;
      if (value.equalsIgnoreCase(n)) return true;
    }
    return false;
  }

  private java.util.List<DefaultMutableTreeNode> findTreeNodesByTarget(TargetRef ref) {
    java.util.ArrayList<DefaultMutableTreeNode> out = new java.util.ArrayList<>();
    if (ref == null) return out;

    Enumeration<?> en = root.depthFirstEnumeration();
    while (en.hasMoreElements()) {
      Object o = en.nextElement();
      if (!(o instanceof DefaultMutableTreeNode node)) continue;
      Object uo = node.getUserObject();
      if (!(uo instanceof NodeData nd)) continue;
      if (nd.ref == null) continue;
      if (!ref.equals(nd.ref)) continue;
      out.add(node);
    }
    return out;
  }

  public void markUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;
    nd.unread++;
    model.nodeChanged(node);
    if (ref != null && ref.isChannel()) {
      emitManagedChannelsChanged(ref.serverId());
    }
  }

  public void markHighlight(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;
    nd.highlightUnread++;
    model.nodeChanged(node);
    if (ref != null && ref.isChannel()) {
      emitManagedChannelsChanged(ref.serverId());
    }
  }

  public void clearUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;
    if (nd.unread == 0 && nd.highlightUnread == 0) return;
    nd.unread = 0;
    nd.highlightUnread = 0;
    model.nodeChanged(node);
    if (ref != null && ref.isChannel()) {
      emitManagedChannelsChanged(ref.serverId());
    }
  }

  public void markTypingActivity(TargetRef ref, String state) {
    if (!supportsTypingActivity(ref)) return;
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;

    if (nd.detached) {
      boolean changed = nd.clearTypingActivityNow();
      typingActivityNodes.remove(node);
      if (typingActivityNodes.isEmpty()) {
        typingActivityTimer.stop();
      }
      if (changed) {
        repaintTreeNode(node);
      }
      return;
    }

    long now = System.currentTimeMillis();
    boolean changed = nd.applyTypingState(state, now, TYPING_ACTIVITY_HOLD_MS);
    if (changed) {
      repaintTreeNode(node);
    }
    if (nd.hasTypingActivity()) {
      typingActivityNodes.add(node);
      startTypingActivityTimerIfNeeded();
      return;
    }
    typingActivityNodes.remove(node);
    if (typingActivityNodes.isEmpty()) {
      typingActivityTimer.stop();
    }
  }

  private void onTypingActivityAnimationTick() {
    if (!isShowing() || !tree.isShowing()) {
      typingActivityTimer.stop();
      return;
    }

    long now = System.currentTimeMillis();
    java.util.ArrayList<DefaultMutableTreeNode> repaintNodes = new java.util.ArrayList<>();

    java.util.Iterator<DefaultMutableTreeNode> it = typingActivityNodes.iterator();
    while (it.hasNext()) {
      DefaultMutableTreeNode node = it.next();
      if (node == null) {
        it.remove();
        continue;
      }
      if (node.getParent() == null) {
        it.remove();
        continue;
      }
      Object uo = node.getUserObject();
      if (!(uo instanceof NodeData nd)) {
        it.remove();
        continue;
      }
      if (!nd.hasTypingActivity()) {
        it.remove();
        continue;
      }

      boolean hadTyping = nd.hasTypingActivity();
      nd.clearTypingActivityIfExpired(now, TYPING_ACTIVITY_FADE_MS);
      if (!nd.hasTypingActivity()) {
        it.remove();
      }
      if (hadTyping) repaintNodes.add(node);
    }

    if (typingActivityNodes.isEmpty()) {
      typingActivityTimer.stop();
    }

    for (DefaultMutableTreeNode node : repaintNodes) {
      repaintTreeNode(node);
    }
  }

  private void startTypingActivityTimerIfNeeded() {
    if (typingActivityNodes.isEmpty()) return;
    if (!isShowing() || !tree.isShowing()) return;
    if (!typingActivityTimer.isRunning()) {
      typingActivityTimer.start();
    }
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

  private static boolean supportsTypingActivity(TargetRef ref) {
    if (ref == null) return false;
    if (ref.isStatus() || ref.isUiOnly() || ref.isNotifications()) return false;
    if (ref.isChannelList() || ref.isDccTransfers()) return false;
    return ref.isChannel();
  }

  private void syncServers(List<ServerEntry> latest) {
    Set<String> newIds = new HashSet<>();
    Map<String, String> nextDisplay = new HashMap<>();
    Set<String> nextEphemeral = new HashSet<>();
    Set<String> nextSojuBouncerControl = new HashSet<>();
    Map<String, String> nextSojuOrigins = new HashMap<>();
    Set<String> nextZncBouncerControl = new HashSet<>();
    Map<String, String> nextZncOrigins = new HashMap<>();

    if (latest != null) {
      for (ServerEntry e : latest) {
        if (e == null || e.server() == null) continue;
        String id = Objects.toString(e.server().id(), "").trim();
        if (id.isEmpty()) continue;
        newIds.add(id);
        nextDisplay.put(id, computeServerDisplayName(e));
        if (e.ephemeral()) nextEphemeral.add(id);

        // If a soju network was discovered from a configured bouncer server, label that server's
        // status tab as "Bouncer Control" for clarity.
        if (id.startsWith("soju:")) {
          String origin = Objects.toString(e.originId(), "").trim();
          if (origin.isEmpty()) {
            origin = parseOriginFromCompoundServerId(id, "soju:");
          }
          if (origin != null && !origin.isBlank()) {
            nextSojuBouncerControl.add(origin);
            nextSojuOrigins.put(id, origin);
          }
        }

        // If a ZNC network was discovered from a configured bouncer server, label that server's
        // status tab as "Bouncer Control" for clarity.
        if (id.startsWith("znc:")) {
          String origin = Objects.toString(e.originId(), "").trim();
          if (origin.isEmpty()) {
            origin = parseOriginFromCompoundServerId(id, "znc:");
          }
          if (origin != null && !origin.isBlank()) {
            nextZncBouncerControl.add(origin);
            nextZncOrigins.put(id, origin);
          }
        }
      }
    }

    // Update origin mapping first so addServerRoot() can nest soju networks properly.
    sojuOriginByServerId.clear();
    sojuOriginByServerId.putAll(nextSojuOrigins);

    // Update origin mapping for ZNC discovered networks.
    zncOriginByServerId.clear();
    zncOriginByServerId.putAll(nextZncOrigins);

    // Ensure origin servers exist before adding nested soju networks.
    for (String id : newIds) {
      if (id.startsWith("soju:") || id.startsWith("znc:")) continue;
      if (!servers.containsKey(id)) {
        addServerRoot(id);
      }
    }
    for (String id : newIds) {
      if (!servers.containsKey(id)) {
        addServerRoot(id);
      }
    }

    for (String existing : List.copyOf(servers.keySet())) {
      if (!newIds.contains(existing)) {
        removeServerRoot(existing);
        serverDisplayNames.remove(existing);
        ephemeralServerIds.remove(existing);
        sojuBouncerControlServerIds.remove(existing);
        zncBouncerControlServerIds.remove(existing);
      }
    }

    updateBouncerControlLabels(nextSojuBouncerControl, nextZncBouncerControl);

    for (String id : newIds) {
      String next = nextDisplay.getOrDefault(id, id);
      String prev = serverDisplayNames.put(id, next);

      boolean eph = nextEphemeral.contains(id);
      boolean prevEph = ephemeralServerIds.contains(id);
      if (eph) ephemeralServerIds.add(id);
      else ephemeralServerIds.remove(id);

      if (!Objects.equals(prev, next) || eph != prevEph) {
        ServerNodes sn = servers.get(id);
        if (sn != null) model.nodeChanged(sn.serverNode);
      }
    }

    Set<TreePath> expandedBeforeReload = snapshotExpandedTreePaths();
    model.reload(root);
    restoreExpandedTreePaths(expandedBeforeReload);
    SwingUtilities.invokeLater(
        () -> {
          TreePath sel = tree.getSelectionPath();
          if (sel != null) {
            Object last = sel.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode n1) {
              if (n1.getPath() != null && n1.getRoot() == root) {
                return;
              }
            }
          }

          TargetRef first =
              servers.values().stream().findFirst().map(sn -> sn.statusRef).orElse(null);
          if (first != null) {
            selectTarget(first);
          } else {
            tree.setSelectionPath(defaultSelectionPath());
          }
        });
  }

  private String computeServerDisplayName(ServerEntry e) {
    if (e == null || e.server() == null) return "";
    String id = Objects.toString(e.server().id(), "").trim();
    if (id.isEmpty()) return id;
    if (!e.ephemeral()) return id;

    String login = Objects.toString(e.server().login(), "").trim();
    if (!login.isEmpty()) {
      int slash = login.indexOf('/');
      if (slash >= 0 && slash + 1 < login.length()) {
        String after = login.substring(slash + 1);
        int at = after.indexOf('@');
        if (at >= 0) after = after.substring(0, at);
        after = after.trim();
        if (!after.isEmpty()) return after;
      }
    }

    return id;
  }

  private String prettyServerLabel(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return id;
    String display = serverDisplayNames.getOrDefault(id, id);
    if (isSojuEphemeralServer(id)) {
      String origin = sojuOriginByServerId.get(id);
      if (origin != null && sojuAutoConnect != null && sojuAutoConnect.isEnabled(origin, display)) {
        return display + " (auto)";
      }
      return display;
    }
    if (isZncEphemeralServer(id)) {
      String origin = zncOriginByServerId.get(id);
      if (origin != null && zncAutoConnect != null && zncAutoConnect.isEnabled(origin, display)) {
        return display + " (auto)";
      }
      return display;
    }
    return display;
  }

  private boolean isSojuEphemeralServer(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    return !id.isEmpty() && id.startsWith("soju:") && ephemeralServerIds.contains(id);
  }

  private boolean isZncEphemeralServer(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    return !id.isEmpty() && id.startsWith("znc:") && ephemeralServerIds.contains(id);
  }

  private DefaultMutableTreeNode getOrCreateSojuNetworksGroupNode(String originServerId) {
    String origin = Objects.toString(originServerId, "").trim();
    if (origin.isEmpty()) return null;

    DefaultMutableTreeNode existing = sojuNetworksGroupByOrigin.get(origin);
    if (existing != null) return existing;

    ServerNodes originNodes = servers.get(origin);
    if (originNodes == null) return null;

    DefaultMutableTreeNode group = new DefaultMutableTreeNode(SOJU_NETWORKS_GROUP_LABEL);

    // Insert right after fixed leaves (status + notifications + optional UI-only leaves) and before
    // PMs.
    int insertIdx = fixedLeafCount(originNodes);
    int pmIdx = originNodes.serverNode.getIndex(originNodes.pmNode);
    if (pmIdx >= 0) insertIdx = Math.min(insertIdx, pmIdx);
    insertIdx = Math.min(insertIdx, originNodes.serverNode.getChildCount());

    originNodes.serverNode.insert(group, insertIdx);
    sojuNetworksGroupByOrigin.put(origin, group);
    return group;
  }

  private boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (uo instanceof String s && SOJU_NETWORKS_GROUP_LABEL.equals(s)) return true;
    return sojuNetworksGroupByOrigin.containsValue(node);
  }

  private DefaultMutableTreeNode getOrCreateZncNetworksGroupNode(String originServerId) {
    String origin = Objects.toString(originServerId, "").trim();
    if (origin.isEmpty()) return null;

    DefaultMutableTreeNode existing = zncNetworksGroupByOrigin.get(origin);
    if (existing != null) return existing;

    ServerNodes originNodes = servers.get(origin);
    if (originNodes == null) return null;

    DefaultMutableTreeNode group = new DefaultMutableTreeNode(ZNC_NETWORKS_GROUP_LABEL);

    // Insert right after fixed leaves (status + notifications + optional UI-only leaves) and before
    // PMs.
    int insertIdx = fixedLeafCount(originNodes);
    int pmIdx = originNodes.serverNode.getIndex(originNodes.pmNode);
    if (pmIdx >= 0) insertIdx = Math.min(insertIdx, pmIdx);
    insertIdx = Math.min(insertIdx, originNodes.serverNode.getChildCount());

    originNodes.serverNode.insert(group, insertIdx);
    zncNetworksGroupByOrigin.put(origin, group);
    return group;
  }

  private boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (uo instanceof String s && ZNC_NETWORKS_GROUP_LABEL.equals(s)) return true;
    return zncNetworksGroupByOrigin.containsValue(node);
  }

  private int fixedLeafCount(ServerNodes originNodes) {
    if (originNodes == null) return 0;
    int count = 0;
    if (leaves.containsKey(originNodes.statusRef)) count++;
    if (leaves.containsKey(originNodes.notificationsRef)) count++;
    if (leaves.containsKey(originNodes.logViewerRef)) count++;
    if (leaves.containsKey(originNodes.channelListRef)) count++;
    if (leaves.containsKey(originNodes.weechatFiltersRef)) count++;
    if (leaves.containsKey(originNodes.ignoresRef)) count++;
    if (leaves.containsKey(originNodes.dccTransfersRef)) count++;
    if (originNodes.interceptorsNode != null
        && originNodes.interceptorsNode.getParent() == originNodes.serverNode) count++;
    if (originNodes.monitorNode != null
        && originNodes.monitorNode.getParent() == originNodes.serverNode) count++;
    return count;
  }

  private String toolTipForEvent(MouseEvent event) {
    if (event == null) return null;
    TreePath path = tree.getPathForLocation(event.getX(), event.getY());
    if (path == null) {
      String sid = serverIdAt(event.getX(), event.getY());
      if (!sid.isEmpty()) path = serverPathForId(sid);
    }
    if (path == null) return null;
    Object comp = path.getLastPathComponent();
    if (!(comp instanceof DefaultMutableTreeNode node)) return null;

    if (isIrcRootNode(node)) {
      return "Configured IRC servers and discovered bouncer networks.";
    }

    if (isApplicationRootNode(node)) {
      return "Application diagnostics buffers.";
    }

    if (isSojuNetworksGroupNode(node)) {
      return "Soju networks discovered from the bouncer (not saved).";
    }

    if (isZncNetworksGroupNode(node)) {
      return "ZNC networks discovered from the bouncer (not saved).";
    }

    if (isInterceptorsGroupNode(node)) {
      return "Interceptors for this server. Count shows total captured hits.";
    }
    if (isMonitorGroupNode(node)) {
      return "Monitored nick presence for this server (IRC MONITOR, with ISON fallback when unavailable).";
    }

    Object uo = node.getUserObject();
    if (uo instanceof NodeData nd && nd.ref != null) {
      if (nd.ref.isChannel() && nd.hasDetachedWarning()) {
        return "Detached: " + nd.detachedWarning + " (click warning icon to clear).";
      }
      if (nd.ref.isApplicationUnhandledErrors()) {
        return "Uncaught JVM exceptions captured by IRCafe.";
      }
      if (nd.ref.isApplicationAssertjSwing()) {
        return "Diagnostic buffer for AssertJ Swing/watchdog output.";
      }
      if (nd.ref.isApplicationJhiccup()) {
        return "Diagnostic buffer for jHiccup latency output.";
      }
      if (nd.ref.isApplicationJfr()) {
        return isApplicationJfrActive()
            ? "Runtime JFR diagnostics are active (status gauges + JFR event stream)."
            : "Runtime JFR diagnostics are disabled. Open the JFR view to enable.";
      }
      if (nd.ref.isApplicationSpring()) {
        return "Spring framework lifecycle and availability event feed.";
      }
      if (nd.ref.isApplicationTerminal()) {
        return "In-app terminal output mirrored from System.out/System.err.";
      }
      if (nd.ref.isStatus()
          && BOUNCER_CONTROL_LABEL.equals(nd.label)
          && (sojuBouncerControlServerIds.contains(nd.ref.serverId())
              || zncBouncerControlServerIds.contains(nd.ref.serverId()))) {
        return "Bouncer Control connection (used to discover bouncer networks).";
      }
      if (nd.ref.isInterceptor()) {
        return "Custom interceptor rules, actions, and captured matches. Scope can be this server or any server.";
      }
      if (nd.ref.isWeechatFilters()) {
        return "WeeChat-style local filters for this server (rules, placeholders, and scope overrides).";
      }
      if (nd.ref.isIgnores()) {
        return "Manage hard and soft ignore rules for this server.";
      }
    }

    if (uo instanceof String serverId && isServerNode(node) && isSojuEphemeralServer(serverId)) {
      ConnectionState state = connectionStateForServer(serverId);
      boolean desired = desiredOnlineForServer(serverId);
      String stateTip = "State: " + serverStateLabel(state) + ".";
      String intentTip = " Intent: " + serverDesiredIntentLabel(desired) + ".";
      String queueTip = serverIntentQueueTip(state, desired);
      String diagnostics = connectionDiagnosticsTipForServer(serverId);
      String origin = Objects.toString(sojuOriginByServerId.get(serverId), "").trim();
      String display = serverDisplayNames.getOrDefault(serverId, serverId);
      boolean auto =
          !origin.isEmpty()
              && sojuAutoConnect != null
              && sojuAutoConnect.isEnabled(origin, display);
      String tip = stateTip + intentTip;
      if (!queueTip.isBlank()) tip += " " + queueTip;
      if (!diagnostics.isBlank()) tip += diagnostics;
      tip += " Discovered from soju; not saved.";
      if (auto) tip += " Auto-connect enabled.";
      if (!origin.isEmpty()) tip += " Origin: " + origin + ".";
      if (display != null && !display.isBlank()) tip += " Network: " + display + ".";
      return tip;
    }

    if (uo instanceof String serverId && isServerNode(node) && isZncEphemeralServer(serverId)) {
      ConnectionState state = connectionStateForServer(serverId);
      boolean desired = desiredOnlineForServer(serverId);
      String stateTip = "State: " + serverStateLabel(state) + ".";
      String intentTip = " Intent: " + serverDesiredIntentLabel(desired) + ".";
      String queueTip = serverIntentQueueTip(state, desired);
      String diagnostics = connectionDiagnosticsTipForServer(serverId);
      String origin = Objects.toString(zncOriginByServerId.get(serverId), "").trim();
      String display = serverDisplayNames.getOrDefault(serverId, serverId);
      boolean auto =
          !origin.isEmpty() && zncAutoConnect != null && zncAutoConnect.isEnabled(origin, display);
      String tip = stateTip + intentTip;
      if (!queueTip.isBlank()) tip += " " + queueTip;
      if (!diagnostics.isBlank()) tip += diagnostics;
      tip += " Discovered from ZNC; not saved.";
      if (auto) tip += " Auto-connect enabled.";
      if (!origin.isEmpty()) tip += " Origin: " + origin + ".";
      if (display != null && !display.isBlank()) tip += " Network: " + display + ".";
      return tip;
    }

    if (uo instanceof String serverId && isServerNode(node)) {
      ConnectionState state = connectionStateForServer(serverId);
      boolean desired = desiredOnlineForServer(serverId);
      String queueTip = serverIntentQueueTip(state, desired);
      String diagnostics = connectionDiagnosticsTipForServer(serverId);
      String action = serverActionHint(state);
      String base =
          "State: "
              + serverStateLabel(state)
              + ". Intent: "
              + serverDesiredIntentLabel(desired)
              + ".";
      if (!queueTip.isBlank() && !diagnostics.isBlank())
        return base + " " + queueTip + diagnostics + " " + action;
      if (!queueTip.isBlank()) return base + " " + queueTip + " " + action;
      if (!diagnostics.isBlank()) return base + diagnostics + " " + action;
      return base + " " + action;
    }

    return null;
  }

  private void refreshSojuAutoConnectBadges() {
    // Update labels for discovered (ephemeral) soju networks when auto-connect toggles change.
    for (String id : ephemeralServerIds) {
      if (!id.startsWith("soju:")) continue;
      ServerNodes sn = servers.get(id);
      if (sn != null) model.nodeChanged(sn.serverNode);
    }
  }

  private void refreshZncAutoConnectBadges() {
    // Update labels for discovered (ephemeral) ZNC networks when auto-connect toggles change.
    for (String id : ephemeralServerIds) {
      if (!id.startsWith("znc:")) continue;
      ServerNodes sn = servers.get(id);
      if (sn != null) model.nodeChanged(sn.serverNode);
    }
  }

  private boolean shouldPersistPrivateMessageList() {
    return runtimeConfig != null
        && logProps != null
        && Boolean.TRUE.equals(logProps.savePrivateMessageList());
  }

  private void removeServerRoot(String serverId) {
    ServerNodes sn = servers.remove(serverId);
    if (sn == null) return;

    if (interceptorStore != null) {
      try {
        interceptorStore.clearServerHits(serverId);
      } catch (Exception ignored) {
      }
    }

    if (Objects.equals(hoveredServerActionServerId, serverId)) {
      hoveredServerActionServerId = "";
    }

    serverStates.remove(serverId);
    serverDesiredOnline.remove(serverId);
    serverLastError.remove(serverId);
    serverNextRetryAtEpochMs.remove(serverId);
    serverRuntimeMetadata.remove(serverId);
    channelSortModeByServer.remove(serverId);
    channelCustomOrderByServer.remove(serverId);
    channelAutoReattachByServer.remove(serverId);
    clearPrivateMessageOnlineStates(serverId);
    leaves.entrySet().removeIf(e -> Objects.equals(e.getKey().serverId(), serverId));
    typingActivityNodes.removeIf(
        node -> {
          if (node == null || node.getParent() == null) return true;
          Object uo = node.getUserObject();
          if (!(uo instanceof NodeData nd) || nd.ref == null) return false;
          return Objects.equals(nd.ref.serverId(), serverId);
        });

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) sn.serverNode.getParent();
    if (parent != null) {
      parent.remove(sn.serverNode);

      if (isSojuNetworksGroupNode(parent) && parent.getChildCount() == 0) {
        DefaultMutableTreeNode originNode = (DefaultMutableTreeNode) parent.getParent();
        if (originNode != null) {
          originNode.remove(parent);
        }
        sojuNetworksGroupByOrigin.entrySet().removeIf(e -> e.getValue() == parent);
      }

      if (isZncNetworksGroupNode(parent) && parent.getChildCount() == 0) {
        DefaultMutableTreeNode originNode = (DefaultMutableTreeNode) parent.getParent();
        if (originNode != null) {
          originNode.remove(parent);
        }
        zncNetworksGroupByOrigin.entrySet().removeIf(e -> e.getValue() == parent);
      }
    }
  }

  private ServerNodes addServerRoot(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();
    if (id.isEmpty()) id = "(server)";
    if (servers.containsKey(id)) return servers.get(id);
    serverStates.putIfAbsent(id, ConnectionState.DISCONNECTED);
    loadChannelStateForServer(id);

    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(id);
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private messages");

    DefaultMutableTreeNode parent = ircRoot;
    if (id.startsWith("soju:")) {
      String origin = sojuOriginByServerId.get(id);
      if (origin == null || origin.isBlank()) {
        origin = parseOriginFromCompoundServerId(id, "soju:");
      }
      if (origin != null && !origin.isBlank()) {
        // Ensure the origin server exists so we can nest beneath it.
        if (!servers.containsKey(origin)) {
          addServerRoot(origin);
        }
        DefaultMutableTreeNode group = getOrCreateSojuNetworksGroupNode(origin);
        if (group != null) parent = group;
      }
    } else if (id.startsWith("znc:")) {
      String origin = zncOriginByServerId.get(id);
      if (origin == null || origin.isBlank()) {
        origin = parseOriginFromCompoundServerId(id, "znc:");
      }
      if (origin != null && !origin.isBlank()) {
        // Ensure the origin server exists so we can nest beneath it.
        if (!servers.containsKey(origin)) {
          addServerRoot(origin);
        }
        DefaultMutableTreeNode group = getOrCreateZncNetworksGroupNode(origin);
        if (group != null) parent = group;
      }
    }

    parent.add(serverNode);
    ServerBuiltInNodesVisibility vis = builtInNodesVisibility(id);
    TargetRef statusRef = new TargetRef(id, "status");
    int nextUiLeafIndex = 0;
    if (vis.server()) {
      DefaultMutableTreeNode statusLeaf =
          new DefaultMutableTreeNode(new NodeData(statusRef, statusLeafLabelForServer(id)));
      serverNode.insert(statusLeaf, nextUiLeafIndex++);
      leaves.put(statusRef, statusLeaf);
    }

    TargetRef notificationsRef = TargetRef.notifications(id);
    NodeData notificationsData = new NodeData(notificationsRef, "Notifications");
    if (notificationStore != null) {
      notificationsData.highlightUnread = notificationStore.count(id);
    }
    if (vis.notifications()) {
      DefaultMutableTreeNode notificationsLeaf = new DefaultMutableTreeNode(notificationsData);
      serverNode.insert(notificationsLeaf, nextUiLeafIndex++);
      leaves.put(notificationsRef, notificationsLeaf);
    }

    TargetRef logViewerRef = TargetRef.logViewer(id);
    if (vis.logViewer()) {
      DefaultMutableTreeNode logViewerLeaf =
          new DefaultMutableTreeNode(new NodeData(logViewerRef, LOG_VIEWER_LABEL));
      serverNode.insert(logViewerLeaf, nextUiLeafIndex++);
      leaves.put(logViewerRef, logViewerLeaf);
    }

    TargetRef channelListRef = TargetRef.channelList(id);
    DefaultMutableTreeNode channelListLeaf =
        new DefaultMutableTreeNode(new NodeData(channelListRef, CHANNEL_LIST_LABEL));
    serverNode.insert(channelListLeaf, nextUiLeafIndex++);
    leaves.put(channelListRef, channelListLeaf);

    TargetRef weechatFiltersRef = TargetRef.weechatFilters(id);
    DefaultMutableTreeNode weechatFiltersLeaf =
        new DefaultMutableTreeNode(new NodeData(weechatFiltersRef, WEECHAT_FILTERS_LABEL));
    serverNode.insert(weechatFiltersLeaf, nextUiLeafIndex++);
    leaves.put(weechatFiltersRef, weechatFiltersLeaf);

    TargetRef ignoresRef = TargetRef.ignores(id);
    DefaultMutableTreeNode ignoresLeaf =
        new DefaultMutableTreeNode(new NodeData(ignoresRef, IGNORES_LABEL));
    serverNode.insert(ignoresLeaf, nextUiLeafIndex++);
    leaves.put(ignoresRef, ignoresLeaf);

    TargetRef dccTransfersRef = TargetRef.dccTransfers(id);
    if (showDccTransfersNodes) {
      DefaultMutableTreeNode dccTransfersLeaf =
          new DefaultMutableTreeNode(new NodeData(dccTransfersRef, DCC_TRANSFERS_LABEL));
      serverNode.insert(dccTransfersLeaf, nextUiLeafIndex++);
      leaves.put(dccTransfersRef, dccTransfersLeaf);
    }

    NodeData interceptorsData = new NodeData(null, INTERCEPTORS_GROUP_LABEL);
    if (interceptorStore != null) {
      interceptorsData.unread = Math.max(0, interceptorStore.totalHitCount(id));
    }
    NodeData monitorData = new NodeData(null, MONITOR_GROUP_LABEL);
    DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode(monitorData);
    if (vis.monitor()) {
      serverNode.add(monitorNode);
    }

    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode(interceptorsData);

    if (interceptorStore != null) {
      List<InterceptorDefinition> defs = interceptorStore.listInterceptors(id);
      if (defs != null) {
        for (InterceptorDefinition def : defs) {
          if (def == null) continue;
          TargetRef ref = TargetRef.interceptor(id, def.id());
          String label = Objects.toString(def.name(), "").trim();
          if (label.isEmpty()) label = "Interceptor";
          DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new NodeData(ref, label));
          interceptorsNode.add(leaf);
          leaves.put(ref, leaf);
        }
      }
    }

    serverNode.add(pmNode);
    if (vis.interceptors()) {
      int interceptorsIdx = serverNode.getIndex(pmNode);
      if (interceptorsIdx < 0) interceptorsIdx = serverNode.getChildCount();
      int monitorIdx = serverNode.getIndex(monitorNode);
      if (monitorIdx >= 0) {
        interceptorsIdx =
            Math.max(monitorIdx + 1, Math.min(interceptorsIdx, serverNode.getChildCount()));
      }
      serverNode.insert(
          interceptorsNode, Math.max(0, Math.min(interceptorsIdx, serverNode.getChildCount())));
    }

    ServerNodes sn =
        new ServerNodes(
            serverNode,
            pmNode,
            monitorNode,
            interceptorsNode,
            statusRef,
            notificationsRef,
            logViewerRef,
            channelListRef,
            weechatFiltersRef,
            ignoresRef,
            dccTransfersRef);
    servers.put(id, sn);

    model.reload(root);
    tree.expandPath(new TreePath(serverNode.getPath()));
    refreshNotificationsCount(id);
    refreshInterceptorGroupCount(id);
    return sn;
  }

  /** Extract the origin server id from compound ids like {@code soju:<origin>:<network>} */
  private static String parseOriginFromCompoundServerId(String serverId, String prefix) {
    String id = Objects.toString(serverId, "").trim();
    String p = Objects.toString(prefix, "").trim();
    if (id.isEmpty() || p.isEmpty() || !id.startsWith(p)) return null;
    int start = p.length();
    int nextColon = id.indexOf(':', start);
    if (nextColon <= start) return null;
    String origin = id.substring(start, nextColon).trim();
    return origin.isEmpty() ? null : origin;
  }

  private String statusLeafLabelForServer(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (id.isEmpty()) return STATUS_LABEL;
    return (sojuBouncerControlServerIds.contains(id) || zncBouncerControlServerIds.contains(id))
        ? BOUNCER_CONTROL_LABEL
        : STATUS_LABEL;
  }

  private void updateBouncerControlLabels(
      Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl) {
    Set<String> nextSoju = nextSojuBouncerControl == null ? Set.of() : nextSojuBouncerControl;
    Set<String> nextZnc = nextZncBouncerControl == null ? Set.of() : nextZncBouncerControl;

    Set<String> prevUnion = new HashSet<>(sojuBouncerControlServerIds);
    prevUnion.addAll(zncBouncerControlServerIds);

    // Update the backing sets first so tooltip/label helpers see the current state.
    sojuBouncerControlServerIds.clear();
    sojuBouncerControlServerIds.addAll(nextSoju);
    zncBouncerControlServerIds.clear();
    zncBouncerControlServerIds.addAll(nextZnc);

    Set<String> nextUnion = new HashSet<>(nextSoju);
    nextUnion.addAll(nextZnc);

    Set<String> all = new HashSet<>(prevUnion);
    all.addAll(nextUnion);

    for (String serverId : all) {
      boolean was = prevUnion.contains(serverId);
      boolean now = nextUnion.contains(serverId);
      if (was == now) continue;

      TargetRef statusRef = new TargetRef(serverId, "status");
      DefaultMutableTreeNode node = leaves.get(statusRef);
      if (node == null) continue;
      Object uo = node.getUserObject();
      if (!(uo instanceof NodeData old)) continue;

      String label = now ? BOUNCER_CONTROL_LABEL : STATUS_LABEL;
      if (Objects.equals(old.label, label)) continue;
      NodeData nd = new NodeData(statusRef, label);
      nd.unread = old.unread;
      nd.highlightUnread = old.highlightUnread;
      nd.detached = old.detached;
      nd.detachedWarning = old.detachedWarning;
      nd.copyTypingFrom(old);
      node.setUserObject(nd);
      model.nodeChanged(node);
    }
  }

  private void refreshNotificationsCount(String serverId) {
    if (notificationStore == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    TargetRef ref = TargetRef.notifications(sid);
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd)) return;
    int count = notificationStore.count(sid);
    if (nd.unread == 0 && nd.highlightUnread == count) return;
    nd.unread = 0;
    nd.highlightUnread = count;
    model.nodeChanged(node);
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

  private void syncTypingIndicatorStyleFromSettings() {
    String configured = null;
    try {
      configured =
          settingsBus != null && settingsBus.get() != null
              ? settingsBus.get().typingIndicatorsTreeStyle()
              : null;
    } catch (Exception ignored) {
    }
    this.typingIndicatorStyle = TreeTypingIndicatorStyle.from(configured);
  }

  private void syncUnreadBadgeScaleFromRuntimeConfig() {
    int next = TREE_BADGE_SCALE_PERCENT_DEFAULT;
    try {
      if (runtimeConfig != null) {
        next =
            runtimeConfig.readServerTreeUnreadBadgeScalePercent(TREE_BADGE_SCALE_PERCENT_DEFAULT);
      }
    } catch (Exception ignored) {
      next = TREE_BADGE_SCALE_PERCENT_DEFAULT;
    }
    if (next < 50) next = 50;
    if (next > 150) next = 150;
    unreadBadgeScalePercent = next;
  }

  private enum TreeTypingIndicatorStyle {
    DOTS,
    KEYBOARD,
    GLOW_DOT;

    static TreeTypingIndicatorStyle from(String raw) {
      String s = Objects.toString(raw, "").trim().toLowerCase(java.util.Locale.ROOT);
      if (s.isEmpty()) return DOTS;
      return switch (s) {
        case "keyboard", "kbd" -> KEYBOARD;
        case "glow-dot", "glowdot", "dot", "green-dot", "glowing-green-dot" -> GLOW_DOT;
        default -> DOTS;
      };
    }
  }

  private record ServerBuiltInNodesVisibility(
      boolean server,
      boolean notifications,
      boolean logViewer,
      boolean monitor,
      boolean interceptors) {
    static ServerBuiltInNodesVisibility defaults() {
      return new ServerBuiltInNodesVisibility(true, true, true, true, true);
    }

    ServerBuiltInNodesVisibility withServer(boolean visible) {
      return new ServerBuiltInNodesVisibility(
          visible, notifications, logViewer, monitor, interceptors);
    }

    ServerBuiltInNodesVisibility withNotifications(boolean visible) {
      return new ServerBuiltInNodesVisibility(server, visible, logViewer, monitor, interceptors);
    }

    ServerBuiltInNodesVisibility withLogViewer(boolean visible) {
      return new ServerBuiltInNodesVisibility(
          server, notifications, visible, monitor, interceptors);
    }

    ServerBuiltInNodesVisibility withMonitor(boolean visible) {
      return new ServerBuiltInNodesVisibility(
          server, notifications, logViewer, visible, interceptors);
    }

    ServerBuiltInNodesVisibility withInterceptors(boolean visible) {
      return new ServerBuiltInNodesVisibility(server, notifications, logViewer, monitor, visible);
    }

    RuntimeConfigStore.ServerTreeBuiltInNodesVisibility toRuntimeVisibility() {
      return new RuntimeConfigStore.ServerTreeBuiltInNodesVisibility(
          server, notifications, logViewer, monitor, interceptors);
    }
  }

  private static final class ServerNodes {
    final DefaultMutableTreeNode serverNode;
    final DefaultMutableTreeNode pmNode;
    final DefaultMutableTreeNode monitorNode;
    final DefaultMutableTreeNode interceptorsNode;
    final TargetRef statusRef;
    final TargetRef notificationsRef;
    final TargetRef logViewerRef;
    final TargetRef channelListRef;
    final TargetRef weechatFiltersRef;
    final TargetRef ignoresRef;
    final TargetRef dccTransfersRef;

    ServerNodes(
        DefaultMutableTreeNode serverNode,
        DefaultMutableTreeNode pmNode,
        DefaultMutableTreeNode monitorNode,
        DefaultMutableTreeNode interceptorsNode,
        TargetRef statusRef,
        TargetRef notificationsRef,
        TargetRef logViewerRef,
        TargetRef channelListRef,
        TargetRef weechatFiltersRef,
        TargetRef ignoresRef,
        TargetRef dccTransfersRef) {
      this.serverNode = serverNode;
      this.pmNode = pmNode;
      this.monitorNode = monitorNode;
      this.interceptorsNode = interceptorsNode;
      this.statusRef = statusRef;
      this.notificationsRef = notificationsRef;
      this.logViewerRef = logViewerRef;
      this.channelListRef = channelListRef;
      this.weechatFiltersRef = weechatFiltersRef;
      this.ignoresRef = ignoresRef;
      this.dccTransfersRef = dccTransfersRef;
    }
  }

  private enum CapabilityState {
    AVAILABLE("available"),
    ENABLED("enabled"),
    DISABLED("disabled"),
    REMOVED("removed");

    private final String label;

    CapabilityState(String label) {
      this.label = label;
    }
  }

  private record CapabilityTransition(
      Instant at, String subcommand, String capability, CapabilityState state) {}

  private static final class ServerRuntimeMetadata {
    String connectedHost = "";
    int connectedPort = 0;
    String nick = "";
    Instant connectedAt;

    String serverName = "";
    String serverVersion = "";
    String userModes = "";
    String channelModes = "";

    final Map<String, CapabilityState> ircv3Caps = new LinkedHashMap<>();
    final Map<String, String> ircv3CapLastSubcommand = new LinkedHashMap<>();
    final List<CapabilityTransition> ircv3CapTransitions = new ArrayList<>();
    final Map<String, String> isupport = new LinkedHashMap<>();
  }

  private final class ServerTreeCellRenderer extends DefaultTreeCellRenderer {
    private float typingIndicatorAlpha = 0f;
    private boolean typingIndicatorSlotVisible = false;
    private boolean detachedWarningIndicatorVisible = false;
    private int unreadBadgeCount = 0;
    private int highlightBadgeCount = 0;

    private void setTreeIcon(String name) {
      Icon icon = SvgIcons.icon(name, TREE_NODE_ICON_SIZE, Palette.TREE);
      Icon disabled = SvgIcons.icon(name, TREE_NODE_ICON_SIZE, Palette.TREE_DISABLED);
      setIcon(icon);
      setDisabledIcon(disabled);
    }

    @Override
    public java.awt.Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean sel,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {

      java.awt.Component c =
          super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      Font base = UIManager.getFont("Tree.font");
      if (base == null) base = tree.getFont();
      if (base == null) base = getFont();
      typingIndicatorAlpha = 0f;
      typingIndicatorSlotVisible = false;
      detachedWarningIndicatorVisible = false;
      unreadBadgeCount = 0;
      highlightBadgeCount = 0;

      if (value instanceof DefaultMutableTreeNode node) {
        Object uo = node.getUserObject();
        if (uo instanceof NodeData nd) {
          setText(nd.label);
          boolean detachedChannel = nd.ref != null && nd.ref.isChannel() && nd.detached;
          int style = nd.highlightUnread > 0 ? Font.BOLD : Font.PLAIN;
          unreadBadgeCount = Math.max(0, nd.unread);
          highlightBadgeCount = Math.max(0, nd.highlightUnread);
          if (detachedChannel) {
            style |= Font.ITALIC;
          }
          setFont(base.deriveFont(style));
          if (!sel && detachedChannel) {
            Color muted = UIManager.getColor("Label.disabledForeground");
            if (muted == null) muted = UIManager.getColor("Component.disabledForeground");
            if (muted != null) setForeground(muted);
          }
          if (nd.ref != null && nd.ref.isChannel()) {
            setTreeIcon("channel");
          } else if (isPrivateMessageTarget(nd.ref)) {
            boolean online = Boolean.TRUE.equals(privateMessageOnlineByTarget.get(nd.ref));
            String name = online ? "pm-online" : "pm-offline";
            Palette pal = online ? Palette.TREE_PM_ONLINE : Palette.TREE_PM_OFFLINE;
            Icon icon = SvgIcons.icon(name, TREE_NODE_ICON_SIZE, pal);
            setIcon(icon);
            setDisabledIcon(icon);
          } else if (nd.ref != null && nd.ref.isApplicationUnhandledErrors()) {
            setTreeIcon("info");
          } else if (nd.ref != null && nd.ref.isApplicationAssertjSwing()) {
            setTreeIcon("settings");
          } else if (nd.ref != null && nd.ref.isApplicationJhiccup()) {
            setTreeIcon("refresh");
          } else if (nd.ref != null && nd.ref.isApplicationJfr()) {
            boolean active = isApplicationJfrActive();
            String iconName = active ? "play" : "pause";
            Palette palette = active ? Palette.TREE_PM_ONLINE : Palette.TREE_DISABLED;
            Icon icon = SvgIcons.icon(iconName, TREE_NODE_ICON_SIZE, palette);
            setIcon(icon);
            setDisabledIcon(icon);
          } else if (nd.ref != null && nd.ref.isApplicationSpring()) {
            setTreeIcon("theme");
          } else if (nd.ref != null && nd.ref.isApplicationTerminal()) {
            setTreeIcon("terminal");
          } else if (nd.ref != null && nd.ref.isStatus()) {
            setTreeIcon("dock-left");
          } else if (nd.ref != null && nd.ref.isNotifications()) {
            setTreeIcon("info");
          } else if (nd.ref != null && nd.ref.isLogViewer()) {
            setTreeIcon("copy");
          } else if (nd.ref != null && nd.ref.isInterceptor()) {
            setTreeIcon(isInterceptorEnabled(nd.ref) ? "interceptor" : "pause");
          } else if (nd.ref != null && nd.ref.isChannelList()) {
            setTreeIcon("add");
          } else if (nd.ref != null && nd.ref.isWeechatFilters()) {
            setTreeIcon("settings");
          } else if (nd.ref != null && nd.ref.isIgnores()) {
            setTreeIcon("ban");
          } else if (nd.ref != null && nd.ref.isDccTransfers()) {
            setTreeIcon("dock-right");
          } else if (nd.ref == null && isMonitorGroupNode(node)) {
            setTreeIcon("eye");
          } else if (nd.ref == null && isInterceptorsGroupNode(node)) {
            setTreeIcon("yin-yang");
          }
          if (supportsTypingActivity(nd.ref)) {
            typingIndicatorSlotVisible = true;
            detachedWarningIndicatorVisible = nd.hasDetachedWarning();
            if (!detachedWarningIndicatorVisible) {
              typingIndicatorAlpha =
                  nd.typingDotAlpha(
                      System.currentTimeMillis(),
                      TYPING_ACTIVITY_PULSE_MS,
                      TYPING_ACTIVITY_FADE_MS);
            }
          }
        } else if (uo instanceof String id && isServerNode(node)) {
          setText(serverNodeDisplayLabel(id));
          if (ephemeralServerIds.contains(id)) {
            setFont(base.deriveFont(Font.ITALIC));
          } else {
            setFont(base.deriveFont(Font.PLAIN));
          }
          ConnectionState state = connectionStateForServer(id);
          String iconName = serverNodeIconName(state);
          Palette palette = serverNodeIconPalette(state);
          Icon icon = SvgIcons.icon(iconName, TREE_NODE_ICON_SIZE, palette);
          Icon disabled = SvgIcons.icon(iconName, TREE_NODE_ICON_SIZE, Palette.TREE_DISABLED);
          setIcon(icon);
          setDisabledIcon(disabled);
        } else if (isIrcRootNode(node)) {
          setText(IRC_ROOT_LABEL);
          setFont(base.deriveFont(Font.PLAIN));
          setTreeIcon("terminal");
        } else if (isApplicationRootNode(node)) {
          setText(APPLICATION_ROOT_LABEL);
          setFont(base.deriveFont(Font.PLAIN));
          setTreeIcon("settings");
        } else if (isPrivateMessagesGroupNode(node)) {
          setFont(base.deriveFont(Font.PLAIN));
          setTreeIcon("account-unknown");
        } else if (isMonitorGroupNode(node)) {
          setFont(base.deriveFont(Font.PLAIN));
          setTreeIcon("eye");
        } else if (isInterceptorsGroupNode(node)) {
          setFont(base.deriveFont(Font.PLAIN));
          setTreeIcon("yin-yang");
        } else if (isSojuNetworksGroupNode(node) || isZncNetworksGroupNode(node)) {
          setFont(base.deriveFont(Font.PLAIN));
          setTreeIcon("dock-left");
        } else {
          setFont(base.deriveFont(Font.PLAIN));
        }
      } else {
        setFont(base.deriveFont(Font.PLAIN));
      }

      return c;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension base = super.getPreferredSize();
      int extra = badgesPreferredWidth();
      if (extra <= 0) return base;
      return new Dimension(base.width + extra, base.height);
    }

    @Override
    public java.awt.Insets getInsets() {
      java.awt.Insets insets = super.getInsets();
      if (!typingIndicatorSlotVisible || insets == null) return insets;
      return new java.awt.Insets(
          insets.top,
          insets.left + typingIndicatorReserveLeftInset(typingIndicatorStyle),
          insets.bottom,
          insets.right);
    }

    @Override
    public java.awt.Insets getInsets(java.awt.Insets insets) {
      java.awt.Insets resolved = super.getInsets(insets);
      if (!typingIndicatorSlotVisible || resolved == null) return resolved;
      resolved.left = resolved.left + typingIndicatorReserveLeftInset(typingIndicatorStyle);
      return resolved;
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

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (typingIndicatorSlotVisible
            && (detachedWarningIndicatorVisible || typingIndicatorAlpha > 0.01f)) {
          TreeTypingIndicatorStyle style = typingIndicatorStyle;
          int width = indicatorWidth(style);
          int height = indicatorHeight(style);
          java.awt.Insets insets = getInsets();
          int leftInset = insets != null ? insets.left : 0;
          int slotWidth = typingIndicatorSlotWidth(style);
          int slotLeft = Math.max(0, leftInset - slotWidth - 1);
          int x = slotLeft + Math.max(0, (slotWidth - width) / 2);
          int y = Math.max(0, (getHeight() - height) / 2);
          float alpha = Math.max(0f, Math.min(1f, typingIndicatorAlpha));

          if (detachedWarningIndicatorVisible) {
            drawDetachedWarningIndicator(g2, slotLeft, 0, slotWidth, getHeight());
          } else {
            switch (style) {
              case KEYBOARD -> drawKeyboardIndicator(g2, x, y, width, height, alpha);
              case GLOW_DOT -> drawGlowDotIndicator(g2, x, y, width, height, alpha);
              case DOTS -> drawDotsIndicator(g2, x, y, alpha);
            }
          }
        }
        paintUnreadBadges(g2);
      } finally {
        g2.dispose();
      }
    }

    private static int indicatorWidth(TreeTypingIndicatorStyle style) {
      return switch (style) {
        case KEYBOARD -> 10;
        case GLOW_DOT -> 8;
        case DOTS ->
            TYPING_ACTIVITY_DOT_COUNT * TYPING_ACTIVITY_DOT_SIZE
                + (TYPING_ACTIVITY_DOT_COUNT - 1) * TYPING_ACTIVITY_DOT_GAP;
      };
    }

    private static int typingIndicatorSlotWidth(TreeTypingIndicatorStyle style) {
      return Math.max(TYPING_ACTIVITY_LEFT_SLOT_WIDTH, indicatorWidth(style) + 2);
    }

    private static int typingIndicatorReserveLeftInset(TreeTypingIndicatorStyle style) {
      return typingIndicatorSlotWidth(style) + 1;
    }

    private static int indicatorHeight(TreeTypingIndicatorStyle style) {
      return switch (style) {
        case KEYBOARD -> 7;
        case GLOW_DOT -> 8;
        case DOTS -> TYPING_ACTIVITY_DOT_SIZE;
      };
    }

    private void drawDotsIndicator(Graphics2D g2, int x, int y, float alpha) {
      int dot = TYPING_ACTIVITY_DOT_SIZE;
      int gap = TYPING_ACTIVITY_DOT_GAP;
      int phase =
          (int)
              ((System.currentTimeMillis() / Math.max(80, TYPING_ACTIVITY_DOT_FRAME_MS))
                  % TYPING_ACTIVITY_DOT_COUNT);
      Color base = typingIndicatorColor();
      g2.setComposite(AlphaComposite.SrcOver);
      for (int i = 0; i < TYPING_ACTIVITY_DOT_COUNT; i++) {
        float pulse = (i == phase) ? 1.0f : 0.42f;
        int a = Math.max(12, Math.min(255, Math.round(255f * alpha * pulse)));
        g2.setColor(withAlpha(base, a));
        g2.fillOval(x + (i * (dot + gap)), y, dot, dot);
      }
    }

    private void drawKeyboardIndicator(
        Graphics2D g2, int x, int y, int width, int height, float alpha) {
      Color base = typingIndicatorColor();
      int fillA = Math.max(8, Math.min(255, Math.round(50f * alpha)));
      int strokeA = Math.max(18, Math.min(255, Math.round(225f * alpha)));
      int keyA = Math.max(14, Math.min(255, Math.round(165f * alpha)));
      g2.setComposite(AlphaComposite.SrcOver);
      g2.setColor(withAlpha(base, fillA));
      g2.fillRoundRect(x, y, width, height, 3, 3);
      g2.setColor(withAlpha(base, strokeA));
      g2.drawRoundRect(x, y, width - 1, height - 1, 3, 3);

      int keyY1 = y + 2;
      int keyY2 = y + 4;
      g2.setColor(withAlpha(base, keyA));
      int[] top = {x + 2, x + 4, x + 6, x + 8};
      for (int keyX : top) {
        g2.fillRect(keyX, keyY1, 1, 1);
      }
      g2.fillRect(x + 3, keyY2, 4, 1);
    }

    private void drawGlowDotIndicator(
        Graphics2D g2, int x, int y, int width, int height, float alpha) {
      int dot = 6;
      int halo = 4;
      int cx = x + Math.max(0, (width - dot) / 2);
      int cy = y + Math.max(0, (height - dot) / 2);
      g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(0.5f, alpha * 0.45f)));
      g2.setColor(TYPING_ACTIVITY_GLOW_HALO);
      g2.fillOval(cx - (halo / 2), cy - (halo / 2), dot + halo, dot + halo);

      g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
      g2.setColor(TYPING_ACTIVITY_GLOW_DOT);
      g2.fillOval(cx, cy, dot, dot);
    }

    private void drawDetachedWarningIndicator(
        Graphics2D g2, int slotLeft, int y, int slotWidth, int slotHeight) {
      int icon = Math.max(8, Math.min(10, Math.min(slotWidth - 2, slotHeight - 4)));
      int x = slotLeft + Math.max(0, (slotWidth - icon) / 2);
      int ty = y + Math.max(1, (slotHeight - icon) / 2);

      int topX = x + (icon / 2);
      int topY = ty;
      int leftX = x;
      int leftY = ty + icon - 1;
      int rightX = x + icon - 1;
      int rightY = leftY;

      Polygon triangle =
          new Polygon(new int[] {topX, leftX, rightX}, new int[] {topY, leftY, rightY}, 3);
      g2.setComposite(AlphaComposite.SrcOver);
      g2.setColor(DETACHED_WARNING_FILL);
      g2.fillPolygon(triangle);
      g2.setColor(DETACHED_WARNING_STROKE);
      g2.drawPolygon(triangle);

      int cx = topX;
      int exTop = ty + Math.max(2, icon / 4);
      int exBottom = ty + Math.max(exTop + 1, icon - 4);
      g2.setColor(DETACHED_WARNING_TEXT);
      g2.drawLine(cx, exTop, cx, exBottom);
      g2.fillOval(cx - 1, ty + icon - 3, 2, 2);
    }

    private int badgesPreferredWidth() {
      if (unreadBadgeCount <= 0 && highlightBadgeCount <= 0) return 0;
      FontMetrics fm = getFontMetrics(badgeFont());
      if (fm == null) return 0;
      int width = badgeClusterWidth(fm);
      return width > 0 ? (width + scaledBadgeGap()) : 0;
    }

    private void paintUnreadBadges(Graphics2D g2) {
      if (unreadBadgeCount <= 0 && highlightBadgeCount <= 0) return;
      Font badgeFont = badgeFont();
      FontMetrics fm = g2.getFontMetrics(badgeFont);
      if (fm == null) return;

      int badgeHeight =
          Math.max(scaledBadgeMinHeight(), fm.getAscent() + (scaledBadgeVerticalPadding() * 2));
      int x = badgeStartX(fm);
      int y = Math.max(0, (getHeight() - badgeHeight) / 2);

      if (unreadBadgeCount > 0) {
        String text = Integer.toString(unreadBadgeCount);
        int w = badgeWidthForText(fm, text);
        paintBadge(g2, x, y, w, badgeHeight, TREE_UNREAD_BADGE_BG, text, fm, badgeFont);
        x += w + scaledBadgeGap();
      }

      if (highlightBadgeCount > 0) {
        String text = Integer.toString(highlightBadgeCount);
        int w = badgeWidthForText(fm, text);
        paintBadge(g2, x, y, w, badgeHeight, TREE_HIGHLIGHT_BADGE_BG, text, fm, badgeFont);
      }
    }

    private int badgeStartX(FontMetrics fm) {
      int x = 0;
      java.awt.Insets insets = getInsets();
      if (insets != null) x += insets.left;
      Icon icon = getIcon();
      if (icon != null) x += icon.getIconWidth() + Math.max(0, getIconTextGap());
      String text = Objects.toString(getText(), "");
      if (!text.isEmpty()) x += fm.stringWidth(text);
      x += scaledBadgeGap();
      return x;
    }

    private int badgeClusterWidth(FontMetrics fm) {
      int width = 0;
      if (unreadBadgeCount > 0) {
        width += badgeWidthForText(fm, Integer.toString(unreadBadgeCount));
      }
      if (highlightBadgeCount > 0) {
        if (width > 0) width += scaledBadgeGap();
        width += badgeWidthForText(fm, Integer.toString(highlightBadgeCount));
      }
      return width;
    }

    private int badgeWidthForText(FontMetrics fm, String text) {
      int t = fm == null ? 0 : fm.stringWidth(Objects.toString(text, ""));
      return Math.max(scaledBadgeMinWidth(), t + (scaledBadgeHorizontalPadding() * 2));
    }

    private void paintBadge(
        Graphics2D g2,
        int x,
        int y,
        int width,
        int height,
        Color bg,
        String text,
        FontMetrics fm,
        Font badgeFont) {
      if (g2 == null) return;
      g2.setComposite(AlphaComposite.SrcOver);
      g2.setColor(bg);
      int arc = scaledBadgeArc();
      g2.fillRoundRect(x, y, width, height, arc, arc);
      g2.setFont(badgeFont);
      int textX = x + Math.max(0, (width - fm.stringWidth(text)) / 2);
      int textY = y + Math.max(0, ((height - fm.getHeight()) / 2) + fm.getAscent());
      g2.setColor(TREE_BADGE_FG);
      g2.drawString(text, textX, textY);
    }

    private Font badgeFont() {
      Font base = getFont();
      if (base == null) base = UIManager.getFont("Tree.font");
      if (base == null) base = UIManager.getFont("defaultFont");
      if (base == null) return new Font("SansSerif", Font.PLAIN, 12);
      float scaledSize =
          Math.max(
              8f, base.getSize2D() * (Math.max(50, Math.min(150, unreadBadgeScalePercent)) / 100f));
      return base.deriveFont(scaledSize);
    }

    private int scaledBadgeHorizontalPadding() {
      return scaleBadgeMetric(TREE_BADGE_HORIZONTAL_PADDING, 1);
    }

    private int scaledBadgeVerticalPadding() {
      return scaleBadgeMetric(TREE_BADGE_VERTICAL_PADDING, 1);
    }

    private int scaledBadgeMinWidth() {
      return scaleBadgeMetric(TREE_BADGE_MIN_WIDTH, 10);
    }

    private int scaledBadgeMinHeight() {
      return scaleBadgeMetric(TREE_BADGE_MIN_HEIGHT, 8);
    }

    private int scaledBadgeGap() {
      return scaleBadgeMetric(TREE_BADGE_GAP, 1);
    }

    private int scaledBadgeArc() {
      return scaleBadgeMetric(TREE_BADGE_ARC, 4);
    }

    private int scaleBadgeMetric(int base, int minimum) {
      float factor = Math.max(50, Math.min(150, unreadBadgeScalePercent)) / 100f;
      return Math.max(minimum, Math.round(base * factor));
    }

    private Color typingIndicatorColor() {
      Color c = UIManager.getColor("@accentColor");
      if (c == null) c = UIManager.getColor("Component.focusColor");
      if (c == null) c = UIManager.getColor("Label.foreground");
      if (c == null) c = TYPING_ACTIVITY_INDICATOR_FALLBACK;
      return c;
    }
  }

  static final class NodeData {
    final TargetRef ref;
    final String label;
    int unread = 0;
    int highlightUnread = 0;
    boolean detached = false;
    String detachedWarning = "";
    long typingPulseUntilMs = 0L;
    long typingDoneFadeStartMs = 0L;

    NodeData(TargetRef ref, String label) {
      this.ref = ref;
      this.label = label;
    }

    void copyTypingFrom(NodeData other) {
      if (other == null) return;
      this.typingPulseUntilMs = other.typingPulseUntilMs;
      this.typingDoneFadeStartMs = other.typingDoneFadeStartMs;
    }

    boolean hasTypingActivity() {
      return typingPulseUntilMs > 0L || typingDoneFadeStartMs > 0L;
    }

    boolean hasDetachedWarning() {
      return detached && !Objects.toString(detachedWarning, "").trim().isEmpty();
    }

    boolean clearTypingActivityNow() {
      long prevPulse = typingPulseUntilMs;
      long prevFade = typingDoneFadeStartMs;
      typingPulseUntilMs = 0L;
      typingDoneFadeStartMs = 0L;
      return prevPulse != typingPulseUntilMs || prevFade != typingDoneFadeStartMs;
    }

    boolean applyTypingState(String state, long now, int holdMs) {
      long prevPulse = typingPulseUntilMs;
      long prevFade = typingDoneFadeStartMs;
      String normalized = normalizeTypingState(state);
      if ("done".equals(normalized)) {
        if (hasTypingActivity()) {
          typingPulseUntilMs = 0L;
          typingDoneFadeStartMs = now;
        }
      } else {
        long until = now + Math.max(500L, holdMs);
        if (until > typingPulseUntilMs) {
          typingPulseUntilMs = until;
        }
        typingDoneFadeStartMs = 0L;
      }
      return prevPulse != typingPulseUntilMs || prevFade != typingDoneFadeStartMs;
    }

    void clearTypingActivityIfExpired(long now, int fadeMs) {
      int fadeWindow = Math.max(1, fadeMs);
      if (typingDoneFadeStartMs > 0L) {
        if (now - typingDoneFadeStartMs >= fadeWindow) {
          typingPulseUntilMs = 0L;
          typingDoneFadeStartMs = 0L;
        }
        return;
      }
      if (typingPulseUntilMs <= 0L) return;
      if (now - typingPulseUntilMs >= fadeWindow) {
        typingPulseUntilMs = 0L;
      }
    }

    float typingDotAlpha(long now, int pulseMs, int fadeMs) {
      int pulseWindow = Math.max(300, pulseMs);
      int fadeWindow = Math.max(1, fadeMs);

      if (typingDoneFadeStartMs > 0L) {
        return fadeAlpha(now, typingDoneFadeStartMs, fadeWindow);
      }
      if (typingPulseUntilMs <= 0L) return 0f;
      if (now < typingPulseUntilMs) {
        double phase = (now % pulseWindow) / (double) pulseWindow;
        double wave = 0.5d + (0.5d * Math.sin((phase * (Math.PI * 2.0d)) - (Math.PI / 2.0d)));
        return (float) (0.35d + (0.65d * wave));
      }
      return fadeAlpha(now, typingPulseUntilMs, fadeWindow);
    }

    private static float fadeAlpha(long now, long fadeStartMs, int fadeWindowMs) {
      if (fadeStartMs <= 0L) return 0f;
      long elapsed = now - fadeStartMs;
      if (elapsed <= 0L) return 1f;
      if (elapsed >= fadeWindowMs) return 0f;
      float progress = elapsed / (float) fadeWindowMs;
      return Math.max(0f, 1f - progress);
    }

    private static String normalizeTypingState(String state) {
      String s = Objects.toString(state, "").trim().toLowerCase(java.util.Locale.ROOT);
      return switch (s) {
        case "active", "composing", "paused" -> "active";
        case "done", "inactive" -> "done";
        default -> "active";
      };
    }

    @Override
    public String toString() {
      return label;
    }
  }

  @PreDestroy
  void shutdown() {
    try {
      if (settingsBus != null && settingsListener != null) {
        settingsBus.removeListener(settingsListener);
        settingsListener = null;
      }
      if (jfrRuntimeEventsService != null && jfrStateListener != null) {
        jfrRuntimeEventsService.removeStateListener(jfrStateListener);
        jfrStateListener = null;
      }
      if (typingActivityTimer != null) typingActivityTimer.stop();
      if (treeWheelSelectionDecorator != null) treeWheelSelectionDecorator.close();
      nodeActions.close();
    } catch (Exception ignored) {
    }
    disposables.dispose();
  }
}
