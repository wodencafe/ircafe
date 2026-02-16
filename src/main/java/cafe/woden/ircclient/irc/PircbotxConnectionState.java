package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.irc.soju.SojuNetwork;
import cafe.woden.ircclient.irc.znc.ZncNetwork;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
  final AtomicReference<Disposable> heartbeatDisposable = new AtomicReference<>();

  final AtomicBoolean manualDisconnect = new AtomicBoolean(false);

  /**
   * Some failures are not transient (e.g. authentication failures). When we detect those we want to
   * avoid an auto-reconnect loop and instead require user intervention.
   */
  final AtomicBoolean suppressAutoReconnectOnce = new AtomicBoolean(false);

  final AtomicLong reconnectAttempts = new AtomicLong(0);
  final AtomicReference<Disposable> reconnectDisposable = new AtomicReference<>();
  final AtomicReference<String> disconnectReasonOverride = new AtomicReference<>();

  final Map<String, String> lastHostmaskByNickLower = new ConcurrentHashMap<>();

  final Map<String, Boolean> whoisSawAwayByNickLower = new ConcurrentHashMap<>();

  /**
   * Tracks WHOIS probes we initiated so we can (optionally) infer LOGGED_OUT when a WHOIS completes
   * without a 330 (account) numeric.
   */
  final Map<String, Boolean> whoisSawAccountByNickLower = new ConcurrentHashMap<>();

  /**
   * Be conservative: some networks do not expose WHOIS account info at all. We only infer LOGGED_OUT
   * from a missing 330 after we've observed at least one 330 on this connection.
   */
  final AtomicBoolean whoisAccountNumericSupported = new AtomicBoolean(false);

  /**
   * Tracks whether IRCafe's expected WHOX reply schema appears compatible on this connection.
   *
   * <p>Some networks advertise WHOX but return 354 fields in a different order or omit account/flags.
   * We use this to avoid repeatedly issuing WHOX channel scans that will never yield account state.
   */
  final AtomicBoolean whoxSchemaCompatible = new AtomicBoolean(true);

  /**
   * Ensures we only emit a single "schema incompatible" signal per connection.
   */
  final AtomicBoolean whoxSchemaIncompatibleEmitted = new AtomicBoolean(false);

  /**
   * Ensures we only emit a single "schema compatible" confirmation per connection.
   */
  final AtomicBoolean whoxSchemaCompatibleEmitted = new AtomicBoolean(false);

  // ZNC Playback module support
  final AtomicBoolean zncPlaybackCapAcked = new AtomicBoolean(false);
  final AtomicBoolean zncPlaybackRequestedThisSession = new AtomicBoolean(false);

  // Ensures we only issue *status ListNetworks once per connection (ZNC).
  final AtomicBoolean zncListNetworksRequestedThisSession = new AtomicBoolean(false);

  // Captures networks discovered via *status ListNetworks (ZNC multi-network).
  final AtomicBoolean zncListNetworksCaptureActive = new AtomicBoolean(false);
  final AtomicLong zncListNetworksCaptureStartedMs = new AtomicLong(0);
  final Map<String, ZncNetwork> zncNetworksByNameLower = new ConcurrentHashMap<>();

  // ZNC (bouncer) detection / username parsing.
  final AtomicBoolean zncDetected = new AtomicBoolean(false);
  final AtomicBoolean zncDetectedLogged = new AtomicBoolean(false);

  // Parsed ZNC-style login fields (user[@client]/network).
  final AtomicReference<String> zncBaseUser = new AtomicReference<>("");
  final AtomicReference<String> zncClientId = new AtomicReference<>("");
  final AtomicReference<String> zncNetwork = new AtomicReference<>("");

  final ZncPlaybackCaptureCoordinator zncPlaybackCapture = new ZncPlaybackCaptureCoordinator();

  // IRCv3 history support (soju): detect whether the server accepted these capabilities.
  final AtomicBoolean batchCapAcked = new AtomicBoolean(false);
  final AtomicBoolean chatHistoryCapAcked = new AtomicBoolean(false);
  final AtomicBoolean echoMessageCapAcked = new AtomicBoolean(false);
  final AtomicBoolean capNotifyCapAcked = new AtomicBoolean(false);
  final AtomicBoolean labeledResponseCapAcked = new AtomicBoolean(false);
  final AtomicBoolean setnameCapAcked = new AtomicBoolean(false);
  final AtomicBoolean chghostCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftReplyCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftReactCapAcked = new AtomicBoolean(false);
  final AtomicBoolean typingCapAcked = new AtomicBoolean(false);
  final AtomicBoolean readMarkerCapAcked = new AtomicBoolean(false);

  // soju bouncer network discovery (cap: soju.im/bouncer-networks)
  final AtomicBoolean sojuBouncerNetworksCapAcked = new AtomicBoolean(false);

  // Ensures we only issue BOUNCER LISTNETWORKS once per connection.
  final AtomicBoolean sojuListNetworksRequestedThisSession = new AtomicBoolean(false);

  // soju bouncer: if present, this connection is bound to a specific network on the bouncer.
  // If blank, this connection is the "bouncer control" session.
  final AtomicReference<String> sojuBouncerNetId = new AtomicReference<>("");

  // Networks discovered via `BOUNCER LISTNETWORKS` (de-duped by netId).
  final Map<String, SojuNetwork> sojuNetworksByNetId = new ConcurrentHashMap<>();

  // IRCv3 server-time support (canonical message timestamps).
  final AtomicBoolean serverTimeCapAcked = new AtomicBoolean(false);
  // Warn once per application run if server-time wasn't negotiated on this server.
  final AtomicBoolean serverTimeMissingWarned = new AtomicBoolean(false);

  // One-time connect log summary of negotiated caps.
  final AtomicBoolean capSummaryLogged = new AtomicBoolean(false);

  PircbotxConnectionState(String serverId) {
    this.serverId = serverId;
  }

  void resetNegotiatedCaps() {
    zncPlaybackCapAcked.set(false);
    batchCapAcked.set(false);
    chatHistoryCapAcked.set(false);
    echoMessageCapAcked.set(false);
    capNotifyCapAcked.set(false);
    labeledResponseCapAcked.set(false);
    setnameCapAcked.set(false);
    chghostCapAcked.set(false);
    draftReplyCapAcked.set(false);
    draftReactCapAcked.set(false);
    typingCapAcked.set(false);
    readMarkerCapAcked.set(false);
    sojuBouncerNetworksCapAcked.set(false);
    serverTimeCapAcked.set(false);
    capSummaryLogged.set(false);
  }
}
