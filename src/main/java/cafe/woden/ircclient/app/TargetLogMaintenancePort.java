package cafe.woden.ircclient.app;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for target-scoped log maintenance operations. */
@ApplicationLayer
public interface TargetLogMaintenancePort {

  void clearTarget(TargetRef target);
}
