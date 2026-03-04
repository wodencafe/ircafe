package cafe.woden.ircclient.ui.servertree.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeSelectionPersistenceContextAdapterTest {

  @Test
  void delegatesSelectionPersistenceContextLookups() {
    TargetRef lastBroadcast = new TargetRef("libera", "status");
    TargetRef selectedTarget = new TargetRef("libera", "#ircafe");
    DefaultMutableTreeNode selectedNode = new DefaultMutableTreeNode("selected");
    ServerTreeSelectionPersistenceContextAdapter adapter =
        new ServerTreeSelectionPersistenceContextAdapter(
            () -> lastBroadcast,
            () -> selectedTarget,
            () -> selectedNode,
            node -> "libera",
            node -> node == selectedNode,
            node -> false);

    assertSame(lastBroadcast, adapter.lastBroadcastSelection());
    assertSame(selectedTarget, adapter.selectedTargetRef());
    assertSame(selectedNode, adapter.selectedTreeNode());
    assertEquals("libera", adapter.owningServerIdForNode(selectedNode));
    assertTrue(adapter.isMonitorGroupNode(selectedNode));
    assertFalse(adapter.isInterceptorsGroupNode(selectedNode));
  }
}
