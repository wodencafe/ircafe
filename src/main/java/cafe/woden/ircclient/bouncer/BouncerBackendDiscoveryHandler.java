package cafe.woden.ircclient.bouncer;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend-specific handler for generic bouncer discovery events. */
@ApplicationLayer
public interface BouncerBackendDiscoveryHandler {

  String backendId();

  void onNetworkDiscovered(BouncerDiscoveredNetwork network);

  void onOriginDisconnected(String originServerId);
}
