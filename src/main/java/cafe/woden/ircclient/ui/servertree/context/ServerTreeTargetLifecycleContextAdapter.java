package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetLifecycleCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeTargetLifecycleCoordinator.Context}. */
public final class ServerTreeTargetLifecycleContextAdapter
    implements ServerTreeTargetLifecycleCoordinator.Context {

  private final Supplier<Boolean> applicationRootVisible;
  private final Consumer<Boolean> setApplicationRootVisible;
  private final Function<TargetRef, String> applicationLeafLabel;
  private final BiConsumer<TargetRef, String> addApplicationLeaf;
  private final Runnable nodeStructureChangedForApplicationRoot;
  private final Supplier<Boolean> dccTransfersNodesVisible;
  private final Consumer<Boolean> setDccTransfersNodesVisible;
  private final Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility;
  private final Function<String, ServerNodes> addServerRoot;
  private final Function<TargetRef, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
      builtInLayoutNodeKindForRef;
  private final Function<String, RuntimeConfigStore.ServerTreeBuiltInLayout> builtInLayout;
  private final Function<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> rootSiblingOrder;
  private final Function<ServerNodes, DefaultMutableTreeNode> ensureChannelListNode;
  private final BiConsumer<ServerNodes, RuntimeConfigStore.ServerTreeBuiltInLayout>
      applyBuiltInLayoutToTree;
  private final BiConsumer<ServerNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder>
      applyRootSiblingOrderToTree;
  private final Consumer<String> persistBuiltInLayoutFromTree;
  private final Predicate<TargetRef> isPrivateMessageTarget;
  private final Supplier<Boolean> shouldPersistPrivateMessageList;
  private final BiConsumer<String, String> rememberPrivateMessageTarget;
  private final Consumer<TargetRef> ensureChannelKnownInConfig;
  private final Consumer<String> sortChannelsUnderChannelList;
  private final Consumer<String> emitManagedChannelsChanged;
  private final Function<String, String> normalizeServerId;
  private final Consumer<DefaultMutableTreeNode> expandPath;
  private final Runnable reloadRoot;

  public ServerTreeTargetLifecycleContextAdapter(
      Supplier<Boolean> applicationRootVisible,
      Consumer<Boolean> setApplicationRootVisible,
      Function<TargetRef, String> applicationLeafLabel,
      BiConsumer<TargetRef, String> addApplicationLeaf,
      Runnable nodeStructureChangedForApplicationRoot,
      Supplier<Boolean> dccTransfersNodesVisible,
      Consumer<Boolean> setDccTransfersNodesVisible,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Function<String, ServerNodes> addServerRoot,
      Function<TargetRef, RuntimeConfigStore.ServerTreeBuiltInLayoutNode>
          builtInLayoutNodeKindForRef,
      Function<String, RuntimeConfigStore.ServerTreeBuiltInLayout> builtInLayout,
      Function<String, RuntimeConfigStore.ServerTreeRootSiblingOrder> rootSiblingOrder,
      Function<ServerNodes, DefaultMutableTreeNode> ensureChannelListNode,
      BiConsumer<ServerNodes, RuntimeConfigStore.ServerTreeBuiltInLayout> applyBuiltInLayoutToTree,
      BiConsumer<ServerNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder>
          applyRootSiblingOrderToTree,
      Consumer<String> persistBuiltInLayoutFromTree,
      Predicate<TargetRef> isPrivateMessageTarget,
      Supplier<Boolean> shouldPersistPrivateMessageList,
      BiConsumer<String, String> rememberPrivateMessageTarget,
      Consumer<TargetRef> ensureChannelKnownInConfig,
      Consumer<String> sortChannelsUnderChannelList,
      Consumer<String> emitManagedChannelsChanged,
      Function<String, String> normalizeServerId,
      Consumer<DefaultMutableTreeNode> expandPath,
      Runnable reloadRoot) {
    this.applicationRootVisible =
        Objects.requireNonNull(applicationRootVisible, "applicationRootVisible");
    this.setApplicationRootVisible =
        Objects.requireNonNull(setApplicationRootVisible, "setApplicationRootVisible");
    this.applicationLeafLabel =
        Objects.requireNonNull(applicationLeafLabel, "applicationLeafLabel");
    this.addApplicationLeaf = Objects.requireNonNull(addApplicationLeaf, "addApplicationLeaf");
    this.nodeStructureChangedForApplicationRoot =
        Objects.requireNonNull(
            nodeStructureChangedForApplicationRoot, "nodeStructureChangedForApplicationRoot");
    this.dccTransfersNodesVisible =
        Objects.requireNonNull(dccTransfersNodesVisible, "dccTransfersNodesVisible");
    this.setDccTransfersNodesVisible =
        Objects.requireNonNull(setDccTransfersNodesVisible, "setDccTransfersNodesVisible");
    this.builtInNodesVisibility =
        Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    this.addServerRoot = Objects.requireNonNull(addServerRoot, "addServerRoot");
    this.builtInLayoutNodeKindForRef =
        Objects.requireNonNull(builtInLayoutNodeKindForRef, "builtInLayoutNodeKindForRef");
    this.builtInLayout = Objects.requireNonNull(builtInLayout, "builtInLayout");
    this.rootSiblingOrder = Objects.requireNonNull(rootSiblingOrder, "rootSiblingOrder");
    this.ensureChannelListNode =
        Objects.requireNonNull(ensureChannelListNode, "ensureChannelListNode");
    this.applyBuiltInLayoutToTree =
        Objects.requireNonNull(applyBuiltInLayoutToTree, "applyBuiltInLayoutToTree");
    this.applyRootSiblingOrderToTree =
        Objects.requireNonNull(applyRootSiblingOrderToTree, "applyRootSiblingOrderToTree");
    this.persistBuiltInLayoutFromTree =
        Objects.requireNonNull(persistBuiltInLayoutFromTree, "persistBuiltInLayoutFromTree");
    this.isPrivateMessageTarget =
        Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
    this.shouldPersistPrivateMessageList =
        Objects.requireNonNull(shouldPersistPrivateMessageList, "shouldPersistPrivateMessageList");
    this.rememberPrivateMessageTarget =
        Objects.requireNonNull(rememberPrivateMessageTarget, "rememberPrivateMessageTarget");
    this.ensureChannelKnownInConfig =
        Objects.requireNonNull(ensureChannelKnownInConfig, "ensureChannelKnownInConfig");
    this.sortChannelsUnderChannelList =
        Objects.requireNonNull(sortChannelsUnderChannelList, "sortChannelsUnderChannelList");
    this.emitManagedChannelsChanged =
        Objects.requireNonNull(emitManagedChannelsChanged, "emitManagedChannelsChanged");
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.expandPath = Objects.requireNonNull(expandPath, "expandPath");
    this.reloadRoot = Objects.requireNonNull(reloadRoot, "reloadRoot");
  }

  @Override
  public boolean applicationRootVisible() {
    return applicationRootVisible.get();
  }

  @Override
  public void setApplicationRootVisible(boolean visible) {
    setApplicationRootVisible.accept(visible);
  }

  @Override
  public String applicationLeafLabel(TargetRef ref) {
    return applicationLeafLabel.apply(ref);
  }

  @Override
  public void addApplicationLeaf(TargetRef ref, String label) {
    addApplicationLeaf.accept(ref, label);
  }

  @Override
  public void nodeStructureChangedForApplicationRoot() {
    nodeStructureChangedForApplicationRoot.run();
  }

  @Override
  public boolean dccTransfersNodesVisible() {
    return dccTransfersNodesVisible.get();
  }

  @Override
  public void setDccTransfersNodesVisible(boolean visible) {
    setDccTransfersNodesVisible.accept(visible);
  }

  @Override
  public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    return builtInNodesVisibility.apply(serverId);
  }

  @Override
  public ServerNodes addServerRoot(String serverId) {
    return addServerRoot.apply(serverId);
  }

  @Override
  public RuntimeConfigStore.ServerTreeBuiltInLayoutNode builtInLayoutNodeKindForRef(TargetRef ref) {
    return builtInLayoutNodeKindForRef.apply(ref);
  }

  @Override
  public RuntimeConfigStore.ServerTreeBuiltInLayout builtInLayout(String serverId) {
    return builtInLayout.apply(serverId);
  }

  @Override
  public RuntimeConfigStore.ServerTreeRootSiblingOrder rootSiblingOrder(String serverId) {
    return rootSiblingOrder.apply(serverId);
  }

  @Override
  public DefaultMutableTreeNode ensureChannelListNode(ServerNodes serverNodes) {
    return ensureChannelListNode.apply(serverNodes);
  }

  @Override
  public void applyBuiltInLayoutToTree(
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeBuiltInLayout layout) {
    applyBuiltInLayoutToTree.accept(serverNodes, layout);
  }

  @Override
  public void applyRootSiblingOrderToTree(
      ServerNodes serverNodes, RuntimeConfigStore.ServerTreeRootSiblingOrder order) {
    applyRootSiblingOrderToTree.accept(serverNodes, order);
  }

  @Override
  public void persistBuiltInLayoutFromTree(String serverId) {
    persistBuiltInLayoutFromTree.accept(serverId);
  }

  @Override
  public boolean isPrivateMessageTarget(TargetRef ref) {
    return isPrivateMessageTarget.test(ref);
  }

  @Override
  public boolean shouldPersistPrivateMessageList() {
    return shouldPersistPrivateMessageList.get();
  }

  @Override
  public void rememberPrivateMessageTarget(String serverId, String target) {
    rememberPrivateMessageTarget.accept(serverId, target);
  }

  @Override
  public void ensureChannelKnownInConfig(TargetRef ref) {
    ensureChannelKnownInConfig.accept(ref);
  }

  @Override
  public void sortChannelsUnderChannelList(String serverId) {
    sortChannelsUnderChannelList.accept(serverId);
  }

  @Override
  public void emitManagedChannelsChanged(String serverId) {
    emitManagedChannelsChanged.accept(serverId);
  }

  @Override
  public String normalizeServerId(String serverId) {
    return normalizeServerId.apply(serverId);
  }

  @Override
  public void expandPath(DefaultMutableTreeNode parentNode) {
    expandPath.accept(parentNode);
  }

  @Override
  public void reloadRoot() {
    reloadRoot.run();
  }
}
