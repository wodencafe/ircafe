package cafe.woden.ircclient.bouncer;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared contract for bouncer network auto-connect preferences. */
@ApplicationLayer
public interface BouncerAutoConnectStore {

  boolean isEnabled(String bouncerServerId, String networkName);

  void setEnabled(String bouncerServerId, String networkName, boolean enable);
}
