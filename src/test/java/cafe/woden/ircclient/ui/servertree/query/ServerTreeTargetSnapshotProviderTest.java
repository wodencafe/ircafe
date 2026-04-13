package cafe.woden.ircclient.ui.servertree.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeTargetSnapshotProviderTest {

  @Test
  void snapshotOpenChannelsForServerDeduplicatesAndSortsChannels() {
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    TargetRef alpha = new TargetRef("libera", "#Alpha");
    TargetRef alphaLower = new TargetRef("libera", "#alpha");
    TargetRef beta = new TargetRef("libera", "#beta");
    TargetRef pm = new TargetRef("libera", "alice");
    leaves.put(alpha, new DefaultMutableTreeNode("#Alpha"));
    leaves.put(alphaLower, new DefaultMutableTreeNode("#alpha"));
    leaves.put(beta, new DefaultMutableTreeNode("#beta"));
    leaves.put(pm, new DefaultMutableTreeNode("alice"));

    ServerTreeTargetSnapshotProvider provider = new ServerTreeTargetSnapshotProvider();
    ServerTreeTargetSnapshotProvider.Context context =
        ServerTreeTargetSnapshotProvider.context(leaves, root);

    assertEquals(
        List.of("#Alpha", "#beta"), provider.snapshotOpenChannelsForServer(context, "libera"));
    assertTrue(provider.snapshotOpenChannelsForServer(context, "oftc").isEmpty());
  }

  @Test
  void findTreeNodesByTargetReturnsAllMatchingNodesInTreeOrder() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode server = new DefaultMutableTreeNode("server");
    root.add(server);
    TargetRef target = new TargetRef("libera", "#ircafe");
    DefaultMutableTreeNode first =
        new DefaultMutableTreeNode(new ServerTreeNodeData(target, "#ircafe"));
    DefaultMutableTreeNode second =
        new DefaultMutableTreeNode(new ServerTreeNodeData(target, "#ircafe (detached)"));
    DefaultMutableTreeNode other =
        new DefaultMutableTreeNode(
            new ServerTreeNodeData(new TargetRef("libera", "#other"), "#other"));
    server.add(first);
    server.add(other);
    server.add(second);

    ServerTreeTargetSnapshotProvider provider = new ServerTreeTargetSnapshotProvider();
    ServerTreeTargetSnapshotProvider.Context context =
        ServerTreeTargetSnapshotProvider.context(new HashMap<>(), root);

    assertEquals(List.of(first, second), provider.findTreeNodesByTarget(context, target));
  }
}
