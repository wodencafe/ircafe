package cafe.woden.ircclient.ui.servertree.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import io.github.andrewauclair.moderndocking.Dockable;
import java.util.function.Function;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ServerTreeInteractionSetupCoordinatorTest {

  @Test
  void createBuildsInteractionCollaboratorsAndDelegatesLifecycleOperations() {
    ServerTreeInteractionWiringFactory wiringFactory =
        mock(ServerTreeInteractionWiringFactory.class);
    ServerTreeMiddleDragReorderHandler.Context middleDragContext =
        mock(ServerTreeMiddleDragReorderHandler.Context.class);
    ServerTreePinnedDockDragController pinnedDockDragController =
        mock(ServerTreePinnedDockDragController.class);
    ServerTreeInteractionMediator interactionMediator = mock(ServerTreeInteractionMediator.class);
    ServerTreeInteractionWiringFactory.MiddleDragInputs middleDragInputs =
        new ServerTreeInteractionWiringFactory.MiddleDragInputs(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null);
    ServerTreeInteractionWiringFactory.PinnedDockDragInputs pinnedDockDragInputs =
        new ServerTreeInteractionWiringFactory.PinnedDockDragInputs(null, null);

    when(wiringFactory.createMiddleDragReorderContext(middleDragInputs))
        .thenReturn(middleDragContext);
    when(wiringFactory.createPinnedDockDragController(pinnedDockDragInputs))
        .thenReturn(pinnedDockDragController);
    when(wiringFactory.createInteractionMediator(any())).thenReturn(interactionMediator);

    ServerTreeInteractionSetupCoordinator setupCoordinator =
        ServerTreeInteractionSetupCoordinator.create(
            wiringFactory, middleDragInputs, pinnedDockDragInputs, (pinned, middle) -> null);

    Function<TargetRef, Dockable> provider = ref -> null;
    setupCoordinator.install();
    setupCoordinator.setPinnedDockableProvider(provider);
    setupCoordinator.clearPreparedChannelDockDrag();

    verify(wiringFactory).createMiddleDragReorderContext(middleDragInputs);
    verify(wiringFactory).createPinnedDockDragController(pinnedDockDragInputs);
    verify(wiringFactory).createInteractionMediator(null);
    verify(interactionMediator).install();
    verify(pinnedDockDragController).setPinnedDockableProvider(provider);
    verify(pinnedDockDragController).clearPreparedChannelDockDrag();
  }

  @Test
  void createInputsBuildsSetupCoordinatorWithWiredLifecycleDelegates() {
    ServerTreeInteractionWiringFactory interactionWiringFactory =
        mock(ServerTreeInteractionWiringFactory.class);
    ServerTreeMiddleDragReorderHandler.Context middleDragContext =
        mock(ServerTreeMiddleDragReorderHandler.Context.class);
    ServerTreePinnedDockDragController pinnedDockDragController =
        mock(ServerTreePinnedDockDragController.class);
    ServerTreeInteractionMediator interactionMediator = mock(ServerTreeInteractionMediator.class);

    when(interactionWiringFactory.createMiddleDragReorderContext(any()))
        .thenReturn(middleDragContext);
    when(interactionWiringFactory.createPinnedDockDragController(any()))
        .thenReturn(pinnedDockDragController);
    when(interactionWiringFactory.createInteractionMediator(any())).thenReturn(interactionMediator);

    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);
    ServerTreeDragReorderSupport dragReorderSupport = mock(ServerTreeDragReorderSupport.class);
    ServerTreeServerActionOverlay serverActionOverlay = mock(ServerTreeServerActionOverlay.class);

    ServerTreeInteractionSetupCoordinator setupCoordinator =
        ServerTreeInteractionSetupCoordinator.create(
            new ServerTreeInteractionSetupCoordinator.Inputs(
                interactionWiringFactory,
                tree,
                model,
                dragReorderSupport,
                __ -> false,
                __ -> "libera",
                __ -> null,
                __ -> RuntimeConfigStore.ServerTreeRootSiblingNode.CHANNEL_LIST,
                __ -> RuntimeConfigStore.ServerTreeBuiltInLayoutNode.SERVER,
                (__1, __2) -> 0,
                __ -> {},
                __ -> {},
                __ -> {},
                Runnable::run,
                () -> {},
                (x, y) -> new TargetRef("libera", "#ircafe"),
                serverActionOverlay,
                __ -> {},
                () -> false,
                __ -> {},
                __ -> false,
                __ -> false,
                __ -> false,
                __ -> false,
                (x, y) -> null,
                __ -> new JPopupMenu(),
                () -> true,
                () -> {},
                __ -> true,
                () -> "libera",
                __ -> {},
                () -> null));

    Function<TargetRef, Dockable> provider = __ -> null;
    setupCoordinator.install();
    setupCoordinator.setPinnedDockableProvider(provider);
    setupCoordinator.clearPreparedChannelDockDrag();

    ArgumentCaptor<ServerTreeInteractionWiringFactory.MediatorInputs> mediatorInputsCaptor =
        ArgumentCaptor.forClass(ServerTreeInteractionWiringFactory.MediatorInputs.class);
    verify(interactionWiringFactory).createMiddleDragReorderContext(any());
    verify(interactionWiringFactory).createPinnedDockDragController(any());
    verify(interactionWiringFactory).createInteractionMediator(mediatorInputsCaptor.capture());
    verify(interactionMediator).install();
    verify(pinnedDockDragController).setPinnedDockableProvider(provider);
    verify(pinnedDockDragController).clearPreparedChannelDockDrag();
    assertEquals(tree, mediatorInputsCaptor.getValue().tree());
  }
}
