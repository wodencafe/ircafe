package cafe.woden.ircclient.ui.servertree.model;

import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Shared node classification helpers used by server-tree collaborators. */
@Component
public final class ServerTreeNodeClassifier {

  public interface Context {
    String privateMessagesLabel();

    String interceptorsGroupLabel();

    String monitorGroupLabel();

    String otherGroupLabel();

    Predicate<DefaultMutableTreeNode> isServerNode();
  }

  private record DefaultContext(
      String privateMessagesLabel,
      String interceptorsGroupLabel,
      String monitorGroupLabel,
      String otherGroupLabel,
      Predicate<DefaultMutableTreeNode> isServerNode)
      implements Context {}

  public static Context context(
      String privateMessagesLabel,
      String interceptorsGroupLabel,
      String monitorGroupLabel,
      String otherGroupLabel,
      Predicate<DefaultMutableTreeNode> isServerNode) {
    return new DefaultContext(
        privateMessagesLabel,
        interceptorsGroupLabel,
        monitorGroupLabel,
        otherGroupLabel,
        Objects.requireNonNull(isServerNode, "isServerNode"));
  }

  public String owningServerIdForNode(Context context, DefaultMutableTreeNode node) {
    Context in = Objects.requireNonNull(context, "context");
    DefaultMutableTreeNode current = node;
    while (current != null) {
      if (in.isServerNode().test(current)) {
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

  public boolean isPrivateMessagesGroupNode(Context context, DefaultMutableTreeNode node) {
    String label = nodeLabel(node);
    if (label.isEmpty()) return false;
    return privateMessagesLabel(context).equalsIgnoreCase(label)
        || "Private messages".equalsIgnoreCase(label)
        || "Private Messages".equalsIgnoreCase(label);
  }

  public boolean isInterceptorsGroupNode(Context context, DefaultMutableTreeNode node) {
    return groupNodeMatchesLabel(node, interceptorsGroupLabel(context));
  }

  public boolean isMonitorGroupNode(Context context, DefaultMutableTreeNode node) {
    return groupNodeMatchesLabel(node, monitorGroupLabel(context));
  }

  public boolean isOtherGroupNode(Context context, DefaultMutableTreeNode node) {
    return groupNodeMatchesLabel(node, otherGroupLabel(context));
  }

  private static String nodeLabel(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object userObject = node.getUserObject();
    if (userObject instanceof String label) return label.trim();
    if (userObject instanceof ServerTreeNodeData nodeData) {
      if (nodeData.ref != null
          && !nodeData.ref.isMonitorGroup()
          && !nodeData.ref.isInterceptorsGroup()) {
        return "";
      }
      return Objects.toString(nodeData.label, "").trim();
    }
    return "";
  }

  private static boolean groupNodeMatchesLabel(DefaultMutableTreeNode node, String expectedLabel) {
    if (node == null) return false;
    String label = nodeLabel(node);
    return !label.isEmpty() && expectedLabel.equalsIgnoreCase(label);
  }

  private static String privateMessagesLabel(Context context) {
    return Objects.toString(
            Objects.requireNonNull(context, "context").privateMessagesLabel(), "Private Messages")
        .trim();
  }

  private static String interceptorsGroupLabel(Context context) {
    return Objects.toString(
            Objects.requireNonNull(context, "context").interceptorsGroupLabel(), "Interceptors")
        .trim();
  }

  private static String monitorGroupLabel(Context context) {
    return Objects.toString(
            Objects.requireNonNull(context, "context").monitorGroupLabel(), "Monitor")
        .trim();
  }

  private static String otherGroupLabel(Context context) {
    return Objects.toString(Objects.requireNonNull(context, "context").otherGroupLabel(), "Other")
        .trim();
  }
}
