package cafe.woden.ircclient.ui.servertree.query;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Provides typed and root-aware access helpers for server-tree nodes. */
public final class ServerTreeNodeAccess {

  private final JTree tree;
  private final DefaultMutableTreeNode root;
  private final DefaultMutableTreeNode ircRoot;
  private final DefaultMutableTreeNode applicationRoot;
  private final Predicate<DefaultMutableTreeNode> isServerNode;

  public ServerTreeNodeAccess(
      JTree tree,
      DefaultMutableTreeNode root,
      DefaultMutableTreeNode ircRoot,
      DefaultMutableTreeNode applicationRoot,
      Predicate<DefaultMutableTreeNode> isServerNode) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.root = Objects.requireNonNull(root, "root");
    this.ircRoot = Objects.requireNonNull(ircRoot, "ircRoot");
    this.applicationRoot = Objects.requireNonNull(applicationRoot, "applicationRoot");
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
  }

  public boolean isRootServerNode(DefaultMutableTreeNode node) {
    return node != null && node.getParent() == ircRoot && isServerNode.test(node);
  }

  public boolean isIrcRootNode(DefaultMutableTreeNode node) {
    return node != null && node == ircRoot;
  }

  public boolean isApplicationRootNode(DefaultMutableTreeNode node) {
    return node != null && node == applicationRoot;
  }

  public boolean isChannelListLeafNode(DefaultMutableTreeNode node) {
    TargetRef ref = targetRefForNode(node);
    return ref != null && ref.isChannelList();
  }

  public TargetRef targetRefForNode(DefaultMutableTreeNode node) {
    if (node == null) return null;
    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeNodeData data) return data.ref;
    return null;
  }

  public String nodeLabelForNode(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeNodeData data) return Objects.toString(data.label, "");
    if (uo instanceof String s) return s;
    return "";
  }

  public TargetRef selectedTargetRef() {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
    return targetRefForNode(node);
  }

  public boolean hasValidTreeSelection() {
    TreePath selection = tree.getSelectionPath();
    if (selection == null) return false;
    Object last = selection.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return false;
    return node.getPath() != null && node.getRoot() == root;
  }
}
