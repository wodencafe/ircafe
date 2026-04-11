package cafe.woden.ircclient.ui.servertree.composition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeStateInteractionCollaboratorsFactoryTest {

  @Test
  void createBuildsCollaboratorsAndWiresSharedChannelStateStore() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);
    ServerTreeChannelStateStore channelStateStore = new ServerTreeChannelStateStore();
    channelStateStore
        .channelSortModeByServer()
        .put("libera", ServerTreeDockable.ChannelSortMode.CUSTOM);

    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    leaves.put(
        channelRef, new DefaultMutableTreeNode(new ServerTreeNodeData(channelRef, "#ircafe")));

    DefaultMutableTreeNode typingNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(channelRef, "#ircafe"));
    DefaultMutableTreeNode typingParent = new DefaultMutableTreeNode("typing-parent");
    typingParent.add(typingNode);
    Set<DefaultMutableTreeNode> typingActivityNodes = new HashSet<>();
    typingActivityNodes.add(typingNode);

    ServerTreeRuntimeState runtimeState = new ServerTreeRuntimeState(16, __ -> {});
    runtimeState.setServerConnectionState("libera", ConnectionState.CONNECTED);
    ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore =
        new ServerTreePrivateMessageOnlineStateStore();
    AtomicReference<String> clearedPrivateMessageServer = new AtomicReference<>("");

    ServerTreeStateInteractionCollaborators collaborators =
        new ServerTreeStateInteractionCollaboratorsFactory(new ServerTreeServerStateCleaner())
            .create(
                new ServerTreeStateInteractionCollaboratorsFactory.Inputs(
                    tree,
                    model,
                    null,
                    null,
                    channelStateStore,
                    null,
                    runtimeState,
                    new HashMap<>(),
                    leaves,
                    typingActivityNodes,
                    privateMessageOnlineStateStore,
                    ref -> false,
                    node -> node != null && node.getUserObject() instanceof String,
                    __ -> {},
                    () -> 12,
                    clearedPrivateMessageServer::set,
                    noOpOverlayContext(runtimeState),
                    noOpChannelStateContext(),
                    noOpTargetRemovalContext(),
                    16,
                    12,
                    6));

    assertNotNull(collaborators.serverActionOverlay());
    assertNotNull(collaborators.serverRuntimeUiUpdater());
    assertNotNull(collaborators.serverStateCleaner());
    assertNotNull(collaborators.serverStateCleanerContext());
    assertNotNull(collaborators.channelStateCoordinator());
    assertNotNull(collaborators.ensureNodeParentResolver());
    assertNotNull(collaborators.ensureNodeLeafInserter());
    assertNotNull(collaborators.targetNodeRemovalMutator());
    assertNotNull(collaborators.targetRemovalStateCoordinator());
    assertNotNull(collaborators.detachedWarningClickHandler());
    assertNotNull(collaborators.rowInteractionHandler());

    collaborators
        .serverStateCleaner()
        .cleanupServerState(collaborators.serverStateCleanerContext(), "libera");

    assertFalse(channelStateStore.channelSortModeByServer().containsKey("libera"));
    assertEquals(ConnectionState.DISCONNECTED, runtimeState.connectionStateForServer("libera"));
    assertEquals("libera", clearedPrivateMessageServer.get());
    assertFalse(leaves.containsKey(channelRef));
    assertFalse(typingActivityNodes.contains(typingNode));
  }

  private static ServerTreeServerActionOverlay.Context noOpOverlayContext(
      ServerTreeRuntimeState runtimeState) {
    return new ServerTreeServerActionOverlay.Context() {
      @Override
      public boolean isServerNode(DefaultMutableTreeNode node) {
        return node != null && node.getUserObject() instanceof String;
      }

      @Override
      public boolean isChannelNode(DefaultMutableTreeNode node) {
        return false;
      }

      @Override
      public javax.swing.tree.TreePath serverPathForId(String serverId) {
        return null;
      }

      @Override
      public javax.swing.tree.TreePath channelPathForRef(TargetRef channelRef) {
        return null;
      }

      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return runtimeState.connectionStateForServer(serverId);
      }

      @Override
      public void connectServer(String serverId) {}

      @Override
      public void disconnectServer(String serverId) {}

      @Override
      public boolean isChannelDisconnected(TargetRef channelRef) {
        return false;
      }

      @Override
      public void joinChannel(TargetRef channelRef) {}

      @Override
      public void disconnectChannel(TargetRef channelRef) {}

      @Override
      public boolean confirmCloseChannel(TargetRef channelRef, String channelLabel) {
        return false;
      }

      @Override
      public void closeChannel(TargetRef channelRef) {}
    };
  }

  private static ServerTreeChannelStateCoordinator.Context noOpChannelStateContext() {
    return new ServerTreeChannelStateCoordinator.Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return serverId == null ? "" : serverId.trim();
      }

      @Override
      public DefaultMutableTreeNode channelListNode(String serverId) {
        return null;
      }

      @Override
      public Set<javax.swing.tree.TreePath> snapshotExpandedTreePaths() {
        return Set.of();
      }

      @Override
      public void restoreExpandedTreePaths(Set<javax.swing.tree.TreePath> expanded) {}

      @Override
      public void emitManagedChannelsChanged(String serverId) {}
    };
  }

  private static ServerTreeTargetRemovalStateCoordinator.Context noOpTargetRemovalContext() {
    return new ServerTreeTargetRemovalStateCoordinator.Context() {
      @Override
      public boolean isPrivateMessageTarget(TargetRef ref) {
        return false;
      }

      @Override
      public boolean shouldPersistPrivateMessageList() {
        return false;
      }

      @Override
      public String foldChannelKey(String channelName) {
        return channelName == null ? "" : channelName.trim().toLowerCase(java.util.Locale.ROOT);
      }

      @Override
      public void emitManagedChannelsChanged(String serverId) {}
    };
  }
}
