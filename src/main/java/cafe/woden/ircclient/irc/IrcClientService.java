package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Multi-server IRC client API.
 *
 * <p>All operations are explicitly scoped to a server id.
 */
public interface IrcClientService {
  Flowable<ServerIrcEvent> events();

  Optional<String> currentNick(String serverId);

  Completable connect(String serverId);
  Completable disconnect(String serverId);

  Completable changeNick(String serverId, String newNick);

  /**
   * Set the local user's away message.
   *
   * <p>If {@code awayMessage} is null or blank, away should be cleared.
   */
  Completable setAway(String serverId, String awayMessage);

  Completable requestNames(String serverId, String channel);
  Completable joinChannel(String serverId, String channel);

  /** Request WHOIS info for a nick (results will be emitted on {@link #events()}). */
  Completable whois(String serverId, String nick);

  
  default Completable partChannel(String serverId, String channel) {
    return partChannel(serverId, channel, null);
  }

  Completable partChannel(String serverId, String channel, String reason);
  Completable sendToChannel(String serverId, String channel, String message);

  Completable sendPrivateMessage(String serverId, String nick, String message);

  Completable sendNoticeToChannel(String serverId, String channel, String message);

  Completable sendNoticePrivate(String serverId, String nick, String message);

  default Completable sendNotice(String serverId, String target, String message) {
    String t = target == null ? "" : target.trim();
    if (t.startsWith("#") || t.startsWith("&")) {
      return sendNoticeToChannel(serverId, t, message);
    }
    return sendNoticePrivate(serverId, t, message);
  }


  /** Send a raw IRC line (advanced). */
  Completable sendRaw(String serverId, String rawLine);

  /**
   * Request chat history from the server/bouncer.
   *
   * <p>Requires IRCv3 {@code draft/chathistory} (and typically {@code batch}) to be negotiated.
   * The returned history will arrive asynchronously on {@link #events()} and will be handled
   * by later pipeline steps.
   */
  Completable requestChatHistoryBefore(String serverId, String target, Instant beforeExclusive, int limit);

  default Completable requestChatHistoryBefore(String serverId, String target, long beforeExclusiveEpochMs, int limit) {
    return requestChatHistoryBefore(serverId, target, Instant.ofEpochMilli(beforeExclusiveEpochMs), limit);
  }

  /**
   * @return true if IRCv3 chat history is usable on this connection (e.g. draft/chathistory negotiated).
   */
  default boolean isChatHistoryAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if the connection negotiated {@code znc.in/playback} (ZNC playback module).
   */
  default boolean isZncPlaybackAvailable(String serverId) {
    return false;
  }

  /**
   * Request backlog playback from ZNC.
   *
   * <p>Requires {@code znc.in/playback}. ZNC playback replays messages as normal PRIVMSG/NOTICE/ACTION
   * lines (often with {@code server-time} tags), rather than returning a structured batch.
   *
   * <p>This method only issues the request; callers are responsible for capturing/processing the
   * replayed lines.
   */
  default Completable requestZncPlaybackRange(
      String serverId,
      String target,
      Instant fromInclusive,
      Instant toInclusive
  ) {
    return Completable.error(new UnsupportedOperationException("ZNC playback not supported"));
  }

  /** Convenience overload: request a window ending at {@code beforeExclusive}. */
  default Completable requestZncPlaybackBefore(String serverId, String target, Instant beforeExclusive, Duration window) {
    Instant end = beforeExclusive == null ? Instant.now() : beforeExclusive;
    Duration w = (window == null) ? Duration.ofMinutes(30) : window;
    // Clamp to seconds because ZNC playback typically uses epoch-seconds.
    Instant start = end.minus(w.toMillis(), ChronoUnit.MILLIS);
    return requestZncPlaybackRange(serverId, target, start, end);
  }


  default Completable sendAction(String serverId, String target, String action) {
    String a = action == null ? "" : action;
    // Fallback implementation: manual CTCP wrapper.
    return sendMessage(serverId, target, "\u0001ACTION " + a + "\u0001");
  }

  /**
   * Convenience method used by the app layer.
   *
   * <p>If {@code target} looks like a channel (# or &), we send to the channel.
   * Otherwise we treat it as a nick and send a private message.
   */
  default Completable sendMessage(String serverId, String target, String message) {
    String t = target == null ? "" : target.trim();
    if (t.startsWith("#") || t.startsWith("&")) {
      return sendToChannel(serverId, t, message);
    }
    return sendPrivateMessage(serverId, t, message);
  }
}
