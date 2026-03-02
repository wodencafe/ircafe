package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Adapter for {@link ServerTreeBouncerDetachPolicy.Context}. */
public final class ServerTreeBouncerDetachPolicyContextAdapter
    implements ServerTreeBouncerDetachPolicy.Context {

  private final Function<String, ConnectionState> connectionStateForServer;
  private final Predicate<String> isSojuEphemeralServer;
  private final Predicate<String> isZncEphemeralServer;
  private final BiPredicate<String, String> hasBouncerCapability;

  public ServerTreeBouncerDetachPolicyContextAdapter(
      Function<String, ConnectionState> connectionStateForServer,
      Predicate<String> isSojuEphemeralServer,
      Predicate<String> isZncEphemeralServer,
      BiPredicate<String, String> hasBouncerCapability) {
    this.connectionStateForServer =
        Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    this.isSojuEphemeralServer =
        Objects.requireNonNull(isSojuEphemeralServer, "isSojuEphemeralServer");
    this.isZncEphemeralServer =
        Objects.requireNonNull(isZncEphemeralServer, "isZncEphemeralServer");
    this.hasBouncerCapability =
        Objects.requireNonNull(hasBouncerCapability, "hasBouncerCapability");
  }

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
}
