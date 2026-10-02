package cafe.woden.ircclient.irc.playback;

import java.util.OptionalLong;

/** Provides a "resume cursor" for bouncer playback. */
public interface PlaybackCursorProvider {

  /**
   * @param serverId the configured server/network id (IRCafe's per-server id)
   * @return epoch seconds of the newest persisted received conversation message for this server, or
   *     empty if unknown; local status and bootstrap traffic must not advance this cursor
   */
  OptionalLong lastSeenEpochSeconds(String serverId);
}
