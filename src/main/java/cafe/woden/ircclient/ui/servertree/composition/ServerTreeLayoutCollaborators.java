package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutOrchestrator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutApplier;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutPersistenceCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeRootSiblingOrderCoordinator;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;

/** Container for server-tree layout and built-in visibility collaborators. */
public record ServerTreeLayoutCollaborators(
    ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator,
    ServerTreeLayoutApplier layoutApplier,
    ServerTreeLayoutPersistenceCoordinator layoutPersistenceCoordinator,
    ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator,
    ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator,
    ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator) {}
