package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Default adapter implementation for interaction mediator context wiring. */
public final class ServerTreeInteractionMediatorContextAdapter
    implements ServerTreeInteractionMediator.Context {

  @FunctionalInterface
  public interface TreePathLookup {
    TreePath lookup(int x, int y);
  }

  private final Consumer<Boolean> onTreeShowingChanged;
  private final BooleanSupplier isSelectionBroadcastSuppressed;
  private final Consumer<TargetRef> emitSelection;
  private final Predicate<DefaultMutableTreeNode> isMonitorGroupNode;
  private final Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode;
  private final Function<DefaultMutableTreeNode, String> owningServerIdForNode;
  private final Predicate<MouseEvent> maybeHandleDisconnectedWarningClick;
  private final Predicate<MouseEvent> maybeSelectRowFromLeftClick;
  private final TreePathLookup treePathForRowHit;
  private final Consumer<Runnable> withSuppressedSelectionBroadcast;
  private final Runnable refreshNodeActionsEnabled;
  private final Function<TreePath, JPopupMenu> buildPopupMenu;
  private final Supplier<ServerTreeMiddleDragReorderHandler.Context> middleDragReorderContext;
  private final BooleanSupplier startupSelectionCompleted;
  private final Runnable markStartupSelectionCompleted;
  private final Predicate<TreePath> isPathInCurrentTreeModel;
  private final Supplier<String> firstServerId;
  private final Consumer<String> selectStartupDefaultForServer;
  private final Supplier<TreePath> defaultSelectionPath;

  public ServerTreeInteractionMediatorContextAdapter(
      Consumer<Boolean> onTreeShowingChanged,
      BooleanSupplier isSelectionBroadcastSuppressed,
      Consumer<TargetRef> emitSelection,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Predicate<MouseEvent> maybeHandleDisconnectedWarningClick,
      Predicate<MouseEvent> maybeSelectRowFromLeftClick,
      TreePathLookup treePathForRowHit,
      Consumer<Runnable> withSuppressedSelectionBroadcast,
      Runnable refreshNodeActionsEnabled,
      Function<TreePath, JPopupMenu> buildPopupMenu,
      Supplier<ServerTreeMiddleDragReorderHandler.Context> middleDragReorderContext,
      BooleanSupplier startupSelectionCompleted,
      Runnable markStartupSelectionCompleted,
      Predicate<TreePath> isPathInCurrentTreeModel,
      Supplier<String> firstServerId,
      Consumer<String> selectStartupDefaultForServer,
      Supplier<TreePath> defaultSelectionPath) {
    this.onTreeShowingChanged =
        Objects.requireNonNull(onTreeShowingChanged, "onTreeShowingChanged");
    this.isSelectionBroadcastSuppressed =
        Objects.requireNonNull(isSelectionBroadcastSuppressed, "isSelectionBroadcastSuppressed");
    this.emitSelection = Objects.requireNonNull(emitSelection, "emitSelection");
    this.isMonitorGroupNode = Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    this.isInterceptorsGroupNode =
        Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    this.owningServerIdForNode =
        Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    this.maybeHandleDisconnectedWarningClick =
        Objects.requireNonNull(
            maybeHandleDisconnectedWarningClick, "maybeHandleDisconnectedWarningClick");
    this.maybeSelectRowFromLeftClick =
        Objects.requireNonNull(maybeSelectRowFromLeftClick, "maybeSelectRowFromLeftClick");
    this.treePathForRowHit = Objects.requireNonNull(treePathForRowHit, "treePathForRowHit");
    this.withSuppressedSelectionBroadcast =
        Objects.requireNonNull(
            withSuppressedSelectionBroadcast, "withSuppressedSelectionBroadcast");
    this.refreshNodeActionsEnabled =
        Objects.requireNonNull(refreshNodeActionsEnabled, "refreshNodeActionsEnabled");
    this.buildPopupMenu = Objects.requireNonNull(buildPopupMenu, "buildPopupMenu");
    this.middleDragReorderContext =
        Objects.requireNonNull(middleDragReorderContext, "middleDragReorderContext");
    this.startupSelectionCompleted =
        Objects.requireNonNull(startupSelectionCompleted, "startupSelectionCompleted");
    this.markStartupSelectionCompleted =
        Objects.requireNonNull(markStartupSelectionCompleted, "markStartupSelectionCompleted");
    this.isPathInCurrentTreeModel =
        Objects.requireNonNull(isPathInCurrentTreeModel, "isPathInCurrentTreeModel");
    this.firstServerId = Objects.requireNonNull(firstServerId, "firstServerId");
    this.selectStartupDefaultForServer =
        Objects.requireNonNull(selectStartupDefaultForServer, "selectStartupDefaultForServer");
    this.defaultSelectionPath =
        Objects.requireNonNull(defaultSelectionPath, "defaultSelectionPath");
  }

  @Override
  public void onTreeShowingChanged(boolean showing) {
    onTreeShowingChanged.accept(showing);
  }

  @Override
  public boolean isSelectionBroadcastSuppressed() {
    return isSelectionBroadcastSuppressed.getAsBoolean();
  }

  @Override
  public void emitSelection(TargetRef ref) {
    emitSelection.accept(ref);
  }

  @Override
  public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
    return isMonitorGroupNode.test(node);
  }

  @Override
  public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
    return isInterceptorsGroupNode.test(node);
  }

  @Override
  public String owningServerIdForNode(DefaultMutableTreeNode node) {
    return owningServerIdForNode.apply(node);
  }

  @Override
  public boolean maybeHandleDisconnectedWarningClick(MouseEvent event) {
    return maybeHandleDisconnectedWarningClick.test(event);
  }

  @Override
  public boolean maybeSelectRowFromLeftClick(MouseEvent event) {
    return maybeSelectRowFromLeftClick.test(event);
  }

  @Override
  public TreePath treePathForRowHit(int x, int y) {
    return treePathForRowHit.lookup(x, y);
  }

  @Override
  public void withSuppressedSelectionBroadcast(Runnable task) {
    withSuppressedSelectionBroadcast.accept(task);
  }

  @Override
  public void refreshNodeActionsEnabled() {
    refreshNodeActionsEnabled.run();
  }

  @Override
  public JPopupMenu buildPopupMenu(TreePath path) {
    return buildPopupMenu.apply(path);
  }

  @Override
  public ServerTreeMiddleDragReorderHandler.Context middleDragReorderContext() {
    return middleDragReorderContext.get();
  }

  @Override
  public boolean startupSelectionCompleted() {
    return startupSelectionCompleted.getAsBoolean();
  }

  @Override
  public void markStartupSelectionCompleted() {
    markStartupSelectionCompleted.run();
  }

  @Override
  public boolean isPathInCurrentTreeModel(TreePath path) {
    return isPathInCurrentTreeModel.test(path);
  }

  @Override
  public String firstServerId() {
    return firstServerId.get();
  }

  @Override
  public void selectStartupDefaultForServer(String serverId) {
    selectStartupDefaultForServer.accept(serverId);
  }

  @Override
  public TreePath defaultSelectionPath() {
    return defaultSelectionPath.get();
  }
}
