package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.ConnectionState;
import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.util.TreeNodeActions;
import cafe.woden.ircclient.ui.util.TreeWheelSelectionDecorator;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Enumeration;
import javax.swing.JMenuItem;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.JViewport;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import jakarta.annotation.PreDestroy;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;

@org.springframework.stereotype.Component
@Lazy
public class ServerTreeDockable extends JPanel implements Dockable {
  private static final Logger log = LoggerFactory.getLogger(ServerTreeDockable.class);

  // UI label for the per-server "status" transcript target.
  // The target id remains "status" internally; this is just what the user sees in the tree.
  private static final String STATUS_LABEL = "Server";
  private static final String CHANNEL_LIST_LABEL = "Channel List";
  private static final String DCC_TRANSFERS_LABEL = "DCC Transfers";
  private static final String BOUNCER_CONTROL_LABEL = "Bouncer Control";
  private static final String SOJU_NETWORKS_GROUP_LABEL = "Soju Networks";
  private static final String ZNC_NETWORKS_GROUP_LABEL = "ZNC Networks";
  public static final String PROP_CHANNEL_LIST_NODES_VISIBLE = "channelListNodesVisible";
  public static final String PROP_DCC_TRANSFERS_NODES_VISIBLE = "dccTransfersNodesVisible";

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

  private final FlowableProcessor<TargetRef> clearLogRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> openPinnedChatRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("IRC");
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

  private final JTree tree = new JTree(model) {
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      ServerTreeDockable.this.paintInsertionLine(g);
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

  private final ServerTreeCellRenderer treeCellRenderer = new ServerTreeCellRenderer();

  private final JLabel statusLabel = new JLabel("Disconnected");

  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;

  private final Map<String, ServerNodes> servers = new HashMap<>();
  private final Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
  private final Map<TargetRef, Boolean> privateMessageOnlineByTarget = new HashMap<>();

  private final Map<String, ConnectionState> serverStates = new HashMap<>();

  private final ServerCatalog serverCatalog;

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
  private final ServerDialogs serverDialogs;
  private volatile boolean showChannelListNodes = false;
  private volatile boolean showDccTransfersNodes = false;

  public ServerTreeDockable(
      ServerCatalog serverCatalog,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      NotificationStore notificationStore,
      ServerDialogs serverDialogs) {
    super(new BorderLayout());

    this.serverCatalog = serverCatalog;
    this.sojuAutoConnect = sojuAutoConnect;
    this.zncAutoConnect = zncAutoConnect;
    this.notificationStore = notificationStore;
    this.serverDialogs = serverDialogs;

    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

    header.add(connectBtn);
    header.add(Box.createHorizontalStrut(6));
    header.add(disconnectBtn);
    header.add(Box.createHorizontalStrut(10));
    header.add(statusLabel);
    header.add(Box.createHorizontalGlue());

    add(header, BorderLayout.NORTH);
    setConnectionControlsEnabled(true, false);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(0);

    tree.setCellRenderer(treeCellRenderer);
    ToolTipManager.sharedInstance().registerComponent(tree);
    tree.addPropertyChangeListener("UI", e -> SwingUtilities.invokeLater(this::refreshTreeLayoutAfterUiChange));
    this.nodeActions = new TreeNodeActions<>(
        tree,
        model,
        new ServerTreeNodeReorderPolicy(this::isServerNode),
        n -> {
          Object uo = n.getUserObject();
          if (uo instanceof NodeData nd) return nd.ref;
          return null;
        },
        ref -> closeTargetRequests.onNext(ref)
    );
    installTreeKeyBindings();

    JScrollPane scroll = new JScrollPane(tree);
    scroll.setPreferredSize(new Dimension(260, 400));
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    treeWheelSelectionDecorator = TreeWheelSelectionDecorator.decorate(tree, scroll);
    add(scroll, BorderLayout.CENTER);
    if (serverCatalog != null) {
      syncServers(serverCatalog.entries());

      disposables.add(
          serverCatalog.updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(this::syncServers,
                  err -> log.error("[ircafe] server catalog stream error", err))
      );
    }
    if (notificationStore != null) {
      disposables.add(
          notificationStore.changes()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  ch -> refreshNotificationsCount(ch.serverId()),
                  err -> log.error("[ircafe] notification store stream error", err))
      );
    }

    if (sojuAutoConnect != null) {
      disposables.add(
          sojuAutoConnect.updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  __ -> refreshSojuAutoConnectBadges(),
                  err -> log.error("[ircafe] soju auto-connect store stream error", err))
      );
    }

    if (zncAutoConnect != null) {
      disposables.add(
          zncAutoConnect.updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  __ -> refreshZncAutoConnectBadges(),
                  err -> log.error("[ircafe] znc auto-connect store stream error", err))
      );
    }
    TreeSelectionListener tsl = e -> {
      if (suppressSelectionBroadcast) return;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
      if (node == null) return;
      Object uo = node.getUserObject();
      if (uo instanceof NodeData nd) {
        selections.onNext(nd.ref);
      }
    };
    tree.addTreeSelectionListener(tsl);
    MouseAdapter popupListener = new MouseAdapter() {
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
        TreePath path = tree.getPathForLocation(x, y);
        if (path == null) {
          return;
        }
        suppressSelectionBroadcast = true;
        try {
          tree.setSelectionPath(path);
        } finally {
          suppressSelectionBroadcast = false;
        }
        nodeActions.refreshEnabledState();

        JPopupMenu menu = buildPopupMenu(path);
        if (menu == null || menu.getComponentCount() == 0) return;
        menu.show(tree, x, y);
      }
    };
    tree.addMouseListener(popupListener);
    MouseAdapter middleDragReorder = new MouseAdapter() {
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

        int desiredInsertBeforeRemoval = computeDesiredInsertBeforeRemoval(e, targetPath, targetNode);
        if (desiredInsertBeforeRemoval < 0) return null;

        desiredInsertBeforeRemoval = Math.max(minInsertIndex(dragParent),
            Math.min(maxInsertIndex(dragParent), desiredInsertBeforeRemoval));

        return ServerTreeDockable.this.insertionLineForIndex(dragParent, desiredInsertBeforeRemoval);
      }

      private int computeDesiredInsertBeforeRemoval(MouseEvent e, TreePath targetPath, DefaultMutableTreeNode targetNode) {
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
        desiredInsertBeforeRemoval = Math.max(minInsertIndex(dragParent),
            Math.min(maxInsertIndex(dragParent), desiredInsertBeforeRemoval));
        int desiredAfterRemoval = desiredInsertBeforeRemoval;
        if (desiredAfterRemoval > dragFromIndex) desiredAfterRemoval--;
        if (desiredAfterRemoval == dragFromIndex) return;
        model.removeNodeFromParent(dragNode);
        desiredAfterRemoval = Math.max(minInsertIndex(dragParent),
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
      }
    };

    tree.addMouseListener(middleDragReorder);
    tree.addMouseMotionListener(middleDragReorder);
    SwingUtilities.invokeLater(() -> {
      TargetRef first = servers.values().stream()
          .findFirst()
          .map(sn -> sn.statusRef)
          .orElse(null);
      if (first != null) {
        selectTarget(first);
      } else {
        tree.setSelectionPath(new TreePath(root.getPath()));
      }
    });
  }

  private JPopupMenu buildPopupMenu(TreePath path) {
    if (path == null) return null;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (node == null) return null;
    if (isServerNode(node)) {
      String serverId = Objects.toString(node.getUserObject(), "").trim();
      if (serverId.isEmpty()) return null;

      String pretty = prettyServerLabel(serverId);

      ConnectionState state = serverStates.getOrDefault(serverId, ConnectionState.DISCONNECTED);
      JPopupMenu menu = new JPopupMenu();
      boolean canReorder = isRootServerNode(node);
      if (canReorder) {
        menu.add(new JMenuItem(moveNodeUpAction()));
        menu.add(new JMenuItem(moveNodeDownAction()));
        menu.addSeparator();
      }

      JMenuItem connectOne = new JMenuItem("Connect \"" + pretty + "\"");
      connectOne.setEnabled(state == ConnectionState.DISCONNECTED);
      connectOne.addActionListener(ev -> connectServerRequests.onNext(serverId));
      menu.add(connectOne);

      JMenuItem disconnectOne = new JMenuItem("Disconnect \"" + pretty + "\"");
      disconnectOne.setEnabled(state == ConnectionState.CONNECTING
          || state == ConnectionState.CONNECTED
          || state == ConnectionState.RECONNECTING);
      disconnectOne.addActionListener(ev -> disconnectServerRequests.onNext(serverId));
      menu.add(disconnectOne);

      // Ephemeral servers can be promoted to persisted servers. This is especially useful for
      // bouncer-discovered networks that would otherwise disappear when the bouncer disconnects.
      boolean ephemeral = serverCatalog != null
          && serverCatalog.findEntry(serverId).map(ServerEntry::ephemeral).orElse(false);
      if (ephemeral) {
        menu.addSeparator();
        JMenuItem save = new JMenuItem("Save \"" + pretty + "\"…");
        save.setIcon(SvgIcons.action("plus", 16));
        save.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
        save.setEnabled(serverDialogs != null);
        save.addActionListener(ev -> {
          if (serverDialogs == null) return;
          Window w = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
          serverDialogs.openSaveEphemeralServer(w, serverId);
        });
        menu.add(save);
      }

      // Only show server editing for the primary, configured server entries directly under the IRC root.
      if (canReorder) {
        boolean editable = serverDialogs != null
            && serverCatalog != null
            && serverCatalog.findEntry(serverId).map(se -> !se.ephemeral()).orElse(false);

        menu.addSeparator();
        JMenuItem edit = new JMenuItem("Edit \"" + pretty + "\"…");
        edit.setIcon(SvgIcons.action("edit", 16));
        edit.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
        edit.setEnabled(editable);
        edit.addActionListener(ev -> {
          Window w = SwingUtilities.getWindowAncestor(ServerTreeDockable.this);
          serverDialogs.openEditServer(w, serverId);
        });
        menu.add(edit);
      }

      if (isSojuEphemeralServer(serverId)) {
        String originId = sojuOriginByServerId.get(serverId);
        String networkKey = serverDisplayNames.getOrDefault(serverId, serverId);
        boolean enabled = originId != null && sojuAutoConnect != null
            && sojuAutoConnect.isEnabled(originId, networkKey);

        menu.addSeparator();
        JCheckBoxMenuItem auto = new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
        auto.setSelected(enabled);
        auto.setEnabled(originId != null && !originId.isBlank() && sojuAutoConnect != null);
        auto.addActionListener(ev -> {
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
        boolean enabled = originId != null && zncAutoConnect != null
            && zncAutoConnect.isEnabled(originId, networkKey);

        menu.addSeparator();
        JCheckBoxMenuItem auto = new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
        auto.setSelected(enabled);
        auto.setEnabled(originId != null && !originId.isBlank() && zncAutoConnect != null);
        auto.addActionListener(ev -> {
          if (originId == null || originId.isBlank() || zncAutoConnect == null) return;
          boolean en = auto.isSelected();
          zncAutoConnect.setEnabled(originId, networkKey, en);
          refreshZncAutoConnectBadges();
        });
        menu.add(auto);
      }

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
            JMenuItem leave = new JMenuItem("Leave \"" + nd.label + "\"");
            leave.addActionListener(ev -> closeTargetRequests.onNext(nd.ref));
            menu.add(leave);
          } else {
            JMenuItem close = new JMenuItem("Close \"" + nd.label + "\"");
            close.addActionListener(ev -> closeTargetRequests.onNext(nd.ref));
            menu.add(close);
          }
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

    String msg = "Clear log for " + scope + " \"" + pretty + "\"?\n\n"
        + "This will permanently delete the persisted chat history for this target.";

    int choice = JOptionPane.showConfirmDialog(
        w,
        msg,
        "Clear Log",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE
    );

    if (choice == JOptionPane.YES_OPTION) {
      clearLogRequests.onNext(target);
    }
  }

private boolean isServerNode(DefaultMutableTreeNode node) {
  if (node == null) return false;
  Object uo = node.getUserObject();
  if (!(uo instanceof String id)) return false;
  ServerNodes sn = servers.get(id);
  return sn != null && sn.serverNode == node;
}

private boolean isRootServerNode(DefaultMutableTreeNode node) {
  return node != null && node.getParent() == root && isServerNode(node);
}


  private boolean isDraggableChannelNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd)) return false;
    if (nd.ref == null || !nd.ref.isChannel()) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    return parent != null && isServerNode(parent);
  }

  private int minInsertIndex(DefaultMutableTreeNode serverNode) {
    if (serverNode == null) return 0;
    int min = 0;
    int count = serverNode.getChildCount();
    while (min < count) {
      Object uo = ((DefaultMutableTreeNode) serverNode.getChildAt(min)).getUserObject();
      if (uo instanceof NodeData nd && nd.ref != null) {
        if (nd.ref.isStatus() || nd.ref.isUiOnly()) {
          min++;
          continue;
        }
      }
      break;
    }
    return min;
  }

  private int maxInsertIndex(DefaultMutableTreeNode serverNode) {
    if (serverNode == null) return 0;
    int idx = serverNode.getChildCount();
    while (idx > 0) {
      DefaultMutableTreeNode tail = (DefaultMutableTreeNode) serverNode.getChildAt(idx - 1);
      if (isReservedServerTailNode(tail)) {
        idx--;
        continue;
      }
      break;
    }
    return idx;
  }

  private boolean isReservedServerTailNode(DefaultMutableTreeNode node) {
    return isPrivateMessagesGroupNode(node) || isSojuNetworksGroupNode(node) || isZncNetworksGroupNode(node);
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

private InsertionLine insertionLineForIndex(DefaultMutableTreeNode parent, int insertBeforeIndex) {
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

  private boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String s)) return false;
    String label = s.trim();
    return label.equalsIgnoreCase("Private messages") || label.equalsIgnoreCase("Private Messages");
  }

  @Override
  public String getPersistentID() {
    return ID;
  }

  @Override
  public String getTabText() {
    return "Servers";
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

  public Flowable<TargetRef> clearLogRequests() {
    return clearLogRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> openPinnedChatRequests() {
    return openPinnedChatRequests.onBackpressureLatest();
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
  }

  public void setStatusText(String text) {
    statusLabel.setText(Objects.toString(text, ""));
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
    return showChannelListNodes;
  }

  public boolean isDccTransfersNodesVisible() {
    return showDccTransfersNodes;
  }

  public void setChannelListNodesVisible(boolean visible) {
    boolean old = showChannelListNodes;
    boolean next = visible;
    if (old == next) return;
    showChannelListNodes = next;
    syncUiLeafVisibility();
    firePropertyChange(PROP_CHANNEL_LIST_NODES_VISIBLE, old, next);
  }

  public void setDccTransfersNodesVisible(boolean visible) {
    boolean old = showDccTransfersNodes;
    boolean next = visible;
    if (old == next) return;
    showDccTransfersNodes = next;
    syncUiLeafVisibility();
    firePropertyChange(PROP_DCC_TRANSFERS_NODES_VISIBLE, old, next);
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
    tree.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
        "ircafe.tree.nodeMoveUp");
    tree.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
        "ircafe.tree.nodeMoveDown");
    tree.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
        "ircafe.tree.closeNode");
    tree.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
        "ircafe.tree.closeNode");
    tree.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
        "ircafe.tree.openPinnedDock");

    tree.getActionMap().put("ircafe.tree.nodeMoveUp", new AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        moveNodeUpAction().actionPerformed(e);
      }
    });
    tree.getActionMap().put("ircafe.tree.nodeMoveDown", new AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        moveNodeDownAction().actionPerformed(e);
      }
    });
    tree.getActionMap().put("ircafe.tree.closeNode", new AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent e) {
        closeNodeAction().actionPerformed(e);
      }
    });
    tree.getActionMap().put("ircafe.tree.openPinnedDock", new AbstractAction() {
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
    java.util.Iterator<Map.Entry<TargetRef, Boolean>> it = privateMessageOnlineByTarget.entrySet().iterator();
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

  private void syncUiLeafVisibility() {
    TargetRef selected = selectedTargetRef();

    for (ServerNodes sn : servers.values()) {
      if (sn == null || sn.serverNode == null) continue;

      ensureUiLeafVisible(sn, sn.channelListRef, CHANNEL_LIST_LABEL, showChannelListNodes);
      ensureUiLeafVisible(sn, sn.dccTransfersRef, DCC_TRANSFERS_LABEL, showDccTransfersNodes);
    }

    if (selected != null) {
      if (selected.isChannelList() && !showChannelListNodes) {
        selectTarget(new TargetRef(selected.serverId(), "status"));
      } else if (selected.isDccTransfers() && !showDccTransfersNodes) {
        selectTarget(new TargetRef(selected.serverId(), "status"));
      }
    }
  }

  private boolean ensureUiLeafVisible(
      ServerNodes sn,
      TargetRef ref,
      String label,
      boolean visible
  ) {
    if (sn == null || ref == null) return false;
    DefaultMutableTreeNode existing = leaves.get(ref);
    if (!visible) {
      if (existing == null) return false;
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode) existing.getParent();
      int idx = parent == null ? -1 : parent.getIndex(existing);
      leaves.remove(ref);
      if (parent != null) {
        Object[] removed = new Object[] { existing };
        if (idx < 0) {
          parent.remove(existing);
          model.nodeStructureChanged(parent);
        } else {
          parent.remove(existing);
          model.nodesWereRemoved(parent, new int[] { idx }, removed);
        }
      }
      return true;
    }

    if (existing != null) return false;
    DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new NodeData(ref, label));
    leaves.put(ref, leaf);
    int idx = fixedLeafInsertIndexFor(sn, ref);
    sn.serverNode.insert(leaf, idx);
    model.nodesWereInserted(sn.serverNode, new int[] { idx });
    return true;
  }

  private int fixedLeafInsertIndexFor(ServerNodes sn, TargetRef ref) {
    int idx = 0;
    if (leaves.containsKey(sn.statusRef)) idx++;
    if (leaves.containsKey(sn.notificationsRef)) idx++;

    boolean hasChannelList = leaves.containsKey(sn.channelListRef);
    boolean hasDccTransfers = leaves.containsKey(sn.dccTransfersRef);

    if (ref.equals(sn.channelListRef)) {
      return idx;
    }

    if (hasChannelList) idx++;
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
    if (ref.isChannelList() && !showChannelListNodes) {
      setChannelListNodesVisible(true);
    }
    if (ref.isDccTransfers() && !showDccTransfersNodes) {
      setDccTransfersNodesVisible(true);
    }
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
    } else if (ref.isChannelList()) {
      parent = sn.serverNode;
    } else if (ref.isDccTransfers()) {
      parent = sn.serverNode;
    } else if (ref.isChannel()) {
      parent = sn.serverNode;
    } else {
      parent = sn.pmNode;
    }

    String leafLabel = ref.target();
    if (ref.isNotifications()) {
      leafLabel = "Notifications";
    } else if (ref.isChannelList()) {
      leafLabel = CHANNEL_LIST_LABEL;
    } else if (ref.isDccTransfers()) {
      leafLabel = DCC_TRANSFERS_LABEL;
    }
    DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new NodeData(ref, leafLabel));
    leaves.put(ref, leaf);
    if (isPrivateMessageTarget(ref)) {
      privateMessageOnlineByTarget.putIfAbsent(ref, Boolean.FALSE);
    }
    int idx;
    if (ref.isChannel() && parent == sn.serverNode) {
      int beforePm = sn.serverNode.getChildCount();
      if (beforePm > 0) {
        DefaultMutableTreeNode last = (DefaultMutableTreeNode) sn.serverNode.getChildAt(beforePm - 1);
        if (isPrivateMessagesGroupNode(last)) {
          beforePm = beforePm - 1;
        }
      }
      idx = Math.max(minInsertIndex(sn.serverNode), beforePm);
    } else {
      idx = parent.getChildCount();
    }
    parent.insert(leaf, idx);

    model.reload(parent);
    tree.expandPath(new TreePath(parent.getPath()));
  }

  public void selectTarget(TargetRef ref) {
    ensureNode(ref);
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    TreePath path = new TreePath(node.getPath());
    tree.setSelectionPath(path);
    tree.scrollPathToVisible(path);
  }

  public void removeTarget(TargetRef ref) {
    if (ref == null || ref.isStatus() || ref.isUiOnly()) return;
    DefaultMutableTreeNode node = leaves.remove(ref);
    if (node == null) return;
    if (isPrivateMessageTarget(ref)) {
      privateMessageOnlineByTarget.remove(ref);
    }

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    if (parent != null) {
      parent.remove(node);
      model.reload(parent);
    } else {
      model.reload(root);
    }
  }

  public void markUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;
    nd.unread++;
    model.nodeChanged(node);
  }
  public void markHighlight(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;
    nd.highlightUnread++;
    model.nodeChanged(node);
  }

  public void clearUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;
    if (nd.unread == 0 && nd.highlightUnread == 0) return;
    nd.unread = 0;
    nd.highlightUnread = 0;
    model.nodeChanged(node);
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
    if (eph) ephemeralServerIds.add(id); else ephemeralServerIds.remove(id);

    if (!Objects.equals(prev, next) || eph != prevEph) {
      ServerNodes sn = servers.get(id);
      if (sn != null) model.nodeChanged(sn.serverNode);
    }
  }

  model.reload(root);
  SwingUtilities.invokeLater(() -> {
    TreePath sel = tree.getSelectionPath();
    if (sel != null) {
      Object last = sel.getLastPathComponent();
      if (last instanceof DefaultMutableTreeNode n1) {
        if (n1.getPath() != null && n1.getRoot() == root) {
          return;
        }
      }
    }

    TargetRef first = servers.values().stream()
        .findFirst()
        .map(sn -> sn.statusRef)
        .orElse(null);
    if (first != null) {
      selectTarget(first);
    } else {
      tree.setSelectionPath(new TreePath(root.getPath()));
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

    // Insert right after fixed leaves (status + notifications + optional UI-only leaves) and before PMs.
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

    // Insert right after fixed leaves (status + notifications + optional UI-only leaves) and before PMs.
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
    if (leaves.containsKey(originNodes.channelListRef)) count++;
    if (leaves.containsKey(originNodes.dccTransfersRef)) count++;
    return count;
  }

  private String toolTipForEvent(MouseEvent event) {
    if (event == null) return null;
    TreePath path = tree.getPathForLocation(event.getX(), event.getY());
    if (path == null) return null;
    Object comp = path.getLastPathComponent();
    if (!(comp instanceof DefaultMutableTreeNode node)) return null;

    if (isSojuNetworksGroupNode(node)) {
      return "Soju networks discovered from the bouncer (not saved).";
    }

    if (isZncNetworksGroupNode(node)) {
      return "ZNC networks discovered from the bouncer (not saved).";
    }

    Object uo = node.getUserObject();
    if (uo instanceof NodeData nd && nd.ref != null) {
      if (nd.ref.isStatus() && BOUNCER_CONTROL_LABEL.equals(nd.label)
          && (sojuBouncerControlServerIds.contains(nd.ref.serverId())
              || zncBouncerControlServerIds.contains(nd.ref.serverId()))) {
        return "Bouncer Control connection (used to discover bouncer networks).";
      }
    }

    if (uo instanceof String serverId && isServerNode(node) && isSojuEphemeralServer(serverId)) {
      String origin = Objects.toString(sojuOriginByServerId.get(serverId), "").trim();
      String display = serverDisplayNames.getOrDefault(serverId, serverId);
      boolean auto = !origin.isEmpty() && sojuAutoConnect != null && sojuAutoConnect.isEnabled(origin, display);
      String tip = "Discovered from soju; not saved.";
      if (auto) tip += " Auto-connect enabled.";
      if (!origin.isEmpty()) tip += " Origin: " + origin + ".";
      if (display != null && !display.isBlank()) tip += " Network: " + display + ".";
      return tip;
    }

    if (uo instanceof String serverId && isServerNode(node) && isZncEphemeralServer(serverId)) {
      String origin = Objects.toString(zncOriginByServerId.get(serverId), "").trim();
      String display = serverDisplayNames.getOrDefault(serverId, serverId);
      boolean auto = !origin.isEmpty() && zncAutoConnect != null && zncAutoConnect.isEnabled(origin, display);
      String tip = "Discovered from ZNC; not saved.";
      if (auto) tip += " Auto-connect enabled.";
      if (!origin.isEmpty()) tip += " Origin: " + origin + ".";
      if (display != null && !display.isBlank()) tip += " Network: " + display + ".";
      return tip;
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

private void removeServerRoot(String serverId) {
  ServerNodes sn = servers.remove(serverId);
  if (sn == null) return;

  serverStates.remove(serverId);
  clearPrivateMessageOnlineStates(serverId);
  leaves.entrySet().removeIf(e -> Objects.equals(e.getKey().serverId(), serverId));

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

    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(id);
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private messages");

    DefaultMutableTreeNode parent = root;
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
    TargetRef statusRef = new TargetRef(id, "status");
    DefaultMutableTreeNode statusLeaf = new DefaultMutableTreeNode(new NodeData(statusRef, statusLeafLabelForServer(id)));
    serverNode.insert(statusLeaf, 0);
    leaves.put(statusRef, statusLeaf);

    TargetRef notificationsRef = TargetRef.notifications(id);
    NodeData notificationsData = new NodeData(notificationsRef, "Notifications");
    if (notificationStore != null) {
      notificationsData.highlightUnread = notificationStore.count(id);
    }
    DefaultMutableTreeNode notificationsLeaf = new DefaultMutableTreeNode(notificationsData);
    serverNode.insert(notificationsLeaf, 1);
    leaves.put(notificationsRef, notificationsLeaf);

    TargetRef channelListRef = TargetRef.channelList(id);
    if (showChannelListNodes) {
      DefaultMutableTreeNode channelListLeaf = new DefaultMutableTreeNode(new NodeData(channelListRef, CHANNEL_LIST_LABEL));
      serverNode.insert(channelListLeaf, 2);
      leaves.put(channelListRef, channelListLeaf);
    }

    TargetRef dccTransfersRef = TargetRef.dccTransfers(id);
    if (showDccTransfersNodes) {
      int dccIndex = showChannelListNodes ? 3 : 2;
      DefaultMutableTreeNode dccTransfersLeaf = new DefaultMutableTreeNode(new NodeData(dccTransfersRef, DCC_TRANSFERS_LABEL));
      serverNode.insert(dccTransfersLeaf, dccIndex);
      leaves.put(dccTransfersRef, dccTransfersLeaf);
    }

    serverNode.add(pmNode);

    ServerNodes sn = new ServerNodes(serverNode, pmNode, statusRef, notificationsRef, channelListRef, dccTransfersRef);
    servers.put(id, sn);

    model.reload(root);
    tree.expandPath(new TreePath(serverNode.getPath()));
    refreshNotificationsCount(id);
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

  private void updateBouncerControlLabels(Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl) {
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
      TreePath rootPath = new TreePath(root.getPath());
      Set<TreePath> expanded = new HashSet<>();
      Enumeration<TreePath> en = tree.getExpandedDescendants(rootPath);
      if (en != null) {
        while (en.hasMoreElements()) {
          expanded.add(en.nextElement());
        }
      }
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

      for (TreePath p : expanded) {
        tree.expandPath(p);
      }

      tree.revalidate();
      tree.repaint();
    } catch (Exception ignored) {
    }
  }

  private static final class ServerNodes {
    final DefaultMutableTreeNode serverNode;
    final DefaultMutableTreeNode pmNode;
    final TargetRef statusRef;
    final TargetRef notificationsRef;
    final TargetRef channelListRef;
    final TargetRef dccTransfersRef;

    ServerNodes(DefaultMutableTreeNode serverNode,
        DefaultMutableTreeNode pmNode,
        TargetRef statusRef,
        TargetRef notificationsRef,
        TargetRef channelListRef,
        TargetRef dccTransfersRef) {
      this.serverNode = serverNode;
      this.pmNode = pmNode;
      this.statusRef = statusRef;
      this.notificationsRef = notificationsRef;
      this.channelListRef = channelListRef;
      this.dccTransfersRef = dccTransfersRef;
    }
  }

  

private final class ServerTreeCellRenderer extends DefaultTreeCellRenderer {
  @Override
  public java.awt.Component getTreeCellRendererComponent(
      JTree tree,
      Object value,
      boolean sel,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus) {

    java.awt.Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
    Font base = tree.getFont();
    if (base == null) base = UIManager.getFont("Tree.font");
    if (base == null) base = getFont();

    if (value instanceof DefaultMutableTreeNode node) {
      Object uo = node.getUserObject();
      if (uo instanceof NodeData nd) {
        setText(nd.toString());
        if (nd.highlightUnread > 0) {
          setFont(base.deriveFont(Font.BOLD));
        } else {
          setFont(base.deriveFont(Font.PLAIN));
        }
        if (nd.ref != null && nd.ref.isChannel()) {
          Icon icon = SvgIcons.icon("channel", 13, Palette.TREE);
          Icon disabled = SvgIcons.icon("channel", 13, Palette.TREE_DISABLED);
          setIcon(icon);
          setDisabledIcon(disabled);
        } else if (isPrivateMessageTarget(nd.ref)) {
          boolean online = Boolean.TRUE.equals(privateMessageOnlineByTarget.get(nd.ref));
          String name = online ? "pm-online" : "pm-offline";
          Palette pal = online ? Palette.TREE_PM_ONLINE : Palette.TREE_PM_OFFLINE;
          Icon icon = SvgIcons.icon(name, 13, pal);
          setIcon(icon);
          setDisabledIcon(icon);
        }
      } else if (uo instanceof String id && isServerNode(node)) {
        setText(prettyServerLabel(id));
        if (ephemeralServerIds.contains(id)) {
          setFont(base.deriveFont(Font.ITALIC));
        } else {
          setFont(base.deriveFont(Font.PLAIN));
        }
      } else {
        setFont(base.deriveFont(Font.PLAIN));
      }
    } else {
      setFont(base.deriveFont(Font.PLAIN));
    }

    return c;
  }
}

static final class NodeData {
    final TargetRef ref;
    final String label;
    int unread = 0;
    int highlightUnread = 0;

    NodeData(TargetRef ref, String label) {
      this.ref = ref;
      this.label = label;
    }

    @Override
    public String toString() {
      if (unread > 0 && highlightUnread > 0) return label + " (" + unread + ", " + highlightUnread + "!)";
      if (unread > 0) return label + " (" + unread + ")";
      if (highlightUnread > 0) return label + " (" + highlightUnread + "!)";
      return label;
    }
  }

  @PreDestroy
  void shutdown() {
    try {
      if (treeWheelSelectionDecorator != null) treeWheelSelectionDecorator.close();
      nodeActions.close();
    } catch (Exception ignored) {
    }
    disposables.dispose();
  }
}
