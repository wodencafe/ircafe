package cafe.woden.ircclient.irc.pircbotx.state;

import cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import io.reactivex.rxjava3.disposables.Disposable;
import java.time.Instant;
import java.util.Objects;
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
  private final AtomicReference<PircBotX> reconnectSourceBot = new AtomicReference<>();
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

  private final Ircv3HostmaskChangeTracker hostmaskChanges = new Ircv3HostmaskChangeTracker();
  private final Ircv3WhoisProbeTracker whoisProbes = new Ircv3WhoisProbeTracker();
  private final Ircv3WhoxSchemaTracker whoxSchema = new Ircv3WhoxSchemaTracker();

  private final Ircv3CapabilityState capabilities = new Ircv3CapabilityState();
  final ZncPlaybackCaptureCoordinator zncPlaybackCapture = new ZncPlaybackCaptureCoordinator();

  // Current connection metadata (used by transport/capability policy helpers).
  final AtomicReference<String> connectedHost = new AtomicReference<>("");
  final AtomicBoolean connectedWithTls = new AtomicBoolean(false);
  final AtomicBoolean registrationComplete = new AtomicBoolean(false);

  // Best-effort bridge between InputParser command metadata and PrivateMessageEvent objects.
  private final Ircv3EchoMessageTargetHintStore privateTargetHints =
      new Ircv3EchoMessageTargetHintStore();
  private final PircbotxChannelMode324Deduper channelMode324Deduper =
      new PircbotxChannelMode324Deduper();
  private final PircbotxBouncerDiscoveryState bouncerDiscovery =
      new PircbotxBouncerDiscoveryState();

  public PircbotxConnectionState(String serverId) {
    this.serverId = serverId;
  }

  public String serverId() {
    return serverId;
  }

  public PircBotX currentBot() {
    return botRef.get();
  }

  public boolean hasBot() {
    return currentBot() != null;
  }

  public void setBot(PircBotX bot) {
    botRef.set(bot);
    reconnectSourceBot.set(bot);
  }

  public boolean clearBotIf(PircBotX bot) {
    return botRef.compareAndSet(bot, null);
  }

  public PircBotX takeBot() {
    PircBotX bot = botRef.getAndSet(null);
    reconnectSourceBot.compareAndSet(bot, null);
    return bot;
  }

  /**
   * Claims reconnect handling once per bot session. Disconnect callbacks may arrive after the bot
   * loop has cleared its reference, or after a newer session has started.
   */
  public boolean claimReconnectFor(PircBotX bot) {
    return bot != null && reconnectSourceBot.compareAndSet(bot, null);
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
    return capabilities.beginCapabilitySummaryObservation();
  }

  public Ircv3CapabilitySnapshot capabilitySnapshot() {
    return capabilities.snapshot();
  }

  public boolean isZncPlaybackCapAcked() {
    return capabilities.zncPlaybackCapAcked();
  }

  public boolean isBatchCapAcked() {
    return capabilities.batchCapAcked();
  }

  public boolean isChatHistoryCapAcked() {
    return capabilities.chatHistoryCapAcked();
  }

  public boolean isMessageTagsCapAcked() {
    return capabilities.messageTagsCapAcked();
  }

  public boolean beginMessageTagsFallbackRequest() {
    return capabilities.beginMessageTagsFallbackRequest();
  }

  public void clearMessageTagsFallbackRequest() {
    capabilities.clearMessageTagsFallbackRequest();
  }

  public boolean beginBatchFallbackRequest() {
    return capabilities.beginBatchFallbackRequest();
  }

  public void clearBatchFallbackRequest() {
    capabilities.clearBatchFallbackRequest();
  }

  public boolean beginChatHistoryFallbackRequest() {
    return capabilities.beginChatHistoryFallbackRequest();
  }

  public void clearChatHistoryFallbackRequest() {
    capabilities.clearChatHistoryFallbackRequest();
  }

  public void setZncPlaybackCapAcked(boolean acked) {
    capabilities.setZncPlaybackCapAcked(acked);
  }

  public void setBatchCapAcked(boolean acked) {
    capabilities.setBatchCapAcked(acked);
  }

  public void setChatHistoryCapAcked(boolean acked) {
    capabilities.setChatHistoryCapAcked(acked);
  }

  public void setEchoMessageCapAcked(boolean acked) {
    capabilities.setEchoMessageCapAcked(acked);
  }

  public void setMultilineCapAcked(boolean acked) {
    capabilities.setMultilineCapAcked(acked);
  }

  public void setDraftMultilineCapAcked(boolean acked) {
    capabilities.setDraftMultilineCapAcked(acked);
  }

  public void setMultilineLimits(long maxBytes, long maxLines) {
    capabilities.setMultilineLimits(maxBytes, maxLines);
  }

  public void setDraftMultilineLimits(long maxBytes, long maxLines) {
    capabilities.setDraftMultilineLimits(maxBytes, maxLines);
  }

  public void setMessageTagsCapAcked(boolean acked) {
    capabilities.setMessageTagsCapAcked(acked);
  }

  public void setReadMarkerCapAcked(boolean acked) {
    capabilities.setReadMarkerCapAcked(acked);
  }

  public long multilineOfferedMaxBytes(boolean draft) {
    return capabilities.multilineOfferedMaxBytes(draft);
  }

  public long multilineOfferedMaxLines(boolean draft) {
    return capabilities.multilineOfferedMaxLines(draft);
  }

  public void setMultilineOfferedMaxBytes(boolean draft, long maxBytes) {
    capabilities.setMultilineOfferedMaxBytes(draft, maxBytes);
  }

  public void setMultilineOfferedMaxLines(boolean draft, long maxLines) {
    capabilities.setMultilineOfferedMaxLines(draft, maxLines);
  }

  public void setNegotiatedMultilineMaxBytes(boolean draft, long maxBytes) {
    capabilities.setNegotiatedMultilineMaxBytes(draft, maxBytes);
  }

  public void setNegotiatedMultilineMaxLines(boolean draft, long maxLines) {
    capabilities.setNegotiatedMultilineMaxLines(draft, maxLines);
  }

  public boolean updateTrackedCapability(String capabilityName, boolean enabled) {
    return capabilities.updateTrackedCapability(capabilityName, enabled);
  }

  public boolean isSojuBouncerNetworksCapAcked() {
    return capabilities.sojuBouncerNetworksCapAcked();
  }

  public void setSojuBouncerNetworksCapAcked(boolean acked) {
    capabilities.setSojuBouncerNetworksCapAcked(acked);
  }

  public boolean updateMonitorSupport(boolean supported, long limit) {
    return capabilities.updateMonitorSupport(supported, limit);
  }

  public boolean updateTypingClientTagPolicy(boolean allowed) {
    return capabilities.updateTypingClientTagPolicy(allowed);
  }

  public void clearSojuDiscoverySession() {
    clearSojuDiscoveredNetworks();
    clearSojuListNetworksRequest();
    clearSojuBouncerNetId();
    capabilities.setSojuBouncerNetworksCapAcked(false);
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

  public void completeZncPlaybackCapture(String reason) {
    zncPlaybackCapture.completeActive(reason);
  }

  public boolean shouldWarnMissingServerTime() {
    return capabilities.shouldWarnMissingServerTime();
  }

  public boolean shouldWarnUnavailableTyping() {
    return capabilities.shouldWarnUnavailableTyping();
  }

  public void resetNegotiatedCaps() {
    capabilities.resetConnectionSession();
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
    return hostmaskChanges.rememberIfChanged(nick, hostmask);
  }

  public void beginWhoisProbe(String nick) {
    whoisProbes.begin(nick);
  }

  public void markWhoisAwayObserved(String nick) {
    whoisProbes.observeAway(nick);
  }

  public void markWhoisAccountObserved(String nick) {
    whoisProbes.observeAccount(nick);
  }

  public Ircv3WhoisProbeTracker.Completion completeWhoisProbe(String nick) {
    return whoisProbes.complete(nick);
  }

  public boolean markWhoxSchemaCompatibleObserved() {
    return whoxSchema.observeCompatible();
  }

  public boolean markWhoxSchemaIncompatibleObserved() {
    return whoxSchema.observeIncompatible();
  }

  public void beginLagProbe(String token, long sentAtMs) {
    lagProbe.beginProbe(token, sentAtMs);
  }

  public boolean observeLagProbePong(String token, long observedAtMs) {
    return lagProbe.observePong(token, observedAtMs);
  }

  public void observePassiveLagSample(long lagMs, long observedAtMs) {
    lagProbe.observePassiveSample(lagMs, observedAtMs);
  }

  public long lagMsIfFresh(long nowMs) {
    return lagProbe.lagMsIfFresh(nowMs);
  }

  public void resetLagProbeState() {
    lagProbe.reset();
  }

  void clearPrivateTargetHints() {
    privateTargetHints.clear();
  }

  public boolean tryClaimChannelMode324(String channel, String details) {
    return channelMode324Deduper.tryClaim(channel, details);
  }

  public String currentLagProbeToken() {
    return lagProbe.currentProbeToken();
  }

  public long currentLagProbeSentAtMs() {
    return lagProbe.currentProbeSentAtMs();
  }

  public long currentMeasuredLagMs() {
    return lagProbe.currentMeasuredLagMs();
  }

  public long currentMeasuredLagAtMs() {
    return lagProbe.currentMeasuredAtMs();
  }

  public boolean isZncPlaybackCaptureActive() {
    return zncPlaybackCapture.isActive();
  }

  public java.util.Optional<String> activeZncPlaybackCaptureTarget() {
    return zncPlaybackCapture.activeTarget();
  }

  public boolean hasPendingWhoisProbe(String nick) {
    return whoisProbes.hasPending(nick);
  }
}
