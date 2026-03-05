package cafe.woden.ircclient.state.api;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for WHOIS response routing. */
@ApplicationLayer
public interface WhoisRoutingPort {

  void put(String serverId, String nick, TargetRef target);

  TargetRef remove(String serverId, String nick);

  TargetRef get(String serverId, String nick);

  void clearServer(String serverId);
}
