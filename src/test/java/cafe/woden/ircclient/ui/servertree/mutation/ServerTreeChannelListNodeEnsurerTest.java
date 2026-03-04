package cafe.woden.ircclient.ui.servertree.mutation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeChannelListNodeEnsurerTest {

  @Test
  void ensureChannelListNodeInsertsMissingLeafAtFixedPosition() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    ServerNodes nodes = serverNodes("libera");
    nodes.serverNode.add(new DefaultMutableTreeNode("status"));
    AtomicReference<DefaultMutableTreeNode> insertedParent = new AtomicReference<>();
    AtomicReference<int[]> insertedIndexes = new AtomicReference<>();
    ServerTreeChannelListNodeEnsurer ensurer =
        new ServerTreeChannelListNodeEnsurer(
            "Channel List",
            leaves,
            (parent, indexes) -> {
              insertedParent.set(parent);
              insertedIndexes.set(indexes);
            });

    DefaultMutableTreeNode ensured = ensurer.ensureChannelListNode(nodes);

    assertNotNull(ensured);
    assertSame(nodes.serverNode, ensured.getParent());
    assertEquals(0, nodes.serverNode.getIndex(ensured));
    assertSame(nodes.serverNode, insertedParent.get());
    assertArrayEquals(new int[] {0}, insertedIndexes.get());
    assertSame(ensured, leaves.get(nodes.channelListRef));
    Object userObject = ensured.getUserObject();
    assertEquals(nodes.channelListRef, ((ServerTreeNodeData) userObject).ref);
    assertEquals("Channel List", ((ServerTreeNodeData) userObject).label);
  }

  @Test
  void ensureChannelListNodeReturnsExistingLeafWithoutInsertEvent() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    ServerNodes nodes = serverNodes("libera");
    DefaultMutableTreeNode existing = new DefaultMutableTreeNode("Channel List");
    nodes.serverNode.add(existing);
    leaves.put(nodes.channelListRef, existing);
    AtomicInteger insertEvents = new AtomicInteger();
    ServerTreeChannelListNodeEnsurer ensurer =
        new ServerTreeChannelListNodeEnsurer(
            "Channel List", leaves, (parent, indexes) -> insertEvents.incrementAndGet());

    DefaultMutableTreeNode ensured = ensurer.ensureChannelListNode(nodes);

    assertSame(existing, ensured);
    assertEquals(0, insertEvents.get());
  }

  private static ServerNodes serverNodes(String serverId) {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(serverId);
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private Messages");
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");
    DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("Monitor");
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("Interceptors");
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
