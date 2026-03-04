package cafe.woden.ircclient.ui.servertree.interaction;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import io.github.andrewauclair.moderndocking.Dockable;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

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
}
