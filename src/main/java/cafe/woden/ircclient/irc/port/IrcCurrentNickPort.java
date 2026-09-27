package cafe.woden.ircclient.irc.port;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Current nickname lookup used by app/domain services. */
@SecondaryPort
@ApplicationLayer
public interface IrcCurrentNickPort {

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }
}
