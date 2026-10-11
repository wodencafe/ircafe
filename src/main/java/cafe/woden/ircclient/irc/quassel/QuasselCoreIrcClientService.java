package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.sanitizeHistoryTarget;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsCrlf;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_MESSAGE_EDIT;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_MESSAGE_REDACTION;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_MULTILINE;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_READ_MARKER;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.LABELED_RESPONSE;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.MESSAGE_TAGS;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.MULTILINE;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.READ_MARKER;
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
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.QualifiedTarget;
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
import java.util.function.Supplier;
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

  private static final int BUFFER_CHANNEL = 0x02;
  private static final int BUFFER_QUERY = 0x04;
  private static final String BUFFER_SYNCER_CLASS = "BufferSyncer";
  private static final String BUFFER_SYNCER_OBJECT = "";
  private static final String BACKEND_UNAVAILABLE_REASON = "Quassel Core backend is not connected";
  private static final String HANDSHAKE_INCOMPLETE_REASON =
      "Quassel protocol negotiated, but login/session handshake is not complete";
  private static final String DEFAULT_DISCONNECT_REASON = "Client requested disconnect";
  private static final String FEATURE_PHASE_PREFIX = "quassel-phase=";
  private static final String FEATURE_DETAIL_PREFIX = ";detail=";
  private static final String PHASE_PROTOCOL_NEGOTIATED = "protocol-negotiated";
  private static final String PHASE_SETUP_REQUIRED = "setup-required";

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();
  private final QuasselCoreObservationMediator observations = new QuasselCoreObservationMediator();
  private final Map<String, QuasselCoreSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> availabilityReasonByServer = new ConcurrentHashMap<>();
  private final Map<String, QuasselCoreSetupPrompt> pendingSetupByServer =
      new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  private final ServerCatalog serverCatalog;
  private final QuasselCoreSocketConnector socketConnector;
  private final QuasselCoreProtocolProbe protocolProbe;
  private final QuasselCoreAuthHandshake authHandshake;
  private final QuasselCoreReadLoop readLoop;
  private final QuasselCoreSignalProxySender signalProxySender;
  private final QuasselCoreReconnectCoordinator reconnects;
  private final QuasselIrcv3RuntimeSupport ircv3RuntimeSupport;
  private final QuasselCoreTargetResolver targets;
  private final QuasselCoreInputCoordinator inputs;
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
    this.protocolProbe = Objects.requireNonNull(protocolProbe, "protocolProbe");
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
    IrcProperties.Client client = props == null ? null : props.client();
    this.reconnects =
        new QuasselCoreReconnectCoordinator(
            client == null ? null : client.reconnect(),
            serverCatalog::containsId,
            sid -> connectInternal(sid, false),
            bus::onNext,
            RxVirtualSchedulers::io);
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
    pendingSetupByServer.clear();
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
    QuasselCoreSession session = sessions.get(normalizeServerId(serverId));
    if (session == null || session.socketRef.get() == null) return Optional.empty();
    String nick = session.nicks.current();
    return nick.isEmpty() ? Optional.empty() : Optional.of(nick);
  }

  @Override
  public String backendAvailabilityReason(String serverId) {
    String sid = normalizeServerId(serverId);
    QuasselCoreSession session = sessions.get(sid);
    if (session != null) {
      QuasselSessionPhase phase = session.phase.get();
      if (phase == QuasselSessionPhase.SESSION_ESTABLISHED) {
        return "";
      }
      if (phase == QuasselSessionPhase.AUTHENTICATING
          || phase == QuasselSessionPhase.PROTOCOL_NEGOTIATED) {
        return HANDSHAKE_INCOMPLETE_REASON;
      }
      if (phase == QuasselSessionPhase.TRANSPORT_CONNECTED) {
        return "Quassel transport connected, but protocol negotiation is incomplete";
      }
    }
    String remembered = Objects.toString(availabilityReasonByServer.get(sid), "").trim();
    if (!remembered.isEmpty()) return remembered;
    return BACKEND_UNAVAILABLE_REASON;
  }

  @Override
  public boolean isQuasselCoreSetupPending(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && pendingSetupByServer.containsKey(sid);
  }

  @Override
  public boolean hasEstablishedQuasselCoreSession(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    QuasselCoreSession session = sessions.get(sid);
    if (session == null || session.socketRef.get() == null) return false;
    return session.phase.get() == QuasselSessionPhase.SESSION_ESTABLISHED;
  }

  @Override
  public Optional<QuasselCoreSetupPrompt> quasselCoreSetupPrompt(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Optional.empty();
    return Optional.ofNullable(pendingSetupByServer.get(sid));
  }

  @Override
  public Completable submitQuasselCoreSetup(String serverId, QuasselCoreSetupRequest request) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSetupPrompt prompt = pendingSetupByServer.get(sid);
              if (prompt == null) {
                throw new IllegalStateException("Quassel Core setup is not pending for " + sid);
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              QuasselCoreSetupRequest req =
                  QuasselCoreSetupSupport.normalizeSetupRequest(
                      prompt, Objects.requireNonNull(request, "request"));

              reconnects.cancel(sid, true);
              reconnects.reset(sid);
              QuasselCoreSession removed = sessions.remove(sid);
              if (removed != null) {
                closeSession(removed, "Completing Quassel Core setup", false);
              }

              try (Socket socket = socketConnector.connect(server)) {
                QuasselCoreProtocolProbe.ProbeSelection probe = protocolProbe.negotiate(socket);
                if (probe.protocolType() != QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM) {
                  throw new IllegalStateException(
                      "Quassel core selected unsupported protocol "
                          + QuasselCoreProtocolProbe.protocolLabel(probe.protocolType()));
                }
                authHandshake.performCoreSetup(
                    socket,
                    new QuasselCoreAuthHandshake.CoreSetupRequest(
                        req.adminUser(),
                        req.adminPassword(),
                        req.storageBackend(),
                        req.authenticator(),
                        req.storageSetupData(),
                        req.authSetupData()));
              }

              pendingSetupByServer.remove(sid);
              availabilityReasonByServer.put(
                  sid, "Quassel Core setup completed. Reconnect to start a session.");
            })
        .subscribeOn(RxVirtualSchedulers.io());
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
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session =
                  requireEstablishedSession(sid, "quassel connect network");
              networkManagement.connect(session, networkIdOrName);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable quasselCoreDisconnectNetwork(String serverId, String networkIdOrName) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session =
                  requireEstablishedSession(sid, "quassel disconnect network");
              networkManagement.disconnect(session, networkIdOrName);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable quasselCoreCreateNetwork(
      String serverId, QuasselCoreNetworkCreateRequest request) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session = requireEstablishedSession(sid, "quassel create network");
              networkManagement.create(session, request);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable quasselCoreUpdateNetwork(
      String serverId, String networkIdOrName, QuasselCoreNetworkUpdateRequest request) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session = requireEstablishedSession(sid, "quassel update network");
              networkManagement.update(session, networkIdOrName, request);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable quasselCoreRemoveNetwork(String serverId, String networkIdOrName) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session = requireEstablishedSession(sid, "quassel remove network");
              networkManagement.remove(session, networkIdOrName);
            })
        .subscribeOn(RxVirtualSchedulers.io());
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
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.changeNick(newNick));
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.setAway(awayMessage));
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.requestNames(channel));
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.joinChannel(channel));
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.whois(nick));
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.partChannel(channel, reason));
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.sendToChannel(channel, message));
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.sendPrivateMessage(nick, message));
  }

  @Override
  public Completable sendNoticeToChannel(String serverId, String channel, String message) {
    return sendPlannedInput(
        serverId, () -> QuasselCoreUserInput.sendNoticeToChannel(channel, message));
  }

  @Override
  public Completable sendNoticePrivate(String serverId, String nick, String message) {
    return sendPlannedInput(serverId, () -> QuasselCoreUserInput.sendNoticePrivate(nick, message));
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              String raw = Objects.toString(rawLine, "").strip();
              if (sid.isEmpty()) throw new IllegalArgumentException(SERVER_ID_BLANK);
              if (raw.isEmpty()) throw new IllegalArgumentException("raw line is blank");
              if (containsCrlf(raw)) throw new IllegalArgumentException("raw line contains CR/LF");

              QuasselCoreSession session = requireEstablishedSession(sid, "send raw");
              inputs.sendRaw(session, "send raw", raw);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session = requireEstablishedSession(sid, "send typing");
              if (!isTypingAvailable(sid)) {
                String reason = Objects.toString(typingAvailabilityReason(sid), "").trim();
                String suffix = reason.isEmpty() ? "" : (" (" + reason + ")");
                throw new IllegalStateException(
                    "Typing indicators not available (requires message-tags and server allowing +typing)"
                        + suffix
                        + ": "
                        + sid);
              }

              QualifiedTarget dest = sanitizeHistoryTarget(target);
              List<String> rawLines = ircv3RuntimeSupport.typingRawLines(dest.rawTarget(), state);
              for (String rawLine : rawLines) {
                inputs.sendRaw(session, "send typing", rawLine);
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session = requireEstablishedSession(sid, "send read marker");
              if (!isReadMarkerAvailable(sid)) {
                throw new IllegalStateException(
                    "read-marker capability not negotiated (requires read-marker or draft/read-marker): "
                        + sid);
              }

              QualifiedTarget requested = sanitizeHistoryTarget(target);
              int typeBitsHint =
                  looksLikeChannel(requested.baseTarget()) ? BUFFER_CHANNEL : BUFFER_QUERY;
              QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
                  targets.outboundBuffer(session, typeBitsHint, requested);
              stateObservations.observeTargetNetwork(
                  session, requested.baseTarget(), bufferInfo.networkId(), true);

              Instant at = markerAt == null ? Instant.now() : markerAt;
              String markerTarget =
                  targets.targetForBuffer(session, bufferInfo, requested.baseTarget());
              if (markerTarget.isEmpty()) {
                markerTarget = requested.rawTarget();
              }
              long markerMsgId = session.history.readMarkerMsgIdForTimestamp(markerTarget, at);
              if (markerMsgId > 0L && bufferInfo.bufferId() >= 0) {
                sendBufferSyncerReadMarkerUpdate(session, bufferInfo.bufferId(), markerMsgId);
                return;
              }

              if (!hasCapability(session, READ_MARKER, DRAFT_READ_MARKER)) {
                throw new IllegalStateException(
                    "Quassel read marker requires an observed message for "
                        + requested.rawTarget());
              }

              List<String> rawLines =
                  ircv3RuntimeSupport.readMarkerRawLines(requested.rawTarget(), at);
              if (rawLines.isEmpty()) {
                throw new IllegalStateException(
                    "Read-marker runtime provider did not render a command for "
                        + requested.rawTarget());
              }
              for (String rawLine : rawLines) {
                inputs.sendRaw(session, "send read marker", rawLine);
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit) {
    return requestHistory(
        serverId,
        "request chat history",
        () ->
            ircv3RuntimeSupport.chatHistoryBefore(
                target, "", limit, beforeExclusive == null ? Instant.now() : beforeExclusive));
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    return requestHistory(
        serverId,
        "request chat history",
        () -> ircv3RuntimeSupport.chatHistoryBefore(target, selector, limit, Instant.now()));
  }

  @Override
  public Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    return requestHistory(
        serverId,
        "request latest chat history",
        () -> ircv3RuntimeSupport.chatHistoryLatest(target, selector, limit));
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return requestHistory(
        serverId,
        "request bounded chat history",
        () -> ircv3RuntimeSupport.chatHistoryBetween(target, startSelector, endSelector, limit));
  }

  @Override
  public Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return requestHistory(
        serverId,
        "request surrounding chat history",
        () -> ircv3RuntimeSupport.chatHistoryAround(target, selector, limit));
  }

  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    return isSessionEstablished(serverId);
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    return isSessionEstablished(serverId);
  }

  @Override
  public boolean isMessageTagsAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, MULTILINE, DRAFT_MULTILINE);
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    if (session == null || !session.features.hasMultilineLimits()) return 0L;
    return session.features.multilineMaxBytes(primaryNetworkId(session));
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    if (session == null || !session.features.hasMultilineLimits()) return 0;
    return session.features.multilineMaxLines(primaryNetworkId(session));
  }

  @Override
  public boolean isExperimentalMessageEditAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, DRAFT_MESSAGE_EDIT);
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, DRAFT_MESSAGE_REDACTION);
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    if (session == null) return false;
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    if (session == null) {
      return backendAvailabilityReason(serverId);
    }
    if (hasCapability(session, MESSAGE_TAGS)) {
      return "";
    }
    if (!session.features.hasObservedCapabilities()) {
      return "typing support status is not yet available from Quassel backend state";
    }
    if (!hasCapability(session, MESSAGE_TAGS)) {
      return "message-tags not negotiated in Quassel backend network state";
    }
    return "server may be blocking +typing via CLIENTTAGDENY";
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return session != null
        && (session.nativeReadMarkerSupportObserved.get()
            || hasCapability(session, READ_MARKER, DRAFT_READ_MARKER));
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, LABELED_RESPONSE);
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return hasCapability(session, STANDARD_REPLIES);
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    return session != null
        && session.features.hasMonitorState()
        && session.features.monitorAvailable(primaryNetworkId(session));
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    QuasselCoreSession session = findEstablishedSession(serverId);
    if (session == null || !session.features.hasMonitorState()) return 0;
    return session.features.monitorLimit(primaryNetworkId(session));
  }

  @Override
  public Completable requestLagProbe(String serverId) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSession session = requireEstablishedSession(sid, "request lag probe");
              Socket socket = session.socketRef.get();
              if (socket == null) {
                throw new IllegalStateException("Quassel socket is closed");
              }

              QuasselCoreDatastreamCodec.QtDateTimeValue token = session.lag.beginProbe();

              try {
                session.outbound.send(
                    socket,
                    (codec, out) -> {
                      codec.writeSignalProxyHeartBeat(out, token);
                    });
              } catch (Exception e) {
                session.lag.cancelProbe();
                throw e;
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    QuasselCoreSession session = sessions.get(normalizeServerId(serverId));
    if (session == null || session.socketRef.get() == null) return OptionalLong.empty();
    return session.lag.lastMeasuredLagMs();
  }

  private QuasselCoreSession findEstablishedSession(String serverId) {
    QuasselCoreSession session = sessions.get(normalizeServerId(serverId));
    if (session == null) return null;
    if (session.socketRef.get() == null) return null;
    if (session.phase.get() != QuasselSessionPhase.SESSION_ESTABLISHED) return null;
    return session;
  }

  private boolean isSessionEstablished(String serverId) {
    return findEstablishedSession(serverId) != null;
  }

  private boolean hasCapability(QuasselCoreSession session, String... capabilities) {
    return session != null && session.features.hasAnyCapability(capabilities);
  }

  private Completable requestHistory(
      String serverId, String operation, Supplier<Ircv3ChatHistoryRuntimeSupport.Plan> planner) {
    return Completable.fromAction(
            () -> {
              Ircv3ChatHistoryRuntimeSupport.Plan plan = planner.get();
              HistoryRequestContext ctx = prepareHistoryRequest(serverId, plan.target(), operation);
              QuasselCoreHistoryRequestPlanner.BacklogRequest request =
                  QuasselCoreHistoryRequestPlanner.plan(ctx.session().history, plan);
              ctx.session()
                  .bufferCommands
                  .requestBacklog(
                      ctx.bufferInfo(), request.firstMsgId(), request.lastMsgId(), request.limit());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private HistoryRequestContext prepareHistoryRequest(
      String serverId, String target, String operation) throws BackendNotAvailableException {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      throw new IllegalArgumentException(SERVER_ID_BLANK);
    }
    QualifiedTarget tgt = sanitizeHistoryTarget(target);
    QuasselCoreSession session = requireEstablishedSession(sid, operation);
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
        targets.historyBuffer(session, sid, operation, tgt);
    return new HistoryRequestContext(session, bufferInfo);
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

      QuasselCoreProtocolProbe.ProbeSelection probe = protocolProbe.negotiate(openedSocket);
      if (probe.protocolType() != QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM) {
        throw new IllegalStateException(
            "Quassel core selected unsupported protocol "
                + QuasselCoreProtocolProbe.protocolLabel(probe.protocolType()));
      }

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
      emitConnectionPhase(session, PHASE_PROTOCOL_NEGOTIATED, renderProbeSource(probe));

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
      pendingSetupByServer.remove(sid);
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
      availabilityReasonByServer.put(sid, reason);
      pendingSetupByServer.put(
          sid, QuasselCoreSetupSupport.buildSetupPrompt(sid, reason, e.setupFields()));
      emitConnectionPhase(session, PHASE_SETUP_REQUIRED, reason);
      bus.onNext(new ServerIrcEvent(sid, new IrcEvent.Error(Instant.now(), reason, e)));
      emitDisconnectedOnce(session, reason);
    } catch (Exception e) {
      closeQuietly(openedSocket);
      sessions.remove(sid, session);
      if (session.closeRequested.get()) return;

      String detail = renderThrowableMessage(e);
      String reason = detail.isEmpty() ? "Connect failed" : ("Connect failed: " + detail);
      availabilityReasonByServer.put(sid, reason);
      bus.onNext(new ServerIrcEvent(sid, new IrcEvent.Error(Instant.now(), reason, e)));
      emitDisconnectedOnce(session, reason);
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
      QuasselCoreReadMarkerCoordinator markers = readMarkerCoordinator(session);
      QuasselCoreSyncDispatcher sync = syncDispatcher(session, markers);
      QuasselCoreRpcDispatcher rpc = rpcDispatcher(session, inboundCoordinator(session, markers));
      QuasselCoreSignalProxyDispatcher dispatcher =
          new QuasselCoreSignalProxyDispatcher(session, rpc::dispatch, sync::dispatch);
      readLoop.read(
          sid,
          socket,
          () -> shuttingDown.get() || session.closeRequested.get(),
          dispatcher::dispatch,
          failure -> handleReadFailure(session, failure));
    } finally {
      session.readiness.close();
      closeQuietly(session.socketRef.getAndSet(null));
      sessions.remove(sid, session);
      if (session.closeRequested.get()) {
        String reason = normalizeDisconnectReason(session.closeReason.get());
        availabilityReasonByServer.put(sid, reason);
        emitDisconnectedOnce(session, reason);
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
    emitDisconnectedOnce(session, reason);
    scheduleReconnectIfEligible(session, reason);
  }

  private QuasselCoreInboundMessageCoordinator inboundCoordinator(
      QuasselCoreSession session, QuasselCoreReadMarkerCoordinator markers) {
    return new QuasselCoreInboundMessageCoordinator(
        session,
        markers,
        targets,
        ircv3RuntimeSupport,
        new QuasselCoreInboundMessageCoordinator.SessionPort() {
          @Override
          public QuasselCoreDatastreamCodec.BufferInfoValue resolveBuffer(
              QuasselCoreDatastreamCodec.BufferInfoValue incoming) {
            return stateObservations.resolveBuffer(session, incoming);
          }

          @Override
          public String currentNick(int networkId) {
            return session.nicks.forNetwork(networkId);
          }

          @Override
          public boolean isSelfNick(String nick, int networkId) {
            return session.nicks.isSelf(nick, networkId);
          }

          @Override
          public void observeTargetNetwork(String target, int networkId) {
            stateObservations.observeTargetNetwork(session, target, networkId, true);
          }

          @Override
          public void observeNick(int networkId, Instant at, String nick) {
            session.nicks.observe(networkId, nick, at);
          }

          @Override
          public void emit(IrcEvent event) {
            bus.onNext(new ServerIrcEvent(session.serverId, event));
          }
        });
  }

  private QuasselCoreRpcDispatcher rpcDispatcher(
      QuasselCoreSession session, QuasselCoreInboundMessageCoordinator inbound) {
    QuasselCoreNetworkLifecycleTranslator networks =
        new QuasselCoreNetworkLifecycleTranslator(
            session.serverId,
            session.networks::claimCreatedName,
            (networkId, name) -> stateObservations.observeNetwork(session, networkId, name),
            networkId -> stateObservations.forgetNetwork(session, networkId),
            (networkId, state) -> stateObservations.observeFullState(session, networkId, state));
    return new QuasselCoreRpcDispatcher(
        session,
        networks,
        new QuasselCoreRpcDispatcher.SessionPort() {
          @Override
          public void displayMessage(QuasselCoreDatastreamCodec.MessageValue message) {
            inbound.handle(message);
          }

          @Override
          public void observeBuffer(QuasselCoreDatastreamCodec.BufferInfoValue merged) {
            stateObservations.observeBuffer(session, merged);
          }

          @Override
          public void emit(IrcEvent event) {
            bus.onNext(new ServerIrcEvent(session.serverId, event));
          }
        });
  }

  private QuasselCoreSyncDispatcher syncDispatcher(
      QuasselCoreSession session, QuasselCoreReadMarkerCoordinator markers) {
    return new QuasselCoreSyncDispatcher(
        session,
        networkSyncTranslator(session),
        new QuasselCoreSyncDispatcher.SessionPort() {
          @Override
          public void applyBufferInfoSnapshot(List<Object> values) {
            stateObservations.applyBufferSnapshot(session, values);
          }

          @Override
          public void observeReadMarkers(String slotName, List<Object> values) {
            markers.observeSync(slotName, values);
          }

          @Override
          public void receiveBacklog(List<Object> values) {
            handleBacklogSync(session, markers, values);
          }

          @Override
          public void observeNetwork(int networkId, String name) {
            stateObservations.observeNetwork(session, networkId, name);
          }

          @Override
          public String qualifyTarget(String target, int networkId) {
            return targets.qualifyTarget(session, target, networkId);
          }

          @Override
          public void emit(IrcEvent event) {
            bus.onNext(new ServerIrcEvent(session.serverId, event));
          }
        });
  }

  private QuasselCoreNetworkSyncTranslator networkSyncTranslator(QuasselCoreSession session) {
    return new QuasselCoreNetworkSyncTranslator(
        session.serverId,
        (networkId, name) -> stateObservations.observeNetwork(session, networkId, name),
        (networkId, state) -> stateObservations.observeState(session, networkId, state),
        (networkId, state) -> stateObservations.observeFullState(session, networkId, state),
        (networkId, nick) -> session.nicks.observe(networkId, nick, Instant.now()));
  }

  private QuasselCoreReadMarkerCoordinator readMarkerCoordinator(QuasselCoreSession session) {
    return new QuasselCoreReadMarkerCoordinator(
        session,
        new QuasselCoreReadMarkerCoordinator.SessionPort() {
          @Override
          public String currentNick(int networkId) {
            return session.nicks.forNetwork(networkId);
          }

          @Override
          public String historyTarget(
              QuasselCoreDatastreamCodec.BufferInfoValue buffer, String from) {
            return targets.targetForBuffer(session, buffer, from);
          }

          @Override
          public String qualifyTarget(String target, int networkId) {
            return targets.qualifyTarget(session, target, networkId);
          }

          @Override
          public void observeTargetNetwork(String target, int networkId) {
            stateObservations.observeTargetNetwork(session, target, networkId, true);
          }

          @Override
          public void emit(IrcEvent.ReadMarkerObserved event) {
            bus.onNext(new ServerIrcEvent(session.serverId, event));
          }
        });
  }

  private void handleBacklogSync(
      QuasselCoreSession session, QuasselCoreReadMarkerCoordinator markers, List<Object> values) {
    IrcEvent.ChatHistoryBatchReceived batch =
        session.backlog.sync(
            values,
            session.buffers::get,
            info -> stateObservations.resolveBuffer(session, info),
            (info, from) -> targets.targetForBuffer(session, info, from),
            (target, networkId) ->
                stateObservations.observeTargetNetwork(session, target, networkId, true),
            (message, entry) ->
                markers.observeHistory(entry.target(), message.messageId(), entry.at()));
    if (batch != null) bus.onNext(new ServerIrcEvent(session.serverId, batch));
  }

  private static int primaryNetworkId(QuasselCoreSession session) {
    return session == null
        ? -1
        : session.networks.primaryNetworkId(session.authResult.get(), session.buffers.values());
  }

  private record HistoryRequestContext(
      QuasselCoreSession session, QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {}

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
      emitDisconnectedOnce(session, session.closeReason.get());
    }
  }

  private void emitDisconnectedOnce(QuasselCoreSession session, String reason) {
    if (session == null) return;
    session.readiness.close();
    if (!session.disconnectedEmitted.compareAndSet(false, true)) return;
    bus.onNext(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.Disconnected(Instant.now(), normalizeDisconnectReason(reason))));
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

  private static String normalizeDisconnectReason(String reason) {
    String value = Objects.toString(reason, "").trim();
    if (value.isEmpty()) return DEFAULT_DISCONNECT_REASON;
    return value;
  }

  private static String renderThrowableMessage(Throwable err) {
    if (err == null) return "";
    String message = Objects.toString(err.getMessage(), "").trim();
    if (!message.isEmpty()) return message;
    return err.getClass().getSimpleName();
  }

  private static String renderProbeSource(QuasselCoreProtocolProbe.ProbeSelection probe) {
    return "quassel-probe protocol="
        + QuasselCoreProtocolProbe.protocolLabel(probe.protocolType())
        + " proto-features="
        + QuasselCoreProtocolProbe.hex16(probe.protocolFeatures())
        + " conn-features="
        + QuasselCoreProtocolProbe.hex8(probe.connectionFeatures());
  }

  private void emitConnectionPhase(QuasselCoreSession session, String phase, String detail) {
    if (session == null) return;
    String phaseToken = Objects.toString(phase, "").trim();
    if (phaseToken.isEmpty()) return;
    String source = FEATURE_PHASE_PREFIX + phaseToken;
    String detailToken = Objects.toString(detail, "").trim();
    if (!detailToken.isEmpty()) {
      source = source + FEATURE_DETAIL_PREFIX + detailToken.replace('\n', ' ').replace('\r', ' ');
    }
    bus.onNext(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), source)));
  }

  private QuasselCoreSession requireEstablishedSession(String serverId, String operation)
      throws BackendNotAvailableException {
    String sid = normalizeServerId(serverId);
    QuasselCoreSession session = sessions.get(sid);
    if (session == null
        || session.socketRef.get() == null
        || session.phase.get() != QuasselSessionPhase.SESSION_ESTABLISHED) {
      throw new BackendNotAvailableException(
          IrcProperties.Server.Backend.QUASSEL_CORE,
          operation,
          sid,
          backendAvailabilityReason(sid));
    }
    return session;
  }

  private void sendSignalProxyInitRequest(
      QuasselCoreSession session, String className, String objectName) throws Exception {
    if (session == null) return;
    session.networkCommands.requestInit(className, objectName);
  }

  private Completable sendPlannedInput(
      String serverId, Supplier<QuasselCoreUserInput.Plan> planner) {
    QuasselCoreUserInput.Plan plan;
    try {
      plan = planner.get();
    } catch (IllegalArgumentException error) {
      return Completable.error(error);
    }
    return sendInputWithBuffer(
        serverId, plan.operation(), plan.typeBits(), plan.target(), plan.input());
  }

  private Completable sendInputWithBuffer(
      String serverId, String operation, int typeBits, String bufferName, String input) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) throw new IllegalArgumentException(SERVER_ID_BLANK);

              QuasselCoreSession session = requireEstablishedSession(sid, operation);
              inputs.sendInput(session, operation, typeBits, bufferName, input);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private void sendBufferSyncerReadMarkerUpdate(
      QuasselCoreSession session, int bufferId, long markerMsgId) throws Exception {
    if (session == null) return;
    session.bufferCommands.updateReadMarker(bufferId, markerMsgId);
  }
}
