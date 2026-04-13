package cafe.woden.ircclient.ui.servertree.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeNodeClassifierTest {

  private final ServerTreeNodeClassifier classifier = new ServerTreeNodeClassifier();

  @Test
  void classifiesBuiltInGroupNodesAndOwningServer() {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("libera");
    DefaultMutableTreeNode monitorNode =
        new DefaultMutableTreeNode(
            new ServerTreeNodeData(TargetRef.monitorGroup("libera"), "Monitor"));
    DefaultMutableTreeNode interceptorsNode =
        new DefaultMutableTreeNode(
            new ServerTreeNodeData(TargetRef.interceptorsGroup("libera"), "Interceptors"));
    DefaultMutableTreeNode privateMessagesNode = new DefaultMutableTreeNode("Private Messages");
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");

    serverNode.add(monitorNode);
    serverNode.add(interceptorsNode);
    serverNode.add(privateMessagesNode);
    serverNode.add(otherNode);

    ServerTreeNodeClassifier.Context context =
        ServerTreeNodeClassifier.context(
            "Private Messages", "Interceptors", "Monitor", "Other", serverNode::equals);

    assertTrue(classifier.isMonitorGroupNode(context, monitorNode));
    assertTrue(classifier.isInterceptorsGroupNode(context, interceptorsNode));
    assertTrue(classifier.isPrivateMessagesGroupNode(context, privateMessagesNode));
    assertTrue(classifier.isOtherGroupNode(context, otherNode));
    assertEquals("libera", classifier.owningServerIdForNode(context, interceptorsNode));
  }

  @Test
  void ignoresUnknownNodes() {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("Elsewhere");
    ServerTreeNodeClassifier.Context context =
        ServerTreeNodeClassifier.context(
            "Private Messages", "Interceptors", "Monitor", "Other", __ -> false);

    assertFalse(classifier.isMonitorGroupNode(context, node));
    assertFalse(classifier.isInterceptorsGroupNode(context, node));
    assertFalse(classifier.isPrivateMessagesGroupNode(context, node));
    assertFalse(classifier.isOtherGroupNode(context, node));
    assertEquals("", classifier.owningServerIdForNode(context, node));
  }
}
