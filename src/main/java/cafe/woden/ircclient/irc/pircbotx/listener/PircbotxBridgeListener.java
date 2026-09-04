package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxActionEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChannelMessageEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChannelModeEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxDccRequestEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxInviteEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxMembershipEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxMonitorEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxNoticeEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxPrivateMessageEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxRosterEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxTopicEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxUnknownCtcpEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxWhoisResultEmitter;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxPresenceSignalSupport;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventAccessors;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericCTCPEvent;

/** Translates PircBotX events into ServerIrcEvent. */
final class PircbotxBridgeListener extends ListenerAdapter {
  private final PircbotxSelfIdentityTracker selfIdentity;

  private final PircbotxConnectionSessionHandler session;
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
  private final PircbotxInviteEventEmitter inviteEvents;
  private final PircbotxTopicEventEmitter topicEvents;
  private final PircbotxChannelModeEventEmitter channelModeEvents;
  private final PircbotxChannelMessageEmitter channelMessageEvents;
  private final PircbotxPrivateMessageEmitter privateMessageEvents;
  private final PircbotxActionEventEmitter actionEvents;
  private final PircbotxNoticeEventEmitter noticeEvents;
  private final PircbotxInboundCtcpHandler inboundCtcpHandler;
  private final PircbotxDccRequestEmitter dccRequests;
  private final PircbotxWhoisResultEmitter whoisResults;
  private final PircbotxUnknownLineFallbackHandler unknownLineFallback;
  private final PircbotxUnknownEventRouter unknownEventRouter;
  private final PircbotxServerNumericRouter serverNumericRouter;
  private final Ircv3MultilineAccumulator multilineAccumulator = new Ircv3MultilineAccumulator();

  PircbotxBridgeListener(
      String serverId,
      PircbotxConnectionState conn,
      FlowableProcessor<ServerIrcEvent> bus,
      Consumer<PircbotxConnectionState> heartbeatStopper,
      BiConsumer<PircbotxConnectionState, String> reconnectScheduler,
      PircbotxCtcpRequestHandler ctcpHandler,
      boolean disconnectOnSaslFailure,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled,
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents,
      PlaybackCursorProvider playbackCursorProvider,
      ServerIsupportStatePort serverIsupportState,
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog,
      Ircv3InboundTagSignalRuntimeCatalog inboundTagRuntimeCatalog,
      Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport,
      Ircv3HistoryTransportRuntimeSupport historyTransportRuntimeSupport,
      Ircv3IsupportRuntimeSupport isupportRuntimeSupport,
      Ircv3TypingRuntimeSupport typingRuntimeSupport,
      Ircv3SaslRuntimeSupport saslRuntimeSupport) {
    Objects.requireNonNull(serverId, "serverId");
    Objects.requireNonNull(conn, "conn");
    Objects.requireNonNull(bus, "bus");
    Objects.requireNonNull(heartbeatStopper, "heartbeatStopper");
    Objects.requireNonNull(reconnectScheduler, "reconnectScheduler");
    Objects.requireNonNull(ctcpHandler, "ctcpHandler");
    this.selfIdentity = new PircbotxSelfIdentityTracker(conn);

    PlaybackCursorProvider cursorProvider =
        Objects.requireNonNull(playbackCursorProvider, "playbackCursorProvider");
    ServerIsupportStatePort isupportState =
        Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    Ircv3HistoryTransportRuntimeSupport historyTransport =
        Objects.requireNonNull(historyTransportRuntimeSupport, "historyTransportRuntimeSupport");
    Ircv3IsupportRuntimeSupport isupport =
        Objects.requireNonNull(isupportRuntimeSupport, "isupportRuntimeSupport");
    Ircv3TypingRuntimeSupport typing =
        Objects.requireNonNull(typingRuntimeSupport, "typingRuntimeSupport");
    Ircv3SaslRuntimeSupport sasl = Objects.requireNonNull(saslRuntimeSupport, "saslRuntimeSupport");

    this.bouncerDiscovery =
        new PircbotxBouncerDiscoveryCoordinator(
            serverId,
            conn,
            sojuDiscoveryEnabled,
            zncDiscoveryEnabled,
            bouncerBackends,
            bouncerDiscoveryEvents);
    this.chatHistoryBatches =
        new PircbotxChatHistoryBatchCollector(
            serverId,
            bus::onNext,
            inboundCommandRuntimeCatalog,
            inboundTagRuntimeCatalog,
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport);
    this.monitorEvents =
        new PircbotxMonitorEventEmitter(
            serverId, bus::onNext, inboundCommandRuntimeCatalog, serverTimeRuntimeSupport);
    this.serverResponses =
        new PircbotxServerResponseEmitter(
            serverId, bus::onNext, serverTimeRuntimeSupport, messageTagsRuntimeSupport);
    this.session =
        new PircbotxConnectionSessionHandler(
            serverId,
            conn,
            heartbeatStopper,
            reconnectScheduler,
            bouncerDiscovery,
            chatHistoryBatches,
            serverResponses,
            multilineAccumulator,
            bus::onNext);
    this.unknownCtcp =
        new PircbotxUnknownCtcpEmitter(
            serverId,
            bus::onNext,
            selfIdentity::nickMatchesSelf,
            PircbotxSelfIdentityTracker::isSelfEchoed,
            selfIdentity::resolveSelfNick,
            serverTimeRuntimeSupport);
    this.whoEvents =
        new PircbotxWhoEventEmitter(serverId, conn, bus::onNext, inboundCommandRuntimeCatalog);
    PircbotxPresenceSignalSupport presenceSignals =
        new PircbotxPresenceSignalSupport(serverId, bus::onNext, inboundCommandRuntimeCatalog);
    this.isupportObserver =
        new PircbotxIsupportObserver(
            serverId,
            conn,
            isupportState,
            bus::onNext,
            bouncerDiscovery::observeSojuBouncerNetId,
            isupport,
            typing);
    this.saslFailures =
        new PircbotxSaslFailureHandler(serverId, conn, bus::onNext, disconnectOnSaslFailure, sasl);
    this.registrationLifecycle =
        new PircbotxRegistrationLifecycleHandler(
            serverId,
            conn,
            cursorProvider,
            bouncerDiscovery,
            serverResponses,
            bus::onNext,
            outboundCommandRuntimeCatalog,
            historyTransport);
    this.rosterEmitter = new PircbotxRosterEmitter(serverId, conn, isupportState, bus::onNext);
    this.membershipEvents =
        new PircbotxMembershipEventEmitter(
            serverId,
            conn,
            rosterEmitter,
            bus::onNext,
            selfIdentity::nickMatchesSelf,
            selfIdentity::rememberSelfNickHint,
            PircbotxSelfIdentityTracker::resolveBotNick);
    this.inviteEvents =
        new PircbotxInviteEventEmitter(
            serverId, rosterEmitter, inboundCommandRuntimeCatalog, bus::onNext);
    this.topicEvents = new PircbotxTopicEventEmitter(serverId, bus::onNext);
    this.channelModeEvents =
        new PircbotxChannelModeEventEmitter(
            serverId,
            rosterEmitter,
            bus::onNext,
            PircbotxEventAccessors::nickFromEvent,
            PircbotxEventAccessors::modeDetailsFromEvent);
    this.channelMessageEvents =
        new PircbotxChannelMessageEmitter(
            serverId,
            conn,
            rosterEmitter,
            chatHistoryBatches,
            multilineAccumulator,
            bus::onNext,
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport);
    this.privateMessageEvents =
        new PircbotxPrivateMessageEmitter(
            serverId,
            conn,
            rosterEmitter,
            bouncerDiscovery,
            chatHistoryBatches,
            multilineAccumulator,
            bus::onNext,
            selfIdentity::resolveSelfNick,
            PircbotxEventAccessors::privmsgTargetFromEvent,
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport,
            historyTransport);
    this.actionEvents =
        new PircbotxActionEventEmitter(
            serverId,
            conn,
            rosterEmitter,
            chatHistoryBatches,
            bus::onNext,
            selfIdentity::resolveSelfNick,
            PircbotxEventAccessors::privmsgTargetFromEvent,
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport,
            historyTransport);
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
            PircbotxEventAccessors::senderNickFromEvent,
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport);
    this.inboundCtcpHandler =
        new PircbotxInboundCtcpHandler(
            serverId,
            selfIdentity::nickMatchesSelf,
            PircbotxSelfIdentityTracker::isSelfEchoed,
            selfIdentity::resolveSelfNick,
            PircbotxEventAccessors::rawLineFromEvent,
            PircbotxEventAccessors::privmsgTargetFromEvent,
            rosterEmitter::maybeEmitHostmaskObserved,
            bus::onNext,
            ctcpHandler,
            serverTimeRuntimeSupport);
    this.dccRequests =
        new PircbotxDccRequestEmitter(serverId, bus::onNext, serverTimeRuntimeSupport);
    this.whoisResults = new PircbotxWhoisResultEmitter(serverId, bus::onNext);
    this.unknownLineFallback =
        new PircbotxUnknownLineFallbackHandler(
            serverId,
            conn,
            bouncerDiscovery,
            chatHistoryBatches,
            serverResponses,
            saslFailures,
            isupportObserver,
            whoEvents,
            bus::onNext,
            selfIdentity::resolveSelfNick,
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport,
            presenceSignals,
            historyTransport);
    this.unknownEventRouter =
        new PircbotxUnknownEventRouter(
            serverId,
            selfIdentity::rememberSelfNickHint,
            PircbotxSelfIdentityTracker::resolveBotNick,
            serverResponses,
            monitorEvents,
            chatHistoryBatches,
            unknownCtcp,
            unknownLineFallback,
            serverTimeRuntimeSupport,
            inboundCommandRuntimeCatalog,
            bus::onNext);
    this.serverNumericRouter =
        new PircbotxServerNumericRouter(
            serverId,
            selfIdentity::rememberSelfNickHint,
            bus::onNext,
            saslFailures,
            monitorEvents,
            isupportObserver,
            registrationLifecycle,
            whoEvents,
            serverResponses,
            presenceSignals);
  }

  @Override
  public void onConnect(ConnectEvent event) {
    session.onConnect(event);
  }

  @Override
  public void onDisconnect(DisconnectEvent event) {
    session.onDisconnect(event);
  }

  @Override
  public void onMessage(MessageEvent event) {
    session.recordInboundActivity();
    channelMessageEvents.onMessage(event);
  }

  @Override
  public void onAction(ActionEvent event) {
    session.recordInboundActivity();
    actionEvents.onAction(event);
  }

  @Override
  public void onTopic(TopicEvent event) {
    session.recordInboundActivity();
    topicEvents.onTopic(event);
  }

  @Override
  public void onInvite(InviteEvent event) {
    session.recordInboundActivity();
    inviteEvents.onInvite(event);
  }

  @Override
  public void onPrivateMessage(PrivateMessageEvent event) {
    session.recordInboundActivity();
    privateMessageEvents.onPrivateMessage(event);
  }

  @Override
  public void onNotice(NoticeEvent event) {
    session.recordInboundActivity();
    noticeEvents.onNotice(event);
  }

  @Override
  public void onGenericCTCP(GenericCTCPEvent event) throws Exception {
    session.recordInboundActivity();
    inboundCtcpHandler.onGenericCtcp(event);
  }

  @Override
  public void onIncomingChatRequest(IncomingChatRequestEvent event) {
    session.recordInboundActivity();
    dccRequests.onIncomingChatRequest(event);
  }

  @Override
  public void onIncomingFileTransfer(IncomingFileTransferEvent event) {
    session.recordInboundActivity();
    dccRequests.onIncomingFileTransfer(event);
  }

  @Override
  public void onFinger(FingerEvent event) throws Exception {
    session.recordInboundActivity();
    inboundCtcpHandler.onFinger(event);
  }

  @Override
  public void onWhois(WhoisEvent event) {
    session.recordInboundActivity();
    whoisResults.onWhois(event);
  }

  @Override
  public void onUserList(UserListEvent event) {
    rosterEmitter.emitRoster(event.getChannel());
  }

  @Override
  public void onUnknown(UnknownEvent event) {
    session.recordInboundActivity();
    unknownEventRouter.handle(event);
  }

  @Override
  public void onServerResponse(ServerResponseEvent event) {
    session.recordInboundActivity();
    serverNumericRouter.onServerResponse(event);
  }

  @Override
  public void onJoin(JoinEvent event) {
    session.recordInboundActivity();
    membershipEvents.onJoin(event);
  }

  @Override
  public void onPart(PartEvent event) {
    session.recordInboundActivity();
    membershipEvents.onPart(event);
  }

  @Override
  public void onQuit(QuitEvent event) {
    session.recordInboundActivity();
    membershipEvents.onQuit(event);
  }

  @Override
  public void onServerPing(ServerPingEvent event) {
    session.recordInboundActivity();
  }

  @Override
  public void onKick(KickEvent event) {
    session.recordInboundActivity();
    membershipEvents.onKick(event);
  }

  @Override
  public void onNickChange(NickChangeEvent event) {
    session.recordInboundActivity();
    membershipEvents.onNickChange(event);
  }

  @Override
  public void onMode(ModeEvent event) {
    session.recordInboundActivity();
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
