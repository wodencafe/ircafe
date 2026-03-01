package cafe.woden.ircclient.ui.servertree.interaction;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Default adapter implementation for middle-drag reorder context wiring. */
public final class ServerTreeMiddleDragReorderContextAdapter
    implements ServerTreeMiddleDragReorderHandler.Context {

  private final JTree tree;
  private final DefaultTreeModel model;
  private final Predicate<DefaultMutableTreeNode> isDraggableChannelNode;
  private final Predicate<DefaultMutableTreeNode> isRootSiblingReorderableNode;
  private final Predicate<DefaultMutableTreeNode> isMovableBuiltInNode;
  private final Function<DefaultMutableTreeNode, String> owningServerIdForNode;
  private final Function<String, ServerNodes> serverNodes;
  private final Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
      rootSiblingNodeKindForNode;
  private final Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
      builtInLayoutNodeKindForNode;
  private final ToIntFunction<DefaultMutableTreeNode> minInsertIndex;
  private final ToIntFunction<DefaultMutableTreeNode> maxInsertIndex;
  private final BiFunction<ServerNodes, Integer, Integer> rootBuiltInInsertIndex;
  private final BiConsumer<DefaultMutableTreeNode, Integer> setInsertionLineForIndex;
  private final Runnable clearInsertionLine;
  private final Predicate<DefaultMutableTreeNode> isChannelListLeafNode;
  private final Consumer<DefaultMutableTreeNode> persistCustomOrderForParent;
  private final Consumer<String> persistBuiltInLayout;
  private final Consumer<String> persistRootSiblingOrder;
  private final Consumer<Runnable> withSuppressedSelectionBroadcast;
  private final Runnable refreshNodeActionsEnabled;

  public ServerTreeMiddleDragReorderContextAdapter(
      JTree tree,
      DefaultTreeModel model,
      Predicate<DefaultMutableTreeNode> isDraggableChannelNode,
      Predicate<DefaultMutableTreeNode> isRootSiblingReorderableNode,
      Predicate<DefaultMutableTreeNode> isMovableBuiltInNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Function<String, ServerNodes> serverNodes,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeRootSiblingNode>
          rootSiblingNodeKindForNode,
      Function<DefaultMutableTreeNode, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
          builtInLayoutNodeKindForNode,
      ToIntFunction<DefaultMutableTreeNode> minInsertIndex,
      ToIntFunction<DefaultMutableTreeNode> maxInsertIndex,
      BiFunction<ServerNodes, Integer, Integer> rootBuiltInInsertIndex,
      BiConsumer<DefaultMutableTreeNode, Integer> setInsertionLineForIndex,
      Runnable clearInsertionLine,
      Predicate<DefaultMutableTreeNode> isChannelListLeafNode,
      Consumer<DefaultMutableTreeNode> persistCustomOrderForParent,
      Consumer<String> persistBuiltInLayout,
      Consumer<String> persistRootSiblingOrder,
      Consumer<Runnable> withSuppressedSelectionBroadcast,
      Runnable refreshNodeActionsEnabled) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.model = Objects.requireNonNull(model, "model");
    this.isDraggableChannelNode =
        Objects.requireNonNull(isDraggableChannelNode, "isDraggableChannelNode");
    this.isRootSiblingReorderableNode =
        Objects.requireNonNull(isRootSiblingReorderableNode, "isRootSiblingReorderableNode");
    this.isMovableBuiltInNode =
        Objects.requireNonNull(isMovableBuiltInNode, "isMovableBuiltInNode");
    this.owningServerIdForNode =
        Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    this.serverNodes = Objects.requireNonNull(serverNodes, "serverNodes");
    this.rootSiblingNodeKindForNode =
        Objects.requireNonNull(rootSiblingNodeKindForNode, "rootSiblingNodeKindForNode");
    this.builtInLayoutNodeKindForNode =
        Objects.requireNonNull(builtInLayoutNodeKindForNode, "builtInLayoutNodeKindForNode");
    this.minInsertIndex = Objects.requireNonNull(minInsertIndex, "minInsertIndex");
    this.maxInsertIndex = Objects.requireNonNull(maxInsertIndex, "maxInsertIndex");
    this.rootBuiltInInsertIndex =
        Objects.requireNonNull(rootBuiltInInsertIndex, "rootBuiltInInsertIndex");
    this.setInsertionLineForIndex =
        Objects.requireNonNull(setInsertionLineForIndex, "setInsertionLineForIndex");
    this.clearInsertionLine = Objects.requireNonNull(clearInsertionLine, "clearInsertionLine");
    this.isChannelListLeafNode =
        Objects.requireNonNull(isChannelListLeafNode, "isChannelListLeafNode");
    this.persistCustomOrderForParent =
        Objects.requireNonNull(persistCustomOrderForParent, "persistCustomOrderForParent");
    this.persistBuiltInLayout =
        Objects.requireNonNull(persistBuiltInLayout, "persistBuiltInLayout");
    this.persistRootSiblingOrder =
        Objects.requireNonNull(persistRootSiblingOrder, "persistRootSiblingOrder");
    this.withSuppressedSelectionBroadcast =
        Objects.requireNonNull(
            withSuppressedSelectionBroadcast, "withSuppressedSelectionBroadcast");
    this.refreshNodeActionsEnabled =
        Objects.requireNonNull(refreshNodeActionsEnabled, "refreshNodeActionsEnabled");
  }

  @Override
  public JTree tree() {
    return tree;
  }

  @Override
  public DefaultTreeModel model() {
    return model;
  }

  @Override
  public boolean isDraggableChannelNode(DefaultMutableTreeNode node) {
    return isDraggableChannelNode.test(node);
  }

  @Override
  public boolean isRootSiblingReorderableNode(DefaultMutableTreeNode node) {
    return isRootSiblingReorderableNode.test(node);
  }

  @Override
  public boolean isMovableBuiltInNode(DefaultMutableTreeNode node) {
    return isMovableBuiltInNode.test(node);
  }

  @Override
  public String owningServerIdForNode(DefaultMutableTreeNode node) {
    return owningServerIdForNode.apply(node);
  }

  @Override
  public ServerNodes serverNodes(String serverId) {
    return serverNodes.apply(serverId);
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
  public int minInsertIndex(DefaultMutableTreeNode parentNode) {
    return minInsertIndex.applyAsInt(parentNode);
  }

  @Override
  public int maxInsertIndex(DefaultMutableTreeNode parentNode) {
    return maxInsertIndex.applyAsInt(parentNode);
  }

  @Override
  public int rootBuiltInInsertIndex(ServerNodes serverNodes, int desiredIndex) {
    return rootBuiltInInsertIndex.apply(serverNodes, desiredIndex);
  }

  @Override
  public void setInsertionLineForIndex(DefaultMutableTreeNode parent, int insertBeforeIndex) {
    setInsertionLineForIndex.accept(parent, insertBeforeIndex);
  }

  @Override
  public void clearInsertionLine() {
    clearInsertionLine.run();
  }

  @Override
  public boolean isChannelListLeafNode(DefaultMutableTreeNode node) {
    return isChannelListLeafNode.test(node);
  }

  @Override
  public void persistCustomOrderForParent(DefaultMutableTreeNode parentNode) {
    persistCustomOrderForParent.accept(parentNode);
  }

  @Override
  public void persistBuiltInLayout(String serverId) {
    persistBuiltInLayout.accept(serverId);
  }

  @Override
  public void persistRootSiblingOrder(String serverId) {
    persistRootSiblingOrder.accept(serverId);
  }

  @Override
  public void withSuppressedSelectionBroadcast(Runnable task) {
    withSuppressedSelectionBroadcast.accept(task);
  }

  @Override
  public void refreshNodeActionsEnabled() {
    refreshNodeActionsEnabled.run();
  }
}
