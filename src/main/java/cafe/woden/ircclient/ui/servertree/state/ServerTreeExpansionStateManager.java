package cafe.woden.ircclient.ui.servertree.state;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Tracks expanded-tree paths and provides default selection targets after structure changes. */
public final class ServerTreeExpansionStateManager {

  private final JTree tree;
  private final DefaultMutableTreeNode root;
  private final DefaultMutableTreeNode ircRoot;
  private final DefaultMutableTreeNode applicationRoot;

  public ServerTreeExpansionStateManager(
      JTree tree,
      DefaultMutableTreeNode root,
      DefaultMutableTreeNode ircRoot,
      DefaultMutableTreeNode applicationRoot) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.root = Objects.requireNonNull(root, "root");
    this.ircRoot = Objects.requireNonNull(ircRoot, "ircRoot");
    this.applicationRoot = Objects.requireNonNull(applicationRoot, "applicationRoot");
  }

  public Set<TreePath> snapshotExpandedTreePaths() {
    TreePath rootPath = new TreePath(root.getPath());
    Set<TreePath> expanded = new HashSet<>();
    Enumeration<TreePath> en = tree.getExpandedDescendants(rootPath);
    if (en != null) {
      while (en.hasMoreElements()) {
        expanded.add(en.nextElement());
      }
    }
    return expanded;
  }

  public void restoreExpandedTreePaths(Set<TreePath> expanded) {
    if (expanded == null || expanded.isEmpty()) return;
    for (TreePath path : expanded) {
      if (isPathInCurrentTreeModel(path)) {
        tree.expandPath(path);
      }
    }
  }

  public boolean isPathInCurrentTreeModel(TreePath path) {
    if (path == null) return false;
    Object[] nodes = path.getPath();
    if (nodes.length == 0 || nodes[0] != root) return false;

    DefaultMutableTreeNode cursor = root;
    for (int i = 1; i < nodes.length; i++) {
      Object next = nodes[i];
      DefaultMutableTreeNode matched = null;
      for (int childIndex = 0; childIndex < cursor.getChildCount(); childIndex++) {
        Object child = cursor.getChildAt(childIndex);
        if (child == next && child instanceof DefaultMutableTreeNode mutableTreeNode) {
          matched = mutableTreeNode;
          break;
        }
      }
      if (matched == null) return false;
      cursor = matched;
    }
    return true;
  }

  public TreePath defaultSelectionPath() {
    if (ircRoot.getParent() == root) {
      return new TreePath(ircRoot.getPath());
    }
    if (applicationRoot.getParent() == root) {
      return new TreePath(applicationRoot.getPath());
    }
    return new TreePath(root.getPath());
  }
}
