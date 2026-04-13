package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort;
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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Factory that assembles state/interaction collaborators for server tree construction. */
@Component
@RequiredArgsConstructor
public final class ServerTreeStateInteractionCollaboratorsFactory {

  @NonNull private final ServerTreeEnsureNodeParentResolver ensureNodeParentResolver;
  @NonNull private final ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter;
  @NonNull private final ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater;
  @NonNull private final ServerTreeServerStateCleaner serverStateCleaner;
  @NonNull private final ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator;
  @NonNull private final ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator;

  public ServerTreeStateInteractionCollaborators create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");

    ServerTreeServerActionOverlay serverActionOverlay =
        new ServerTreeServerActionOverlay(
            in.tree(),
            in.serverActionButtonSize(),
            in.serverActionButtonIconSize(),
            in.serverActionButtonMargin(),
            Objects.requireNonNull(in.serverActionOverlayContext(), "serverActionOverlayContext"));
    ServerTreeServerRuntimeUiUpdater.Context serverRuntimeUiUpdaterContext =
        ServerTreeServerRuntimeUiUpdater.context(
            Objects.requireNonNull(in.runtimeState(), "runtimeState"),
            Objects.requireNonNull(in.servers(), "servers"),
            Objects.requireNonNull(in.model(), "model")::nodeChanged,
            serverActionOverlay::isHoveredServer,
            in.tree()::repaint);
    ServerTreeServerStateCleaner.Context serverStateCleanerContext =
        ServerTreeServerStateCleaner.context(
            in.interceptorStore(),
            serverActionOverlay::clearHoveredServer,
            Objects.requireNonNull(in.runtimeState(), "runtimeState")::removeServer,
            Objects.requireNonNull(in.channelStateStore(), "channelStateStore")::clearServer,
            Objects.requireNonNull(
                in.clearPrivateMessageOnlineStates(), "clearPrivateMessageOnlineStates"),
            Objects.requireNonNull(in.leaves(), "leaves"),
            Objects.requireNonNull(in.typingActivityNodes(), "typingActivityNodes"));
    ServerTreeChannelStateCoordinator channelStateCoordinator =
        new ServerTreeChannelStateCoordinator(
            in.channelStateConfig(),
            in.channelStateStore(),
            in.model(),
            Objects.requireNonNull(
                in.channelStateCoordinatorContext(), "channelStateCoordinatorContext"));
    ServerTreeEnsureNodeLeafInserter.Context ensureNodeLeafInserterContext =
        ServerTreeEnsureNodeLeafInserter.context(
            in.leaves(),
            in.model(),
            Objects.requireNonNull(
                in.privateMessageOnlineStateStore(), "privateMessageOnlineStateStore"),
            Objects.requireNonNull(
                in.isPrivateMessageTargetForEnsureNodeLeafInserter(),
                "isPrivateMessageTargetForEnsureNodeLeafInserter"));
    ServerTreeTargetNodeRemovalMutator.Context targetNodeRemovalMutatorContext =
        ServerTreeTargetNodeRemovalMutator.context(in.typingActivityNodes(), in.model());
    ServerTreeTargetRemovalStateCoordinator.Context targetRemovalStateCoordinatorContext =
        Objects.requireNonNull(
            in.targetRemovalStateCoordinatorContext(), "targetRemovalStateCoordinatorContext");
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
        serverRuntimeUiUpdaterContext,
        serverStateCleaner,
        serverStateCleanerContext,
        channelStateCoordinator,
        ensureNodeParentResolver,
        ensureNodeLeafInserter,
        ensureNodeLeafInserterContext,
        targetNodeRemovalMutator,
        targetNodeRemovalMutatorContext,
        targetRemovalStateCoordinator,
        targetRemovalStateCoordinatorContext,
        detachedWarningClickHandler,
        rowInteractionHandler);
  }

  public record Inputs(
      JTree tree,
      DefaultTreeModel model,
      IrcSessionRuntimeConfigPort sessionRuntimeConfig,
      ServerTreeChannelStateConfigPort channelStateConfig,
      ServerTreeChannelStateStore channelStateStore,
      InterceptorStore interceptorStore,
      ServerTreeRuntimeState runtimeState,
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Set<DefaultMutableTreeNode> typingActivityNodes,
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      java.util.function.Predicate<TargetRef> isPrivateMessageTargetForEnsureNodeLeafInserter,
      java.util.function.Predicate<DefaultMutableTreeNode> isServerNode,
      Consumer<TargetRef> clearChannelDisconnectedWarning,
      IntSupplier typingSlotWidth,
      Consumer<String> clearPrivateMessageOnlineStates,
      ServerTreeServerActionOverlay.Context serverActionOverlayContext,
      ServerTreeChannelStateCoordinator.Context channelStateCoordinatorContext,
      ServerTreeTargetRemovalStateCoordinator.Context targetRemovalStateCoordinatorContext,
      int serverActionButtonSize,
      int serverActionButtonIconSize,
      int serverActionButtonMargin) {}
}
