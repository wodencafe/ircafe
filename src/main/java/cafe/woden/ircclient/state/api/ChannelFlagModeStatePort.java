package cafe.woden.ircclient.state.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for tracked arg-less channel MODE flags. */
@ApplicationLayer
public interface ChannelFlagModeStatePort {

  boolean applyDelta(String serverId, String channel, String details);

  boolean hasAnyState(String serverId, String channel);

  String snapshotModeSummary(String serverId, String channel);

  void clearServer(String serverId);

  void clearChannel(String serverId, String channel);
}
