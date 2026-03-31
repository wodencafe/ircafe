package cafe.woden.ircclient.irc;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Describes why a disconnect was requested so transports can apply source-specific policy. */
@ApplicationLayer
public enum DisconnectRequestSource {
  USER_REQUEST(true),
  RECONNECT(false),
  SERVER_REMOVED(true),
  AUTOMATION(true),
  UNKNOWN(true);

  private final boolean clearDiscoveredBouncerNetworks;

  DisconnectRequestSource(boolean clearDiscoveredBouncerNetworks) {
    this.clearDiscoveredBouncerNetworks = clearDiscoveredBouncerNetworks;
  }

  public boolean clearDiscoveredBouncerNetworks() {
    return clearDiscoveredBouncerNetworks;
  }
}
