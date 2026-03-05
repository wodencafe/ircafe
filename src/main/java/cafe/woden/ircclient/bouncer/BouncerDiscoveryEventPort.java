package cafe.woden.ircclient.bouncer;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-facing ingress for backend-agnostic bouncer discovery events. */
@ApplicationLayer
public interface BouncerDiscoveryEventPort {

  BouncerDiscoveryEventPort NO_OP =
      new BouncerDiscoveryEventPort() {
        @Override
        public void onNetworkDiscovered(BouncerDiscoveredNetwork network) {}

        @Override
        public void onOriginDisconnected(String backendId, String originServerId) {}
      };

  void onNetworkDiscovered(BouncerDiscoveredNetwork network);

  void onOriginDisconnected(String backendId, String originServerId);

  static BouncerDiscoveryEventPort noOp() {
    return NO_OP;
  }
}
