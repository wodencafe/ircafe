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
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeQuasselNetworkNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTargetNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipTextPolicy;
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
        new ServerTreeViewInteractionCollaboratorsFactory(
                new ServerTreeContextMenuBuilder(
                    new ServerTreeServerNodeMenuBuilder(),
                    new ServerTreeTargetNodeMenuBuilder(),
                    new ServerTreeQuasselNetworkNodeMenuBuilder()),
                new ServerTreeTooltipResolver(),
                new ServerTreeTooltipTextPolicy())
            .create(
                new ServerTreeViewInteractionCollaboratorsFactory.Inputs(
                    tree,
                    mock(ServerTreeRowInteractionHandler.class),
                    mock(ServerTreeUiHooks.class),
                    mock(ServerTreeNodeAccess.class),
                    mock(ServerTreeNetworkGroupManager.class),
                    mock(ServerTreeNodeClassifier.class),
                    mock(ServerTreeNodeClassifier.Context.class),
                    mock(ServerTreeRuntimeState.class),
                    mock(ServerTreeServerLabelPolicy.class),
                    mock(ServerTreeServerLabelPolicy.Context.class),
                    new HashMap<>(),
                    __ -> "",
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
                    mock(ServerTreeInterceptorActions.Context.class),
                    null,
                    new javax.swing.JPanel(),
                    null,
                    mock(ServerTreeNodeBadgeUpdater.class),
                    mock(ServerTreeNodeBadgeUpdater.Context.class),
                    mock(ServerTreeBouncerDetachPolicy.class),
                    mock(ServerTreeBouncerDetachPolicy.Context.class),
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
                    __ -> false,
                    (serverId, networkToken) -> ""));

    assertNotNull(collaborators.tooltipProvider());
    assertNotNull(collaborators.tooltipResolver());
    assertNotNull(collaborators.contextMenuBuilder());
  }
}
