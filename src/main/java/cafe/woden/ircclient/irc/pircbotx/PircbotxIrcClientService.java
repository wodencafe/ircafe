package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.config.api.CtcpReplyRuntimeConfigPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@InfrastructureLayer
public class PircbotxIrcClientService
    implements IrcBackendClientService, IrcDisconnectWithSourcePort {

  private static final Logger log = LoggerFactory.getLogger(PircbotxIrcClientService.class);

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();

  private final Map<String, PircbotxConnectionState> connections = new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
  private final ServerCatalog serverCatalog;
  private final PircbotxInputParserHookInstaller inputParserHookInstaller;
  private final PircbotxBotFactory botFactory;
  @NonNull private final PircbotxBridgeListenerFactory bridgeListenerFactory;
  private final PircbotxConnectionTimersRx timers;
  @NonNull private final BouncerDiscoveryEventPort bouncerDiscoveryEvents;
  @NonNull private final BouncerBackendRegistry bouncerBackends;
  private final CtcpReplyRuntimeConfigPort runtimeConfig;
  private final ChatCommandRuntimeConfigPort chatCommandRuntimeConfig;
  @NonNull private final ServerIsupportStatePort serverIsupportState;
  @NonNull private final Ircv3StsPolicyService stsPolicies;
  private final String version;
  private final PircbotxCtcpAutoReplyHandler ctcpAutoReplyHandler;
  private final PircbotxCapabilityCommandSupport capabilityCommandSupport;
  private final PircbotxConnectPreparationSupport connectPreparationSupport;
  private final PircbotxConnectSessionSupport connectSessionSupport;
  private final PircbotxDisconnectSupport disconnectSupport;
  private final PircbotxShutdownSupport shutdownSupport;
  private final PircbotxActionCommandSupport actionCommandSupport;
  private final PircbotxZncPlaybackRequestSupport zncPlaybackRequestSupport;
  private final PircbotxMultilineMessageSupport multilineMessageSupport =
      new PircbotxMultilineMessageSupport();

  public PircbotxIrcClientService(
      IrcProperties props,
      ServerCatalog serverCatalog,
      PircbotxInputParserHookInstaller inputParserHookInstaller,
      PircbotxBotFactory botFactory,
      PircbotxBridgeListenerFactory bridgeListenerFactory,
      CtcpReplyRuntimeConfigPort runtimeConfig,
      ChatCommandRuntimeConfigPort chatCommandRuntimeConfig,
      Ircv3StsPolicyService stsPolicies,
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents,
      PircbotxConnectionTimersRx timers,
      ServerIsupportStatePort serverIsupportState) {
    this.serverCatalog = serverCatalog;
    this.inputParserHookInstaller = inputParserHookInstaller;
    this.botFactory = botFactory;
    this.bridgeListenerFactory =
        Objects.requireNonNull(bridgeListenerFactory, "bridgeListenerFactory");
    this.timers = timers;
    this.bouncerDiscoveryEvents =
        Objects.requireNonNull(bouncerDiscoveryEvents, "bouncerDiscoveryEvents");
    this.bouncerBackends = Objects.requireNonNull(bouncerBackends, "bouncerBackends");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.chatCommandRuntimeConfig =
        Objects.requireNonNull(chatCommandRuntimeConfig, "chatCommandRuntimeConfig");
    this.serverIsupportState = Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    this.stsPolicies = Objects.requireNonNull(stsPolicies, "stsPolicies");
    this.version = Objects.requireNonNull(props, "props").client().version();
    this.ctcpAutoReplyHandler = new PircbotxCtcpAutoReplyHandler(this.version, this.runtimeConfig);
    this.capabilityCommandSupport = new PircbotxCapabilityCommandSupport();
    this.connectPreparationSupport =
        new PircbotxConnectPreparationSupport(
            this.serverCatalog, this.stsPolicies, this.serverIsupportState, this.timers);
    this.connectSessionSupport =
        new PircbotxConnectSessionSupport(
            this.bus,
            this.bridgeListenerFactory,
            this.botFactory,
            this.inputParserHookInstaller,
            this.timers,
            this.version);
    this.disconnectSupport =
        new PircbotxDisconnectSupport(
            this.bus,
            this.serverIsupportState,
            this.timers,
            this.bouncerBackends,
            this.bouncerDiscoveryEvents);
    this.shutdownSupport = new PircbotxShutdownSupport(this.timers);
    this.actionCommandSupport = new PircbotxActionCommandSupport();
    this.zncPlaybackRequestSupport = new PircbotxZncPlaybackRequestSupport(this.bus);
  }

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.IRC;
  }

  /**
   * Reschedules heartbeat tickers for all currently-active connections.
   *
   * <p>This is used when the user changes heartbeat settings in Preferences and clicks Apply. We
   * rebuild the Rx interval so the new check period/timeout takes effect immediately.
   */
  @Override
  public void rescheduleActiveHeartbeats() {
    if (shuttingDown.get()) return;
    for (PircbotxConnectionState c : connections.values()) {
      try {
        if (c != null && c.botRef.get() != null) {
          timers.rescheduleHeartbeat(c);
        }
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return bus.onBackpressureBuffer();
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    PircBotX bot = conn(serverId).botRef.get();
    return bot == null ? Optional.empty() : Optional.ofNullable(bot.getNick());
  }

  @Override
  public Completable connect(String serverId) {
    return Completable.fromAction(
            () -> {
              if (shuttingDown.get()) return;
              PircbotxConnectionState c = conn(serverId);
              if (c.botRef.get() != null) return;
              PircbotxConnectPreparationSupport.PreparedConnect prepared =
                  connectPreparationSupport.prepare(serverId, c);
              PircBotX bot =
                  connectSessionSupport.openSession(
                      serverId,
                      c,
                      prepared.server(),
                      ctcpAutoReplyHandler::handleIfPresent,
                      this::scheduleReconnect,
                      prepared.disconnectOnSaslFailure());
              RxVirtualSchedulers.io()
                  .scheduleDirect(
                      () ->
                          connectSessionSupport.runBotLoop(
                              serverId, c, bot, this::scheduleReconnect));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable disconnect(String serverId) {
    return disconnect(serverId, null, DisconnectRequestSource.USER_REQUEST);
  }

  @Override
  public Completable disconnect(String serverId, String reason) {
    return disconnect(serverId, reason, DisconnectRequestSource.USER_REQUEST);
  }

  @Override
  public Completable disconnect(String serverId, String reason, DisconnectRequestSource source) {
    return Completable.fromAction(
            () -> {
              PircbotxConnectionState c = conn(serverId);
              disconnectSupport.disconnect(serverId, c, reason, source);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return Completable.fromAction(
            () -> {
              String nick = PircbotxUtil.sanitizeNick(newNick);
              requireBot(serverId).sendIRC().changeNick(nick);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return Completable.fromAction(
            () -> {
              String msg = awayMessage == null ? "" : awayMessage.trim();
              if (msg.contains("\r") || msg.contains("\n")) {
                throw new IllegalArgumentException("away message contains CR/LF");
              }
              if (msg.isEmpty()) {
                requireBot(serverId).sendRaw().rawLine("AWAY");
              } else {
                requireBot(serverId).sendRaw().rawLine("AWAY :" + msg);
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().joinChannel(channel))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return Completable.fromAction(
            () -> {
              String chan = PircbotxUtil.sanitizeChannel(channel);
              String msg = reason == null ? "" : reason.trim();
              if (msg.isEmpty()) {
                requireBot(serverId).sendRaw().rawLine("PART " + chan);
              } else {
                requireBot(serverId).sendRaw().rawLine("PART " + chan + " :" + msg);
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(
            () -> {
              String chan = PircbotxUtil.sanitizeChannel(channel);
              sendMessageWithMultiline(serverId, chan, message, false);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendNoticeToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(
            () -> {
              String chan = PircbotxUtil.sanitizeChannel(channel);
              sendMessageWithMultiline(serverId, chan, message, true);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return Completable.fromAction(
            () -> {
              String target = PircbotxUtil.sanitizeNick(nick);
              sendMessageWithMultiline(serverId, target, message, false);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendNoticePrivate(String serverId, String nick, String message) {
    return Completable.fromAction(
            () -> {
              String target = PircbotxUtil.sanitizeNick(nick);
              sendMessageWithMultiline(serverId, target, message, true);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    return Completable.fromAction(
            () -> {
              String line = rawLine == null ? "" : rawLine.trim();
              if (line.isEmpty()) return;
              requireBot(serverId).sendRaw().rawLine(line);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private void sendMessageWithMultiline(
      String serverId, String target, String message, boolean notice) {
    multilineMessageSupport.send(
        requireBot(serverId), conn(serverId), serverId, target, message, notice);
  }

  static long multilinePayloadUtf8Bytes(List<String> lines) {
    return PircbotxMultilineMessageSupport.multilinePayloadUtf8Bytes(lines);
  }

  static void requireWithinMultilineMaxBytes(long maxBytes, List<String> lines, String serverId) {
    PircbotxMultilineMessageSupport.requireWithinMaxBytes(maxBytes, lines, serverId);
  }

  static void requireWithinMultilineMaxLines(long maxLines, List<String> lines, String serverId) {
    PircbotxMultilineMessageSupport.requireWithinMaxLines(maxLines, lines, serverId);
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return Completable.fromAction(
            () -> {
              capabilityCommandSupport.sendTyping(serverId, conn(serverId), target, state);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return Completable.fromAction(
            () ->
                capabilityCommandSupport.sendReadMarker(serverId, conn(serverId), target, markerAt))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, java.time.Instant beforeExclusive, int limit) {
    java.time.Instant before = beforeExclusive == null ? java.time.Instant.now() : beforeExclusive;
    String selector = Ircv3ChatHistoryCommandBuilder.timestampSelector(before);
    return requestChatHistoryBefore(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    return io.reactivex.rxjava3.core.Completable.fromAction(
            () ->
                capabilityCommandSupport.requestChatHistoryBefore(
                    serverId, conn(serverId), target, selector, limit))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    return io.reactivex.rxjava3.core.Completable.fromAction(
            () ->
                capabilityCommandSupport.requestChatHistoryLatest(
                    serverId, conn(serverId), target, selector, limit))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return io.reactivex.rxjava3.core.Completable.fromAction(
            () ->
                capabilityCommandSupport.requestChatHistoryBetween(
                    serverId, conn(serverId), target, startSelector, endSelector, limit))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return io.reactivex.rxjava3.core.Completable.fromAction(
            () ->
                capabilityCommandSupport.requestChatHistoryAround(
                    serverId, conn(serverId), target, selector, limit))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    try {
      return capabilityCommandSupport.isChatHistoryAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.echoMessageCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftReplyCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftReactCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null
          && c.botRef.get() != null
          && (c.draftUnreactCapAcked.get() || c.draftReactCapAcked.get());
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null
          && c.botRef.get() != null
          && (c.multilineCapAcked.get() || c.draftMultilineCapAcked.get());
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    try {
      return PircbotxMultilineMessageSupport.negotiatedMaxBytes(conn(serverId));
    } catch (Exception e) {
      return 0L;
    }
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    try {
      long max = PircbotxMultilineMessageSupport.negotiatedMaxLines(conn(serverId));
      if (max <= 0L) return 0;
      if (max >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
      return (int) max;
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftMessageEditCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftMessageRedactionCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    try {
      return capabilityCommandSupport.isTypingAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    try {
      return capabilityCommandSupport.typingAvailabilityReason(conn(serverId));
    } catch (Exception e) {
      return "error determining typing availability: " + e.getClass().getSimpleName();
    }
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    try {
      return capabilityCommandSupport.isReadMarkerAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.labeledResponseCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.standardRepliesCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null
          && c.botRef.get() != null
          && (c.monitorSupported.get() || c.monitorCapAcked.get());
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      if (c == null) return 0;
      long limit = Math.max(0L, c.monitorMaxTargets.get());
      if (limit <= 0L) return 0;
      if (limit >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
      return (int) limit;
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public Completable requestLagProbe(String serverId) {
    return Completable.fromAction(
            () -> {
              String sid = Objects.toString(serverId, "").trim();
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("serverId is blank");
              }

              PircbotxConnectionState c = conn(sid);
              PircBotX bot = c.botRef.get();
              if (bot == null) {
                throw new IllegalStateException("Not connected: " + sid);
              }
              if (!c.registrationComplete.get()) {
                throw new IllegalStateException("Registration not complete: " + sid);
              }

              String token =
                  "ircafe-lag-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
              c.beginLagProbe(token, System.currentTimeMillis());
              bot.sendRaw().rawLine("PING :" + token);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public boolean shouldRequestLagProbe(String serverId) {
    return false;
  }

  @Override
  public boolean isLagProbeReady(String serverId) {
    try {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return false;
      PircbotxConnectionState c = conn(sid);
      return c != null && c.botRef.get() != null && c.registrationComplete.get();
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    try {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return OptionalLong.empty();
      PircbotxConnectionState c = conn(sid);
      if (c == null) return OptionalLong.empty();
      long lagMs = c.lagMsIfFresh(System.currentTimeMillis());
      return lagMs >= 0L ? OptionalLong.of(lagMs) : OptionalLong.empty();
    } catch (Exception ignored) {
      return OptionalLong.empty();
    }
  }

  @Override
  public boolean isZncPlaybackAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.zncPlaybackCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isZncBouncerDetected(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && (c.zncDetected.get() || c.zncPlaybackCapAcked.get());
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isSojuBouncerAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.sojuBouncerNetworksCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Completable requestZncPlaybackRange(
      String serverId, String target, Instant fromInclusive, Instant toInclusive) {
    return Completable.fromAction(
            () ->
                zncPlaybackRequestSupport.requestPlaybackRange(
                    serverId, conn(serverId), target, fromInclusive, toInclusive))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendAction(String serverId, String target, String action) {
    return Completable.fromAction(
            () -> actionCommandSupport.sendAction(serverId, requireBot(serverId), target, action))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return Completable.fromAction(
            () -> {
              String chan = PircbotxUtil.sanitizeChannel(channel);
              requireBot(serverId).sendRaw().rawLine("NAMES " + chan);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return Completable.fromAction(
            () -> {
              String n = PircbotxUtil.sanitizeNick(nick);
              conn(serverId)
                  .whoisSawAwayByNickLower
                  .putIfAbsent(n.toLowerCase(Locale.ROOT), Boolean.FALSE);
              conn(serverId)
                  .whoisSawAccountByNickLower
                  .putIfAbsent(n.toLowerCase(Locale.ROOT), Boolean.FALSE);

              requireBot(serverId).sendRaw().rawLine("WHOIS " + n);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whowas(String serverId, String nick, int count) {
    return Completable.fromAction(
            () -> {
              String n = PircbotxUtil.sanitizeNick(nick);
              if (count > 0) {
                requireBot(serverId).sendRaw().rawLine("WHOWAS " + n + " " + count);
              } else {
                requireBot(serverId).sendRaw().rawLine("WHOWAS " + n);
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private PircbotxConnectionState conn(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();

    return connections.computeIfAbsent(id, k -> new PircbotxConnectionState(id));
  }

  private PircBotX requireBot(String serverId) {
    PircBotX bot = conn(serverId).botRef.get();
    if (bot == null) throw new IllegalStateException("Not connected: " + serverId);
    return bot;
  }

  private void cancelReconnect(PircbotxConnectionState c) {
    timers.cancelReconnect(c);
  }

  private void scheduleReconnect(PircbotxConnectionState c, String reason) {
    if (c == null) return;
    if (shuttingDown.get()) return;
    if (c.manualDisconnect.get()) return;
    timers.scheduleReconnect(c, reason, this::connect, bus::onNext);
  }

  @Override
  public void shutdownNow() {
    shuttingDown.set(true);
    String shutdownQuitReason = chatCommandRuntimeConfig.readDefaultQuitMessage();
    for (PircbotxConnectionState c : connections.values()) {
      if (c == null) continue;
      try {
        shutdownSupport.shutdownConnection(c, shutdownQuitReason);
      } catch (Exception e) {
        log.debug("[ircafe] Error during shutdown for {}", c.serverId, e);
      }
    }
  }

  @PreDestroy
  void shutdown() {
    shutdownNow();
  }
}
