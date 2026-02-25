package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned control surface for startup/shutdown connection actions. */
@ApplicationLayer
public interface MediatorControlPort {

  void connectAll();
}
