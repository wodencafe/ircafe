package cafe.woden.ircclient.state.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for short-lived status-mode recency signals. */
@ApplicationLayer
public interface RecentStatusModePort {

  void markStatusMode(String serverId, String channel);

  boolean isRecent(String serverId, String channel, long withinMs);

  void clearServer(String serverId);

  void clearChannel(String serverId, String channel);
}
