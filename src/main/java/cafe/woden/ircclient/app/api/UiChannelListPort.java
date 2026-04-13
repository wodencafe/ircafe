package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Channel-list, ban-list, and channel mode snapshot UI updates. */
@SecondaryPort
@ApplicationLayer
public interface UiChannelListPort {

  default void beginChannelList(String serverId, String banner) {}

  default void appendChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {}

  default void endChannelList(String serverId, String summary) {}

  default void beginChannelBanList(String serverId, String channel) {}

  default void appendChannelBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {}

  default void endChannelBanList(String serverId, String channel, String summary) {}

  default void setChannelModeSnapshot(
      String serverId, String channel, String rawModes, String friendlySummary) {}
}
