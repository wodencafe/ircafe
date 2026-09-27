package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Selector-based IRC history requests. Results arrive through the IRC event stream. */
@SecondaryPort
@ApplicationLayer
public interface IrcChatHistoryPort {

  /** Request messages preceding a msgid or timestamp selector. */
  Completable requestChatHistoryBefore(String serverId, String target, String selector, int limit);

  /** Request latest messages using a selector or *. */
  Completable requestChatHistoryLatest(String serverId, String target, String selector, int limit);

  /** Request context around a msgid or timestamp selector. */
  Completable requestChatHistoryAround(String serverId, String target, String selector, int limit);

  /** Request a bounded history window between two selectors. */
  Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit);
}
