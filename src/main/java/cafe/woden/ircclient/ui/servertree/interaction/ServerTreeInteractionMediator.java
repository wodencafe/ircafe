package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Binds tree listeners and startup-selection choreography for server-tree interactions. */
public final class ServerTreeInteractionMediator {

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

    ServerTreeMiddleDragReorderHandler.Context middleDragReorderContext();

    boolean startupSelectionCompleted();

    void markStartupSelectionCompleted();

    boolean isPathInCurrentTreeModel(TreePath path);

    String firstServerId();

    void selectStartupDefaultForServer(String serverId);

    TreePath defaultSelectionPath();
  }

  private final JTree tree;
  private final ServerTreeServerActionOverlay serverActionOverlay;
  private final Context context;

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
            emitSelectionForNode(node);
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
            serverActionOverlay.updateHovered(null);
          }

          @Override
          public void mouseExited(MouseEvent event) {
            serverActionOverlay.updateHovered(null);
          }

          @Override
          public void mousePressed(MouseEvent event) {
            if (serverActionOverlay.maybeHandleActionClick(event)) return;
            if (context.maybeHandleDisconnectedWarningClick(event)) return;
            context.maybeSelectRowFromLeftClick(event);
            serverActionOverlay.updateHovered(event);
          }

          @Override
          public void mouseReleased(MouseEvent event) {
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
}
