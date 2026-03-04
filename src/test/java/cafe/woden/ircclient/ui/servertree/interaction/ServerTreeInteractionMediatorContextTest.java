package cafe.woden.ircclient.ui.servertree.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ServerTreeInteractionMediatorContextTest {

  @Test
  void contextDelegatesMediatorOperations() {
    JTree tree = new JTree();
    MouseEvent event =
        new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, 0L, 0, 10, 12, 1, false, MouseEvent.BUTTON1);
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("node");
    TargetRef targetRef = new TargetRef("libera", "#ircafe");
    TreePath path = new TreePath(new Object[] {"root", "leaf"});
    JPopupMenu popupMenu = new JPopupMenu();
    ServerTreeMiddleDragReorderHandler.Context middleDragContext =
        Mockito.mock(ServerTreeMiddleDragReorderHandler.Context.class);
    AtomicReference<Boolean> treeShowing = new AtomicReference<>();
    AtomicBoolean selectionBroadcastSuppressed = new AtomicBoolean(true);
    AtomicReference<TargetRef> emittedSelection = new AtomicReference<>();
    AtomicBoolean withSuppressedSelectionBroadcast = new AtomicBoolean(false);
    AtomicBoolean refreshedNodeActionsEnabled = new AtomicBoolean(false);
    AtomicReference<MouseEvent> preparedChannelDockDrag = new AtomicReference<>();
    AtomicBoolean clearedPreparedChannelDockDrag = new AtomicBoolean(false);
    AtomicBoolean startupSelectionCompleted = new AtomicBoolean(false);
    AtomicBoolean markedStartupSelectionCompleted = new AtomicBoolean(false);
    AtomicReference<String> startupDefaultServer = new AtomicReference<>();

    ServerTreeInteractionMediator.Context context =
        ServerTreeInteractionMediator.context(
            treeShowing::set,
            selectionBroadcastSuppressed::get,
            emittedSelection::set,
            value -> value == node,
            value -> false,
            value -> "libera",
            value -> value == event,
            value -> value == event,
            (x, y) -> path,
            runnable -> {
              withSuppressedSelectionBroadcast.set(true);
              if (runnable != null) {
                runnable.run();
              }
            },
            () -> refreshedNodeActionsEnabled.set(true),
            value -> popupMenu,
            preparedChannelDockDrag::set,
            () -> clearedPreparedChannelDockDrag.set(true),
            () -> middleDragContext,
            startupSelectionCompleted::get,
            () -> markedStartupSelectionCompleted.set(true),
            value -> value == path,
            () -> "libera",
            startupDefaultServer::set,
            () -> path);

    context.onTreeShowingChanged(true);
    assertTrue(treeShowing.get());
    assertTrue(context.isSelectionBroadcastSuppressed());
    context.emitSelection(targetRef);
    assertSame(targetRef, emittedSelection.get());
    assertTrue(context.isMonitorGroupNode(node));
    assertFalse(context.isInterceptorsGroupNode(node));
    assertEquals("libera", context.owningServerIdForNode(node));
    assertTrue(context.maybeHandleDisconnectedWarningClick(event));
    assertTrue(context.maybeSelectRowFromLeftClick(event));
    assertSame(path, context.treePathForRowHit(10, 12));
    context.withSuppressedSelectionBroadcast(() -> {});
    assertTrue(withSuppressedSelectionBroadcast.get());
    context.refreshNodeActionsEnabled();
    assertTrue(refreshedNodeActionsEnabled.get());
    assertSame(popupMenu, context.buildPopupMenu(path));
    context.prepareChannelDockDrag(event);
    assertSame(event, preparedChannelDockDrag.get());
    context.clearPreparedChannelDockDrag();
    assertTrue(clearedPreparedChannelDockDrag.get());
    assertSame(middleDragContext, context.middleDragReorderContext());
    assertFalse(context.startupSelectionCompleted());
    context.markStartupSelectionCompleted();
    assertTrue(markedStartupSelectionCompleted.get());
    assertTrue(context.isPathInCurrentTreeModel(path));
    assertEquals("libera", context.firstServerId());
    context.selectStartupDefaultForServer("libera");
    assertEquals("libera", startupDefaultServer.get());
    assertSame(path, context.defaultSelectionPath());
  }
}
