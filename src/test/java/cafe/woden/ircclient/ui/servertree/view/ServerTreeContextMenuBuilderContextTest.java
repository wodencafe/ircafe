package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeContextMenuBuilderContextTest {

  @Test
  void routingContextDelegatesPredicates() {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("server");
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("interceptors");
    DefaultMutableTreeNode quasselNetworkNode = new DefaultMutableTreeNode("quassel-network");
    DefaultMutableTreeNode quasselEmptyStateNode = new DefaultMutableTreeNode("quassel-empty");

    ServerTreeContextMenuBuilder.RoutingContext context =
        ServerTreeContextMenuBuilder.routingContext(
            node -> node == serverNode,
            node -> node == interceptorsNode,
            node -> node == quasselNetworkNode,
            node -> node == quasselEmptyStateNode);

    assertTrue(context.isServerNode(serverNode));
    assertFalse(context.isServerNode(interceptorsNode));
    assertTrue(context.isInterceptorsGroupNode(interceptorsNode));
    assertFalse(context.isInterceptorsGroupNode(serverNode));
    assertTrue(context.isQuasselNetworkNode(quasselNetworkNode));
    assertFalse(context.isQuasselNetworkNode(quasselEmptyStateNode));
    assertTrue(context.isQuasselEmptyStateNode(quasselEmptyStateNode));
    assertFalse(context.isQuasselEmptyStateNode(quasselNetworkNode));
  }
}
