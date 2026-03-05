package cafe.woden.ircclient.bouncer;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend-specific mapping logic from discovery events to ephemeral server config. */
@ApplicationLayer
public interface BouncerNetworkMappingStrategy {

  String backendId();

  ResolvedBouncerNetwork resolveNetwork(
      IrcProperties.Server bouncer, BouncerDiscoveredNetwork network);

  IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer, ResolvedBouncerNetwork resolved, List<String> autoJoinChannels);

  default String networkDebugId(BouncerDiscoveredNetwork network) {
    return "";
  }
}
