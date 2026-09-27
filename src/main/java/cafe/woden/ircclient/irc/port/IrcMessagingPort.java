package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Outbound chat, notice, and action delivery, scoped to a server. */
@SecondaryPort
@ApplicationLayer
public interface IrcMessagingPort {

  /** Send a chat message using the backend's target routing. */
  Completable sendMessage(String serverId, String target, String message);

  /** Send a notice using the backend's target routing. */
  Completable sendNotice(String serverId, String target, String message);

  /** Send an action using the backend's action encoding. */
  Completable sendAction(String serverId, String target, String action);
}
