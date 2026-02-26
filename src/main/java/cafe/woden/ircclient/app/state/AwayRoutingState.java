package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.app.api.TargetRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@ApplicationLayer
public class AwayRoutingState {

  // Away state tracking (per server) so bare `/away` can toggle between set/clear.
  private final ConcurrentHashMap<String, Boolean> awayByServer = new ConcurrentHashMap<>();

  // Last away reason (per server). Used to decorate server confirmation numerics (306) with the
  // reason the user supplied (or the default).
  private final ConcurrentHashMap<String, String> awayReasonByServer = new ConcurrentHashMap<>();

  // Route away confirmations back to where the user initiated the /away command.
  private final ConcurrentHashMap<String, RecentTarget> recentAwayTargets =
      new ConcurrentHashMap<>();

  private record RecentTarget(TargetRef target, Instant at) {}

  private static String normalizeServer(String serverId) {
    return (serverId == null) ? "" : serverId.trim();
  }

  public boolean isAway(String serverId) {
    return awayByServer.getOrDefault(normalizeServer(serverId), false);
  }

  public void setAway(String serverId, boolean away) {
    awayByServer.put(normalizeServer(serverId), away);
  }

  public String getLastReason(String serverId) {
    return awayReasonByServer.get(normalizeServer(serverId));
  }

  public void setLastReason(String serverId, String reason) {
    String sid = normalizeServer(serverId);
    if (reason == null) {
      awayReasonByServer.remove(sid);
    } else {
      awayReasonByServer.put(sid, reason);
    }
  }

  public void rememberOrigin(String serverId, TargetRef target) {
    if (target == null) return;
    recentAwayTargets.put(normalizeServer(serverId), new RecentTarget(target, Instant.now()));
  }

  public TargetRef recentOriginIfFresh(String serverId, Duration maxAge) {
    Objects.requireNonNull(maxAge, "maxAge");
    RecentTarget rt = recentAwayTargets.get(normalizeServer(serverId));
    if (rt == null) return null;
    if (Duration.between(rt.at(), Instant.now()).compareTo(maxAge) <= 0) {
      return rt.target();
    }
    return null;
  }

  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    awayByServer.remove(sid);
    awayReasonByServer.remove(sid);
    recentAwayTargets.remove(sid);
  }
}
