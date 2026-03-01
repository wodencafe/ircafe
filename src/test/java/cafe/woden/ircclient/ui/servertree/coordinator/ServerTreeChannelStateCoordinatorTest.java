package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeChannelStateCoordinatorTest {

  @Test
  void customOrderLoadedBeforeIncrementalChannelsIsPreserved() {
    TestFixture fixture =
        newFixture(
            List.of("#gamma", "#alpha", "#beta"),
            Map.of(),
            List.of("#alpha", "#beta", "#gamma"));

    assertEquals(List.of("#gamma", "#alpha", "#beta"), fixture.treeChannelOrder());
  }

  @Test
  void pinnedAndUnpinnedCustomOrderLoadedBeforeIncrementalChannelsIsPreserved() {
    TestFixture fixture =
        newFixture(
            List.of("#p2", "#p1", "#u2", "#u1"),
            Map.of("#p1", true, "#p2", true),
            List.of("#u1", "#p1", "#u2", "#p2"));

    assertEquals(List.of("#p2", "#p1", "#u2", "#u1"), fixture.treeChannelOrder());
  }

  private static TestFixture newFixture(
      List<String> customOrder, Map<String, Boolean> pinnedByChannel, List<String> addOrder) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode channelListNode =
        new DefaultMutableTreeNode(
            new ServerTreeNodeData(TargetRef.channelList("libera"), "Channel List"));
    DefaultTreeModel model = new DefaultTreeModel(root);
    model.insertNodeInto(channelListNode, root, 0);

    ServerTreeChannelStateStore store = new ServerTreeChannelStateStore();
    store.channelSortModeByServer().put("libera", ServerTreeDockable.ChannelSortMode.CUSTOM);
    store.channelCustomOrderByServer().put("libera", new ArrayList<>(customOrder));
    store.channelAutoReattachByServer().put("libera", new HashMap<>());
    store.channelActivityRankByServer().put("libera", new HashMap<>());

    HashMap<String, Boolean> pinned = new HashMap<>();
    for (Map.Entry<String, Boolean> entry : pinnedByChannel.entrySet()) {
      if (!Boolean.TRUE.equals(entry.getValue())) continue;
      String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
      if (!key.isEmpty()) pinned.put(key, true);
    }
    store.channelPinnedByServer().put("libera", pinned);

    ServerTreeChannelStateCoordinator coordinator =
        new ServerTreeChannelStateCoordinator(
            null, store, model, new TestContext("libera", channelListNode));

    TestFixture fixture = new TestFixture(model, channelListNode, coordinator);
    for (String channel : addOrder) {
      fixture.addChannel(channel);
      coordinator.sortChannelsUnderChannelList("libera");
    }
    return fixture;
  }

  private static final class TestFixture {
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode channelListNode;
    private final ServerTreeChannelStateCoordinator coordinator;

    private TestFixture(
        DefaultTreeModel model,
        DefaultMutableTreeNode channelListNode,
        ServerTreeChannelStateCoordinator coordinator) {
      this.model = model;
      this.channelListNode = channelListNode;
      this.coordinator = coordinator;
    }

    private void addChannel(String channel) {
      TargetRef ref = new TargetRef("libera", channel);
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(new ServerTreeNodeData(ref, channel));
      model.insertNodeInto(node, channelListNode, channelListNode.getChildCount());
      coordinator.ensureChannelKnownInConfig(ref);
    }

    private List<String> treeChannelOrder() {
      ArrayList<String> out = new ArrayList<>();
      for (int i = 0; i < channelListNode.getChildCount(); i++) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
        Object userObject = child.getUserObject();
        if (!(userObject instanceof ServerTreeNodeData nodeData)) continue;
        if (nodeData.ref == null || !nodeData.ref.isChannel()) continue;
        out.add(nodeData.ref.target());
      }
      return out;
    }
  }

  private static final class TestContext implements ServerTreeChannelStateCoordinator.Context {
    private final String serverId;
    private final DefaultMutableTreeNode channelListNode;

    private TestContext(String serverId, DefaultMutableTreeNode channelListNode) {
      this.serverId = serverId;
      this.channelListNode = channelListNode;
    }

    @Override
    public String normalizeServerId(String rawServerId) {
      return serverId.equals(rawServerId) ? serverId : "";
    }

    @Override
    public DefaultMutableTreeNode channelListNode(String rawServerId) {
      return serverId.equals(rawServerId) ? channelListNode : null;
    }

    @Override
    public Set<TreePath> snapshotExpandedTreePaths() {
      return Set.of();
    }

    @Override
    public void restoreExpandedTreePaths(Set<TreePath> expanded) {}

    @Override
    public void emitManagedChannelsChanged(String serverId) {}
  }
}
