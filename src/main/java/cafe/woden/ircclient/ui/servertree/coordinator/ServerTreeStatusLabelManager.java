package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Maintains status-node labels for regular server and bouncer-control states. */
@Component
public final class ServerTreeStatusLabelManager {

  public interface Context {
    String statusLabel();

    String bouncerControlLabel();

    Map<String, Set<String>> bouncerControlServerIdsByBackendId();

    Map<TargetRef, DefaultMutableTreeNode> leaves();

    void nodeChanged(DefaultMutableTreeNode node);
  }

  private record DefaultContext(
      String statusLabel,
      String bouncerControlLabel,
      Map<String, Set<String>> bouncerControlServerIdsByBackendId,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      NodeChanged nodeChanged)
      implements Context {

    @Override
    public void nodeChanged(DefaultMutableTreeNode node) {
      nodeChanged.nodeChanged(node);
    }
  }

  @FunctionalInterface
  public interface NodeChanged {
    void nodeChanged(DefaultMutableTreeNode node);
  }

  public static Context context(
      String statusLabel,
      String bouncerControlLabel,
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Set<String> genericBouncerControlServerIds,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      NodeChanged nodeChanged) {
    return context(
        statusLabel,
        bouncerControlLabel,
        bouncerControlStateByBackend(
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            genericBouncerControlServerIds),
        leaves,
        nodeChanged);
  }

  public static Context context(
      String statusLabel,
      String bouncerControlLabel,
      Map<String, Set<String>> bouncerControlServerIdsByBackendId,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      NodeChanged nodeChanged) {
    return new DefaultContext(
        Objects.toString(statusLabel, "Server"),
        Objects.toString(bouncerControlLabel, "Bouncer Control"),
        Objects.requireNonNull(
            bouncerControlServerIdsByBackendId, "bouncerControlServerIdsByBackendId"),
        Objects.requireNonNull(leaves, "leaves"),
        Objects.requireNonNull(nodeChanged, "nodeChanged"));
  }

  public String statusLeafLabelForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String id = normalize(serverId);
    if (id.isEmpty()) return in.statusLabel();
    return isBouncerControlServer(in, id) ? in.bouncerControlLabel() : in.statusLabel();
  }

  public boolean isBouncerControlServer(Context context, String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return false;
    for (Set<String> ids : context.bouncerControlServerIdsByBackendId().values()) {
      if (ids != null && ids.contains(id)) {
        return true;
      }
    }
    return false;
  }

  public void updateBouncerControlLabels(
      Context context,
      Set<String> nextSojuBouncerControl,
      Set<String> nextZncBouncerControl,
      Set<String> nextGenericBouncerControl) {
    Map<String, Set<String>> nextByBackendId = new HashMap<>();
    nextByBackendId.put(ServerTreeBouncerBackends.SOJU, nextSojuBouncerControl);
    nextByBackendId.put(ServerTreeBouncerBackends.ZNC, nextZncBouncerControl);
    nextByBackendId.put(ServerTreeBouncerBackends.GENERIC, nextGenericBouncerControl);
    updateBouncerControlLabels(context, nextByBackendId);
  }

  public void updateBouncerControlLabels(
      Context context, Map<String, Set<String>> nextBouncerControlByBackendId) {
    Context in = Objects.requireNonNull(context, "context");
    Set<String> prevUnion = unionOfControlServerIds(in);

    for (Set<String> ids : in.bouncerControlServerIdsByBackendId().values()) {
      if (ids != null) {
        ids.clear();
      }
    }
    if (nextBouncerControlByBackendId != null) {
      for (Map.Entry<String, Set<String>> entry : nextBouncerControlByBackendId.entrySet()) {
        String backendId = normalize(entry.getKey());
        if (backendId.isEmpty()) {
          continue;
        }
        Set<String> target =
            in.bouncerControlServerIdsByBackendId()
                .computeIfAbsent(backendId, ignored -> new HashSet<>());
        Set<String> nextIds = entry.getValue();
        if (nextIds == null || nextIds.isEmpty()) {
          continue;
        }
        target.addAll(nextIds);
      }
    }

    Set<String> nextUnion = unionOfControlServerIds(in);

    Set<String> all = new HashSet<>(prevUnion);
    all.addAll(nextUnion);

    for (String serverId : all) {
      boolean was = prevUnion.contains(serverId);
      boolean now = nextUnion.contains(serverId);
      if (was == now) continue;

      TargetRef statusRef = new TargetRef(serverId, "status");
      DefaultMutableTreeNode node = in.leaves().get(statusRef);
      if (node == null) continue;
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData old)) continue;

      String label = now ? in.bouncerControlLabel() : in.statusLabel();
      if (Objects.equals(old.label, label)) continue;

      ServerTreeNodeData next = new ServerTreeNodeData(statusRef, label);
      next.unread = old.unread;
      next.highlightUnread = old.highlightUnread;
      next.detached = old.detached;
      next.detachedWarning = old.detachedWarning;
      next.copyTypingFrom(old);
      node.setUserObject(next);
      in.nodeChanged(node);
    }
  }

  private Set<String> unionOfControlServerIds(Context context) {
    Set<String> union = new HashSet<>();
    for (Set<String> ids : context.bouncerControlServerIdsByBackendId().values()) {
      if (ids == null || ids.isEmpty()) {
        continue;
      }
      union.addAll(ids);
    }
    return union;
  }

  private static Map<String, Set<String>> bouncerControlStateByBackend(
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Set<String> genericBouncerControlServerIds) {
    Map<String, Set<String>> state = new HashMap<>();
    state.put(
        ServerTreeBouncerBackends.SOJU,
        Objects.requireNonNull(sojuBouncerControlServerIds, "sojuBouncerControlServerIds"));
    state.put(
        ServerTreeBouncerBackends.ZNC,
        Objects.requireNonNull(zncBouncerControlServerIds, "zncBouncerControlServerIds"));
    state.put(
        ServerTreeBouncerBackends.GENERIC,
        Objects.requireNonNull(genericBouncerControlServerIds, "genericBouncerControlServerIds"));
    return state;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
