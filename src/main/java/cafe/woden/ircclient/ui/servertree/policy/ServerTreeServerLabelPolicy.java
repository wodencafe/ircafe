package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.bouncer.BouncerAutoConnectStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Encapsulates display-label and ephemeral server badge policy. */
@Component
public final class ServerTreeServerLabelPolicy {

  public interface Context {
    String displayNameForServer(String serverId);

    boolean isEphemeralServer(String backendId, String serverId);

    String mappedOriginForServer(String backendId, String serverId);

    boolean isAutoConnectEnabled(String backendId, String originId, String networkKey);
  }

  public static Context context(
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Map<String, Map<String, String>> originByServerIdByBackendId,
      Map<String, BouncerAutoConnectStore> autoConnectStoreByBackendId) {
    Objects.requireNonNull(serverDisplayNames, "serverDisplayNames");
    Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    Map<String, Map<String, String>> normalizedOrigins =
        normalizedOriginMap(
            Objects.requireNonNull(originByServerIdByBackendId, "originByServerIdByBackendId"));
    Map<String, BouncerAutoConnectStore> normalizedAutoConnectStores =
        normalizedAutoConnectMap(
            Objects.requireNonNull(autoConnectStoreByBackendId, "autoConnectStoreByBackendId"));
    return new Context() {
      @Override
      public String displayNameForServer(String serverId) {
        String id = normalize(serverId);
        return serverDisplayNames.getOrDefault(id, id);
      }

      @Override
      public boolean isEphemeralServer(String backendId, String serverId) {
        String id = normalize(serverId);
        String prefix = normalize(ServerTreeBouncerBackends.prefixFor(backendId));
        return !id.isEmpty()
            && !prefix.isEmpty()
            && id.startsWith(prefix)
            && ephemeralServerIds.contains(id);
      }

      @Override
      public String mappedOriginForServer(String backendId, String serverId) {
        String backend = normalize(backendId);
        String id = normalize(serverId);
        if (backend.isEmpty() || id.isEmpty()) {
          return null;
        }
        Map<String, String> originByServerId = normalizedOrigins.get(backend);
        return originByServerId == null ? null : originByServerId.get(id);
      }

      @Override
      public boolean isAutoConnectEnabled(String backendId, String originId, String networkKey) {
        String backend = normalize(backendId);
        String origin = normalize(originId);
        String network = normalize(networkKey);
        if (backend.isEmpty() || origin.isEmpty() || network.isEmpty()) {
          return false;
        }
        BouncerAutoConnectStore store = normalizedAutoConnectStores.get(backend);
        return store != null && store.isEnabled(origin, network);
      }
    };
  }

  public String prettyServerLabel(Context context, String serverId) {
    Objects.requireNonNull(context, "context");
    String id = normalize(serverId);
    if (id.isEmpty()) return id;

    String display = context.displayNameForServer(id);
    String backendId = backendIdForEphemeralServer(context, id);
    if (backendId == null || backendId.isBlank()) {
      return display;
    }
    String origin = originForServer(context, backendId, id);
    if (origin != null
        && !origin.isBlank()
        && isAutoConnectEnabled(context, backendId, origin, display)) {
      return display + " (auto)";
    }
    return display;
  }

  public boolean isSojuEphemeralServer(Context context, String serverId) {
    return isEphemeralServer(context, ServerTreeBouncerBackends.SOJU, serverId);
  }

  public boolean isZncEphemeralServer(Context context, String serverId) {
    return isEphemeralServer(context, ServerTreeBouncerBackends.ZNC, serverId);
  }

  public boolean isGenericEphemeralServer(Context context, String serverId) {
    return isEphemeralServer(context, ServerTreeBouncerBackends.GENERIC, serverId);
  }

  public String backendIdForEphemeralServer(Context context, String serverId) {
    Objects.requireNonNull(context, "context");
    for (String backendId : ServerTreeBouncerBackends.orderedIds()) {
      if (isEphemeralServer(context, backendId, serverId)) {
        return backendId;
      }
    }
    return null;
  }

  public boolean isEphemeralServer(Context context, String backendId, String serverId) {
    Objects.requireNonNull(context, "context");
    return context.isEphemeralServer(backendId, serverId);
  }

  public String originForServer(Context context, String backendId, String serverId) {
    Objects.requireNonNull(context, "context");
    String id = normalize(serverId);
    if (id.isEmpty()) return null;
    String origin = context.mappedOriginForServer(backendId, id);
    if (origin != null && !origin.isBlank()) {
      return origin;
    }
    return parseOrigin(id, ServerTreeBouncerBackends.prefixFor(backendId));
  }

  public boolean isAutoConnectEnabled(
      Context context, String backendId, String originId, String networkKey) {
    Objects.requireNonNull(context, "context");
    return context.isAutoConnectEnabled(backendId, originId, networkKey);
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
