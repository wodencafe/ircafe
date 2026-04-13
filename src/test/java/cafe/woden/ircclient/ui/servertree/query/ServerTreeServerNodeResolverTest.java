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

    ServerTreeServerNodeResolver resolver = new ServerTreeServerNodeResolver();
    ServerTreeServerNodeResolver.Context context =
        ServerTreeServerNodeResolver.context(
            servers, leaves, serverId -> Objects.toString(serverId, "").trim());

    assertSame(nodes, resolver.serverNodesForServer(context, " libera "));
    assertTrue(resolver.hasServer(context, "libera"));
    assertFalse(resolver.hasServer(context, " "));
    assertSame(nodes.serverNode, resolver.serverNodeForServer(context, "libera"));
    assertSame(nodes.pmNode, resolver.privateMessagesNodeForServer(context, "libera"));
    assertSame(nodes.monitorNode, resolver.monitorNodeForServer(context, "libera"));
    assertSame(nodes.interceptorsNode, resolver.interceptorsNodeForServer(context, "libera"));
    assertSame(channelListLeaf, resolver.channelListNodeForServer(context, "libera"));
    assertNull(resolver.channelListNodeForServer(context, "missing"));
  }

  @Test
  void firstServerIdPrefersRememberedSelectionWhenPresent() {
    Map<String, ServerNodes> servers = new LinkedHashMap<>();
    servers.put("oftc", serverNodes("oftc"));
    servers.put("libera", serverNodes("libera"));

    ServerTreeServerNodeResolver resolver = new ServerTreeServerNodeResolver();
    ServerTreeServerNodeResolver.Context context =
        ServerTreeServerNodeResolver.context(
            servers, new HashMap<>(), serverId -> Objects.toString(serverId, "").trim());

    assertEquals(
        "libera", resolver.firstServerIdOrEmpty(context, () -> new TargetRef("libera", "status")));
    assertEquals(
        "oftc", resolver.firstServerIdOrEmpty(context, () -> new TargetRef("missing", "status")));
    assertEquals("oftc", resolver.firstServerIdOrEmpty(context, null));
  }

  @Test
  void firstServerStatusRefOrNullReflectsCurrentServers() {
    Map<String, ServerNodes> servers = new LinkedHashMap<>();
    ServerTreeServerNodeResolver resolver = new ServerTreeServerNodeResolver();
    ServerTreeServerNodeResolver.Context context =
        ServerTreeServerNodeResolver.context(
            servers, new HashMap<>(), serverId -> Objects.toString(serverId, "").trim());

    assertNull(resolver.firstServerStatusRefOrNull(context));

    ServerNodes libera = serverNodes("libera");
    servers.put("libera", libera);
    assertSame(libera.statusRef, resolver.firstServerStatusRefOrNull(context));
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
