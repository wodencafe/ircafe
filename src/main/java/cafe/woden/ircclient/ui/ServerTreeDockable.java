package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.ConnectionState;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Enumeration;
import javax.swing.JMenuItem;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.JViewport;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import jakarta.annotation.PreDestroy;

/**
 * Server/channel tree.
 *
 */
@org.springframework.stereotype.Component
@Lazy
public class ServerTreeDockable extends JPanel implements Dockable {
  private static final Logger log = LoggerFactory.getLogger(ServerTreeDockable.class);

  private final CompositeDisposable disposables = new CompositeDisposable();
  public static final String ID = "server-tree";

  private AutoCloseable treeWheelSelectionDecorator;

  /** Generic move/close behavior (used by the Window menu actions). */
  private final TreeNodeActions<TargetRef> nodeActions;

  private final FlowableProcessor<TargetRef> selections =
      PublishProcessor.<TargetRef>create().toSerialized();

  /**
   * Suppresses broadcasting selection changes into {@link #selections}.
   *
   * <p>We intentionally set tree selection on right-click so context menus can
   * reuse the same enabled/disabled state as the Window menu actions without
   * switching the active chat dock.
   */
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

  /** Optional insertion line shown during middle-click drag reorder. */
  private volatile InsertionLine insertionLine;



/**
 * Simple geometry for a horizontal insertion indicator shown while dragging.
 * Coordinates are in the tree's coordinate space.
 */
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
    // Give a little vertical room for stroke thickness.
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
      // Avoid FlatLaf/renderer text truncation ("...") when the tree's width
      // does not track the viewport width. Track width when the viewport is
      // wider than the preferred content, otherwise allow horizontal scrolling.
      // NOTE: Some docking containers may insert wrappers between the tree and the viewport.
      // Use ancestor lookup rather than assuming the direct parent is the viewport.
      JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
      if (vp == null) return false;
      return vp.getWidth() > getPreferredSize().width;
    }
  };

  private final ServerTreeCellRenderer treeCellRenderer = new ServerTreeCellRenderer();

  private final JLabel statusLabel = new JLabel("Disconnected");

  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;

  private final Map<String, ServerNodes> servers = new HashMap<>();
  private final Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();

  /** Per-server connection state for context menu enabling/disabling. */
  private final Map<String, ConnectionState> serverStates = new HashMap<>();

  private final ServerRegistry serverRegistry;


  public ServerTreeDockable(ServerRegistry serverRegistry, ConnectButton connectBtn, DisconnectButton disconnectBtn) {
    super(new BorderLayout());

    this.serverRegistry = serverRegistry;

    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;


    // Header
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

    // Initial UI state
    setConnectionControlsEnabled(true, false);

    // Tree
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(0);

    tree.setCellRenderer(treeCellRenderer);

    // After LAF/theme changes, JTree's layout cache can keep stale (often tiny)
    // row widths from before UI defaults/fonts were fully applied. That can make
    // FlatLaf render "..." even when there's plenty of room. Force a layout
    // refresh after UI changes.
    tree.addPropertyChangeListener("UI", e -> SwingUtilities.invokeLater(this::refreshTreeLayoutAfterUiChange));

    // Reuse generic helpers for move/close behavior.
    // This keeps all rules in one place (ServerTreeNodeReorderPolicy) and avoids duplicating tree math.
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

    JScrollPane scroll = new JScrollPane(tree);
    scroll.setPreferredSize(new Dimension(260, 400));
    // Ensure long node labels can be fully viewed by allowing horizontal scrolling when needed.
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    // Some docking layouts (and some Linux/WM combos) can intercept the wheel events.
    // Make sure the tree always scrolls when the pointer is over it.
    treeWheelSelectionDecorator = TreeWheelSelectionDecorator.decorate(tree, scroll);
    add(scroll, BorderLayout.CENTER);

    // Build server roots + status nodes
    if (serverRegistry != null) {
      for (IrcProperties.Server s : serverRegistry.servers()) {
        if (s == null) continue;
        addServerRoot(s.id());
      }

      disposables.add(
          serverRegistry.updates()
              .observeOn(SwingEdt.scheduler())
              .subscribe(this::syncServers,
                  err -> log.error("[ircafe] server registry stream error", err))
      );
    }

    // Selection stream
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

    // Right-click context menu
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

        // Select the node under the cursor so move/close actions reflect the clicked node,
        // but suppress selection broadcast so the main chat dock does not switch.
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
    // Middle-button drag-to-reorder for channels only.
    //
    // We intentionally avoid changing tree selection during drag so the active chat dock
    // does not switch while the user is reordering the channel list.
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
        // Lead selection gives a visual hint without changing actual selection.
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
          // Dropping on the server row -> move to the top of the channel block.
          return minInsertIndex(dragParent);
        }

        DefaultMutableTreeNode targetParent = (DefaultMutableTreeNode) targetNode.getParent();
        if (targetParent != dragParent) {
          // Only reorder within the same server node (same as Move Up/Down).
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
          // Dropping on the server row -> move to the top of the channel block.
          desiredInsertBeforeRemoval = minInsertIndex(dragParent);
        } else {
          DefaultMutableTreeNode targetParent = (DefaultMutableTreeNode) targetNode.getParent();
          if (targetParent != dragParent) {
            // Only reorder within the same server node (same as Move Up/Down).
            return;
          }

          int idx = dragParent.getIndex(targetNode);
          Rectangle r = tree.getPathBounds(targetPath);
          boolean after = r != null && e.getY() > (r.y + (r.height / 2));
          desiredInsertBeforeRemoval = idx + (after ? 1 : 0);
        }

        // Clamp into the allowed insertion range (before removal).
        desiredInsertBeforeRemoval = Math.max(minInsertIndex(dragParent),
            Math.min(maxInsertIndex(dragParent), desiredInsertBeforeRemoval));

        // Translate index to the "after removal" coordinate system.
        int desiredAfterRemoval = desiredInsertBeforeRemoval;
        if (desiredAfterRemoval > dragFromIndex) desiredAfterRemoval--;

        // If we land back where we started, no-op.
        if (desiredAfterRemoval == dragFromIndex) return;

        // Preserve node identity so any external maps that store node instances remain valid.
        model.removeNodeFromParent(dragNode);

        // Clamp again after removal (bounds shift by 1).
        desiredAfterRemoval = Math.max(minInsertIndex(dragParent),
            Math.min(maxInsertIndex(dragParent), desiredAfterRemoval));

        model.insertNodeInto(dragNode, dragParent, desiredAfterRemoval);

        // Restore selection if we moved the selected node (without switching active chat).
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

    // Default selection: first server's status, otherwise root.
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

    // Only show connect/disconnect on the server nodes (not status/channel/PM/group nodes).
    if (isServerNode(node)) {
      String serverId = Objects.toString(node.getUserObject(), "").trim();
      if (serverId.isEmpty()) return null;

      ConnectionState state = serverStates.getOrDefault(serverId, ConnectionState.DISCONNECTED);
      JPopupMenu menu = new JPopupMenu();

      // Keep these visible but disabled when not applicable, same as the Window menu.
      menu.add(new JMenuItem(moveNodeUpAction()));
      menu.add(new JMenuItem(moveNodeDownAction()));
      menu.addSeparator();

      JMenuItem connectOne = new JMenuItem("Connect \"" + serverId + "\"");
      connectOne.setEnabled(state == ConnectionState.DISCONNECTED);
      connectOne.addActionListener(ev -> connectServerRequests.onNext(serverId));
      menu.add(connectOne);

      JMenuItem disconnectOne = new JMenuItem("Disconnect \"" + serverId + "\"");
      disconnectOne.setEnabled(state == ConnectionState.CONNECTING
          || state == ConnectionState.CONNECTED
          || state == ConnectionState.RECONNECTING);
      disconnectOne.addActionListener(ev -> disconnectServerRequests.onNext(serverId));
      menu.add(disconnectOne);

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
        // Keep these visible but disabled when not applicable, same as the Window menu.
        menu.add(new JMenuItem(moveNodeUpAction()));
        menu.add(new JMenuItem(moveNodeDownAction()));

        if (nd.ref.isChannel() || nd.ref.isStatus()) {
          menu.addSeparator();
          JMenuItem clearLog = new JMenuItem("Clear Log…");
          clearLog.addActionListener(ev -> confirmAndRequestClearLog(nd.ref, nd.label));
          menu.add(clearLog);
        }

        if (!nd.ref.isStatus()) {
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
    // Only channels + status per request.
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
    if (node.getParent() != root) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String id)) return false;
    ServerNodes sn = servers.get(id);
    return sn != null && sn.serverNode == node;
  }

  private boolean isDraggableChannelNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof NodeData nd)) return false;
    if (nd.ref == null || !nd.ref.isChannel()) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    return parent != null && isServerNode(parent);
  }

  /** Minimum allowed insertion index for channel nodes under a server node. */
  private int minInsertIndex(DefaultMutableTreeNode serverNode) {
    if (serverNode == null) return 0;

    // Keep status (index 0) fixed, if present.
    if (serverNode.getChildCount() > 0) {
      Object first = ((DefaultMutableTreeNode) serverNode.getChildAt(0)).getUserObject();
      if (first instanceof NodeData nd && nd.ref != null && nd.ref.isStatus()) {
        return 1;
      }
    }
    return 0;
  }

  /** Maximum allowed insertion index for channel nodes under a server node. */
  private int maxInsertIndex(DefaultMutableTreeNode serverNode) {
    if (serverNode == null) return 0;
    int count = serverNode.getChildCount();
    if (count == 0) return 0;

    DefaultMutableTreeNode last = (DefaultMutableTreeNode) serverNode.getChildAt(count - 1);
    if (isPrivateMessagesGroupNode(last)) {
      // Insert before the group (i.e., at its current index).
      return count - 1;
    }
    return count;
  }


private void setInsertionLine(InsertionLine line) {
  InsertionLine old = this.insertionLine;
  this.insertionLine = line;

  // Repaint minimal regions to avoid flicker.
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

  /**
   * Actions for the Window menu (and any other UI that wants to expose node movement/closure).
   *
   * Enabled/disabled state updates automatically based on the tree selection.
   */
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

  public void ensureNode(TargetRef ref) {
    Objects.requireNonNull(ref, "ref");
    if (leaves.containsKey(ref)) return;

    ServerNodes sn = servers.get(ref.serverId());
    if (sn == null) {
      // If a server is unknown (e.g., it was removed), don't resurrect it.
      // The mediator already attempts to switch away from removed servers.
      if (serverRegistry == null || serverRegistry.containsId(ref.serverId()) || servers.isEmpty()) {
        sn = addServerRoot(ref.serverId());
      } else {
        return;
      }
    }

    DefaultMutableTreeNode parent;
    if (ref.isStatus()) {
      parent = sn.serverNode;
    } else if (ref.isChannel()) {
      parent = sn.serverNode;
    } else {
      parent = sn.pmNode;
    }

    DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new NodeData(ref, ref.target()));
    leaves.put(ref, leaf);

    // Insert with simple ordering: status first; channels before the PM group.
    int idx;
    if (ref.isChannel() && parent == sn.serverNode) {
      // Keep the "Private messages" group as the last child.
      idx = Math.max(1, sn.serverNode.getChildCount() - 1);
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
    if (ref == null || ref.isStatus()) return;
    DefaultMutableTreeNode node = leaves.remove(ref);
    if (node == null) return;

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

  private void syncServers(List<IrcProperties.Server> latest) {
    Set<String> newIds = new HashSet<>();
    if (latest != null) {
      for (IrcProperties.Server s : latest) {
        if (s == null) continue;
        String id = Objects.toString(s.id(), "").trim();
        if (!id.isEmpty()) newIds.add(id);
      }
    }

    // Add missing
    for (String id : newIds) {
      if (!servers.containsKey(id)) {
        addServerRoot(id);
      }
    }

    // Remove no-longer-present
    for (String existing : List.copyOf(servers.keySet())) {
      if (!newIds.contains(existing)) {
        removeServerRoot(existing);
      }
    }

    model.reload(root);

    // If the selected path became invalid (e.g., server removed), pick a sensible default.
    SwingUtilities.invokeLater(() -> {
      TreePath sel = tree.getSelectionPath();
      if (sel != null) {
        Object last = sel.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode n) {
          if (n.getPath() != null && n.getRoot() == root) {
            // still valid enough
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

  private void removeServerRoot(String serverId) {
    ServerNodes sn = servers.remove(serverId);
    if (sn == null) return;

    serverStates.remove(serverId);

    // Remove all leaves for this server.
    leaves.entrySet().removeIf(e -> Objects.equals(e.getKey().serverId(), serverId));

    // Remove the root node.
    root.remove(sn.serverNode);
  }

  private ServerNodes addServerRoot(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();
    if (id.isEmpty()) id = "(server)";
    if (servers.containsKey(id)) return servers.get(id);

    // Default per-server connection state.
    serverStates.putIfAbsent(id, ConnectionState.DISCONNECTED);

    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(id);
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private messages");
    root.add(serverNode);
    serverNode.add(pmNode);

    TargetRef statusRef = new TargetRef(id, "status");
    DefaultMutableTreeNode statusLeaf = new DefaultMutableTreeNode(new NodeData(statusRef, "status"));
    serverNode.insert(statusLeaf, 0);
    leaves.put(statusRef, statusLeaf);

    ServerNodes sn = new ServerNodes(serverNode, pmNode, statusRef);
    servers.put(id, sn);

    model.reload(root);
    tree.expandPath(new TreePath(serverNode.getPath()));
    return sn;
  }

  /** Refresh JTree UI/layout caches after LAF/theme switches to avoid stale row bounds. */
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

      // Re-apply these to ensure UI defaults don't override the intended behavior.
      tree.setRowHeight(0);
      // Refresh renderer UI defaults (icons/colors) after LAF/theme switches.
      try {
        treeCellRenderer.updateUI();
        treeCellRenderer.setOpenIcon(UIManager.getIcon("Tree.openIcon"));
        treeCellRenderer.setClosedIcon(UIManager.getIcon("Tree.closedIcon"));
        treeCellRenderer.setLeafIcon(UIManager.getIcon("Tree.leafIcon"));
      } catch (Exception ignored) {
      }
      tree.setCellRenderer(treeCellRenderer);

      // This triggers the UI/layout cache to recompute row sizes.
      model.reload(root);

      for (TreePath p : expanded) {
        tree.expandPath(p);
      }

      tree.revalidate();
      tree.repaint();
    } catch (Exception ignored) {
      // best-effort; UI should still remain functional
    }
  }

  private static final class ServerNodes {
    final DefaultMutableTreeNode serverNode;
    final DefaultMutableTreeNode pmNode;
    final TargetRef statusRef;

    ServerNodes(DefaultMutableTreeNode serverNode, DefaultMutableTreeNode pmNode, TargetRef statusRef) {
      this.serverNode = serverNode;
      this.pmNode = pmNode;
      this.statusRef = statusRef;
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

    // Prefer the tree's current font (tracks LAF changes and any runtime overrides).
    Font base = tree.getFont();
    if (base == null) base = UIManager.getFont("Tree.font");
    if (base == null) base = getFont();

    if (value instanceof DefaultMutableTreeNode node) {
      Object uo = node.getUserObject();
      if (uo instanceof NodeData nd) {
        setText(nd.toString());
        // Make highlight-unread visually stronger than normal unread.
        if (nd.highlightUnread > 0) {
          setFont(base.deriveFont(Font.BOLD));
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
