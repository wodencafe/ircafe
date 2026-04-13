package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.util.Ircv3CapabilityNameSupport;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime resolver for IRCv3 capability aliases, request tokens, and preference keys. */
@SecondaryPort
@ApplicationLayer
public interface Ircv3CapabilityNameResolverPort {

  default String normalizePreferenceKey(String capability) {
    return Ircv3CapabilityNameSupport.normalizePreferenceKey(capability);
  }

  default String normalizeRequestToken(String capability) {
    return Ircv3CapabilityNameSupport.normalizeRequestToken(capability);
  }
}
