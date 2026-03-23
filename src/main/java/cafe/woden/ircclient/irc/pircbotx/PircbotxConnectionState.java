package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.pircbotx.PircBotX;

/**
 * Mutable connection state for a single IRC server.
 *
 * <p>This used to be a nested type inside {@link PircbotxIrcClientService}. It is now a small
 * top-level (package-private) helper so we can continue splitting responsibilities without turning
 * PircbotxIrcClientService into a god-file.
 */
public final class PircbotxConnectionState {
  private static final long CHANNEL_MODE_324_DEDUPE_TTL_MS = 2_000L;
  private static final int CHANNEL_MODE_324_DEDUPE_MAX = 256;
  private static final long LAG_SAMPLE_STALE_AFTER_MS = 120_000L;
  private static final long MAX_PASSIVE_LAG_SAMPLE_MS = TimeUnit.MINUTES.toMillis(5);

  final String serverId;
  final AtomicReference<PircBotX> botRef = new AtomicReference<>();
  final AtomicReference<String> selfNickHint = new AtomicReference<>("");

  final AtomicLong lastInboundMs = new AtomicLong(0);
  final AtomicBoolean localTimeoutEmitted = new AtomicBoolean(false);
  final AtomicReference<Disposable> heartbeatDisposable = new AtomicReference<>();
  final AtomicReference<String> lagProbeToken = new AtomicReference<>("");
  final AtomicLong lagProbeSentAtMs = new AtomicLong(0L);
  final AtomicLong lagLastMeasuredMs = new AtomicLong(-1L);
  final AtomicLong lagLastMeasuredAtMs = new AtomicLong(0L);

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
   * Be conservative: some networks do not expose WHOIS account info at all. We only infer
   * LOGGED_OUT from a missing 330 after we've observed at least one 330 on this connection.
   */
  final AtomicBoolean whoisAccountNumericSupported = new AtomicBoolean(false);

  /**
   * Tracks whether IRCafe's expected WHOX reply schema appears compatible on this connection.
   *
   * <p>Some networks advertise WHOX but return 354 fields in a different order or omit
   * account/flags. We use this to avoid repeatedly issuing WHOX channel scans that will never yield
   * account state.
   */
  final AtomicBoolean whoxSchemaCompatible = new AtomicBoolean(true);

  /** Ensures we only emit a single "schema incompatible" signal per connection. */
  final AtomicBoolean whoxSchemaIncompatibleEmitted = new AtomicBoolean(false);

  /** Ensures we only emit a single "schema compatible" confirmation per connection. */
  final AtomicBoolean whoxSchemaCompatibleEmitted = new AtomicBoolean(false);

  // ZNC Playback module support
  final AtomicBoolean zncPlaybackCapAcked = new AtomicBoolean(false);
  final AtomicBoolean zncPlaybackRequestedThisSession = new AtomicBoolean(false);

  // Ensures we only issue *status ListNetworks once per connection (ZNC).
  final AtomicBoolean zncListNetworksRequestedThisSession = new AtomicBoolean(false);

  // Captures networks discovered via *status ListNetworks (ZNC multi-network).
  final AtomicBoolean zncListNetworksCaptureActive = new AtomicBoolean(false);
  final AtomicLong zncListNetworksCaptureStartedMs = new AtomicLong(0);
  final Map<String, BouncerDiscoveredNetwork> zncNetworksByNameLower = new ConcurrentHashMap<>();

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
  final AtomicBoolean stsCapAcked = new AtomicBoolean(false);
  final AtomicBoolean multilineCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftMultilineCapAcked = new AtomicBoolean(false);
  final AtomicLong multilineMaxBytes = new AtomicLong(0L);
  final AtomicLong multilineMaxLines = new AtomicLong(0L);
  final AtomicLong draftMultilineMaxBytes = new AtomicLong(0L);
  final AtomicLong draftMultilineMaxLines = new AtomicLong(0L);
  final AtomicLong multilineOfferedMaxBytes = new AtomicLong(0L);
  final AtomicLong multilineOfferedMaxLines = new AtomicLong(0L);
  final AtomicLong draftMultilineOfferedMaxBytes = new AtomicLong(0L);
  final AtomicLong draftMultilineOfferedMaxLines = new AtomicLong(0L);
  final AtomicBoolean draftChannelContextCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftReplyCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftReactCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftUnreactCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftMessageEditCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftMessageRedactionCapAcked = new AtomicBoolean(false);
  final AtomicBoolean messageTagsCapAcked = new AtomicBoolean(false);

  /**
   * Whether the server allows the IRCv3 {@code +typing} client-only tag, per RPL_ISUPPORT
   * CLIENTTAGDENY.
   *
   * <p>Default is {@code true} (i.e. allow), which matches the "missing or empty CLIENTTAGDENY"
   * default.
   */
  final AtomicBoolean typingClientTagAllowed = new AtomicBoolean(true);

  final AtomicBoolean typingClientTagPolicyKnown = new AtomicBoolean(false);

  final AtomicBoolean typingCapAcked = new AtomicBoolean(false);
  final AtomicBoolean messageTagsFallbackReqSent = new AtomicBoolean(false);
  final AtomicBoolean batchFallbackReqSent = new AtomicBoolean(false);
  final AtomicBoolean chatHistoryFallbackReqSent = new AtomicBoolean(false);
  final AtomicBoolean readMarkerCapAcked = new AtomicBoolean(false);
  final AtomicBoolean monitorCapAcked = new AtomicBoolean(false);
  final AtomicBoolean extendedMonitorCapAcked = new AtomicBoolean(false);

  // soju bouncer network discovery (cap: soju.im/bouncer-networks)
  final AtomicBoolean sojuBouncerNetworksCapAcked = new AtomicBoolean(false);

  // Ensures we only issue BOUNCER LISTNETWORKS once per connection.
  final AtomicBoolean sojuListNetworksRequestedThisSession = new AtomicBoolean(false);

  // soju bouncer: if present, this connection is bound to a specific network on the bouncer.
  // If blank, this connection is the "bouncer control" session.
  final AtomicReference<String> sojuBouncerNetId = new AtomicReference<>("");

  // Networks discovered via `BOUNCER LISTNETWORKS` (de-duped by netId).
  final Map<String, BouncerDiscoveredNetwork> sojuNetworksByNetId = new ConcurrentHashMap<>();

  // Networks discovered via generic bouncer protocol lines.
  final Map<String, BouncerDiscoveredNetwork> genericBouncerNetworksById =
      new ConcurrentHashMap<>();

  // IRCv3 server-time support (canonical message timestamps).
  final AtomicBoolean serverTimeCapAcked = new AtomicBoolean(false);
  final AtomicBoolean standardRepliesCapAcked = new AtomicBoolean(false);
  final AtomicBoolean monitorSupported = new AtomicBoolean(false);
  final AtomicLong monitorMaxTargets = new AtomicLong(0L);
  // Warn once per application run if server-time wasn't negotiated on this server.
  final AtomicBoolean serverTimeMissingWarned = new AtomicBoolean(false);

  // Warn once per connection if typing isn't available.
  final AtomicBoolean typingMissingWarned = new AtomicBoolean(false);

  // One-time connect log summary of negotiated caps.
  final AtomicBoolean capSummaryLogged = new AtomicBoolean(false);

  // Current connection metadata (used by transport/capability policy helpers).
  final AtomicReference<String> connectedHost = new AtomicReference<>("");
  final AtomicBoolean connectedWithTls = new AtomicBoolean(false);
  final AtomicBoolean registrationComplete = new AtomicBoolean(false);

  // Best-effort bridge between InputParser command metadata and PrivateMessageEvent objects.
  private final PircbotxPrivateTargetHintStore privateTargetHints =
      new PircbotxPrivateTargetHintStore();
  private final Map<String, Long> recentChannelMode324ByKey = new ConcurrentHashMap<>();

  public PircbotxConnectionState(String serverId) {
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
    stsCapAcked.set(false);
    multilineCapAcked.set(false);
    draftMultilineCapAcked.set(false);
    multilineMaxBytes.set(0L);
    multilineMaxLines.set(0L);
    draftMultilineMaxBytes.set(0L);
    draftMultilineMaxLines.set(0L);
    multilineOfferedMaxBytes.set(0L);
    multilineOfferedMaxLines.set(0L);
    draftMultilineOfferedMaxBytes.set(0L);
    draftMultilineOfferedMaxLines.set(0L);
    draftChannelContextCapAcked.set(false);
    draftReplyCapAcked.set(false);
    draftReactCapAcked.set(false);
    draftUnreactCapAcked.set(false);
    draftMessageEditCapAcked.set(false);
    draftMessageRedactionCapAcked.set(false);
    messageTagsCapAcked.set(false);
    typingClientTagAllowed.set(true);
    typingClientTagPolicyKnown.set(false);
    typingCapAcked.set(false);
    messageTagsFallbackReqSent.set(false);
    batchFallbackReqSent.set(false);
    chatHistoryFallbackReqSent.set(false);
    readMarkerCapAcked.set(false);
    monitorCapAcked.set(false);
    extendedMonitorCapAcked.set(false);
    sojuBouncerNetworksCapAcked.set(false);
    serverTimeCapAcked.set(false);
    standardRepliesCapAcked.set(false);
    monitorSupported.set(false);
    monitorMaxTargets.set(0L);
    capSummaryLogged.set(false);
    typingMissingWarned.set(false);
    connectedHost.set("");
    connectedWithTls.set(false);
    registrationComplete.set(false);
    resetLagProbeState();
    clearPrivateTargetHints();
    recentChannelMode324ByKey.clear();
  }

  public void rememberPrivateTargetHint(
      String fromNick,
      String target,
      String kind,
      String payload,
      String messageId,
      long observedAtMs) {
    privateTargetHints.remember(fromNick, target, kind, payload, messageId, observedAtMs);
  }

  public String findPrivateTargetHint(
      String fromNick, String kind, String payload, String messageId, long nowMs) {
    return privateTargetHints.find(fromNick, kind, payload, messageId, nowMs);
  }

  public void onPlaybackControlLine(String line) {
    zncPlaybackCapture.onPlaybackControlLine(line);
  }

  public boolean shouldCapturePlayback(String target, Instant at) {
    return zncPlaybackCapture.shouldCapture(target, at);
  }

  public void addPlaybackEntry(ChatHistoryEntry entry) {
    if (entry != null) {
      zncPlaybackCapture.addEntry(entry);
    }
  }

  void beginLagProbe(String token, long sentAtMs) {
    String normalizedToken = normalizeLagToken(token);
    if (normalizedToken.isEmpty()) return;
    long sent = sentAtMs > 0 ? sentAtMs : System.currentTimeMillis();
    lagProbeToken.set(normalizedToken);
    lagProbeSentAtMs.set(sent);
  }

  boolean observeLagProbePong(String token, long observedAtMs) {
    String observedToken = normalizeLagToken(token);
    if (observedToken.isEmpty()) return false;
    String expected = lagProbeToken.get();
    if (expected.isEmpty()) return false;
    if (!Objects.equals(expected, observedToken)) return false;
    if (!lagProbeToken.compareAndSet(expected, "")) return false;

    long sentAt = lagProbeSentAtMs.getAndSet(0L);
    long now = observedAtMs > 0 ? observedAtMs : System.currentTimeMillis();
    long lagMs = Math.max(0L, now - sentAt);
    lagLastMeasuredMs.set(lagMs);
    lagLastMeasuredAtMs.set(now);
    return true;
  }

  void observePassiveLagSample(long lagMs, long observedAtMs) {
    long sample = Math.max(0L, lagMs);
    if (sample > MAX_PASSIVE_LAG_SAMPLE_MS) return;
    long now = observedAtMs > 0 ? observedAtMs : System.currentTimeMillis();
    lagLastMeasuredMs.set(sample);
    lagLastMeasuredAtMs.set(now);
  }

  long lagMsIfFresh(long nowMs) {
    long measuredAt = lagLastMeasuredAtMs.get();
    if (measuredAt <= 0) return -1L;
    long now = nowMs > 0 ? nowMs : System.currentTimeMillis();
    if (now - measuredAt > LAG_SAMPLE_STALE_AFTER_MS) return -1L;
    return Math.max(-1L, lagLastMeasuredMs.get());
  }

  void resetLagProbeState() {
    lagProbeToken.set("");
    lagProbeSentAtMs.set(0L);
    lagLastMeasuredMs.set(-1L);
    lagLastMeasuredAtMs.set(0L);
  }

  void clearPrivateTargetHints() {
    privateTargetHints.clear();
  }

  boolean tryClaimChannelMode324(String channel, String details) {
    String key = channelMode324Key(channel, details);
    if (key.isEmpty()) return true;

    long now = System.currentTimeMillis();
    cleanupRecentChannelMode324(now);

    Long previous = recentChannelMode324ByKey.put(key, now);
    return previous == null || (now - previous.longValue()) > CHANNEL_MODE_324_DEDUPE_TTL_MS;
  }

  private void cleanupRecentChannelMode324(long now) {
    long cutoff = now - CHANNEL_MODE_324_DEDUPE_TTL_MS;
    recentChannelMode324ByKey
        .entrySet()
        .removeIf(e -> e.getValue() == null || e.getValue() < cutoff);

    // Keep this short-lived dedupe map hard-bounded even during very large join bursts.
    if (recentChannelMode324ByKey.size() > CHANNEL_MODE_324_DEDUPE_MAX) {
      recentChannelMode324ByKey.clear();
    }
  }

  private static String normalizeLower(String raw) {
    String s = Objects.toString(raw, "").trim();
    return s.isEmpty() ? "" : s.toLowerCase(java.util.Locale.ROOT);
  }

  private static String normalizeLagToken(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String channelMode324Key(String channel, String details) {
    String normalizedChannel = normalizeLower(channel);
    String normalizedDetails = normalizeWhitespace(details);
    if (normalizedChannel.isEmpty() || normalizedDetails.isEmpty()) return "";
    return normalizedChannel + '\n' + normalizedDetails;
  }

  private static String normalizeWhitespace(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return "";
    return value.replaceAll("\\s+", " ");
  }
}
