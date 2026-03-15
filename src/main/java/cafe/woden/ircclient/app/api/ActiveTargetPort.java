package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned read access to the currently active chat target context. */
@PrimaryPort
@ApplicationLayer
public interface ActiveTargetPort {

  TargetRef getActiveTarget();

  TargetRef safeStatusTarget();
}
