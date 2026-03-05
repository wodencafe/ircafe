package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for pending MODE query routing. */
@ApplicationLayer
public interface ModeRoutingPort {

  void putPendingModeTarget(String serverId, String channel, TargetRef target);

  TargetRef removePendingModeTarget(String serverId, String channel);

  TargetRef getPendingModeTarget(String serverId, String channel);

  void clearServer(String serverId);
}
