package cafe.woden.ircclient.irc.playback;

import io.reactivex.rxjava3.core.Completable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** IRC bouncer capability and playback extension operations. */
public interface IrcBouncerPlaybackPort {
  /**
   * @return true if the connection negotiated {@code znc.in/playback} (ZNC playback module).
   */
  default boolean isZncPlaybackAvailable(String serverId) {
    return false;
  }

  /**
   * @return true when this connection appears to be backed by a ZNC bouncer session.
   */
  default boolean isZncBouncerDetected(String serverId) {
    return false;
  }

  /**
   * @return true if the connection negotiated {@code soju.im/bouncer-networks}.
   */
  default boolean isSojuBouncerAvailable(String serverId) {
    return false;
  }

  /**
   * Request backlog playback from ZNC.
   *
   * <p>Requires {@code znc.in/playback}. ZNC playback replays messages as normal
   * PRIVMSG/NOTICE/ACTION lines (often with {@code server-time} tags), rather than returning a
   * structured batch.
   *
   * <p>This method only issues the request; callers are responsible for capturing/processing the
   * replayed lines.
   */
  default Completable requestZncPlaybackRange(
      String serverId, String target, Instant fromInclusive, Instant toInclusive) {
    return Completable.error(new UnsupportedOperationException("ZNC playback not supported"));
  }

  /** Convenience overload: request a window ending at {@code beforeExclusive}. */
  default Completable requestZncPlaybackBefore(
      String serverId, String target, Instant beforeExclusive, Duration window) {
    Instant end = beforeExclusive == null ? Instant.now() : beforeExclusive;
    Duration w = (window == null) ? Duration.ofMinutes(30) : window;
    // Clamp to seconds because ZNC playback typically uses epoch-seconds.
    Instant start = end.minus(w.toMillis(), ChronoUnit.MILLIS);
    return requestZncPlaybackRange(serverId, target, start, end);
  }
}
