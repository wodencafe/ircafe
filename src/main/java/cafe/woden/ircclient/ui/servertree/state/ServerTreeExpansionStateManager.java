package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
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
      TreePath resolved = resolvePathInCurrentTreeModel(path);
      if (resolved != null) {
        tree.expandPath(resolved);
      }
    }
  }

  public boolean isPathInCurrentTreeModel(TreePath path) {
    return resolvePathInCurrentTreeModel(path) != null;
  }

  private TreePath resolvePathInCurrentTreeModel(TreePath path) {
    if (path == null) return null;
    Object[] nodes = path.getPath();
    if (nodes.length == 0 || nodes[0] != root) return null;

    DefaultMutableTreeNode cursor = root;
    Object[] resolved = new Object[nodes.length];
    resolved[0] = root;
    for (int i = 1; i < nodes.length; i++) {
      if (!(nodes[i] instanceof DefaultMutableTreeNode expectedNode)) return null;
      DefaultMutableTreeNode matched = findMatchingChild(cursor, expectedNode);
      if (matched == null) return null;
      cursor = matched;
      resolved[i] = matched;
    }
    return new TreePath(resolved);
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

  private static DefaultMutableTreeNode findMatchingChild(
      DefaultMutableTreeNode parent, DefaultMutableTreeNode expectedNode) {
    if (parent == null || expectedNode == null) return null;
    for (int childIndex = 0; childIndex < parent.getChildCount(); childIndex++) {
      Object child = parent.getChildAt(childIndex);
      if (child == expectedNode && child instanceof DefaultMutableTreeNode matched) {
        return matched;
      }
    }
    String expectedKey = nodeIdentityKey(expectedNode);
    if (expectedKey.isEmpty()) return null;
    for (int childIndex = 0; childIndex < parent.getChildCount(); childIndex++) {
      Object child = parent.getChildAt(childIndex);
      if (!(child instanceof DefaultMutableTreeNode candidate)) continue;
      if (expectedKey.equals(nodeIdentityKey(candidate))) {
        return candidate;
      }
    }
    return null;
  }

  private static String nodeIdentityKey(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object userObject = node.getUserObject();
    if (userObject instanceof String label) {
      return "str:" + normalize(label);
    }
    if (userObject instanceof ServerTreeQuasselNetworkNodeData quasselNodeData) {
      String server = normalize(quasselNodeData.serverId());
      if (quasselNodeData.emptyState()) {
        return "qempty:" + server;
      }
      return "qnet:" + server + "|" + normalize(quasselNodeData.networkToken());
    }
    if (userObject instanceof ServerTreeNodeData nodeData) {
      TargetRef ref = nodeData.ref;
      if (ref != null) {
        return "ref:" + normalize(ref.serverId()) + "|" + normalize(ref.key());
      }
      return "label:" + normalize(nodeData.label);
    }
    return "obj:" + normalize(Objects.toString(userObject, ""));
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
  }
}
