package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeInterceptorActions.Context}. */
public final class ServerTreeInterceptorActionsContextAdapter
    implements ServerTreeInterceptorActions.Context {

  private final Consumer<TargetRef> ensureNode;
  private final Consumer<TargetRef> selectTarget;
  private final Consumer<TargetRef> removeTarget;
  private final Function<TargetRef, DefaultMutableTreeNode> leafNode;
  private final Function<String, DefaultMutableTreeNode> interceptorsGroupNode;
  private final Consumer<DefaultMutableTreeNode> nodeChanged;

  public ServerTreeInterceptorActionsContextAdapter(
      Consumer<TargetRef> ensureNode,
      Consumer<TargetRef> selectTarget,
      Consumer<TargetRef> removeTarget,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Function<String, DefaultMutableTreeNode> interceptorsGroupNode,
      Consumer<DefaultMutableTreeNode> nodeChanged) {
    this.ensureNode = Objects.requireNonNull(ensureNode, "ensureNode");
    this.selectTarget = Objects.requireNonNull(selectTarget, "selectTarget");
    this.removeTarget = Objects.requireNonNull(removeTarget, "removeTarget");
    this.leafNode = Objects.requireNonNull(leafNode, "leafNode");
    this.interceptorsGroupNode =
        Objects.requireNonNull(interceptorsGroupNode, "interceptorsGroupNode");
    this.nodeChanged = Objects.requireNonNull(nodeChanged, "nodeChanged");
  }

  @Override
  public void ensureNode(TargetRef ref) {
    ensureNode.accept(ref);
  }

  @Override
  public void selectTarget(TargetRef ref) {
    selectTarget.accept(ref);
  }

  @Override
  public void removeTarget(TargetRef ref) {
    removeTarget.accept(ref);
  }

  @Override
  public DefaultMutableTreeNode leafNode(TargetRef ref) {
    return leafNode.apply(ref);
  }

  @Override
  public DefaultMutableTreeNode interceptorsGroupNode(String serverId) {
    return interceptorsGroupNode.apply(serverId);
  }

  @Override
  public void nodeChanged(DefaultMutableTreeNode node) {
    nodeChanged.accept(node);
  }
}
