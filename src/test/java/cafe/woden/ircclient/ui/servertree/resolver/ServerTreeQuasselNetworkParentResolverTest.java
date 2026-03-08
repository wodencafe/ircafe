package cafe.woden.ircclient.ui.servertree.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeQuasselNetworkParentResolverTest {

  @Test
  void routesQualifiedChannelToNetworkChannelListNode() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");

    ServerNodes serverNodes = serverNodes("quassel");
    TargetRef channel = new TargetRef("quassel", "#ircafe{net:libera}");

    DefaultMutableTreeNode parent = resolver.resolveParent(channel, serverNodes);
    assertNotNull(parent);
    assertNotNull(parent.getUserObject());
    ServerTreeNodeData channelListData = (ServerTreeNodeData) parent.getUserObject();
    assertEquals(TargetRef.channelList("quassel", "libera"), channelListData.ref);
    assertEquals("Channel List", channelListData.label);
    Object networkUserObject = ((DefaultMutableTreeNode) parent.getParent()).getUserObject();
    assertTrue(networkUserObject instanceof ServerTreeQuasselNetworkNodeData);
    ServerTreeQuasselNetworkNodeData networkNodeData =
        (ServerTreeQuasselNetworkNodeData) networkUserObject;
    assertEquals("quassel", networkNodeData.serverId());
    assertEquals("libera", networkNodeData.networkToken());
    assertEquals("Libera", networkNodeData.label());
  }

  @Test
  void routesQualifiedPrivateMessageToNetworkPrivateMessagesNode() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");

    ServerNodes serverNodes = serverNodes("quassel");
    DefaultMutableTreeNode firstParent =
        resolver.resolveParent(new TargetRef("quassel", "#one{net:libera}"), serverNodes);
    DefaultMutableTreeNode parent =
        resolver.resolveParent(new TargetRef("quassel", "alice{net:libera}"), serverNodes);

    assertNotNull(firstParent);
    assertNotNull(parent);
    assertEquals("Private Messages", parent.getUserObject());
    assertSame(firstParent.getParent(), parent.getParent());
  }

  @Test
  void ignoresUnqualifiedAndNonQuasselTargets() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");

    assertNull(resolver.resolveParent(new TargetRef("quassel", "#ircafe"), serverNodes("quassel")));
    assertNull(
        resolver.resolveParent(
            new TargetRef("libera", "#ircafe{net:libera}"), serverNodes("libera")));
  }

  @Test
  void aliasesLegacyServerChannelListToFirstNetworkChannelList() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");

    ServerNodes serverNodes = serverNodes("quassel");
    TargetRef legacyRef = TargetRef.channelList("quassel");
    DefaultMutableTreeNode legacyChannelListNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(legacyRef, "Channel List"));
    serverNodes.serverNode.insert(legacyChannelListNode, 0);
    leaves.put(legacyRef, legacyChannelListNode);

    resolver.resolveParent(new TargetRef("quassel", "#ircafe{net:libera}"), serverNodes);

    DefaultMutableTreeNode aliasedNode = leaves.get(legacyRef);
    assertNotNull(aliasedNode);
    ServerTreeNodeData aliasedData = (ServerTreeNodeData) aliasedNode.getUserObject();
    assertEquals(TargetRef.channelList("quassel", "libera"), aliasedData.ref);
    assertNull(legacyChannelListNode.getParent());
  }

  @Test
  void initializesEmptyStateNodeForQuasselServer() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");
    ServerNodes serverNodes = serverNodes("quassel");

    resolver.initializeServer("quassel", serverNodes);

    assertEquals(3, serverNodes.serverNode.getChildCount());
    boolean foundEmptyState = false;
    for (int i = 0; i < serverNodes.serverNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) serverNodes.serverNode.getChildAt(i);
      if (resolver.isQuasselEmptyStateNode(child)) {
        foundEmptyState = true;
        break;
      }
    }
    assertTrue(foundEmptyState);
  }

  @Test
  void syncServerNetworksUsesRealNamesAndConnectionState() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");
    ServerNodes serverNodes = serverNodes("quassel");
    resolver.initializeServer("quassel", serverNodes);

    resolver.syncServerNetworks(
        "quassel",
        serverNodes,
        List.of(
            new ServerTreeQuasselNetworkParentResolver.NetworkPresentation(
                "2", "Libera Chat", true, true),
            new ServerTreeQuasselNetworkParentResolver.NetworkPresentation(
                "oftc", "OFTC", false, true)));

    DefaultMutableTreeNode liberaNode = networkNodeByToken(serverNodes.serverNode, "2");
    assertNotNull(liberaNode);
    ServerTreeQuasselNetworkNodeData liberaData =
        (ServerTreeQuasselNetworkNodeData) liberaNode.getUserObject();
    assertEquals("Libera Chat", liberaData.label());
    assertEquals(Boolean.TRUE, liberaData.connected());
    assertEquals(Boolean.TRUE, liberaData.enabled());
    assertTrue(resolver.isQuasselNetworkNode(liberaNode));
  }

  @Test
  void syncServerNetworksRemovesStaleNodesAndRestoresEmptyState() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");
    ServerNodes serverNodes = serverNodes("quassel");
    resolver.initializeServer("quassel", serverNodes);

    resolver.syncServerNetworks(
        "quassel",
        serverNodes,
        List.of(
            new ServerTreeQuasselNetworkParentResolver.NetworkPresentation(
                "2", "Libera", true, true)));
    assertNotNull(networkNodeByToken(serverNodes.serverNode, "2"));
    assertNotNull(leaves.get(TargetRef.channelList("quassel", "2")));

    resolver.syncServerNetworks("quassel", serverNodes, List.of());

    assertNull(networkNodeByToken(serverNodes.serverNode, "2"));
    assertNull(leaves.get(TargetRef.channelList("quassel", "2")));
    boolean foundEmptyState = false;
    for (int i = 0; i < serverNodes.serverNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) serverNodes.serverNode.getChildAt(i);
      if (resolver.isQuasselEmptyStateNode(child)) {
        foundEmptyState = true;
        break;
      }
    }
    assertTrue(foundEmptyState);
  }

  @Test
  void syncServerNetworksUpdatesExistingNodeState() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    ServerTreeQuasselNetworkParentResolver resolver =
        new ServerTreeQuasselNetworkParentResolver(
            leaves,
            model,
            sid -> "quassel".equalsIgnoreCase(sid),
            "Channel List",
            "Private Messages");
    ServerNodes serverNodes = serverNodes("quassel");
    resolver.initializeServer("quassel", serverNodes);

    resolver.syncServerNetworks(
        "quassel",
        serverNodes,
        List.of(
            new ServerTreeQuasselNetworkParentResolver.NetworkPresentation(
                "2", "Libera", false, true)));
    resolver.syncServerNetworks(
        "quassel",
        serverNodes,
        List.of(
            new ServerTreeQuasselNetworkParentResolver.NetworkPresentation(
                "2", "Libera Updated", true, false)));

    DefaultMutableTreeNode node = networkNodeByToken(serverNodes.serverNode, "2");
    assertNotNull(node);
    ServerTreeQuasselNetworkNodeData data = (ServerTreeQuasselNetworkNodeData) node.getUserObject();
    assertEquals("Libera Updated", data.label());
    assertEquals(Boolean.TRUE, data.connected());
    assertEquals(Boolean.FALSE, data.enabled());
  }

  private static DefaultMutableTreeNode networkNodeByToken(
      DefaultMutableTreeNode serverNode, String token) {
    if (serverNode == null) return null;
    String want = token == null ? "" : token.trim().toLowerCase(java.util.Locale.ROOT);
    for (int i = 0; i < serverNode.getChildCount(); i++) {
      Object child = serverNode.getChildAt(i);
      if (!(child instanceof DefaultMutableTreeNode childNode)) continue;
      Object userObject = childNode.getUserObject();
      if (!(userObject instanceof ServerTreeQuasselNetworkNodeData data)) continue;
      if (data.emptyState()) continue;
      if (want.equals(data.networkToken())) return childNode;
    }
    return null;
  }

  private static ServerNodes serverNodes(String serverId) {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(serverId);
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private Messages");
    serverNode.add(otherNode);
    serverNode.add(pmNode);
    return new ServerNodes(
        serverNode,
        pmNode,
        otherNode,
        new DefaultMutableTreeNode("Monitor"),
        new DefaultMutableTreeNode("Interceptors"),
        new TargetRef(serverId, "status"),
        TargetRef.notifications(serverId),
        TargetRef.logViewer(serverId),
        TargetRef.channelList(serverId),
        TargetRef.weechatFilters(serverId),
        TargetRef.ignores(serverId),
        TargetRef.dccTransfers(serverId));
  }
}
