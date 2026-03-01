package cafe.woden.ircclient.ui.servertree.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.view.ServerTreeDetachedWarningClickHandler;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeRowInteractionHandlerTest {

  @Test
  void serverIdAtUsesDirectHitPath() {
    StubTree tree = new StubTree();
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("libera");
    tree.pathForLocation = new TreePath(serverNode.getPath());

    ServerTreeRowInteractionHandler handler = newHandler(tree);

    assertEquals("libera", handler.serverIdAt(4, 8));
  }

  @Test
  void serverIdAtFallsBackToClosestRowBounds() {
    StubTree tree = new StubTree();
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("oftc");
    tree.closestPath = new TreePath(serverNode.getPath());
    tree.closestPathBounds = new Rectangle(0, 10, 120, 20);

    ServerTreeRowInteractionHandler handler = newHandler(tree);

    assertEquals("oftc", handler.serverIdAt(9, 22));
    assertEquals("", handler.serverIdAt(9, 35));
  }

  @Test
  void maybeSelectRowFromLeftClickSelectsHitPath() {
    StubTree tree = new StubTree();
    DefaultMutableTreeNode rowNode = new DefaultMutableTreeNode("#ircafe");
    tree.pathForRow = new TreePath(rowNode.getPath());
    tree.closestRow = 2;
    tree.rowBounds = new Rectangle(0, 10, 160, 22);

    ServerTreeRowInteractionHandler handler = newHandler(tree);
    MouseEvent leftClick =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 12, 18, 1, false, MouseEvent.BUTTON1);

    assertTrue(handler.maybeSelectRowFromLeftClick(leftClick));
    assertEquals(tree.pathForRow, tree.getSelectionPath());
  }

  @Test
  void maybeSelectRowFromLeftClickRejectsNonLeftAndPopup() {
    StubTree tree = new StubTree();
    DefaultMutableTreeNode rowNode = new DefaultMutableTreeNode("#ircafe");
    tree.pathForRow = new TreePath(rowNode.getPath());
    tree.closestRow = 1;
    tree.rowBounds = new Rectangle(0, 0, 160, 20);

    ServerTreeRowInteractionHandler handler = newHandler(tree);
    MouseEvent rightClick =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 10, 1, false, MouseEvent.BUTTON3);
    MouseEvent popupLeftClick =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 10, 1, true, MouseEvent.BUTTON1);

    assertFalse(handler.maybeSelectRowFromLeftClick(rightClick));
    assertFalse(handler.maybeSelectRowFromLeftClick(popupLeftClick));
  }

  private static ServerTreeRowInteractionHandler newHandler(StubTree tree) {
    ServerTreeDetachedWarningClickHandler warningClickHandler =
        new ServerTreeDetachedWarningClickHandler(tree, __ -> {});
    return new ServerTreeRowInteractionHandler(
        tree,
        warningClickHandler,
        node -> node != null && node.getUserObject() instanceof String,
        () -> 12);
  }

  private static final class StubTree extends JTree {
    private TreePath pathForLocation;
    private TreePath closestPath;
    private Rectangle closestPathBounds;
    private int closestRow = -1;
    private Rectangle rowBounds;
    private TreePath pathForRow;

    @Override
    public TreePath getPathForLocation(int x, int y) {
      return pathForLocation;
    }

    @Override
    public TreePath getClosestPathForLocation(int x, int y) {
      return closestPath;
    }

    @Override
    public Rectangle getPathBounds(TreePath path) {
      if (Objects.equals(path, closestPath)) {
        return closestPathBounds;
      }
      if (Objects.equals(path, pathForRow)) {
        return rowBounds;
      }
      return null;
    }

    @Override
    public int getClosestRowForLocation(int x, int y) {
      return closestRow;
    }

    @Override
    public Rectangle getRowBounds(int row) {
      return row == closestRow ? rowBounds : null;
    }

    @Override
    public TreePath getPathForRow(int row) {
      return row == closestRow ? pathForRow : null;
    }
  }
}
