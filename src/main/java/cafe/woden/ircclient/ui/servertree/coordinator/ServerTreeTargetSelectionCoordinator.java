package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Orchestrates selecting target/group nodes after ensuring they exist in the tree model. */
public final class ServerTreeTargetSelectionCoordinator {

  public interface Context {
    void ensureNode(TargetRef ref);

    DefaultMutableTreeNode monitorGroupNode(String serverId);

    DefaultMutableTreeNode interceptorsGroupNode(String serverId);

    boolean isGroupNodeSelectable(String serverId, DefaultMutableTreeNode node);

    DefaultMutableTreeNode leafNode(TargetRef ref);

    void selectNode(DefaultMutableTreeNode node);
  }

  public static Context context(
      Consumer<TargetRef> ensureNode,
      Function<String, DefaultMutableTreeNode> monitorGroupNode,
      Function<String, DefaultMutableTreeNode> interceptorsGroupNode,
      BiPredicate<String, DefaultMutableTreeNode> isGroupNodeSelectable,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Consumer<DefaultMutableTreeNode> selectNode) {
    Objects.requireNonNull(ensureNode, "ensureNode");
    Objects.requireNonNull(monitorGroupNode, "monitorGroupNode");
    Objects.requireNonNull(interceptorsGroupNode, "interceptorsGroupNode");
    Objects.requireNonNull(isGroupNodeSelectable, "isGroupNodeSelectable");
    Objects.requireNonNull(leafNode, "leafNode");
    Objects.requireNonNull(selectNode, "selectNode");
    return new Context() {
      @Override
      public void ensureNode(TargetRef ref) {
        ensureNode.accept(ref);
      }

      @Override
      public DefaultMutableTreeNode monitorGroupNode(String serverId) {
        return monitorGroupNode.apply(serverId);
      }

      @Override
      public DefaultMutableTreeNode interceptorsGroupNode(String serverId) {
        return interceptorsGroupNode.apply(serverId);
      }

      @Override
      public boolean isGroupNodeSelectable(String serverId, DefaultMutableTreeNode node) {
        return isGroupNodeSelectable.test(serverId, node);
      }

      @Override
      public DefaultMutableTreeNode leafNode(TargetRef ref) {
        return leafNode.apply(ref);
      }

      @Override
      public void selectNode(DefaultMutableTreeNode node) {
        selectNode.accept(node);
      }
    };
  }

  private final Context context;

  public ServerTreeTargetSelectionCoordinator(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public void selectTarget(TargetRef ref) {
    if (ref == null) return;
    if (ref.isMonitorGroup()) {
      selectGroupTarget(ref, true);
      return;
    }
    if (ref.isInterceptorsGroup()) {
      selectGroupTarget(ref, false);
      return;
    }
    context.ensureNode(ref);
    DefaultMutableTreeNode node = context.leafNode(ref);
    if (node == null) return;
    context.selectNode(node);
  }

  private void selectGroupTarget(TargetRef ref, boolean monitor) {
    context.ensureNode(ref);
    DefaultMutableTreeNode exactNode = context.leafNode(ref);
    if (exactNode != null) {
      context.selectNode(exactNode);
      return;
    }
    String serverId = ServerTreeConventions.normalizeServerId(ref.serverId());
    DefaultMutableTreeNode node =
        monitor ? context.monitorGroupNode(serverId) : context.interceptorsGroupNode(serverId);
    if (node == null || !context.isGroupNodeSelectable(serverId, node)) return;
    context.selectNode(node);
  }
}
