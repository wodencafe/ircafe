package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.util.TreeNodeActions;

/** Container for tree interaction bindings wired during server tree construction. */
public record ServerTreeTreeInteractionBindings(TreeNodeActions<TargetRef> nodeActions) {}
