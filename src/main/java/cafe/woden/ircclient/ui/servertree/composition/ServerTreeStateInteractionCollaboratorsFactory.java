package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeDetachedWarningClickHandler;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Factory that assembles state/interaction collaborators for server tree construction. */
public final class ServerTreeStateInteractionCollaboratorsFactory {

  private ServerTreeStateInteractionCollaboratorsFactory() {}

  public static ServerTreeStateInteractionCollaborators create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");

    ServerTreeServerActionOverlay serverActionOverlay =
        new ServerTreeServerActionOverlay(
            in.tree(),
            in.serverActionButtonSize(),
            in.serverActionButtonIconSize(),
            in.serverActionButtonMargin(),
            Objects.requireNonNull(in.serverActionOverlayContext(), "serverActionOverlayContext"));
    ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater =
        new ServerTreeServerRuntimeUiUpdater(
            Objects.requireNonNull(in.runtimeState(), "runtimeState"),
            Objects.requireNonNull(in.servers(), "servers"),
            Objects.requireNonNull(in.model(), "model"),
            serverActionOverlay,
            in.tree());
    ServerTreeServerStateCleaner serverStateCleaner =
        new ServerTreeServerStateCleaner(
            in.interceptorStore(),
            serverActionOverlay,
            in.runtimeState(),
            Objects.requireNonNull(in.channelStateStore(), "channelStateStore"),
            Objects.requireNonNull(in.leaves(), "leaves"),
            Objects.requireNonNull(in.typingActivityNodes(), "typingActivityNodes"),
            Objects.requireNonNull(in.serverStateCleanerContext(), "serverStateCleanerContext"));
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        new ServerTreeChannelStateCoordinator(
            in.runtimeConfig(),
            in.channelStateStore(),
            in.model(),
            Objects.requireNonNull(
                in.channelStateCoordinatorContext(), "channelStateCoordinatorContext"));
    ServerTreeEnsureNodeParentResolver ensureNodeParentResolver =
        new ServerTreeEnsureNodeParentResolver();
    ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter =
        new ServerTreeEnsureNodeLeafInserter(
            in.leaves(),
            in.model(),
            Objects.requireNonNull(
                in.privateMessageOnlineStateStore(), "privateMessageOnlineStateStore"),
            Objects.requireNonNull(
                in.ensureNodeLeafInserterContext(), "ensureNodeLeafInserterContext"));
    ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator =
        new ServerTreeTargetNodeRemovalMutator(in.typingActivityNodes(), in.model());
    ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator =
        new ServerTreeTargetRemovalStateCoordinator(
            in.privateMessageOnlineStateStore(),
            in.runtimeConfig(),
            in.channelStateStore(),
            Objects.requireNonNull(
                in.targetRemovalStateCoordinatorContext(), "targetRemovalStateCoordinatorContext"));
    ServerTreeDetachedWarningClickHandler detachedWarningClickHandler =
        new ServerTreeDetachedWarningClickHandler(
            in.tree(),
            Objects.requireNonNull(
                in.clearChannelDisconnectedWarning(), "clearChannelDisconnectedWarning"));
    ServerTreeRowInteractionHandler rowInteractionHandler =
        new ServerTreeRowInteractionHandler(
            in.tree(),
            detachedWarningClickHandler,
            Objects.requireNonNull(in.isServerNode(), "isServerNode"),
            Objects.requireNonNull(in.typingSlotWidth(), "typingSlotWidth"));

    return new ServerTreeStateInteractionCollaborators(
        serverActionOverlay,
        serverRuntimeUiUpdater,
        serverStateCleaner,
        channelStateCoordinator,
        ensureNodeParentResolver,
        ensureNodeLeafInserter,
        targetNodeRemovalMutator,
        targetRemovalStateCoordinator,
        detachedWarningClickHandler,
        rowInteractionHandler);
  }

  public record Inputs(
      JTree tree,
      DefaultTreeModel model,
      RuntimeConfigStore runtimeConfig,
      ServerTreeChannelStateStore channelStateStore,
      InterceptorStore interceptorStore,
      ServerTreeRuntimeState runtimeState,
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Set<DefaultMutableTreeNode> typingActivityNodes,
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      ServerTreeEnsureNodeLeafInserter.Context ensureNodeLeafInserterContext,
      java.util.function.Predicate<DefaultMutableTreeNode> isServerNode,
      Consumer<TargetRef> clearChannelDisconnectedWarning,
      IntSupplier typingSlotWidth,
      ServerTreeServerStateCleaner.Context serverStateCleanerContext,
      ServerTreeServerActionOverlay.Context serverActionOverlayContext,
      ServerTreeChannelStateCoordinator.Context channelStateCoordinatorContext,
      ServerTreeTargetRemovalStateCoordinator.Context targetRemovalStateCoordinatorContext,
      int serverActionButtonSize,
      int serverActionButtonIconSize,
      int serverActionButtonMargin) {}
}
