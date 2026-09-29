package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Channel/query target membership operations used by target coordination. */
@SecondaryPort
@ApplicationLayer
public interface IrcTargetMembershipPort {

  Completable joinChannel(String serverId, String channel);

  default Completable partChannel(String serverId, String channel) {
    return partChannel(serverId, channel, null);
  }

  Completable partChannel(String serverId, String channel, String reason);

  Completable requestNames(String serverId, String channel);

  Completable sendRaw(String serverId, String line);

  Optional<String> currentNick(String serverId);
}
