package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for target-scoped log maintenance operations. */
@ApplicationLayer
public interface TargetLogMaintenancePort {

  void clearTarget(TargetRef target);
}
