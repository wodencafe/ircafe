package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import java.util.OptionalLong;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Lag probe operations used by UI lag indicator surfaces. */
@SecondaryPort
@ApplicationLayer
public interface IrcLagProbePort {

  default Optional<String> currentNick(String serverId) {
    return Optional.empty();
  }

  default Completable requestLagProbe(String serverId) {
    return Completable.complete();
  }

  default boolean shouldRequestLagProbe(String serverId) {
    return true;
  }

  default boolean isLagProbeReady(String serverId) {
    return currentNick(serverId).isPresent();
  }

  default OptionalLong lastMeasuredLagMs(String serverId) {
    return OptionalLong.empty();
  }
}
