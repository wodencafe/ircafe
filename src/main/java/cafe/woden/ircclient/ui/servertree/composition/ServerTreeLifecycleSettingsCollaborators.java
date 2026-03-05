package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLifecycleFacade;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerRootLifecycleManager;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeSettingsSynchronizer;

/** Container for lifecycle and settings collaborators wired during server tree construction. */
public record ServerTreeLifecycleSettingsCollaborators(
    ServerTreeServerRootLifecycleManager serverRootLifecycleManager,
    ServerTreeServerLifecycleFacade serverLifecycleFacade,
    ServerTreeSettingsSynchronizer settingsSynchronizer) {}
