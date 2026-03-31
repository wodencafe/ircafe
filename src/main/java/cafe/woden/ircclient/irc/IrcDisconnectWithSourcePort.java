package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Optional extension for transports that need to know why a disconnect was requested. */
@SecondaryPort
@ApplicationLayer
public interface IrcDisconnectWithSourcePort {

  Completable disconnect(String serverId, String reason, DisconnectRequestSource source);
}
