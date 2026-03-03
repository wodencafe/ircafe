package cafe.woden.ircclient.ui.servertree.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ServerTreeInteractionMediatorChannelDockDragTest {

  @Test
  void installWiresPrepareAndClearForLeftMousePressRelease() {
    JTree tree = new JTree();
    StubContext context = new StubContext();
    ListenerBundle listeners = installMediator(tree, context);

    MouseEvent press =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    for (var listener : listeners.mouseListeners()) {
      listener.mousePressed(press);
    }

    assertNotNull(context.preparedEvent);

    MouseEvent release =
        new MouseEvent(
            tree, MouseEvent.MOUSE_RELEASED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    for (var listener : listeners.mouseListeners()) {
      listener.mouseReleased(release);
    }

    assertTrue(context.clearCount > 0);
  }

  @Test
  void channelSelectionFromClickEmitsOnMouseRelease() {
    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode channelNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(channelRef, "#ircafe"));
    root.add(channelNode);
    JTree tree = new JTree(root);
    TreePath channelPath = new TreePath(channelNode.getPath());
    StubContext context = new StubContext(tree, channelPath);
    ListenerBundle listeners = installMediator(tree, context);

    MouseEvent press =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    for (var listener : listeners.mouseListeners()) {
      listener.mousePressed(press);
    }
    assertTrue(context.emittedSelections.isEmpty());

    MouseEvent release =
        new MouseEvent(
            tree, MouseEvent.MOUSE_RELEASED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    for (var listener : listeners.mouseListeners()) {
      listener.mouseReleased(release);
    }

    assertEquals(List.of(channelRef), context.emittedSelections);
  }

  @Test
  void channelSelectionFromDragDoesNotEmitOnMouseRelease() {
    TargetRef channelRef = new TargetRef("libera", "#ircafe");
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode channelNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(channelRef, "#ircafe"));
    root.add(channelNode);
    JTree tree = new JTree(root);
    TreePath channelPath = new TreePath(channelNode.getPath());
    StubContext context = new StubContext(tree, channelPath);
    ListenerBundle listeners = installMediator(tree, context);

    MouseEvent press =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    for (var listener : listeners.mouseListeners()) {
      listener.mousePressed(press);
    }
    assertTrue(context.emittedSelections.isEmpty());

    MouseEvent drag =
        new MouseEvent(
            tree,
            MouseEvent.MOUSE_DRAGGED,
            0L,
            InputEvent.BUTTON1_DOWN_MASK,
            64,
            68,
            0,
            false,
            MouseEvent.BUTTON1);
    for (var listener : listeners.mouseMotionListeners()) {
      listener.mouseDragged(drag);
    }

    MouseEvent release =
        new MouseEvent(
            tree, MouseEvent.MOUSE_RELEASED, 0L, 0, 64, 68, 1, false, MouseEvent.BUTTON1);
    for (var listener : listeners.mouseListeners()) {
      listener.mouseReleased(release);
    }

    assertTrue(context.emittedSelections.isEmpty());
  }

  private static ListenerBundle installMediator(JTree tree, StubContext context) {
    Set<MouseListener> existingMouseListeners = Collections.newSetFromMap(new IdentityHashMap<>());
    existingMouseListeners.addAll(Arrays.asList(tree.getMouseListeners()));
    Set<MouseMotionListener> existingMouseMotionListeners =
        Collections.newSetFromMap(new IdentityHashMap<>());
    existingMouseMotionListeners.addAll(Arrays.asList(tree.getMouseMotionListeners()));

    ServerTreeInteractionMediator mediator =
        new ServerTreeInteractionMediator(
            tree, Mockito.mock(ServerTreeServerActionOverlay.class), context);
    mediator.install();

    MouseListener[] mouseListeners =
        Arrays.stream(tree.getMouseListeners())
            .filter(listener -> !existingMouseListeners.contains(listener))
            .toArray(MouseListener[]::new);
    MouseMotionListener[] mouseMotionListeners =
        Arrays.stream(tree.getMouseMotionListeners())
            .filter(listener -> !existingMouseMotionListeners.contains(listener))
            .toArray(MouseMotionListener[]::new);
    return new ListenerBundle(mouseListeners, mouseMotionListeners);
  }

  private record ListenerBundle(
      MouseListener[] mouseListeners, MouseMotionListener[] mouseMotionListeners) {}

  private static final class StubContext implements ServerTreeInteractionMediator.Context {
    private final JTree tree;
    private final TreePath rowPath;
    private MouseEvent preparedEvent;
    private int clearCount;
    private final List<TargetRef> emittedSelections = new ArrayList<>();
    private final ServerTreeMiddleDragReorderHandler.Context middleDragContext =
        Mockito.mock(ServerTreeMiddleDragReorderHandler.Context.class);

    private StubContext() {
      this(null, null);
    }

    private StubContext(JTree tree, TreePath rowPath) {
      this.tree = tree;
      this.rowPath = rowPath;
    }

    @Override
    public void onTreeShowingChanged(boolean showing) {}

    @Override
    public boolean isSelectionBroadcastSuppressed() {
      return false;
    }

    @Override
    public void emitSelection(TargetRef ref) {
      if (ref != null) {
        emittedSelections.add(ref);
      }
    }

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
      if (tree == null || rowPath == null) return false;
      if (event == null || event.isConsumed()) return false;
      if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;
      tree.setSelectionPath(rowPath);
      return true;
    }

    @Override
    public TreePath treePathForRowHit(int x, int y) {
      return rowPath;
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
