package cafe.woden.ircclient.ui.servertree.model;

import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Shared node classification helpers used by server-tree collaborators. */
public final class ServerTreeNodeClassifier {

  private final String privateMessagesLabel;
  private final String interceptorsGroupLabel;
  private final String monitorGroupLabel;
  private final String otherGroupLabel;
  private final Predicate<DefaultMutableTreeNode> isServerNode;

  public ServerTreeNodeClassifier(
      String privateMessagesLabel,
      String interceptorsGroupLabel,
      String monitorGroupLabel,
      String otherGroupLabel,
      Predicate<DefaultMutableTreeNode> isServerNode) {
    this.privateMessagesLabel = Objects.toString(privateMessagesLabel, "Private Messages").trim();
    this.interceptorsGroupLabel = Objects.toString(interceptorsGroupLabel, "Interceptors").trim();
    this.monitorGroupLabel = Objects.toString(monitorGroupLabel, "Monitor").trim();
    this.otherGroupLabel = Objects.toString(otherGroupLabel, "Other").trim();
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
  }

  public String owningServerIdForNode(DefaultMutableTreeNode node) {
    DefaultMutableTreeNode current = node;
    while (current != null) {
      if (isServerNode.test(current)) {
        Object userObject = current.getUserObject();
        if (userObject instanceof String serverId) {
          return serverId.trim();
        }
      }
      javax.swing.tree.TreeNode parent = current.getParent();
      current = (parent instanceof DefaultMutableTreeNode parentNode) ? parentNode : null;
    }
    return "";
  }

  public boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {
    String label = nodeLabel(node);
    if (label.isEmpty()) return false;
    return privateMessagesLabel.equalsIgnoreCase(label)
        || "Private messages".equalsIgnoreCase(label)
        || "Private Messages".equalsIgnoreCase(label);
  }

  public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
    return groupNodeMatchesLabel(node, interceptorsGroupLabel);
  }

  public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
    return groupNodeMatchesLabel(node, monitorGroupLabel);
  }

  public boolean isOtherGroupNode(DefaultMutableTreeNode node) {
    return groupNodeMatchesLabel(node, otherGroupLabel);
  }

  private static String nodeLabel(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object userObject = node.getUserObject();
    if (userObject instanceof String label) return label.trim();
    if (userObject instanceof ServerTreeNodeData nodeData) {
      if (nodeData.ref != null) return "";
      return Objects.toString(nodeData.label, "").trim();
    }
    return "";
  }

  private static boolean groupNodeMatchesLabel(DefaultMutableTreeNode node, String expectedLabel) {
    if (node == null) return false;
    String label = nodeLabel(node);
    return !label.isEmpty() && expectedLabel.equalsIgnoreCase(label);
  }
}
