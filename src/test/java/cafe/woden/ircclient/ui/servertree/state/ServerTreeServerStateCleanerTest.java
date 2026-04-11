package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeServerStateCleanerTest {

  @Test
  void cleanupServerStateClearsTransientAndPersistedStateForServer() {
    ServerTreeServerStateCleaner cleaner = new ServerTreeServerStateCleaner();
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    ServerTreeRuntimeState runtimeState = new ServerTreeRuntimeState(16, __ -> {});
    runtimeState.setServerConnectionState("libera", ConnectionState.CONNECTED);

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

    AtomicReference<String> clearedHoveredServer = new AtomicReference<>("");
    AtomicReference<String> clearedPrivateMessageServer = new AtomicReference<>("");

    ServerTreeServerStateCleaner.Context context =
        ServerTreeServerStateCleaner.context(
            interceptorStore,
            clearedHoveredServer::set,
            runtimeState::removeServer,
            channelStateStore::clearServer,
            clearedPrivateMessageServer::set,
            leaves,
            typingActivityNodes);

    cleaner.cleanupServerState(context, "libera");

    verify(interceptorStore).clearServerHits("libera");
    assertEquals("libera", clearedHoveredServer.get());
    assertEquals(ConnectionState.DISCONNECTED, runtimeState.connectionStateForServer("libera"));
    assertFalse(channelStateStore.channelSortModeByServer().containsKey("libera"));
    assertEquals("libera", clearedPrivateMessageServer.get());
    assertFalse(leaves.containsKey(channelRef));
    assertFalse(typingActivityNodes.contains(typingNode));
  }
}
