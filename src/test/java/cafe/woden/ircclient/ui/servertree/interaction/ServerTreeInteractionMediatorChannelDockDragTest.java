package cafe.woden.ircclient.ui.servertree.interaction;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ServerTreeInteractionMediatorChannelDockDragTest {

  @Test
  void installWiresPrepareAndClearForLeftMousePressRelease() {
    JTree tree = new JTree();
    StubContext context = new StubContext();
    Set<MouseListener> existingListeners = Collections.newSetFromMap(new IdentityHashMap<>());
    existingListeners.addAll(Arrays.asList(tree.getMouseListeners()));

    ServerTreeInteractionMediator mediator =
        new ServerTreeInteractionMediator(
            tree, Mockito.mock(ServerTreeServerActionOverlay.class), context);
    mediator.install();
    MouseListener[] mediatorListeners =
        Arrays.stream(tree.getMouseListeners())
            .filter(listener -> !existingListeners.contains(listener))
            .toArray(MouseListener[]::new);

    MouseEvent press =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    for (var listener : mediatorListeners) {
      listener.mousePressed(press);
    }

    assertNotNull(context.preparedEvent);

    MouseEvent release =
        new MouseEvent(
            tree, MouseEvent.MOUSE_RELEASED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    for (var listener : mediatorListeners) {
      listener.mouseReleased(release);
    }

    assertTrue(context.clearCount > 0);
  }

  private static final class StubContext implements ServerTreeInteractionMediator.Context {
    private MouseEvent preparedEvent;
    private int clearCount;
    private final ServerTreeMiddleDragReorderHandler.Context middleDragContext =
        Mockito.mock(ServerTreeMiddleDragReorderHandler.Context.class);

    @Override
    public void onTreeShowingChanged(boolean showing) {}

    @Override
    public boolean isSelectionBroadcastSuppressed() {
      return false;
    }

    @Override
    public void emitSelection(TargetRef ref) {}

    @Override
    public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
      return false;
    }

    @Override
    public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
      return false;
    }

    @Override
    public String owningServerIdForNode(DefaultMutableTreeNode node) {
      return "";
    }

    @Override
    public boolean maybeHandleDisconnectedWarningClick(MouseEvent event) {
      return false;
    }

    @Override
    public boolean maybeSelectRowFromLeftClick(MouseEvent event) {
      return false;
    }

    @Override
    public TreePath treePathForRowHit(int x, int y) {
      return null;
    }

    @Override
    public void withSuppressedSelectionBroadcast(Runnable task) {
      if (task != null) task.run();
    }

    @Override
    public void refreshNodeActionsEnabled() {}

    @Override
    public JPopupMenu buildPopupMenu(TreePath path) {
      return null;
    }

    @Override
    public void prepareChannelDockDrag(MouseEvent event) {
      preparedEvent = event;
    }

    @Override
    public void clearPreparedChannelDockDrag() {
      clearCount++;
    }

    @Override
    public ServerTreeMiddleDragReorderHandler.Context middleDragReorderContext() {
      return middleDragContext;
    }

    @Override
    public boolean startupSelectionCompleted() {
      return true;
    }

    @Override
    public void markStartupSelectionCompleted() {}

    @Override
    public boolean isPathInCurrentTreeModel(TreePath path) {
      return true;
    }

    @Override
    public String firstServerId() {
      return "";
    }

    @Override
    public void selectStartupDefaultForServer(String serverId) {}

    @Override
    public TreePath defaultSelectionPath() {
      return null;
    }
  }
}
