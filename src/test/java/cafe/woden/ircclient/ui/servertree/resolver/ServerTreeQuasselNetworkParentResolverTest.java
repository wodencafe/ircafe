package cafe.woden.ircclient.ui.servertree.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
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
    assertEquals("libera", ((DefaultMutableTreeNode) parent.getParent()).getUserObject());
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
