package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeKeyBindingsInstaller;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeNodeActionsFactory;
import cafe.woden.ircclient.ui.util.TreeNodeActions;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Factory that assembles tree interaction bindings used by the server tree UI. */
public final class ServerTreeTreeInteractionBindingsFactory {

  private ServerTreeTreeInteractionBindingsFactory() {}

  public static ServerTreeTreeInteractionBindings create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");
    JTree tree = Objects.requireNonNull(in.tree(), "tree");

    ToolTipManager.sharedInstance().registerComponent(tree);
    tree.addPropertyChangeListener(
        "UI",
        event ->
            javax.swing.SwingUtilities.invokeLater(
                Objects.requireNonNull(
                    in.refreshTreeLayoutAfterUiChange(), "refreshTreeLayoutAfterUiChange")));

    TreeNodeActions<TargetRef> nodeActions =
        Objects.requireNonNull(in.nodeActionsFactory(), "nodeActionsFactory")
            .create(
                new ServerTreeNodeActionsFactory.Inputs(
                    tree,
                    Objects.requireNonNull(in.model(), "model"),
                    Objects.requireNonNull(in.isServerNode(), "isServerNode"),
                    Objects.requireNonNull(in.isChannelListLeafNode(), "isChannelListLeafNode"),
                    Objects.requireNonNull(in.isChannelPinned(), "isChannelPinned"),
                    Objects.requireNonNull(in.targetRefForNode(), "targetRefForNode"),
                    Objects.requireNonNull(in.nodeLabelForNode(), "nodeLabelForNode"),
                    Objects.requireNonNull(in.isChannelDisconnected(), "isChannelDisconnected"),
                    Objects.requireNonNull(in.emitDisconnectChannel(), "emitDisconnectChannel"),
                    Objects.requireNonNull(in.emitCloseTarget(), "emitCloseTarget"),
                    Objects.requireNonNull(
                        in.isRootSiblingReorderableNode(), "isRootSiblingReorderableNode"),
                    Objects.requireNonNull(in.isMovableBuiltInNode(), "isMovableBuiltInNode"),
                    Objects.requireNonNull(in.owningServerIdForNode(), "owningServerIdForNode"),
                    Objects.requireNonNull(
                        in.persistOrderAndResortAfterManualMove(),
                        "persistOrderAndResortAfterManualMove"),
                    Objects.requireNonNull(
                        in.persistRootSiblingOrderFromTree(), "persistRootSiblingOrderFromTree"),
                    Objects.requireNonNull(
                        in.persistBuiltInLayoutFromTree(), "persistBuiltInLayoutFromTree")));
    ServerTreeKeyBindingsInstaller.install(
        tree,
        nodeActions::moveUpAction,
        nodeActions::moveDownAction,
        nodeActions::closeAction,
        Objects.requireNonNull(in.openSelectedNodeInChatDock(), "openSelectedNodeInChatDock"));

    return new ServerTreeTreeInteractionBindings(nodeActions);
  }

  public record Inputs(
      ServerTreeNodeActionsFactory nodeActionsFactory,
      JTree tree,
      DefaultTreeModel model,
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isChannelListLeafNode,
      Predicate<TargetRef> isChannelPinned,
      Function<DefaultMutableTreeNode, TargetRef> targetRefForNode,
      Function<DefaultMutableTreeNode, String> nodeLabelForNode,
      Predicate<TargetRef> isChannelDisconnected,
      Consumer<TargetRef> emitDisconnectChannel,
      Consumer<TargetRef> emitCloseTarget,
      Predicate<DefaultMutableTreeNode> isRootSiblingReorderableNode,
      Predicate<DefaultMutableTreeNode> isMovableBuiltInNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Consumer<String> persistOrderAndResortAfterManualMove,
      Consumer<String> persistRootSiblingOrderFromTree,
      Consumer<String> persistBuiltInLayoutFromTree,
      Runnable refreshTreeLayoutAfterUiChange,
      Runnable openSelectedNodeInChatDock) {}
}
