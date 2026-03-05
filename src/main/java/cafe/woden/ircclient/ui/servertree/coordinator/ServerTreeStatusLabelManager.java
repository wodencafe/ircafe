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

/** Maintains status-node labels for regular server and bouncer-control states. */
public final class ServerTreeStatusLabelManager {

  public interface Context {
    void nodeChanged(DefaultMutableTreeNode node);
  }

  private final String statusLabel;
  private final String bouncerControlLabel;
  private final Map<String, Set<String>> bouncerControlServerIdsByBackendId;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Context context;

  public ServerTreeStatusLabelManager(
      String statusLabel,
      String bouncerControlLabel,
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Set<String> genericBouncerControlServerIds,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Context context) {
    this(
        statusLabel,
        bouncerControlLabel,
        bouncerControlStateByBackend(
            sojuBouncerControlServerIds,
            zncBouncerControlServerIds,
            genericBouncerControlServerIds),
        leaves,
        context);
  }

  public ServerTreeStatusLabelManager(
      String statusLabel,
      String bouncerControlLabel,
      Map<String, Set<String>> bouncerControlServerIdsByBackendId,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Context context) {
    this.statusLabel = Objects.toString(statusLabel, "Server");
    this.bouncerControlLabel = Objects.toString(bouncerControlLabel, "Bouncer Control");
    this.bouncerControlServerIdsByBackendId =
        Objects.requireNonNull(
            bouncerControlServerIdsByBackendId, "bouncerControlServerIdsByBackendId");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.context = Objects.requireNonNull(context, "context");
  }

  public String statusLeafLabelForServer(String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return statusLabel;
    return isBouncerControlServer(id) ? bouncerControlLabel : statusLabel;
  }

  public boolean isBouncerControlServer(String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return false;
    for (Set<String> ids : bouncerControlServerIdsByBackendId.values()) {
      if (ids != null && ids.contains(id)) {
        return true;
      }
    }
    return false;
  }

  public void updateBouncerControlLabels(
      Set<String> nextSojuBouncerControl,
      Set<String> nextZncBouncerControl,
      Set<String> nextGenericBouncerControl) {
    Map<String, Set<String>> nextByBackendId = new HashMap<>();
    nextByBackendId.put(ServerTreeBouncerBackends.SOJU, nextSojuBouncerControl);
    nextByBackendId.put(ServerTreeBouncerBackends.ZNC, nextZncBouncerControl);
    nextByBackendId.put(ServerTreeBouncerBackends.GENERIC, nextGenericBouncerControl);
    updateBouncerControlLabels(nextByBackendId);
  }

  public void updateBouncerControlLabels(Map<String, Set<String>> nextBouncerControlByBackendId) {
    Set<String> prevUnion = unionOfControlServerIds();

    for (Set<String> ids : bouncerControlServerIdsByBackendId.values()) {
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
            bouncerControlServerIdsByBackendId.computeIfAbsent(
                backendId, ignored -> new HashSet<>());
        Set<String> nextIds = entry.getValue();
        if (nextIds == null || nextIds.isEmpty()) {
          continue;
        }
        target.addAll(nextIds);
      }
    }

    Set<String> nextUnion = unionOfControlServerIds();

    Set<String> all = new HashSet<>(prevUnion);
    all.addAll(nextUnion);

    for (String serverId : all) {
      boolean was = prevUnion.contains(serverId);
      boolean now = nextUnion.contains(serverId);
      if (was == now) continue;

      TargetRef statusRef = new TargetRef(serverId, "status");
      DefaultMutableTreeNode node = leaves.get(statusRef);
      if (node == null) continue;
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData old)) continue;

      String label = now ? bouncerControlLabel : statusLabel;
      if (Objects.equals(old.label, label)) continue;

      ServerTreeNodeData next = new ServerTreeNodeData(statusRef, label);
      next.unread = old.unread;
      next.highlightUnread = old.highlightUnread;
      next.detached = old.detached;
      next.detachedWarning = old.detachedWarning;
      next.copyTypingFrom(old);
      node.setUserObject(next);
      context.nodeChanged(node);
    }
  }

  private Set<String> unionOfControlServerIds() {
    Set<String> union = new HashSet<>();
    for (Set<String> ids : bouncerControlServerIdsByBackendId.values()) {
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
