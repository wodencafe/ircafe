package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Font;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeUiRefreshCoordinatorTest {

  @Test
  void refreshReappliesRendererAndExpandedPaths() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode child = new DefaultMutableTreeNode("child");
    root.add(child);
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);
    tree.setRowHeight(21);

    DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
    AtomicInteger snapshotCount = new AtomicInteger();
    AtomicInteger restoreCount = new AtomicInteger();
    AtomicReference<Set<TreePath>> restored = new AtomicReference<>();
    Set<TreePath> expanded = Set.of(new TreePath(child.getPath()));
    ServerTreeUiRefreshCoordinator coordinator = new ServerTreeUiRefreshCoordinator();
    ServerTreeUiRefreshCoordinator.Context context =
        ServerTreeUiRefreshCoordinator.context(
            tree,
            model,
            root,
            renderer,
            () -> {
              snapshotCount.incrementAndGet();
              return expanded;
            },
            paths -> {
              restoreCount.incrementAndGet();
              restored.set(paths);
            });

    coordinator.refreshTreeLayoutAfterUiChange(context);

    assertEquals(1, snapshotCount.get());
    assertEquals(1, restoreCount.get());
    assertEquals(expanded, restored.get());
    assertEquals(0, tree.getRowHeight());
    assertSame(renderer, tree.getCellRenderer());
  }

  @Test
  void applyTreeFontFromUiDefaultsUsesTreeFontWhenConfigured() {
    JTree tree = new JTree();
    Font originalTreeFont = UIManager.getFont("Tree.font");
    Font originalDefaultFont = UIManager.getFont("defaultFont");
    Font expected = new Font("Dialog", Font.BOLD, 13);
    try {
      UIManager.put("Tree.font", expected);
      tree.setFont(new Font("Dialog", Font.PLAIN, 11));

      ServerTreeUiRefreshCoordinator.applyTreeFontFromUiDefaults(tree);

      assertEquals(expected, tree.getFont());
    } finally {
      UIManager.put("Tree.font", originalTreeFont);
      UIManager.put("defaultFont", originalDefaultFont);
    }
  }
}
