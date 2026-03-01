package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
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
  private final Set<String> sojuBouncerControlServerIds;
  private final Set<String> zncBouncerControlServerIds;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Context context;

  public ServerTreeStatusLabelManager(
      String statusLabel,
      String bouncerControlLabel,
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Context context) {
    this.statusLabel = Objects.toString(statusLabel, "Server");
    this.bouncerControlLabel = Objects.toString(bouncerControlLabel, "Bouncer Control");
    this.sojuBouncerControlServerIds =
        Objects.requireNonNull(sojuBouncerControlServerIds, "sojuBouncerControlServerIds");
    this.zncBouncerControlServerIds =
        Objects.requireNonNull(zncBouncerControlServerIds, "zncBouncerControlServerIds");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.context = Objects.requireNonNull(context, "context");
  }

  public String statusLeafLabelForServer(String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return statusLabel;
    return (sojuBouncerControlServerIds.contains(id) || zncBouncerControlServerIds.contains(id))
        ? bouncerControlLabel
        : statusLabel;
  }

  public void updateBouncerControlLabels(
      Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl) {
    Set<String> nextSoju = nextSojuBouncerControl == null ? Set.of() : nextSojuBouncerControl;
    Set<String> nextZnc = nextZncBouncerControl == null ? Set.of() : nextZncBouncerControl;

    Set<String> prevUnion = new HashSet<>(sojuBouncerControlServerIds);
    prevUnion.addAll(zncBouncerControlServerIds);

    sojuBouncerControlServerIds.clear();
    sojuBouncerControlServerIds.addAll(nextSoju);
    zncBouncerControlServerIds.clear();
    zncBouncerControlServerIds.addAll(nextZnc);

    Set<String> nextUnion = new HashSet<>(nextSoju);
    nextUnion.addAll(nextZnc);

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

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
