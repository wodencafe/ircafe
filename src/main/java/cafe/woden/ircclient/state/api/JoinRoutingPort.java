package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import java.time.Duration;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for join-origin routing. */
@ApplicationLayer
public interface JoinRoutingPort {

  void rememberOrigin(String serverId, String channel, TargetRef origin);

  TargetRef recentOriginIfFresh(String serverId, String channel, Duration maxAge);

  void clear(String serverId, String channel);

  void clearServer(String serverId);
}
