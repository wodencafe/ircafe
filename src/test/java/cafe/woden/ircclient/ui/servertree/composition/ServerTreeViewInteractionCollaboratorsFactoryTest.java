package cafe.woden.ircclient.ui.servertree.composition;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.ui.servertree.ServerTreeUiHooks;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeNodeAccess;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestStreams;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeViewInteractionCollaboratorsFactoryTest {

  @Test
  void createBuildsTooltipResolverAndContextMenuCollaborators() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);

    ServerTreeViewInteractionCollaborators collaborators =
        ServerTreeViewInteractionCollaboratorsFactory.create(
            new ServerTreeViewInteractionCollaboratorsFactory.Inputs(
                tree,
                mock(ServerTreeRowInteractionHandler.class),
                mock(ServerTreeUiHooks.class),
                mock(ServerTreeNodeAccess.class),
                mock(ServerTreeNetworkGroupManager.class),
                mock(ServerTreeNodeClassifier.class),
                mock(ServerTreeRuntimeState.class),
                mock(ServerTreeServerLabelPolicy.class),
                new HashMap<>(),
                new HashMap<>(),
                Map.of(),
                Map.of(),
                () -> true,
                mock(ServerTreeServerActionOverlay.class),
                null,
                () -> null,
                () -> null,
                __ -> {},
                mock(ServerTreeRequestEmitter.class),
                null,
                mock(ServerTreeInterceptorActions.class),
                null,
                new javax.swing.JPanel(),
                null,
                mock(ServerTreeNodeBadgeUpdater.class),
                mock(ServerTreeBouncerDetachPolicy.class),
                __ -> false,
                __ -> false,
                (ref, enabled) -> {},
                __ -> false,
                (ref, enabled) -> {},
                __ -> false,
                (ref, enabled) -> {},
                mock(ServerTreeRequestStreams.class),
                __ -> false,
                __ -> false,
                __ -> false,
                (serverId, networkToken) -> ""));

    assertNotNull(collaborators.tooltipProvider());
    assertNotNull(collaborators.tooltipResolver());
    assertNotNull(collaborators.contextMenuBuilder());
  }
}
