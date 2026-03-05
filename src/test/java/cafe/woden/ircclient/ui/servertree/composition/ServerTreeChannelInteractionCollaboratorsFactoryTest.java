package cafe.woden.ircclient.ui.servertree.composition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelTargetOperations;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeChannelInteractionCollaboratorsFactoryTest {

  @Test
  void createBuildsChannelInteractionCollaboratorsAndTimerWiring() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);
    AtomicInteger typingTickCount = new AtomicInteger();

    ServerTreeChannelInteractionCollaborators collaborators =
        ServerTreeChannelInteractionCollaboratorsFactory.create(
            new ServerTreeChannelInteractionCollaboratorsFactory.Inputs(
                tree,
                model,
                new HashMap<>(),
                new HashSet<>(),
                100,
                8000,
                900,
                typingTickCount::incrementAndGet,
                () -> true,
                () -> true,
                __ -> {},
                __ -> {},
                __ -> null,
                __ -> {},
                __ -> null,
                __ -> null,
                __ -> null,
                __ -> false,
                __ -> {},
                __ -> {},
                mock(ServerTreeChannelQueryService.class),
                mock(ServerTreeChannelTargetOperations.class)));

    assertNotNull(collaborators.typingActivityTimer());
    assertNotNull(collaborators.typingActivityManager());
    assertNotNull(collaborators.channelDisconnectStateManager());
    assertNotNull(collaborators.targetSelectionCoordinator());
    assertNotNull(collaborators.unreadStateCoordinator());
    assertNotNull(collaborators.channelInteractionApi());
    assertTrue(collaborators.typingActivityTimer().isRepeats());

    for (var listener : collaborators.typingActivityTimer().getActionListeners()) {
      listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "tick"));
    }
    assertEquals(1, typingTickCount.get());
  }
}
