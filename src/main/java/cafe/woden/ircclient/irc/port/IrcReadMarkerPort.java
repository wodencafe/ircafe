package cafe.woden.ircclient.irc.port;

import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Read-marker capability/readiness + send API used by UI coordinators. */
@SecondaryPort
@ApplicationLayer
public interface IrcReadMarkerPort {

  default boolean isReadMarkerAvailable(String serverId) {
    return false;
  }

  default Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return Completable.error(
        new UnsupportedOperationException("read-marker capability not supported"));
  }
}
