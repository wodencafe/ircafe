package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeNodeBadgeUpdaterTest {

  private final ServerTreeNodeBadgeUpdater updater = new ServerTreeNodeBadgeUpdater();

  @Test
  void refreshNotificationsCountUpdatesNotificationLeaf() {
    NotificationStore notificationStore = mock(NotificationStore.class);
    when(notificationStore.count("libera")).thenReturn(4);

    TargetRef notificationsRef = TargetRef.notifications("libera");
    DefaultMutableTreeNode notificationsNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(notificationsRef, "Notifications"));
    HashMap<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    leaves.put(notificationsRef, notificationsNode);

    DefaultMutableTreeNode[] changed = new DefaultMutableTreeNode[1];
    ServerTreeNodeBadgeUpdater.Context context =
        ServerTreeNodeBadgeUpdater.context(
            notificationStore, Set.of(), leaves, node -> changed[0] = node, __ -> null);

    updater.refreshNotificationsCount(context, "libera");

    assertSame(notificationsNode, changed[0]);
    ServerTreeNodeData data = (ServerTreeNodeData) notificationsNode.getUserObject();
    assertEquals(0, data.unread);
    assertEquals(4, data.highlightUnread);
  }

  @Test
  void refreshAutoConnectBadgesTouchesMatchingEphemeralServers() {
    HashMap<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    String[] changedServerId = new String[1];
    ServerTreeNodeBadgeUpdater.Context context =
        ServerTreeNodeBadgeUpdater.context(
            null,
            Set.of("soju:work:libera", "znc:home:freenode"),
            leaves,
            __ -> {},
            serverId -> {
              changedServerId[0] = serverId;
              return new DefaultMutableTreeNode(serverId);
            });

    updater.refreshAutoConnectBadges(context, ServerTreeBouncerBackends.SOJU);

    assertEquals("soju:work:libera", changedServerId[0]);
  }
}
