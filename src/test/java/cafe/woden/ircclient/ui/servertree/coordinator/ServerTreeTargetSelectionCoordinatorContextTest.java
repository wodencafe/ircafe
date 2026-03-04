package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeTargetSelectionCoordinatorContextTest {

  @Test
  void contextDelegatesSelectionOperations() {
    String serverId = "libera";
    TargetRef channel = new TargetRef(serverId, "#ircafe");
    DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("Monitor");
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("Interceptors");
    DefaultMutableTreeNode channelNode = new DefaultMutableTreeNode("#ircafe");

    AtomicReference<TargetRef> ensured = new AtomicReference<>();
    AtomicReference<DefaultMutableTreeNode> selected = new AtomicReference<>();

    ServerTreeTargetSelectionCoordinator.Context context =
        ServerTreeTargetSelectionCoordinator.context(
            ensured::set,
            sid -> monitorNode,
            sid -> interceptorsNode,
            (sid, node) -> node == monitorNode || node == interceptorsNode,
            ref -> channelNode,
            selected::set);

    context.ensureNode(channel);
    context.selectNode(channelNode);

    assertSame(channel, ensured.get());
    assertSame(channelNode, selected.get());
    assertSame(monitorNode, context.monitorGroupNode(serverId));
    assertSame(interceptorsNode, context.interceptorsGroupNode(serverId));
    assertSame(channelNode, context.leafNode(channel));
    assertTrue(context.isGroupNodeSelectable(serverId, monitorNode));
    assertTrue(context.isGroupNodeSelectable(serverId, interceptorsNode));
    assertFalse(context.isGroupNodeSelectable(serverId, new DefaultMutableTreeNode("orphan")));
  }
}
