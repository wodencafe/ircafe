package cafe.woden.ircclient.ui.servertree.mutation;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLeafInsertPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import javax.swing.tree.DefaultMutableTreeNode;

/** Ensures each server root has a channel-list leaf with stable insert positioning. */
public final class ServerTreeChannelListNodeEnsurer {
  private final String channelListLabel;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final BiConsumer<DefaultMutableTreeNode, int[]> nodesWereInserted;

  public ServerTreeChannelListNodeEnsurer(
      String channelListLabel,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      BiConsumer<DefaultMutableTreeNode, int[]> nodesWereInserted) {
    this.channelListLabel = Objects.requireNonNull(channelListLabel, "channelListLabel");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.nodesWereInserted = Objects.requireNonNull(nodesWereInserted, "nodesWereInserted");
  }

  public DefaultMutableTreeNode ensureChannelListNode(ServerNodes serverNodes) {
    if (serverNodes == null
        || serverNodes.serverNode == null
        || serverNodes.channelListRef == null) {
      return null;
    }
    DefaultMutableTreeNode existing = leaves.get(serverNodes.channelListRef);
    if (existing != null) return existing;

    DefaultMutableTreeNode channelListLeaf =
        new DefaultMutableTreeNode(
            new ServerTreeNodeData(serverNodes.channelListRef, channelListLabel));
    int channelListIdx =
        ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
            serverNodes, serverNodes.channelListRef, leaves::get);
    serverNodes.serverNode.insert(channelListLeaf, channelListIdx);
    leaves.put(serverNodes.channelListRef, channelListLeaf);
    nodesWereInserted.accept(serverNodes.serverNode, new int[] {channelListIdx});
    return channelListLeaf;
  }
}
