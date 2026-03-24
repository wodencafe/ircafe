package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Instant;
import java.util.Locale;
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
public final class PircbotxConnectionState {
  final String serverId;
  final AtomicReference<PircBotX> botRef = new AtomicReference<>();
  final AtomicReference<String> selfNickHint = new AtomicReference<>("");

  final AtomicLong lastInboundMs = new AtomicLong(0);
  final AtomicBoolean localTimeoutEmitted = new AtomicBoolean(false);
  final AtomicReference<Disposable> heartbeatDisposable = new AtomicReference<>();
  private final PircbotxLagProbeState lagProbe = new PircbotxLagProbeState();

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

  final AtomicBoolean zncPlaybackCapAcked = new AtomicBoolean(false);
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
  private final PircbotxChannelMode324Deduper channelMode324Deduper =
      new PircbotxChannelMode324Deduper();
  private final PircbotxBouncerDiscoveryState bouncerDiscovery =
      new PircbotxBouncerDiscoveryState();

  public PircbotxConnectionState(String serverId) {
    this.serverId = serverId;
  }

  /** Immutable read view over negotiated IRCv3 capability state for one connection. */
  public record CapabilitySnapshot(
      boolean zncPlaybackCapAcked,
      boolean batchCapAcked,
      boolean chatHistoryCapAcked,
      boolean echoMessageCapAcked,
      boolean capNotifyCapAcked,
      boolean labeledResponseCapAcked,
      boolean setnameCapAcked,
      boolean chghostCapAcked,
      boolean stsCapAcked,
      boolean multilineCapAcked,
      boolean draftMultilineCapAcked,
      long multilineMaxBytes,
      long multilineMaxLines,
      long draftMultilineMaxBytes,
      long draftMultilineMaxLines,
      boolean draftChannelContextCapAcked,
      boolean draftReplyCapAcked,
      boolean draftReactCapAcked,
      boolean draftUnreactCapAcked,
      boolean draftMessageEditCapAcked,
      boolean draftMessageRedactionCapAcked,
      boolean messageTagsCapAcked,
      boolean typingClientTagAllowed,
      boolean typingClientTagPolicyKnown,
      boolean typingCapAcked,
      boolean readMarkerCapAcked,
      boolean monitorCapAcked,
      boolean extendedMonitorCapAcked,
      boolean sojuBouncerNetworksCapAcked,
      boolean serverTimeCapAcked,
      boolean standardRepliesCapAcked,
      boolean monitorSupported,
      long monitorMaxTargets) {

    public boolean draftUnreactAvailable() {
      return draftUnreactCapAcked || draftReactCapAcked;
    }

    public boolean multilineAvailable() {
      return multilineCapAcked || draftMultilineCapAcked;
    }

    public long negotiatedMultilineMaxBytes() {
      if (multilineCapAcked) {
        return Math.max(0L, multilineMaxBytes);
      }
      if (draftMultilineCapAcked) {
        return Math.max(0L, draftMultilineMaxBytes);
      }
      return 0L;
    }

    public long negotiatedMultilineMaxLines() {
      if (multilineCapAcked) {
        return Math.max(0L, multilineMaxLines);
      }
      if (draftMultilineCapAcked) {
        return Math.max(0L, draftMultilineMaxLines);
      }
      return 0L;
    }

    public boolean typingAllowedByPolicy() {
      return typingClientTagPolicyKnown && typingClientTagAllowed;
    }

    public boolean typingAvailable() {
      return messageTagsCapAcked && (typingCapAcked || typingAllowedByPolicy());
    }

    public boolean chatHistoryAvailable() {
      return chatHistoryCapAcked && batchCapAcked;
    }

    public boolean monitorAvailable() {
      return monitorSupported || monitorCapAcked;
    }
  }

  public PircBotX currentBot() {
    return botRef.get();
  }

  public boolean hasBot() {
    return currentBot() != null;
  }

  public void setBot(PircBotX bot) {
    botRef.set(bot);
  }

  public boolean clearBotIf(PircBotX bot) {
    return botRef.compareAndSet(bot, null);
  }

  public PircBotX takeBot() {
    return botRef.getAndSet(null);
  }

  public String selfNickHint() {
    return selfNickHint.get();
  }

  public void setSelfNickHint(String nick) {
    selfNickHint.set(Objects.toString(nick, ""));
  }

  public void setConnectedEndpoint(String host, boolean tls) {
    connectedHost.set(Objects.toString(host, "").trim());
    connectedWithTls.set(tls);
  }

  public String connectedHost() {
    return connectedHost.get();
  }

  public boolean connectedWithTls() {
    return connectedWithTls.get();
  }

  public void markRegistrationComplete() {
    registrationComplete.set(true);
  }

  public boolean registrationComplete() {
    return registrationComplete.get();
  }

  public void recordInboundActivity(long observedAtMs) {
    lastInboundMs.set(observedAtMs);
    localTimeoutEmitted.set(false);
  }

  public long lastInboundActivityMs() {
    return lastInboundMs.get();
  }

  public boolean localTimeoutEmitted() {
    return localTimeoutEmitted.get();
  }

  public void setLocalTimeoutEmitted(boolean emitted) {
    localTimeoutEmitted.set(emitted);
  }

  public void ensureHeartbeatClock(long nowMs, boolean resetIdleClock) {
    if (resetIdleClock || lastInboundMs.get() <= 0L) {
      lastInboundMs.set(nowMs);
    }
    localTimeoutEmitted.set(false);
  }

  public long idleMsAt(long nowMs) {
    return nowMs - lastInboundMs.get();
  }

  public boolean markLocalTimeout(String reason) {
    if (!localTimeoutEmitted.compareAndSet(false, true)) {
      return false;
    }
    overrideDisconnectReason(reason);
    return true;
  }

  public Disposable replaceHeartbeatDisposable(Disposable next) {
    return heartbeatDisposable.getAndSet(next);
  }

  public Disposable clearHeartbeatDisposable() {
    return heartbeatDisposable.getAndSet(null);
  }

  public void markManualDisconnect() {
    manualDisconnect.set(true);
  }

  public void clearManualDisconnect() {
    manualDisconnect.set(false);
  }

  public boolean manualDisconnectRequested() {
    return manualDisconnect.get();
  }

  public void resetReconnectAttempts() {
    reconnectAttempts.set(0L);
  }

  public long reconnectAttempts() {
    return reconnectAttempts.get();
  }

  public void setReconnectAttempts(long attempts) {
    reconnectAttempts.set(Math.max(0L, attempts));
  }

  public long nextReconnectAttempt() {
    return reconnectAttempts.incrementAndGet();
  }

  public Disposable replaceReconnectDisposable(Disposable next) {
    return reconnectDisposable.getAndSet(next);
  }

  public Disposable clearReconnectDisposable() {
    return reconnectDisposable.getAndSet(null);
  }

  public String disconnectReasonOverride() {
    return disconnectReasonOverride.get();
  }

  public void overrideDisconnectReason(String reason) {
    disconnectReasonOverride.set(reason);
  }

  public String takeDisconnectReasonOverride() {
    return disconnectReasonOverride.getAndSet(null);
  }

  public void suppressAutoReconnectOnce() {
    suppressAutoReconnectOnce.set(true);
  }

  public boolean autoReconnectSuppressed() {
    return suppressAutoReconnectOnce.get();
  }

  public boolean consumeSuppressAutoReconnectOnce() {
    return suppressAutoReconnectOnce.getAndSet(false);
  }

  public boolean isZncDetected() {
    return bouncerDiscovery.isZncDetected();
  }

  public boolean markZncDetected() {
    return bouncerDiscovery.markZncDetected();
  }

  public boolean markZncDetectionLogged() {
    return bouncerDiscovery.markZncDetectionLogged();
  }

  public boolean zncDetectionLogged() {
    return bouncerDiscovery.zncDetectionLogged();
  }

  public void clearZncDetection() {
    bouncerDiscovery.clearZncDetection();
  }

  public String zncBaseUser() {
    return bouncerDiscovery.zncBaseUser();
  }

  public String zncClientId() {
    return bouncerDiscovery.zncClientId();
  }

  public String zncNetwork() {
    return bouncerDiscovery.zncNetwork();
  }

  public void setZncLoginContext(String baseUser, String clientId, String network) {
    bouncerDiscovery.setZncLoginContext(baseUser, clientId, network);
  }

  public void clearZncLoginContext() {
    bouncerDiscovery.clearZncLoginContext();
  }

  public boolean beginZncPlaybackRequest() {
    return bouncerDiscovery.beginZncPlaybackRequest();
  }

  public void clearZncPlaybackRequest() {
    bouncerDiscovery.clearZncPlaybackRequest();
  }

  public boolean zncPlaybackRequestedThisSession() {
    return bouncerDiscovery.zncPlaybackRequestedThisSession();
  }

  public boolean beginZncListNetworksRequest() {
    return bouncerDiscovery.beginZncListNetworksRequest();
  }

  public void beginZncListNetworksCapture(long startedAtMs) {
    bouncerDiscovery.beginZncListNetworksCapture(startedAtMs);
  }

  public boolean isZncListNetworksCaptureActive() {
    return bouncerDiscovery.isZncListNetworksCaptureActive();
  }

  public long zncListNetworksCaptureStartedAtMs() {
    return bouncerDiscovery.zncListNetworksCaptureStartedAtMs();
  }

  public void finishZncListNetworksCapture() {
    bouncerDiscovery.finishZncListNetworksCapture();
  }

  public void clearZncListNetworksRequest() {
    bouncerDiscovery.clearZncListNetworksRequest();
  }

  public boolean zncListNetworksRequestedThisSession() {
    return bouncerDiscovery.zncListNetworksRequestedThisSession();
  }

  public void clearZncDiscoveredNetworks() {
    bouncerDiscovery.clearZncDiscoveredNetworks();
  }

  public int zncDiscoveredNetworkCount() {
    return bouncerDiscovery.zncDiscoveredNetworkCount();
  }

  public BouncerDiscoveredNetwork zncDiscoveredNetwork(String key) {
    return bouncerDiscovery.zncDiscoveredNetwork(key);
  }

  public void storeZncDiscoveredNetwork(String key, BouncerDiscoveredNetwork network) {
    bouncerDiscovery.storeZncDiscoveredNetwork(key, network);
  }

  public boolean beginSojuListNetworksRequest() {
    return bouncerDiscovery.beginSojuListNetworksRequest();
  }

  public void clearSojuListNetworksRequest() {
    bouncerDiscovery.clearSojuListNetworksRequest();
  }

  public boolean sojuListNetworksRequestedThisSession() {
    return bouncerDiscovery.sojuListNetworksRequestedThisSession();
  }

  public void clearSojuDiscoveredNetworks() {
    bouncerDiscovery.clearSojuDiscoveredNetworks();
  }

  public BouncerDiscoveredNetwork sojuDiscoveredNetwork(String netId) {
    return bouncerDiscovery.sojuDiscoveredNetwork(netId);
  }

  public void storeSojuDiscoveredNetwork(String netId, BouncerDiscoveredNetwork network) {
    bouncerDiscovery.storeSojuDiscoveredNetwork(netId, network);
  }

  public boolean hasSojuDiscoveredNetwork(String netId) {
    return bouncerDiscovery.hasSojuDiscoveredNetwork(netId);
  }

  public boolean hasAnySojuDiscoveredNetworks() {
    return bouncerDiscovery.hasAnySojuDiscoveredNetworks();
  }

  public String sojuBouncerNetId() {
    return bouncerDiscovery.sojuBouncerNetId();
  }

  public void setSojuBouncerNetId(String netId) {
    bouncerDiscovery.setSojuBouncerNetId(netId);
  }

  public void clearSojuBouncerNetId() {
    bouncerDiscovery.clearSojuBouncerNetId();
  }

  public void clearGenericBouncerDiscoveredNetworks() {
    bouncerDiscovery.clearGenericBouncerDiscoveredNetworks();
  }

  public BouncerDiscoveredNetwork genericBouncerDiscoveredNetwork(String key) {
    return bouncerDiscovery.genericBouncerDiscoveredNetwork(key);
  }

  public void storeGenericBouncerDiscoveredNetwork(String key, BouncerDiscoveredNetwork network) {
    bouncerDiscovery.storeGenericBouncerDiscoveredNetwork(key, network);
  }

  public boolean hasGenericBouncerDiscoveredNetwork(String key) {
    return bouncerDiscovery.hasGenericBouncerDiscoveredNetwork(key);
  }

  public boolean hasAnyGenericBouncerDiscoveredNetworks() {
    return bouncerDiscovery.hasAnyGenericBouncerDiscoveredNetworks();
  }

  public boolean beginCapabilitySummaryLog() {
    return !capSummaryLogged.getAndSet(true);
  }

  public CapabilitySnapshot capabilitySnapshot() {
    return new CapabilitySnapshot(
        zncPlaybackCapAcked.get(),
        batchCapAcked.get(),
        chatHistoryCapAcked.get(),
        echoMessageCapAcked.get(),
        capNotifyCapAcked.get(),
        labeledResponseCapAcked.get(),
        setnameCapAcked.get(),
        chghostCapAcked.get(),
        stsCapAcked.get(),
        multilineCapAcked.get(),
        draftMultilineCapAcked.get(),
        multilineMaxBytes.get(),
        multilineMaxLines.get(),
        draftMultilineMaxBytes.get(),
        draftMultilineMaxLines.get(),
        draftChannelContextCapAcked.get(),
        draftReplyCapAcked.get(),
        draftReactCapAcked.get(),
        draftUnreactCapAcked.get(),
        draftMessageEditCapAcked.get(),
        draftMessageRedactionCapAcked.get(),
        messageTagsCapAcked.get(),
        typingClientTagAllowed.get(),
        typingClientTagPolicyKnown.get(),
        typingCapAcked.get(),
        readMarkerCapAcked.get(),
        monitorCapAcked.get(),
        extendedMonitorCapAcked.get(),
        sojuBouncerNetworksCapAcked.get(),
        serverTimeCapAcked.get(),
        standardRepliesCapAcked.get(),
        monitorSupported.get(),
        monitorMaxTargets.get());
  }

  public boolean isZncPlaybackCapAcked() {
    return zncPlaybackCapAcked.get();
  }

  public void setZncPlaybackCapAcked(boolean acked) {
    zncPlaybackCapAcked.set(acked);
  }

  public boolean isSojuBouncerNetworksCapAcked() {
    return sojuBouncerNetworksCapAcked.get();
  }

  public void setSojuBouncerNetworksCapAcked(boolean acked) {
    sojuBouncerNetworksCapAcked.set(acked);
  }

  public boolean updateMonitorSupport(boolean supported, long limit) {
    boolean normalizedSupported = supported;
    long normalizedLimit = Math.max(0L, limit);
    boolean prevSupported = monitorSupported.getAndSet(normalizedSupported);
    long prevLimit = monitorMaxTargets.getAndSet(normalizedLimit);
    return prevSupported != normalizedSupported || prevLimit != normalizedLimit;
  }

  public boolean updateTypingClientTagPolicy(boolean allowed) {
    typingClientTagPolicyKnown.set(true);
    boolean prev = typingClientTagAllowed.getAndSet(allowed);
    return prev != allowed;
  }

  public void clearSojuDiscoverySession() {
    clearSojuDiscoveredNetworks();
    clearSojuListNetworksRequest();
    clearSojuBouncerNetId();
    sojuBouncerNetworksCapAcked.set(false);
  }

  public void startZncPlaybackCapture(
      String serverId,
      String target,
      Instant fromInclusive,
      Instant toInclusive,
      java.util.function.Consumer<ServerIrcEvent> emit) {
    zncPlaybackCapture.start(serverId, target, fromInclusive, toInclusive, emit);
  }

  public void cancelZncPlaybackCapture(String reason) {
    zncPlaybackCapture.cancelActive(reason);
  }

  public boolean shouldWarnMissingServerTime() {
    return serverTimeMissingWarned.compareAndSet(false, true);
  }

  public boolean shouldWarnUnavailableTyping() {
    return typingMissingWarned.compareAndSet(false, true);
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
    channelMode324Deduper.clear();
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

  public boolean rememberHostmaskIfChanged(String nick, String hostmask) {
    String key = normalizedNickKey(nick);
    if (key == null || hostmask == null || hostmask.isBlank()) {
      return false;
    }
    String prev = lastHostmaskByNickLower.put(key, hostmask);
    return !Objects.equals(prev, hostmask);
  }

  public void beginWhoisProbe(String nick) {
    String key = normalizedNickKey(nick);
    if (key == null) {
      return;
    }
    whoisSawAwayByNickLower.putIfAbsent(key, Boolean.FALSE);
    whoisSawAccountByNickLower.putIfAbsent(key, Boolean.FALSE);
  }

  public void markWhoisAwayObserved(String nick) {
    String key = normalizedNickKey(nick);
    if (key != null) {
      whoisSawAwayByNickLower.computeIfPresent(key, (ignored, prior) -> Boolean.TRUE);
    }
  }

  public void markWhoisAccountObserved(String nick) {
    String key = normalizedNickKey(nick);
    if (key != null) {
      whoisSawAccountByNickLower.computeIfPresent(key, (ignored, prior) -> Boolean.TRUE);
    }
  }

  public Boolean completeWhoisAwayProbe(String nick) {
    String key = normalizedNickKey(nick);
    return key == null ? null : whoisSawAwayByNickLower.remove(key);
  }

  public Boolean completeWhoisAccountProbe(String nick) {
    String key = normalizedNickKey(nick);
    return key == null ? null : whoisSawAccountByNickLower.remove(key);
  }

  public void markWhoisAccountNumericSupported() {
    whoisAccountNumericSupported.set(true);
  }

  public boolean whoisAccountNumericSupported() {
    return whoisAccountNumericSupported.get();
  }

  public boolean markWhoxSchemaCompatibleObserved() {
    if (whoxSchemaCompatibleEmitted.compareAndSet(false, true)) {
      whoxSchemaCompatible.set(true);
      return true;
    }
    return false;
  }

  public boolean markWhoxSchemaIncompatibleObserved() {
    if (whoxSchemaIncompatibleEmitted.compareAndSet(false, true)) {
      whoxSchemaCompatible.set(false);
      return true;
    }
    return false;
  }

  void beginLagProbe(String token, long sentAtMs) {
    lagProbe.beginProbe(token, sentAtMs);
  }

  boolean observeLagProbePong(String token, long observedAtMs) {
    return lagProbe.observePong(token, observedAtMs);
  }

  void observePassiveLagSample(long lagMs, long observedAtMs) {
    lagProbe.observePassiveSample(lagMs, observedAtMs);
  }

  long lagMsIfFresh(long nowMs) {
    return lagProbe.lagMsIfFresh(nowMs);
  }

  void resetLagProbeState() {
    lagProbe.reset();
  }

  void clearPrivateTargetHints() {
    privateTargetHints.clear();
  }

  public boolean tryClaimChannelMode324(String channel, String details) {
    return channelMode324Deduper.tryClaim(channel, details);
  }

  String currentLagProbeToken() {
    return lagProbe.currentProbeToken();
  }

  long currentLagProbeSentAtMs() {
    return lagProbe.currentProbeSentAtMs();
  }

  long currentMeasuredLagMs() {
    return lagProbe.currentMeasuredLagMs();
  }

  long currentMeasuredLagAtMs() {
    return lagProbe.currentMeasuredAtMs();
  }

  private static String normalizedNickKey(String nick) {
    if (nick == null || nick.isBlank()) {
      return null;
    }
    return nick.trim().toLowerCase(Locale.ROOT);
  }
}
