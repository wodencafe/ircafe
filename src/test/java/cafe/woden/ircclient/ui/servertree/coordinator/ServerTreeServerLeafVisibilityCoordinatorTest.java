package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeNodeVisibilityMutator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeServerLeafVisibilityCoordinatorTest {

  @Test
  void syncInsertsChannelListAndAppliesLayoutAndOrder() {
    String serverId = "libera";
    Fixture fixture = new Fixture(serverId);
    AtomicInteger layoutApplyCount = new AtomicInteger();
    AtomicInteger orderApplyCount = new AtomicInteger();
    AtomicReference<RuntimeConfigStore.ServerTreeBuiltInLayout> appliedLayout =
        new AtomicReference<>();
    AtomicReference<RuntimeConfigStore.ServerTreeRootSiblingOrder> appliedOrder =
        new AtomicReference<>();

    RuntimeConfigStore.ServerTreeBuiltInLayout layout =
        RuntimeConfigStore.ServerTreeBuiltInLayout.defaults();
    RuntimeConfigStore.ServerTreeRootSiblingOrder order =
        RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults();

    ServerTreeServerLeafVisibilityCoordinator coordinator =
        new ServerTreeServerLeafVisibilityCoordinator(
            "Channel List",
            "Filters",
            "Ignores",
            "DCC Transfers",
            "Notifications",
            "Log Viewer",
            fixture.nodeVisibilityMutator,
            value -> value == null ? "" : value.trim(),
            sid -> fixture.serverNodesById.get(sid),
            sid -> new ServerBuiltInNodesVisibility(true, true, true, false, false),
            sid -> layout,
            sid -> order,
            (sn, next) -> {
              layoutApplyCount.incrementAndGet();
              appliedLayout.set(next);
            },
            (sn, next) -> {
              orderApplyCount.incrementAndGet();
              appliedOrder.set(next);
            },
            sid -> "Server",
            () -> false,
            fixture.leaves::get);

    coordinator.syncUiLeafVisibilityForServer(serverId);

    DefaultMutableTreeNode channelListLeaf = fixture.leaves.get(TargetRef.channelList(serverId));
    assertNotNull(channelListLeaf);
    assertEquals(fixture.nodes.serverNode, channelListLeaf.getParent());
    assertFalse(fixture.leaves.containsKey(TargetRef.dccTransfers(serverId)));

    assertTrue(fixture.leaves.containsKey(new TargetRef(serverId, "status")));
    assertTrue(fixture.leaves.containsKey(TargetRef.notifications(serverId)));
    assertTrue(fixture.leaves.containsKey(TargetRef.logViewer(serverId)));
    assertTrue(fixture.leaves.containsKey(TargetRef.weechatFilters(serverId)));
    assertTrue(fixture.leaves.containsKey(TargetRef.ignores(serverId)));

    assertEquals(1, layoutApplyCount.get());
    assertEquals(layout, appliedLayout.get());
    assertEquals(1, orderApplyCount.get());
    assertEquals(order, appliedOrder.get());
  }

  @Test
  void syncRemovesDccTransfersWhenVisibilityDisabled() {
    String serverId = "libera";
    Fixture fixture = new Fixture(serverId);
    TargetRef dccRef = TargetRef.dccTransfers(serverId);
    DefaultMutableTreeNode dccLeaf = new DefaultMutableTreeNode("DCC Transfers");
    fixture.nodes.serverNode.add(dccLeaf);
    fixture.leaves.put(dccRef, dccLeaf);

    ServerTreeServerLeafVisibilityCoordinator coordinator =
        new ServerTreeServerLeafVisibilityCoordinator(
            "Channel List",
            "Filters",
            "Ignores",
            "DCC Transfers",
            "Notifications",
            "Log Viewer",
            fixture.nodeVisibilityMutator,
            value -> value == null ? "" : value.trim(),
            sid -> fixture.serverNodesById.get(sid),
            sid -> new ServerBuiltInNodesVisibility(true, true, true, false, false),
            sid -> RuntimeConfigStore.ServerTreeBuiltInLayout.defaults(),
            sid -> RuntimeConfigStore.ServerTreeRootSiblingOrder.defaults(),
            (sn, next) -> {},
            (sn, next) -> {},
            sid -> "Server",
            () -> false,
            fixture.leaves::get);

    coordinator.syncUiLeafVisibilityForServer(serverId);

    assertFalse(fixture.leaves.containsKey(dccRef));
    assertNull(dccLeaf.getParent());
  }

  private static final class Fixture {
    private final Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    private final Map<String, ServerNodes> serverNodesById = new HashMap<>();
    private final ServerTreeNodeVisibilityMutator nodeVisibilityMutator;
    private final ServerNodes nodes;

    private Fixture(String serverId) {
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
      DefaultTreeModel model = new DefaultTreeModel(root);
      this.nodeVisibilityMutator =
          new ServerTreeNodeVisibilityMutator(model, leaves, new HashSet<>());

      DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(serverId);
      DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("pm");
      DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("other");
      DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("monitor");
      DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("interceptors");

      serverNode.add(pmNode);
      serverNode.add(otherNode);
      root.add(serverNode);

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
      this.serverNodesById.put(serverId, nodes);
    }
  }
}
