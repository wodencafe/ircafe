package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;

/** Container for tooltip/context-menu collaborators wired during server tree construction. */
public record ServerTreeViewInteractionCollaborators(
    ServerTreeTooltipProvider tooltipProvider,
    ServerTreeTooltipResolver tooltipResolver,
    ServerTreeContextMenuBuilder contextMenuBuilder) {}
