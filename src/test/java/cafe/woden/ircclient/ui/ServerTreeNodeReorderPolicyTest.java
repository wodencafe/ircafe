package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.util.TreeNodeReorderPolicy;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeNodeReorderPolicyTest {

  @Test
  void doesNotMoveChannelBelowZncNetworksGroup() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode ch1 = channelNode("test", "#alpha");
    DefaultMutableTreeNode ch2 = channelNode("test", "#beta");
    server.add(statusNode("test"));
    server.add(notificationsNode("test"));
    server.add(ch1);
    server.add(ch2);
    server.add(new DefaultMutableTreeNode("ZNC Networks"));
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(ch2, +1);

    assertNull(plan, "last movable channel should not move below reserved group nodes");
  }

  @Test
  void doesNotMoveChannelBelowSojuNetworksGroup() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode ch1 = channelNode("test", "#alpha");
    DefaultMutableTreeNode ch2 = channelNode("test", "#beta");
    server.add(statusNode("test"));
    server.add(notificationsNode("test"));
    server.add(ch1);
    server.add(ch2);
    server.add(new DefaultMutableTreeNode("Soju Networks"));
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(ch2, +1);

    assertNull(plan, "last movable channel should not move below reserved group nodes");
  }

  @Test
  void allowsMovingChannelWithinChannelRegion() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode ch1 = channelNode("test", "#alpha");
    DefaultMutableTreeNode ch2 = channelNode("test", "#beta");
    server.add(statusNode("test"));
    server.add(notificationsNode("test"));
    server.add(ch1);
    server.add(ch2);
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(ch1, +1);

    assertNotNull(plan);
    assertEquals(2, plan.fromIndex());
    assertEquals(3, plan.toIndex());
  }

  private static ServerTreeNodeReorderPolicy policyForServerLabel(String label) {
    return new ServerTreeNodeReorderPolicy(node -> node != null && label.equals(node.getUserObject()));
  }

  private static DefaultMutableTreeNode serverNode(String id) {
    return new DefaultMutableTreeNode(id);
  }

  private static DefaultMutableTreeNode statusNode(String serverId) {
    return leafNode(new TargetRef(serverId, "status"), "status");
  }

  private static DefaultMutableTreeNode notificationsNode(String serverId) {
    return leafNode(TargetRef.notifications(serverId), "Notifications");
  }

  private static DefaultMutableTreeNode channelNode(String serverId, String channel) {
    return leafNode(new TargetRef(serverId, channel), channel);
  }

  private static DefaultMutableTreeNode leafNode(TargetRef ref, String label) {
    return new DefaultMutableTreeNode(new ServerTreeDockable.NodeData(ref, label));
  }
}
