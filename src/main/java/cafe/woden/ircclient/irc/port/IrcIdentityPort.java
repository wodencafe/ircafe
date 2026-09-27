package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Local nickname and away-state commands, scoped to a server. */
@SecondaryPort
@ApplicationLayer
public interface IrcIdentityPort {

  /** Request a local nickname change. */
  Completable changeNick(String serverId, String newNick);

  /** Set the away message; null or blank clears away status. */
  Completable setAway(String serverId, String awayMessage);
}
