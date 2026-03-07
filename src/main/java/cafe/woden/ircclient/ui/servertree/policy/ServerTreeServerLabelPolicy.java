package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.bouncer.BouncerAutoConnectStore;
import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Encapsulates display-label and ephemeral server badge policy. */
public final class ServerTreeServerLabelPolicy {

  private final Map<String, String> serverDisplayNames;
  private final Set<String> ephemeralServerIds;
  private final Map<String, Map<String, String>> originByServerIdByBackendId;
  private final Map<String, BouncerAutoConnectStore> autoConnectStoreByBackendId;

  public ServerTreeServerLabelPolicy(
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      Map<String, String> genericOriginByServerId,
      GenericBouncerAutoConnectStore genericAutoConnect,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect) {
    this(
        serverDisplayNames,
        ephemeralServerIds,
        originByServerIdByBackend(
            sojuOriginByServerId, zncOriginByServerId, genericOriginByServerId),
        autoConnectStoresByBackend(genericAutoConnect, sojuAutoConnect, zncAutoConnect));
  }

  public ServerTreeServerLabelPolicy(
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Map<String, Map<String, String>> originByServerIdByBackendId,
      Map<String, BouncerAutoConnectStore> autoConnectStoreByBackendId) {
    this.serverDisplayNames = Objects.requireNonNull(serverDisplayNames, "serverDisplayNames");
    this.ephemeralServerIds = Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    this.originByServerIdByBackendId =
        normalizedOriginMap(
            Objects.requireNonNull(originByServerIdByBackendId, "originByServerIdByBackendId"));
    this.autoConnectStoreByBackendId =
        normalizedAutoConnectMap(
            Objects.requireNonNull(autoConnectStoreByBackendId, "autoConnectStoreByBackendId"));
  }

  public String prettyServerLabel(String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return id;

    String display = serverDisplayNames.getOrDefault(id, id);
    String backendId = backendIdForEphemeralServer(id);
    if (backendId == null || backendId.isBlank()) {
      return display;
    }
    String origin = originForServer(backendId, id);
    if (origin != null && !origin.isBlank() && isAutoConnectEnabled(backendId, origin, display)) {
      return display + " (auto)";
    }
    return display;
  }

  public boolean isSojuEphemeralServer(String serverId) {
    return isEphemeralServer(ServerTreeBouncerBackends.SOJU, serverId);
  }

  public boolean isZncEphemeralServer(String serverId) {
    return isEphemeralServer(ServerTreeBouncerBackends.ZNC, serverId);
  }

  public boolean isGenericEphemeralServer(String serverId) {
    return isEphemeralServer(ServerTreeBouncerBackends.GENERIC, serverId);
  }

  public String backendIdForEphemeralServer(String serverId) {
    for (String backendId : ServerTreeBouncerBackends.orderedIds()) {
      if (isEphemeralServer(backendId, serverId)) {
        return backendId;
      }
    }
    return null;
  }

  public boolean isEphemeralServer(String backendId, String serverId) {
    String id = normalize(serverId);
    String prefix = normalize(ServerTreeBouncerBackends.prefixFor(backendId));
    return !id.isEmpty()
        && !prefix.isEmpty()
        && id.startsWith(prefix)
        && ephemeralServerIds.contains(id);
  }

  public String originForServer(String backendId, String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return null;
    Map<String, String> originByServerId = originMapForBackend(backendId);
    String origin = originByServerId == null ? null : originByServerId.get(id);
    if (origin != null && !origin.isBlank()) {
      return origin;
    }
    return parseOrigin(id, ServerTreeBouncerBackends.prefixFor(backendId));
  }

  public boolean isAutoConnectEnabled(String backendId, String originId, String networkKey) {
    String backend = normalize(backendId);
    String origin = normalize(originId);
    String network = normalize(networkKey);
    if (backend.isEmpty() || origin.isEmpty() || network.isEmpty()) {
      return false;
    }
    BouncerAutoConnectStore store = autoConnectStoreByBackendId.get(backend);
    return store != null && store.isEnabled(origin, network);
  }

  private Map<String, String> originMapForBackend(String backendId) {
    String backend = normalize(backendId);
    if (backend.isEmpty()) return null;
    return originByServerIdByBackendId.get(backend);
  }

  private static String parseOrigin(String serverId, String prefix) {
    String id = normalize(serverId);
    String p = normalize(prefix);
    if (id.isEmpty() || p.isEmpty() || !id.startsWith(p)) return null;
    int start = p.length();
    int nextColon = id.indexOf(':', start);
    if (nextColon <= start) return null;
    String origin = id.substring(start, nextColon).trim();
    return origin.isEmpty() ? null : origin;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  private static Map<String, Map<String, String>> originByServerIdByBackend(
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      Map<String, String> genericOriginByServerId) {
    Map<String, Map<String, String>> origins = new LinkedHashMap<>();
    origins.put(
        ServerTreeBouncerBackends.SOJU,
        Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId"));
    origins.put(
        ServerTreeBouncerBackends.ZNC,
        Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId"));
    origins.put(
        ServerTreeBouncerBackends.GENERIC,
        Objects.requireNonNull(genericOriginByServerId, "genericOriginByServerId"));
    return origins;
  }

  private static Map<String, BouncerAutoConnectStore> autoConnectStoresByBackend(
      GenericBouncerAutoConnectStore genericAutoConnect,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect) {
    Map<String, BouncerAutoConnectStore> stores = new LinkedHashMap<>();
    if (sojuAutoConnect != null) {
      stores.put(ServerTreeBouncerBackends.SOJU, sojuAutoConnect);
    }
    if (zncAutoConnect != null) {
      stores.put(ServerTreeBouncerBackends.ZNC, zncAutoConnect);
    }
    if (genericAutoConnect != null) {
      stores.put(ServerTreeBouncerBackends.GENERIC, genericAutoConnect);
    }
    return stores;
  }

  private static Map<String, Map<String, String>> normalizedOriginMap(
      Map<String, Map<String, String>> source) {
    Map<String, Map<String, String>> normalized = new LinkedHashMap<>();
    for (var entry : source.entrySet()) {
      String backend = normalize(entry.getKey());
      if (backend.isEmpty() || entry.getValue() == null) continue;
      normalized.put(backend, entry.getValue());
    }
    return normalized;
  }

  private static Map<String, BouncerAutoConnectStore> normalizedAutoConnectMap(
      Map<String, BouncerAutoConnectStore> source) {
    Map<String, BouncerAutoConnectStore> normalized = new LinkedHashMap<>();
    for (var entry : source.entrySet()) {
      String backend = normalize(entry.getKey());
      if (backend.isEmpty() || entry.getValue() == null) continue;
      normalized.put(backend, entry.getValue());
    }
    return normalized;
  }
}
