package cafe.woden.ircclient.ui.servertree.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeLayoutPersistenceCoordinatorContextTest {

  @Test
  void contextDelegatesLayoutPersistenceOperations() {
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");
    DefaultMutableTreeNode statusNode = new DefaultMutableTreeNode("Server");
    RuntimeConfigStore.ServerTreeRootSiblingOrder currentOrder =
        new RuntimeConfigStore.ServerTreeRootSiblingOrder(
            List.of(RuntimeConfigStore.ServerTreeRootSiblingNode.OTHER));
    RuntimeConfigStore.ServerTreeBuiltInLayout currentLayout =
        new RuntimeConfigStore.ServerTreeBuiltInLayout(
            List.of(RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER),
            List.of(RuntimeConfigStore.ServerTreeBuiltInLayoutNode.MONITOR));
    AtomicReference<String> persistedRootOrderServer = new AtomicReference<>();
    AtomicReference<RuntimeConfigStore.ServerTreeRootSiblingOrder> persistedRootOrder =
        new AtomicReference<>();
    AtomicReference<String> persistedBuiltInLayoutServer = new AtomicReference<>();
    AtomicReference<RuntimeConfigStore.ServerTreeBuiltInLayout> persistedBuiltInLayout =
        new AtomicReference<>();

    ServerTreeLayoutPersistenceCoordinator.Context context =
        ServerTreeLayoutPersistenceCoordinator.context(
            node ->
                node == otherNode
                    ? RuntimeConfigStore.ServerTreeRootSiblingNode.OTHER
                    : RuntimeConfigStore.ServerTreeRootSiblingNode.CHANNEL_LIST,
            node ->
                node == statusNode
                    ? RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER
                    : RuntimeConfigStore.ServerTreeBuiltInLayoutNode.MONITOR,
            serverId -> currentOrder,
            serverId -> currentLayout,
            (serverId, order) -> {
              persistedRootOrderServer.set(serverId);
              persistedRootOrder.set(order);
            },
            (serverId, layout) -> {
              persistedBuiltInLayoutServer.set(serverId);
              persistedBuiltInLayout.set(layout);
            });

    assertEquals(
        RuntimeConfigStore.ServerTreeRootSiblingNode.OTHER,
        context.rootSiblingNodeKindForNode(otherNode));
    assertEquals(
        RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER,
        context.builtInLayoutNodeKindForNode(statusNode));
    assertSame(currentOrder, context.currentRootSiblingOrder("libera"));
    assertSame(currentLayout, context.currentBuiltInLayout("libera"));

    context.persistRootSiblingOrder("libera", currentOrder);
    context.persistBuiltInLayout("libera", currentLayout);

    assertEquals("libera", persistedRootOrderServer.get());
    assertSame(currentOrder, persistedRootOrder.get());
    assertEquals("libera", persistedBuiltInLayoutServer.get());
    assertSame(currentLayout, persistedBuiltInLayout.get());
  }
}
