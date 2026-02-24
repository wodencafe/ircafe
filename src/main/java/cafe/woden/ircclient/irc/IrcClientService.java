package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.jmolecules.architecture.layered.ApplicationLayer;

/**
 * Multi-server IRC client API.
 *
 * <p>All operations are explicitly scoped to a server id.
 */
@ApplicationLayer
public interface IrcClientService {
  /**
   * Stop reconnect timers and close any active IRC connections immediately.
   *
   * <p>This is used during app shutdown so transports are torn down even before the Spring context
   * finishes closing.
   */
  default void shutdownNow() {}

  Flowable<ServerIrcEvent> events();

  Optional<String> currentNick(String serverId);

  Completable connect(String serverId);

  Completable disconnect(String serverId);

  /**
   * Disconnect from a server, optionally sending a QUIT reason.
   *
   * <p>If {@code reason} is blank, implementations should use their default disconnect reason.
   */
  default Completable disconnect(String serverId, String reason) {
    return disconnect(serverId);
  }

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

  /** Request WHOWAS info for a nick (results will be emitted on {@link #events()}). */
  default Completable whowas(String serverId, String nick, int count) {
    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) return Completable.error(new IllegalArgumentException("nick is blank"));
    String line = count > 0 ? ("WHOWAS " + n + " " + count) : ("WHOWAS " + n);
    return sendRaw(serverId, line);
  }

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
   * <p>Requires IRCv3 {@code chathistory} (or legacy {@code draft/chathistory}), and typically
   * {@code batch}, to be negotiated. The returned history will arrive asynchronously on {@link
   * #events()} and will be handled by later pipeline steps.
   */
  Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit);

  /**
   * Request chat history with an explicit selector.
   *
   * <p>The selector must be an IRCv3 CHATHISTORY selector token (for example {@code
   * timestamp=2026-02-16T12:34:56.000Z} or {@code msgid=abc123}).
   */
  default Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    return Completable.error(
        new UnsupportedOperationException("CHATHISTORY selector requests not supported"));
  }

  /**
   * Request chat history using IRCv3 {@code CHATHISTORY LATEST}.
   *
   * <p>The selector may be {@code *} or a standard selector token ({@code msgid=...}, {@code
   * timestamp=...}).
   */
  default Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    return Completable.error(
        new UnsupportedOperationException("CHATHISTORY latest requests not supported"));
  }

  /**
   * Request chat history using IRCv3 {@code CHATHISTORY BETWEEN}.
   *
   * <p>Selectors may be standard selector tokens and, where supported by the server, {@code *}.
   */
  default Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return Completable.error(
        new UnsupportedOperationException("CHATHISTORY between requests not supported"));
  }

  /** Request chat history using IRCv3 {@code CHATHISTORY AROUND}. */
  default Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return Completable.error(
        new UnsupportedOperationException("CHATHISTORY around requests not supported"));
  }

  default Completable requestChatHistoryBefore(
      String serverId, String target, long beforeExclusiveEpochMs, int limit) {
    return requestChatHistoryBefore(
        serverId, target, Instant.ofEpochMilli(beforeExclusiveEpochMs), limit);
  }

  /**
   * @return true if IRCv3 chat history is usable on this connection (for example when {@code
   *     chathistory} or {@code draft/chathistory} is negotiated).
   */
  default boolean isChatHistoryAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code echo-message} is negotiated on this connection.
   */
  default boolean isEchoMessageAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code draft/reply} is negotiated on this connection.
   */
  default boolean isDraftReplyAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code draft/react} is negotiated on this connection.
   */
  default boolean isDraftReactAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code multiline} (or {@code draft/multiline}) is negotiated.
   */
  default boolean isMultilineAvailable(String serverId) {
    return false;
  }

  /**
   * @return negotiated IRCv3 multiline max-bytes limit, or {@code 0} if unlimited/unknown.
   */
  default long negotiatedMultilineMaxBytes(String serverId) {
    return 0L;
  }

  /**
   * @return negotiated IRCv3 multiline max-lines limit, or {@code 0} if unlimited/unknown.
   */
  default int negotiatedMultilineMaxLines(String serverId) {
    return 0;
  }

  /**
   * @return true if IRCv3 {@code draft/message-edit} (or equivalent) is negotiated on this
   *     connection.
   */
  default boolean isMessageEditAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code draft/message-redaction} (or equivalent) is negotiated on this
   *     connection.
   */
  default boolean isMessageRedactionAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code typing} is negotiated on this connection.
   */
  default boolean isTypingAvailable(String serverId) {
    return false;
  }

  /**
   * Human-readable diagnostic reason why IRCv3 typing indicators are unavailable.
   *
   * <p>Useful for logs and UI warnings.
   */
  default String typingAvailabilityReason(String serverId) {
    return "";
  }

  /**
   * @return true if IRCv3 {@code read-marker} is negotiated on this connection.
   */
  default boolean isReadMarkerAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code labeled-response} is negotiated on this connection.
   */
  default boolean isLabeledResponseAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRCv3 {@code standard-replies} is negotiated on this connection.
   */
  default boolean isStandardRepliesAvailable(String serverId) {
    return false;
  }

  /**
   * @return true if IRC MONITOR support is available via CAP and/or RPL_ISUPPORT.
   */
  default boolean isMonitorAvailable(String serverId) {
    return false;
  }

  /**
   * @return advertised IRC MONITOR target limit (RPL_ISUPPORT MONITOR=...), or {@code 0} when
   *     unknown/unbounded.
   */
  default int negotiatedMonitorLimit(String serverId) {
    return 0;
  }

  /**
   * Send an IRCv3 typing state signal for a target using {@code TAGMSG}.
   *
   * <p>Typical states are {@code active}, {@code paused}, {@code done}.
   */
  default Completable sendTyping(String serverId, String target, String state) {
    return Completable.error(new UnsupportedOperationException("typing capability not supported"));
  }

  /** Send an IRCv3 read-marker update for a target. */
  default Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return Completable.error(
        new UnsupportedOperationException("read-marker capability not supported"));
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

  default Completable sendAction(String serverId, String target, String action) {
    String a = action == null ? "" : action;
    // Fallback implementation: manual CTCP wrapper.
    return sendMessage(serverId, target, "\u0001ACTION " + a + "\u0001");
  }

  /**
   * Convenience method used by the app layer.
   *
   * <p>If {@code target} looks like a channel (# or &), we send to the channel. Otherwise we treat
   * it as a nick and send a private message.
   */
  default Completable sendMessage(String serverId, String target, String message) {
    String t = target == null ? "" : target.trim();
    if (t.startsWith("#") || t.startsWith("&")) {
      return sendToChannel(serverId, t, message);
    }
    return sendPrivateMessage(serverId, t, message);
  }
}
