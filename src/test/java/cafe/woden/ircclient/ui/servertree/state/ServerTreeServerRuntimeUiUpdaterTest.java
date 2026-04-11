package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeServerRuntimeUiUpdaterTest {

  @Test
  void setServerDesiredOnlineRefreshesNodeAndRepaintsHoveredServer() {
    ServerTreeServerRuntimeUiUpdater updater = new ServerTreeServerRuntimeUiUpdater();
    ServerTreeRuntimeState runtimeState = new ServerTreeRuntimeState(16, __ -> {});
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("libera");
    Map<String, ServerNodes> servers = new HashMap<>();
    servers.put(
        "libera",
        new ServerNodes(
            serverNode, null, null, null, null, null, null, null, null, null, null, null));
    AtomicReference<DefaultMutableTreeNode> changedNode = new AtomicReference<>();
    AtomicBoolean repainted = new AtomicBoolean(false);

    ServerTreeServerRuntimeUiUpdater.Context context =
        ServerTreeServerRuntimeUiUpdater.context(
            runtimeState, servers, changedNode::set, "libera"::equals, () -> repainted.set(true));

    updater.setServerDesiredOnline(context, "libera", true);

    assertTrue(runtimeState.desiredOnlineForServer("libera"));
    assertSame(serverNode, changedNode.get());
    assertTrue(repainted.get());
  }

  @Test
  void setServerConnectionDiagnosticsRefreshesNodeAndRepaintsTree() {
    ServerTreeServerRuntimeUiUpdater updater = new ServerTreeServerRuntimeUiUpdater();
    ServerTreeRuntimeState runtimeState = new ServerTreeRuntimeState(16, __ -> {});
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("libera");
    Map<String, ServerNodes> servers = new HashMap<>();
    servers.put(
        "libera",
        new ServerNodes(
            serverNode, null, null, null, null, null, null, null, null, null, null, null));
    AtomicReference<DefaultMutableTreeNode> changedNode = new AtomicReference<>();
    AtomicBoolean repainted = new AtomicBoolean(false);

    ServerTreeServerRuntimeUiUpdater.Context context =
        ServerTreeServerRuntimeUiUpdater.context(
            runtimeState, servers, changedNode::set, __ -> false, () -> repainted.set(true));

    updater.setServerConnectionDiagnostics(context, "libera", "timeout", 123L);

    assertEquals(
        " Last error: timeout. Next retry: imminent.",
        runtimeState.connectionDiagnosticsTipForServer("libera"));
    assertSame(serverNode, changedNode.get());
    assertTrue(repainted.get());
  }
}
