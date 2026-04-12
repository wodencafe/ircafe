package cafe.woden.ircclient.ui.servertree.mutation;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLeafInsertPolicy;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Ensures each server root has a channel-list leaf with stable insert positioning. */
@Component
public final class ServerTreeChannelListNodeEnsurer {

  public interface Context {
    String channelListLabel();

    DefaultMutableTreeNode leafFor(TargetRef ref);

    void rememberLeaf(TargetRef ref, DefaultMutableTreeNode leaf);

    void insertLeaf(DefaultMutableTreeNode serverNode, DefaultMutableTreeNode leaf, int index);
  }

  public static Context context(
      String channelListLabel,
      java.util.Map<TargetRef, DefaultMutableTreeNode> leaves,
      java.util.function.BiConsumer<DefaultMutableTreeNode, int[]> nodesWereInserted) {
    java.util.Objects.requireNonNull(channelListLabel, "channelListLabel");
    java.util.Objects.requireNonNull(leaves, "leaves");
    java.util.Objects.requireNonNull(nodesWereInserted, "nodesWereInserted");
    return new Context() {
      @Override
      public String channelListLabel() {
        return channelListLabel;
      }

      @Override
      public DefaultMutableTreeNode leafFor(TargetRef ref) {
        return leaves.get(ref);
      }

      @Override
      public void rememberLeaf(TargetRef ref, DefaultMutableTreeNode leaf) {
        leaves.put(ref, leaf);
      }

      @Override
      public void insertLeaf(
          DefaultMutableTreeNode serverNode, DefaultMutableTreeNode leaf, int index) {
        serverNode.insert(leaf, index);
        nodesWereInserted.accept(serverNode, new int[] {index});
      }
    };
  }

  public DefaultMutableTreeNode ensureChannelListNode(Context context, ServerNodes serverNodes) {
    Context in = java.util.Objects.requireNonNull(context, "context");
    if (serverNodes == null
        || serverNodes.serverNode == null
        || serverNodes.channelListRef == null) {
      return null;
    }
    DefaultMutableTreeNode existing = in.leafFor(serverNodes.channelListRef);
    if (existing != null) return existing;

    DefaultMutableTreeNode channelListLeaf =
        new DefaultMutableTreeNode(
            new ServerTreeNodeData(serverNodes.channelListRef, in.channelListLabel()));
    int channelListIdx =
        ServerTreeServerLeafInsertPolicy.fixedServerLeafInsertIndexFor(
            serverNodes, serverNodes.channelListRef, in::leafFor);
    in.insertLeaf(serverNodes.serverNode, channelListLeaf, channelListIdx);
    in.rememberLeaf(serverNodes.channelListRef, channelListLeaf);
    return channelListLeaf;
  }
}
