package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Encapsulates display-label and ephemeral server badge policy. */
public final class ServerTreeServerLabelPolicy {

  private final Map<String, String> serverDisplayNames;
  private final Set<String> ephemeralServerIds;
  private final Map<String, String> sojuOriginByServerId;
  private final Map<String, String> zncOriginByServerId;
  private final Map<String, String> genericOriginByServerId;
  private final GenericBouncerAutoConnectStore genericAutoConnect;
  private final SojuAutoConnectStore sojuAutoConnect;
  private final ZncAutoConnectStore zncAutoConnect;

  public ServerTreeServerLabelPolicy(
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      Map<String, String> genericOriginByServerId,
      GenericBouncerAutoConnectStore genericAutoConnect,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect) {
    this.serverDisplayNames = Objects.requireNonNull(serverDisplayNames, "serverDisplayNames");
    this.ephemeralServerIds = Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    this.sojuOriginByServerId =
        Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId");
    this.zncOriginByServerId = Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId");
    this.genericOriginByServerId =
        Objects.requireNonNull(genericOriginByServerId, "genericOriginByServerId");
    this.genericAutoConnect = genericAutoConnect;
    this.sojuAutoConnect = sojuAutoConnect;
    this.zncAutoConnect = zncAutoConnect;
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
    if (ServerTreeBouncerBackends.SOJU.equals(backend)) {
      return sojuAutoConnect != null && sojuAutoConnect.isEnabled(origin, network);
    }
    if (ServerTreeBouncerBackends.ZNC.equals(backend)) {
      return zncAutoConnect != null && zncAutoConnect.isEnabled(origin, network);
    }
    if (ServerTreeBouncerBackends.GENERIC.equals(backend)) {
      return genericAutoConnect != null && genericAutoConnect.isEnabled(origin, network);
    }
    return false;
  }

  private Map<String, String> originMapForBackend(String backendId) {
    String backend = normalize(backendId);
    if (ServerTreeBouncerBackends.SOJU.equals(backend)) {
      return sojuOriginByServerId;
    }
    if (ServerTreeBouncerBackends.ZNC.equals(backend)) {
      return zncOriginByServerId;
    }
    if (ServerTreeBouncerBackends.GENERIC.equals(backend)) {
      return genericOriginByServerId;
    }
    return null;
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
}
