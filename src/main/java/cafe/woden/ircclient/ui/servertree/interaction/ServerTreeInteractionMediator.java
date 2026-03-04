package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.dnd.DragSource;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Binds tree listeners and startup-selection choreography for server-tree interactions. */
public final class ServerTreeInteractionMediator {
  private static final int CHANNEL_DRAG_THRESHOLD_FALLBACK_PX = 5;

  public interface Context {
    void onTreeShowingChanged(boolean showing);

    boolean isSelectionBroadcastSuppressed();

    void emitSelection(TargetRef ref);

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    String owningServerIdForNode(DefaultMutableTreeNode node);

    boolean maybeHandleDisconnectedWarningClick(MouseEvent event);

    boolean maybeSelectRowFromLeftClick(MouseEvent event);

    TreePath treePathForRowHit(int x, int y);

    void withSuppressedSelectionBroadcast(Runnable task);

    void refreshNodeActionsEnabled();

    JPopupMenu buildPopupMenu(TreePath path);

    void prepareChannelDockDrag(MouseEvent event);

    void clearPreparedChannelDockDrag();

    ServerTreeMiddleDragReorderHandler.Context middleDragReorderContext();

    boolean startupSelectionCompleted();

    void markStartupSelectionCompleted();

    boolean isPathInCurrentTreeModel(TreePath path);

    String firstServerId();

    void selectStartupDefaultForServer(String serverId);

    TreePath defaultSelectionPath();
  }

  @FunctionalInterface
  public interface TreePathLookup {
    TreePath lookup(int x, int y);
  }

  public static Context context(
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
      Consumer<MouseEvent> prepareChannelDockDrag,
      Runnable clearPreparedChannelDockDrag,
      Supplier<ServerTreeMiddleDragReorderHandler.Context> middleDragReorderContext,
      BooleanSupplier startupSelectionCompleted,
      Runnable markStartupSelectionCompleted,
      Predicate<TreePath> isPathInCurrentTreeModel,
      Supplier<String> firstServerId,
      Consumer<String> selectStartupDefaultForServer,
      Supplier<TreePath> defaultSelectionPath) {
    Objects.requireNonNull(onTreeShowingChanged, "onTreeShowingChanged");
    Objects.requireNonNull(isSelectionBroadcastSuppressed, "isSelectionBroadcastSuppressed");
    Objects.requireNonNull(emitSelection, "emitSelection");
    Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    Objects.requireNonNull(
        maybeHandleDisconnectedWarningClick, "maybeHandleDisconnectedWarningClick");
    Objects.requireNonNull(maybeSelectRowFromLeftClick, "maybeSelectRowFromLeftClick");
    Objects.requireNonNull(treePathForRowHit, "treePathForRowHit");
    Objects.requireNonNull(withSuppressedSelectionBroadcast, "withSuppressedSelectionBroadcast");
    Objects.requireNonNull(refreshNodeActionsEnabled, "refreshNodeActionsEnabled");
    Objects.requireNonNull(buildPopupMenu, "buildPopupMenu");
    Objects.requireNonNull(prepareChannelDockDrag, "prepareChannelDockDrag");
    Objects.requireNonNull(clearPreparedChannelDockDrag, "clearPreparedChannelDockDrag");
    Objects.requireNonNull(middleDragReorderContext, "middleDragReorderContext");
    Objects.requireNonNull(startupSelectionCompleted, "startupSelectionCompleted");
    Objects.requireNonNull(markStartupSelectionCompleted, "markStartupSelectionCompleted");
    Objects.requireNonNull(isPathInCurrentTreeModel, "isPathInCurrentTreeModel");
    Objects.requireNonNull(firstServerId, "firstServerId");
    Objects.requireNonNull(selectStartupDefaultForServer, "selectStartupDefaultForServer");
    Objects.requireNonNull(defaultSelectionPath, "defaultSelectionPath");
    return new Context() {
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
      public void prepareChannelDockDrag(MouseEvent event) {
        prepareChannelDockDrag.accept(event);
      }

      @Override
      public void clearPreparedChannelDockDrag() {
        clearPreparedChannelDockDrag.run();
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
    };
  }

  private final JTree tree;
  private final ServerTreeServerActionOverlay serverActionOverlay;
  private final Context context;
  private boolean pendingChannelSelectionOnPress = false;
  private boolean channelPressTrackingActive = false;
  private boolean channelPressDragged = false;
  private Point channelPressPoint = null;
  private TreePath channelPressPath = null;

  public ServerTreeInteractionMediator(
      JTree tree, ServerTreeServerActionOverlay serverActionOverlay, Context context) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.serverActionOverlay = Objects.requireNonNull(serverActionOverlay, "serverActionOverlay");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void install() {
    tree.addHierarchyListener(
        event -> {
          if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
          context.onTreeShowingChanged(tree.isShowing());
        });

    tree.addTreeSelectionListener(
        event -> {
          DefaultMutableTreeNode node =
              (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
          if (!context.isSelectionBroadcastSuppressed() && node != null) {
            if (shouldDelayChannelSelectionUntilMouseRelease(node)) {
              pendingChannelSelectionOnPress = true;
            } else {
              emitSelectionForNode(node);
            }
          }
          tree.repaint();
        });

    MouseAdapter hoverServerActionListener =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent event) {
            serverActionOverlay.updateHovered(event);
          }

          @Override
          public void mouseDragged(MouseEvent event) {
            updateChannelPressDragState(event);
            serverActionOverlay.updateHovered(null);
          }

          @Override
          public void mouseExited(MouseEvent event) {
            // Keep prepared drag while left button is still held; clearing here can cancel an
            // in-progress drag before DnD recognizes it.
            if ((event.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) == 0) {
              context.clearPreparedChannelDockDrag();
              resetChannelPressState();
            }
            serverActionOverlay.updateHovered(null);
          }

          @Override
          public void mousePressed(MouseEvent event) {
            if (serverActionOverlay.maybeHandleActionClick(event)) return;
            if (context.maybeHandleDisconnectedWarningClick(event)) return;
            beginChannelPressTracking(event);
            context.maybeSelectRowFromLeftClick(event);
            context.prepareChannelDockDrag(event);
            serverActionOverlay.updateHovered(event);
          }

          @Override
          public void mouseReleased(MouseEvent event) {
            context.clearPreparedChannelDockDrag();
            handleChannelSelectionOnMouseRelease(event);
            serverActionOverlay.updateHovered(event);
          }
        };
    tree.addMouseMotionListener(hoverServerActionListener);
    tree.addMouseListener(hoverServerActionListener);

    MouseAdapter popupListener =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            maybeShowPopup(event);
          }

          @Override
          public void mouseReleased(MouseEvent event) {
            maybeShowPopup(event);
          }

          private void maybeShowPopup(MouseEvent event) {
            if (!event.isPopupTrigger()) return;

            int x = event.getX();
            int y = event.getY();
            TreePath path = context.treePathForRowHit(x, y);
            if (path == null) return;

            context.withSuppressedSelectionBroadcast(() -> tree.setSelectionPath(path));
            context.refreshNodeActionsEnabled();

            JPopupMenu menu = context.buildPopupMenu(path);
            if (menu == null || menu.getComponentCount() == 0) return;
            PopupMenuThemeSupport.prepareForDisplay(menu);
            menu.show(tree, x, y);
          }
        };
    tree.addMouseListener(popupListener);

    ServerTreeMiddleDragReorderHandler middleDragReorder =
        new ServerTreeMiddleDragReorderHandler(context.middleDragReorderContext());
    tree.addMouseListener(middleDragReorder);
    tree.addMouseMotionListener(middleDragReorder);

    SwingUtilities.invokeLater(this::applyStartupSelectionIfNeeded);
  }

  private void applyStartupSelectionIfNeeded() {
    if (context.startupSelectionCompleted()) return;
    TreePath existingSelection = tree.getSelectionPath();
    if (existingSelection != null && context.isPathInCurrentTreeModel(existingSelection)) {
      context.markStartupSelectionCompleted();
      return;
    }
    String firstServerId = context.firstServerId();
    if (!firstServerId.isBlank()) {
      context.selectStartupDefaultForServer(firstServerId);
      context.markStartupSelectionCompleted();
      return;
    }
    tree.setSelectionPath(context.defaultSelectionPath());
  }

  private void emitSelectionForNode(DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData) {
      if (nodeData.ref != null) {
        context.emitSelection(nodeData.ref);
        return;
      }
      if (context.isMonitorGroupNode(node)) {
        String serverId = context.owningServerIdForNode(node);
        if (!serverId.isBlank()) {
          context.emitSelection(TargetRef.monitorGroup(serverId));
        }
        return;
      }
      if (context.isInterceptorsGroupNode(node)) {
        String serverId = context.owningServerIdForNode(node);
        if (!serverId.isBlank()) {
          context.emitSelection(TargetRef.interceptorsGroup(serverId));
        }
      }
      return;
    }

    if (context.isMonitorGroupNode(node)) {
      String serverId = context.owningServerIdForNode(node);
      if (!serverId.isBlank()) {
        context.emitSelection(TargetRef.monitorGroup(serverId));
      }
      return;
    }
    if (context.isInterceptorsGroupNode(node)) {
      String serverId = context.owningServerIdForNode(node);
      if (!serverId.isBlank()) {
        context.emitSelection(TargetRef.interceptorsGroup(serverId));
      }
    }
  }

  private boolean shouldDelayChannelSelectionUntilMouseRelease(DefaultMutableTreeNode node) {
    return isChannelNode(node)
        && (channelPressTrackingActive || isSelectionTriggeredByLeftMousePress());
  }

  private boolean isSelectionTriggeredByLeftMousePress() {
    if (!(EventQueue.getCurrentEvent() instanceof MouseEvent mouseEvent)) return false;
    if (mouseEvent.getID() != MouseEvent.MOUSE_PRESSED) return false;
    return SwingUtilities.isLeftMouseButton(mouseEvent) && !mouseEvent.isPopupTrigger();
  }

  private boolean isChannelNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return false;
    return nodeData.ref != null && nodeData.ref.isChannel();
  }

  private void beginChannelPressTracking(MouseEvent event) {
    resetChannelPressState();
    if (event == null || event.isConsumed()) return;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return;

    TreePath path = context.treePathForRowHit(event.getX(), event.getY());
    if (path == null) return;
    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node) || !isChannelNode(node)) return;

    channelPressTrackingActive = true;
    channelPressPoint = event.getPoint();
    channelPressPath = path;
  }

  private void updateChannelPressDragState(MouseEvent event) {
    if (!channelPressTrackingActive || channelPressDragged || event == null) return;
    if ((event.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) == 0) return;
    if (channelPressPoint == null) {
      channelPressDragged = true;
      return;
    }
    if (channelPressPoint.distance(event.getPoint()) >= channelDragThresholdPx()) {
      channelPressDragged = true;
    }
  }

  private void handleChannelSelectionOnMouseRelease(MouseEvent event) {
    if (shouldEmitPendingChannelSelection(event)) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
      if (node != null && !context.isSelectionBroadcastSuppressed()) {
        emitSelectionForNode(node);
      }
    }
    resetChannelPressState();
  }

  private boolean shouldEmitPendingChannelSelection(MouseEvent event) {
    if (!pendingChannelSelectionOnPress) return false;
    if (event == null || event.isConsumed()) return false;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;
    if (!channelPressTrackingActive) return true;
    if (channelPressDragged) return false;
    if (channelPressPath == null) return true;
    TreePath releasePath = context.treePathForRowHit(event.getX(), event.getY());
    return Objects.equals(channelPressPath, releasePath);
  }

  private int channelDragThresholdPx() {
    try {
      return Math.max(1, DragSource.getDragThreshold());
    } catch (Exception ignored) {
      return CHANNEL_DRAG_THRESHOLD_FALLBACK_PX;
    }
  }

  private void resetChannelPressState() {
    pendingChannelSelectionOnPress = false;
    channelPressTrackingActive = false;
    channelPressDragged = false;
    channelPressPoint = null;
    channelPressPath = null;
  }
}
