package cafe.woden.ircclient.ui.servertree.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeBuiltInLayoutOrchestratorContextTest {

  @Test
  void contextDelegatesBuiltInLayoutOperations() {
    String serverId = "libera";
    ServerNodes nodes = serverNodes(serverId);
    DefaultMutableTreeNode statusNode = new DefaultMutableTreeNode("Server");
    Map<TargetRef, DefaultMutableTreeNode> leaves = Map.of(nodes.statusRef, statusNode);
    ServerBuiltInNodesVisibility visibility = ServerBuiltInNodesVisibility.defaults();
    AtomicReference<DefaultMutableTreeNode> structureChanged = new AtomicReference<>();

    ServerTreeBuiltInLayoutOrchestrator.Context context =
        ServerTreeBuiltInLayoutOrchestrator.context(
            id -> id == null ? "" : id.trim(),
            id -> serverId.equals(id) ? nodes : null,
            leaves::get,
            id -> visibility,
            node -> RuntimeConfigStore.ServerTreeRootSiblingNode.OTHER,
            structureChanged::set);

    assertEquals(serverId, context.normalizeServerId(" libera "));
    assertSame(nodes, context.serverNodes(serverId));
    assertSame(statusNode, context.leafNode(nodes.statusRef));
    assertSame(visibility, context.builtInNodesVisibility(serverId));
    assertEquals(
        RuntimeConfigStore.ServerTreeRootSiblingNode.OTHER,
        context.rootSiblingNodeKindForNode(nodes.otherNode));

    context.nodeStructureChanged(nodes.serverNode);
    assertSame(nodes.serverNode, structureChanged.get());
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
