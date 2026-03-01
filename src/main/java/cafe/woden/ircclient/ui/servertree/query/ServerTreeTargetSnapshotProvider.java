package cafe.woden.ircclient.ui.servertree.query;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;

/** Provides tree and leaf snapshots for target-based queries. */
public final class ServerTreeTargetSnapshotProvider {

  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final DefaultMutableTreeNode root;

  public ServerTreeTargetSnapshotProvider(
      Map<TargetRef, DefaultMutableTreeNode> leaves, DefaultMutableTreeNode root) {
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.root = Objects.requireNonNull(root, "root");
  }

  public List<String> snapshotOpenChannelsForServer(String serverId) {
    LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
    for (TargetRef ref : leaves.keySet()) {
      if (ref == null) continue;
      if (!Objects.equals(serverId, ref.serverId())) continue;
      if (!ref.isChannel()) continue;
      String target = Objects.toString(ref.target(), "").trim();
      if (target.isEmpty()) continue;
      String key = target.toLowerCase(java.util.Locale.ROOT);
      byKey.putIfAbsent(key, target);
    }
    if (byKey.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(byKey.values());
    out.sort(String.CASE_INSENSITIVE_ORDER);
    return List.copyOf(out);
  }

  public List<DefaultMutableTreeNode> findTreeNodesByTarget(TargetRef ref) {
    ArrayList<DefaultMutableTreeNode> out = new ArrayList<>();
    if (ref == null) return out;

    Enumeration<?> en = root.depthFirstEnumeration();
    while (en.hasMoreElements()) {
      Object o = en.nextElement();
      if (!(o instanceof DefaultMutableTreeNode node)) continue;
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData nodeData)) continue;
      if (nodeData.ref == null) continue;
      if (!ref.equals(nodeData.ref)) continue;
      out.add(node);
    }
    return out;
  }
}
