package cafe.woden.ircclient.irc.port;

import cafe.woden.ircclient.irc.DisconnectRequestSource;
import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Connection lifecycle operations used by connection orchestration. */
@SecondaryPort
@ApplicationLayer
public interface IrcConnectionLifecyclePort {

  Completable connect(String serverId);

  Completable disconnect(String serverId);

  default Completable disconnect(String serverId, String reason) {
    return disconnect(serverId);
  }

  default Completable disconnect(String serverId, String reason, DisconnectRequestSource source) {
    return disconnect(serverId, reason);
  }

  Optional<String> currentNick(String serverId);
}
