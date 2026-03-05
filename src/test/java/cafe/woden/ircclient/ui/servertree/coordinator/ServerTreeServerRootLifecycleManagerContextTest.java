package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeServerRootLifecycleManagerContextTest {

  @Test
  void contextDelegatesServerRootLifecycleOperations() {
    String serverId = "libera";
    ServerNodes nodes = serverNodes(serverId);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    root.add(nodes.serverNode);
    TrackingTreeModel model = new TrackingTreeModel(root);
    JTree tree = new JTree(model);
    TreePath serverPath = new TreePath(nodes.serverNode.getPath());

    AtomicReference<String> markedKnownServer = new AtomicReference<>();
    AtomicReference<String> loadedChannelStateServer = new AtomicReference<>();
    AtomicBoolean showDccTransfersNodes = new AtomicBoolean(true);
    AtomicReference<ServerNodes> appliedBuiltInLayoutServer = new AtomicReference<>();
    AtomicReference<RuntimeConfigStore.ServerTreeBuiltInLayout> appliedBuiltInLayout =
        new AtomicReference<>();
    AtomicReference<ServerNodes> appliedRootOrderServer = new AtomicReference<>();
    AtomicReference<RuntimeConfigStore.ServerTreeRootSiblingOrder> appliedRootOrder =
        new AtomicReference<>();
    AtomicReference<String> refreshedNotificationsServer = new AtomicReference<>();
    AtomicReference<String> refreshedInterceptorsServer = new AtomicReference<>();
    AtomicReference<String> cleanedUpServer = new AtomicReference<>();
    AtomicReference<DefaultMutableTreeNode> removedEmptyGroup = new AtomicReference<>();

    Map<String, ServerNodes> servers = new java.util.HashMap<>(Map.of(serverId, nodes));
    Map<TargetRef, DefaultMutableTreeNode> leaves = new java.util.HashMap<>();
    RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout =
        new RuntimeConfigStore.ServerTreeBuiltInLayout(
            List.of(RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER),
            List.of(RuntimeConfigStore.ServerTreeBuiltInLayoutNode.MONITOR));
    RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder =
        new RuntimeConfigStore.ServerTreeRootSiblingOrder(
            List.of(RuntimeConfigStore.ServerTreeRootSiblingNode.OTHER));
    List<InterceptorDefinition> interceptorDefinitions = List.of();

    ServerTreeServerRootLifecycleManager.Context context =
        ServerTreeServerRootLifecycleManager.context(
            id -> id == null ? "" : id.trim(),
            servers,
            markedKnownServer::set,
            loadedChannelStateServer::set,
            id -> root,
            id -> ServerBuiltInNodesVisibility.defaults(),
            showDccTransfersNodes::get,
            id -> "Server",
            id -> 5,
            id -> 2,
            id -> interceptorDefinitions,
            leaves,
            id -> builtInLayout,
            id -> rootSiblingOrder,
            (serverNodes, layout) -> {
              appliedBuiltInLayoutServer.set(serverNodes);
              appliedBuiltInLayout.set(layout);
            },
            (serverNodes, order) -> {
              appliedRootOrderServer.set(serverNodes);
              appliedRootOrder.set(order);
            },
            model,
            root,
            tree,
            refreshedNotificationsServer::set,
            refreshedInterceptorsServer::set,
            cleanedUpServer::set,
            removedEmptyGroup::set);

    assertEquals(serverId, context.normalizeServerId(" libera "));
    assertSame(nodes, context.server(serverId));
    assertSame(nodes, context.removeServer(serverId));
    assertEquals(0, servers.size());
    context.putServer(serverId, nodes);
    assertSame(nodes, context.server(serverId));

    context.markServerKnown(serverId);
    context.loadChannelStateForServer(serverId);
    assertEquals(serverId, markedKnownServer.get());
    assertEquals(serverId, loadedChannelStateServer.get());

    assertSame(root, context.resolveParentForServer(serverId));
    assertTrue(context.builtInNodesVisibility(serverId).server());
    assertTrue(context.showDccTransfersNodes());
    assertEquals("Server", context.statusLeafLabelForServer(serverId));
    assertEquals(5, context.notificationsCount(serverId));
    assertEquals(2, context.interceptorHitCount(serverId));
    assertSame(interceptorDefinitions, context.interceptorDefinitions(serverId));

    TargetRef statusRef = nodes.statusRef;
    DefaultMutableTreeNode statusNode = new DefaultMutableTreeNode("Server");
    context.putLeaves(Map.of(statusRef, statusNode));
    assertSame(statusNode, leaves.get(statusRef));

    assertSame(builtInLayout, context.builtInLayout(serverId));
    assertSame(rootSiblingOrder, context.rootSiblingOrder(serverId));

    context.applyBuiltInLayoutToTree(nodes, builtInLayout);
    context.applyRootSiblingOrderToTree(nodes, rootSiblingOrder);
    assertSame(nodes, appliedBuiltInLayoutServer.get());
    assertSame(builtInLayout, appliedBuiltInLayout.get());
    assertSame(nodes, appliedRootOrderServer.get());
    assertSame(rootSiblingOrder, appliedRootOrder.get());

    context.reloadRootModel();
    assertTrue(model.reloadCalled.get());

    context.expandPath(nodes.serverNode);
    assertTrue(tree.isExpanded(serverPath));
    context.collapsePath(nodes.serverNode);
    assertFalse(tree.isExpanded(serverPath));

    context.refreshNotificationsCount(serverId);
    context.refreshInterceptorGroupCount(serverId);
    context.cleanupServerState(serverId);
    context.removeEmptyGroupIfNeeded(nodes.otherNode);
    assertEquals(serverId, refreshedNotificationsServer.get());
    assertEquals(serverId, refreshedInterceptorsServer.get());
    assertEquals(serverId, cleanedUpServer.get());
    assertSame(nodes.otherNode, removedEmptyGroup.get());
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

  private static final class TrackingTreeModel extends DefaultTreeModel {
    private final AtomicBoolean reloadCalled = new AtomicBoolean(false);

    private TrackingTreeModel(DefaultMutableTreeNode root) {
      super(root);
    }

    @Override
    public void reload(javax.swing.tree.TreeNode node) {
      reloadCalled.set(true);
      super.reload(node);
    }
  }
}
