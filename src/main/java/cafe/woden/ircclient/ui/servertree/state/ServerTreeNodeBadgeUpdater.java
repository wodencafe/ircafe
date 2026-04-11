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
import org.springframework.stereotype.Component;

/** Updates dynamic node badges/labels driven by notifications and auto-connect state. */
@Component
public final class ServerTreeNodeBadgeUpdater {

  public interface Context {
    NotificationStore notificationStore();

    Set<String> ephemeralServerIds();

    Map<TargetRef, DefaultMutableTreeNode> leaves();

    void nodeChanged(DefaultMutableTreeNode node);

    void nodeChangedForServer(String serverId);
  }

  private record DefaultContext(
      NotificationStore notificationStore,
      Set<String> ephemeralServerIds,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Function<String, DefaultMutableTreeNode> serverNodeById)
      implements Context {

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
  }

  public static Context context(
      NotificationStore notificationStore,
      Set<String> ephemeralServerIds,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Function<String, DefaultMutableTreeNode> serverNodeById) {
    Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(nodeChanged, "nodeChanged");
    Objects.requireNonNull(serverNodeById, "serverNodeById");
    return new DefaultContext(
        notificationStore, ephemeralServerIds, leaves, nodeChanged, serverNodeById);
  }

  public void refreshNotificationsCount(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    if (in.notificationStore() == null) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    TargetRef ref = TargetRef.notifications(sid);
    DefaultMutableTreeNode node = in.leaves().get(ref);
    if (node == null) return;

    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return;

    int count = in.notificationStore().count(sid);
    if (nodeData.unread == 0 && nodeData.highlightUnread == count) return;
    nodeData.unread = 0;
    nodeData.highlightUnread = count;
    in.nodeChanged(node);
  }

  public void refreshSojuAutoConnectBadges(Context context) {
    refreshAutoConnectBadges(context, ServerTreeBouncerBackends.SOJU);
  }

  public void refreshZncAutoConnectBadges(Context context) {
    refreshAutoConnectBadges(context, ServerTreeBouncerBackends.ZNC);
  }

  public void refreshGenericAutoConnectBadges(Context context) {
    refreshAutoConnectBadges(context, ServerTreeBouncerBackends.GENERIC);
  }

  public void refreshAutoConnectBadges(Context context, String backendId) {
    Context in = Objects.requireNonNull(context, "context");
    String prefix = normalize(ServerTreeBouncerBackends.prefixFor(backendId));
    if (prefix.isEmpty()) return;

    for (String id : in.ephemeralServerIds()) {
      if (!Objects.toString(id, "").startsWith(prefix)) continue;
      in.nodeChangedForServer(id);
    }
  }

  private static String normalize(String value) {
    return ServerTreeConventions.normalize(value);
  }
}
