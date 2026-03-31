package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeTargetLifecycleCoordinatorContextTest {

  @Test
  void contextDelegatesTargetLifecycleOperations() {
    String serverId = "libera";
    TargetRef channelRef = new TargetRef(serverId, "#ircafe");
    ServerNodes nodes = serverNodes(serverId);
    ServerTreeBuiltInLayout builtInLayout =
        new ServerTreeBuiltInLayout(
            List.of(ServerTreeBuiltInLayoutNode.SERVER),
            List.of(ServerTreeBuiltInLayoutNode.MONITOR));
    ServerTreeRootSiblingOrder rootSiblingOrder =
        new ServerTreeRootSiblingOrder(List.of(ServerTreeRootSiblingNode.OTHER));
    AtomicBoolean applicationRootVisible = new AtomicBoolean(true);
    AtomicBoolean dccTransfersNodesVisible = new AtomicBoolean(false);
    AtomicReference<Boolean> setApplicationRootVisible = new AtomicReference<>();
    AtomicReference<TargetRef> addedApplicationRef = new AtomicReference<>();
    AtomicReference<String> addedApplicationLabel = new AtomicReference<>();
    AtomicBoolean applicationRootStructureChanged = new AtomicBoolean(false);
    AtomicReference<Boolean> setDccTransfersVisible = new AtomicReference<>();
    AtomicReference<ServerNodes> appliedBuiltInLayoutServer = new AtomicReference<>();
    AtomicReference<ServerTreeBuiltInLayout> appliedBuiltInLayout = new AtomicReference<>();
    AtomicReference<ServerNodes> appliedRootOrderServer = new AtomicReference<>();
    AtomicReference<ServerTreeRootSiblingOrder> appliedRootOrder = new AtomicReference<>();
    AtomicReference<String> persistedBuiltInLayoutServer = new AtomicReference<>();
    AtomicReference<String> rememberedPmServer = new AtomicReference<>();
    AtomicReference<String> rememberedPmTarget = new AtomicReference<>();
    AtomicReference<TargetRef> ensuredChannel = new AtomicReference<>();
    AtomicReference<String> sortedChannelsServer = new AtomicReference<>();
    AtomicReference<String> emittedManagedChannelsServer = new AtomicReference<>();
    AtomicReference<DefaultMutableTreeNode> expandedNode = new AtomicReference<>();
    AtomicReference<TargetRef> backendParentRef = new AtomicReference<>();
    AtomicReference<ServerNodes> backendParentServerNodes = new AtomicReference<>();
    AtomicBoolean reloadRootCalled = new AtomicBoolean(false);

    ServerTreeTargetLifecycleCoordinator.Context context =
        ServerTreeTargetLifecycleCoordinator.context(
            applicationRootVisible::get,
            setApplicationRootVisible::set,
            ref -> "App " + ref.target(),
            (ref, label) -> {
              addedApplicationRef.set(ref);
              addedApplicationLabel.set(label);
            },
            () -> applicationRootStructureChanged.set(true),
            dccTransfersNodesVisible::get,
            setDccTransfersVisible::set,
            id -> ServerBuiltInNodesVisibility.defaults(),
            id -> serverId.equals(id) ? nodes : null,
            ref -> ServerTreeBuiltInLayoutNode.SERVER,
            id -> builtInLayout,
            id -> rootSiblingOrder,
            (ref, serverNodes) -> {
              backendParentRef.set(ref);
              backendParentServerNodes.set(serverNodes);
              return serverNodes == null ? null : serverNodes.pmNode;
            },
            serverNodes ->
                serverNodes.channelListRef == null
                    ? null
                    : new DefaultMutableTreeNode("Channel List"),
            (serverNodes, layout) -> {
              appliedBuiltInLayoutServer.set(serverNodes);
              appliedBuiltInLayout.set(layout);
            },
            (serverNodes, order) -> {
              appliedRootOrderServer.set(serverNodes);
              appliedRootOrder.set(order);
            },
            persistedBuiltInLayoutServer::set,
            ref -> false,
            () -> true,
            (sid, target) -> {
              rememberedPmServer.set(sid);
              rememberedPmTarget.set(target);
            },
            ensuredChannel::set,
            sortedChannelsServer::set,
            emittedManagedChannelsServer::set,
            id -> id == null ? "" : id.trim(),
            expandedNode::set,
            () -> reloadRootCalled.set(true));

    assertTrue(context.applicationRootVisible());
    context.setApplicationRootVisible(false);
    assertFalse(setApplicationRootVisible.get());

    assertEquals("App #ircafe", context.applicationLeafLabel(channelRef));
    context.addApplicationLeaf(channelRef, "App #ircafe");
    assertSame(channelRef, addedApplicationRef.get());
    assertEquals("App #ircafe", addedApplicationLabel.get());
    context.nodeStructureChangedForApplicationRoot();
    assertTrue(applicationRootStructureChanged.get());

    assertFalse(context.dccTransfersNodesVisible());
    context.setDccTransfersNodesVisible(true);
    assertTrue(setDccTransfersVisible.get());

    assertTrue(context.builtInNodesVisibility(serverId).server());
    assertSame(nodes, context.addServerRoot(serverId));
    assertEquals(
        ServerTreeBuiltInLayoutNode.SERVER, context.builtInLayoutNodeKindForRef(channelRef));
    assertSame(builtInLayout, context.builtInLayout(serverId));
    assertSame(rootSiblingOrder, context.rootSiblingOrder(serverId));
    assertSame(nodes.pmNode, context.backendSpecificParent(channelRef, nodes));
    assertSame(channelRef, backendParentRef.get());
    assertSame(nodes, backendParentServerNodes.get());
    assertEquals("Channel List", context.ensureChannelListNode(nodes).getUserObject().toString());

    context.applyBuiltInLayoutToTree(nodes, builtInLayout);
    context.applyRootSiblingOrderToTree(nodes, rootSiblingOrder);
    assertSame(nodes, appliedBuiltInLayoutServer.get());
    assertSame(builtInLayout, appliedBuiltInLayout.get());
    assertSame(nodes, appliedRootOrderServer.get());
    assertSame(rootSiblingOrder, appliedRootOrder.get());

    context.persistBuiltInLayoutFromTree(serverId);
    assertEquals(serverId, persistedBuiltInLayoutServer.get());
    assertFalse(context.isPrivateMessageTarget(channelRef));
    assertTrue(context.shouldPersistPrivateMessageList());
    context.rememberPrivateMessageTarget(serverId, "nick");
    assertEquals(serverId, rememberedPmServer.get());
    assertEquals("nick", rememberedPmTarget.get());
    context.ensureChannelKnownInConfig(channelRef);
    assertSame(channelRef, ensuredChannel.get());
    context.sortChannelsUnderChannelList(serverId);
    context.emitManagedChannelsChanged(serverId);
    assertEquals(serverId, sortedChannelsServer.get());
    assertEquals(serverId, emittedManagedChannelsServer.get());

    assertEquals(serverId, context.normalizeServerId(" libera "));
    context.expandPath(nodes.serverNode);
    assertSame(nodes.serverNode, expandedNode.get());
    context.reloadRoot();
    assertTrue(reloadRootCalled.get());
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
