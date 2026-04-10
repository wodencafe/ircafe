package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutOrchestrator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutApplier;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutPersistenceCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeRootSiblingOrderCoordinator;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Composition helper for creating layout/visibility collaborators used by server tree. */
@Component
@RequiredArgsConstructor
public final class ServerTreeLayoutCollaboratorsFactory {

  @NonNull private final ServerTreeLayoutApplier layoutApplier;
  @NonNull private final ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator;
  @NonNull private final ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator;

  public ServerTreeLayoutCollaborators create(
      ServerTreeBuiltInVisibilityConfigPort builtInVisibilityConfig,
      ServerTreeBuiltInVisibilityCoordinator.Context builtInVisibilityContext,
      ServerTreeLayoutPersistenceCoordinator.Context layoutPersistenceContext,
      ServerTreeBuiltInLayoutOrchestrator.Context builtInLayoutOrchestratorContext) {
    ServerTreeBuiltInVisibilityCoordinator builtInVisibilityCoordinator =
        new ServerTreeBuiltInVisibilityCoordinator(
            builtInVisibilityConfig,
            Objects.requireNonNull(builtInVisibilityContext, "builtInVisibilityContext"));
    ServerTreeLayoutPersistenceCoordinator layoutPersistenceCoordinator =
        new ServerTreeLayoutPersistenceCoordinator(
            Objects.requireNonNull(layoutPersistenceContext, "layoutPersistenceContext"));
    ServerTreeBuiltInLayoutOrchestrator builtInLayoutOrchestrator =
        new ServerTreeBuiltInLayoutOrchestrator(
            layoutApplier,
            layoutPersistenceCoordinator,
            Objects.requireNonNull(
                builtInLayoutOrchestratorContext, "builtInLayoutOrchestratorContext"));

    return new ServerTreeLayoutCollaborators(
        builtInVisibilityCoordinator,
        layoutApplier,
        layoutPersistenceCoordinator,
        builtInLayoutOrchestrator,
        builtInLayoutCoordinator,
        rootSiblingOrderCoordinator);
  }
}
