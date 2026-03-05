package cafe.woden.ircclient.ui.servertree.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeServerNodeResolverTest {

  @Test
  void resolvesServerScopedNodes() {
    Map<String, ServerNodes> servers = new LinkedHashMap<>();
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    ServerNodes nodes = serverNodes("libera");
    servers.put("libera", nodes);

    DefaultMutableTreeNode channelListLeaf = new DefaultMutableTreeNode("Channel List");
    leaves.put(nodes.channelListRef, channelListLeaf);

    ServerTreeServerNodeResolver resolver =
        new ServerTreeServerNodeResolver(
            servers, leaves, serverId -> Objects.toString(serverId, "").trim());

    assertSame(nodes, resolver.serverNodesForServer(" libera "));
    assertTrue(resolver.hasServer("libera"));
    assertFalse(resolver.hasServer(" "));
    assertSame(nodes.serverNode, resolver.serverNodeForServer("libera"));
    assertSame(nodes.pmNode, resolver.privateMessagesNodeForServer("libera"));
    assertSame(nodes.monitorNode, resolver.monitorNodeForServer("libera"));
    assertSame(nodes.interceptorsNode, resolver.interceptorsNodeForServer("libera"));
    assertSame(channelListLeaf, resolver.channelListNodeForServer("libera"));
    assertNull(resolver.channelListNodeForServer("missing"));
  }

  @Test
  void firstServerIdPrefersRememberedSelectionWhenPresent() {
    Map<String, ServerNodes> servers = new LinkedHashMap<>();
    servers.put("oftc", serverNodes("oftc"));
    servers.put("libera", serverNodes("libera"));

    ServerTreeServerNodeResolver resolver =
        new ServerTreeServerNodeResolver(
            servers, new HashMap<>(), serverId -> Objects.toString(serverId, "").trim());

    assertEquals("libera", resolver.firstServerIdOrEmpty(() -> new TargetRef("libera", "status")));
    assertEquals("oftc", resolver.firstServerIdOrEmpty(() -> new TargetRef("missing", "status")));
    assertEquals("oftc", resolver.firstServerIdOrEmpty(null));
  }

  @Test
  void firstServerStatusRefOrNullReflectsCurrentServers() {
    Map<String, ServerNodes> servers = new LinkedHashMap<>();
    ServerTreeServerNodeResolver resolver =
        new ServerTreeServerNodeResolver(
            servers, new HashMap<>(), serverId -> Objects.toString(serverId, "").trim());

    assertNull(resolver.firstServerStatusRefOrNull());

    ServerNodes libera = serverNodes("libera");
    servers.put("libera", libera);
    assertSame(libera.statusRef, resolver.firstServerStatusRefOrNull());
  }

  private static ServerNodes serverNodes(String serverId) {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(serverId);
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private Messages");
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");
    DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("Monitor");
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("Interceptors");
    serverNode.add(pmNode);
    serverNode.add(otherNode);
    return new ServerNodes(
        serverNode,
        pmNode,
        otherNode,
        monitorNode,
        interceptorsNode,
        new TargetRef(serverId, "status"),
        TargetRef.notifications(serverId),
        TargetRef.logViewer(serverId),
        TargetRef.channelList(serverId),
        TargetRef.weechatFilters(serverId),
        TargetRef.ignores(serverId),
        TargetRef.dccTransfers(serverId));
  }
}
