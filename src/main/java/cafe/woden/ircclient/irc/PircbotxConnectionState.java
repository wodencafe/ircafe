package cafe.woden.ircclient.irc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.pircbotx.PircBotX;

/**
 * Mutable connection state for a single IRC server.
 *
 * <p>This used to be a nested type inside {@link PircbotxIrcClientService}. It is now a small
 * top-level (package-private) helper so we can continue splitting responsibilities without
 * turning PircbotxIrcClientService into a god-file.
 */
final class PircbotxConnectionState {
  final String serverId;
  final AtomicReference<PircBotX> botRef = new AtomicReference<>();

  final AtomicLong lastInboundMs = new AtomicLong(0);
  final AtomicBoolean localTimeoutEmitted = new AtomicBoolean(false);
  final AtomicReference<ScheduledFuture<?>> heartbeatFuture = new AtomicReference<>();

  final AtomicBoolean manualDisconnect = new AtomicBoolean(false);
  final AtomicLong reconnectAttempts = new AtomicLong(0);
  final AtomicReference<ScheduledFuture<?>> reconnectFuture = new AtomicReference<>();
  final AtomicReference<String> disconnectReasonOverride = new AtomicReference<>();

  /**
   * Best-effort, passive hostmask cache learned from server prefixes (JOIN/PRIVMSG/etc.).
   * Keyed by lowercase nick. Used to avoid spamming the app layer with redundant observations.
   */
  final Map<String, String> lastHostmaskByNickLower = new ConcurrentHashMap<>();

  /** Tracks whether a WHOIS for a nick reported RPL_AWAY (301) before RPL_ENDOFWHOIS (318). */
  final Map<String, Boolean> whoisSawAwayByNickLower = new ConcurrentHashMap<>();

  PircbotxConnectionState(String serverId) {
    this.serverId = serverId;
  }
}
