package cafe.woden.ircclient.ui.servertree.mutation;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLeafInsertPolicy;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.tree.DefaultMutableTreeNode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Ensures each server root has a channel-list leaf with stable insert positioning. */
@RequiredArgsConstructor
public final class ServerTreeChannelListNodeEnsurer {

  @NonNull private final String channelListLabel;
  @NonNull private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  @NonNull private final BiConsumer<DefaultMutableTreeNode, int[]> nodesWereInserted;

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
