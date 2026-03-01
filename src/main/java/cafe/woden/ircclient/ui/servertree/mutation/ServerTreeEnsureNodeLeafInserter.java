package cafe.woden.ircclient.ui.servertree.mutation;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.Map;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Inserts ensured leaves into the tree model and updates default PM-online state. */
public final class ServerTreeEnsureNodeLeafInserter {

  public interface Context {
    boolean isPrivateMessageTarget(TargetRef ref);
  }

  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final DefaultTreeModel model;
  private final ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore;
  private final Context context;

  public ServerTreeEnsureNodeLeafInserter(
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      DefaultTreeModel model,
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      Context context) {
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.model = Objects.requireNonNull(model, "model");
    this.privateMessageOnlineStateStore =
        Objects.requireNonNull(privateMessageOnlineStateStore, "privateMessageOnlineStateStore");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void insertLeaf(DefaultMutableTreeNode parent, TargetRef ref, String leafLabel) {
    if (parent == null || ref == null) return;
    DefaultMutableTreeNode leaf =
        new DefaultMutableTreeNode(new ServerTreeNodeData(ref, Objects.toString(leafLabel, "")));
    leaves.put(ref, leaf);
    if (context.isPrivateMessageTarget(ref)) {
      privateMessageOnlineStateStore.putIfAbsent(ref, false);
    }
    int idx = parent.getChildCount();
    parent.insert(leaf, idx);
    model.nodesWereInserted(parent, new int[] {idx});
  }
}
