package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.processors.FlowableProcessor;
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
  private final PircbotxWhoisResultEmitter whoisResults;
  private final PircbotxUnknownLineFallbackEmitter unknownLineFallback;
  private final PircbotxUnknownEventRouter unknownEventRouter;
  private final PircbotxServerNumericRouter serverNumericRouter;
  private final Ircv3MultilineAccumulator multilineAccumulator = new Ircv3MultilineAccumulator();

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
            selfIdentity::resolveSelfNick);
    this.whoEvents = new PircbotxWhoEventEmitter(serverId, conn, bus::onNext);
    this.isupportObserver =
        new PircbotxIsupportObserver(
            serverId, conn, isupportState, bus::onNext, bouncerDiscovery::observeSojuBouncerNetId);
    this.saslFailures =
        new PircbotxSaslFailureHandler(serverId, conn, bus::onNext, disconnectOnSaslFailure);
    this.registrationLifecycle =
        new PircbotxRegistrationLifecycleHandler(
            serverId, conn, cursorProvider, bouncerDiscovery, serverResponses, bus::onNext);
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
    this.inviteEvents = new PircbotxInviteEventEmitter(serverId, rosterEmitter, bus::onNext);
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
            selfIdentity::resolveSelfNick,
            PircbotxEventAccessors::privmsgTargetFromEvent);
    this.actionEvents =
        new PircbotxActionEventEmitter(
            serverId,
            conn,
            rosterEmitter,
            chatHistoryBatches,
            bus::onNext,
            selfIdentity::resolveSelfNick,
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
            selfIdentity::nickMatchesSelf,
            PircbotxSelfIdentityTracker::isSelfEchoed,
            selfIdentity::resolveSelfNick,
            PircbotxSelfIdentityTracker::resolveBotNick,
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
            selfIdentity::resolveSelfNick);
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
            serverResponses);
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
