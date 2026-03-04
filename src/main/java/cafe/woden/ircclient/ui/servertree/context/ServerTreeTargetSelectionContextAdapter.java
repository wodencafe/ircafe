package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetSelectionCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeTargetSelectionCoordinator.Context}. */
public final class ServerTreeTargetSelectionContextAdapter
    implements ServerTreeTargetSelectionCoordinator.Context {

  private final Consumer<TargetRef> ensureNode;
  private final Function<String, DefaultMutableTreeNode> monitorGroupNode;
  private final Function<String, DefaultMutableTreeNode> interceptorsGroupNode;
  private final Function<String, ServerNodes> serverNodesForServer;
  private final Function<TargetRef, DefaultMutableTreeNode> leafNode;
  private final Consumer<DefaultMutableTreeNode> selectNode;

  public ServerTreeTargetSelectionContextAdapter(
      Consumer<TargetRef> ensureNode,
      Function<String, DefaultMutableTreeNode> monitorGroupNode,
      Function<String, DefaultMutableTreeNode> interceptorsGroupNode,
      Function<String, ServerNodes> serverNodesForServer,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Consumer<DefaultMutableTreeNode> selectNode) {
    this.ensureNode = Objects.requireNonNull(ensureNode, "ensureNode");
    this.monitorGroupNode = Objects.requireNonNull(monitorGroupNode, "monitorGroupNode");
    this.interceptorsGroupNode =
        Objects.requireNonNull(interceptorsGroupNode, "interceptorsGroupNode");
    this.serverNodesForServer =
        Objects.requireNonNull(serverNodesForServer, "serverNodesForServer");
    this.leafNode = Objects.requireNonNull(leafNode, "leafNode");
    this.selectNode = Objects.requireNonNull(selectNode, "selectNode");
  }

  @Override
  public void ensureNode(TargetRef ref) {
    ensureNode.accept(ref);
  }

  @Override
  public DefaultMutableTreeNode monitorGroupNode(String serverId) {
    return monitorGroupNode.apply(serverId);
  }

  @Override
  public DefaultMutableTreeNode interceptorsGroupNode(String serverId) {
    return interceptorsGroupNode.apply(serverId);
  }

  @Override
  public boolean isGroupNodeSelectable(String serverId, DefaultMutableTreeNode node) {
    ServerNodes nodes = serverNodesForServer.apply(serverId);
    if (nodes == null || node == null) return false;
    return node.getParent() == nodes.serverNode || node.getParent() == nodes.otherNode;
  }

  @Override
  public DefaultMutableTreeNode leafNode(TargetRef ref) {
    return leafNode.apply(ref);
  }

  @Override
  public void selectNode(DefaultMutableTreeNode node) {
    selectNode.accept(node);
  }
}
