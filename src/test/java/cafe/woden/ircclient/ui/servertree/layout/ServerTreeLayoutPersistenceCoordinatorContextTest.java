package cafe.woden.ircclient.ui.servertree.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeLayoutPersistenceCoordinatorContextTest {

  @Test
  void contextDelegatesLayoutPersistenceOperations() {
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");
    DefaultMutableTreeNode statusNode = new DefaultMutableTreeNode("Server");
    ServerTreeRootSiblingOrder currentOrder =
        new ServerTreeRootSiblingOrder(List.of(ServerTreeRootSiblingNode.OTHER));
    ServerTreeBuiltInLayout currentLayout =
        new ServerTreeBuiltInLayout(
            List.of(ServerTreeBuiltInLayoutNode.SERVER),
            List.of(ServerTreeBuiltInLayoutNode.MONITOR));
    AtomicReference<String> persistedRootOrderServer = new AtomicReference<>();
    AtomicReference<ServerTreeRootSiblingOrder> persistedRootOrder = new AtomicReference<>();
    AtomicReference<String> persistedBuiltInLayoutServer = new AtomicReference<>();
    AtomicReference<ServerTreeBuiltInLayout> persistedBuiltInLayout = new AtomicReference<>();

    ServerTreeLayoutPersistenceCoordinator.Context context =
        ServerTreeLayoutPersistenceCoordinator.context(
            node ->
                node == otherNode
                    ? ServerTreeRootSiblingNode.OTHER
                    : ServerTreeRootSiblingNode.CHANNEL_LIST,
            node ->
                node == statusNode
                    ? ServerTreeBuiltInLayoutNode.SERVER
                    : ServerTreeBuiltInLayoutNode.MONITOR,
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

    assertEquals(ServerTreeRootSiblingNode.OTHER, context.rootSiblingNodeKindForNode(otherNode));
    assertEquals(
        ServerTreeBuiltInLayoutNode.SERVER, context.builtInLayoutNodeKindForNode(statusNode));
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
