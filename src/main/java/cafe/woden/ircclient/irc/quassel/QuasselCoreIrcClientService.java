package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreSessionEventPublisher.normalizeDisconnectReason;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreSessionEventPublisher.renderProbeSource;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_MESSAGE_EDIT;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_MESSAGE_REDACTION;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_MULTILINE;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.LABELED_RESPONSE;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.MESSAGE_TAGS;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.MULTILINE;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.STANDARD_REPLIES;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Quassel Core backend transport.
 *
 * <p>Current scope: transport connect/probe/auth, outbound input bridging, and baseline inbound
 * SignalProxy message translation.
 */
@Service
@InfrastructureLayer
public class QuasselCoreIrcClientService implements IrcBackendRuntimeClientService {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private static final String BUFFER_SYNCER_CLASS = "BufferSyncer";
  private static final String BUFFER_SYNCER_OBJECT = "";
  private static final String PHASE_PROTOCOL_NEGOTIATED = "protocol-negotiated";
  private static final String PHASE_SETUP_REQUIRED = "setup-required";

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();
  private final QuasselCoreSessionEventPublisher sessionEvents =
      new QuasselCoreSessionEventPublisher(bus::onNext);
  private final QuasselCoreObservationMediator observations = new QuasselCoreObservationMediator();
  private final Map<String, QuasselCoreSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> availabilityReasonByServer = new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  private final ServerCatalog serverCatalog;
  private final QuasselCoreSocketConnector socketConnector;
  private final QuasselCoreHandshakeCoordinator handshakes;
  private final QuasselCoreSetupCoordinator setups;
  private final QuasselCoreAuthHandshake authHandshake;
  private final QuasselCoreReadLoop readLoop;
  private final QuasselCoreSignalProxySender signalProxySender;
  private final QuasselCoreReconnectCoordinator reconnects;
  private final QuasselIrcv3RuntimeSupport ircv3RuntimeSupport;
  private final QuasselCoreTargetResolver targets;
  private final QuasselCoreInputCoordinator inputs;
  private final QuasselCoreOutboundSignalCoordinator outboundSignals;
  private final QuasselCoreHistoryRequestCoordinator historyRequests;
  private final QuasselCoreLagProbeCoordinator lagProbes;
  private final QuasselCoreSessionQueries sessionQueries;
  private final QuasselCoreSessionOperations sessionOperations;
  private final QuasselCoreStateObservationCoordinator stateObservations;
  private final QuasselCoreNetworkManagementCoordinator networkManagement;

  @Autowired
  public QuasselCoreIrcClientService(
      ServerCatalog serverCatalog,
      QuasselCoreSocketConnector socketConnector,
      QuasselCoreProtocolProbe protocolProbe,
      QuasselCoreAuthHandshake authHandshake,
      QuasselCoreDatastreamCodec datastreamCodec,
      QuasselIrcv3RuntimeSupport ircv3RuntimeSupport) {
    this(
        serverCatalog,
        socketConnector,
        protocolProbe,
        authHandshake,
        datastreamCodec,
        null,
        ircv3RuntimeSupport);
  }

  public QuasselCoreIrcClientService(
      ServerCatalog serverCatalog,
      QuasselCoreSocketConnector socketConnector,
      QuasselCoreProtocolProbe protocolProbe,
      QuasselCoreAuthHandshake authHandshake,
      QuasselCoreDatastreamCodec datastreamCodec,
      IrcProperties props,
      QuasselIrcv3RuntimeSupport ircv3RuntimeSupport) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    this.socketConnector = Objects.requireNonNull(socketConnector, "socketConnector");
    this.handshakes =
        new QuasselCoreHandshakeCoordinator(this.socketConnector, protocolProbe, authHandshake);
    this.authHandshake = Objects.requireNonNull(authHandshake, "authHandshake");
    this.readLoop =
        new QuasselCoreReadLoop(Objects.requireNonNull(datastreamCodec, "datastreamCodec"));
    this.signalProxySender = new QuasselCoreDatastreamSender(datastreamCodec);
    this.ircv3RuntimeSupport = Objects.requireNonNull(ircv3RuntimeSupport, "ircv3RuntimeSupport");
    this.targets =
        new QuasselCoreTargetResolver(
            new QuasselCoreTargetResolver.SessionPort() {
              @Override
              public void observeNetwork(QuasselCoreSession session, int networkId) {
                stateObservations.observeNetwork(session, networkId, "");
              }

              @Override
              public boolean isSelfNick(QuasselCoreSession session, String nick, int networkId) {
                return session.nicks.isSelf(nick, networkId);
              }
            });
    this.stateObservations =
        new QuasselCoreStateObservationCoordinator(targets, observations::observeNetwork);
    this.networkManagement =
        new QuasselCoreNetworkManagementCoordinator(observations, stateObservations);
    this.inputs =
        new QuasselCoreInputCoordinator(
            targets,
            (session, target, networkId) ->
                stateObservations.observeTargetNetwork(session, target, networkId, true));
    this.outboundSignals =
        new QuasselCoreOutboundSignalCoordinator(
            ircv3RuntimeSupport, targets, inputs, stateObservations);
    this.sessionQueries =
        new QuasselCoreSessionQueries(sessions::get, availabilityReasonByServer::get);
    this.sessionOperations = new QuasselCoreSessionOperations(sessionQueries, inputs);
    this.historyRequests =
        new QuasselCoreHistoryRequestCoordinator(
            ircv3RuntimeSupport, targets, sessionOperations::requireEstablishedSession);
    this.lagProbes =
        new QuasselCoreLagProbeCoordinator(
            sessionOperations::requireEstablishedSession, sessions::get);
    IrcProperties.Client client = props == null ? null : props.client();
    this.reconnects =
        new QuasselCoreReconnectCoordinator(
            client == null ? null : client.reconnect(),
            serverCatalog::containsId,
            sid -> connectInternal(sid, false),
            bus::onNext,
            RxVirtualSchedulers::io);
    this.setups =
        new QuasselCoreSetupCoordinator(
            serverCatalog, handshakes, this::prepareCoreSetup, availabilityReasonByServer::put);
  }

  @PreDestroy
  void onDestroy() {
    shutdownNow();
  }

  @Override
  public void shutdownNow() {
    if (!shuttingDown.compareAndSet(false, true)) return;
    reconnects.close();
    for (Map.Entry<String, QuasselCoreSession> entry : sessions.entrySet()) {
      closeSession(entry.getValue(), "Client shutting down", false);
    }
    sessions.clear();
    availabilityReasonByServer.clear();
    setups.clear();
  }

  @Override
  public String backendId() {
    return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.QUASSEL_CORE);
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return bus.onBackpressureBuffer();
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    return sessionQueries.currentNick(serverId);
  }

  @Override
  public String backendAvailabilityReason(String serverId) {
    return sessionQueries.availabilityReason(serverId);
  }

  @Override
  public boolean isQuasselCoreSetupPending(String serverId) {
    return setups.isPending(serverId);
  }

  @Override
  public boolean hasEstablishedQuasselCoreSession(String serverId) {
    return sessionQueries.hasEstablished(serverId);
  }

  @Override
  public Optional<QuasselCoreSetupPrompt> quasselCoreSetupPrompt(String serverId) {
    return setups.prompt(serverId);
  }

  @Override
  public Completable submitQuasselCoreSetup(String serverId, QuasselCoreSetupRequest request) {
    return setups.submit(serverId, request);
  }

  @Override
  public List<QuasselCoreNetworkSummary> quasselCoreNetworks(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    QuasselCoreSession session = sessions.get(sid);
    if (session == null || session.socketRef.get() == null) return List.of();
    return stateObservations.snapshot(session);
  }

  @Override
  public Flowable<QuasselCoreNetworkSnapshotEvent> quasselCoreNetworkEvents() {
    return observations.networkEvents();
  }

  @Override
  public Completable quasselCoreConnectNetwork(String serverId, String networkIdOrName) {
    return sessionOperations.withEstablishedSession(
        serverId,
        "quassel connect network",
        (ignored, session) -> networkManagement.connect(session, networkIdOrName));
  }

  @Override
  public Completable quasselCoreDisconnectNetwork(String serverId, String networkIdOrName) {
    return sessionOperations.withEstablishedSession(
        serverId,
        "quassel disconnect network",
        (ignored, session) -> networkManagement.disconnect(session, networkIdOrName));
  }

  @Override
  public Completable quasselCoreCreateNetwork(
      String serverId, QuasselCoreNetworkCreateRequest request) {
    return sessionOperations.withEstablishedSession(
        serverId,
        "quassel create network",
        (ignored, session) -> networkManagement.create(session, request));
  }

  @Override
  public Completable quasselCoreUpdateNetwork(
      String serverId, String networkIdOrName, QuasselCoreNetworkUpdateRequest request) {
    return sessionOperations.withEstablishedSession(
        serverId,
        "quassel update network",
        (ignored, session) -> networkManagement.update(session, networkIdOrName, request));
  }

  @Override
  public Completable quasselCoreRemoveNetwork(String serverId, String networkIdOrName) {
    return sessionOperations.withEstablishedSession(
        serverId,
        "quassel remove network",
        (ignored, session) -> networkManagement.remove(session, networkIdOrName));
  }

  @Override
  public Completable connect(String serverId) {
    return connectInternal(serverId, true);
  }

  private Completable connectInternal(String serverId, boolean resetReconnectAttempts) {
    return Completable.fromAction(
            () -> {
              if (shuttingDown.get()) return;

              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              reconnects.cancel(sid, false);
              if (resetReconnectAttempts) {
                reconnects.reset(sid);
              }
              availabilityReasonByServer.put(sid, "Quassel transport is connecting");

              IrcProperties.Server server = serverCatalog.require(sid);
              QuasselCoreSession existing = sessions.get(sid);
              if (existing != null) return;

              String nick = configuredNick(server);
              QuasselCoreSession next =
                  new QuasselCoreSession(
                      sid,
                      nick,
                      server.host(),
                      server.port(),
                      signalProxySender,
                      observations::observeIdentity,
                      event -> bus.onNext(new ServerIrcEvent(sid, event)));
              QuasselCoreSession previous = sessions.putIfAbsent(sid, next);
              if (previous != null) return;

              bus.onNext(
                  new ServerIrcEvent(
                      sid,
                      new IrcEvent.Connecting(
                          Instant.now(),
                          next.connectedHost,
                          next.connectedPort,
                          next.initialNick)));

              RxVirtualSchedulers.io().scheduleDirect(() -> establishSession(server, next));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable disconnect(String serverId) {
    return disconnect(serverId, null);
  }

  @Override
  public Completable disconnect(String serverId, String reason) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) return;
              reconnects.cancel(sid, true);
              reconnects.reset(sid);

              QuasselCoreSession removed = sessions.remove(sid);
              if (removed == null) {
                availabilityReasonByServer.put(sid, normalizeDisconnectReason(reason));
                bus.onNext(
                    new ServerIrcEvent(
                        sid,
                        new IrcEvent.Disconnected(
                            Instant.now(), normalizeDisconnectReason(reason))));
                return;
              }

              closeSession(removed, normalizeDisconnectReason(reason), true);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.changeNick(newNick));
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.setAway(awayMessage));
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.requestNames(channel));
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.joinChannel(channel));
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return sessionOperations.sendPlannedInput(serverId, () -> QuasselCoreUserInput.whois(nick));
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.partChannel(channel, reason));
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.sendToChannel(channel, message));
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.sendPrivateMessage(nick, message));
  }

  @Override
  public Completable sendNoticeToChannel(String serverId, String channel, String message) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.sendNoticeToChannel(channel, message));
  }

  @Override
  public Completable sendNoticePrivate(String serverId, String nick, String message) {
    return sessionOperations.sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.sendNoticePrivate(nick, message));
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    return sessionOperations.sendRaw(serverId, rawLine);
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return sessionOperations.withEstablishedSession(
        serverId,
        "send typing",
        (sid, session) -> {
          if (!isTypingAvailable(sid)) {
            String reason = Objects.toString(typingAvailabilityReason(sid), "").trim();
            String suffix = reason.isEmpty() ? "" : (" (" + reason + ")");
            throw new IllegalStateException(
                "Typing indicators not available (requires message-tags and server allowing +typing)"
                    + suffix
                    + ": "
                    + sid);
          }

          outboundSignals.sendTyping(session, target, state);
        });
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return sessionOperations.withEstablishedSession(
        serverId,
        "send read marker",
        (sid, session) -> {
          if (!isReadMarkerAvailable(sid)) {
            throw new IllegalStateException(
                "read-marker capability not negotiated (requires read-marker or draft/read-marker): "
                    + sid);
          }

          outboundSignals.sendReadMarker(session, target, markerAt);
        });
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit) {
    return historyRequests.requestBefore(serverId, target, beforeExclusive, limit);
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    return historyRequests.requestBefore(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    return historyRequests.requestLatest(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return historyRequests.requestBetween(serverId, target, startSelector, endSelector, limit);
  }

  @Override
  public Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return historyRequests.requestAround(serverId, target, selector, limit);
  }

  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    return sessionQueries.isEstablished(serverId);
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    return sessionQueries.isEstablished(serverId);
  }

  @Override
  public boolean isMessageTagsAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, MESSAGE_TAGS);
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, MULTILINE, DRAFT_MULTILINE);
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    return sessionQueries.multilineMaxBytes(serverId);
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    return sessionQueries.multilineMaxLines(serverId);
  }

  @Override
  public boolean isExperimentalMessageEditAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, DRAFT_MESSAGE_EDIT);
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, DRAFT_MESSAGE_REDACTION);
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    QuasselCoreSession session = sessionQueries.findEstablished(serverId);
    return outboundSignals.isTypingAvailable(session);
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    QuasselCoreSession session = sessionQueries.findEstablished(serverId);
    if (session == null) {
      return backendAvailabilityReason(serverId);
    }
    return outboundSignals.typingAvailabilityReason(session);
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    QuasselCoreSession session = sessionQueries.findEstablished(serverId);
    return outboundSignals.isReadMarkerAvailable(session);
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, LABELED_RESPONSE);
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    return sessionQueries.hasCapability(serverId, STANDARD_REPLIES);
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    return sessionQueries.monitorAvailable(serverId);
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    return sessionQueries.monitorLimit(serverId);
  }

  @Override
  public Completable requestLagProbe(String serverId) {
    return lagProbes.request(serverId);
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    return lagProbes.lastMeasuredLagMs(serverId);
  }

  private void scheduleReconnectIfEligible(QuasselCoreSession session, String reason) {
    if (session == null || shuttingDown.get() || session.closeRequested.get()) return;
    if (!session.reconnectScheduled.compareAndSet(false, true)) return;
    reconnects.schedule(session.serverId, reason);
  }

  private void establishSession(IrcProperties.Server server, QuasselCoreSession session) {
    String sid = session.serverId;
    Socket openedSocket = null;
    try {
      if (shuttingDown.get() || session.closeRequested.get()) {
        sessions.remove(sid, session);
        return;
      }

      openedSocket = socketConnector.connect(server);
      if (shuttingDown.get() || session.closeRequested.get()) {
        closeQuietly(openedSocket);
        sessions.remove(sid, session);
        return;
      }

      QuasselCoreProtocolProbe.ProbeSelection probe = handshakes.negotiateDatastream(openedSocket);

      session.socketRef.set(openedSocket);
      session.phase.set(QuasselSessionPhase.TRANSPORT_CONNECTED);
      bus.onNext(
          new ServerIrcEvent(
              sid,
              new IrcEvent.Connected(
                  Instant.now(),
                  session.connectedHost,
                  session.connectedPort,
                  session.nicks.lastObservedNick())));

      session.probeSelection.set(probe);
      session.phase.set(QuasselSessionPhase.PROTOCOL_NEGOTIATED);
      sessionEvents.emitConnectionPhase(
          session, PHASE_PROTOCOL_NEGOTIATED, renderProbeSource(probe));

      session.phase.set(QuasselSessionPhase.AUTHENTICATING);
      QuasselCoreAuthHandshake.AuthResult auth = authHandshake.authenticate(openedSocket, server);
      session.initialize(
          auth,
          id -> stateObservations.observeNetwork(session, id, ""),
          info ->
              stateObservations.observeTargetNetwork(
                  session, info.bufferName(), info.networkId(), false));
      session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
      availabilityReasonByServer.remove(sid);
      setups.forget(sid);
      reconnects.reset(sid);
      reconnects.cancel(sid, false);

      sendSignalProxyInitRequest(session, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT);
      for (Integer networkId :
          session.networks.knownIds(session.authResult.get(), session.buffers.values())) {
        networkManagement.requestNetworkInitState(session, networkId);
      }
      Disposable readTask = RxVirtualSchedulers.io().scheduleDirect(() -> runReadLoop(session));
      session.readLoopTask.set(readTask);
      session.readiness.scheduleFallback();
    } catch (QuasselCoreAuthHandshake.CoreSetupRequiredException e) {
      closeQuietly(openedSocket);
      sessions.remove(sid, session);
      if (session.closeRequested.get()) return;

      String detail = renderThrowableMessage(e);
      String reason = detail.isEmpty() ? "Quassel Core setup is required before login" : detail;
      setups.rememberRequired(sid, reason, e.setupFields());
      sessionEvents.emitConnectionPhase(session, PHASE_SETUP_REQUIRED, reason);
      bus.onNext(new ServerIrcEvent(sid, new IrcEvent.Error(Instant.now(), reason, e)));
      sessionEvents.emitDisconnectedOnce(session, reason);
    } catch (Exception e) {
      closeQuietly(openedSocket);
      sessions.remove(sid, session);
      if (session.closeRequested.get()) return;

      String detail = renderThrowableMessage(e);
      String reason = detail.isEmpty() ? "Connect failed" : ("Connect failed: " + detail);
      availabilityReasonByServer.put(sid, reason);
      bus.onNext(new ServerIrcEvent(sid, new IrcEvent.Error(Instant.now(), reason, e)));
      sessionEvents.emitDisconnectedOnce(session, reason);
      scheduleReconnectIfEligible(session, reason);
    }
  }

  private void runReadLoop(QuasselCoreSession session) {
    String sid = session.serverId;
    Socket socket = session.socketRef.get();
    if (socket == null) {
      session.readiness.close();
      return;
    }

    try {
      QuasselCoreInboundPipeline pipeline =
          new QuasselCoreInboundPipeline(
              session, targets, stateObservations, ircv3RuntimeSupport, bus::onNext);
      readLoop.read(
          sid,
          socket,
          () -> shuttingDown.get() || session.closeRequested.get(),
          pipeline::dispatch,
          failure -> handleReadFailure(session, failure));
    } finally {
      session.readiness.close();
      closeQuietly(session.socketRef.getAndSet(null));
      sessions.remove(sid, session);
      if (session.closeRequested.get()) {
        String reason = normalizeDisconnectReason(session.closeReason.get());
        availabilityReasonByServer.put(sid, reason);
        sessionEvents.emitDisconnectedOnce(session, reason);
      }
    }
  }

  private void handleReadFailure(QuasselCoreSession session, QuasselCoreReadLoop.Failure failure) {
    session.readiness.close();
    String sid = session.serverId;
    String reason = failure.reason();
    availabilityReasonByServer.put(sid, reason);
    if (failure.cause() != null) {
      bus.onNext(
          new ServerIrcEvent(sid, new IrcEvent.Error(Instant.now(), reason, failure.cause())));
    }
    sessionEvents.emitDisconnectedOnce(session, reason);
    scheduleReconnectIfEligible(session, reason);
  }

  private void prepareCoreSetup(String serverId) {
    reconnects.cancel(serverId, true);
    reconnects.reset(serverId);
    QuasselCoreSession removed = sessions.remove(serverId);
    if (removed != null) {
      closeSession(removed, "Completing Quassel Core setup", false);
    }
  }

  private void closeSession(QuasselCoreSession session, String reason, boolean emitDisconnected) {
    if (session == null) return;
    session.closeRequested.set(true);
    session.closeReason.set(normalizeDisconnectReason(reason));

    session.readiness.close();

    session.disposeReadLoopTask();

    closeQuietly(session.socketRef.getAndSet(null));
    session.clearObservedState();
    availabilityReasonByServer.put(session.serverId, session.closeReason.get());
    if (emitDisconnected) {
      sessionEvents.emitDisconnectedOnce(session, session.closeReason.get());
    }
  }

  private static void closeQuietly(Socket socket) {
    if (socket == null) return;
    try {
      socket.close();
    } catch (Exception ignored) {
    }
  }

  private static String configuredNick(IrcProperties.Server server) {
    String nick = Objects.toString(server.nick(), "").trim();
    return nick.isEmpty() ? "quassel-user" : nick;
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String renderThrowableMessage(Throwable err) {
    if (err == null) return "";
    String message = Objects.toString(err.getMessage(), "").trim();
    if (!message.isEmpty()) return message;
    return err.getClass().getSimpleName();
  }

  private void sendSignalProxyInitRequest(
      QuasselCoreSession session, String className, String objectName) throws Exception {
    if (session == null) return;
    session.networkCommands.requestInit(className, objectName);
  }
}
