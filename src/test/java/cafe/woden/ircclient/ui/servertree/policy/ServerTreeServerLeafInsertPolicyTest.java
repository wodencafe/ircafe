package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeServerLeafInsertPolicyTest {

  @Test
  void channelListAlwaysInsertsAtZero() {
    ServerNodesFixture fixture = new ServerNodesFixture("libera");
    fixture.serverNode.add(new DefaultMutableTreeNode("existing"));

    int idx =
        ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
            fixture.nodes, fixture.nodes.channelListRef, fixture.leaves::get);

    assertEquals(0, idx);
  }

  @Test
  void dccTransfersInsertsAfterChannelListWhenPresent() {
    ServerNodesFixture fixture = new ServerNodesFixture("libera");

    DefaultMutableTreeNode channelListLeaf = new DefaultMutableTreeNode("channel-list");
    fixture.serverNode.add(channelListLeaf);
    fixture.serverNode.add(new DefaultMutableTreeNode("other"));
    fixture.leaves.put(fixture.nodes.channelListRef, channelListLeaf);

    int idx =
        ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
            fixture.nodes, fixture.nodes.dccTransfersRef, fixture.leaves::get);

    assertEquals(1, idx);
  }

  @Test
  void dccTransfersFallsBackToZeroWhenChannelListNotAttached() {
    ServerNodesFixture fixture = new ServerNodesFixture("libera");
    DefaultMutableTreeNode detachedChannelListLeaf = new DefaultMutableTreeNode("channel-list");
    fixture.leaves.put(fixture.nodes.channelListRef, detachedChannelListLeaf);

    int idx =
        ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
            fixture.nodes, fixture.nodes.dccTransfersRef, fixture.leaves::get);

    assertEquals(0, idx);
  }

  @Test
  void otherLeavesAppendToServerChildCount() {
    ServerNodesFixture fixture = new ServerNodesFixture("libera");
    fixture.serverNode.add(new DefaultMutableTreeNode("a"));
    fixture.serverNode.add(new DefaultMutableTreeNode("b"));

    int idx =
        ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
            fixture.nodes, fixture.nodes.logViewerRef, fixture.leaves::get);

    assertEquals(2, idx);
  }

  private static final class ServerNodesFixture {
    private final DefaultMutableTreeNode serverNode;
    private final Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    private final ServerNodes nodes;

    private ServerNodesFixture(String serverId) {
      this.serverNode = new DefaultMutableTreeNode(serverId);
      DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("pm");
      DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("other");
      DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("monitor");
      DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("interceptors");
      this.nodes =
          new ServerNodes(
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
}
