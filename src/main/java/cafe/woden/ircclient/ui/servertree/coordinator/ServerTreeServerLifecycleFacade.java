package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Facade for server-root lifecycle and server-status label updates. */
public final class ServerTreeServerLifecycleFacade {
  private final ServerTreeServerRootLifecycleManager serverRootLifecycleManager;
  private final ServerTreeStatusLabelManager statusLabelManager;
  private final ServerTreeStatusLabelManager.Context statusLabelManagerContext;

  public ServerTreeServerLifecycleFacade(
      ServerTreeServerRootLifecycleManager serverRootLifecycleManager,
      ServerTreeStatusLabelManager statusLabelManager,
      ServerTreeStatusLabelManager.Context statusLabelManagerContext) {
    this.serverRootLifecycleManager =
        Objects.requireNonNull(serverRootLifecycleManager, "serverRootLifecycleManager");
    this.statusLabelManager = Objects.requireNonNull(statusLabelManager, "statusLabelManager");
    this.statusLabelManagerContext =
        Objects.requireNonNull(statusLabelManagerContext, "statusLabelManagerContext");
  }

  public void removeServerRoot(String serverId) {
    serverRootLifecycleManager.removeServerRoot(serverId);
  }

  public ServerNodes addServerRoot(String serverId) {
    return serverRootLifecycleManager.addServerRoot(serverId);
  }

  public void updateBouncerControlLabels(Map<String, Set<String>> nextBouncerControlByBackendId) {
    statusLabelManager.updateBouncerControlLabels(
        statusLabelManagerContext, nextBouncerControlByBackendId);
  }
}
