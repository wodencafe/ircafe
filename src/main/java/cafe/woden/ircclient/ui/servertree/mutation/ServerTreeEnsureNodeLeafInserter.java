package cafe.woden.ircclient.ui.servertree.mutation;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.springframework.stereotype.Component;

/** Inserts ensured leaves into the tree model and updates default PM-online state. */
@Component
public final class ServerTreeEnsureNodeLeafInserter {

  public interface Context {
    boolean isPrivateMessageTarget(TargetRef ref);

    void rememberLeaf(TargetRef ref, DefaultMutableTreeNode leaf);

    void initializePrivateMessageOnlineState(TargetRef ref);

    void insertNode(DefaultMutableTreeNode parent, DefaultMutableTreeNode leaf, int index);
  }

  public static Context context(
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      DefaultTreeModel model,
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      Predicate<TargetRef> isPrivateMessageTarget) {
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(privateMessageOnlineStateStore, "privateMessageOnlineStateStore");
    Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
    return new Context() {
      @Override
      public boolean isPrivateMessageTarget(TargetRef ref) {
        return isPrivateMessageTarget.test(ref);
      }

      @Override
      public void rememberLeaf(TargetRef ref, DefaultMutableTreeNode leaf) {
        leaves.put(ref, leaf);
      }

      @Override
      public void initializePrivateMessageOnlineState(TargetRef ref) {
        privateMessageOnlineStateStore.putIfAbsent(ref, false);
      }

      @Override
      public void insertNode(
          DefaultMutableTreeNode parent, DefaultMutableTreeNode leaf, int index) {
        parent.insert(leaf, index);
        model.nodesWereInserted(parent, new int[] {index});
      }
    };
  }

  public void insertLeaf(
      Context context, DefaultMutableTreeNode parent, TargetRef ref, String leafLabel) {
    Context in = Objects.requireNonNull(context, "context");
    if (parent == null || ref == null) return;
    DefaultMutableTreeNode leaf =
        new DefaultMutableTreeNode(new ServerTreeNodeData(ref, Objects.toString(leafLabel, "")));
    in.rememberLeaf(ref, leaf);
    if (in.isPrivateMessageTarget(ref)) {
      in.initializePrivateMessageOnlineState(ref);
    }
    int idx = parent.getChildCount();
    in.insertNode(parent, leaf, idx);
  }
}
