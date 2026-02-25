package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned read access to the currently active chat target context. */
@ApplicationLayer
public interface ActiveTargetPort {

  TargetRef getActiveTarget();

  TargetRef safeStatusTarget();
}
