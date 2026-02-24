package cafe.woden.ircclient.ui.util;

import javax.swing.tree.DefaultMutableTreeNode;

public interface TreeNodeReorderPolicy {

  record MovePlan(DefaultMutableTreeNode parent, int fromIndex, int toIndex) {}

  /**
   * Return a {@link MovePlan} describing the intended move, or {@code null} if the node is not
   * movable in the requested direction.
   *
   * @param node the selected node
   * @param dir negative to move up, positive to move down
   */
  MovePlan planMove(DefaultMutableTreeNode node, int dir);

  boolean canClose(DefaultMutableTreeNode node);
}
