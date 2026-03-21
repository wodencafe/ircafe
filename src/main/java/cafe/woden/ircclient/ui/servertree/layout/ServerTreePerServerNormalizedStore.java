package cafe.woden.ircclient.ui.servertree.layout;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Shared per-server normalized state/persistence helper for server-tree layout coordinators. */
final class ServerTreePerServerNormalizedStore<T> {

  interface Persistence<T> {
    Map<String, T> read(ServerTreeLayoutConfigPort runtimeConfig);

    void write(ServerTreeLayoutConfigPort runtimeConfig, String serverId, T value);
  }

  private final ServerTreeLayoutConfigPort runtimeConfig;
  private final T defaults;
  private final UnaryOperator<T> normalize;
  private final Persistence<T> persistence;
  private final Map<String, T> byServer = new HashMap<>();

  ServerTreePerServerNormalizedStore(
      ServerTreeLayoutConfigPort runtimeConfig,
      T defaults,
      UnaryOperator<T> normalize,
      Persistence<T> persistence) {
    this.runtimeConfig = runtimeConfig;
    this.defaults = Objects.requireNonNull(defaults, "defaults");
    this.normalize = Objects.requireNonNull(normalize, "normalize");
    this.persistence = Objects.requireNonNull(persistence, "persistence");
    loadPersisted();
  }

  T valueForServer(String serverId) {
    String sid = ServerTreeConventions.normalizeServerId(serverId);
    if (sid.isEmpty()) return defaults;
    return byServer.getOrDefault(sid, defaults);
  }

  void remember(String serverId, T value) {
    String sid = ServerTreeConventions.normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    T normalized = normalize.apply(value == null ? defaults : value);
    if (defaults.equals(normalized)) {
      byServer.remove(sid);
    } else {
      byServer.put(sid, normalized);
    }

    if (runtimeConfig != null) {
      persistence.write(runtimeConfig, sid, normalized);
    }
  }

  private void loadPersisted() {
    if (runtimeConfig == null) return;
    try {
      Map<String, T> persisted = persistence.read(runtimeConfig);
      if (persisted == null || persisted.isEmpty()) return;
      for (Map.Entry<String, T> entry : persisted.entrySet()) {
        String sid = ServerTreeConventions.normalizeServerId(entry.getKey());
        if (sid.isEmpty()) continue;
        T normalized = normalize.apply(entry.getValue());
        if (defaults.equals(normalized)) {
          byServer.remove(sid);
        } else {
          byServer.put(sid, normalized);
        }
      }
    } catch (Exception ignored) {
    }
  }
}
