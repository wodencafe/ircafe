package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeNodeReorderPolicy;
import cafe.woden.ircclient.ui.util.TreeNodeActions;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Factory for building server-tree node actions wiring. */
public final class ServerTreeNodeActionsFactory {

  public record Inputs(
      JTree tree,
      DefaultTreeModel model,
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isChannelListLeafNode,
      Predicate<TargetRef> isChannelPinned,
      Function<DefaultMutableTreeNode, TargetRef> targetRefForNode,
      Function<DefaultMutableTreeNode, String> nodeLabelForNode,
      Predicate<TargetRef> isChannelDisconnected,
      Consumer<TargetRef> requestDisconnectChannel,
      Consumer<TargetRef> requestCloseTarget,
      Predicate<DefaultMutableTreeNode> isRootSiblingReorderableNode,
      Predicate<DefaultMutableTreeNode> isMovableBuiltInNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Consumer<String> persistOrderAndResortAfterManualMove,
      Consumer<String> persistRootSiblingOrder,
      Consumer<String> persistBuiltInLayout) {}

  public TreeNodeActions<TargetRef> create(Inputs in) {
    Objects.requireNonNull(in, "in");
    return new TreeNodeActions<>(
        Objects.requireNonNull(in.tree(), "tree"),
        Objects.requireNonNull(in.model(), "model"),
        new ServerTreeNodeReorderPolicy(
            Objects.requireNonNull(in.isServerNode(), "isServerNode"),
            Objects.requireNonNull(in.isChannelListLeafNode(), "isChannelListLeafNode"),
            Objects.requireNonNull(in.isChannelPinned(), "isChannelPinned"),
            Objects.requireNonNull(in.targetRefForNode(), "targetRefForNode"),
            Objects.requireNonNull(in.nodeLabelForNode(), "nodeLabelForNode")),
        n -> {
          Object userObject = n.getUserObject();
          if (userObject instanceof ServerTreeNodeData nodeData) return nodeData.ref;
          return null;
        },
        ref -> {
          if (ref == null) return;
          if (ref.isChannel()) {
            if (!in.isChannelDisconnected().test(ref)) {
              in.requestDisconnectChannel().accept(ref);
            }
            return;
          }
          in.requestCloseTarget().accept(ref);
        },
        movedNode -> {
          if (movedNode == null) return;
          Object userObject = movedNode.getUserObject();
          DefaultMutableTreeNode parent = (DefaultMutableTreeNode) movedNode.getParent();
          if (userObject instanceof ServerTreeNodeData nodeData
              && nodeData.ref != null
              && nodeData.ref.isChannel()) {
            if (!in.isChannelListLeafNode().test(parent)) return;
            String sid = in.owningServerIdForNode().apply(parent);
            in.persistOrderAndResortAfterManualMove().accept(sid);
            return;
          }
          if (in.isRootSiblingReorderableNode().test(movedNode)) {
            String sid = in.owningServerIdForNode().apply(movedNode);
            if (sid.isBlank()) return;
            in.persistRootSiblingOrder().accept(sid);
            return;
          }
          if (!in.isMovableBuiltInNode().test(movedNode)) return;
          String sid = in.owningServerIdForNode().apply(movedNode);
          if (sid.isBlank()) return;
          in.persistBuiltInLayout().accept(sid);
        });
  }
}
