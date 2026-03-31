package cafe.woden.ircclient.bouncer;

import io.reactivex.rxjava3.core.Completable;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Port used by bouncer importers to trigger a server connection. */
@SecondaryPort
@ApplicationLayer
public interface BouncerConnectionPort {

  Completable connect(String serverId);
}
