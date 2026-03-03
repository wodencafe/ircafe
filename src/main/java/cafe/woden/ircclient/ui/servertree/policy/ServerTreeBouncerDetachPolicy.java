package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Encapsulates bouncer-detach eligibility policy for server/channel actions. */
public final class ServerTreeBouncerDetachPolicy {

  public interface Context {
    ConnectionState connectionStateForServer(String serverId);

    boolean isSojuEphemeralServer(String serverId);

    boolean isZncEphemeralServer(String serverId);

    boolean hasBouncerCapability(String serverId, String capability);
  }

  public static Context context(
      Function<String, ConnectionState> connectionStateForServer,
      Predicate<String> isSojuEphemeralServer,
      Predicate<String> isZncEphemeralServer,
      BiPredicate<String, String> hasBouncerCapability) {
    Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    Objects.requireNonNull(isSojuEphemeralServer, "isSojuEphemeralServer");
    Objects.requireNonNull(isZncEphemeralServer, "isZncEphemeralServer");
    Objects.requireNonNull(hasBouncerCapability, "hasBouncerCapability");
    return new Context() {
      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return connectionStateForServer.apply(serverId);
      }

      @Override
      public boolean isSojuEphemeralServer(String serverId) {
        return isSojuEphemeralServer.test(serverId);
      }

      @Override
      public boolean isZncEphemeralServer(String serverId) {
        return isZncEphemeralServer.test(serverId);
      }

      @Override
      public boolean hasBouncerCapability(String serverId, String capability) {
        return hasBouncerCapability.test(serverId, capability);
      }
    };
  }

  private final Set<String> sojuBouncerControlServerIds;
  private final Set<String> zncBouncerControlServerIds;
  private final Context context;

  public ServerTreeBouncerDetachPolicy(
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Context context) {
    this.sojuBouncerControlServerIds =
        Objects.requireNonNull(sojuBouncerControlServerIds, "sojuBouncerControlServerIds");
    this.zncBouncerControlServerIds =
        Objects.requireNonNull(zncBouncerControlServerIds, "zncBouncerControlServerIds");
    this.context = Objects.requireNonNull(context, "context");
  }

  public boolean supportsBouncerDetach(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) return false;
    if (context.connectionStateForServer(sid) != ConnectionState.CONNECTED) {
      return false;
    }
    if (context.isSojuEphemeralServer(sid)
        || context.isZncEphemeralServer(sid)
        || sojuBouncerControlServerIds.contains(sid)
        || zncBouncerControlServerIds.contains(sid)) {
      return true;
    }
    return context.hasBouncerCapability(sid, "soju.im/bouncer-networks")
        || context.hasBouncerCapability(sid, "znc.in/playback");
  }

  private static String normalize(String value) {
    return ServerTreeConventions.normalize(value);
  }
}
