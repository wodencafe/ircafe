package cafe.woden.ircclient.ui;

import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@Component
@Lazy
public class ServerTreeDockable extends JPanel implements Dockable {
  public static final String ID = "serverTree";

  private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("IRC");
  private final DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("main");
  private final DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private Messages");
  private final DefaultTreeModel model = new DefaultTreeModel(root);
  private final JTree tree = new JTree(model);

  private final Map<String, DefaultMutableTreeNode> targetNodes = new HashMap<>();
  private final Map<String, Integer> unreadCounts = new HashMap<>();

  private final FlowableProcessor<String> selections =
      PublishProcessor.<String>create().toSerialized();

  private final JButton connectBtn;
  private final JButton disconnectBtn;
  private final JLabel status = new JLabel("Disconnected");

  private final FlowableProcessor<Object> disconnectClicks =
      PublishProcessor.create().toSerialized();

  public ServerTreeDockable(ConnectButton connectBtn,
      DisconnectButton disconnectBtn) {
    super(new BorderLayout());
    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;

    // Header bar
    JPanel header = new JPanel(new BorderLayout(8, 0));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    buttons.add(connectBtn);
    buttons.add(disconnectBtn);

    header.add(buttons, BorderLayout.WEST);
    header.add(status, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);

    disconnectBtn.setEnabled(false);

    disconnectBtn.addActionListener(e -> disconnectClicks.onNext(new Object()));

    // Tree setup
    root.add(serverNode);
    ensureNode("status");
    serverNode.add(pmNode);
    model.reload();

    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    tree.addTreeSelectionListener(e -> {
      Object obj = tree.getLastSelectedPathComponent();
      if (!(obj instanceof DefaultMutableTreeNode node)) return;
      Object uo = node.getUserObject();
      if (!(uo instanceof String label)) return;

      String target = stripUnread(label);
      // Only leaf targets (status, #channels, and PM nicks) are in targetNodes
      if (targetNodes.containsKey(target)) {
        selections.onNext(target);
      }
    });

    add(new JScrollPane(tree), BorderLayout.CENTER);
  }

  public Flowable<String> selectionStream() {
    return selections.onBackpressureLatest();
  }

  public void selectTarget(String target) {
    if (target == null || target.isBlank()) return;
    ensureNode(target);
    DefaultMutableTreeNode node = targetNodes.get(target);
    if (node == null) return;

    TreePath path = new TreePath(node.getPath());
    if (path.getParentPath() != null) tree.expandPath(path.getParentPath());

    tree.setSelectionPath(path);
    tree.scrollPathToVisible(path);

    selections.onNext(target);
  }

  // Call these from UiController based on connection events
  public void setConnectedUi(boolean connected) {
    status.setText(connected ? "Connected" : "Disconnected");
    connectBtn.setEnabled(!connected);
    disconnectBtn.setEnabled(connected);
  }

  public void setStatusText(String text) {
    status.setText(text);
  }

  public void ensureNode(String target) {
    if (target == null || target.isBlank()) return;
    if (targetNodes.containsKey(target)) return;

    DefaultMutableTreeNode node = new DefaultMutableTreeNode(target);
    targetNodes.put(target, node);
    unreadCounts.putIfAbsent(target, 0);

    if ("status".equals(target) || (target.startsWith("#") || target.startsWith("&"))) {
      // Keep channels/status before the PM group node
      int pmIndex = serverNode.getIndex(pmNode);
      if (pmIndex < 0) pmIndex = serverNode.getChildCount();
      serverNode.insert(node, pmIndex);
      model.reload(serverNode);
    } else {
      pmNode.add(node);
      model.reload(pmNode);
    }
  }

  public void markUnread(String target) {
    if (!targetNodes.containsKey(target)) ensureNode(target);
    int next = unreadCounts.getOrDefault(target, 0) + 1;
    unreadCounts.put(target, next);
    updateNodeLabel(target);
  }

  public void clearUnread(String target) {
    if (!targetNodes.containsKey(target)) return;
    unreadCounts.put(target, 0);
    updateNodeLabel(target);
  }

  private void updateNodeLabel(String target) {
    DefaultMutableTreeNode node = targetNodes.get(target);
    if (node == null) return;
    int unread = unreadCounts.getOrDefault(target, 0);
    node.setUserObject(unread > 0 ? target + " (" + unread + ")" : target);
    model.nodeChanged(node);
  }

  private String stripUnread(String label) {
    int idx = label.lastIndexOf(" (");
    if (idx > 0 && label.endsWith(")")) return label.substring(0, idx);
    return label;
  }

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Servers"; }
}
