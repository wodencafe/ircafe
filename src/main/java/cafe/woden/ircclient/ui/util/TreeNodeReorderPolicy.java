package cafe.woden.ircclient.ui.util;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Policy interface for determining whether a tree node may be moved or closed,
 * and if moved, where it should be re-inserted.
 */
public interface TreeNodeReorderPolicy {

  /** A planned move for a node within its current parent. */
  record MovePlan(DefaultMutableTreeNode parent, int fromIndex, int toIndex) {}

  /**
   * Return a {@link MovePlan} describing the intended move, or {@code null} if the node
   * is not movable in the requested direction.
   *
   * @param node the selected node
   * @param dir  negative to move up, positive to move down
   */
  MovePlan planMove(DefaultMutableTreeNode node, int dir);

  /** Whether the given node may be closed (removed / closed in UI). */
  boolean canClose(DefaultMutableTreeNode node);
}
