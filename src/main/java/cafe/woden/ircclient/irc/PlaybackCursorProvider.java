package cafe.woden.ircclient.irc;

import java.util.OptionalLong;

/**
 * Provides a "resume cursor" for bouncer playback.
 *
 * <p>Implementations should return the last message timestamp (epoch seconds) that the client has
 * already persisted for the given server/network.
 */
public interface PlaybackCursorProvider {

  /**
   * @param serverId the configured server/network id (IRCafe's per-server id)
   * @return epoch seconds of the newest persisted line for this server, or empty if unknown
   */
  OptionalLong lastSeenEpochSeconds(String serverId);
}
