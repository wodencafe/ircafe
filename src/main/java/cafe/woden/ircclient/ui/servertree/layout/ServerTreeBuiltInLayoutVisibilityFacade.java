package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Facade for built-in node visibility, layout ordering, and associated persistence operations. */
@Component
public final class ServerTreeBuiltInLayoutVisibilityFacade {

  public interface Context {
    ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator();

    ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator();

    ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator();

    ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator();

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    boolean isOtherGroupNode(DefaultMutableTreeNode node);

    boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node);

    TargetRef targetRefForNode(DefaultMutableTreeNode node);
  }

  public static Context context(
      ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator,
      ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator,
      ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator,
      ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode,
      Function<DefaultMutableTreeNode, TargetRef> targetRefForNode) {
    Objects.requireNonNull(builtInVisibilityCoordinator, "builtInVisibilityCoordinator");
    Objects.requireNonNull(builtInLayoutCoordinator, "builtInLayoutCoordinator");
    Objects.requireNonNull(rootSiblingOrderCoordinator, "rootSiblingOrderCoordinator");
    Objects.requireNonNull(builtInLayoutOrchestrator, "builtInLayoutOrchestrator");
    Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    Objects.requireNonNull(isOtherGroupNode, "isOtherGroupNode");
    Objects.requireNonNull(isPrivateMessagesGroupNode, "isPrivateMessagesGroupNode");
    Objects.requireNonNull(targetRefForNode, "targetRefForNode");
    return new Context() {
      @Override
      public ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator() {
        return builtInVisibilityCoordinator;
      }

      @Override
      public ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator() {
        return builtInLayoutCoordinator;
      }

      @Override
      public ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator() {
        return rootSiblingOrderCoordinator;
      }

      @Override
      public ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator() {
        return builtInLayoutOrchestrator;
      }

      @Override
      public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
        return isMonitorGroupNode.test(node);
      }

      @Override
      public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
        return isInterceptorsGroupNode.test(node);
      }

      @Override
      public boolean isOtherGroupNode(DefaultMutableTreeNode node) {
        return isOtherGroupNode.test(node);
      }

      @Override
      public boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {
        return isPrivateMessagesGroupNode.test(node);
      }

      @Override
      public TargetRef targetRefForNode(DefaultMutableTreeNode node) {
        return targetRefForNode.apply(node);
      }
    };
  }

  public void loadPersistedBuiltInNodesVisibility(Context context) {
    Objects.requireNonNull(context, "context")
        .builtInVisibilityCoordinator()
        .loadPersistedBuiltInNodesVisibility();
  }

  public ServerBuiltInNodesVisibility builtInNodesVisibility(Context context, String serverId) {
    return Objects.requireNonNull(context, "context")
        .builtInVisibilityCoordinator()
        .builtInNodesVisibility(serverId);
  }

  public ServerTreeBuiltInLayout builtInLayout(Context context, String serverId) {
    return Objects.requireNonNull(context, "context")
        .builtInLayoutCoordinator()
        .layoutForServer(serverId);
  }

  public void rememberBuiltInLayout(
      Context context, String serverId, ServerTreeBuiltInLayout layout) {
    Objects.requireNonNull(context, "context")
        .builtInLayoutCoordinator()
        .rememberLayout(serverId, layout);
  }

  public ServerTreeRootSiblingOrder rootSiblingOrder(Context context, String serverId) {
    return Objects.requireNonNull(context, "context")
        .rootSiblingOrderCoordinator()
        .orderForServer(serverId);
  }

  public void rememberRootSiblingOrder(
      Context context, String serverId, ServerTreeRootSiblingOrder order) {
    Objects.requireNonNull(context, "context")
        .rootSiblingOrderCoordinator()
        .rememberOrder(serverId, order);
  }

  public void applyBuiltInNodesVisibilityGlobally(
      Context context, UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
    Objects.requireNonNull(context, "context")
        .builtInVisibilityCoordinator()
        .applyBuiltInNodesVisibilityGlobally(mutator);
  }

  public void applyBuiltInNodesVisibilityForServer(
      Context context,
      String serverId,
      ServerBuiltInNodesVisibility next,
      boolean persist,
      boolean syncUi) {
    Objects.requireNonNull(context, "context")
        .builtInVisibilityCoordinator()
        .applyBuiltInNodesVisibilityForServer(serverId, next, persist, syncUi);
  }

  public ServerBuiltInNodesVisibility defaultVisibility(Context context) {
    return Objects.requireNonNull(context, "context")
        .builtInVisibilityCoordinator()
        .defaultVisibility();
  }

  public void setDefaultVisibility(Context context, ServerBuiltInNodesVisibility next) {
    Objects.requireNonNull(context, "context")
        .builtInVisibilityCoordinator()
        .setDefaultVisibility(next);
  }

  public ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForRef(TargetRef ref) {
    return ServerTreeBuiltInLayoutCoordinator.nodeKindForRef(ref);
  }

  public ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
      Context context, DefaultMutableTreeNode node) {
    Context in = Objects.requireNonNull(context, "context");
    return in.builtInLayoutCoordinator()
        .nodeKindForNode(
            node, in::isMonitorGroupNode, in::isInterceptorsGroupNode, in::targetRefForNode);
  }

  public ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
      Context context, DefaultMutableTreeNode node) {
    Context in = Objects.requireNonNull(context, "context");
    return in.rootSiblingOrderCoordinator()
        .nodeKindForNode(
            node, in::isOtherGroupNode, in::isPrivateMessagesGroupNode, in::targetRefForNode);
  }

  public int rootBuiltInInsertIndex(Context context, ServerNodes serverNodes, int desiredIndex) {
    return Objects.requireNonNull(context, "context")
        .builtInLayoutOrchestrator()
        .rootBuiltInInsertIndex(serverNodes, desiredIndex);
  }

  public void applyBuiltInLayoutToTree(
      Context context, ServerNodes serverNodes, ServerTreeBuiltInLayout requestedLayout) {
    Objects.requireNonNull(context, "context")
        .builtInLayoutOrchestrator()
        .applyBuiltInLayoutToTree(serverNodes, requestedLayout);
  }

  public void applyRootSiblingOrderToTree(
      Context context, ServerNodes serverNodes, ServerTreeRootSiblingOrder requestedOrder) {
    Objects.requireNonNull(context, "context")
        .builtInLayoutOrchestrator()
        .applyRootSiblingOrderToTree(serverNodes, requestedOrder);
  }

  public void persistRootSiblingOrderFromTree(Context context, String serverId) {
    Objects.requireNonNull(context, "context")
        .builtInLayoutOrchestrator()
        .persistRootSiblingOrderFromTree(serverId);
  }

  public void persistBuiltInLayoutFromTree(Context context, String serverId) {
    Objects.requireNonNull(context, "context")
        .builtInLayoutOrchestrator()
        .persistBuiltInLayoutFromTree(serverId);
  }
}
