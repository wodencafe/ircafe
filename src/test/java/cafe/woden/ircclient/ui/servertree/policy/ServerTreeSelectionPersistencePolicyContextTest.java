package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeSelectionPersistencePolicyContextTest {

  @Test
  void contextDelegatesSelectionPersistenceLookups() {
    TargetRef lastBroadcast = new TargetRef("libera", "status");
    TargetRef selectedTarget = new TargetRef("libera", "#ircafe");
    DefaultMutableTreeNode selectedNode = new DefaultMutableTreeNode("selected");

    ServerTreeSelectionPersistencePolicy.Context context =
        ServerTreeSelectionPersistencePolicy.context(
            () -> lastBroadcast,
            () -> selectedTarget,
            () -> selectedNode,
            node -> "libera",
            node -> node == selectedNode,
            node -> false,
            node -> TargetRef.channelList("libera", "libera"));

    assertSame(lastBroadcast, context.lastBroadcastSelection());
    assertSame(selectedTarget, context.selectedTargetRef());
    assertSame(selectedNode, context.selectedTreeNode());
    assertEquals("libera", context.owningServerIdForNode(selectedNode));
    assertTrue(context.isMonitorGroupNode(selectedNode));
    assertFalse(context.isInterceptorsGroupNode(selectedNode));
    assertEquals(
        TargetRef.channelList("libera", "libera"), context.syntheticTargetForNode(selectedNode));
  }
}
