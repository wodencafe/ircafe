package cafe.woden.ircclient.ui.servertree.interaction;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import io.github.andrewauclair.moderndocking.Dockable;
import java.util.function.Function;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeInteractionSetupCoordinatorTest {

  @Test
  void constructorDelegatesLifecycleOperations() {
    ServerTreePinnedDockDragController pinnedDockDragController =
        mock(ServerTreePinnedDockDragController.class);
    ServerTreeInteractionMediator interactionMediator = mock(ServerTreeInteractionMediator.class);
    ServerTreeInteractionSetupCoordinator setupCoordinator =
        new ServerTreeInteractionSetupCoordinator(pinnedDockDragController, interactionMediator);

    Function<TargetRef, Dockable> provider = ref -> null;
    setupCoordinator.install();
    setupCoordinator.setPinnedDockableProvider(provider);
    setupCoordinator.clearPreparedChannelDockDrag();

    verify(interactionMediator).install();
    verify(pinnedDockDragController).setPinnedDockableProvider(provider);
    verify(pinnedDockDragController).clearPreparedChannelDockDrag();
  }

  @Test
  void createInputsBuildsSetupCoordinatorWithWiredLifecycleDelegates() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);
    ServerTreeDragReorderSupport dragReorderSupport = mock(ServerTreeDragReorderSupport.class);
    ServerTreeServerActionOverlay serverActionOverlay = mock(ServerTreeServerActionOverlay.class);

    ServerTreeInteractionSetupCoordinator setupCoordinator =
        ServerTreeInteractionSetupCoordinator.create(
            new ServerTreeInteractionSetupCoordinator.Inputs(
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
    assertNotNull(setupCoordinator);
  }
}
