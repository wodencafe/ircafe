package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** First-launch setup state and persistence. */
@SecondaryPort
@ApplicationLayer
public interface FirstRunSetupConfigPort {
  /** Cached startup decision; safe to call on the EDT. */
  boolean shouldOfferSetup();

  /** Persist off the EDT. A null server dismisses setup without changing any server settings. */
  void finishSetup(IrcProperties.Server server);
}
