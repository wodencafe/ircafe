package cafe.woden.ircclient.ui.servertree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeNodeReorderPolicy;
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

  @Test
  void doesNotMoveChannelAboveInterceptorsGroup() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode ch1 = channelNode("test", "#alpha");
    server.add(statusNode("test"));
    server.add(notificationsNode("test"));
    server.add(interceptorsGroupNode());
    server.add(ch1);
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(ch1, -1);

    assertNull(plan, "channel should not move above the Interceptors group node");
  }

  @Test
  void doesNotMoveChannelAboveMonitorGroup() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode ch1 = channelNode("test", "#alpha");
    server.add(statusNode("test"));
    server.add(notificationsNode("test"));
    server.add(monitorGroupNode());
    server.add(ch1);
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(ch1, -1);

    assertNull(plan, "channel should not move above the Monitor group node");
  }

  @Test
  void allowsMovingBuiltInNodeWithinOtherGroup() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode other = otherGroupNode();
    DefaultMutableTreeNode status = statusNode("test");
    DefaultMutableTreeNode notifications = notificationsNode("test");
    other.add(status);
    other.add(notifications);
    server.add(channelListNode("test"));
    server.add(other);
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(status, +1);

    assertNotNull(plan);
    assertEquals(0, plan.fromIndex());
    assertEquals(1, plan.toIndex());
  }

  @Test
  void doesNotMoveServerLevelBuiltInBelowOtherGroup() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode status = statusNode("test");
    DefaultMutableTreeNode notifications = notificationsNode("test");
    server.add(channelListNode("test"));
    server.add(status);
    server.add(notifications);
    server.add(otherGroupNode());
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(notifications, +1);

    assertNull(plan);
  }

  @Test
  void doesNotMoveServerLevelBuiltInAboveChannelList() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode status = statusNode("test");
    server.add(channelListNode("test"));
    server.add(status);
    server.add(otherGroupNode());
    server.add(new DefaultMutableTreeNode("Private messages"));

    ServerTreeNodeReorderPolicy policy = policyForServerLabel("test");
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(status, -1);

    assertNull(plan);
  }

  @Test
  void pinnedChannelCannotMoveBelowUnpinnedInChannelList() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode channelList = channelListNode("test");
    DefaultMutableTreeNode pinned = channelNode("test", "#pinned");
    DefaultMutableTreeNode normal = channelNode("test", "#normal");
    channelList.add(pinned);
    channelList.add(normal);
    server.add(channelList);

    ServerTreeNodeReorderPolicy policy =
        policyForServerLabel("test", ref -> "#pinned".equals(ref.target()));
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(pinned, +1);

    assertNull(plan);
  }

  @Test
  void unpinnedChannelCannotMoveAbovePinnedInChannelList() {
    DefaultMutableTreeNode server = serverNode("test");
    DefaultMutableTreeNode channelList = channelListNode("test");
    DefaultMutableTreeNode pinned = channelNode("test", "#pinned");
    DefaultMutableTreeNode normal = channelNode("test", "#normal");
    channelList.add(pinned);
    channelList.add(normal);
    server.add(channelList);

    ServerTreeNodeReorderPolicy policy =
        policyForServerLabel("test", ref -> "#pinned".equals(ref.target()));
    TreeNodeReorderPolicy.MovePlan plan = policy.planMove(normal, -1);

    assertNull(plan);
  }

  private static ServerTreeNodeReorderPolicy policyForServerLabel(String label) {
    return policyForServerLabel(label, __ -> false);
  }

  private static ServerTreeNodeReorderPolicy policyForServerLabel(
      String label, java.util.function.Predicate<TargetRef> isPinned) {
    return new ServerTreeNodeReorderPolicy(
        node -> node != null && label.equals(node.getUserObject()),
        node -> {
          if (node == null) return false;
          Object uo = node.getUserObject();
          if (!(uo instanceof ServerTreeNodeData nd)) return false;
          return nd.ref != null && nd.ref.isChannelList();
        },
        isPinned,
        node -> {
          if (node == null) return null;
          Object uo = node.getUserObject();
          if (uo instanceof ServerTreeNodeData nd) return nd.ref;
          return null;
        },
        node -> {
          if (node == null) return "";
          Object uo = node.getUserObject();
          if (uo instanceof ServerTreeNodeData nd) return nd.label;
          if (uo instanceof String s) return s;
          return "";
        });
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

  private static DefaultMutableTreeNode channelListNode(String serverId) {
    return leafNode(TargetRef.channelList(serverId), "Channel List");
  }

  private static DefaultMutableTreeNode interceptorsGroupNode() {
    return new DefaultMutableTreeNode(new ServerTreeNodeData(null, "Interceptors"));
  }

  private static DefaultMutableTreeNode monitorGroupNode() {
    return new DefaultMutableTreeNode(new ServerTreeNodeData(null, "Monitor"));
  }

  private static DefaultMutableTreeNode otherGroupNode() {
    return new DefaultMutableTreeNode(new ServerTreeNodeData(null, "Other"));
  }

  private static DefaultMutableTreeNode leafNode(TargetRef ref, String label) {
    return new DefaultMutableTreeNode(new ServerTreeNodeData(ref, label));
  }
}
