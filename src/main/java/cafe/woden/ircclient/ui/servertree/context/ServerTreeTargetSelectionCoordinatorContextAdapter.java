package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetSelectionCoordinator;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeTargetSelectionCoordinator.Context}. */
public final class ServerTreeTargetSelectionCoordinatorContextAdapter
    implements ServerTreeTargetSelectionCoordinator.Context {

  private final Consumer<TargetRef> ensureNode;
  private final Function<String, DefaultMutableTreeNode> monitorGroupNode;
  private final Function<String, DefaultMutableTreeNode> interceptorsGroupNode;
  private final BiPredicate<String, DefaultMutableTreeNode> isGroupNodeSelectable;
  private final Function<TargetRef, DefaultMutableTreeNode> leafNode;
  private final Consumer<DefaultMutableTreeNode> selectNode;

  public ServerTreeTargetSelectionCoordinatorContextAdapter(
      Consumer<TargetRef> ensureNode,
      Function<String, DefaultMutableTreeNode> monitorGroupNode,
      Function<String, DefaultMutableTreeNode> interceptorsGroupNode,
      BiPredicate<String, DefaultMutableTreeNode> isGroupNodeSelectable,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Consumer<DefaultMutableTreeNode> selectNode) {
    this.ensureNode = Objects.requireNonNull(ensureNode, "ensureNode");
    this.monitorGroupNode = Objects.requireNonNull(monitorGroupNode, "monitorGroupNode");
    this.interceptorsGroupNode =
        Objects.requireNonNull(interceptorsGroupNode, "interceptorsGroupNode");
    this.isGroupNodeSelectable =
        Objects.requireNonNull(isGroupNodeSelectable, "isGroupNodeSelectable");
    this.leafNode = Objects.requireNonNull(leafNode, "leafNode");
    this.selectNode = Objects.requireNonNull(selectNode, "selectNode");
  }

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
}
