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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.JMenuItem;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Server/channel tree.
 *
 */
@Component
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

  private final FlowableProcessor<TargetRef> openPinnedChatRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("IRC");
  private final DefaultTreeModel model = new DefaultTreeModel(root);
  private final JTree tree = new JTree(model);

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

  private boolean isServerNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    if (node.getParent() != root) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof String id)) return false;
    ServerNodes sn = servers.get(id);
    return sn != null && sn.serverNode == node;
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

  /** Enable/disable the global Connect/Disconnect buttons independently. */
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

  public void clearUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof NodeData nd)) return;
    if (nd.unread == 0) return;
    nd.unread = 0;
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

  static final class NodeData {
    final TargetRef ref;
    final String label;
    int unread = 0;

    NodeData(TargetRef ref, String label) {
      this.ref = ref;
      this.label = label;
    }

    @Override
    public String toString() {
      if (unread > 0) return label + " (" + unread + ")";
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

