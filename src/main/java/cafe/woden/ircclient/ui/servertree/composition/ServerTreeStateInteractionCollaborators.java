package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeRowInteractionHandler;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeDetachedWarningClickHandler;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;

/** Container for state and interaction collaborators wired during server tree construction. */
public record ServerTreeStateInteractionCollaborators(
    ServerTreeServerActionOverlay serverActionOverlay,
    ServerTreeServerRuntimeUiUpdater serverRuntimeUiUpdater,
    ServerTreeServerRuntimeUiUpdater.Context serverRuntimeUiUpdaterContext,
    ServerTreeServerStateCleaner serverStateCleaner,
    ServerTreeServerStateCleaner.Context serverStateCleanerContext,
    ServerTreeChannelStateCoordinator channelStateCoordinator,
    ServerTreeEnsureNodeParentResolver ensureNodeParentResolver,
    ServerTreeEnsureNodeLeafInserter ensureNodeLeafInserter,
    ServerTreeEnsureNodeLeafInserter.Context ensureNodeLeafInserterContext,
    ServerTreeTargetNodeRemovalMutator targetNodeRemovalMutator,
    ServerTreeTargetNodeRemovalMutator.Context targetNodeRemovalMutatorContext,
    ServerTreeTargetRemovalStateCoordinator targetRemovalStateCoordinator,
    ServerTreeTargetRemovalStateCoordinator.Context targetRemovalStateCoordinatorContext,
    ServerTreeDetachedWarningClickHandler detachedWarningClickHandler,
    ServerTreeRowInteractionHandler rowInteractionHandler) {}
