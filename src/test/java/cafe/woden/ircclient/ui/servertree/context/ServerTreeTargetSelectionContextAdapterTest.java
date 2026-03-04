package cafe.woden.ircclient.ui.servertree.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeTargetSelectionContextAdapterTest {

  @Test
  void delegatesSelectionOperationsAndChecksGroupNodeParentage() {
    String serverId = "libera";
    ServerNodes nodes = serverNodes(serverId);
    nodes.serverNode.add(nodes.monitorNode);
    nodes.otherNode.add(nodes.interceptorsNode);

    AtomicReference<TargetRef> ensured = new AtomicReference<>();
    AtomicReference<DefaultMutableTreeNode> selected = new AtomicReference<>();
    TargetRef channel = new TargetRef(serverId, "#ircafe");
    DefaultMutableTreeNode channelNode = new DefaultMutableTreeNode("#ircafe");

    ServerTreeTargetSelectionContextAdapter adapter =
        new ServerTreeTargetSelectionContextAdapter(
            ensured::set,
            sid -> nodes.monitorNode,
            sid -> nodes.interceptorsNode,
            sid -> nodes,
            ref -> channelNode,
            selected::set);

    adapter.ensureNode(channel);
    adapter.selectNode(channelNode);

    assertSame(channel, ensured.get());
    assertSame(channelNode, selected.get());
    assertSame(nodes.monitorNode, adapter.monitorGroupNode(serverId));
    assertSame(nodes.interceptorsNode, adapter.interceptorsGroupNode(serverId));
    assertSame(channelNode, adapter.leafNode(channel));

    assertTrue(adapter.isGroupNodeSelectable(serverId, nodes.monitorNode));
    assertTrue(adapter.isGroupNodeSelectable(serverId, nodes.interceptorsNode));
    assertFalse(adapter.isGroupNodeSelectable(serverId, new DefaultMutableTreeNode("orphan")));
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
