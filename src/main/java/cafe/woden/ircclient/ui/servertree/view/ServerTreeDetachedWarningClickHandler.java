package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Handles click hit-testing for detached-channel warning indicators in the tree renderer. */
public final class ServerTreeDetachedWarningClickHandler {

  private final JTree tree;
  private final Consumer<TargetRef> clearWarning;

  public ServerTreeDetachedWarningClickHandler(JTree tree, Consumer<TargetRef> clearWarning) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.clearWarning = Objects.requireNonNull(clearWarning, "clearWarning");
  }

  public boolean maybeHandleClick(MouseEvent event, int typingSlotWidth) {
    if (event == null || event.isConsumed()) return false;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;

    TreePath path = treePathForRowHit(event.getX(), event.getY());
    if (path == null) return false;

    Object comp = path.getLastPathComponent();
    if (!(comp instanceof DefaultMutableTreeNode node)) return false;
    Object uo = node.getUserObject();
    if (!(uo instanceof ServerTreeNodeData nd) || nd.ref == null || !nd.hasDetachedWarning()) {
      return false;
    }

    Rectangle warningBounds = disconnectedWarningIndicatorBounds(path, node, typingSlotWidth);
    if (warningBounds == null || !warningBounds.contains(event.getPoint())) return false;

    clearWarning.accept(nd.ref);
    event.consume();
    tree.repaint(warningBounds);
    return true;
  }

  public Rectangle disconnectedWarningIndicatorBounds(
      TreePath path, DefaultMutableTreeNode node, int typingSlotWidth) {
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
    if (!(rendered instanceof JComponent component)) return null;

    java.awt.Insets insets = component.getInsets();
    int leftInset = insets != null ? insets.left : 0;
    int slotLeft = rowBounds.x + Math.max(0, leftInset - typingSlotWidth - 1);
    return new Rectangle(slotLeft, rowBounds.y, typingSlotWidth, rowBounds.height);
  }

  private TreePath treePathForRowHit(int x, int y) {
    int row = tree.getClosestRowForLocation(x, y);
    if (row < 0) return null;

    Rectangle rowBounds = tree.getRowBounds(row);
    if (rowBounds == null) return null;
    if (y < rowBounds.y || y >= (rowBounds.y + rowBounds.height)) return null;

    return tree.getPathForRow(row);
  }
}
