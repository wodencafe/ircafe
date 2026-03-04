package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeStartupSelectionRestorer;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeStartupSelectionRestorer.Context}. */
public final class ServerTreeStartupSelectionRestorerContextAdapter
    implements ServerTreeStartupSelectionRestorer.Context {

  private final Function<String, String> normalizeServerId;
  private final Predicate<TargetRef> hasLeaf;
  private final Function<String, DefaultMutableTreeNode> monitorNodeForServer;
  private final Function<String, DefaultMutableTreeNode> interceptorsNodeForServer;
  private final Function<String, ServerNodes> serverNodesForServer;
  private final Consumer<TargetRef> selectTarget;

  public ServerTreeStartupSelectionRestorerContextAdapter(
      Function<String, String> normalizeServerId,
      Predicate<TargetRef> hasLeaf,
      Function<String, DefaultMutableTreeNode> monitorNodeForServer,
      Function<String, DefaultMutableTreeNode> interceptorsNodeForServer,
      Function<String, ServerNodes> serverNodesForServer,
      Consumer<TargetRef> selectTarget) {
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.hasLeaf = Objects.requireNonNull(hasLeaf, "hasLeaf");
    this.monitorNodeForServer =
        Objects.requireNonNull(monitorNodeForServer, "monitorNodeForServer");
    this.interceptorsNodeForServer =
        Objects.requireNonNull(interceptorsNodeForServer, "interceptorsNodeForServer");
    this.serverNodesForServer =
        Objects.requireNonNull(serverNodesForServer, "serverNodesForServer");
    this.selectTarget = Objects.requireNonNull(selectTarget, "selectTarget");
  }

  @Override
  public String normalizeServerId(String serverId) {
    return normalizeServerId.apply(serverId);
  }

  @Override
  public boolean hasLeaf(TargetRef ref) {
    return hasLeaf.test(ref);
  }

  @Override
  public boolean isMonitorGroupSelectable(String serverId) {
    String sid = normalizeServerId(serverId);
    return isGroupNodeSelectable(sid, monitorNodeForServer.apply(sid));
  }

  @Override
  public boolean isInterceptorsGroupSelectable(String serverId) {
    String sid = normalizeServerId(serverId);
    return isGroupNodeSelectable(sid, interceptorsNodeForServer.apply(sid));
  }

  @Override
  public void selectTarget(TargetRef ref) {
    selectTarget.accept(ref);
  }

  private boolean isGroupNodeSelectable(String serverId, DefaultMutableTreeNode node) {
    ServerNodes nodes = serverNodesForServer.apply(serverId);
    if (nodes == null || node == null) return false;
    return node.getParent() == nodes.serverNode || node.getParent() == nodes.otherNode;
  }
}
