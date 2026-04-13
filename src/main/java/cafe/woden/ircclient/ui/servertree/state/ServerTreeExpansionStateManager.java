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
import org.springframework.stereotype.Component;

/** Tracks expanded-tree paths and provides default selection targets after structure changes. */
@Component
public final class ServerTreeExpansionStateManager {

  public interface Context {
    JTree tree();

    DefaultMutableTreeNode root();

    DefaultMutableTreeNode ircRoot();

    DefaultMutableTreeNode applicationRoot();
  }

  public static Context context(
      JTree tree,
      DefaultMutableTreeNode root,
      DefaultMutableTreeNode ircRoot,
      DefaultMutableTreeNode applicationRoot) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(ircRoot, "ircRoot");
    Objects.requireNonNull(applicationRoot, "applicationRoot");
    return new Context() {
      @Override
      public JTree tree() {
        return tree;
      }

      @Override
      public DefaultMutableTreeNode root() {
        return root;
      }

      @Override
      public DefaultMutableTreeNode ircRoot() {
        return ircRoot;
      }

      @Override
      public DefaultMutableTreeNode applicationRoot() {
        return applicationRoot;
      }
    };
  }

  public Set<TreePath> snapshotExpandedTreePaths(Context context) {
    Context in = Objects.requireNonNull(context, "context");
    TreePath rootPath = new TreePath(in.root().getPath());
    Set<TreePath> expanded = new HashSet<>();
    Enumeration<TreePath> en = in.tree().getExpandedDescendants(rootPath);
    if (en != null) {
      while (en.hasMoreElements()) {
        expanded.add(en.nextElement());
      }
    }
    return expanded;
  }

  public void restoreExpandedTreePaths(Context context, Set<TreePath> expanded) {
    Context in = Objects.requireNonNull(context, "context");
    if (expanded == null || expanded.isEmpty()) return;
    for (TreePath path : expanded) {
      TreePath resolved = resolvePathInCurrentTreeModel(in, path);
      if (resolved != null) {
        in.tree().expandPath(resolved);
      }
    }
  }

  public boolean isPathInCurrentTreeModel(Context context, TreePath path) {
    return resolvePathInCurrentTreeModel(Objects.requireNonNull(context, "context"), path) != null;
  }

  public TreePath defaultSelectionPath(Context context) {
    Context in = Objects.requireNonNull(context, "context");
    if (in.ircRoot().getParent() == in.root()) {
      return new TreePath(in.ircRoot().getPath());
    }
    if (in.applicationRoot().getParent() == in.root()) {
      return new TreePath(in.applicationRoot().getPath());
    }
    return new TreePath(in.root().getPath());
  }

  private TreePath resolvePathInCurrentTreeModel(Context context, TreePath path) {
    if (path == null) return null;
    Object[] nodes = path.getPath();
    if (nodes.length == 0 || nodes[0] != context.root()) return null;

    DefaultMutableTreeNode cursor = context.root();
    Object[] resolved = new Object[nodes.length];
    resolved[0] = context.root();
    for (int i = 1; i < nodes.length; i++) {
      if (!(nodes[i] instanceof DefaultMutableTreeNode expectedNode)) return null;
      DefaultMutableTreeNode matched = findMatchingChild(cursor, expectedNode);
      if (matched == null) return null;
      cursor = matched;
      resolved[i] = matched;
    }
    return new TreePath(resolved);
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
