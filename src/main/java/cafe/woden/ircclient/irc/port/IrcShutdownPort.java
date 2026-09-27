package cafe.woden.ircclient.irc.port;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shutdown hook operations used during application teardown. */
@SecondaryPort
@ApplicationLayer
public interface IrcShutdownPort {

  default void shutdownNow() {}
}
