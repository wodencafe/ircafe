package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.ui.servertree.view.ServerTreeDetachedWarningClickHandler;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Handles row hit-testing and click behavior used by server-tree interaction wiring. */
public final class ServerTreeRowInteractionHandler {

  private final JTree tree;
  private final ServerTreeDetachedWarningClickHandler detachedWarningClickHandler;
  private final Predicate<DefaultMutableTreeNode> isServerNode;
  private final IntSupplier typingSlotWidth;

  public ServerTreeRowInteractionHandler(
      JTree tree,
      ServerTreeDetachedWarningClickHandler detachedWarningClickHandler,
      Predicate<DefaultMutableTreeNode> isServerNode,
      IntSupplier typingSlotWidth) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.detachedWarningClickHandler =
        Objects.requireNonNull(detachedWarningClickHandler, "detachedWarningClickHandler");
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
    this.typingSlotWidth = Objects.requireNonNull(typingSlotWidth, "typingSlotWidth");
  }

  public String serverIdAt(int x, int y) {
    TreePath path = pathForLocationWithRowFallback(x, y);
    if (path == null) return "";

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node) || !isServerNode.test(node)) {
      return "";
    }
    return Objects.toString(node.getUserObject(), "").trim();
  }

  public boolean maybeHandleDisconnectedWarningClick(MouseEvent event) {
    return detachedWarningClickHandler.maybeHandleClick(event, typingSlotWidth.getAsInt());
  }

  public boolean maybeSelectRowFromLeftClick(MouseEvent event) {
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

  public TreePath treePathForRowHit(int x, int y) {
    int row = tree.getClosestRowForLocation(x, y);
    if (row < 0) return null;

    Rectangle rowBounds = tree.getRowBounds(row);
    if (rowBounds == null) return null;
    if (y < rowBounds.y || y >= (rowBounds.y + rowBounds.height)) return null;

    return tree.getPathForRow(row);
  }

  private TreePath pathForLocationWithRowFallback(int x, int y) {
    TreePath path = tree.getPathForLocation(x, y);
    if (path != null) return path;

    TreePath closest = tree.getClosestPathForLocation(x, y);
    if (closest == null) return null;

    Rectangle row = tree.getPathBounds(closest);
    if (row != null && y >= row.y && y < (row.y + row.height)) {
      return closest;
    }
    return null;
  }
}
