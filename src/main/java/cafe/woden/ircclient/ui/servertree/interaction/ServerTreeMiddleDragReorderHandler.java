package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Handles middle-mouse drag-and-drop reordering for channels and built-in server nodes. */
public final class ServerTreeMiddleDragReorderHandler extends MouseAdapter {

  public interface Context {
    JTree tree();

    DefaultTreeModel model();

    boolean isDraggableChannelNode(DefaultMutableTreeNode node);

    boolean isRootSiblingReorderableNode(DefaultMutableTreeNode node);

    boolean isMovableBuiltInNode(DefaultMutableTreeNode node);

    String owningServerIdForNode(DefaultMutableTreeNode node);

    ServerNodes serverNodes(String serverId);

    RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
        DefaultMutableTreeNode node);

    RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
        DefaultMutableTreeNode node);

    int minInsertIndex(DefaultMutableTreeNode parentNode);

    int maxInsertIndex(DefaultMutableTreeNode parentNode);

    int rootBuiltInInsertIndex(ServerNodes serverNodes, int desiredIndex);

    void setInsertionLineForIndex(DefaultMutableTreeNode parent, int insertBeforeIndex);

    void clearInsertionLine();

    boolean isChannelListLeafNode(DefaultMutableTreeNode node);

    void persistCustomOrderForParent(DefaultMutableTreeNode parentNode);

    void persistBuiltInLayout(String serverId);

    void persistRootSiblingOrder(String serverId);

    void withSuppressedSelectionBroadcast(Runnable task);

    void refreshNodeActionsEnabled();
  }

  private final Context context;

  private DefaultMutableTreeNode dragNode;
  private DefaultMutableTreeNode dragParent;
  private int dragFromIndex = -1;
  private boolean dragBuiltInNode = false;
  private String dragBuiltInServerId = "";
  private boolean dragRootSiblingNode = false;
  private String dragRootSiblingServerId = "";
  private boolean dragging = false;
  private boolean draggedWasSelected = false;
  private Cursor oldCursor;

  public ServerTreeMiddleDragReorderHandler(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (!SwingUtilities.isMiddleMouseButton(e)) return;
    JTree tree = context.tree();

    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path == null) return;

    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    boolean channelDrag = context.isDraggableChannelNode(node);
    boolean rootSiblingDrag = !channelDrag && context.isRootSiblingReorderableNode(node);
    boolean builtInDrag = !channelDrag && !rootSiblingDrag && context.isMovableBuiltInNode(node);
    if (!channelDrag && !rootSiblingDrag && !builtInDrag) return;

    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
    if (parent == null) return;

    String sid = "";
    if (builtInDrag || rootSiblingDrag) {
      sid = context.owningServerIdForNode(node);
      if (sid.isBlank()) return;
      ServerNodes serverNodes = context.serverNodes(sid);
      if (serverNodes == null) return;
      if (builtInDrag && (parent != serverNodes.serverNode && parent != serverNodes.otherNode))
        return;
      if (rootSiblingDrag && parent != serverNodes.serverNode) return;
    }

    dragNode = node;
    dragParent = parent;
    dragFromIndex = parent.getIndex(node);
    dragBuiltInNode = builtInDrag;
    dragBuiltInServerId = sid;
    dragRootSiblingNode = rootSiblingDrag;
    dragRootSiblingServerId = sid;
    dragging = true;

    TreePath selectionPath = tree.getSelectionPath();
    draggedWasSelected = selectionPath != null && selectionPath.getLastPathComponent() == dragNode;

    oldCursor = tree.getCursor();
    tree.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    updateInsertionLine(e);
    e.consume();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (!dragging) return;
    JTree tree = context.tree();

    TreePath lead = tree.getClosestPathForLocation(e.getX(), e.getY());
    tree.setLeadSelectionPath(lead);

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
    if (dragNode == null || dragParent == null) {
      context.clearInsertionLine();
      return;
    }
    TreeDropTarget target =
        dragBuiltInNode
            ? computeBuiltInDropTarget(e)
            : dragRootSiblingNode ? computeRootSiblingDropTarget(e) : computeChannelDropTarget(e);
    if (target == null || target.parent() == null) {
      context.clearInsertionLine();
      return;
    }
    context.setInsertionLineForIndex(target.parent(), target.insertBeforeIndex());
  }

  private TreeDropTarget computeChannelDropTarget(MouseEvent e) {
    if (dragNode == null || dragParent == null) return null;
    JTree tree = context.tree();

    TreePath targetPath = tree.getClosestPathForLocation(e.getX(), e.getY());
    DefaultMutableTreeNode targetNode = null;
    if (targetPath != null) {
      Object last = targetPath.getLastPathComponent();
      if (last instanceof DefaultMutableTreeNode n) targetNode = n;
    }

    if (targetNode == null) {
      int idx =
          Math.max(
              context.minInsertIndex(dragParent),
              Math.min(context.maxInsertIndex(dragParent), dragFromIndex));
      return new TreeDropTarget(dragParent, idx);
    }

    if (targetNode == dragNode) {
      return null;
    }

    if (targetNode == dragParent) {
      return new TreeDropTarget(dragParent, context.minInsertIndex(dragParent));
    }

    DefaultMutableTreeNode targetParent = (DefaultMutableTreeNode) targetNode.getParent();
    if (targetParent != dragParent) {
      return null;
    }

    int idx = dragParent.getIndex(targetNode);
    Rectangle bounds = targetPath == null ? null : tree.getPathBounds(targetPath);
    boolean after = bounds != null && e.getY() > (bounds.y + (bounds.height / 2));
    int desired = idx + (after ? 1 : 0);
    desired =
        Math.max(
            context.minInsertIndex(dragParent),
            Math.min(context.maxInsertIndex(dragParent), desired));
    return new TreeDropTarget(dragParent, desired);
  }

  private TreeDropTarget computeBuiltInDropTarget(MouseEvent e) {
    if (dragNode == null || dragParent == null) return null;
    String sid = Objects.toString(dragBuiltInServerId, "").trim();
    if (sid.isEmpty()) return null;
    ServerNodes serverNodes = context.serverNodes(sid);
    if (serverNodes == null || serverNodes.serverNode == null || serverNodes.otherNode == null) {
      return null;
    }
    JTree tree = context.tree();

    TreePath targetPath = tree.getClosestPathForLocation(e.getX(), e.getY());
    DefaultMutableTreeNode targetNode = null;
    if (targetPath != null) {
      Object last = targetPath.getLastPathComponent();
      if (last instanceof DefaultMutableTreeNode n) targetNode = n;
    }

    if (targetNode == null) {
      int idx = clampBuiltInInsertIndex(serverNodes, dragParent, dragFromIndex);
      return idx < 0 ? null : new TreeDropTarget(dragParent, idx);
    }

    DefaultMutableTreeNode anchor = resolveBuiltInDropAnchor(targetNode, serverNodes);
    if (anchor == null || anchor == dragNode) return null;

    if (anchor == serverNodes.serverNode) {
      int idx =
          context.rootBuiltInInsertIndex(
              serverNodes, serverNodes.serverNode.getIndex(serverNodes.otherNode));
      return new TreeDropTarget(serverNodes.serverNode, idx);
    }

    if (anchor == serverNodes.otherNode) {
      Rectangle bounds = tree.getPathBounds(new TreePath(anchor.getPath()));
      boolean after = bounds == null || e.getY() > (bounds.y + (bounds.height / 2));
      int idx = after ? serverNodes.otherNode.getChildCount() : 0;
      idx = clampBuiltInInsertIndex(serverNodes, serverNodes.otherNode, idx);
      return idx < 0 ? null : new TreeDropTarget(serverNodes.otherNode, idx);
    }

    DefaultMutableTreeNode anchorParent = (DefaultMutableTreeNode) anchor.getParent();
    if (anchorParent == serverNodes.serverNode || anchorParent == serverNodes.otherNode) {
      int idx = anchorParent.getIndex(anchor);
      Rectangle bounds = tree.getPathBounds(new TreePath(anchor.getPath()));
      boolean after = bounds != null && e.getY() > (bounds.y + (bounds.height / 2));
      int desired = idx + (after ? 1 : 0);
      desired = clampBuiltInInsertIndex(serverNodes, anchorParent, desired);
      if (desired < 0) return null;
      return new TreeDropTarget(anchorParent, desired);
    }

    return null;
  }

  private TreeDropTarget computeRootSiblingDropTarget(MouseEvent e) {
    if (dragNode == null || dragParent == null) return null;
    String sid = Objects.toString(dragRootSiblingServerId, "").trim();
    if (sid.isEmpty()) return null;
    ServerNodes serverNodes = context.serverNodes(sid);
    if (serverNodes == null || serverNodes.serverNode == null) return null;
    if (dragParent != serverNodes.serverNode) return null;
    RuntimeConfigStore.ServerTreeRootSiblingNode draggedKind =
        context.rootSiblingNodeKindForNode(dragNode);
    if (draggedKind == null) return null;
    JTree tree = context.tree();

    TreePath targetPath = tree.getClosestPathForLocation(e.getX(), e.getY());
    DefaultMutableTreeNode targetNode = null;
    if (targetPath != null) {
      Object last = targetPath.getLastPathComponent();
      if (last instanceof DefaultMutableTreeNode n) targetNode = n;
    }

    if (targetNode == null) {
      int idx = serverNodes.serverNode.getIndex(dragNode);
      if (idx < 0) idx = 0;
      return new TreeDropTarget(serverNodes.serverNode, idx);
    }

    DefaultMutableTreeNode targetParent = (DefaultMutableTreeNode) targetNode.getParent();
    if (draggedKind == RuntimeConfigStore.ServerTreeRootSiblingNode.NOTIFICATIONS
        && targetParent == serverNodes.otherNode) {
      int idx = serverNodes.otherNode.getIndex(targetNode);
      if (idx < 0) idx = serverNodes.otherNode.getChildCount();
      Rectangle bounds = tree.getPathBounds(targetPath);
      boolean after = bounds != null && e.getY() > (bounds.y + (bounds.height / 2));
      int desired = idx + (after ? 1 : 0);
      desired = clampBuiltInInsertIndex(serverNodes, serverNodes.otherNode, desired);
      return desired < 0 ? null : new TreeDropTarget(serverNodes.otherNode, desired);
    }

    DefaultMutableTreeNode anchor = resolveRootSiblingDropAnchor(targetNode, serverNodes);
    if (anchor == null || anchor == dragNode) return null;
    if (anchor == serverNodes.serverNode) {
      int idx = serverNodes.serverNode.getIndex(dragNode);
      if (idx < 0) idx = 0;
      return new TreeDropTarget(serverNodes.serverNode, idx);
    }
    if (anchor.getParent() != serverNodes.serverNode) return null;

    int idx = serverNodes.serverNode.getIndex(anchor);
    if (idx < 0) return null;

    Rectangle bounds = tree.getPathBounds(new TreePath(anchor.getPath()));
    boolean after = bounds != null && e.getY() > (bounds.y + (bounds.height / 2));
    if (anchor == serverNodes.otherNode
        && draggedKind == RuntimeConfigStore.ServerTreeRootSiblingNode.NOTIFICATIONS
        && after) {
      int otherIdx =
          clampBuiltInInsertIndex(
              serverNodes, serverNodes.otherNode, serverNodes.otherNode.getChildCount());
      return otherIdx < 0 ? null : new TreeDropTarget(serverNodes.otherNode, otherIdx);
    }
    int desired = idx + (after ? 1 : 0);
    desired = Math.max(0, Math.min(serverNodes.serverNode.getChildCount(), desired));
    return new TreeDropTarget(serverNodes.serverNode, desired);
  }

  private DefaultMutableTreeNode resolveRootSiblingDropAnchor(
      DefaultMutableTreeNode node, ServerNodes serverNodes) {
    DefaultMutableTreeNode current = node;
    while (current != null) {
      if (current == serverNodes.serverNode) return current;
      if (current.getParent() == serverNodes.serverNode
          && context.rootSiblingNodeKindForNode(current) != null) {
        return current;
      }
      javax.swing.tree.TreeNode parent = current.getParent();
      current = (parent instanceof DefaultMutableTreeNode dmtn) ? dmtn : null;
    }
    return null;
  }

  private DefaultMutableTreeNode resolveBuiltInDropAnchor(
      DefaultMutableTreeNode node, ServerNodes serverNodes) {
    DefaultMutableTreeNode current = node;
    while (current != null) {
      if (current == serverNodes.serverNode || current == serverNodes.otherNode) return current;
      if (current == serverNodes.pmNode) return serverNodes.serverNode;
      if (context.builtInLayoutNodeKindForNode(current) != null) return current;
      javax.swing.tree.TreeNode parent = current.getParent();
      current = (parent instanceof DefaultMutableTreeNode dmtn) ? dmtn : null;
    }
    return null;
  }

  private int clampBuiltInInsertIndex(
      ServerNodes serverNodes, DefaultMutableTreeNode parent, int desiredIndex) {
    if (serverNodes == null || parent == null) return -1;
    if (parent == serverNodes.serverNode) {
      return context.rootBuiltInInsertIndex(serverNodes, desiredIndex);
    }
    if (parent == serverNodes.otherNode) {
      return Math.max(0, Math.min(serverNodes.otherNode.getChildCount(), desiredIndex));
    }
    return -1;
  }

  private void cleanup() {
    dragging = false;
    dragNode = null;
    dragParent = null;
    dragFromIndex = -1;
    dragBuiltInNode = false;
    dragBuiltInServerId = "";
    dragRootSiblingNode = false;
    dragRootSiblingServerId = "";
    draggedWasSelected = false;
    JTree tree = context.tree();
    tree.setLeadSelectionPath(null);
    context.clearInsertionLine();
    if (oldCursor != null) tree.setCursor(oldCursor);
    oldCursor = null;
  }

  private void performDrop(MouseEvent e) {
    if (dragNode == null || dragParent == null) return;
    if (dragBuiltInNode) {
      performBuiltInDrop(e);
    } else if (dragRootSiblingNode) {
      performRootSiblingDrop(e);
    } else {
      performChannelDrop(e);
    }
  }

  private void performChannelDrop(MouseEvent e) {
    TreeDropTarget target = computeChannelDropTarget(e);
    if (target == null || target.parent() == null) return;
    DefaultTreeModel model = context.model();
    JTree tree = context.tree();

    int desiredAfterRemoval = target.insertBeforeIndex();
    if (desiredAfterRemoval > dragFromIndex) desiredAfterRemoval--;
    if (desiredAfterRemoval == dragFromIndex) return;
    model.removeNodeFromParent(dragNode);
    desiredAfterRemoval =
        Math.max(
            context.minInsertIndex(dragParent),
            Math.min(context.maxInsertIndex(dragParent), desiredAfterRemoval));

    model.insertNodeInto(dragNode, dragParent, desiredAfterRemoval);
    if (draggedWasSelected) {
      context.withSuppressedSelectionBroadcast(
          () -> {
            TreePath np = new TreePath(dragNode.getPath());
            tree.setSelectionPath(np);
          });
    }

    TreePath moved = new TreePath(dragNode.getPath());
    tree.scrollPathToVisible(moved);
    context.refreshNodeActionsEnabled();
    if (context.isChannelListLeafNode(dragParent)) {
      context.persistCustomOrderForParent(dragParent);
    }
  }

  private void performBuiltInDrop(MouseEvent e) {
    String sid = Objects.toString(dragBuiltInServerId, "").trim();
    if (sid.isEmpty()) return;
    ServerNodes serverNodes = context.serverNodes(sid);
    if (serverNodes == null) return;
    RuntimeConfigStore.ServerTreeBuiltInLayoutNode draggedKind =
        context.builtInLayoutNodeKindForNode(dragNode);

    TreeDropTarget target = computeBuiltInDropTarget(e);
    if (target == null || target.parent() == null) return;
    DefaultMutableTreeNode targetParent = target.parent();
    int desiredAfterRemoval = target.insertBeforeIndex();
    DefaultTreeModel model = context.model();
    JTree tree = context.tree();

    int sourceIndex = dragParent.getIndex(dragNode);
    boolean sameParent = targetParent == dragParent;
    if (sameParent && desiredAfterRemoval > sourceIndex) desiredAfterRemoval--;
    if (sameParent && desiredAfterRemoval == sourceIndex) return;

    model.removeNodeFromParent(dragNode);
    desiredAfterRemoval = clampBuiltInInsertIndex(serverNodes, targetParent, desiredAfterRemoval);
    if (desiredAfterRemoval < 0) return;

    model.insertNodeInto(dragNode, targetParent, desiredAfterRemoval);
    dragParent = targetParent;
    context.persistBuiltInLayout(sid);
    if (draggedKind == RuntimeConfigStore.ServerTreeBuiltInLayoutNode.NOTIFICATIONS
        && targetParent == serverNodes.serverNode) {
      context.persistRootSiblingOrder(sid);
    }

    if (draggedWasSelected) {
      context.withSuppressedSelectionBroadcast(
          () -> {
            TreePath np = new TreePath(dragNode.getPath());
            tree.setSelectionPath(np);
          });
    }

    TreePath moved = new TreePath(dragNode.getPath());
    tree.scrollPathToVisible(moved);
    context.refreshNodeActionsEnabled();
  }

  private void performRootSiblingDrop(MouseEvent e) {
    String sid = Objects.toString(dragRootSiblingServerId, "").trim();
    if (sid.isEmpty()) return;
    ServerNodes serverNodes = context.serverNodes(sid);
    if (serverNodes == null || serverNodes.serverNode == null) return;
    RuntimeConfigStore.ServerTreeRootSiblingNode draggedKind =
        context.rootSiblingNodeKindForNode(dragNode);
    if (draggedKind == null) return;

    TreeDropTarget target = computeRootSiblingDropTarget(e);
    if (target == null || target.parent() == null) return;
    DefaultMutableTreeNode targetParent = target.parent();
    if (targetParent != serverNodes.serverNode && targetParent != serverNodes.otherNode) return;
    if (targetParent == serverNodes.otherNode
        && draggedKind != RuntimeConfigStore.ServerTreeRootSiblingNode.NOTIFICATIONS) {
      return;
    }
    int desiredAfterRemoval = target.insertBeforeIndex();
    DefaultTreeModel model = context.model();
    JTree tree = context.tree();

    int sourceIndex = dragParent.getIndex(dragNode);
    boolean sameParent = targetParent == dragParent;
    if (sameParent && desiredAfterRemoval > sourceIndex) desiredAfterRemoval--;
    if (sameParent && desiredAfterRemoval == sourceIndex) return;

    model.removeNodeFromParent(dragNode);
    if (targetParent == serverNodes.serverNode) {
      desiredAfterRemoval =
          Math.max(0, Math.min(targetParent.getChildCount(), desiredAfterRemoval));
    } else {
      desiredAfterRemoval = clampBuiltInInsertIndex(serverNodes, targetParent, desiredAfterRemoval);
      if (desiredAfterRemoval < 0) return;
    }
    model.insertNodeInto(dragNode, targetParent, desiredAfterRemoval);
    dragParent = targetParent;

    if (targetParent == serverNodes.otherNode) {
      context.persistBuiltInLayout(sid);
    }
    context.persistRootSiblingOrder(sid);

    if (draggedWasSelected) {
      context.withSuppressedSelectionBroadcast(
          () -> {
            TreePath np = new TreePath(dragNode.getPath());
            tree.setSelectionPath(np);
          });
    }

    TreePath moved = new TreePath(dragNode.getPath());
    tree.scrollPathToVisible(moved);
    context.refreshNodeActionsEnabled();
  }

  private record TreeDropTarget(DefaultMutableTreeNode parent, int insertBeforeIndex) {}
}
