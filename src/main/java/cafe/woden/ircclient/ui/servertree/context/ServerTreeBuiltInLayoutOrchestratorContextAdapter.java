package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutOrchestrator;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeBuiltInLayoutOrchestrator.Context}. */
public final class ServerTreeBuiltInLayoutOrchestratorContextAdapter
    implements ServerTreeBuiltInLayoutOrchestrator.Context {

  private final Function<String, String> normalizeServerId;
  private final Function<String, ServerNodes> serverNodes;
  private final Function<TargetRef, DefaultMutableTreeNode> leafNode;
  private final Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility;
  private final Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
      rootSiblingNodeKindForNode;
  private final Consumer<DefaultMutableTreeNode> nodeStructureChanged;

  public ServerTreeBuiltInLayoutOrchestratorContextAdapter(
      Function<String, String> normalizeServerId,
      Function<String, ServerNodes> serverNodes,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
          rootSiblingNodeKindForNode,
      Consumer<DefaultMutableTreeNode> nodeStructureChanged) {
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.serverNodes = Objects.requireNonNull(serverNodes, "serverNodes");
    this.leafNode = Objects.requireNonNull(leafNode, "leafNode");
    this.builtInNodesVisibility =
        Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    this.rootSiblingNodeKindForNode =
        Objects.requireNonNull(rootSiblingNodeKindForNode, "rootSiblingNodeKindForNode");
    this.nodeStructureChanged =
        Objects.requireNonNull(nodeStructureChanged, "nodeStructureChanged");
  }

  @Override
  public String normalizeServerId(String serverId) {
    return normalizeServerId.apply(serverId);
  }

  @Override
  public ServerNodes serverNodes(String serverId) {
    return serverNodes.apply(serverId);
  }

  @Override
  public DefaultMutableTreeNode leafNode(TargetRef ref) {
    return leafNode.apply(ref);
  }

  @Override
  public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    return builtInNodesVisibility.apply(serverId);
  }

  @Override
  public RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
      DefaultMutableTreeNode node) {
    return rootSiblingNodeKindForNode.apply(node);
  }

  @Override
  public void nodeStructureChanged(DefaultMutableTreeNode node) {
    nodeStructureChanged.accept(node);
  }
}
