package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetLifecycleCoordinator;
import cafe.woden.ircclient.ui.util.TreeNodeActions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.stereotype.Component;

/**
 * Spring-managed assembler for the server-tree collaborator bundles that remain per-tree runtime
 * objects.
 */
@Component
@InterfaceLayer
@RequiredArgsConstructor
public final class ServerTreeCompositionAssembler {

  @NonNull private final ServerTreeLayoutCollaboratorsFactory layoutCollaboratorsFactory;

  @NonNull
  private final ServerTreeStateInteractionCollaboratorsFactory stateInteractionCollaboratorsFactory;

  @NonNull
  private final ServerTreeViewInteractionCollaboratorsFactory viewInteractionCollaboratorsFactory;

  @NonNull
  private final ServerTreeLifecycleSettingsCollaboratorsFactory
      lifecycleSettingsCollaboratorsFactory;

  @NonNull
  private final ServerTreeTargetLifecycleCoordinatorFactory targetLifecycleCoordinatorFactory;

  @NonNull
  private final ServerTreeChannelInteractionCollaboratorsFactory
      channelInteractionCollaboratorsFactory;

  @NonNull private final ServerTreeTreeInteractionBindingsFactory treeInteractionBindingsFactory;

  public ServerTreeLayoutCollaborators createLayoutCollaborators(
      cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort builtInVisibilityConfig,
      cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator.Context
          builtInVisibilityContext,
      cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutPersistenceCoordinator.Context
          layoutPersistenceContext,
      cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutOrchestrator.Context
          builtInLayoutOrchestratorContext) {
    return layoutCollaboratorsFactory.create(
        builtInVisibilityConfig,
        builtInVisibilityContext,
        layoutPersistenceContext,
        builtInLayoutOrchestratorContext);
  }

  public ServerTreeStateInteractionCollaborators createStateInteractionCollaborators(
      ServerTreeStateInteractionCollaboratorsFactory.Inputs inputs) {
    return stateInteractionCollaboratorsFactory.create(inputs);
  }

  public ServerTreeViewInteractionCollaborators createViewInteractionCollaborators(
      ServerTreeViewInteractionCollaboratorsFactory.Inputs inputs) {
    return viewInteractionCollaboratorsFactory.create(inputs);
  }

  public ServerTreeLifecycleSettingsCollaborators createLifecycleSettingsCollaborators(
      ServerTreeLifecycleSettingsCollaboratorsFactory.Inputs inputs) {
    return lifecycleSettingsCollaboratorsFactory.create(inputs);
  }

  public ServerTreeTargetLifecycleCoordinator createTargetLifecycleCoordinator(
      ServerTreeTargetLifecycleCoordinatorFactory.Inputs inputs) {
    return targetLifecycleCoordinatorFactory.create(inputs);
  }

  public ServerTreeChannelInteractionCollaborators createChannelInteractionCollaborators(
      ServerTreeChannelInteractionCollaboratorsFactory.Inputs inputs) {
    return channelInteractionCollaboratorsFactory.create(inputs);
  }

  public TreeNodeActions<TargetRef> createTreeInteractionBindings(
      ServerTreeTreeInteractionBindingsFactory.Inputs inputs) {
    return treeInteractionBindingsFactory.create(inputs);
  }
}
