package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for CTCP response routing. */
@ApplicationLayer
public interface CtcpRoutingPort {

  record PendingCtcp(TargetRef target, long startedMs) {}

  void put(String serverId, String nick, String command, String token, TargetRef target);

  PendingCtcp remove(String serverId, String nick, String command, String token);

  PendingCtcp get(String serverId, String nick, String command, String token);

  void clearServer(String serverId);
}
