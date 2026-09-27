package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Typing readiness + send API used by UI coordinators. */
@SecondaryPort
@ApplicationLayer
public interface IrcTypingPort {

  default boolean isTypingAvailable(String serverId) {
    return false;
  }

  default String typingAvailabilityReason(String serverId) {
    return "";
  }

  default Completable sendTyping(String serverId, String target, String state) {
    return Completable.error(new UnsupportedOperationException("typing support not available"));
  }
}
