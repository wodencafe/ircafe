package cafe.woden.ircclient.app.state;

import cafe.woden.ircclient.app.TargetRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks away state per server, plus (optionally) the most recent tab where the user initiated
 * an /away command so confirmations (305/306) can be printed where the user expects.
 */
@Component
public class AwayRoutingState {

  // Away state tracking (per server) so bare `/away` can toggle between set/clear.
  private final ConcurrentHashMap<String, Boolean> awayByServer = new ConcurrentHashMap<>();

  // Last away reason (per server). Used to decorate server confirmation numerics (306) with the
  // reason the user supplied (or the default).
  private final ConcurrentHashMap<String, String> awayReasonByServer = new ConcurrentHashMap<>();

  // Route away confirmations back to where the user initiated the /away command.
  private final ConcurrentHashMap<String, RecentTarget> recentAwayTargets = new ConcurrentHashMap<>();

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

  /** Drop all stored state for a server (e.g., on disconnect). */
  public void clearServer(String serverId) {
    String sid = normalizeServer(serverId);
    awayByServer.remove(sid);
    awayReasonByServer.remove(sid);
    recentAwayTargets.remove(sid);
  }
}
