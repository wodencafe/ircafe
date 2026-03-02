package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Adapter for {@link ServerTreeBuiltInVisibilityCoordinator.Context}. */
public final class ServerTreeBuiltInVisibilityContextAdapter
    implements ServerTreeBuiltInVisibilityCoordinator.Context {

  private final Function<String, String> normalizeServerId;
  private final Supplier<Set<String>> currentServerIds;
  private final Runnable syncUiLeafVisibility;

  public ServerTreeBuiltInVisibilityContextAdapter(
      Function<String, String> normalizeServerId,
      Supplier<Set<String>> currentServerIds,
      Runnable syncUiLeafVisibility) {
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.currentServerIds = Objects.requireNonNull(currentServerIds, "currentServerIds");
    this.syncUiLeafVisibility =
        Objects.requireNonNull(syncUiLeafVisibility, "syncUiLeafVisibility");
  }

  @Override
  public String normalizeServerId(String serverId) {
    return normalizeServerId.apply(serverId);
  }

  @Override
  public Set<String> currentServerIds() {
    return currentServerIds.get();
  }

  @Override
  public void syncUiLeafVisibility() {
    syncUiLeafVisibility.run();
  }
}
