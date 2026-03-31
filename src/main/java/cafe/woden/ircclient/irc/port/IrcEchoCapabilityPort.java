package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.IrcClientService;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Echo-message capability/readiness API used by outbound message services. */
@SecondaryPort
@ApplicationLayer
public interface IrcEchoCapabilityPort {

  default boolean isEchoMessageAvailable(String serverId) {
    return false;
  }

  static IrcEchoCapabilityPort from(IrcClientService irc) {
    if (irc instanceof IrcEchoCapabilityPort port) {
      return port;
    }
    if (irc == null) {
      return new IrcEchoCapabilityPort() {};
    }
    return new IrcEchoCapabilityPort() {
      @Override
      public boolean isEchoMessageAvailable(String serverId) {
        return irc.isEchoMessageAvailable(serverId);
      }
    };
  }
}
