package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.ConnectionState;
import java.util.Objects;
import java.util.Set;

/** Encapsulates bouncer-detach eligibility policy for server/channel actions. */
public final class ServerTreeBouncerDetachPolicy {

  public interface Context {
    ConnectionState connectionStateForServer(String serverId);

    boolean isSojuEphemeralServer(String serverId);

    boolean isZncEphemeralServer(String serverId);

    boolean hasBouncerCapability(String serverId, String capability);
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
    return Objects.toString(value, "").trim();
  }
}
