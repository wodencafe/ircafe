package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isHistoryTextMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.extractNick;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.UNKNOWN_MSG_ID;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreLogSummary.summarizeNetworkInfoForLog;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkConnected;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkIdentityId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreSession.MAX_NETWORK_NICKS_PER_SESSION;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.parseQualifiedTarget;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.routeOutboundRawLine;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.sanitizeHistoryTarget;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsCrlf;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.stripLeadingColon;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;
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
import cafe.woden.ircclient.irc.quassel.QuasselCoreBufferSyncerParser.ReadMarkerUpdate;
import cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.HistorySelector;
import cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.HistorySelectorKind;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.OutboundRawRoute;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreIrcClientService.class);
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private static final int BUFFER_STATUS = 0x01;
  private static final int BUFFER_CHANNEL = 0x02;
  private static final int BUFFER_QUERY = 0x04;
  private static final int MESSAGE_FLAG_BACKLOG = 0x80;
  private static final String SYNC_CONNECT_NETWORK_SLOT = "requestConnect";
  private static final String SYNC_DISCONNECT_NETWORK_SLOT = "requestDisconnect";
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
  private final QuasselCoreIrcv3InboundTranslator inboundTranslator;

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
    this.inboundTranslator = new QuasselCoreIrcv3InboundTranslator(ircv3RuntimeSupport);
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
    String nick = currentNickForPrimaryNetwork(session);
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
    return snapshotQuasselCoreNetworks(session);
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
              int networkId =
                  resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel connect network");
              log.debug(
                  "Quassel connect network request: serverId={}, networkToken='{}', resolvedNetworkId={}, slot={}",
                  sid,
                  Objects.toString(networkIdOrName, ""),
                  networkId,
                  SYNC_CONNECT_NETWORK_SLOT);
              QuasselCoreNetworkSummary targetSummary =
                  findNetworkSummaryById(snapshotQuasselCoreNetworks(session), networkId);
              if (targetSummary == null) {
                log.debug(
                    "Quassel connect target summary missing from current snapshot: serverId={}, networkId={}, knownNetworkIds={}, displayNames={}, stateKeys={}",
                    sid,
                    networkId,
                    collectKnownNetworkIds(session),
                    session.networks.displayNames(),
                    session.networks.stateIds());
              } else {
                log.debug(
                    "Quassel connect target summary: serverId={}, networkId={}, networkName={}, connected={}, enabled={}, identityId={}, host={}, port={}, tls={}, rawState={}",
                    sid,
                    networkId,
                    targetSummary.networkName(),
                    targetSummary.connected(),
                    targetSummary.enabled(),
                    targetSummary.identityId(),
                    targetSummary.serverHost(),
                    targetSummary.serverPort(),
                    targetSummary.useTls(),
                    summarizeNetworkInfoForLog(targetSummary.rawState()));
              }
              boolean repairConfirmed = maybeRepairNetworkIdentityBeforeConnect(session, networkId);
              if (!repairConfirmed) {
                log.debug(
                    "Skipping Quassel connect because identity repair is not confirmed yet: serverId={}, networkId={}",
                    sid,
                    networkId);
                return;
              }
              sendNetworkRequest(session, networkId, SYNC_CONNECT_NETWORK_SLOT);
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
              int networkId =
                  resolveQuasselNetworkId(
                      session, sid, networkIdOrName, "quassel disconnect network");
              log.debug(
                  "Quassel disconnect network request: serverId={}, networkToken='{}', resolvedNetworkId={}, slot={}",
                  sid,
                  Objects.toString(networkIdOrName, ""),
                  networkId,
                  SYNC_DISCONNECT_NETWORK_SLOT);
              sendNetworkRequest(session, networkId, SYNC_DISCONNECT_NETWORK_SLOT);
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
              QuasselCoreNetworkCreateRequest req =
                  QuasselCoreNetworkRequests.normalizeCreateRequest(
                      Objects.requireNonNull(request, "request"));
              log.debug(
                  "Quassel create network preflight: serverId={}, requestedIdentityId={}, knownIdentityIds={}, identityStateKeys={}, identityNames={}, authNetworkIds={}",
                  sid,
                  req.identityId(),
                  session.identities.knownIds(),
                  session.identities.stateIds(),
                  session.identities.names(),
                  Optional.ofNullable(session.authResult.get())
                      .map(QuasselCoreAuthHandshake.AuthResult::networkIds)
                      .orElse(List.of()));
              coordinateNetworkCreation(session, req);
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
              int networkId =
                  resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel update network");
              QuasselCoreNetworkUpdateRequest req =
                  QuasselCoreNetworkRequests.normalizeUpdateRequest(
                      Objects.requireNonNull(request, "request"));
              sendUpdateNetworkRequest(session, networkId, req);
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
              int networkId =
                  resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel remove network");
              sendRemoveNetworkRequest(session, networkId);
              forgetKnownNetwork(session, networkId);
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
    String nick = Objects.toString(newNick, "").trim();
    if (nick.isEmpty()) {
      return Completable.error(new IllegalArgumentException("new nick is blank"));
    }
    if (containsCrlf(nick)) {
      return Completable.error(new IllegalArgumentException("new nick contains CR/LF"));
    }
    return sendStatusInput(serverId, "change nick", "/NICK " + nick);
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    String message = Objects.toString(awayMessage, "").trim();
    if (containsCrlf(message)) {
      return Completable.error(new IllegalArgumentException("away message contains CR/LF"));
    }
    return sendStatusInput(
        serverId, "set away", message.isEmpty() ? "/AWAY" : ("/AWAY " + message));
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    String chan = Objects.toString(channel, "").trim();
    if (chan.isEmpty()) {
      return Completable.error(new IllegalArgumentException("channel is blank"));
    }
    if (containsCrlf(chan)) {
      return Completable.error(new IllegalArgumentException("channel contains CR/LF"));
    }
    return sendTargetInput(serverId, "request names", chan, BUFFER_CHANNEL, "/NAMES " + chan);
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    String chan = Objects.toString(channel, "").trim();
    if (chan.isEmpty()) {
      return Completable.error(new IllegalArgumentException("channel is blank"));
    }
    if (containsCrlf(chan)) {
      return Completable.error(new IllegalArgumentException("channel contains CR/LF"));
    }
    return sendStatusInput(serverId, "join channel", "/JOIN " + chan);
  }

  @Override
  public Completable whois(String serverId, String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) {
      return Completable.error(new IllegalArgumentException("nick is blank"));
    }
    if (containsCrlf(n)) {
      return Completable.error(new IllegalArgumentException("nick contains CR/LF"));
    }
    return sendTargetInput(serverId, "whois", n, BUFFER_QUERY, "/WHOIS " + n);
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    String chan = Objects.toString(channel, "").trim();
    if (chan.isEmpty()) {
      return Completable.error(new IllegalArgumentException("channel is blank"));
    }
    String text = Objects.toString(reason, "").trim();
    if (containsCrlf(chan) || containsCrlf(text)) {
      return Completable.error(new IllegalArgumentException("part parameters contain CR/LF"));
    }
    String command = text.isEmpty() ? ("/PART " + chan) : ("/PART " + chan + " " + text);
    return sendTargetInput(serverId, "part channel", chan, BUFFER_CHANNEL, command);
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    String chan = Objects.toString(channel, "").trim();
    String text = Objects.toString(message, "").trim();
    if (chan.isEmpty()) {
      return Completable.error(new IllegalArgumentException("channel is blank"));
    }
    if (text.isEmpty()) {
      return Completable.error(new IllegalArgumentException("message is blank"));
    }
    if (containsCrlf(chan) || containsCrlf(text)) {
      return Completable.error(new IllegalArgumentException("message parameters contain CR/LF"));
    }
    return sendTargetInput(serverId, "send message to channel", chan, BUFFER_CHANNEL, text);
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    String target = Objects.toString(nick, "").trim();
    String text = Objects.toString(message, "").trim();
    if (target.isEmpty()) {
      return Completable.error(new IllegalArgumentException("nick is blank"));
    }
    if (text.isEmpty()) {
      return Completable.error(new IllegalArgumentException("message is blank"));
    }
    if (containsCrlf(target) || containsCrlf(text)) {
      return Completable.error(new IllegalArgumentException("message parameters contain CR/LF"));
    }
    return sendTargetInput(serverId, "send private message", target, BUFFER_QUERY, text);
  }

  @Override
  public Completable sendNoticeToChannel(String serverId, String channel, String message) {
    String chan = Objects.toString(channel, "").trim();
    String text = Objects.toString(message, "").trim();
    if (chan.isEmpty()) {
      return Completable.error(new IllegalArgumentException("channel is blank"));
    }
    if (text.isEmpty()) {
      return Completable.error(new IllegalArgumentException("message is blank"));
    }
    if (containsCrlf(chan) || containsCrlf(text)) {
      return Completable.error(new IllegalArgumentException("notice parameters contain CR/LF"));
    }
    return sendTargetInput(
        serverId, "send notice to channel", chan, BUFFER_CHANNEL, "/NOTICE " + chan + " " + text);
  }

  @Override
  public Completable sendNoticePrivate(String serverId, String nick, String message) {
    String target = Objects.toString(nick, "").trim();
    String text = Objects.toString(message, "").trim();
    if (target.isEmpty()) {
      return Completable.error(new IllegalArgumentException("nick is blank"));
    }
    if (text.isEmpty()) {
      return Completable.error(new IllegalArgumentException("message is blank"));
    }
    if (containsCrlf(target) || containsCrlf(text)) {
      return Completable.error(new IllegalArgumentException("notice parameters contain CR/LF"));
    }
    return sendTargetInput(
        serverId, "send notice", target, BUFFER_QUERY, "/NOTICE " + target + " " + text);
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
              sendRawInternal(session, sid, "send raw", raw);
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
                sendRawInternal(session, sid, "send typing", rawLine);
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
                  resolveOutboundBufferInfo(session, typeBitsHint, requested);
              noteTargetNetworkHint(session, requested.baseTarget(), bufferInfo.networkId(), true);

              Instant at = markerAt == null ? Instant.now() : markerAt;
              String markerTarget =
                  historyTargetForBuffer(session, bufferInfo, requested.baseTarget());
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
                sendRawInternal(session, sid, "send read marker", rawLine);
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit) {
    return Completable.fromAction(
            () -> {
              Instant before = beforeExclusive == null ? Instant.now() : beforeExclusive;
              Ircv3ChatHistoryRuntimeSupport.Plan plan =
                  ircv3RuntimeSupport.chatHistoryBefore(target, "", limit, before);
              HistoryRequestContext ctx =
                  prepareHistoryRequest(
                      serverId, plan.target(), plan.limit(), "request chat history");
              HistorySelector parsed =
                  QuasselCoreHistorySupport.parseHistorySelector(plan.primarySelector(), false);
              int lastMsgId =
                  resolveHistoryBoundaryMsgId(ctx.session(), ctx.target(), parsed, false);
              sendBacklogRequest(
                  ctx.session(), ctx.bufferInfo(), UNKNOWN_MSG_ID, lastMsgId, ctx.limit());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    return Completable.fromAction(
            () -> {
              Ircv3ChatHistoryRuntimeSupport.Plan plan =
                  ircv3RuntimeSupport.chatHistoryBefore(target, selector, limit, Instant.now());
              HistoryRequestContext ctx =
                  prepareHistoryRequest(
                      serverId, plan.target(), plan.limit(), "request chat history");
              HistorySelector parsed =
                  QuasselCoreHistorySupport.parseHistorySelector(plan.primarySelector(), false);
              int lastMsgId =
                  resolveHistoryBoundaryMsgId(ctx.session(), ctx.target(), parsed, false);
              sendBacklogRequest(
                  ctx.session(), ctx.bufferInfo(), UNKNOWN_MSG_ID, lastMsgId, ctx.limit());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    return Completable.fromAction(
            () -> {
              Ircv3ChatHistoryRuntimeSupport.Plan plan =
                  ircv3RuntimeSupport.chatHistoryLatest(target, selector, limit);
              HistoryRequestContext ctx =
                  prepareHistoryRequest(
                      serverId, plan.target(), plan.limit(), "request latest chat history");
              HistorySelector parsed =
                  QuasselCoreHistorySupport.parseHistorySelector(plan.primarySelector(), true);

              int firstMsgId =
                  resolveHistoryBoundaryMsgId(ctx.session(), ctx.target(), parsed, true);
              sendBacklogRequest(
                  ctx.session(), ctx.bufferInfo(), firstMsgId, UNKNOWN_MSG_ID, ctx.limit());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    return Completable.fromAction(
            () -> {
              Ircv3ChatHistoryRuntimeSupport.Plan plan =
                  ircv3RuntimeSupport.chatHistoryBetween(target, startSelector, endSelector, limit);
              HistoryRequestContext ctx =
                  prepareHistoryRequest(
                      serverId, plan.target(), plan.limit(), "request bounded chat history");
              HistorySelector start =
                  QuasselCoreHistorySupport.parseHistorySelector(plan.primarySelector(), true);
              HistorySelector end =
                  QuasselCoreHistorySupport.parseHistorySelector(plan.secondarySelector(), true);

              int startMsgId =
                  resolveHistoryBoundaryMsgId(ctx.session(), ctx.target(), start, false);
              int endMsgId = resolveHistoryBoundaryMsgId(ctx.session(), ctx.target(), end, false);
              boolean reversed =
                  start.kind() == HistorySelectorKind.TIMESTAMP
                          && end.kind() == HistorySelectorKind.TIMESTAMP
                      ? start.timestamp().isAfter(end.timestamp())
                      : startMsgId > 0 && endMsgId > 0 && startMsgId > endMsgId;
              HistorySelector lower = reversed ? end : start;
              HistorySelector upper = reversed ? start : end;
              int firstMsgId =
                  resolveHistoryBoundaryMsgId(ctx.session(), ctx.target(), lower, true);
              int lastMsgId =
                  resolveHistoryBoundaryMsgId(ctx.session(), ctx.target(), upper, false);

              sendBacklogRequest(
                  ctx.session(), ctx.bufferInfo(), firstMsgId, lastMsgId, ctx.limit());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return Completable.fromAction(
            () -> {
              Ircv3ChatHistoryRuntimeSupport.Plan plan =
                  ircv3RuntimeSupport.chatHistoryAround(target, selector, limit);
              HistoryRequestContext ctx =
                  prepareHistoryRequest(
                      serverId, plan.target(), plan.limit(), "request surrounding chat history");
              HistorySelector parsed =
                  QuasselCoreHistorySupport.parseHistorySelector(plan.primarySelector(), false);
              long anchorMsgId = resolveHistorySelectorMsgId(ctx.session(), ctx.target(), parsed);

              int firstMsgId = UNKNOWN_MSG_ID;
              int lastMsgId = UNKNOWN_MSG_ID;
              if (anchorMsgId > 0) {
                int halfWindow = Math.max(1, ctx.limit() / 2);
                firstMsgId =
                    QuasselCoreHistorySupport.clampMsgId(Math.max(1L, anchorMsgId - halfWindow));
                lastMsgId = QuasselCoreHistorySupport.clampMsgId(anchorMsgId + halfWindow);
              }

              sendBacklogRequest(
                  ctx.session(), ctx.bufferInfo(), firstMsgId, lastMsgId, ctx.limit());
            })
        .subscribeOn(RxVirtualSchedulers.io());
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

  private HistoryRequestContext prepareHistoryRequest(
      String serverId, String target, int limit, String operation)
      throws BackendNotAvailableException {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      throw new IllegalArgumentException(SERVER_ID_BLANK);
    }
    QualifiedTarget tgt = sanitizeHistoryTarget(target);
    int lim = QuasselCoreHistorySupport.normalizeHistoryLimit(limit);
    QuasselCoreSession session = requireEstablishedSession(sid, operation);
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
        resolveHistoryBuffer(session, sid, operation, tgt);
    return new HistoryRequestContext(session, tgt.rawTarget(), bufferInfo, lim);
  }

  private QuasselCoreDatastreamCodec.BufferInfoValue resolveHistoryBuffer(
      QuasselCoreSession session, String serverId, String operation, QualifiedTarget target)
      throws BackendNotAvailableException {
    if (target == null) {
      throw new IllegalArgumentException("target is blank");
    }
    int typeBitsHint = looksLikeChannel(target.baseTarget()) ? BUFFER_CHANNEL : BUFFER_QUERY;
    int preferredNetworkId =
        preferredNetworkIdForTarget(session, target.baseTarget(), target.networkToken());
    QuasselCoreDatastreamCodec.BufferInfoValue byName =
        session.buffers.findByName(target.baseTarget(), typeBitsHint, preferredNetworkId);
    if (byName != null && byName.bufferId() >= 0) {
      return byName;
    }
    throw new BackendNotAvailableException(
        IrcProperties.Server.Backend.QUASSEL_CORE,
        operation,
        serverId,
        "target buffer '" + target.baseTarget() + "' is not known yet");
  }

  private void sendBacklogRequest(
      QuasselCoreSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      int firstMsgId,
      int lastMsgId,
      int limit)
      throws Exception {
    session.bufferCommands.requestBacklog(bufferInfo, firstMsgId, lastMsgId, limit);
  }

  private long resolveHistorySelectorMsgId(
      QuasselCoreSession session, String target, HistorySelector selector) {
    if (selector == null) return UNKNOWN_MSG_ID;
    return switch (selector.kind()) {
      case WILDCARD -> UNKNOWN_MSG_ID;
      case MSGID -> selector.msgId();
      case TIMESTAMP -> resolveHistoryMsgIdByTimestamp(session, target, selector.timestamp());
    };
  }

  private int resolveHistoryBoundaryMsgId(
      QuasselCoreSession session, String target, HistorySelector selector, boolean lowerBound) {
    long boundary =
        switch (selector.kind()) {
          case WILDCARD -> UNKNOWN_MSG_ID;
          // Core's lower ID bound is inclusive, whereas both history selectors are exclusive.
          case MSGID -> lowerBound ? selector.msgId() + 1L : selector.msgId();
          case TIMESTAMP ->
              lowerBound
                  ? session.history.firstMsgIdAfterTimestamp(target, selector.timestamp())
                  : session.history.firstMsgIdAtOrAfterTimestamp(target, selector.timestamp());
        };
    return QuasselCoreHistorySupport.clampMsgId(boundary);
  }

  private long resolveHistoryMsgIdByTimestamp(
      QuasselCoreSession session, String target, Instant timestamp) {
    return session == null ? UNKNOWN_MSG_ID : session.history.msgIdForTimestamp(target, timestamp);
  }

  private long resolveHistoryTimestampByMsgId(
      QuasselCoreSession session, String target, long msgId) {
    return session == null ? UNKNOWN_MSG_ID : session.history.exactTimestampForMsgId(target, msgId);
  }

  private void noteHistoryObservation(
      QuasselCoreSession session, String target, long messageId, Instant at) {
    if (session == null) return;
    session.history.observe(target, messageId, at);
    Integer bufferId = session.pendingReadMarkers.takeBufferForMessage(messageId);
    if (bufferId != null) {
      emitReadMarkerObserved(session, bufferId, messageId, at);
    }
  }

  private void noteTargetNetworkHint(
      QuasselCoreSession session, String target, int networkId, boolean preferObservedNetwork) {
    if (session == null || networkId < 0) return;
    observeKnownNetwork(session, networkId, "");
    if (preferObservedNetwork) {
      session.targetNetworkHints.observe(target, networkId);
    } else {
      session.targetNetworkHints.seed(target, networkId, () -> firstKnownNetworkId(session));
    }
  }

  private int preferredNetworkIdForTarget(
      QuasselCoreSession session, String target, String networkToken) {
    if (session == null) return -1;
    String token = Objects.toString(networkToken, "").trim().toLowerCase(Locale.ROOT);
    if (!token.isEmpty()) {
      Integer byToken = session.networks.idForToken(token);
      if (byToken != null && byToken.intValue() >= 0) {
        return byToken.intValue();
      }
    }
    int hinted = session.targetNetworkHints.networkIdForTarget(target);
    if (hinted >= 0) return hinted;
    return firstKnownNetworkId(session);
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
                  Objects.toString(session.currentNick.get(), ""))));

      session.probeSelection.set(probe);
      session.phase.set(QuasselSessionPhase.PROTOCOL_NEGOTIATED);
      emitConnectionPhase(session, PHASE_PROTOCOL_NEGOTIATED, renderProbeSource(probe));

      session.phase.set(QuasselSessionPhase.AUTHENTICATING);
      QuasselCoreAuthHandshake.AuthResult auth = authHandshake.authenticate(openedSocket, server);
      session.initialize(
          auth,
          id -> observeKnownNetwork(session, id, ""),
          info -> noteTargetNetworkHint(session, info.bufferName(), info.networkId(), false));
      session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
      availabilityReasonByServer.remove(sid);
      pendingSetupByServer.remove(sid);
      reconnects.reset(sid);
      reconnects.cancel(sid, false);

      sendSignalProxyInitRequest(session, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT);
      for (Integer networkId : collectKnownNetworkIds(session)) {
        requestNetworkInitState(session, networkId);
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
      QuasselCoreSyncDispatcher sync = syncDispatcher(session);
      QuasselCoreRpcDispatcher rpc = rpcDispatcher(session);
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

  private QuasselCoreRpcDispatcher rpcDispatcher(QuasselCoreSession session) {
    QuasselCoreNetworkLifecycleTranslator networks =
        new QuasselCoreNetworkLifecycleTranslator(
            session.serverId,
            session.networks::claimCreatedName,
            (networkId, name) -> observeKnownNetwork(session, networkId, name),
            networkId -> forgetKnownNetwork(session, networkId),
            (networkId, state) -> observeFullNetworkState(session, networkId, state));
    return new QuasselCoreRpcDispatcher(
        session,
        networks,
        new QuasselCoreRpcDispatcher.SessionPort() {
          @Override
          public void displayMessage(QuasselCoreDatastreamCodec.MessageValue message) {
            handleDisplayMessage(session, message);
          }

          @Override
          public void observeBuffer(QuasselCoreDatastreamCodec.BufferInfoValue merged) {
            observeKnownNetwork(session, merged.networkId(), "");
            noteTargetNetworkHint(session, merged.bufferName(), merged.networkId(), false);
            emitJoinedChannelFromBufferInfoIfNetworkConnected(session, merged);
          }

          @Override
          public void emit(IrcEvent event) {
            bus.onNext(new ServerIrcEvent(session.serverId, event));
          }
        });
  }

  private QuasselCoreSyncDispatcher syncDispatcher(QuasselCoreSession session) {
    return new QuasselCoreSyncDispatcher(
        session,
        networkSyncTranslator(session),
        new QuasselCoreSyncDispatcher.SessionPort() {
          @Override
          public void applyBufferInfoSnapshot(List<Object> values) {
            QuasselCoreIrcClientService.this.applyBufferInfoSnapshot(session, values);
          }

          @Override
          public void observeReadMarkers(String slotName, List<Object> values) {
            handleBufferSyncerSync(session, slotName, values);
          }

          @Override
          public void receiveBacklog(List<Object> values) {
            handleBacklogSync(session, values);
          }

          @Override
          public void observeNetwork(int networkId, String name) {
            observeKnownNetwork(session, networkId, name);
          }

          @Override
          public String qualifyTarget(String target, int networkId) {
            return qualifyTargetForNetwork(session, target, networkId);
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
        (networkId, name) -> observeKnownNetwork(session, networkId, name),
        (networkId, state) -> observeNetworkStateSnapshot(session, networkId, state),
        (networkId, state) -> observeFullNetworkState(session, networkId, state),
        (networkId, nick) -> observeCurrentNick(session, networkId, nick, Instant.now()));
  }

  private void observeFullNetworkState(QuasselCoreSession session, int networkId, Map<?, ?> state) {
    observeNetworkStateSnapshot(session, networkId, state);
    observeNetworkCapabilities(session, networkId, state);
    observeNetworkMonitorSupport(session, networkId, state);
  }

  private void applyBufferInfoSnapshot(QuasselCoreSession session, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    ArrayList<QuasselCoreDatastreamCodec.BufferInfoValue> found = new ArrayList<>();
    for (Object value : values) {
      collectBufferInfos(value, found);
    }
    if (found.isEmpty()) return;
    for (QuasselCoreDatastreamCodec.BufferInfoValue info : found) {
      if (info == null || info.bufferId() < 0) continue;
      QuasselCoreDatastreamCodec.BufferInfoValue merged = session.buffers.merge(info);
      observeKnownNetwork(session, merged.networkId(), "");
      noteTargetNetworkHint(session, merged.bufferName(), merged.networkId(), false);
      emitJoinedChannelFromBufferInfoIfNetworkConnected(session, merged);
    }
  }

  private static void collectBufferInfos(
      Object raw, List<QuasselCoreDatastreamCodec.BufferInfoValue> out) {
    if (raw == null || out == null) return;
    if (raw instanceof QuasselCoreDatastreamCodec.BufferInfoValue info) {
      out.add(info);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectBufferInfos(value, out);
      }
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        collectBufferInfos(value, out);
      }
    }
  }

  private void handleBufferSyncerSync(
      QuasselCoreSession session, String slotName, List<Object> values) {
    if (session == null || values == null || values.isEmpty()) return;
    Instant now = Instant.now();
    for (ReadMarkerUpdate update :
        QuasselCoreBufferSyncerParser.parseReadMarkers(slotName, values)) {
      emitReadMarkerObserved(session, update.bufferId(), update.msgId(), now);
    }
  }

  private void emitReadMarkerObserved(
      QuasselCoreSession session, int bufferId, long markerMsgId, Instant at) {
    if (session == null || bufferId < 0 || markerMsgId <= 0L) return;
    // Core init-data arrives before backlog. Keep the native ID until its timestamp is known.
    session.pendingReadMarkers.forgetBuffer(bufferId);
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo = session.buffers.get(bufferId);
    if (bufferInfo == null) {
      session.pendingReadMarkers.defer(bufferId, markerMsgId);
      return;
    }

    int networkId = bufferInfo.networkId();
    String from = currentNickForNetwork(session, networkId);
    if (from.isBlank()) {
      from = "server";
    }
    String target = historyTargetForBuffer(session, bufferInfo, from);
    if (target.isBlank()) {
      target = qualifyTargetForNetwork(session, normalizedBufferName(bufferInfo), networkId);
    }
    if (target.isBlank()) return;

    noteTargetNetworkHint(session, target, networkId, true);
    Instant fallback = at == null ? Instant.now() : at;
    long resolvedEpochMs = resolveHistoryTimestampByMsgId(session, target, markerMsgId);
    if (resolvedEpochMs <= 0L) {
      session.pendingReadMarkers.defer(bufferId, markerMsgId);
      return;
    }
    String marker =
        Ircv3ChatHistorySelectors.TIMESTAMP_PREFIX
            + Ircv3ReadMarkerCommandBuilder.formatTimestamp(Instant.ofEpochMilli(resolvedEpochMs));
    bus.onNext(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.ReadMarkerObserved(fallback, from, target, marker)));
  }

  private void observeNetworkCapabilities(
      QuasselCoreSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeCapabilities(resolvedNetworkId, stateMap);
  }

  private void observeNetworkMonitorSupport(
      QuasselCoreSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeMonitor(resolvedNetworkId, stateMap);
  }

  private void handleBacklogSync(QuasselCoreSession session, List<Object> values) {
    IrcEvent.ChatHistoryBatchReceived batch =
        session.backlog.sync(
            values,
            session.buffers::get,
            info -> resolveBufferInfo(session, info),
            (info, from) -> historyTargetForBuffer(session, info, from),
            (target, networkId) -> noteTargetNetworkHint(session, target, networkId, true),
            (message, entry) ->
                noteHistoryObservation(session, entry.target(), message.messageId(), entry.at()));
    if (batch != null) bus.onNext(new ServerIrcEvent(session.serverId, batch));
  }

  private void handleDisplayMessage(
      QuasselCoreSession session, QuasselCoreDatastreamCodec.MessageValue message) {
    if (message == null) return;

    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
        resolveBufferInfo(session, message.bufferInfo());
    Instant at =
        message.timestampEpochSeconds() > 0
            ? Instant.ofEpochSecond(message.timestampEpochSeconds())
            : Instant.now();
    String messageId = message.messageId() > 0 ? Long.toString(message.messageId()) : "";
    String senderHostmask = Objects.toString(message.sender(), "").trim();
    String from = extractNick(senderHostmask);
    int networkId = bufferInfo == null ? -1 : bufferInfo.networkId();
    String fromDisplay = from.isEmpty() ? currentNickForNetwork(session, networkId) : from;
    String content = Objects.toString(message.content(), "");
    QuasselCoreIrcEnvelope ircEnvelope = QuasselCoreIrcEnvelope.parse(content, ircv3RuntimeSupport);
    Map<String, String> ircv3Tags = ircEnvelope.ircv3Tags();
    String payloadText = ircEnvelope.payloadText(content);
    String target = targetForBuffer(session, bufferInfo, fromDisplay);
    String historyTarget = historyTargetForBuffer(session, bufferInfo, fromDisplay);
    int historyNetworkId = networkId;
    noteTargetNetworkHint(session, historyTarget, historyNetworkId, true);
    noteHistoryObservation(session, historyTarget, message.messageId(), at);
    int typeBits = message.typeBits();
    String fallbackSignalTarget = target;
    Function<String, String> resolveSignalTarget =
        rawTarget ->
            resolveSignalTargetForRawTarget(
                session, fromDisplay, fallbackSignalTarget, networkId, rawTarget);
    Consumer<IrcEvent> emit = event -> bus.onNext(new ServerIrcEvent(session.serverId, event));
    QuasselCoreIrcv3InboundTranslator.Observation observation =
        new QuasselCoreIrcv3InboundTranslator.Observation(at, fromDisplay, ircEnvelope, messageId);
    inboundTranslator.observeTags(observation, resolveSignalTarget, emit);

    String envelopeCommand = ircEnvelope.command();
    if ("CAP".equals(envelopeCommand)) {
      emitCapabilityChangesFromCapLine(session, at, networkId, ircEnvelope);
    }
    if (inboundTranslator.handleCommand(observation, resolveSignalTarget, emit)) {
      return;
    }
    if ("TAGMSG".equals(envelopeCommand) && payloadText.isBlank()) {
      return;
    }

    if (inboundTranslator.handleMonitor(
        at,
        content,
        support -> {
          int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
          session.features.observeMonitor(resolvedNetworkId, support.supported(), support.limit());
        },
        emit)) {
      return;
    }

    if (isBacklogMessage(message.flags()) && isHistoryTextMessage(typeBits)) {
      emitBacklogHistoryBatch(session, at, target, message, messageId);
      return;
    }

    QuasselCoreDisplayMessageTranslator.translate(
        new QuasselCoreDisplayMessageTranslator.Observation(
            at,
            bufferInfo,
            target,
            fromDisplay,
            senderHostmask,
            payloadText,
            messageId,
            ircv3Tags,
            message),
        new QuasselCoreDisplayMessageTranslator.SessionPort() {
          @Override
          public boolean isSelfNick(String nick) {
            return QuasselCoreIrcClientService.isSelfNick(session, nick, networkId);
          }

          @Override
          public String currentNick() {
            return currentNickForNetwork(session, networkId);
          }

          @Override
          public String queryTarget() {
            return targetForBuffer(session, bufferInfo, fromDisplay);
          }

          @Override
          public void observeJoin(Instant joinedAt, String channel) {
            session.membership.observeJoin(joinedAt, channel, networkId);
          }

          @Override
          public void leave(String channel) {
            session.membership.leave(channel, networkId);
          }

          @Override
          public void observeNick(Instant changedAt, String nick) {
            observeCurrentNick(session, networkId, nick, changedAt);
          }
        },
        emit);
  }

  private void emitCapabilityChangesFromCapLine(
      QuasselCoreSession session, Instant at, int networkId, QuasselCoreIrcEnvelope envelope) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeCapLine(at, resolvedNetworkId, envelope);
  }

  private String resolveSignalTargetForRawTarget(
      QuasselCoreSession session,
      String fromDisplay,
      String fallbackTarget,
      int networkId,
      String rawTarget) {
    String fallback = Objects.toString(fallbackTarget, "").trim();
    String hint = stripLeadingColon(rawTarget);
    if (hint.isBlank()) {
      return fallback;
    }
    QualifiedTarget parsed = parseQualifiedTarget(hint);
    String base = parsed.baseTarget();
    if (base.isBlank()) {
      return fallback;
    }
    if (isSelfNick(session, base, networkId)) {
      String from = Objects.toString(fromDisplay, "").trim();
      if (!from.isBlank()) {
        base = from;
      }
    }
    if (!parsed.networkToken().isBlank()) {
      return parsed.rawTarget();
    }
    return qualifyTargetForNetwork(session, base, networkId);
  }

  private void emitBacklogHistoryBatch(
      QuasselCoreSession session,
      Instant at,
      String targetFromBuffer,
      QuasselCoreDatastreamCodec.MessageValue message,
      String messageId) {
    IrcEvent.ChatHistoryBatchReceived batch =
        session.backlog.display(
            at,
            targetFromBuffer,
            message,
            messageId,
            (info, from) -> historyTargetForBuffer(session, info, from));
    if (batch != null) bus.onNext(new ServerIrcEvent(session.serverId, batch));
  }

  private QuasselCoreDatastreamCodec.BufferInfoValue resolveBufferInfo(
      QuasselCoreSession session, QuasselCoreDatastreamCodec.BufferInfoValue incoming) {
    if (incoming == null) {
      int networkId = primaryNetworkId(session);
      return new QuasselCoreDatastreamCodec.BufferInfoValue(-1, networkId, BUFFER_STATUS, -1, "");
    }

    if (incoming.bufferId() < 0) {
      return incoming;
    }

    QuasselCoreDatastreamCodec.BufferInfoValue merged = session.buffers.resolve(incoming);
    observeKnownNetwork(session, merged.networkId(), "");
    return merged;
  }

  private static String normalizedBufferName(
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {
    if (bufferInfo == null) return "";
    return Objects.toString(bufferInfo.bufferName(), "").trim();
  }

  private static boolean isChannelBuffer(QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {
    if (bufferInfo == null) return false;
    return (bufferInfo.typeBits() & BUFFER_CHANNEL) != 0;
  }

  private static boolean isQueryBuffer(QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {
    if (bufferInfo == null) return false;
    return (bufferInfo.typeBits() & BUFFER_QUERY) != 0;
  }

  private static boolean isStatusBuffer(QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {
    if (bufferInfo == null) return false;
    return (bufferInfo.typeBits() & BUFFER_STATUS) != 0;
  }

  private static boolean isBacklogMessage(int flags) {
    return (flags & MESSAGE_FLAG_BACKLOG) != 0;
  }

  private String targetForBuffer(
      QuasselCoreSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      String fallbackFromNick) {
    return qualifiedTargetForBuffer(session, bufferInfo, fallbackFromNick);
  }

  private String historyTargetForBuffer(
      QuasselCoreSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      String fallbackFromNick) {
    return qualifiedTargetForBuffer(session, bufferInfo, fallbackFromNick);
  }

  private String qualifiedTargetForBuffer(
      QuasselCoreSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      String fallbackFromNick) {
    boolean channelBuffer = isChannelBuffer(bufferInfo);
    boolean queryBuffer = isQueryBuffer(bufferInfo);
    if (!channelBuffer && !queryBuffer && isStatusBuffer(bufferInfo)) {
      return "status";
    }
    String base = normalizedBufferName(bufferInfo);
    if (base.isEmpty() && queryBuffer) {
      base = Objects.toString(fallbackFromNick, "").trim();
    }
    if (base.isEmpty()) return "";
    if (!channelBuffer && !queryBuffer) {
      return base;
    }
    int networkId = bufferInfo == null ? -1 : bufferInfo.networkId();
    return qualifyTargetForNetwork(session, base, networkId);
  }

  private static String currentNickForPrimaryNetwork(QuasselCoreSession session) {
    if (session == null) return "";
    int primary = primaryNetworkId(session);
    if (primary >= 0) {
      String byNetwork =
          Objects.toString(session.networkCurrentNickByNetworkId.get(primary), "").trim();
      if (!byNetwork.isEmpty()) return byNetwork;
    }
    return Objects.toString(session.currentNick.get(), "").trim();
  }

  private static String currentNickForNetwork(QuasselCoreSession session, int networkId) {
    if (session == null) return "";
    if (networkId >= 0) {
      String byNetwork =
          Objects.toString(session.networkCurrentNickByNetworkId.get(networkId), "").trim();
      if (!byNetwork.isEmpty()) return byNetwork;
    }
    return currentNickForPrimaryNetwork(session);
  }

  private void observeCurrentNick(
      QuasselCoreSession session, int networkId, String nextNick, Instant at) {
    if (session == null) return;
    String next = Objects.toString(nextNick, "").trim();
    if (next.isEmpty()) return;

    if (networkId >= 0) {
      session.networkCurrentNickByNetworkId.put(networkId, next);
      trimMapToMaxSize(session.networkCurrentNickByNetworkId, MAX_NETWORK_NICKS_PER_SESSION);
    }

    int primaryNetworkId = primaryNetworkId(session);
    if (networkId >= 0 && primaryNetworkId >= 0 && networkId != primaryNetworkId) {
      return;
    }

    String oldNick = Objects.toString(session.currentNick.getAndSet(next), "").trim();
    if (!oldNick.isEmpty() && !oldNick.equalsIgnoreCase(next)) {
      bus.onNext(new ServerIrcEvent(session.serverId, new IrcEvent.NickChanged(at, oldNick, next)));
    }
  }

  private static int primaryNetworkId(QuasselCoreSession session) {
    return session == null
        ? -1
        : session.networks.primaryNetworkId(session.authResult.get(), session.buffers.values());
  }

  private void observeKnownNetwork(QuasselCoreSession session, int networkId, String networkName) {
    if (session == null || networkId < 0) return;
    session.networks.observe(networkId, networkName);
    emitQuasselNetworkSnapshotEvent(session, "observe-known-network");
  }

  private void forgetKnownNetwork(QuasselCoreSession session, int networkId) {
    if (session == null || networkId < 0) return;
    session.networks.forget(networkId);
    session.networkCurrentNickByNetworkId.remove(networkId);
    session.features.removeNetwork(networkId);
    session.buffers.forgetNetwork(networkId, session.pendingReadMarkers::forgetBuffer);
    session.targetNetworkHints.forgetNetwork(networkId);
    session.membership.forgetNetwork(networkId);
    emitQuasselNetworkSnapshotEvent(session, "forget-known-network");
  }

  private void observeNetworkStateSnapshot(
      QuasselCoreSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    Map<String, Object> merged = session.networks.observeState(networkId, stateMap);
    if (merged == null) return;
    session.membership.reconcileNetwork(
        networkId,
        parseNetworkConnected(merged),
        session.buffers.values(),
        (channel, id) -> qualifyTargetForNetwork(session, channel, id),
        (target, id) -> noteTargetNetworkHint(session, target, id, true));
    emitQuasselNetworkSnapshotEvent(session, "observe-network-state");
  }

  private void emitJoinedChannelFromBufferInfoIfNetworkConnected(
      QuasselCoreSession session, QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {
    if (session == null || bufferInfo == null) return;
    int networkId = bufferInfo.networkId();
    if (networkId < 0) return;
    Map<String, Object> state = session.networks.state(networkId);
    if (!parseNetworkConnected(state)) return;
    session.membership.observeBuffer(
        bufferInfo,
        Instant.now(),
        (channel, id) -> qualifyTargetForNetwork(session, channel, id),
        (target, id) -> noteTargetNetworkHint(session, target, id, true));
  }

  private static int firstKnownIdentityId(QuasselCoreSession session) {
    return session == null ? -1 : session.identities.firstKnownId();
  }

  private List<QuasselCoreNetworkSummary> snapshotQuasselCoreNetworks(QuasselCoreSession session) {
    return session == null
        ? List.of()
        : session.networks.snapshot(session.authResult.get(), session.buffers.values());
  }

  private void emitQuasselNetworkSnapshotEvent(QuasselCoreSession session, String source) {
    if (session == null) return;
    String sid = normalizeServerId(session.serverId);
    if (sid.isEmpty()) return;
    List<QuasselCoreNetworkSummary> snapshot = snapshotQuasselCoreNetworks(session);
    observations.observeNetwork(new QuasselCoreNetworkSnapshotEvent(sid, snapshot, source));
  }

  private static LinkedHashSet<Integer> collectKnownNetworkIds(QuasselCoreSession session) {
    return session == null
        ? new LinkedHashSet<>()
        : session.networks.knownIds(session.authResult.get(), session.buffers.values());
  }

  private int resolveQuasselNetworkId(
      QuasselCoreSession session, String serverId, String networkIdOrName, String operation) {
    if (session == null) throw new IllegalStateException("Quassel session is missing");
    int resolved = session.networks.resolve(networkIdOrName);
    if (resolved >= 0) return resolved;
    String raw = Objects.toString(networkIdOrName, "").trim();
    throw new BackendNotAvailableException(
        IrcProperties.Server.Backend.QUASSEL_CORE,
        operation,
        serverId,
        "unknown Quassel network '" + raw + "' (run /quasselnet list)");
  }

  private static int resolveQuasselIdentityId(
      QuasselCoreSession session, Integer requestedIdentityId) {
    if (requestedIdentityId != null && requestedIdentityId.intValue() > 0) {
      return requestedIdentityId.intValue();
    }
    if (session != null) {
      int observedIdentity = firstKnownIdentityId(session);
      if (observedIdentity >= 0) return observedIdentity;
      int primary = primaryNetworkId(session);
      if (primary >= 0) {
        Map<String, Object> state = session.networks.state(primary);
        int fromPrimary = parseNetworkIdentityId(state);
        if (fromPrimary >= 0) return fromPrimary;
      }
      for (Map<String, Object> state : session.networks.states()) {
        int parsed = parseNetworkIdentityId(state);
        if (parsed >= 0) return parsed;
      }
      log.debug(
          "Falling back to default Quassel identity id=1: serverId={}, requestedIdentityId={}, knownIdentityIds={}, identityStateKeys={}, networkStateKeys={}",
          session.serverId,
          requestedIdentityId,
          session.identities.knownIds(),
          session.identities.stateIds(),
          session.networks.stateIds());
    }
    return 1;
  }

  private String qualifyTargetForNetwork(
      QuasselCoreSession session, String baseTarget, int networkId) {
    String base = Objects.toString(baseTarget, "").trim();
    if (base.isEmpty()) return "";
    if (session == null || networkId < 0) return base;
    if (knownNetworkCount(session) <= 1) return base;
    String token = networkTokenForNetworkId(session, networkId);
    return QuasselCoreTargetRouting.qualifyTarget(base, token);
  }

  private static int knownNetworkCount(QuasselCoreSession session) {
    return collectKnownNetworkIds(session).size();
  }

  private String networkTokenForNetworkId(QuasselCoreSession session, int networkId) {
    if (session == null || networkId < 0) return "";
    String token = session.networks.token(networkId);
    if (!token.isEmpty()) return token;
    observeKnownNetwork(session, networkId, "");
    return session.networks.token(networkId);
  }

  private static boolean isSelfNick(QuasselCoreSession session, String nick, int networkId) {
    String candidate = Objects.toString(nick, "").trim();
    if (candidate.isEmpty()) return false;
    if (networkId >= 0) {
      String perNetwork =
          Objects.toString(session.networkCurrentNickByNetworkId.get(networkId), "").trim();
      if (!perNetwork.isEmpty() && perNetwork.equalsIgnoreCase(candidate)) {
        return true;
      }
    }
    String known = currentNickForPrimaryNetwork(session);
    return !known.isEmpty() && known.equalsIgnoreCase(candidate);
  }

  private record HistoryRequestContext(
      QuasselCoreSession session,
      String target,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      int limit) {}

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

  private static QuasselCoreNetworkCommandSender networkCommands(QuasselCoreSession session) {
    if (session == null) throw new IllegalStateException("Quassel session is missing");
    return session.networkCommands;
  }

  private void sendNetworkRequest(QuasselCoreSession session, int networkId, String slotName)
      throws Exception {
    networkCommands(session).syncNetwork(networkId, slotName);
  }

  private boolean maybeRepairNetworkIdentityBeforeConnect(QuasselCoreSession session, int networkId)
      throws Exception {
    if (session == null || networkId < 0) return true;
    return new QuasselCoreNetworkConnectPreflight(
            session.serverId,
            session.networks,
            session.identities,
            observations,
            () -> resolveQuasselIdentityId(session, null),
            () -> collectKnownNetworkIds(session),
            networkCommandMediator(session))
        .prepare(networkId);
  }

  private void requestNetworkInitState(QuasselCoreSession session, int networkId) throws Exception {
    if (session == null || networkId < 0) return;
    networkCommandMediator(session).requestNetworkInitState(networkId);
  }

  private void sendSignalProxyInitRequest(
      QuasselCoreSession session, String className, String objectName) throws Exception {
    if (session == null) return;
    session.networkCommands.requestInit(className, objectName);
  }

  private static QuasselCoreNetworkSummary findNetworkSummaryById(
      List<QuasselCoreNetworkSummary> networks, int networkId) {
    if (networks == null || networks.isEmpty() || networkId < 0) return null;
    for (QuasselCoreNetworkSummary summary : networks) {
      if (summary == null || summary.networkId() < 0) continue;
      if (summary.networkId() == networkId) return summary;
    }
    return null;
  }

  private void coordinateNetworkCreation(
      QuasselCoreSession session, QuasselCoreNetworkCreateRequest request) throws Exception {
    new QuasselCoreNetworkCreationCoordinator(
            session.serverId,
            session.initialNick,
            session.identities,
            session.networks,
            observations,
            requested -> resolveQuasselIdentityId(session, requested),
            () -> collectKnownNetworkIds(session),
            networkCommandMediator(session))
        .create(request);
  }

  private void sendUpdateNetworkRequest(
      QuasselCoreSession session, int networkId, QuasselCoreNetworkUpdateRequest request)
      throws Exception {
    networkCommandMediator(session).updateNetwork(networkId, request);
  }

  private QuasselCoreNetworkCommandMediator networkCommandMediator(QuasselCoreSession session) {
    if (session == null) throw new IllegalStateException("Quassel session is missing");
    return new QuasselCoreNetworkCommandMediator(
        session,
        () -> resolveQuasselIdentityId(session, null),
        (networkId, name) -> observeKnownNetwork(session, networkId, name));
  }

  private void sendRemoveNetworkRequest(QuasselCoreSession session, int networkId)
      throws Exception {
    networkCommands(session).removeNetwork(networkId);
  }

  private void sendInput(
      QuasselCoreSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      String userInput)
      throws Exception {
    session.bufferCommands.sendInput(bufferInfo, userInput);
  }

  private Completable sendStatusInput(String serverId, String operation, String command) {
    return sendInputWithBuffer(serverId, operation, BUFFER_STATUS, "", command);
  }

  private Completable sendTargetInput(
      String serverId, String operation, String target, int typeBits, String input) {
    return sendInputWithBuffer(serverId, operation, typeBits, target, input);
  }

  private Completable sendInputWithBuffer(
      String serverId, String operation, int typeBits, String bufferName, String input) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) throw new IllegalArgumentException(SERVER_ID_BLANK);

              QuasselCoreSession session = requireEstablishedSession(sid, operation);
              if (firstKnownNetworkId(session) < 0) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.QUASSEL_CORE,
                    operation,
                    sid,
                    "no active Quassel network is available yet");
              }

              QualifiedTarget requestedTarget = parseQualifiedTarget(bufferName);
              QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
                  resolveOutboundBufferInfo(session, typeBits, requestedTarget);
              noteTargetNetworkHint(
                  session, requestedTarget.baseTarget(), bufferInfo.networkId(), true);
              sendInput(session, bufferInfo, input);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private void sendRawInternal(
      QuasselCoreSession session, String serverId, String operation, String rawLine)
      throws Exception {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    if (firstKnownNetworkId(session) < 0) {
      throw new BackendNotAvailableException(
          IrcProperties.Server.Backend.QUASSEL_CORE,
          operation,
          serverId,
          "no active Quassel network is available yet");
    }

    OutboundRawRoute route = routeOutboundRawLine(rawLine);
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo;
    if (route.requestedTarget() == null) {
      bufferInfo = resolveOutboundBufferInfo(session, BUFFER_STATUS, parseQualifiedTarget(""));
    } else {
      bufferInfo =
          resolveOutboundBufferInfo(session, route.targetTypeBitsHint(), route.requestedTarget());
      noteTargetNetworkHint(
          session, route.requestedTarget().baseTarget(), bufferInfo.networkId(), true);
    }
    sendInput(session, bufferInfo, "/QUOTE " + route.rewrittenRawLine());
  }

  private void sendBufferSyncerReadMarkerUpdate(
      QuasselCoreSession session, int bufferId, long markerMsgId) throws Exception {
    if (session == null) return;
    session.bufferCommands.updateReadMarker(bufferId, markerMsgId);
  }

  private QuasselCoreDatastreamCodec.BufferInfoValue resolveOutboundBufferInfo(
      QuasselCoreSession session, int fallbackTypeBits, QualifiedTarget requestedTarget) {
    if (requestedTarget == null) {
      return new QuasselCoreDatastreamCodec.BufferInfoValue(
          -1, firstKnownNetworkId(session), fallbackTypeBits, -1, "");
    }
    String requestedName = requestedTarget.baseTarget();
    int preferredNetworkId =
        preferredNetworkIdForTarget(session, requestedName, requestedTarget.networkToken());
    QuasselCoreDatastreamCodec.BufferInfoValue byName =
        session.buffers.findByName(requestedName, fallbackTypeBits, preferredNetworkId);
    if (byName != null) {
      return byName;
    }

    int networkId = preferredNetworkId >= 0 ? preferredNetworkId : firstKnownNetworkId(session);
    return new QuasselCoreDatastreamCodec.BufferInfoValue(
        -1, networkId, fallbackTypeBits, -1, requestedName);
  }

  private static int firstKnownNetworkId(QuasselCoreSession session) {
    return session == null
        ? -1
        : session.networks.firstKnownNetworkId(session.authResult.get(), session.buffers.values());
  }
}
