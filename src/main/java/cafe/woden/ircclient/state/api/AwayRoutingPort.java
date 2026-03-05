package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import java.time.Duration;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for away-state routing and correlation. */
@ApplicationLayer
public interface AwayRoutingPort {

  boolean isAway(String serverId);

  void setAway(String serverId, boolean away);

  String getLastReason(String serverId);

  void setLastReason(String serverId, String reason);

  void rememberOrigin(String serverId, TargetRef target);

  TargetRef recentOriginIfFresh(String serverId, Duration maxAge);

  void clearServer(String serverId);
}
