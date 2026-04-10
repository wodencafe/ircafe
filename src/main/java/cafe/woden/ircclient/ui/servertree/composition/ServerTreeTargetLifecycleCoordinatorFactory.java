package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetLifecycleCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/**
 * Factory that assembles target lifecycle coordinator dependencies for server tree construction.
 */
@Component
public final class ServerTreeTargetLifecycleCoordinatorFactory {

  public ServerTreeTargetLifecycleCoordinator create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");
    return new ServerTreeTargetLifecycleCoordinator(
        Objects.requireNonNull(in.servers(), "servers"),
        Objects.requireNonNull(in.leaves(), "leaves"),
        in.serverCatalog(),
        Objects.requireNonNull(in.ensureNodeParentResolver(), "ensureNodeParentResolver"),
        Objects.requireNonNull(in.ensureNodeLeafInserter(), "ensureNodeLeafInserter"),
        Objects.requireNonNull(in.targetNodePolicy(), "targetNodePolicy"),
        Objects.requireNonNull(in.targetSnapshotProvider(), "targetSnapshotProvider"),
        Objects.requireNonNull(in.targetRemovalStateCoordinator(), "targetRemovalStateCoordinator"),
        Objects.requireNonNull(in.targetNodeRemovalMutator(), "targetNodeRemovalMutator"),
        ServerTreeTargetLifecycleCoordinator.context(
            Objects.requireNonNull(in.applicationRootVisible(), "applicationRootVisible"),
            Objects.requireNonNull(in.setApplicationRootVisible(), "setApplicationRootVisible"),
            Objects.requireNonNull(in.applicationLeafLabel(), "applicationLeafLabel"),
            Objects.requireNonNull(in.addApplicationLeaf(), "addApplicationLeaf"),
            Objects.requireNonNull(
                in.nodeStructureChangedForApplicationRoot(),
                "nodeStructureChangedForApplicationRoot"),
            Objects.requireNonNull(in.dccTransfersNodesVisible(), "dccTransfersNodesVisible"),
            Objects.requireNonNull(in.setDccTransfersNodesVisible(), "setDccTransfersNodesVisible"),
            Objects.requireNonNull(in.builtInNodesVisibility(), "builtInNodesVisibility"),
            Objects.requireNonNull(in.addServerRoot(), "addServerRoot"),
            Objects.requireNonNull(in.builtInLayoutNodeKindForRef(), "builtInLayoutNodeKindForRef"),
            Objects.requireNonNull(in.builtInLayout(), "builtInLayout"),
            Objects.requireNonNull(in.rootSiblingOrder(), "rootSiblingOrder"),
            Objects.requireNonNull(in.backendSpecificParent(), "backendSpecificParent"),
            Objects.requireNonNull(in.ensureChannelListNode(), "ensureChannelListNode"),
            Objects.requireNonNull(in.applyBuiltInLayoutToTree(), "applyBuiltInLayoutToTree"),
            Objects.requireNonNull(in.applyRootSiblingOrderToTree(), "applyRootSiblingOrderToTree"),
            Objects.requireNonNull(
                in.persistBuiltInLayoutFromTree(), "persistBuiltInLayoutFromTree"),
            Objects.requireNonNull(in.isPrivateMessageTarget(), "isPrivateMessageTarget"),
            Objects.requireNonNull(
                in.shouldPersistPrivateMessageList(), "shouldPersistPrivateMessageList"),
            Objects.requireNonNull(
                in.rememberPrivateMessageTarget(), "rememberPrivateMessageTarget"),
            Objects.requireNonNull(in.ensureChannelKnownInConfig(), "ensureChannelKnownInConfig"),
            Objects.requireNonNull(
                in.sortChannelsUnderChannelList(), "sortChannelsUnderChannelList"),
            Objects.requireNonNull(in.emitManagedChannelsChanged(), "emitManagedChannelsChanged"),
            Objects.requireNonNull(in.normalizeServerId(), "normalizeServerId"),
            Objects.requireNonNull(in.expandPath(), "expandPath"),
            Objects.requireNonNull(in.reloadRoot(), "reloadRoot")));
  }

  public record Inputs(
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      ServerCatalog serverCatalog,
      ServerTreeEnsureNodeParentResolver ensureNodeParentResolver,
      ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter,
      ServerTreeTargetNodePolicy targetNodePolicy,
      ServerTreeTargetSnapshotProvider targetSnapshotProvider,
      ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator,
      ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator,
      Supplier<Boolean> applicationRootVisible,
      Consumer<Boolean> setApplicationRootVisible,
      Function<TargetRef, String> applicationLeafLabel,
      BiConsumer<TargetRef, String> addApplicationLeaf,
      Runnable nodeStructureChangedForApplicationRoot,
      Supplier<Boolean> dccTransfersNodesVisible,
      Consumer<Boolean> setDccTransfersNodesVisible,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Function<String, ServerNodes> addServerRoot,
      Function<TargetRef, ServerTreeBuiltInLayoutNode> builtInLayoutNodeKindForRef,
      Function<String, ServerTreeBuiltInLayout> builtInLayout,
      Function<String, ServerTreeRootSiblingOrder> rootSiblingOrder,
      java.util.function.BiFunction<TargetRef, ServerNodes, DefaultMutableTreeNode>
          backendSpecificParent,
      Function<ServerNodes, DefaultMutableTreeNode> ensureChannelListNode,
      BiConsumer<ServerNodes, ServerTreeBuiltInLayout> applyBuiltInLayoutToTree,
      BiConsumer<ServerNodes, ServerTreeRootSiblingOrder> applyRootSiblingOrderToTree,
      Consumer<String> persistBuiltInLayoutFromTree,
      Predicate<TargetRef> isPrivateMessageTarget,
      Supplier<Boolean> shouldPersistPrivateMessageList,
      BiConsumer<String, String> rememberPrivateMessageTarget,
      Consumer<TargetRef> ensureChannelKnownInConfig,
      Consumer<String> sortChannelsUnderChannelList,
      Consumer<String> emitManagedChannelsChanged,
      Function<String, String> normalizeServerId,
      Consumer<DefaultMutableTreeNode> expandPath,
      Runnable reloadRoot) {}
}
