package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeNetworkGroupManagerTest {

  @Test
  void getOrCreateNetworksGroupNodeInsertsBeforePrivateMessagesAndReusesGroup() {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("server");
    DefaultMutableTreeNode statusNode = new DefaultMutableTreeNode("status");
    DefaultMutableTreeNode privateMessagesNode = new DefaultMutableTreeNode("privateMessages");
    serverNode.add(statusNode);
    serverNode.add(privateMessagesNode);

    Map<String, Map<String, DefaultMutableTreeNode>> groupsByOriginByBackendId = new HashMap<>();
    ServerTreeNetworkGroupManager.Context context =
        ServerTreeNetworkGroupManager.context(
            Map.of(ServerTreeBouncerBackends.SOJU, "Soju Networks"),
            groupsByOriginByBackendId,
            serverId -> "origin".equals(serverId) ? serverNode : null,
            serverId -> "origin".equals(serverId) ? privateMessagesNode : null);

    ServerTreeNetworkGroupManager manager = new ServerTreeNetworkGroupManager();

    DefaultMutableTreeNode group =
        manager.getOrCreateNetworksGroupNode(context, ServerTreeBouncerBackends.SOJU, "origin");

    assertSame(
        group,
        manager.getOrCreateNetworksGroupNode(context, ServerTreeBouncerBackends.SOJU, "origin"));
    assertEquals(1, serverNode.getIndex(group));
    assertEquals(2, serverNode.getIndex(privateMessagesNode));
    assertSame(group, groupsByOriginByBackendId.get(ServerTreeBouncerBackends.SOJU).get("origin"));
    assertEquals(
        ServerTreeBouncerBackends.SOJU, manager.backendIdForNetworksGroupNode(context, group));
    assertTrue(manager.isSojuNetworksGroupNode(context, group));
    assertFalse(manager.isZncNetworksGroupNode(context, group));
  }

  @Test
  void removeEmptyGroupIfNeededRemovesGroupNodeAndRegistryEntry() {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("server");
    DefaultMutableTreeNode privateMessagesNode = new DefaultMutableTreeNode("privateMessages");
    serverNode.add(privateMessagesNode);

    Map<String, Map<String, DefaultMutableTreeNode>> groupsByOriginByBackendId = new HashMap<>();
    ServerTreeNetworkGroupManager.Context context =
        ServerTreeNetworkGroupManager.context(
            Map.of(ServerTreeBouncerBackends.GENERIC, "Bouncer Networks"),
            groupsByOriginByBackendId,
            serverId -> "origin".equals(serverId) ? serverNode : null,
            serverId -> "origin".equals(serverId) ? privateMessagesNode : null);

    ServerTreeNetworkGroupManager manager = new ServerTreeNetworkGroupManager();
    DefaultMutableTreeNode group = manager.getOrCreateGenericNetworksGroupNode(context, "origin");

    manager.removeEmptyGroupIfNeeded(context, group);

    assertEquals(-1, serverNode.getIndex(group));
    assertTrue(
        groupsByOriginByBackendId
            .getOrDefault(ServerTreeBouncerBackends.GENERIC, Map.of())
            .isEmpty());
  }

  @Test
  void parseOriginFromCompoundServerIdReturnsNullForInvalidInputs() {
    assertEquals(
        "origin",
        ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId(
            "soju:origin:network", "soju:"));
    assertNull(ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId("", "soju:"));
    assertNull(
        ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId("soju::network", "soju:"));
  }
}
