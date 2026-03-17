package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned control surface for startup/shutdown connection actions. */
@PrimaryPort
@ApplicationLayer
public interface MediatorControlPort {

  void connectAll();

  void connectAutoConnectOnStartServers();
}
