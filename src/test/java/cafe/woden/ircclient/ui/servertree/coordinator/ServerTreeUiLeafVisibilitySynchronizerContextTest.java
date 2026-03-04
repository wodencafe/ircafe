package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeUiLeafVisibilitySynchronizerContextTest {

  @Test
  void contextDelegatesUiLeafVisibilityOperations() {
    TargetRef selected = new TargetRef("libera", "#ircafe");
    DefaultMutableTreeNode selectedNode = new DefaultMutableTreeNode("selected");
    AtomicReference<String> syncedServer = new AtomicReference<>();
    List<String> fallbackSelections = new ArrayList<>();
    AtomicBoolean showDcc = new AtomicBoolean(true);

    ServerTreeUiLeafVisibilitySynchronizer.Context context =
        ServerTreeUiLeafVisibilitySynchronizer.context(
            () -> selected,
            () -> selectedNode,
            node -> node == selectedNode,
            node -> false,
            node -> "libera",
            () -> List.of("libera"),
            syncedServer::set,
            serverId -> "libera".equals(serverId),
            serverId -> false,
            serverId -> true,
            serverId -> true,
            serverId -> true,
            showDcc::get,
            fallbackSelections::add);

    assertSame(selected, context.selectedTargetRef());
    assertSame(selectedNode, context.selectedTreeNode());
    assertTrue(context.isMonitorGroupNode(selectedNode));
    assertFalse(context.isInterceptorsGroupNode(selectedNode));
    assertEquals("libera", context.owningServerIdForNode(selectedNode));
    assertEquals(List.of("libera"), context.serverIdsSnapshot());

    context.syncServerUiLeafVisibility("libera");
    assertEquals("libera", syncedServer.get());

    assertTrue(context.statusVisible("libera"));
    assertFalse(context.notificationsVisible("libera"));
    assertTrue(context.logViewerVisible("libera"));
    assertTrue(context.monitorVisible("libera"));
    assertTrue(context.interceptorsVisible("libera"));
    assertTrue(context.showDccTransfersNodes());

    context.selectBestFallbackForServer("libera");
    assertEquals(List.of("libera"), fallbackSelections);
  }
}
