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
import org.springframework.stereotype.Component;

/** Refreshes tree renderer/layout state after UI defaults or look-and-feel changes. */
@Component
public final class ServerTreeUiRefreshCoordinator {

  public interface Context {
    JTree tree();

    DefaultTreeModel model();

    DefaultMutableTreeNode root();

    DefaultTreeCellRenderer treeCellRenderer();

    Set<TreePath> snapshotExpandedTreePaths();

    void restoreExpandedTreePaths(Set<TreePath> expanded);
  }

  public static Context context(
      JTree tree,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      DefaultTreeCellRenderer treeCellRenderer,
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(treeCellRenderer, "treeCellRenderer");
    Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
    return new Context() {
      @Override
      public JTree tree() {
        return tree;
      }

      @Override
      public DefaultTreeModel model() {
        return model;
      }

      @Override
      public DefaultMutableTreeNode root() {
        return root;
      }

      @Override
      public DefaultTreeCellRenderer treeCellRenderer() {
        return treeCellRenderer;
      }

      @Override
      public Set<TreePath> snapshotExpandedTreePaths() {
        return snapshotExpandedTreePaths.get();
      }

      @Override
      public void restoreExpandedTreePaths(Set<TreePath> expanded) {
        restoreExpandedTreePaths.accept(expanded);
      }
    };
  }

  public void refreshTreeLayoutAfterUiChange(Context context) {
    Context in = Objects.requireNonNull(context, "context");
    JTree tree = in.tree();
    DefaultTreeModel model = in.model();
    DefaultMutableTreeNode root = in.root();
    DefaultTreeCellRenderer treeCellRenderer = in.treeCellRenderer();
    Set<TreePath> expanded = in.snapshotExpandedTreePaths();

    try {
      applyTreeFontFromUiDefaults(tree);
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
      in.restoreExpandedTreePaths(expanded);

      tree.revalidate();
      tree.repaint();
    } catch (Exception ignored) {
    }
  }

  public ServerTreeUiRefreshCoordinator() {}

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
