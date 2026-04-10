package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipProvider;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import javax.swing.JPopupMenu;
import javax.swing.tree.TreePath;

/** Container for tooltip/context-menu collaborators wired during server tree construction. */
public record ServerTreeViewInteractionCollaborators(
    ServerTreeTooltipProvider tooltipProvider,
    Function<MouseEvent, String> tooltipResolver,
    Function<TreePath, JPopupMenu> contextMenuBuilder) {}
