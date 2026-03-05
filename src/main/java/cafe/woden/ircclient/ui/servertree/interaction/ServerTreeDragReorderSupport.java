package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Drag/reorder hit-testing and insertion-line rendering for the server tree. */
public final class ServerTreeDragReorderSupport {

  private final JTree tree;
  private final Map<String, ServerNodes> servers;
  private final ServerTreeNodeClassifier nodeClassifier;
  private final Predicate<DefaultMutableTreeNode> isServerNode;
  private final Predicate<DefaultMutableTreeNode> isChannelListLeafNode;
  private final Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
      builtInLayoutNodeKindForNode;
  private final Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
      rootSiblingNodeKindForNode;
  private final Function<DefaultMutableTreeNode, String> backendIdForNetworksGroupNode;

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

  public ServerTreeDragReorderSupport(
      JTree tree,
      Map<String, ServerNodes> servers,
      ServerTreeNodeClassifier nodeClassifier,
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isChannelListLeafNode,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
          builtInLayoutNodeKindForNode,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
          rootSiblingNodeKindForNode,
      Function<DefaultMutableTreeNode, String> backendIdForNetworksGroupNode) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.servers = Objects.requireNonNull(servers, "servers");
    this.nodeClassifier = Objects.requireNonNull(nodeClassifier, "nodeClassifier");
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
    this.isChannelListLeafNode =
        Objects.requireNonNull(isChannelListLeafNode, "isChannelListLeafNode");
    this.builtInLayoutNodeKindForNode =
        Objects.requireNonNull(builtInLayoutNodeKindForNode, "builtInLayoutNodeKindForNode");
    this.rootSiblingNodeKindForNode =
        Objects.requireNonNull(rootSiblingNodeKindForNode, "rootSiblingNodeKindForNode");
    this.backendIdForNetworksGroupNode =
        Objects.requireNonNull(backendIdForNetworksGroupNode, "backendIdForNetworksGroupNode");
  }

  public boolean isDraggableChannelNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return false;
    if (nodeData.ref == null || !nodeData.ref.isChannel()) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    return parent != null && (isServerNode.test(parent) || isChannelListLeafNode.test(parent));
  }

  public boolean isMovableBuiltInNode(DefaultMutableTreeNode node) {
    RuntimeConfigStore.ServerTreeBuiltInLayoutNode nodeKind =
        builtInLayoutNodeKindForNode.apply(node);
    if (nodeKind == null) return false;
    String sid = nodeClassifier.owningServerIdForNode(node);
    if (sid.isBlank()) return false;
    ServerNodes serverNodes = servers.get(sid);
    if (serverNodes == null) return false;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    return parent == serverNodes.serverNode || parent == serverNodes.otherNode;
  }

  public boolean isRootSiblingReorderableNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    String sid = nodeClassifier.owningServerIdForNode(node);
    if (sid.isBlank()) return false;
    ServerNodes serverNodes = servers.get(sid);
    if (serverNodes == null || serverNodes.serverNode == null) return false;
    if (node.getParent() != serverNodes.serverNode) return false;
    return rootSiblingNodeKindForNode.apply(node) != null;
  }

  public int minInsertIndex(DefaultMutableTreeNode parentNode) {
    if (parentNode == null) return 0;
    if (isChannelListLeafNode.test(parentNode)) return 0;

    int min = 0;
    int count = parentNode.getChildCount();
    while (min < count) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parentNode.getChildAt(min);
      Object userObject = child.getUserObject();
      if (userObject instanceof ServerTreeNodeData nodeData) {
        if (nodeData.ref == null || nodeData.ref.isStatus() || nodeData.ref.isUiOnly()) {
          min++;
          continue;
        }
      } else if (nodeClassifier.isInterceptorsGroupNode(child)) {
        min++;
        continue;
      }
      break;
    }
    return min;
  }

  public int maxInsertIndex(DefaultMutableTreeNode parentNode) {
    if (parentNode == null) return 0;
    if (isChannelListLeafNode.test(parentNode)) return parentNode.getChildCount();

    int index = parentNode.getChildCount();
    while (index > 0) {
      DefaultMutableTreeNode tail = (DefaultMutableTreeNode) parentNode.getChildAt(index - 1);
      if (isReservedServerTailNode(tail)) {
        index--;
        continue;
      }
      break;
    }
    return index;
  }

  public void setInsertionLineForIndex(DefaultMutableTreeNode parent, int insertBeforeIndex) {
    setInsertionLine(insertionLineForIndex(parent, insertBeforeIndex));
  }

  public void clearInsertionLine() {
    setInsertionLine(null);
  }

  public void paintInsertionLine(Graphics graphics) {
    InsertionLine line = insertionLine;
    if (line == null) return;

    Graphics2D g2 = (Graphics2D) graphics.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Color color = UIManager.getColor("Component.accentColor");
      if (color == null) color = UIManager.getColor("Tree.selectionBorderColor");
      if (color == null) color = UIManager.getColor("Tree.selectionForeground");
      if (color == null) color = Color.BLACK;
      g2.setColor(color);

      g2.setStroke(new BasicStroke(2f));
      g2.drawLine(line.x1, line.y, line.x2, line.y);
    } finally {
      g2.dispose();
    }
  }

  private boolean isReservedServerTailNode(DefaultMutableTreeNode node) {
    String backendId = backendIdForNetworksGroupNode.apply(node);
    return nodeClassifier.isPrivateMessagesGroupNode(node)
        || (backendId != null && !backendId.isBlank());
  }

  private void setInsertionLine(InsertionLine line) {
    InsertionLine old = insertionLine;
    insertionLine = line;
    if (old != null) tree.repaint(old.repaintRect());
    if (line != null) tree.repaint(line.repaintRect());
  }

  private InsertionLine insertionLineForIndex(
      DefaultMutableTreeNode parent, int insertBeforeIndex) {
    if (parent == null) return null;

    int childCount = parent.getChildCount();
    if (childCount == 0) {
      Rectangle parentRect = tree.getPathBounds(new TreePath(parent.getPath()));
      if (parentRect == null) return null;
      int x1 = Math.max(0, parentRect.x);
      int x2 = Math.max(x1 + 1, tree.getWidth() - 4);
      int y = parentRect.y + parentRect.height - 1;
      return new InsertionLine(x1, y, x2);
    }

    int index = Math.max(0, Math.min(childCount, insertBeforeIndex));
    Rectangle rowBounds;
    int y;
    if (index >= childCount) {
      DefaultMutableTreeNode last = (DefaultMutableTreeNode) parent.getChildAt(childCount - 1);
      rowBounds = tree.getPathBounds(new TreePath(last.getPath()));
      if (rowBounds == null) return null;
      y = rowBounds.y + rowBounds.height - 1;
    } else {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(index);
      rowBounds = tree.getPathBounds(new TreePath(child.getPath()));
      if (rowBounds == null) return null;
      y = rowBounds.y;
    }

    int x1 = Math.max(0, rowBounds.x);
    int x2 = Math.max(x1 + 1, tree.getWidth() - 4);
    return new InsertionLine(x1, y, x2);
  }
}
