package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeTooltipProviderContextTest {

  @Test
  void contextDelegatesTooltipLookups() {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("node");
    TreePath path = new TreePath(new Object[] {"root", "libera"});
    ServerTreeNodeData nodeData = new ServerTreeNodeData(null, "Bouncer Control");

    ServerTreeTooltipProvider.Context context =
        ServerTreeTooltipProvider.context(
            (x, y) -> "libera",
            serverId -> path,
            value -> value == node,
            value -> false,
            value -> false,
            value -> true,
            value -> false,
            value -> true,
            value -> false,
            value -> true,
            serverId -> ConnectionState.CONNECTED,
            serverId -> true,
            serverId -> " diagnostic",
            serverId -> "soju".equals(serverId),
            serverId -> "znc".equals(serverId),
            serverId -> "origin-a",
            serverId -> "origin-b",
            serverId -> "Libera",
            (originId, networkKey) -> true,
            (originId, networkKey) -> false,
            () -> true,
            value -> value == nodeData);

    assertEquals("libera", context.serverIdAt(1, 2));
    assertSame(path, context.serverPathForId("libera"));
    assertTrue(context.isIrcRootNode(node));
    assertFalse(context.isApplicationRootNode(node));
    assertFalse(context.isSojuNetworksGroupNode(node));
    assertTrue(context.isZncNetworksGroupNode(node));
    assertFalse(context.isInterceptorsGroupNode(node));
    assertTrue(context.isMonitorGroupNode(node));
    assertFalse(context.isOtherGroupNode(node));
    assertTrue(context.isServerNode(node));
    assertEquals(ConnectionState.CONNECTED, context.connectionStateForServer("libera"));
    assertTrue(context.desiredOnlineForServer("libera"));
    assertEquals(" diagnostic", context.connectionDiagnosticsTipForServer("libera"));
    assertTrue(context.isSojuEphemeralServer("soju"));
    assertTrue(context.isZncEphemeralServer("znc"));
    assertEquals("origin-a", context.sojuOriginByServerId("libera"));
    assertEquals("origin-b", context.zncOriginByServerId("libera"));
    assertEquals("Libera", context.serverDisplayName("libera"));
    assertTrue(context.isSojuAutoConnectEnabled("origin-a", "Libera"));
    assertFalse(context.isZncAutoConnectEnabled("origin-b", "Libera"));
    assertTrue(context.isApplicationJfrActive());
    assertTrue(context.isBouncerControlStatusNode(nodeData));
  }
}
