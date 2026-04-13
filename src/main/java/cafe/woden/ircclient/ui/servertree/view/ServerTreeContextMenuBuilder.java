package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds context menus for server tree nodes. */
@Component
@RequiredArgsConstructor
public final class ServerTreeContextMenuBuilder {

  public interface RoutingContext {
    boolean isServerNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    boolean isQuasselNetworkNode(DefaultMutableTreeNode node);

    boolean isQuasselEmptyStateNode(DefaultMutableTreeNode node);
  }

  public static RoutingContext routingContext(
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isQuasselNetworkNode,
      Predicate<DefaultMutableTreeNode> isQuasselEmptyStateNode) {
    Objects.requireNonNull(isServerNode, "isServerNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    Objects.requireNonNull(isQuasselNetworkNode, "isQuasselNetworkNode");
    Objects.requireNonNull(isQuasselEmptyStateNode, "isQuasselEmptyStateNode");
    return new RoutingContext() {
      @Override
      public boolean isServerNode(DefaultMutableTreeNode node) {
        return isServerNode.test(node);
      }

      @Override
      public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
        return isInterceptorsGroupNode.test(node);
      }

      @Override
      public boolean isQuasselNetworkNode(DefaultMutableTreeNode node) {
        return isQuasselNetworkNode.test(node);
      }

      @Override
      public boolean isQuasselEmptyStateNode(DefaultMutableTreeNode node) {
        return isQuasselEmptyStateNode.test(node);
      }
    };
  }

  public record Contexts(
      RoutingContext routingContext,
      ServerTreeServerNodeMenuBuilder.Context serverNodeContext,
      ServerTreeTargetNodeMenuBuilder.Context targetNodeContext,
      ServerTreeQuasselNetworkNodeMenuBuilder.Context quasselNetworkNodeContext) {}

  private final ServerTreeServerNodeMenuBuilder serverNodeMenuBuilder;
  private final ServerTreeTargetNodeMenuBuilder targetNodeMenuBuilder;
  private final ServerTreeQuasselNetworkNodeMenuBuilder quasselNetworkNodeMenuBuilder;

  public JPopupMenu build(Contexts contexts, TreePath path) {
    Contexts ctx = Objects.requireNonNull(contexts, "contexts");
    if (path == null) return null;

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return null;

    if (ctx.routingContext().isServerNode(node)) {
      return serverNodeMenuBuilder.buildServerNodeMenu(ctx.serverNodeContext(), node);
    }

    if (ctx.routingContext().isInterceptorsGroupNode(node)) {
      return serverNodeMenuBuilder.buildInterceptorsGroupMenu(ctx.serverNodeContext(), node);
    }

    if (ctx.routingContext().isQuasselNetworkNode(node)
        || ctx.routingContext().isQuasselEmptyStateNode(node)) {
      Object userObject = node.getUserObject();
      if (userObject instanceof ServerTreeQuasselNetworkNodeData networkNodeData) {
        return quasselNetworkNodeMenuBuilder.buildNetworkNodeMenu(
            ctx.quasselNetworkNodeContext(), networkNodeData);
      }
    }

    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData && nodeData.ref != null) {
      return targetNodeMenuBuilder.buildTargetNodeMenu(ctx.targetNodeContext(), nodeData);
    }

    return null;
  }
}
