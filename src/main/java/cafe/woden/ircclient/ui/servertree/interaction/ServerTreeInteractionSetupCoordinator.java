package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import io.github.andrewauclair.moderndocking.Dockable;
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

/** Owns interaction wiring assembly and exposes lifecycle operations used by the dockable. */
public final class ServerTreeInteractionSetupCoordinator {

  @FunctionalInterface
  public interface MediatorInputsFactory
      extends BiFunction<
          ServerTreePinnedDockDragController,
          ServerTreeMiddleDragReorderHandler.Context,
          ServerTreeInteractionWiringFactory.MediatorInputs> {}

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

  private final ServerTreePinnedDockDragController pinnedDockDragController;
  private final ServerTreeInteractionMediator interactionMediator;

  public static ServerTreeInteractionSetupCoordinator create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");
    ServerTreeDragReorderSupport dragReorderSupport =
        Objects.requireNonNull(in.dragReorderSupport(), "dragReorderSupport");
    JTree tree = Objects.requireNonNull(in.tree(), "tree");

    return create(
        Objects.requireNonNull(in.interactionWiringFactory(), "interactionWiringFactory"),
        new ServerTreeInteractionWiringFactory.MiddleDragInputs(
            tree,
            Objects.requireNonNull(in.model(), "model"),
            dragReorderSupport::isDraggableChannelNode,
            dragReorderSupport::isRootSiblingReorderableNode,
            dragReorderSupport::isMovableBuiltInNode,
            Objects.requireNonNull(in.owningServerIdForNode(), "owningServerIdForNode"),
            Objects.requireNonNull(in.serverNodes(), "serverNodes"),
            Objects.requireNonNull(in.rootSiblingNodeKindForNode(), "rootSiblingNodeKindForNode"),
            Objects.requireNonNull(
                in.builtInLayoutNodeKindForNode(), "builtInLayoutNodeKindForNode"),
            dragReorderSupport::minInsertIndex,
            dragReorderSupport::maxInsertIndex,
            Objects.requireNonNull(in.rootBuiltInInsertIndex(), "rootBuiltInInsertIndex"),
            dragReorderSupport::setInsertionLineForIndex,
            dragReorderSupport::clearInsertionLine,
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
            tree, Objects.requireNonNull(in.channelTargetForHit(), "channelTargetForHit")),
        (pinnedDockDragController, middleDragReorderContext) ->
            new ServerTreeInteractionWiringFactory.MediatorInputs(
                tree,
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

  public static ServerTreeInteractionSetupCoordinator create(
      ServerTreeInteractionWiringFactory interactionWiringFactory,
      ServerTreeInteractionWiringFactory.MiddleDragInputs middleDragInputs,
      ServerTreeInteractionWiringFactory.PinnedDockDragInputs pinnedDockDragInputs,
      MediatorInputsFactory mediatorInputsFactory) {
    Objects.requireNonNull(interactionWiringFactory, "interactionWiringFactory");
    Objects.requireNonNull(middleDragInputs, "middleDragInputs");
    Objects.requireNonNull(pinnedDockDragInputs, "pinnedDockDragInputs");
    Objects.requireNonNull(mediatorInputsFactory, "mediatorInputsFactory");

    ServerTreeMiddleDragReorderHandler.Context middleDragContext =
        interactionWiringFactory.createMiddleDragReorderContext(middleDragInputs);
    ServerTreePinnedDockDragController pinnedDockDragController =
        interactionWiringFactory.createPinnedDockDragController(pinnedDockDragInputs);
    ServerTreeInteractionWiringFactory.MediatorInputs mediatorInputs =
        mediatorInputsFactory.apply(pinnedDockDragController, middleDragContext);
    ServerTreeInteractionMediator interactionMediator =
        interactionWiringFactory.createInteractionMediator(mediatorInputs);
    return new ServerTreeInteractionSetupCoordinator(pinnedDockDragController, interactionMediator);
  }

  public ServerTreeInteractionSetupCoordinator(
      ServerTreePinnedDockDragController pinnedDockDragController,
      ServerTreeInteractionMediator interactionMediator) {
    this.pinnedDockDragController =
        Objects.requireNonNull(pinnedDockDragController, "pinnedDockDragController");
    this.interactionMediator = Objects.requireNonNull(interactionMediator, "interactionMediator");
  }

  public void setPinnedDockableProvider(Function<TargetRef, Dockable> provider) {
    pinnedDockDragController.setPinnedDockableProvider(provider);
  }

  public void clearPreparedChannelDockDrag() {
    pinnedDockDragController.clearPreparedChannelDockDrag();
  }

  public void install() {
    interactionMediator.install();
  }
}
