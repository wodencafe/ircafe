package cafe.woden.ircclient.irc;

import java.util.OptionalLong;

/**
 * Provides a "resume cursor" for bouncer playback.
 *
 */
public interface PlaybackCursorProvider {

  /**
   * @param serverId the configured server/network id (IRCafe's per-server id)
   * @return epoch seconds of the newest persisted line for this server, or empty if unknown
   */
  OptionalLong lastSeenEpochSeconds(String serverId);
}
