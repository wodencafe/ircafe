package cafe.woden.ircclient.config.api;

import java.nio.file.Path;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for resolving the persisted runtime config path. */
@ApplicationLayer
public interface RuntimeConfigPathPort {

  Path runtimeConfigPath();
}
