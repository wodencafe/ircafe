package cafe.woden.ircclient.ui.servertree.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeMiddleDragReorderHandlerContextTest {

  @Test
  void contextDelegatesMiddleDragReorderOperations() {
    JTree tree = new JTree();
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("node");
    ServerNodes serverNodes = serverNodes("libera");
    AtomicReference<DefaultMutableTreeNode> insertionLineParent = new AtomicReference<>();
    AtomicReference<Integer> insertionLineIndex = new AtomicReference<>();
    AtomicBoolean insertionLineCleared = new AtomicBoolean(false);
    AtomicReference<DefaultMutableTreeNode> persistedParent = new AtomicReference<>();
    AtomicReference<String> persistedBuiltInServer = new AtomicReference<>();
    AtomicReference<String> persistedRootOrderServer = new AtomicReference<>();
    AtomicBoolean suppressedSelectionBroadcast = new AtomicBoolean(false);
    AtomicBoolean refreshedNodeActionsEnabled = new AtomicBoolean(false);

    ServerTreeMiddleDragReorderHandler.Context context =
        ServerTreeMiddleDragReorderHandler.context(
            tree,
            model,
            value -> value == node,
            value -> false,
            value -> true,
            value -> "libera",
            value -> serverNodes,
            value -> ServerTreeRootSiblingNode.CHANNEL_LIST,
            value -> ServerTreeBuiltInLayoutNode.SERVER,
            value -> 1,
            value -> 3,
            (nodes, desired) -> 2,
            (parent, index) -> {
              insertionLineParent.set(parent);
              insertionLineIndex.set(index);
            },
            () -> insertionLineCleared.set(true),
            value -> value == node,
            persistedParent::set,
            persistedBuiltInServer::set,
            persistedRootOrderServer::set,
            runnable -> {
              suppressedSelectionBroadcast.set(true);
              if (runnable != null) {
                runnable.run();
              }
            },
            () -> refreshedNodeActionsEnabled.set(true));

    assertSame(tree, context.tree());
    assertSame(model, context.model());
    assertTrue(context.isDraggableChannelNode(node));
    assertFalse(context.isRootSiblingReorderableNode(node));
    assertTrue(context.isMovableBuiltInNode(node));
    assertEquals("libera", context.owningServerIdForNode(node));
    assertSame(serverNodes, context.serverNodes("libera"));
    assertEquals(ServerTreeRootSiblingNode.CHANNEL_LIST, context.rootSiblingNodeKindForNode(node));
    assertEquals(ServerTreeBuiltInLayoutNode.SERVER, context.builtInLayoutNodeKindForNode(node));
    assertEquals(1, context.minInsertIndex(node));
    assertEquals(3, context.maxInsertIndex(node));
    assertEquals(2, context.rootBuiltInInsertIndex(serverNodes, 10));

    context.setInsertionLineForIndex(node, 4);
    assertSame(node, insertionLineParent.get());
    assertEquals(4, insertionLineIndex.get());
    context.clearInsertionLine();
    assertTrue(insertionLineCleared.get());
    assertTrue(context.isChannelListLeafNode(node));
    context.persistCustomOrderForParent(node);
    assertSame(node, persistedParent.get());
    context.persistBuiltInLayout("libera");
    context.persistRootSiblingOrder("libera");
    assertEquals("libera", persistedBuiltInServer.get());
    assertEquals("libera", persistedRootOrderServer.get());
    context.withSuppressedSelectionBroadcast(() -> {});
    assertTrue(suppressedSelectionBroadcast.get());
    context.refreshNodeActionsEnabled();
    assertTrue(refreshedNodeActionsEnabled.get());
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
        new cafe.woden.ircclient.model.TargetRef(serverId, "status"),
        cafe.woden.ircclient.model.TargetRef.notifications(serverId),
        cafe.woden.ircclient.model.TargetRef.logViewer(serverId),
        cafe.woden.ircclient.model.TargetRef.channelList(serverId),
        cafe.woden.ircclient.model.TargetRef.weechatFilters(serverId),
        cafe.woden.ircclient.model.TargetRef.ignores(serverId),
        cafe.woden.ircclient.model.TargetRef.dccTransfers(serverId));
  }
}
