package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutOrchestrator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutApplier;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutPersistenceCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeRootSiblingOrderCoordinator;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import java.util.Objects;

/** Composition helper for creating layout/visibility collaborators used by server tree. */
public final class ServerTreeLayoutCollaboratorsFactory {

  private ServerTreeLayoutCollaboratorsFactory() {}

  public static ServerTreeLayoutCollaborators create(
      RuntimeConfigStore runtimeConfig,
      ServerTreeBuiltInVisibilityCoordinator.Context builtInVisibilityContext,
      ServerTreeLayoutPersistenceCoordinator.Context layoutPersistenceContext,
      ServerTreeBuiltInLayoutOrchestrator.Context builtInLayoutOrchestratorContext) {
    ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator =
        new ServerTreeBuiltInVisibilityCoordinator(
            runtimeConfig,
            Objects.requireNonNull(builtInVisibilityContext, "builtInVisibilityContext"));
    ServerTreeLayoutApplier layoutApplier = new ServerTreeLayoutApplier();
    ServerTreeLayoutPersistenceCoordinator layoutPersistenceCoordinator =
        new ServerTreeLayoutPersistenceCoordinator(
            Objects.requireNonNull(layoutPersistenceContext, "layoutPersistenceContext"));
    ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator =
        new ServerTreeBuiltInLayoutOrchestrator(
            layoutApplier,
            layoutPersistenceCoordinator,
            Objects.requireNonNull(
                builtInLayoutOrchestratorContext, "builtInLayoutOrchestratorContext"));
    ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator =
        new ServerTreeBuiltInLayoutCoordinator(runtimeConfig);
    ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator =
        new ServerTreeRootSiblingOrderCoordinator(runtimeConfig);

    return new ServerTreeLayoutCollaborators(
        builtInVisibilityCoordinator,
        layoutApplier,
        layoutPersistenceCoordinator,
        builtInLayoutOrchestrator,
        builtInLayoutCoordinator,
        rootSiblingOrderCoordinator);
  }
}
