package cafe.woden.ircclient.ui.servertree.composition;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Color;
import java.util.HashMap;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeLifecycleSettingsCollaboratorsFactoryTest {

  @Test
  void createBuildsLifecycleAndSettingsCollaborators() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);

    ServerTreeLifecycleSettingsCollaborators collaborators =
        new ServerTreeLifecycleSettingsCollaboratorsFactory(new ServerTreeServerNodeBuilder())
            .create(
                new ServerTreeLifecycleSettingsCollaboratorsFactory.Inputs(
                    "Channel List",
                    "Filters",
                    "Ignores",
                    "DCC Transfers",
                    "Log Viewer",
                    "Monitor",
                    "Interceptors",
                    serverId -> serverId == null ? "" : serverId.trim(),
                    new HashMap<>(),
                    new ServerTreeRuntimeState(16, __ -> {}),
                    mock(ServerTreeChannelStateCoordinator.class),
                    mock(ServerTreeServerParentResolver.class),
                    __ -> ServerBuiltInNodesVisibility.defaults(),
                    () -> true,
                    mock(ServerTreeStatusLabelManager.class),
                    null,
                    null,
                    new HashMap<>(),
                    __ -> ServerTreeBuiltInLayout.defaults(),
                    __ -> ServerTreeRootSiblingOrder.defaults(),
                    (__1, __2) -> {},
                    (__1, __2) -> {},
                    model,
                    root,
                    tree,
                    mock(ServerTreeNodeBadgeUpdater.class),
                    mock(
                        cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions
                            .class),
                    mock(ServerTreeServerStateCleaner.class),
                    mock(ServerTreeNetworkGroupManager.class),
                    mock(UiSettingsBus.class),
                    null,
                    null,
                    () -> true,
                    __ -> {},
                    () -> {},
                    __ -> {},
                    __ -> {},
                    __ -> {},
                    (Color __) -> {},
                    (Color __) -> {},
                    () -> {},
                    () -> {},
                    100));

    assertNotNull(collaborators.serverRootLifecycleManager());
    assertNotNull(collaborators.serverLifecycleFacade());
    assertNotNull(collaborators.settingsSynchronizer());
  }
}
