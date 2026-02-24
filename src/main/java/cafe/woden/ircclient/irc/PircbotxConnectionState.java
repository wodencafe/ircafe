package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.irc.soju.SojuNetwork;
import cafe.woden.ircclient.irc.znc.ZncNetwork;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
final class PircbotxConnectionState {
  private static final long PRIVATE_TARGET_HINT_TTL_MS = 120_000L;
  private static final int PRIVATE_TARGET_HINT_MAX = 1_024;

  private record PrivateTargetHint(
      String fromLower, String target, String kind, String payload, long observedAtMs) {}

  final String serverId;
  final AtomicReference<PircBotX> botRef = new AtomicReference<>();
  final AtomicReference<String> selfNickHint = new AtomicReference<>("");

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
  final AtomicBoolean draftReplyCapAcked = new AtomicBoolean(false);
  final AtomicBoolean draftReactCapAcked = new AtomicBoolean(false);
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

  final AtomicBoolean typingCapAcked = new AtomicBoolean(false);
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
  final Map<String, SojuNetwork> sojuNetworksByNetId = new ConcurrentHashMap<>();

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

  // Best-effort bridge between InputParser command metadata and PrivateMessageEvent objects.
  private final Map<String, PrivateTargetHint> privateTargetHintByMessageId =
      new ConcurrentHashMap<>();
  private final Map<String, PrivateTargetHint> privateTargetHintByFingerprint =
      new ConcurrentHashMap<>();

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
    draftReplyCapAcked.set(false);
    draftReactCapAcked.set(false);
    draftMessageEditCapAcked.set(false);
    draftMessageRedactionCapAcked.set(false);
    messageTagsCapAcked.set(false);
    typingClientTagAllowed.set(true);
    typingCapAcked.set(false);
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
    clearPrivateTargetHints();
  }

  void rememberPrivateTargetHint(
      String fromNick,
      String target,
      String kind,
      String payload,
      String messageId,
      long observedAtMs) {
    String from = normalizeLower(fromNick);
    String dest = normalizeTarget(target);
    String k = normalizeKind(kind);
    String body = normalizePayload(payload);
    String msgId = normalizeMessageId(messageId);
    if (from.isEmpty() || dest.isEmpty() || k.isEmpty()) return;
    if (body.isEmpty() && msgId.isEmpty()) return;

    long now = observedAtMs > 0 ? observedAtMs : System.currentTimeMillis();
    cleanupPrivateTargetHints(now);

    PrivateTargetHint hint = new PrivateTargetHint(from, dest, k, body, now);
    if (!msgId.isEmpty()) {
      privateTargetHintByMessageId.put(msgId, hint);
    }
    if (!body.isEmpty()) {
      privateTargetHintByFingerprint.put(fingerprint(from, k, body), hint);
    }
  }

  String findPrivateTargetHint(
      String fromNick, String kind, String payload, String messageId, long nowMs) {
    String from = normalizeLower(fromNick);
    String k = normalizeKind(kind);
    String body = normalizePayload(payload);
    String msgId = normalizeMessageId(messageId);
    long now = nowMs > 0 ? nowMs : System.currentTimeMillis();
    if (from.isEmpty() || k.isEmpty()) return "";

    cleanupPrivateTargetHints(now);

    if (!msgId.isEmpty()) {
      PrivateTargetHint byId = privateTargetHintByMessageId.get(msgId);
      if (isUsableById(byId, from, k, now)) {
        return byId.target();
      }
    }

    if (!body.isEmpty()) {
      PrivateTargetHint byFingerprint =
          privateTargetHintByFingerprint.get(fingerprint(from, k, body));
      if (isUsableByFingerprint(byFingerprint, from, k, body, now)) {
        return byFingerprint.target();
      }
    }
    return "";
  }

  void clearPrivateTargetHints() {
    privateTargetHintByMessageId.clear();
    privateTargetHintByFingerprint.clear();
  }

  private static boolean isUsableById(PrivateTargetHint hint, String from, String kind, long now) {
    if (hint == null) return false;
    if (hint.observedAtMs() + PRIVATE_TARGET_HINT_TTL_MS < now) return false;
    if (!Objects.equals(hint.fromLower(), from)) return false;
    return Objects.equals(hint.kind(), kind);
  }

  private static boolean isUsableByFingerprint(
      PrivateTargetHint hint, String from, String kind, String payload, long now) {
    if (!isUsableById(hint, from, kind, now)) return false;
    return Objects.equals(hint.payload(), payload);
  }

  private void cleanupPrivateTargetHints(long now) {
    long cutoff = now - PRIVATE_TARGET_HINT_TTL_MS;
    privateTargetHintByMessageId
        .entrySet()
        .removeIf(e -> e.getValue() == null || e.getValue().observedAtMs() < cutoff);
    privateTargetHintByFingerprint
        .entrySet()
        .removeIf(e -> e.getValue() == null || e.getValue().observedAtMs() < cutoff);

    // Hard cap in case event volume is very high and many entries have identical timestamps.
    if (privateTargetHintByMessageId.size() > PRIVATE_TARGET_HINT_MAX * 2) {
      privateTargetHintByMessageId.clear();
    }
    if (privateTargetHintByFingerprint.size() > PRIVATE_TARGET_HINT_MAX * 2) {
      privateTargetHintByFingerprint.clear();
    }
  }

  private static String normalizeLower(String raw) {
    String s = Objects.toString(raw, "").trim();
    return s.isEmpty() ? "" : s.toLowerCase(java.util.Locale.ROOT);
  }

  private static String normalizeTarget(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String normalizeKind(String raw) {
    String s = Objects.toString(raw, "").trim().toUpperCase(java.util.Locale.ROOT);
    if (s.isEmpty()) return "";
    return switch (s) {
      case "PRIVMSG", "ACTION" -> s;
      default -> "";
    };
  }

  private static String normalizePayload(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String normalizeMessageId(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static String fingerprint(String fromLower, String kind, String payload) {
    return fromLower + '\n' + kind + '\n' + payload;
  }
}
