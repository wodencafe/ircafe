package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Negotiated MONITOR capacity and command delivery, scoped to a server. */
@SecondaryPort
@ApplicationLayer
public interface IrcMonitorPort {

  /** Return the backend's negotiated MONITOR limit. */
  int negotiatedMonitorLimit(String serverId);

  /** Send a MONITOR protocol command assembled by the application. */
  Completable sendRaw(String serverId, String rawLine);
}
