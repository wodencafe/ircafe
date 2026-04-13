package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
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
    AtomicReference<ServerTreeBuiltInLayout> appliedLayout = new AtomicReference<>();
    AtomicReference<ServerTreeRootSiblingOrder> appliedOrder = new AtomicReference<>();

    ServerTreeBuiltInLayout layout = ServerTreeBuiltInLayout.defaults();
    ServerTreeRootSiblingOrder order = ServerTreeRootSiblingOrder.defaults();
    ServerTreeServerLeafVisibilityCoordinator coordinator =
        new ServerTreeServerLeafVisibilityCoordinator();

    coordinator.syncUiLeafVisibilityForServer(
        context(
            fixture,
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
            sid -> false,
            () -> false),
        serverId);

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
        new ServerTreeServerLeafVisibilityCoordinator();

    coordinator.syncUiLeafVisibilityForServer(
        context(
            fixture,
            sid -> new ServerBuiltInNodesVisibility(true, true, true, false, false),
            sid -> ServerTreeBuiltInLayout.defaults(),
            sid -> ServerTreeRootSiblingOrder.defaults(),
            (sn, next) -> {},
            (sn, next) -> {},
            sid -> false,
            () -> false),
        serverId);

    assertFalse(fixture.leaves.containsKey(dccRef));
    assertNull(dccLeaf.getParent());
  }

  @Test
  void syncHidesQuasselRootChannelListMonitorAndInterceptors() {
    String serverId = "quassel";
    Fixture fixture = new Fixture(serverId);

    DefaultMutableTreeNode channelListLeaf =
        new DefaultMutableTreeNode(
            new ServerTreeNodeData(TargetRef.channelList(serverId), "Channel List"));
    fixture.nodes.serverNode.insert(channelListLeaf, 0);
    fixture.leaves.put(TargetRef.channelList(serverId), channelListLeaf);

    fixture.nodes.otherNode.add(fixture.nodes.monitorNode);
    fixture.nodes.otherNode.add(fixture.nodes.interceptorsNode);

    ServerTreeServerLeafVisibilityCoordinator coordinator =
        new ServerTreeServerLeafVisibilityCoordinator();

    coordinator.syncUiLeafVisibilityForServer(
        context(
            fixture,
            sid -> new ServerBuiltInNodesVisibility(true, true, true, true, true),
            sid -> ServerTreeBuiltInLayout.defaults(),
            sid -> ServerTreeRootSiblingOrder.defaults(),
            (sn, next) -> {
              if (fixture.nodes.monitorNode.getParent() != fixture.nodes.otherNode) {
                fixture.nodes.otherNode.add(fixture.nodes.monitorNode);
              }
              if (fixture.nodes.interceptorsNode.getParent() != fixture.nodes.otherNode) {
                fixture.nodes.otherNode.add(fixture.nodes.interceptorsNode);
              }
            },
            (sn, next) -> {},
            sid -> "quassel".equals(sid),
            () -> true),
        serverId);

    assertFalse(fixture.leaves.containsKey(TargetRef.channelList(serverId)));
    assertNull(channelListLeaf.getParent());
    assertNull(fixture.nodes.monitorNode.getParent());
    assertNull(fixture.nodes.interceptorsNode.getParent());
    assertNull(fixture.nodes.pmNode.getParent());
  }

  @Test
  void syncKeepsAliasedQuasselChannelListOnNetworkNode() {
    String serverId = "quassel";
    Fixture fixture = new Fixture(serverId);

    DefaultMutableTreeNode networkNode = new DefaultMutableTreeNode("network");
    fixture.nodes.serverNode.insert(networkNode, 0);
    TargetRef networkChannelListRef = TargetRef.channelList(serverId, "libera");
    DefaultMutableTreeNode networkChannelListLeaf =
        new DefaultMutableTreeNode(new ServerTreeNodeData(networkChannelListRef, "Channel List"));
    networkNode.add(networkChannelListLeaf);
    fixture.leaves.put(networkChannelListRef, networkChannelListLeaf);
    fixture.leaves.put(TargetRef.channelList(serverId), networkChannelListLeaf);

    ServerTreeServerLeafVisibilityCoordinator coordinator =
        new ServerTreeServerLeafVisibilityCoordinator();

    coordinator.syncUiLeafVisibilityForServer(
        context(
            fixture,
            sid -> new ServerBuiltInNodesVisibility(true, true, true, true, true),
            sid -> ServerTreeBuiltInLayout.defaults(),
            sid -> ServerTreeRootSiblingOrder.defaults(),
            (sn, next) -> {},
            (sn, next) -> {},
            sid -> "quassel".equals(sid),
            () -> true),
        serverId);

    assertEquals(networkChannelListLeaf, fixture.leaves.get(TargetRef.channelList(serverId)));
    assertEquals(networkNode, networkChannelListLeaf.getParent());
  }

  private static ServerTreeServerLeafVisibilityCoordinator.Context context(
      Fixture fixture,
      java.util.function.Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      java.util.function.Function<String, ServerTreeBuiltInLayout> builtInLayout,
      java.util.function.Function<String, ServerTreeRootSiblingOrder> rootSiblingOrder,
      java.util.function.BiConsumer<ServerNodes, ServerTreeBuiltInLayout> applyBuiltInLayoutToTree,
      java.util.function.BiConsumer<ServerNodes, ServerTreeRootSiblingOrder>
          applyRootSiblingOrderToTree,
      java.util.function.Predicate<String> isQuasselServer,
      java.util.function.BooleanSupplier showDccTransfersNodes) {
    return ServerTreeServerLeafVisibilityCoordinator.context(
        "Channel List",
        "Filters",
        "Ignores",
        "DCC Transfers",
        "Notifications",
        "Log Viewer",
        fixture.nodeVisibilityMutator,
        value -> value == null ? "" : value.trim(),
        sid -> fixture.serverNodesById.get(sid),
        builtInNodesVisibility,
        builtInLayout,
        rootSiblingOrder,
        applyBuiltInLayoutToTree,
        applyRootSiblingOrderToTree,
        sid -> "Server",
        isQuasselServer,
        showDccTransfersNodes,
        fixture.leaves::get);
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
