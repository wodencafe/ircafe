package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Factory that assembles interaction setup inputs into a coordinator instance. */
public final class ServerTreeInteractionSetupCoordinatorFactory {

  private ServerTreeInteractionSetupCoordinatorFactory() {}

  public static ServerTreeInteractionSetupCoordinator create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");

    return ServerTreeInteractionSetupCoordinator.create(
        Objects.requireNonNull(in.interactionWiringFactory(), "interactionWiringFactory"),
        new ServerTreeInteractionWiringFactory.MiddleDragInputs(
            Objects.requireNonNull(in.tree(), "tree"),
            Objects.requireNonNull(in.model(), "model"),
            in.dragReorderSupport()::isDraggableChannelNode,
            in.dragReorderSupport()::isRootSiblingReorderableNode,
            in.dragReorderSupport()::isMovableBuiltInNode,
            Objects.requireNonNull(in.owningServerIdForNode(), "owningServerIdForNode"),
            Objects.requireNonNull(in.serverNodes(), "serverNodes"),
            Objects.requireNonNull(in.rootSiblingNodeKindForNode(), "rootSiblingNodeKindForNode"),
            Objects.requireNonNull(
                in.builtInLayoutNodeKindForNode(), "builtInLayoutNodeKindForNode"),
            in.dragReorderSupport()::minInsertIndex,
            in.dragReorderSupport()::maxInsertIndex,
            Objects.requireNonNull(in.rootBuiltInInsertIndex(), "rootBuiltInInsertIndex"),
            in.dragReorderSupport()::setInsertionLineForIndex,
            in.dragReorderSupport()::clearInsertionLine,
            Objects.requireNonNull(in.isChannelListLeafNode(), "isChannelListLeafNode"),
            parentNode -> {
              String serverId = in.owningServerIdForNode().apply(parentNode);
              if (serverId.isBlank()) return;
              in.persistOrderAndResortAfterManualMove().accept(serverId);
            },
            Objects.requireNonNull(
                in.persistBuiltInLayoutFromTree(), "persistBuiltInLayoutFromTree"),
            Objects.requireNonNull(
                in.persistRootSiblingOrderFromTree(), "persistRootSiblingOrderFromTree"),
            Objects.requireNonNull(
                in.withSuppressedSelectionBroadcast(), "withSuppressedSelectionBroadcast"),
            Objects.requireNonNull(in.refreshNodeActionsEnabled(), "refreshNodeActionsEnabled")),
        new ServerTreeInteractionWiringFactory.PinnedDockDragInputs(
            in.tree(), Objects.requireNonNull(in.channelTargetForHit(), "channelTargetForHit")),
        (pinnedDockDragController, middleDragReorderContext) ->
            new ServerTreeInteractionWiringFactory.MediatorInputs(
                in.tree(),
                Objects.requireNonNull(in.serverActionOverlay(), "serverActionOverlay"),
                Objects.requireNonNull(in.onTreeShowingChanged(), "onTreeShowingChanged"),
                Objects.requireNonNull(
                    in.isSelectionBroadcastSuppressed(), "isSelectionBroadcastSuppressed"),
                Objects.requireNonNull(in.publishSelection(), "publishSelection"),
                Objects.requireNonNull(in.isMonitorGroupNode(), "isMonitorGroupNode"),
                Objects.requireNonNull(in.isInterceptorsGroupNode(), "isInterceptorsGroupNode"),
                in.owningServerIdForNode(),
                Objects.requireNonNull(
                    in.maybeHandleDisconnectedWarningClick(),
                    "maybeHandleDisconnectedWarningClick"),
                Objects.requireNonNull(
                    in.maybeSelectRowFromLeftClick(), "maybeSelectRowFromLeftClick"),
                Objects.requireNonNull(in.treePathForRowHit(), "treePathForRowHit"),
                Objects.requireNonNull(
                    in.withSuppressedSelectionBroadcast(), "withSuppressedSelectionBroadcast"),
                Objects.requireNonNull(in.refreshNodeActionsEnabled(), "refreshNodeActionsEnabled"),
                Objects.requireNonNull(in.buildPopupMenu(), "buildPopupMenu"),
                pinnedDockDragController::prepareChannelDockDrag,
                pinnedDockDragController::clearPreparedChannelDockDrag,
                () -> middleDragReorderContext,
                Objects.requireNonNull(in.startupSelectionCompleted(), "startupSelectionCompleted"),
                Objects.requireNonNull(
                    in.markStartupSelectionCompleted(), "markStartupSelectionCompleted"),
                Objects.requireNonNull(in.isPathInCurrentTreeModel(), "isPathInCurrentTreeModel"),
                Objects.requireNonNull(in.firstServerIdOrEmpty(), "firstServerIdOrEmpty"),
                Objects.requireNonNull(
                    in.selectStartupDefaultForServer(), "selectStartupDefaultForServer"),
                Objects.requireNonNull(in.defaultSelectionPath(), "defaultSelectionPath")));
  }

  public record Inputs(
      ServerTreeInteractionWiringFactory interactionWiringFactory,
      JTree tree,
      DefaultTreeModel model,
      ServerTreeDragReorderSupport dragReorderSupport,
      Predicate<DefaultMutableTreeNode> isChannelListLeafNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Function<String, ServerNodes> serverNodes,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
          rootSiblingNodeKindForNode,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
          builtInLayoutNodeKindForNode,
      BiFunction<ServerNodes, Integer, Integer> rootBuiltInInsertIndex,
      Consumer<String> persistOrderAndResortAfterManualMove,
      Consumer<String> persistBuiltInLayoutFromTree,
      Consumer<String> persistRootSiblingOrderFromTree,
      Consumer<Runnable> withSuppressedSelectionBroadcast,
      Runnable refreshNodeActionsEnabled,
      BiFunction<Integer, Integer, TargetRef> channelTargetForHit,
      ServerTreeServerActionOverlay serverActionOverlay,
      Consumer<Boolean> onTreeShowingChanged,
      BooleanSupplier isSelectionBroadcastSuppressed,
      Consumer<TargetRef> publishSelection,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<MouseEvent> maybeHandleDisconnectedWarningClick,
      Predicate<MouseEvent> maybeSelectRowFromLeftClick,
      BiFunction<Integer, Integer, TreePath> treePathForRowHit,
      Function<TreePath, JPopupMenu> buildPopupMenu,
      BooleanSupplier startupSelectionCompleted,
      Runnable markStartupSelectionCompleted,
      Predicate<TreePath> isPathInCurrentTreeModel,
      Supplier<String> firstServerIdOrEmpty,
      Consumer<String> selectStartupDefaultForServer,
      Supplier<TreePath> defaultSelectionPath) {}
}
