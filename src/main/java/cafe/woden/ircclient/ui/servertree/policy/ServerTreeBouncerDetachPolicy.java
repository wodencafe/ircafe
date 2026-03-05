package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Encapsulates bouncer-detach eligibility policy for server/channel actions. */
public final class ServerTreeBouncerDetachPolicy {

  public interface Context {
    ConnectionState connectionStateForServer(String serverId);

    String backendIdForEphemeralServer(String serverId);

    boolean hasBouncerCapability(String serverId, String capability);
  }

  public static Context context(
      Function<String, ConnectionState> connectionStateForServer,
      Function<String, String> backendIdForEphemeralServer,
      BiPredicate<String, String> hasBouncerCapability) {
    Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    Objects.requireNonNull(backendIdForEphemeralServer, "backendIdForEphemeralServer");
    Objects.requireNonNull(hasBouncerCapability, "hasBouncerCapability");
    return new Context() {
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

  private final Map<String, Set<String>> bouncerControlServerIdsByBackendId;
  private final Context context;

  public ServerTreeBouncerDetachPolicy(
      Map<String, Set<String>> bouncerControlServerIdsByBackendId, Context context) {
    this.bouncerControlServerIdsByBackendId =
        Objects.requireNonNull(
            bouncerControlServerIdsByBackendId, "bouncerControlServerIdsByBackendId");
    this.context = Objects.requireNonNull(context, "context");
  }

  public ServerTreeBouncerDetachPolicy(
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Context context) {
    this(
        bouncerControlStateByBackend(sojuBouncerControlServerIds, zncBouncerControlServerIds),
        context);
  }

  public boolean supportsBouncerDetach(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return false;
    if (context.connectionStateForServer(sid) != ConnectionState.CONNECTED) {
      return false;
    }
    String ephemeralBackendId = normalizeBackendId(context.backendIdForEphemeralServer(sid));
    if (!ephemeralBackendId.isEmpty() || isBouncerControlServer(sid)) {
      return true;
    }
    return context.hasBouncerCapability(sid, "soju.im/bouncer-networks")
        || context.hasBouncerCapability(sid, "znc.in/playback");
  }

  private boolean isBouncerControlServer(String serverId) {
    for (Set<String> controlServerIds : bouncerControlServerIdsByBackendId.values()) {
      if (controlServerIds != null && controlServerIds.contains(serverId)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Set<String>> bouncerControlStateByBackend(
      Set<String> sojuBouncerControlServerIds, Set<String> zncBouncerControlServerIds) {
    Map<String, Set<String>> state = new HashMap<>();
    state.put(
        ServerTreeBouncerBackends.SOJU,
        Objects.requireNonNull(sojuBouncerControlServerIds, "sojuBouncerControlServerIds"));
    state.put(
        ServerTreeBouncerBackends.ZNC,
        Objects.requireNonNull(zncBouncerControlServerIds, "zncBouncerControlServerIds"));
    return state;
  }

  private static String normalize(String value) {
    return ServerTreeConventions.normalize(value);
  }

  private static String normalizeBackendId(String value) {
    return Objects.toString(value, "").trim().toLowerCase(java.util.Locale.ROOT);
  }
}
