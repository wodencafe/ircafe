package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Channel/query target membership operations used by target coordination. */
@SecondaryPort
@ApplicationLayer
public interface IrcTargetMembershipPort {

  default Completable joinChannel(String serverId, String channel) {
    return Completable.complete();
  }

  default Completable partChannel(String serverId, String channel) {
    return partChannel(serverId, channel, null);
  }

  default Completable partChannel(String serverId, String channel, String reason) {
    return Completable.complete();
  }

  default Completable requestNames(String serverId, String channel) {
    return Completable.complete();
  }

  default Completable sendRaw(String serverId, String line) {
    return Completable.complete();
  }

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }
}
