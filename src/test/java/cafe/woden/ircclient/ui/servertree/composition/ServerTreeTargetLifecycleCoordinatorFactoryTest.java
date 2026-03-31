package cafe.woden.ircclient.ui.servertree.composition;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetLifecycleCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class ServerTreeTargetLifecycleCoordinatorFactoryTest {

  @Test
  void createBuildsTargetLifecycleCoordinator() {
    ServerTreeTargetLifecycleCoordinator coordinator =
        ServerTreeTargetLifecycleCoordinatorFactory.create(
            new ServerTreeTargetLifecycleCoordinatorFactory.Inputs(
                new HashMap<>(),
                new HashMap<>(),
                null,
                mock(ServerTreeEnsureNodeParentResolver.class),
                mock(ServerTreeEnsureNodeLeafInserter.class),
                mock(ServerTreeTargetNodePolicy.class),
                mock(ServerTreeTargetSnapshotProvider.class),
                mock(ServerTreeTargetRemovalStateCoordinator.class),
                mock(ServerTreeTargetNodeRemovalMutator.class),
                () -> true,
                __ -> {},
                __ -> "",
                (__1, __2) -> {},
                () -> {},
                () -> true,
                __ -> {},
                __ -> ServerBuiltInNodesVisibility.defaults(),
                __ -> null,
                __ -> ServerTreeBuiltInLayoutNode.SERVER,
                __ -> ServerTreeBuiltInLayout.defaults(),
                __ -> ServerTreeRootSiblingOrder.defaults(),
                (__1, __2) -> null,
                __ -> null,
                (ServerNodes __1, ServerTreeBuiltInLayout __2) -> {},
                (ServerNodes __1, ServerTreeRootSiblingOrder __2) -> {},
                __ -> {},
                __ -> false,
                () -> false,
                (__1, __2) -> {},
                __ -> {},
                __ -> {},
                __ -> {},
                serverId -> serverId == null ? "" : serverId.trim(),
                __ -> {},
                () -> {}));

    assertNotNull(coordinator);
  }
}
