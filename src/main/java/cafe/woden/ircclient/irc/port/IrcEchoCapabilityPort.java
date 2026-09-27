package cafe.woden.ircclient.irc.port;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Echo-message capability/readiness API used by outbound message services. */
@SecondaryPort
@ApplicationLayer
public interface IrcEchoCapabilityPort {

  default boolean isEchoMessageAvailable(String serverId) {
    return false;
  }
}
