package cafe.woden.ircclient.config.api;

import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for resolving the persisted runtime config path. */
@SecondaryPort
@ApplicationLayer
public interface RuntimeConfigPathPort {

  Path runtimeConfigPath();
}
