package cafe.woden.ircclient.ui.servertree.query;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Resolves server-scoped tree nodes and first-server selection candidates. */
@Component
public final class ServerTreeServerNodeResolver {
  public interface Context {
    Map<String, ServerNodes> servers();

    Map<TargetRef, DefaultMutableTreeNode> leaves();

    Function<String, String> normalizeServerId();
  }

  private record DefaultContext(
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Function<String, String> normalizeServerId)
      implements Context {}

  public static Context context(
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Function<String, String> normalizeServerId) {
    return new DefaultContext(
        Objects.requireNonNull(servers, "servers"),
        Objects.requireNonNull(leaves, "leaves"),
        Objects.requireNonNull(normalizeServerId, "normalizeServerId"));
  }

  public ServerNodes serverNodesForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = normalize(in, serverId);
    if (sid.isEmpty()) return null;
    return in.servers().get(sid);
  }

  public boolean hasServer(Context context, String serverId) {
    return serverNodesForServer(context, serverId) != null;
  }

  public DefaultMutableTreeNode serverNodeForServer(Context context, String serverId) {
    ServerNodes nodes = serverNodesForServer(context, serverId);
    return nodes == null ? null : nodes.serverNode;
  }

  public DefaultMutableTreeNode privateMessagesNodeForServer(Context context, String serverId) {
    ServerNodes nodes = serverNodesForServer(context, serverId);
    return nodes == null ? null : nodes.pmNode;
  }

  public DefaultMutableTreeNode channelListNodeForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    ServerNodes nodes = serverNodesForServer(in, serverId);
    if (nodes == null || nodes.channelListRef == null) return null;
    return in.leaves().get(nodes.channelListRef);
  }

  public DefaultMutableTreeNode monitorNodeForServer(Context context, String serverId) {
    ServerNodes nodes = serverNodesForServer(context, serverId);
    return nodes == null ? null : nodes.monitorNode;
  }

  public DefaultMutableTreeNode interceptorsNodeForServer(Context context, String serverId) {
    ServerNodes nodes = serverNodesForServer(context, serverId);
    return nodes == null ? null : nodes.interceptorsNode;
  }

  public String firstServerIdOrEmpty(
      Context context, Supplier<TargetRef> rememberedSelectionSupplier) {
    Context in = Objects.requireNonNull(context, "context");
    TargetRef remembered =
        rememberedSelectionSupplier == null ? null : rememberedSelectionSupplier.get();
    if (remembered != null) {
      String preferred = normalize(in, remembered.serverId());
      if (!preferred.isEmpty() && in.servers().containsKey(preferred)) {
        return preferred;
      }
    }
    return in.servers().values().stream().findFirst().map(sn -> sn.statusRef.serverId()).orElse("");
  }

  public TargetRef firstServerStatusRefOrNull(Context context) {
    return Objects.requireNonNull(context, "context").servers().values().stream()
        .findFirst()
        .map(sn -> sn.statusRef)
        .orElse(null);
  }

  private String normalize(Context context, String serverId) {
    return Objects.toString(context.normalizeServerId().apply(serverId), "").trim();
  }
}
