package cafe.woden.ircclient.ui.servertree.query;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Resolves server-scoped tree nodes and first-server selection candidates. */
public final class ServerTreeServerNodeResolver {
  private final Map<String, ServerNodes> servers;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Function<String, String> normalizeServerId;

  public ServerTreeServerNodeResolver(
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Function<String, String> normalizeServerId) {
    this.servers = Objects.requireNonNull(servers, "servers");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
  }

  public ServerNodes serverNodesForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return null;
    return servers.get(sid);
  }

  public boolean hasServer(String serverId) {
    return serverNodesForServer(serverId) != null;
  }

  public DefaultMutableTreeNode serverNodeForServer(String serverId) {
    ServerNodes nodes = serverNodesForServer(serverId);
    return nodes == null ? null : nodes.serverNode;
  }

  public DefaultMutableTreeNode privateMessagesNodeForServer(String serverId) {
    ServerNodes nodes = serverNodesForServer(serverId);
    return nodes == null ? null : nodes.pmNode;
  }

  public DefaultMutableTreeNode channelListNodeForServer(String serverId) {
    ServerNodes nodes = serverNodesForServer(serverId);
    if (nodes == null || nodes.channelListRef == null) return null;
    return leaves.get(nodes.channelListRef);
  }

  public DefaultMutableTreeNode monitorNodeForServer(String serverId) {
    ServerNodes nodes = serverNodesForServer(serverId);
    return nodes == null ? null : nodes.monitorNode;
  }

  public DefaultMutableTreeNode interceptorsNodeForServer(String serverId) {
    ServerNodes nodes = serverNodesForServer(serverId);
    return nodes == null ? null : nodes.interceptorsNode;
  }

  public String firstServerIdOrEmpty(Supplier<TargetRef> rememberedSelectionSupplier) {
    TargetRef remembered =
        rememberedSelectionSupplier == null ? null : rememberedSelectionSupplier.get();
    if (remembered != null) {
      String preferred = normalize(remembered.serverId());
      if (!preferred.isEmpty() && servers.containsKey(preferred)) {
        return preferred;
      }
    }
    return servers.values().stream().findFirst().map(sn -> sn.statusRef.serverId()).orElse("");
  }

  public TargetRef firstServerStatusRefOrNull() {
    return servers.values().stream().findFirst().map(sn -> sn.statusRef).orElse(null);
  }

  private String normalize(String serverId) {
    return Objects.toString(normalizeServerId.apply(serverId), "").trim();
  }
}
