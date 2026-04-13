package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Encapsulates bouncer-detach eligibility policy for server/channel actions. */
@Component
public final class ServerTreeBouncerDetachPolicy {

  public interface Context {
    boolean isBouncerControlServer(String serverId);

    ConnectionState connectionStateForServer(String serverId);

    String backendIdForEphemeralServer(String serverId);

    boolean hasBouncerCapability(String serverId, String capability);
  }

  public static Context context(
      Map<String, Set<String>> bouncerControlServerIdsByBackendId,
      Function<String, ConnectionState> connectionStateForServer,
      Function<String, String> backendIdForEphemeralServer,
      BiPredicate<String, String> hasBouncerCapability) {
    Objects.requireNonNull(
        bouncerControlServerIdsByBackendId, "bouncerControlServerIdsByBackendId");
    Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    Objects.requireNonNull(backendIdForEphemeralServer, "backendIdForEphemeralServer");
    Objects.requireNonNull(hasBouncerCapability, "hasBouncerCapability");
    Map<String, Set<String>> normalizedControlServerIdsByBackendId =
        normalizedControlServerIdsByBackendId(bouncerControlServerIdsByBackendId);
    return new Context() {
      @Override
      public boolean isBouncerControlServer(String serverId) {
        String normalizedServerId = normalize(serverId);
        if (normalizedServerId.isEmpty()) {
          return false;
        }
        for (Set<String> controlServerIds : normalizedControlServerIdsByBackendId.values()) {
          if (controlServerIds != null && controlServerIds.contains(normalizedServerId)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return connectionStateForServer.apply(serverId);
      }

      @Override
      public String backendIdForEphemeralServer(String serverId) {
        return backendIdForEphemeralServer.apply(serverId);
      }

      @Override
      public boolean hasBouncerCapability(String serverId, String capability) {
        return hasBouncerCapability.test(serverId, capability);
      }
    };
  }

  public boolean supportsBouncerDetach(Context context, String serverId) {
    Objects.requireNonNull(context, "context");
    String sid = normalize(serverId);
    if (sid.isEmpty()) return false;
    if (context.connectionStateForServer(sid) != ConnectionState.CONNECTED) {
      return false;
    }
    String ephemeralBackendId = normalizeBackendId(context.backendIdForEphemeralServer(sid));
    if (!ephemeralBackendId.isEmpty() || context.isBouncerControlServer(sid)) {
      return true;
    }
    return context.hasBouncerCapability(sid, "soju.im/bouncer-networks")
        || context.hasBouncerCapability(sid, "znc.in/playback");
  }

  private static Map<String, Set<String>> normalizedControlServerIdsByBackendId(
      Map<String, Set<String>> source) {
    Map<String, Set<String>> normalized = new HashMap<>();
    Map<String, Set<String>> state =
        Objects.requireNonNull(source, "bouncerControlServerIdsByBackendId");
    for (var entry : state.entrySet()) {
      String backendId = normalizeBackendId(entry.getKey());
      if (backendId.isEmpty() || entry.getValue() == null) {
        continue;
      }
      normalized.put(backendId, entry.getValue());
    }
    return normalized;
  }

  private static String normalize(String value) {
    return ServerTreeConventions.normalize(value);
  }

  private static String normalizeBackendId(String value) {
    return Objects.toString(value, "").trim().toLowerCase(java.util.Locale.ROOT);
  }
}
