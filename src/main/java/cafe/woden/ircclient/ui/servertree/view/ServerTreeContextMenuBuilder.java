package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Builds context menus for server tree nodes. */
public final class ServerTreeContextMenuBuilder {

  public interface RoutingContext {
    boolean isServerNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);
  }

  public static RoutingContext routingContext(
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode) {
    Objects.requireNonNull(isServerNode, "isServerNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    return new RoutingContext() {
      @Override
      public boolean isServerNode(DefaultMutableTreeNode node) {
        return isServerNode.test(node);
      }

      @Override
      public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
        return isInterceptorsGroupNode.test(node);
      }
    };
  }

  private final RoutingContext routingContext;
  private final ServerTreeServerNodeMenuBuilder serverNodeMenuBuilder;
  private final ServerTreeTargetNodeMenuBuilder targetNodeMenuBuilder;

  public ServerTreeContextMenuBuilder(
      RoutingContext routingContext,
      ServerTreeServerNodeMenuBuilder.Context serverNodeContext,
      ServerTreeTargetNodeMenuBuilder.Context targetNodeContext) {
    this(
        routingContext,
        new ServerTreeServerNodeMenuBuilder(serverNodeContext),
        new ServerTreeTargetNodeMenuBuilder(targetNodeContext));
  }

  public ServerTreeContextMenuBuilder(
      RoutingContext routingContext,
      ServerTreeServerNodeMenuBuilder serverNodeMenuBuilder,
      ServerTreeTargetNodeMenuBuilder targetNodeMenuBuilder) {
    this.routingContext = Objects.requireNonNull(routingContext, "routingContext");
    this.serverNodeMenuBuilder = Objects.requireNonNull(serverNodeMenuBuilder, "serverNodeMenuBuilder");
    this.targetNodeMenuBuilder = Objects.requireNonNull(targetNodeMenuBuilder, "targetNodeMenuBuilder");
  }

  public JPopupMenu build(TreePath path) {
    if (path == null) return null;

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return null;

    if (routingContext.isServerNode(node)) {
      return serverNodeMenuBuilder.buildServerNodeMenu(node);
    }

    if (routingContext.isInterceptorsGroupNode(node)) {
      return serverNodeMenuBuilder.buildInterceptorsGroupMenu(node);
    }

    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData && nodeData.ref != null) {
      return targetNodeMenuBuilder.buildTargetNodeMenu(nodeData);
    }

    return null;
  }
}
