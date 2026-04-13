package cafe.woden.ircclient.ui.servertree.query;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Provides tree and leaf snapshots for target-based queries. */
@Component
public final class ServerTreeTargetSnapshotProvider {

  public interface Context {
    Iterable<TargetRef> leafRefs();

    DefaultMutableTreeNode rootNode();
  }

  public static Context context(
      Map<TargetRef, DefaultMutableTreeNode> leaves, DefaultMutableTreeNode root) {
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(root, "root");
    return new Context() {
      @Override
      public Iterable<TargetRef> leafRefs() {
        return leaves.keySet();
      }

      @Override
      public DefaultMutableTreeNode rootNode() {
        return root;
      }
    };
  }

  public List<String> snapshotOpenChannelsForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
    for (TargetRef ref : in.leafRefs()) {
      if (ref == null) continue;
      if (!Objects.equals(serverId, ref.serverId())) continue;
      if (!ref.isChannel()) continue;
      String target = Objects.toString(ref.target(), "").trim();
      if (target.isEmpty()) continue;
      String key = target.toLowerCase(Locale.ROOT);
      byKey.putIfAbsent(key, target);
    }
    if (byKey.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(byKey.values());
    out.sort(String.CASE_INSENSITIVE_ORDER);
    return List.copyOf(out);
  }

  public List<DefaultMutableTreeNode> findTreeNodesByTarget(Context context, TargetRef ref) {
    Context in = Objects.requireNonNull(context, "context");
    ArrayList<DefaultMutableTreeNode> out = new ArrayList<>();
    if (ref == null) return out;

    Enumeration<?> en = in.rootNode().depthFirstEnumeration();
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
