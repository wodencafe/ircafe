package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;

/** Updates dynamic node badges/labels driven by notifications and auto-connect state. */
public final class ServerTreeNodeBadgeUpdater {

  public interface Context {
    void nodeChanged(DefaultMutableTreeNode node);

    void nodeChangedForServer(String serverId);
  }

  private final NotificationStore notificationStore;
  private final Set<String> ephemeralServerIds;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Context context;

  public ServerTreeNodeBadgeUpdater(
      NotificationStore notificationStore,
      Set<String> ephemeralServerIds,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Context context) {
    this.notificationStore = notificationStore;
    this.ephemeralServerIds = Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void refreshNotificationsCount(String serverId) {
    if (notificationStore == null) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    TargetRef ref = TargetRef.notifications(sid);
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;

    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return;

    int count = notificationStore.count(sid);
    if (nodeData.unread == 0 && nodeData.highlightUnread == count) return;
    nodeData.unread = 0;
    nodeData.highlightUnread = count;
    context.nodeChanged(node);
  }

  public void refreshSojuAutoConnectBadges() {
    refreshAutoConnectBadges("soju:");
  }

  public void refreshZncAutoConnectBadges() {
    refreshAutoConnectBadges("znc:");
  }

  private void refreshAutoConnectBadges(String serverPrefix) {
    String prefix = normalize(serverPrefix);
    if (prefix.isEmpty()) return;

    for (String id : ephemeralServerIds) {
      if (!Objects.toString(id, "").startsWith(prefix)) continue;
      context.nodeChangedForServer(id);
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
