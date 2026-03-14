package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericCTCPEvent;

/** Translates PircBotX events into ServerIrcEvent. */
final class PircbotxBridgeListener extends ListenerAdapter {
  @FunctionalInterface
  interface CtcpRequestHandler {
    boolean handle(PircBotX bot, String fromNick, String message);
  }

  private final String serverId;
  private final PircbotxConnectionState conn;
  private final FlowableProcessor<ServerIrcEvent> bus;
  private final PlaybackCursorProvider playbackCursorProvider;
  private final ServerIsupportStatePort serverIsupportState;
  private final Consumer<PircbotxConnectionState> heartbeatStopper;
  private final BiConsumer<PircbotxConnectionState, String> reconnectScheduler;

  private final PircbotxBouncerDiscoveryCoordinator bouncerDiscovery;
  private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  private final PircbotxMonitorEventEmitter monitorEvents;
  private final PircbotxServerResponseEmitter serverResponses;
  private final PircbotxUnknownCtcpEmitter unknownCtcp;
  private final PircbotxWhoEventEmitter whoEvents;
  private final PircbotxIsupportObserver isupportObserver;
  private final PircbotxSaslFailureHandler saslFailures;
  private final PircbotxRegistrationLifecycleHandler registrationLifecycle;
  private final PircbotxRosterEmitter rosterEmitter;
  private final PircbotxMembershipEventEmitter membershipEvents;
  private final PircbotxChannelModeEventEmitter channelModeEvents;
  private final PircbotxChannelMessageEmitter channelMessageEvents;
  private final PircbotxPrivateMessageEmitter privateMessageEvents;
  private final PircbotxActionEventEmitter actionEvents;
  private final PircbotxNoticeEventEmitter noticeEvents;
  private final PircbotxInboundCtcpHandler inboundCtcpHandler;
  private final PircbotxWhoisResultEmitter whoisResults;
  private final PircbotxUnknownLineFallbackEmitter unknownLineFallback;
  private final PircbotxUnknownEventRouter unknownEventRouter;
  private final PircbotxServerNumericRouter serverNumericRouter;
  private final Ircv3MultilineAccumulator multilineAccumulator = new Ircv3MultilineAccumulator();

  /** Best-effort resolve of our current nick (prefers UserBot nick when available). */
  private static String resolveBotNick(PircBotX bot) {
    if (bot == null) return "";

    // Prefer UserBot nick, since it tends to reflect the *current* nick (including alt-nick
    // fallback).
    try {
      if (bot.getUserBot() != null) {
        String ub = bot.getUserBot().getNick();
        if (ub != null && !ub.isBlank()) return ub.trim();
      }
    } catch (Exception ignored) {
    }

    try {
      String n = PircbotxUtil.safeStr(bot::getNick, "");
      if (n != null && !n.isBlank()) return n.trim();
    } catch (Exception ignored) {
    }

    // Last resort: poke configuration via reflection (PircBotX versions differ).
    try {
      Object cfg = PircbotxEventAccessors.reflectCall(bot, "getConfiguration");
      Object n = PircbotxEventAccessors.reflectCall(cfg, "getNick");
      if (n != null) {
        String s = String.valueOf(n);
        if (!s.isBlank()) return s.trim();
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  /** True when an inbound event is actually our own message echoed back (IRCv3 echo-message). */
  private static boolean isSelfEchoed(PircBotX bot, String fromNick) {
    try {
      if (fromNick == null || fromNick.isBlank()) return false;

      String botNick = resolveBotNick(bot);
      if (!botNick.isBlank() && fromNick.equalsIgnoreCase(botNick)) return true;

      // Sometimes UserBot nick is available even when botNick isn't.
      try {
        if (bot != null && bot.getUserBot() != null) {
          String ub = bot.getUserBot().getNick();
          if (ub != null && !ub.isBlank() && fromNick.equalsIgnoreCase(ub)) return true;
        }
      } catch (Exception ignored) {
      }

      return false;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean looksLikeSelfNickHint(String nick) {
    if (nick == null) return false;
    String n = nick.trim();
    if (n.isBlank()) return false;
    if ("*".equals(n)) return false;
    if (PircbotxLineParseUtil.looksNumeric(n)) return false;
    if (PircbotxLineParseUtil.looksLikeChannel(n)) return false;
    if (n.indexOf(' ') >= 0) return false;
    return true;
  }

  private void rememberSelfNickHint(String nick) {
    if (!looksLikeSelfNickHint(nick)) return;
    String n = nick.trim();
    conn.selfNickHint.set(n);
  }

  private boolean nickMatchesSelf(PircBotX bot, String nick) {
    if (nick == null || nick.isBlank()) return false;
    String n = nick.trim();

    String hinted = conn.selfNickHint.get();
    if (hinted != null && !hinted.isBlank() && n.equalsIgnoreCase(hinted.trim())) {
      return true;
    }

    String fromBot = resolveBotNick(bot);
    if (!fromBot.isBlank() && n.equalsIgnoreCase(fromBot.trim())) {
      rememberSelfNickHint(fromBot);
      return true;
    }

    return false;
  }

  private String resolveSelfNick(PircBotX bot) {
    String hinted = conn.selfNickHint.get();
    if (looksLikeSelfNickHint(hinted)) {
      return hinted.trim();
    }
    String fromBot = resolveBotNick(bot);
    if (!fromBot.isBlank()) {
      rememberSelfNickHint(fromBot);
      return fromBot;
    }
    return "";
  }

  PircbotxBridgeListener(
      String serverId,
      PircbotxConnectionState conn,
      FlowableProcessor<ServerIrcEvent> bus,
      Consumer<PircbotxConnectionState> heartbeatStopper,
      BiConsumer<PircbotxConnectionState, String> reconnectScheduler,
      CtcpRequestHandler ctcpHandler,
      boolean disconnectOnSaslFailure,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled,
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents,
      PlaybackCursorProvider playbackCursorProvider,
      ServerIsupportStatePort serverIsupportState) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.bus = Objects.requireNonNull(bus, "bus");
    this.heartbeatStopper = Objects.requireNonNull(heartbeatStopper, "heartbeatStopper");
    this.reconnectScheduler = Objects.requireNonNull(reconnectScheduler, "reconnectScheduler");
    Objects.requireNonNull(ctcpHandler, "ctcpHandler");

    this.bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            serverId,
            conn,
            sojuDiscoveryEnabled,
            zncDiscoveryEnabled,
            bouncerBackends,
            bouncerDiscoveryEvents);
    this.chatHistoryBatches = new PircbotxChatHistoryBatchCollector(serverId, bus::onNext);
    this.monitorEvents = new PircbotxMonitorEventEmitter(serverId, bus::onNext);
    this.serverResponses = new PircbotxServerResponseEmitter(serverId, bus::onNext);
    this.unknownCtcp =
        new PircbotxUnknownCtcpEmitter(
            serverId,
            bus::onNext,
            this::nickMatchesSelf,
            PircbotxBridgeListener::isSelfEchoed,
            this::resolveSelfNick);
    this.whoEvents = new PircbotxWhoEventEmitter(serverId, conn, bus::onNext);
    this.playbackCursorProvider =
        java.util.Objects.requireNonNull(playbackCursorProvider, "playbackCursorProvider");
    this.serverIsupportState =
        java.util.Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    this.isupportObserver =
        new PircbotxIsupportObserver(
            serverId,
            conn,
            this.serverIsupportState,
            bus::onNext,
            bouncerDiscovery::observeSojuBouncerNetId);
    this.saslFailures =
        new PircbotxSaslFailureHandler(serverId, conn, bus::onNext, disconnectOnSaslFailure);
    this.registrationLifecycle =
        new PircbotxRegistrationLifecycleHandler(
            serverId,
            conn,
            this.playbackCursorProvider,
            bouncerDiscovery,
            serverResponses,
            bus::onNext);
    this.rosterEmitter =
        new PircbotxRosterEmitter(serverId, conn, this.serverIsupportState, bus::onNext);
    this.membershipEvents =
        new PircbotxMembershipEventEmitter(
            serverId,
            conn,
            rosterEmitter,
            bus::onNext,
            this::nickMatchesSelf,
            this::rememberSelfNickHint,
            PircbotxBridgeListener::resolveBotNick);
    this.channelModeEvents =
        new PircbotxChannelModeEventEmitter(
            serverId,
            rosterEmitter,
            bus::onNext,
            PircbotxEventAccessors::nickFromEvent,
            PircbotxEventAccessors::modeDetailsFromEvent);
    this.channelMessageEvents =
        new PircbotxChannelMessageEmitter(
            serverId, conn, rosterEmitter, chatHistoryBatches, multilineAccumulator, bus::onNext);
    this.privateMessageEvents =
        new PircbotxPrivateMessageEmitter(
            serverId,
            conn,
            rosterEmitter,
            bouncerDiscovery,
            chatHistoryBatches,
            multilineAccumulator,
            bus::onNext,
            this::resolveSelfNick,
            PircbotxEventAccessors::privmsgTargetFromEvent);
    this.actionEvents =
        new PircbotxActionEventEmitter(
            serverId,
            conn,
            rosterEmitter,
            chatHistoryBatches,
            bus::onNext,
            this::resolveSelfNick,
            PircbotxEventAccessors::privmsgTargetFromEvent);
    this.noticeEvents =
        new PircbotxNoticeEventEmitter(
            serverId,
            conn,
            rosterEmitter,
            bouncerDiscovery,
            chatHistoryBatches,
            multilineAccumulator,
            serverResponses,
            bus::onNext,
            PircbotxEventAccessors::senderNickFromEvent);
    this.inboundCtcpHandler =
        new PircbotxInboundCtcpHandler(
            serverId,
            conn.selfNickHint::get,
            this::nickMatchesSelf,
            PircbotxBridgeListener::isSelfEchoed,
            this::resolveSelfNick,
            PircbotxBridgeListener::resolveBotNick,
            PircbotxEventAccessors::rawLineFromEvent,
            PircbotxEventAccessors::privmsgTargetFromEvent,
            rosterEmitter::maybeEmitHostmaskObserved,
            bus::onNext,
            ctcpHandler);
    this.whoisResults = new PircbotxWhoisResultEmitter(serverId, bus::onNext);
    this.unknownLineFallback =
        new PircbotxUnknownLineFallbackEmitter(
            serverId,
            conn,
            bouncerDiscovery,
            chatHistoryBatches,
            serverResponses,
            saslFailures,
            isupportObserver,
            whoEvents,
            bus::onNext,
            this::resolveSelfNick);
    this.unknownEventRouter =
        new PircbotxUnknownEventRouter(
            serverId,
            this::rememberSelfNickHint,
            PircbotxBridgeListener::resolveBotNick,
            serverResponses,
            monitorEvents,
            chatHistoryBatches,
            unknownCtcp,
            unknownLineFallback,
            bus::onNext);
    this.serverNumericRouter =
        new PircbotxServerNumericRouter(
            serverId,
            this::rememberSelfNickHint,
            bus::onNext,
            saslFailures,
            monitorEvents,
            isupportObserver,
            registrationLifecycle,
            whoEvents,
            serverResponses);
  }

  @Override
  public void onConnect(ConnectEvent event) {
    touchInbound();
    PircBotX bot = event.getBot();
    conn.reconnectAttempts.set(0);
    conn.manualDisconnect.set(false);
    serverResponses.clear();

    bus.onNext(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.Connected(
                Instant.now(), bot.getServerHostname(), bot.getServerPort(), bot.getNick())));
  }

  @Override
  public void onDisconnect(DisconnectEvent event) {
    serverResponses.clear();
    String override = conn.disconnectReasonOverride.getAndSet(null);
    Exception ex = event.getDisconnectException();
    String reason =
        (override != null && !override.isBlank())
            ? override
            : (ex != null && ex.getMessage() != null) ? ex.getMessage() : "Disconnected";
    if (conn.botRef.compareAndSet(event.getBot(), null)) {
      heartbeatStopper.accept(conn);
    }
    bouncerDiscovery.onDisconnect();
    multilineAccumulator.clear();
    chatHistoryBatches.clear();

    bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Disconnected(Instant.now(), reason)));
    boolean suppressReconnect = conn.suppressAutoReconnectOnce.getAndSet(false);
    if (!conn.manualDisconnect.get() && !suppressReconnect) {
      reconnectScheduler.accept(conn, reason);
    }
  }

  @Override
  public void onMessage(MessageEvent event) {
    touchInbound();
    channelMessageEvents.onMessage(event);
  }

  @Override
  public void onAction(ActionEvent event) {
    touchInbound();
    actionEvents.onAction(event);
  }

  @Override
  public void onTopic(TopicEvent event) {
    touchInbound();
    if (event == null || event.getChannel() == null) return;
    String channel = event.getChannel().getName();
    String topic = event.getTopic();
    if (channel == null || channel.isBlank()) return;
    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.ChannelTopicUpdated(Instant.now(), channel, topic)));
  }

  @Override
  public void onInvite(InviteEvent event) {
    touchInbound();
    if (event == null) return;

    String channel = "";
    try {
      Object c = PircbotxEventAccessors.reflectCall(event, "getChannel");
      if (c != null) channel = String.valueOf(c);
    } catch (Exception ignored) {
    }
    if (channel == null || channel.isBlank()) {
      try {
        Object c = PircbotxEventAccessors.reflectCall(event, "getChannelName");
        if (c != null) channel = String.valueOf(c);
      } catch (Exception ignored) {
      }
    }
    channel = channel == null ? "" : channel.trim();
    if (channel.isBlank()) return;

    String from = "server";
    String invitee = "";
    String reason = "";
    try {
      if (event.getUser() != null) {
        rosterEmitter.maybeEmitHostmaskObserved(channel, event.getUser());
        String nick = event.getUser().getNick();
        if (nick != null && !nick.isBlank()) from = nick.trim();
      }
    } catch (Exception ignored) {
    }

    try {
      String raw = PircbotxEventAccessors.rawLineFromEvent(event);
      ParsedIrcLine parsed =
          PircbotxInboundLineParsers.parseIrcLine(
              PircbotxLineParseUtil.normalizeIrcLineForParsing(raw));
      ParsedInviteLine pi = PircbotxInboundLineParsers.parseInviteLine(parsed);
      if (pi != null) {
        if (from.isBlank()) from = Objects.toString(pi.fromNick(), "").trim();
        if (!Objects.toString(pi.channel(), "").isBlank()) channel = pi.channel().trim();
        invitee = Objects.toString(pi.inviteeNick(), "").trim();
        reason = Objects.toString(pi.reason(), "").trim();
      }
    } catch (Exception ignored) {
    }

    bus.onNext(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.InvitedToChannel(Instant.now(), channel, from, invitee, reason, false)));
  }

  @Override
  public void onPrivateMessage(PrivateMessageEvent event) {
    touchInbound();
    privateMessageEvents.onPrivateMessage(event);
  }

  @Override
  public void onNotice(NoticeEvent event) {
    touchInbound();
    noticeEvents.onNotice(event);
  }

  @Override
  public void onGenericCTCP(GenericCTCPEvent event) throws Exception {
    touchInbound();
    inboundCtcpHandler.onGenericCtcp(event);
  }

  @Override
  public void onFinger(FingerEvent event) throws Exception {
    touchInbound();
    inboundCtcpHandler.onFinger(event);
  }

  @Override
  public void onWhois(WhoisEvent event) {
    touchInbound();
    whoisResults.onWhois(event);
  }

  @Override
  public void onUserList(UserListEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  @Override
  public void onUnknown(UnknownEvent event) {
    touchInbound();
    unknownEventRouter.handle(event);
  }

  @Override
  public void onServerResponse(ServerResponseEvent event) {
    touchInbound();
    serverNumericRouter.onServerResponse(event);
  }

  @Override
  public void onJoin(JoinEvent event) {
    touchInbound();
    membershipEvents.onJoin(event);
  }

  @Override
  public void onPart(PartEvent event) {
    touchInbound();
    membershipEvents.onPart(event);
  }

  @Override
  public void onQuit(QuitEvent event) {
    touchInbound();
    membershipEvents.onQuit(event);
  }

  private void touchInbound() {
    conn.lastInboundMs.set(System.currentTimeMillis());
    conn.localTimeoutEmitted.set(false);
  }

  @Override
  public void onServerPing(ServerPingEvent event) {
    touchInbound();
  }

  @Override
  public void onKick(KickEvent event) {
    touchInbound();
    membershipEvents.onKick(event);
  }

  @Override
  public void onNickChange(NickChangeEvent event) {
    touchInbound();
    membershipEvents.onNickChange(event);
  }

  @Override
  public void onMode(ModeEvent event) {
    touchInbound();
    channelModeEvents.onMode(event);
  }

  @Override
  public void onOp(OpEvent event) {
    channelModeEvents.onOp(event);
  }

  @Override
  public void onVoice(VoiceEvent event) {
    channelModeEvents.onVoice(event);
  }

  @Override
  public void onHalfOp(HalfOpEvent event) {
    channelModeEvents.onHalfOp(event);
  }

  @Override
  public void onOwner(OwnerEvent event) {
    channelModeEvents.onOwner(event);
  }

  @Override
  public void onSuperOp(SuperOpEvent event) {
    channelModeEvents.onSuperOp(event);
  }
}
