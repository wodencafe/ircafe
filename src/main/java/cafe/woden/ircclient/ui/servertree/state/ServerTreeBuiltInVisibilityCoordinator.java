package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeBuiltInVisibilityConfigPort.ServerTreeBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Manages default/per-server built-in node visibility state and persistence. */
public final class ServerTreeBuiltInVisibilityCoordinator {

  public interface Context {
    String normalizeServerId(String serverId);

    Set<String> currentServerIds();

    void syncUiLeafVisibility();
  }

  public static Context context(
      Function<String, String> normalizeServerId,
      Supplier<Set<String>> currentServerIds,
      Runnable syncUiLeafVisibility) {
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(currentServerIds, "currentServerIds");
    Objects.requireNonNull(syncUiLeafVisibility, "syncUiLeafVisibility");
    return new Context() {
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
    };
  }

  private final ServerTreeBuiltInVisibilityConfigPort runtimeConfig;
  private final Context context;

  private ServerBuiltInNodesVisibility defaultVisibility = ServerBuiltInNodesVisibility.defaults();
  private final Map<String, ServerBuiltInNodesVisibility> visibilityByServer = new HashMap<>();

  public ServerTreeBuiltInVisibilityCoordinator(
      ServerTreeBuiltInVisibilityConfigPort runtimeConfig, Context context) {
    this.runtimeConfig = runtimeConfig;
    this.context = Objects.requireNonNull(context, "context");
  }

  public void loadPersistedBuiltInNodesVisibility() {
    if (runtimeConfig == null) return;
    try {
      Map<String, ServerTreeBuiltInNodesVisibility> persisted =
          runtimeConfig.readServerTreeBuiltInNodesVisibility();
      if (persisted == null || persisted.isEmpty()) return;
      for (Map.Entry<String, ServerTreeBuiltInNodesVisibility> entry : persisted.entrySet()) {
        String sid = context.normalizeServerId(entry.getKey());
        if (sid.isEmpty()) continue;
        ServerTreeBuiltInNodesVisibility raw = entry.getValue();
        ServerBuiltInNodesVisibility parsed =
            raw == null
                ? defaultVisibility
                : new ServerBuiltInNodesVisibility(
                    raw.server(),
                    raw.notifications(),
                    raw.logViewer(),
                    raw.monitor(),
                    raw.interceptors());
        if (parsed.equals(defaultVisibility)) {
          visibilityByServer.remove(sid);
        } else {
          visibilityByServer.put(sid, parsed);
        }
      }
    } catch (Exception ignored) {
    }
  }

  public ServerBuiltInNodesVisibility defaultVisibility() {
    return defaultVisibility;
  }

  public void setDefaultVisibility(ServerBuiltInNodesVisibility next) {
    if (next == null) return;
    defaultVisibility = next;
  }

  public ServerBuiltInNodesVisibility builtInNodesVisibility(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return defaultVisibility;
    return visibilityByServer.getOrDefault(sid, defaultVisibility);
  }

  public void applyBuiltInNodesVisibilityGlobally(
      java.util.function.UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
    if (mutator == null) return;
    Set<String> allServerIds = new HashSet<>(context.currentServerIds());
    allServerIds.addAll(visibilityByServer.keySet());
    for (String sid : allServerIds) {
      if (sid == null || sid.isBlank()) continue;
      ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
      ServerBuiltInNodesVisibility next =
          Objects.requireNonNullElse(mutator.apply(current), current);
      applyBuiltInNodesVisibilityForServer(sid, next, true, false);
    }
    context.syncUiLeafVisibility();
  }

  public void applyBuiltInNodesVisibilityForServer(
      String serverId, ServerBuiltInNodesVisibility next, boolean persist, boolean syncUi) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty() || next == null) return;

    ServerBuiltInNodesVisibility current = builtInNodesVisibility(sid);
    if (current.equals(next)) return;

    if (next.equals(defaultVisibility)) {
      visibilityByServer.remove(sid);
    } else {
      visibilityByServer.put(sid, next);
    }

    if (persist && runtimeConfig != null) {
      runtimeConfig.rememberServerTreeBuiltInNodesVisibility(sid, next.toRuntimeVisibility());
    }

    if (syncUi) {
      context.syncUiLeafVisibility();
    }
  }
}
