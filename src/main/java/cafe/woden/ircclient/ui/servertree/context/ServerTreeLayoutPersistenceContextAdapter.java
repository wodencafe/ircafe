package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutPersistenceCoordinator;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeLayoutPersistenceCoordinator.Context}. */
public final class ServerTreeLayoutPersistenceContextAdapter
    implements ServerTreeLayoutPersistenceCoordinator.Context {

  private final Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
      rootSiblingNodeKindForNode;
  private final Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
      builtInLayoutNodeKindForNode;
  private final Function<String, RuntimeConfigStore.ServerTreeRootSiblingOrder>
      currentRootSiblingOrder;
  private final Function<String, RuntimeConfigStore.ServerTreeBuiltInLayout> currentBuiltInLayout;
  private final BiConsumer<String, RuntimeConfigStore.ServerTreeRootSiblingOrder>
      persistRootSiblingOrder;
  private final BiConsumer<String, RuntimeConfigStore.ServerTreeBuiltInLayout> persistBuiltInLayout;

  public ServerTreeLayoutPersistenceContextAdapter(
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
          rootSiblingNodeKindForNode,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
          builtInLayoutNodeKindForNode,
      Function<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> currentRootSiblingOrder,
      Function<String, RuntimeConfigStore.ServerTreeBuiltInLayout> currentBuiltInLayout,
      BiConsumer<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> persistRootSiblingOrder,
      BiConsumer<String, RuntimeConfigStore.ServerTreeBuiltInLayout> persistBuiltInLayout) {
    this.rootSiblingNodeKindForNode =
        Objects.requireNonNull(rootSiblingNodeKindForNode, "rootSiblingNodeKindForNode");
    this.builtInLayoutNodeKindForNode =
        Objects.requireNonNull(builtInLayoutNodeKindForNode, "builtInLayoutNodeKindForNode");
    this.currentRootSiblingOrder =
        Objects.requireNonNull(currentRootSiblingOrder, "currentRootSiblingOrder");
    this.currentBuiltInLayout =
        Objects.requireNonNull(currentBuiltInLayout, "currentBuiltInLayout");
    this.persistRootSiblingOrder =
        Objects.requireNonNull(persistRootSiblingOrder, "persistRootSiblingOrder");
    this.persistBuiltInLayout =
        Objects.requireNonNull(persistBuiltInLayout, "persistBuiltInLayout");
  }

  @Override
  public RuntimeConfigStore.ServerTreeRootSiblingNode rootSiblingNodeKindForNode(
      DefaultMutableTreeNode node) {
    return rootSiblingNodeKindForNode.apply(node);
  }

  @Override
  public RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForNode(
      DefaultMutableTreeNode node) {
    return builtInLayoutNodeKindForNode.apply(node);
  }

  @Override
  public RuntimeConfigStore.ServerTreeRootSiblingOrder currentRootSiblingOrder(String serverId) {
    return currentRootSiblingOrder.apply(serverId);
  }

  @Override
  public RuntimeConfigStore.ServerTreeBuiltInLayout currentBuiltInLayout(String serverId) {
    return currentBuiltInLayout.apply(serverId);
  }

  @Override
  public void persistRootSiblingOrder(
      String serverId, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
    persistRootSiblingOrder.accept(serverId, order);
  }

  @Override
  public void persistBuiltInLayout(
      String serverId, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    persistBuiltInLayout.accept(serverId, layout);
  }
}
