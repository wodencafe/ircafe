package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Updates dynamic node badges/labels driven by notifications and auto-connect state. */
public final class ServerTreeNodeBadgeUpdater {

  public interface Context {
    void nodeChanged(DefaultMutableTreeNode node);

    void nodeChangedForServer(String serverId);
  }

  public static Context context(
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Function<String, DefaultMutableTreeNode> serverNodeById) {
    Objects.requireNonNull(nodeChanged, "nodeChanged");
    Objects.requireNonNull(serverNodeById, "serverNodeById");
    return new Context() {
      @Override
      public void nodeChanged(DefaultMutableTreeNode node) {
        nodeChanged.accept(node);
      }

      @Override
      public void nodeChangedForServer(String serverId) {
        DefaultMutableTreeNode node = serverNodeById.apply(serverId);
        if (node != null) {
          nodeChanged.accept(node);
        }
      }
    };
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
    refreshAutoConnectBadges(ServerTreeBouncerBackends.SOJU);
  }

  public void refreshZncAutoConnectBadges() {
    refreshAutoConnectBadges(ServerTreeBouncerBackends.ZNC);
  }

  public void refreshGenericAutoConnectBadges() {
    refreshAutoConnectBadges(ServerTreeBouncerBackends.GENERIC);
  }

  public void refreshAutoConnectBadges(String backendId) {
    String prefix = normalize(ServerTreeBouncerBackends.prefixFor(backendId));
    if (prefix.isEmpty()) return;

    for (String id : ephemeralServerIds) {
      if (!Objects.toString(id, "").startsWith(prefix)) continue;
      context.nodeChangedForServer(id);
    }
  }

  private static String normalize(String value) {
    return ServerTreeConventions.normalize(value);
  }
}
