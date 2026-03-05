package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend-specific mapping logic from discovery events to ephemeral server config. */
@ApplicationLayer
public interface BouncerNetworkMappingStrategy {

  String backendId();

  default String ephemeralIdPrefix() {
    return backendId() + ":";
  }

  default String networksGroupLabel() {
    return backendId() + " Networks";
  }

  default Set<String> capabilityHints() {
    return Set.of();
  }

  ResolvedBouncerNetwork resolveNetwork(
      IrcProperties.Server bouncer, BouncerDiscoveredNetwork network);

  IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer, ResolvedBouncerNetwork resolved, List<String> autoJoinChannels);

  default String networkDebugId(BouncerDiscoveredNetwork network) {
    return "";
  }
}
