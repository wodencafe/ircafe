package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Factory for building server-tree interaction wiring collaborators. */
public final class ServerTreeInteractionWiringFactory {

  public record MiddleDragInputs(
      JTree tree,
      DefaultTreeModel model,
      Predicate<DefaultMutableTreeNode> isDraggableChannelNode,
      Predicate<DefaultMutableTreeNode> isRootSiblingReorderableNode,
      Predicate<DefaultMutableTreeNode> isMovableBuiltInNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Function<String, ServerNodes> serverNodes,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
          rootSiblingNodeKindForNode,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
          builtInLayoutNodeKindForNode,
      ToIntFunction<DefaultMutableTreeNode> minInsertIndex,
      ToIntFunction<DefaultMutableTreeNode> maxInsertIndex,
      BiFunction<ServerNodes, Integer, Integer> rootBuiltInInsertIndex,
      BiConsumer<DefaultMutableTreeNode, Integer> setInsertionLineForIndex,
      Runnable clearInsertionLine,
      Predicate<DefaultMutableTreeNode> isChannelListLeafNode,
      Consumer<DefaultMutableTreeNode> persistCustomOrderForParent,
      Consumer<String> persistBuiltInLayout,
      Consumer<String> persistRootSiblingOrder,
      Consumer<Runnable> withSuppressedSelectionBroadcast,
      Runnable refreshNodeActionsEnabled) {}

  public record PinnedDockDragInputs(
      JTree tree, BiFunction<Integer, Integer, TargetRef> channelTargetForHit) {}

  public record MediatorInputs(
      JTree tree,
      ServerTreeServerActionOverlay serverActionOverlay,
      Consumer<Boolean> onTreeShowingChanged,
      BooleanSupplier isSelectionBroadcastSuppressed,
      Consumer<TargetRef> emitSelection,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Predicate<MouseEvent> maybeHandleDisconnectedWarningClick,
      Predicate<MouseEvent> maybeSelectRowFromLeftClick,
      BiFunction<Integer, Integer, TreePath> treePathForRowHit,
      Consumer<Runnable> withSuppressedSelectionBroadcast,
      Runnable refreshNodeActionsEnabled,
      Function<TreePath, JPopupMenu> buildPopupMenu,
      Consumer<MouseEvent> prepareChannelDockDrag,
      Runnable clearPreparedChannelDockDrag,
      Supplier<ServerTreeMiddleDragReorderHandler.Context> middleDragReorderContext,
      BooleanSupplier startupSelectionCompleted,
      Runnable markStartupSelectionCompleted,
      Predicate<TreePath> isPathInCurrentTreeModel,
      Supplier<String> firstServerId,
      Consumer<String> selectStartupDefaultForServer,
      Supplier<TreePath> defaultSelectionPath) {}

  public ServerTreeMiddleDragReorderHandler.Context createMiddleDragReorderContext(
      MiddleDragInputs in) {
    Objects.requireNonNull(in, "in");
    return ServerTreeMiddleDragReorderHandler.context(
        Objects.requireNonNull(in.tree(), "tree"),
        Objects.requireNonNull(in.model(), "model"),
        Objects.requireNonNull(in.isDraggableChannelNode(), "isDraggableChannelNode"),
        Objects.requireNonNull(in.isRootSiblingReorderableNode(), "isRootSiblingReorderableNode"),
        Objects.requireNonNull(in.isMovableBuiltInNode(), "isMovableBuiltInNode"),
        Objects.requireNonNull(in.owningServerIdForNode(), "owningServerIdForNode"),
        Objects.requireNonNull(in.serverNodes(), "serverNodes"),
        Objects.requireNonNull(in.rootSiblingNodeKindForNode(), "rootSiblingNodeKindForNode"),
        Objects.requireNonNull(in.builtInLayoutNodeKindForNode(), "builtInLayoutNodeKindForNode"),
        Objects.requireNonNull(in.minInsertIndex(), "minInsertIndex"),
        Objects.requireNonNull(in.maxInsertIndex(), "maxInsertIndex"),
        Objects.requireNonNull(in.rootBuiltInInsertIndex(), "rootBuiltInInsertIndex"),
        Objects.requireNonNull(in.setInsertionLineForIndex(), "setInsertionLineForIndex"),
        Objects.requireNonNull(in.clearInsertionLine(), "clearInsertionLine"),
        Objects.requireNonNull(in.isChannelListLeafNode(), "isChannelListLeafNode"),
        Objects.requireNonNull(in.persistCustomOrderForParent(), "persistCustomOrderForParent"),
        Objects.requireNonNull(in.persistBuiltInLayout(), "persistBuiltInLayout"),
        Objects.requireNonNull(in.persistRootSiblingOrder(), "persistRootSiblingOrder"),
        Objects.requireNonNull(
            in.withSuppressedSelectionBroadcast(), "withSuppressedSelectionBroadcast"),
        Objects.requireNonNull(in.refreshNodeActionsEnabled(), "refreshNodeActionsEnabled"));
  }

  public ServerTreePinnedDockDragController createPinnedDockDragController(
      PinnedDockDragInputs in) {
    Objects.requireNonNull(in, "in");
    return new ServerTreePinnedDockDragController(
        Objects.requireNonNull(in.tree(), "tree"),
        Objects.requireNonNull(in.channelTargetForHit(), "channelTargetForHit"));
  }

  public ServerTreeInteractionMediator createInteractionMediator(MediatorInputs in) {
    Objects.requireNonNull(in, "in");
    return new ServerTreeInteractionMediator(
        Objects.requireNonNull(in.tree(), "tree"),
        Objects.requireNonNull(in.serverActionOverlay(), "serverActionOverlay"),
        ServerTreeInteractionMediator.context(
            Objects.requireNonNull(in.onTreeShowingChanged(), "onTreeShowingChanged"),
            Objects.requireNonNull(
                in.isSelectionBroadcastSuppressed(), "isSelectionBroadcastSuppressed"),
            Objects.requireNonNull(in.emitSelection(), "emitSelection"),
            Objects.requireNonNull(in.isMonitorGroupNode(), "isMonitorGroupNode"),
            Objects.requireNonNull(in.isInterceptorsGroupNode(), "isInterceptorsGroupNode"),
            Objects.requireNonNull(in.owningServerIdForNode(), "owningServerIdForNode"),
            Objects.requireNonNull(
                in.maybeHandleDisconnectedWarningClick(), "maybeHandleDisconnectedWarningClick"),
            Objects.requireNonNull(in.maybeSelectRowFromLeftClick(), "maybeSelectRowFromLeftClick"),
            (x, y) -> in.treePathForRowHit().apply(x, y),
            Objects.requireNonNull(
                in.withSuppressedSelectionBroadcast(), "withSuppressedSelectionBroadcast"),
            Objects.requireNonNull(in.refreshNodeActionsEnabled(), "refreshNodeActionsEnabled"),
            Objects.requireNonNull(in.buildPopupMenu(), "buildPopupMenu"),
            Objects.requireNonNull(in.prepareChannelDockDrag(), "prepareChannelDockDrag"),
            Objects.requireNonNull(
                in.clearPreparedChannelDockDrag(), "clearPreparedChannelDockDrag"),
            Objects.requireNonNull(in.middleDragReorderContext(), "middleDragReorderContext"),
            Objects.requireNonNull(in.startupSelectionCompleted(), "startupSelectionCompleted"),
            Objects.requireNonNull(
                in.markStartupSelectionCompleted(), "markStartupSelectionCompleted"),
            Objects.requireNonNull(in.isPathInCurrentTreeModel(), "isPathInCurrentTreeModel"),
            Objects.requireNonNull(in.firstServerId(), "firstServerId"),
            Objects.requireNonNull(
                in.selectStartupDefaultForServer(), "selectStartupDefaultForServer"),
            Objects.requireNonNull(in.defaultSelectionPath(), "defaultSelectionPath")));
  }
}
