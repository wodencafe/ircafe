package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeServerCatalogSynchronizerContextTest {

  @Test
  void contextDelegatesServerCatalogSynchronizerOperations() throws Exception {
    String serverId = "libera";
    ServerNodes nodes = serverNodes(serverId);
    TargetRef leafRef = new TargetRef(serverId, "#ircafe");
    DefaultMutableTreeNode leafNode = new DefaultMutableTreeNode("#ircafe");
    nodes.serverNode.add(leafNode);

    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    root.add(nodes.serverNode);
    TrackingTreeModel model = new TrackingTreeModel(root);
    JTree tree = new JTree(model);

    AtomicBoolean startupSelectionCompleted = new AtomicBoolean(false);
    AtomicReference<String> addedServer = new AtomicReference<>();
    AtomicReference<String> removedServer = new AtomicReference<>();
    AtomicReference<Map<String, Set<String>>> bouncerControlByBackend =
        new AtomicReference<>(Map.of());
    AtomicReference<Set<TreePath>> restoredPaths = new AtomicReference<>(Set.of());
    AtomicReference<TargetRef> selectedTarget = new AtomicReference<>();
    AtomicReference<String> startupDefaultServer = new AtomicReference<>();
    AtomicBoolean hasValidSelection = new AtomicBoolean(false);

    Map<String, ServerNodes> servers = Map.of(serverId, nodes);
    Map<TargetRef, DefaultMutableTreeNode> leaves = Map.of(leafRef, leafNode);
    TreePath defaultPath = new TreePath(root.getPath());
    TreePath expandedPath = new TreePath(nodes.serverNode.getPath());

    ServerTreeServerCatalogSynchronizer.Context context =
        ServerTreeServerCatalogSynchronizer.context(
            tree,
            servers,
            leaves,
            new java.util.HashMap<>(),
            new java.util.HashSet<>(),
            new java.util.HashMap<>(),
            new java.util.HashMap<>(),
            model,
            root,
            startupSelectionCompleted::get,
            () -> startupSelectionCompleted.set(true),
            () -> leafRef,
            addedServer::set,
            removedServer::set,
            bouncerControlByBackend::set,
            () -> Set.of(expandedPath),
            restoredPaths::set,
            hasValidSelection::get,
            selectedTarget::set,
            () -> serverId,
            startupDefaultServer::set,
            () -> defaultPath);

    assertFalse(context.treeHasSelectionPath());
    context.selectDefaultPath();
    assertTrue(context.treeHasSelectionPath());

    assertFalse(context.startupSelectionCompleted());
    context.markStartupSelectionCompleted();
    assertTrue(context.startupSelectionCompleted());

    assertSame(leafRef, context.selectedTargetRef());
    assertTrue(context.hasServer(serverId));
    assertEquals(Set.of(serverId), context.currentServerIds());
    assertTrue(context.hasLeaf(leafRef));
    assertFalse(context.hasLeaf(TargetRef.notifications(serverId)));

    context.addServerRoot("oftc");
    context.removeServerRoot("oftc");
    assertEquals("oftc", addedServer.get());
    assertEquals("oftc", removedServer.get());

    Map<String, Set<String>> nextBouncerControlByBackend =
        Map.of(
            ServerTreeBouncerBackends.SOJU,
            Set.of("s1"),
            ServerTreeBouncerBackends.ZNC,
            Set.of("z1"),
            ServerTreeBouncerBackends.GENERIC,
            Set.of("g1"));
    context.updateBouncerControlLabels(nextBouncerControlByBackend);
    assertEquals(nextBouncerControlByBackend, bouncerControlByBackend.get());

    assertEquals(Set.of(expandedPath), context.snapshotExpandedTreePaths());
    context.restoreExpandedTreePaths(Set.of(defaultPath));
    assertEquals(Set.of(defaultPath), restoredPaths.get());

    context.nodeChangedForServer(serverId);
    context.reloadTreeModel();
    assertTrue(model.nodeChangedCalled.get());
    assertTrue(model.reloadCalled.get());

    context.selectTarget(leafRef);
    context.selectStartupDefaultForServer(serverId);
    assertSame(leafRef, selectedTarget.get());
    assertEquals(serverId, startupDefaultServer.get());
    assertEquals(serverId, context.firstServerId());
    hasValidSelection.set(true);
    assertTrue(context.hasValidTreeSelection());
    assertTrue(context.serverDisplayNames().isEmpty());
    assertTrue(context.ephemeralServerIds().isEmpty());
    assertTrue(context.bouncerControlServerIdsByBackendId().isEmpty());
    assertTrue(context.originByServerIdByBackendId().isEmpty());

    CountDownLatch latch = new CountDownLatch(1);
    context.runLater(latch::countDown);
    assertTrue(latch.await(2, TimeUnit.SECONDS));
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
    private final AtomicBoolean nodeChangedCalled = new AtomicBoolean(false);
    private final AtomicBoolean reloadCalled = new AtomicBoolean(false);

    private TrackingTreeModel(DefaultMutableTreeNode root) {
      super(root);
    }

    @Override
    public void nodeChanged(javax.swing.tree.TreeNode node) {
      nodeChangedCalled.set(true);
      super.nodeChanged(node);
    }

    @Override
    public void reload(javax.swing.tree.TreeNode node) {
      reloadCalled.set(true);
      super.reload(node);
    }
  }
}
