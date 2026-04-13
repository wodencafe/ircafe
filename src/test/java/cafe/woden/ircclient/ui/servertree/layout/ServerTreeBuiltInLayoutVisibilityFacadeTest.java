package cafe.woden.ircclient.ui.servertree.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeBuiltInLayoutVisibilityFacadeTest {

  @Test
  void builtInAndRootNodeKindClassificationUsesPredicatesAndRefs() {
    Harness harness = newHarness(new HashSet<>(), new AtomicInteger());

    assertEquals(
        ServerTreeBuiltInLayoutNode.MONITOR,
        harness
            .facade()
            .builtInLayoutNodeKindForNode(
                harness.context(), new DefaultMutableTreeNode("monitor")));
    assertEquals(
        ServerTreeBuiltInLayoutNode.INTERCEPTORS,
        harness
            .facade()
            .builtInLayoutNodeKindForNode(
                harness.context(), new DefaultMutableTreeNode("interceptors")));
    assertEquals(
        ServerTreeBuiltInLayoutNode.LOG_VIEWER,
        harness.facade().builtInLayoutNodeKindForRef(TargetRef.logViewer("s")));

    assertEquals(
        ServerTreeRootSiblingNode.OTHER,
        harness
            .facade()
            .rootSiblingNodeKindForNode(harness.context(), new DefaultMutableTreeNode("other")));
    assertEquals(
        ServerTreeRootSiblingNode.PRIVATE_MESSAGES,
        harness
            .facade()
            .rootSiblingNodeKindForNode(harness.context(), new DefaultMutableTreeNode("pm")));
    assertEquals(
        ServerTreeRootSiblingNode.CHANNEL_LIST,
        harness
            .facade()
            .rootSiblingNodeKindForNode(
                harness.context(), new DefaultMutableTreeNode(TargetRef.channelList("s"))));
  }

  @Test
  void applyBuiltInVisibilityGloballyUpdatesKnownServersAndSyncsOnce() {
    Set<String> serverIds = new HashSet<>(Set.of("libera", "oftc"));
    AtomicInteger syncCalls = new AtomicInteger();
    Harness harness = newHarness(serverIds, syncCalls);

    harness
        .facade()
        .applyBuiltInNodesVisibilityGlobally(
            harness.context(), visibility -> visibility.withNotifications(false));

    assertFalse(
        harness.facade().builtInNodesVisibility(harness.context(), "libera").notifications());
    assertFalse(harness.facade().builtInNodesVisibility(harness.context(), "oftc").notifications());
    assertEquals(1, syncCalls.get());
  }

  @Test
  void rememberLayoutAndRootSiblingOrderRoundTripsThroughFacade() {
    Harness harness = newHarness(new HashSet<>(), new AtomicInteger());

    ServerTreeBuiltInLayout layout =
        new ServerTreeBuiltInLayout(
            List.of(ServerTreeBuiltInLayoutNode.SERVER),
            List.of(ServerTreeBuiltInLayoutNode.LOG_VIEWER));
    ServerTreeRootSiblingOrder order =
        new ServerTreeRootSiblingOrder(
            List.of(ServerTreeRootSiblingNode.OTHER, ServerTreeRootSiblingNode.CHANNEL_LIST));

    harness.facade().rememberBuiltInLayout(harness.context(), "libera", layout);
    harness.facade().rememberRootSiblingOrder(harness.context(), "libera", order);

    assertTrue(
        harness
            .facade()
            .builtInLayout(harness.context(), "libera")
            .rootOrder()
            .contains(ServerTreeBuiltInLayoutNode.SERVER));
    assertEquals(
        ServerTreeRootSiblingNode.OTHER,
        harness.facade().rootSiblingOrder(harness.context(), "libera").order().get(0));
  }

  @Test
  void defaultVisibilityCanBeUpdatedThroughFacade() {
    Harness harness = newHarness(new HashSet<>(), new AtomicInteger());
    ServerBuiltInNodesVisibility next =
        new ServerBuiltInNodesVisibility(false, false, false, false, false);

    harness.facade().setDefaultVisibility(harness.context(), next);

    assertEquals(next, harness.facade().defaultVisibility(harness.context()));
    assertEquals(next, harness.facade().builtInNodesVisibility(harness.context(), "unknown"));
  }

  private static Harness newHarness(Set<String> serverIds, AtomicInteger syncCalls) {
    ServerTreeBuiltInVisibilityCoordinator visibilityCoordinator =
        new ServerTreeBuiltInVisibilityCoordinator(
            null,
            new ServerTreeBuiltInVisibilityCoordinator.Context() {
              @Override
              public String normalizeServerId(String serverId) {
                return serverId == null ? "" : serverId.trim();
              }

              @Override
              public Set<String> currentServerIds() {
                return serverIds;
              }

              @Override
              public void syncUiLeafVisibility() {
                syncCalls.incrementAndGet();
              }
            });
    ServerTreeBuiltInLayoutCoordinator layoutCoordinator =
        new ServerTreeBuiltInLayoutCoordinator(null);
    ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator =
        new ServerTreeRootSiblingOrderCoordinator(null);
    ServerTreeLayoutPersistenceCoordinator layoutPersistenceCoordinator =
        new ServerTreeLayoutPersistenceCoordinator(
            new ServerTreeLayoutPersistenceCoordinator.Context() {
              @Override
              public ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
                  DefaultMutableTreeNode node) {
                return null;
              }

              @Override
              public ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
                  DefaultMutableTreeNode node) {
                return null;
              }

              @Override
              public ServerTreeRootSiblingOrder currentRootSiblingOrder(String serverId) {
                return ServerTreeRootSiblingOrder.defaults();
              }

              @Override
              public ServerTreeBuiltInLayout currentBuiltInLayout(String serverId) {
                return ServerTreeBuiltInLayout.defaults();
              }

              @Override
              public void persistRootSiblingOrder(
                  String serverId, ServerTreeRootSiblingOrder order) {}

              @Override
              public void persistBuiltInLayout(String serverId, ServerTreeBuiltInLayout layout) {}
            });
    Map<String, ServerNodes> nodesByServer = new HashMap<>();
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    ServerTreeBuiltInLayoutOrchestrator layoutOrchestrator =
        new ServerTreeBuiltInLayoutOrchestrator(
            new ServerTreeLayoutApplier(),
            layoutPersistenceCoordinator,
            new ServerTreeBuiltInLayoutOrchestrator.Context() {
              @Override
              public String normalizeServerId(String serverId) {
                return serverId == null ? "" : serverId.trim();
              }

              @Override
              public ServerNodes serverNodes(String serverId) {
                return nodesByServer.get(serverId);
              }

              @Override
              public DefaultMutableTreeNode leafNode(TargetRef ref) {
                return leaves.get(ref);
              }

              @Override
              public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
                return visibilityCoordinator.builtInNodesVisibility(serverId);
              }

              @Override
              public ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
                  DefaultMutableTreeNode node) {
                if (node == null) return null;
                Object userObject = node.getUserObject();
                if ("other".equals(userObject)) {
                  return ServerTreeRootSiblingNode.OTHER;
                }
                if ("pm".equals(userObject)) {
                  return ServerTreeRootSiblingNode.PRIVATE_MESSAGES;
                }
                return null;
              }

              @Override
              public void nodeStructureChanged(DefaultMutableTreeNode node) {}
            });

    ServerTreeBuiltInLayoutVisibilityFacade facade = new ServerTreeBuiltInLayoutVisibilityFacade();
    ServerTreeBuiltInLayoutVisibilityFacade.Context context =
        ServerTreeBuiltInLayoutVisibilityFacade.context(
            visibilityCoordinator,
            layoutCoordinator,
            rootSiblingOrderCoordinator,
            layoutOrchestrator,
            node -> node != null && "monitor".equals(node.getUserObject()),
            node -> node != null && "interceptors".equals(node.getUserObject()),
            node -> node != null && "other".equals(node.getUserObject()),
            node -> node != null && "pm".equals(node.getUserObject()),
            node -> {
              if (node == null) return null;
              Object userObject = node.getUserObject();
              if (userObject instanceof TargetRef ref) {
                return ref;
              }
              return null;
            });
    return new Harness(facade, context);
  }

  private record Harness(
      ServerTreeBuiltInLayoutVisibilityFacade facade,
      ServerTreeBuiltInLayoutVisibilityFacade.Context context) {}
}
