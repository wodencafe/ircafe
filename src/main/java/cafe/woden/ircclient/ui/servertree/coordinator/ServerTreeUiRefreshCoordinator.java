package cafe.woden.ircclient.ui.servertree.coordinator;

import java.awt.Font;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Refreshes tree renderer/layout state after UI defaults or look-and-feel changes. */
public final class ServerTreeUiRefreshCoordinator {

  private final JTree tree;
  private final DefaultTreeModel model;
  private final DefaultMutableTreeNode root;
  private final DefaultTreeCellRenderer treeCellRenderer;
  private final Supplier<Set<TreePath>> snapshotExpandedTreePaths;
  private final Consumer<Set<TreePath>> restoreExpandedTreePaths;

  public ServerTreeUiRefreshCoordinator(
      JTree tree,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      DefaultTreeCellRenderer treeCellRenderer,
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.model = Objects.requireNonNull(model, "model");
    this.root = Objects.requireNonNull(root, "root");
    this.treeCellRenderer = Objects.requireNonNull(treeCellRenderer, "treeCellRenderer");
    this.snapshotExpandedTreePaths =
        Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    this.restoreExpandedTreePaths =
        Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
  }

  public void refreshTreeLayoutAfterUiChange() {
    try {
      applyTreeFontFromUiDefaults(tree);
      Set<TreePath> expanded = snapshotExpandedTreePaths.get();
      tree.setRowHeight(0);
      try {
        treeCellRenderer.updateUI();
        treeCellRenderer.setOpenIcon(UIManager.getIcon("Tree.openIcon"));
        treeCellRenderer.setClosedIcon(UIManager.getIcon("Tree.closedIcon"));
        treeCellRenderer.setLeafIcon(UIManager.getIcon("Tree.leafIcon"));
      } catch (Exception ignored) {
      }
      tree.setCellRenderer(treeCellRenderer);
      ToolTipManager.sharedInstance().registerComponent(tree);
      model.reload(root);
      restoreExpandedTreePaths.accept(expanded);

      tree.revalidate();
      tree.repaint();
    } catch (Exception ignored) {
    }
  }

  public static void applyTreeFontFromUiDefaults(JTree tree) {
    if (tree == null) return;
    Font next = UIManager.getFont("Tree.font");
    if (next == null) next = UIManager.getFont("defaultFont");
    if (next == null) return;
    Font cur = tree.getFont();
    if (!next.equals(cur)) {
      tree.setFont(next);
    }
  }
}
