package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.swing.tree.DefaultMutableTreeNode;

/** Facade for built-in node visibility, layout ordering, and associated persistence operations. */
public final class ServerTreeBuiltInLayoutVisibilityFacade {

  private final ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator;
  private final ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator;
  private final ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator;
  private final ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator;
  private final Predicate<DefaultMutableTreeNode> isMonitorGroupNode;
  private final Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode;
  private final Predicate<DefaultMutableTreeNode> isOtherGroupNode;
  private final Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode;
  private final Function<DefaultMutableTreeNode, TargetRef> targetRefForNode;

  public ServerTreeBuiltInLayoutVisibilityFacade(
      ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator,
      ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator,
      ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator,
      ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode,
      Function<DefaultMutableTreeNode, TargetRef> targetRefForNode) {
    this.builtInVisibilityCoordinator =
        Objects.requireNonNull(builtInVisibilityCoordinator, "builtInVisibilityCoordinator");
    this.builtInLayoutCoordinator =
        Objects.requireNonNull(builtInLayoutCoordinator, "builtInLayoutCoordinator");
    this.rootSiblingOrderCoordinator =
        Objects.requireNonNull(rootSiblingOrderCoordinator, "rootSiblingOrderCoordinator");
    this.builtInLayoutOrchestrator =
        Objects.requireNonNull(builtInLayoutOrchestrator, "builtInLayoutOrchestrator");
    this.isMonitorGroupNode = Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    this.isInterceptorsGroupNode =
        Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    this.isOtherGroupNode = Objects.requireNonNull(isOtherGroupNode, "isOtherGroupNode");
    this.isPrivateMessagesGroupNode =
        Objects.requireNonNull(isPrivateMessagesGroupNode, "isPrivateMessagesGroupNode");
    this.targetRefForNode = Objects.requireNonNull(targetRefForNode, "targetRefForNode");
  }

  public void loadPersistedBuiltInNodesVisibility() {
    builtInVisibilityCoordinator.loadPersistedBuiltInNodesVisibility();
  }

  public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    return builtInVisibilityCoordinator.builtInNodesVisibility(serverId);
  }

  public RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout(String serverId) {
    return builtInLayoutCoordinator.layoutForServer(serverId);
  }

  public void rememberBuiltInLayout(
      String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    builtInLayoutCoordinator.rememberLayout(serverId, layout);
  }

  public RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder(String serverId) {
    return rootSiblingOrderCoordinator.orderForServer(serverId);
  }

  public void rememberRootSiblingOrder(
      String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
    rootSiblingOrderCoordinator.rememberOrder(serverId, order);
  }

  public void applyBuiltInNodesVisibilityGlobally(
      UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
    builtInVisibilityCoordinator.applyBuiltInNodesVisibilityGlobally(mutator);
  }

  public void applyBuiltInNodesVisibilityForServer(
      String serverId, ServerBuiltInNodesVisibility next, boolean persist, boolean syncUi) {
    builtInVisibilityCoordinator.applyBuiltInNodesVisibilityForServer(
        serverId, next, persist, syncUi);
  }

  public ServerBuiltInNodesVisibility defaultVisibility() {
    return builtInVisibilityCoordinator.defaultVisibility();
  }

  public void setDefaultVisibility(ServerBuiltInNodesVisibility next) {
    builtInVisibilityCoordinator.setDefaultVisibility(next);
  }

  public RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForRef(TargetRef ref) {
    return ServerTreeBuiltInLayoutCoordinator.nodeKindForRef(ref);
  }

  public RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
      DefaultMutableTreeNode node) {
    return builtInLayoutCoordinator.nodeKindForNode(
        node, isMonitorGroupNode, isInterceptorsGroupNode, targetRefForNode);
  }

  public RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
      DefaultMutableTreeNode node) {
    return rootSiblingOrderCoordinator.nodeKindForNode(
        node, isOtherGroupNode, isPrivateMessagesGroupNode, targetRefForNode);
  }

  public int rootBuiltInInsertIndex(ServerNodes serverNodes, int desiredIndex) {
    return builtInLayoutOrchestrator.rootBuiltInInsertIndex(serverNodes, desiredIndex);
  }

  public void applyBuiltInLayoutToTree(
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayout requestedLayout) {
    builtInLayoutOrchestrator.applyBuiltInLayoutToTree(serverNodes, requestedLayout);
  }

  public void applyRootSiblingOrderToTree(
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder requestedOrder) {
    builtInLayoutOrchestrator.applyRootSiblingOrderToTree(serverNodes, requestedOrder);
  }

  public void persistRootSiblingOrderFromTree(String serverId) {
    builtInLayoutOrchestrator.persistRootSiblingOrderFromTree(serverId);
  }

  public void persistBuiltInLayoutFromTree(String serverId) {
    builtInLayoutOrchestrator.persistBuiltInLayoutFromTree(serverId);
  }
}
