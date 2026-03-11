package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
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
            value -> value == node ? ServerTreeBouncerBackends.ZNC : null,
            value -> false,
            value -> true,
            value -> false,
            value -> true,
            value -> value == node,
            value -> false,
            serverId -> ConnectionState.CONNECTED,
            serverId -> true,
            serverId -> " diagnostic",
            serverId ->
                "soju".equals(serverId)
                    ? ServerTreeBouncerBackends.SOJU
                    : "znc".equals(serverId)
                        ? ServerTreeBouncerBackends.ZNC
                        : "bouncer".equals(serverId) ? ServerTreeBouncerBackends.GENERIC : null,
            (backendId, serverId) ->
                ServerTreeBouncerBackends.SOJU.equals(backendId)
                    ? "origin-a"
                    : ServerTreeBouncerBackends.ZNC.equals(backendId) ? "origin-b" : "origin-c",
            serverId -> "Libera",
            (backendId, originId, networkKey) -> ServerTreeBouncerBackends.SOJU.equals(backendId),
            () -> true,
            value -> value == nodeData,
            (serverId, networkToken) -> "tip " + serverId + "/" + networkToken);

    assertEquals("libera", context.serverIdAt(1, 2));
    assertSame(path, context.serverPathForId("libera"));
    assertTrue(context.isIrcRootNode(node));
    assertFalse(context.isApplicationRootNode(node));
    assertEquals(ServerTreeBouncerBackends.ZNC, context.backendIdForNetworksGroupNode(node));
    assertFalse(context.isInterceptorsGroupNode(node));
    assertTrue(context.isMonitorGroupNode(node));
    assertFalse(context.isOtherGroupNode(node));
    assertTrue(context.isServerNode(node));
    assertTrue(context.isQuasselNetworkNode(node));
    assertFalse(context.isQuasselEmptyStateNode(node));
    assertEquals(ConnectionState.CONNECTED, context.connectionStateForServer("libera"));
    assertTrue(context.desiredOnlineForServer("libera"));
    assertEquals(" diagnostic", context.connectionDiagnosticsTipForServer("libera"));
    assertEquals(ServerTreeBouncerBackends.SOJU, context.backendIdForEphemeralServer("soju"));
    assertEquals(ServerTreeBouncerBackends.ZNC, context.backendIdForEphemeralServer("znc"));
    assertEquals(ServerTreeBouncerBackends.GENERIC, context.backendIdForEphemeralServer("bouncer"));
    assertEquals("origin-a", context.originByServerId(ServerTreeBouncerBackends.SOJU, "libera"));
    assertEquals("origin-b", context.originByServerId(ServerTreeBouncerBackends.ZNC, "libera"));
    assertEquals("origin-c", context.originByServerId(ServerTreeBouncerBackends.GENERIC, "libera"));
    assertEquals("Libera", context.serverDisplayName("libera"));
    assertTrue(context.isAutoConnectEnabled(ServerTreeBouncerBackends.SOJU, "origin-a", "Libera"));
    assertFalse(context.isAutoConnectEnabled(ServerTreeBouncerBackends.ZNC, "origin-b", "Libera"));
    assertFalse(
        context.isAutoConnectEnabled(ServerTreeBouncerBackends.GENERIC, "origin-c", "Libera"));
    assertTrue(context.isApplicationJfrActive());
    assertTrue(context.isBouncerControlStatusNode(nodeData));
    assertEquals("tip libera/libera", context.quasselNetworkTooltip("libera", "libera"));
  }
}
