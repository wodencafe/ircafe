package cafe.woden.ircclient.ui.util;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Reusable "node move/close" controller for a {@link JTree}. */
public final class TreeNodeActions<T> implements AutoCloseable {

  private final JTree tree;
  private final DefaultTreeModel model;
  private final TreeNodeReorderPolicy policy;
  private final Function<DefaultMutableTreeNode, T> closePayloadExtractor;
  private final Consumer<T> closeHandler;

  private final TreeSelectionListener selectionListener;

  private final Action moveUpAction;
  private final Action moveDownAction;
  private final Action closeAction;

  public TreeNodeActions(
      JTree tree,
      DefaultTreeModel model,
      TreeNodeReorderPolicy policy,
      Function<DefaultMutableTreeNode, T> closePayloadExtractor,
      Consumer<T> closeHandler) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.model = Objects.requireNonNull(model, "model");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.closePayloadExtractor =
        Objects.requireNonNull(closePayloadExtractor, "closePayloadExtractor");
    this.closeHandler = Objects.requireNonNull(closeHandler, "closeHandler");

    this.moveUpAction =
        new AbstractAction("Move Node Up") {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {
            moveUp();
          }
        };

    this.moveDownAction =
        new AbstractAction("Move Node Down") {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {
            moveDown();
          }
        };

    this.closeAction =
        new AbstractAction("Close Node") {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {
            closeSelected();
          }
        };

    this.selectionListener = e -> refreshEnabledState();
    this.tree.addTreeSelectionListener(selectionListener);

    refreshEnabledState();
  }

  public Action moveUpAction() {
    return moveUpAction;
  }

  public Action moveDownAction() {
    return moveDownAction;
  }

  public Action closeAction() {
    return closeAction;
  }

  public boolean canMoveUp() {
    DefaultMutableTreeNode n = selectedNode();
    return n != null && policy.planMove(n, -1) != null;
  }

  public boolean canMoveDown() {
    DefaultMutableTreeNode n = selectedNode();
    return n != null && policy.planMove(n, +1) != null;
  }

  public boolean canClose() {
    DefaultMutableTreeNode n = selectedNode();
    return n != null && policy.canClose(n);
  }

  public void moveUp() {
    move(-1);
  }

  public void moveDown() {
    move(+1);
  }

  public void closeSelected() {
    closeImpl();
  }

  public void refreshEnabledState() {
    moveUpAction.setEnabled(canMoveUp());
    moveDownAction.setEnabled(canMoveDown());
    closeAction.setEnabled(canClose());
  }

  private DefaultMutableTreeNode selectedNode() {
    Object last = tree.getLastSelectedPathComponent();
    if (last instanceof DefaultMutableTreeNode n) return n;
    return null;
  }

  private void move(int dir) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> move(dir));
      return;
    }

    DefaultMutableTreeNode n = selectedNode();
    if (n == null) return;

    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(n, dir);
    if (plan == null) return;

    DefaultMutableTreeNode parent = plan.parent();

    // Preserve node identity so any external maps that store node instances remain valid.
    model.removeNodeFromParent(n);
    model.insertNodeInto(n, parent, plan.toIndex());

    TreePath path = new TreePath(n.getPath());
    tree.setSelectionPath(path);
    tree.scrollPathToVisible(path);

    refreshEnabledState();
  }

  private void closeImpl() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::closeImpl);
      return;
    }

    DefaultMutableTreeNode n = selectedNode();
    if (n == null) return;
    if (!policy.canClose(n)) return;

    T payload = closePayloadExtractor.apply(n);
    if (payload == null) return;

    closeHandler.accept(payload);

    refreshEnabledState();
  }

  @Override
  public void close() {
    tree.removeTreeSelectionListener(selectionListener);
  }
}
