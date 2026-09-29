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

  Optional<String> currentNick(String serverId);

  Completable requestLagProbe(String serverId);

  default boolean shouldRequestLagProbe(String serverId) {
    return true;
  }

  default boolean isLagProbeReady(String serverId) {
    return currentNick(serverId).isPresent();
  }

  OptionalLong lastMeasuredLagMs(String serverId);
}
