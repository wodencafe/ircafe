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
import cafe.woden.ircclient.irc.pircbotx.listener.*;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
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
  private final PircbotxAvailabilitySupport availabilitySupport;
  private final PircbotxLagProbeSupport lagProbeSupport;
  private final PircbotxBasicCommandSupport basicCommandSupport;
  private final PircbotxQueryCommandSupport queryCommandSupport;
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
    this.availabilitySupport = new PircbotxAvailabilitySupport();
    this.lagProbeSupport = new PircbotxLagProbeSupport();
    this.basicCommandSupport = new PircbotxBasicCommandSupport();
    this.queryCommandSupport = new PircbotxQueryCommandSupport();
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
        if (c != null && c.hasBot()) {
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
    PircBotX bot = conn(serverId).currentBot();
    return bot == null ? Optional.empty() : Optional.ofNullable(bot.getNick());
  }

  @Override
  public Completable connect(String serverId) {
    return Completable.fromAction(
            () -> {
              if (shuttingDown.get()) return;
              PircbotxConnectionState c = conn(serverId);
              if (c.hasBot()) return;
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
            () -> basicCommandSupport.changeNick(requireBot(serverId), newNick))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return Completable.fromAction(
            () -> basicCommandSupport.setAway(requireBot(serverId), awayMessage))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return Completable.fromAction(
            () -> basicCommandSupport.joinChannel(requireBot(serverId), channel))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return Completable.fromAction(
            () -> basicCommandSupport.partChannel(requireBot(serverId), channel, reason))
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
    return Completable.fromAction(() -> basicCommandSupport.sendRaw(requireBot(serverId), rawLine))
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
      return availabilitySupport.isEchoMessageAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    try {
      return availabilitySupport.isDraftReplyAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    try {
      return availabilitySupport.isDraftReactAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    try {
      return availabilitySupport.isDraftUnreactAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    try {
      return availabilitySupport.isMultilineAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    try {
      return availabilitySupport.negotiatedMultilineMaxBytes(conn(serverId));
    } catch (Exception e) {
      return 0L;
    }
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    try {
      return availabilitySupport.negotiatedMultilineMaxLines(conn(serverId));
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    try {
      return availabilitySupport.isMessageEditAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    try {
      return availabilitySupport.isMessageRedactionAvailable(conn(serverId));
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
      return availabilitySupport.isLabeledResponseAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    try {
      return availabilitySupport.isStandardRepliesAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    try {
      return availabilitySupport.isMonitorAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    try {
      return availabilitySupport.negotiatedMonitorLimit(conn(serverId));
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
              lagProbeSupport.requestLagProbe(sid, conn(sid));
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
      return lagProbeSupport.isLagProbeReady(conn(sid));
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    try {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return OptionalLong.empty();
      return lagProbeSupport.lastMeasuredLagMs(conn(sid), System.currentTimeMillis());
    } catch (Exception ignored) {
      return OptionalLong.empty();
    }
  }

  @Override
  public boolean isZncPlaybackAvailable(String serverId) {
    try {
      return availabilitySupport.isZncPlaybackAvailable(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isZncBouncerDetected(String serverId) {
    try {
      return availabilitySupport.isZncBouncerDetected(conn(serverId));
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isSojuBouncerAvailable(String serverId) {
    try {
      return availabilitySupport.isSojuBouncerAvailable(conn(serverId));
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
            () -> queryCommandSupport.requestNames(requireBot(serverId), channel))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return Completable.fromAction(
            () -> queryCommandSupport.whois(conn(serverId), requireBot(serverId), nick))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whowas(String serverId, String nick, int count) {
    return Completable.fromAction(
            () -> queryCommandSupport.whowas(requireBot(serverId), nick, count))
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private PircbotxConnectionState conn(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();

    return connections.computeIfAbsent(id, k -> new PircbotxConnectionState(id));
  }

  private PircBotX requireBot(String serverId) {
    PircBotX bot = conn(serverId).currentBot();
    if (bot == null) throw new IllegalStateException("Not connected: " + serverId);
    return bot;
  }

  private void cancelReconnect(PircbotxConnectionState c) {
    timers.cancelReconnect(c);
  }

  private void scheduleReconnect(PircbotxConnectionState c, String reason) {
    if (c == null) return;
    if (shuttingDown.get()) return;
    if (c.manualDisconnectRequested()) return;
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
