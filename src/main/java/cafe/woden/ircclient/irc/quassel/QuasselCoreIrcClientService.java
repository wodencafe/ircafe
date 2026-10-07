package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isActionMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isHistoryTextMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isNoticeMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isPlainMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.extractNick;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.extractNumericCode;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.firstChannelToken;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.normalizeReason;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseKickDetails;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseModeDetails;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseNickChange;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.parseTopic;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.serverResponse;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.UNKNOWN_MSG_ID;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreLogSummary.summarizeNetworkInfoForLog;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkCreationCoordinator.RPC_CREATE_NETWORK_SLOT;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkCreationCoordinator.RPC_CREATE_NETWORK_SLOT_LEGACY;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.collectPotentialNetworkStateMaps;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.flattenNetworkStateFromKeyValueParams;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.networkIdFromStateMap;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkConnected;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkEnabled;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkIdentityId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.parseQualifiedTarget;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.routeOutboundRawLine;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.sanitizeHistoryTarget;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsCrlf;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.parseBoolean;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.stripLeadingColon;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.trimMapToMaxSize;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseInt;
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
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.quassel.QuasselCoreBufferSyncerParser.ReadMarkerUpdate;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.KickDetails;
import cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.HistorySelector;
import cafe.woden.ircclient.irc.quassel.QuasselCoreHistorySupport.HistorySelectorKind;
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.OutboundRawRoute;
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.QualifiedTarget;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.io.EOFException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
  private static final int MESSAGE_TYPE_NICK = 0x0008;
  private static final int MESSAGE_TYPE_MODE = 0x0010;
  private static final int MESSAGE_TYPE_JOIN = 0x0020;
  private static final int MESSAGE_TYPE_PART = 0x0040;
  private static final int MESSAGE_TYPE_QUIT = 0x0080;
  private static final int MESSAGE_TYPE_KICK = 0x0100;
  private static final int MESSAGE_TYPE_SERVER = 0x0400;
  private static final int MESSAGE_TYPE_INFO = 0x0800;
  private static final int MESSAGE_TYPE_ERROR = 0x1000;
  private static final int MESSAGE_TYPE_TOPIC = 0x4000;
  private static final int MESSAGE_TYPE_INVITE = 0x20000;
  private static final int MESSAGE_FLAG_BACKLOG = 0x80;
  private static final String NETWORK_CLASS = "Network";
  private static final String NETWORK_SET_INFO_SLOT = "requestSetNetworkInfo";
  private static final String BACKLOG_MANAGER_CLASS = "BacklogManager";
  private static final String BACKLOG_MANAGER_OBJECT = "";
  private static final String BACKLOG_REQUEST_SLOT = "requestBacklog";
  private static final String RPC_CREATE_IDENTITY_SLOT = "2createIdentity(Identity,QVariantMap)";
  private static final String SYNC_CONNECT_NETWORK_SLOT = "requestConnect";
  private static final String SYNC_DISCONNECT_NETWORK_SLOT = "requestDisconnect";
  private static final String RPC_REMOVE_NETWORK_SLOT = "2removeNetwork(NetworkId)";
  private static final String BUFFER_SYNCER_CLASS = "BufferSyncer";
  private static final String BUFFER_SYNCER_OBJECT = "";
  private static final String BUFFER_SYNCER_MARKER_SLOT = "requestSetMarkerLine";
  private static final String BUFFER_SYNCER_LAST_SEEN_SLOT = "requestSetLastSeenMsg";
  private static final int MAX_NETWORK_NICKS_PER_SESSION = 256;
  private static final int MAX_NETWORK_IDENTITIES_PER_SESSION = 512;
  private static final String BACKEND_UNAVAILABLE_REASON = "Quassel Core backend is not connected";
  private static final String HANDSHAKE_INCOMPLETE_REASON =
      "Quassel protocol negotiated, but login/session handshake is not complete";
  private static final String DEFAULT_DISCONNECT_REASON = "Client requested disconnect";
  private static final String FEATURE_PHASE_PREFIX = "quassel-phase=";
  private static final String FEATURE_DETAIL_PREFIX = ";detail=";
  private static final String PHASE_PROTOCOL_NEGOTIATED = "protocol-negotiated";
  private static final String PHASE_SYNC_READY = "sync-ready";
  private static final String PHASE_SETUP_REQUIRED = "setup-required";

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();
  private final QuasselCoreObservationMediator observations = new QuasselCoreObservationMediator();
  private final Map<String, QuasselSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> availabilityReasonByServer = new ConcurrentHashMap<>();
  private final Map<String, QuasselCoreSetupPrompt> pendingSetupByServer =
      new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  private final ServerCatalog serverCatalog;
  private final QuasselCoreSocketConnector socketConnector;
  private final QuasselCoreProtocolProbe protocolProbe;
  private final QuasselCoreAuthHandshake authHandshake;
  private final QuasselCoreDatastreamCodec datastreamCodec;
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
    this.datastreamCodec = Objects.requireNonNull(datastreamCodec, "datastreamCodec");
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
    for (Map.Entry<String, QuasselSession> entry : sessions.entrySet()) {
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
    QuasselSession session = sessions.get(normalizeServerId(serverId));
    if (session == null || session.socketRef.get() == null) return Optional.empty();
    String nick = currentNickForPrimaryNetwork(session);
    return nick.isEmpty() ? Optional.empty() : Optional.of(nick);
  }

  @Override
  public String backendAvailabilityReason(String serverId) {
    String sid = normalizeServerId(serverId);
    QuasselSession session = sessions.get(sid);
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
    QuasselSession session = sessions.get(sid);
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
              QuasselSession removed = sessions.remove(sid);
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
    QuasselSession session = sessions.get(sid);
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
              QuasselSession session = requireEstablishedSession(sid, "quassel connect network");
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
              QuasselSession session = requireEstablishedSession(sid, "quassel disconnect network");
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
              QuasselSession session = requireEstablishedSession(sid, "quassel create network");
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
              QuasselSession session = requireEstablishedSession(sid, "quassel update network");
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
              QuasselSession session = requireEstablishedSession(sid, "quassel remove network");
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
              QuasselSession existing = sessions.get(sid);
              if (existing != null) return;

              String nick = configuredNick(server);
              QuasselSession next =
                  new QuasselSession(
                      sid,
                      nick,
                      server.host(),
                      server.port(),
                      signalProxySender,
                      observations::observeIdentity,
                      event -> bus.onNext(new ServerIrcEvent(sid, event)));
              QuasselSession previous = sessions.putIfAbsent(sid, next);
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

              QuasselSession removed = sessions.remove(sid);
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

              QuasselSession session = requireEstablishedSession(sid, "send raw");
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
              QuasselSession session = requireEstablishedSession(sid, "send typing");
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
              QuasselSession session = requireEstablishedSession(sid, "send read marker");
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
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, MULTILINE, DRAFT_MULTILINE);
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null || !session.features.hasMultilineLimits()) return 0L;
    return session.features.multilineMaxBytes(primaryNetworkId(session));
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null || !session.features.hasMultilineLimits()) return 0;
    return session.features.multilineMaxLines(primaryNetworkId(session));
  }

  @Override
  public boolean isExperimentalMessageEditAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, DRAFT_MESSAGE_EDIT);
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, DRAFT_MESSAGE_REDACTION);
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null) return false;
    return hasCapability(session, MESSAGE_TAGS);
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
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
    QuasselSession session = findEstablishedSession(serverId);
    return session != null
        && (session.nativeReadMarkerSupportObserved.get()
            || hasCapability(session, READ_MARKER, DRAFT_READ_MARKER));
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, LABELED_RESPONSE);
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return hasCapability(session, STANDARD_REPLIES);
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return session != null
        && session.features.hasMonitorState()
        && session.features.monitorAvailable(primaryNetworkId(session));
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
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
              QuasselSession session = requireEstablishedSession(sid, "request lag probe");
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
    QuasselSession session = sessions.get(normalizeServerId(serverId));
    if (session == null || session.socketRef.get() == null) return OptionalLong.empty();
    return session.lag.lastMeasuredLagMs();
  }

  private QuasselSession findEstablishedSession(String serverId) {
    QuasselSession session = sessions.get(normalizeServerId(serverId));
    if (session == null) return null;
    if (session.socketRef.get() == null) return null;
    if (session.phase.get() != QuasselSessionPhase.SESSION_ESTABLISHED) return null;
    return session;
  }

  private boolean isSessionEstablished(String serverId) {
    return findEstablishedSession(serverId) != null;
  }

  private boolean hasCapability(QuasselSession session, String... capabilities) {
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
    QuasselSession session = requireEstablishedSession(sid, operation);
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
        resolveHistoryBuffer(session, sid, operation, tgt);
    return new HistoryRequestContext(session, tgt.rawTarget(), bufferInfo, lim);
  }

  private QuasselCoreDatastreamCodec.BufferInfoValue resolveHistoryBuffer(
      QuasselSession session, String serverId, String operation, QualifiedTarget target)
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
      QuasselSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      int firstMsgId,
      int lastMsgId,
      int limit)
      throws Exception {
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    if (bufferInfo == null || bufferInfo.bufferId() < 0) {
      throw new IllegalArgumentException("buffer info is missing a valid buffer id");
    }

    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", bufferInfo.bufferId()),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", firstMsgId),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", lastMsgId),
            limit,
            0);
    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxySync(
              out, BACKLOG_MANAGER_CLASS, BACKLOG_MANAGER_OBJECT, BACKLOG_REQUEST_SLOT, params);
        });
  }

  private long resolveHistorySelectorMsgId(
      QuasselSession session, String target, HistorySelector selector) {
    if (selector == null) return UNKNOWN_MSG_ID;
    return switch (selector.kind()) {
      case WILDCARD -> UNKNOWN_MSG_ID;
      case MSGID -> selector.msgId();
      case TIMESTAMP -> resolveHistoryMsgIdByTimestamp(session, target, selector.timestamp());
    };
  }

  private int resolveHistoryBoundaryMsgId(
      QuasselSession session, String target, HistorySelector selector, boolean lowerBound) {
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
      QuasselSession session, String target, Instant timestamp) {
    return session == null ? UNKNOWN_MSG_ID : session.history.msgIdForTimestamp(target, timestamp);
  }

  private long resolveHistoryTimestampByMsgId(QuasselSession session, String target, long msgId) {
    return session == null ? UNKNOWN_MSG_ID : session.history.exactTimestampForMsgId(target, msgId);
  }

  private void noteHistoryObservation(
      QuasselSession session, String target, long messageId, Instant at) {
    if (session == null) return;
    session.history.observe(target, messageId, at);
    Integer bufferId = session.pendingReadMarkers.takeBufferForMessage(messageId);
    if (bufferId != null) {
      emitReadMarkerObserved(session, bufferId, messageId, at);
    }
  }

  private void noteTargetNetworkHint(
      QuasselSession session, String target, int networkId, boolean preferObservedNetwork) {
    if (session == null || networkId < 0) return;
    observeKnownNetwork(session, networkId, "");
    if (preferObservedNetwork) {
      session.targetNetworkHints.observe(target, networkId);
    } else {
      session.targetNetworkHints.seed(target, networkId, () -> firstKnownNetworkId(session));
    }
  }

  private int preferredNetworkIdForTarget(
      QuasselSession session, String target, String networkToken) {
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

  private void scheduleReconnectIfEligible(QuasselSession session, String reason) {
    if (session == null || shuttingDown.get() || session.closeRequested.get()) return;
    if (!session.reconnectScheduled.compareAndSet(false, true)) return;
    reconnects.schedule(session.serverId, reason);
  }

  private void establishSession(IrcProperties.Server server, QuasselSession session) {
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
      session.authResult.set(auth);
      session.buffers.clear();
      session.pendingReadMarkers.clear();
      session.nativeReadMarkerSupportObserved.set(false);
      session.buffers.loadInitial(auth.initialBuffers());
      session.targetNetworkHints.clear();
      session.networks.reset();
      session.identities.clear();
      session.networkCurrentNickByNetworkId.clear();
      session.features.clear();
      observeKnownNetworks(session, auth);
      session.identities.initialize(auth.initialIdentities());
      int primaryNetworkId = primaryNetworkId(session);
      if (primaryNetworkId >= 0) {
        session.networkCurrentNickByNetworkId.put(primaryNetworkId, session.initialNick);
      }
      for (QuasselCoreDatastreamCodec.BufferInfoValue initial : session.buffers.values()) {
        if (initial == null) continue;
        observeKnownNetwork(session, initial.networkId(), "");
        noteTargetNetworkHint(session, initial.bufferName(), initial.networkId(), false);
      }
      trimMapToMaxSize(session.networkCurrentNickByNetworkId, MAX_NETWORK_NICKS_PER_SESSION);
      session.lag.clear();
      session.syncObserved.set(false);
      session.connectionReadyEmitted.set(false);
      session.reconnectScheduled.set(false);
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
      Disposable fallbackReadyTask =
          RxVirtualSchedulers.io()
              .scheduleDirect(
                  () -> {
                    session.syncObserved.compareAndSet(false, true);
                    emitConnectionReadyIfNeeded(session);
                  },
                  3,
                  TimeUnit.SECONDS);
      session.readinessFallbackTask.set(fallbackReadyTask);
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

  private void runReadLoop(QuasselSession session) {
    String sid = session.serverId;
    Socket socket = session.socketRef.get();
    if (socket == null) return;

    try (InputStream in = socket.getInputStream()) {
      while (!shuttingDown.get() && !session.closeRequested.get()) {
        QuasselCoreDatastreamCodec.SignalProxyMessage message;
        try {
          message = datastreamCodec.readSignalProxyMessage(in);
        } catch (SocketTimeoutException timeout) {
          continue;
        } catch (EOFException eof) {
          log.debug("Quassel read loop EOF: serverId={}", sid);
          if (session.closeRequested.get() || shuttingDown.get()) {
            return;
          }
          availabilityReasonByServer.put(sid, "Quassel Core connection closed");
          emitDisconnectedOnce(session, "Quassel Core connection closed");
          scheduleReconnectIfEligible(session, "Quassel Core connection closed");
          return;
        }
        handleSignalProxyMessage(session, message);
      }
    } catch (Exception e) {
      if (!session.closeRequested.get() && !shuttingDown.get()) {
        log.warn("Quassel read loop error: serverId={}", sid, e);
        String detail = renderThrowableMessage(e);
        String reason = detail.isEmpty() ? "Connection error" : ("Connection error: " + detail);
        availabilityReasonByServer.put(sid, reason);
        bus.onNext(new ServerIrcEvent(sid, new IrcEvent.Error(Instant.now(), reason, e)));
        emitDisconnectedOnce(session, reason);
        scheduleReconnectIfEligible(session, reason);
      }
    } finally {
      Disposable readinessTask = session.readinessFallbackTask.getAndSet(null);
      if (readinessTask != null && !readinessTask.isDisposed()) {
        try {
          readinessTask.dispose();
        } catch (Exception ignored) {
        }
      }
      closeQuietly(session.socketRef.getAndSet(null));
      sessions.remove(sid, session);
      if (session.closeRequested.get()) {
        String reason = normalizeDisconnectReason(session.closeReason.get());
        availabilityReasonByServer.put(sid, reason);
        emitDisconnectedOnce(session, reason);
      }
    }
  }

  private void handleSignalProxyMessage(
      QuasselSession session, QuasselCoreDatastreamCodec.SignalProxyMessage message)
      throws Exception {
    if (message == null) return;
    int requestType = message.requestType();
    log.debug(
        "Quassel inbound signal: serverId={}, requestType={}, className={}, objectName={}, slotName={}, paramCount={}",
        session == null ? "" : session.serverId,
        requestType,
        message.className(),
        message.objectName(),
        message.slotName(),
        message.params() == null ? 0 : message.params().size());
    if (requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT) {
      handleHeartbeat(session, message.params());
      return;
    }
    if (requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT_REPLY) {
      handleHeartbeatReply(session, message.params());
      return;
    }
    if (requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL) {
      handleRpcCall(session, message.slotName(), message.params());
      return;
    }
    if (requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC
        || requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_INIT_DATA) {
      session.syncObserved.set(true);
      handleSyncOrInitData(
          session,
          requestType,
          message.className(),
          message.objectName(),
          message.slotName(),
          message.params());
      emitConnectionReadyIfNeeded(session);
    }
  }

  private void handleHeartbeat(QuasselSession session, List<Object> params) throws Exception {
    if (params == null || params.isEmpty()) return;
    Object value = params.get(0);
    if (!(value instanceof QuasselCoreDatastreamCodec.QtDateTimeValue timestamp)) return;

    Socket socket = session.socketRef.get();
    if (socket == null) return;
    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyHeartBeatReply(out, timestamp);
        });
  }

  private void handleHeartbeatReply(QuasselSession session, List<Object> params) {
    if (session != null) session.lag.observeReply(params);
  }

  private void handleRpcCall(QuasselSession session, String slotName, List<Object> params) {
    String slot = Objects.toString(slotName, "").trim();
    if (slot.isEmpty()) return;
    log.debug(
        "Received Quassel RPC slot: serverId={}, slot={}, paramCount={}",
        session == null ? "" : session.serverId,
        slot,
        params == null ? 0 : params.size());
    if (slot.toLowerCase(Locale.ROOT).contains("network")) {
      log.debug(
          "Received Quassel network-related RPC slot: serverId={}, slot={}, params={}",
          session == null ? "" : session.serverId,
          slot,
          params);
    }

    if ("2displayMsg(Message)".equals(slot)) {
      Object first = (params == null || params.isEmpty()) ? null : params.get(0);
      if (first instanceof QuasselCoreDatastreamCodec.MessageValue msg) {
        handleDisplayMessage(session, msg);
      }
      return;
    }

    if ("2displayStatusMsg(QString,QString)".equals(slot)) {
      String network =
          (params == null || params.isEmpty()) ? "" : Objects.toString(params.get(0), "");
      String text =
          (params == null || params.size() < 2) ? "" : Objects.toString(params.get(1), "");
      handleDisplayStatusMessage(session.serverId, network, text);
      return;
    }

    if ("2bufferInfoUpdated(BufferInfo)".equals(slot)) {
      Object first = (params == null || params.isEmpty()) ? null : params.get(0);
      if (first instanceof QuasselCoreDatastreamCodec.BufferInfoValue info
          && info.bufferId() >= 0) {
        QuasselCoreDatastreamCodec.BufferInfoValue merged = session.buffers.merge(info);
        observeKnownNetwork(session, merged.networkId(), "");
        noteTargetNetworkHint(session, merged.bufferName(), merged.networkId(), false);
        emitJoinedChannelFromBufferInfoIfNetworkConnected(session, merged);
      }
      return;
    }

    if ("2bufferInfoRemoved(BufferInfo)".equals(slot)) {
      Object first = (params == null || params.isEmpty()) ? null : params.get(0);
      if (first instanceof QuasselCoreDatastreamCodec.BufferInfoValue info
          && info.bufferId() >= 0) {
        session.buffers.remove(info.bufferId());
        session.pendingReadMarkers.forgetBuffer(info.bufferId());
      }
    }

    observeNetworkLifecycleFromRpcSlot(session, slot, params);
    session.identities.handleRpc(slot, params);
  }

  private void observeNetworkLifecycleFromRpcSlot(
      QuasselSession session, String slotName, List<Object> params) {
    if (session == null) return;
    String slot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    if (slot.isEmpty() || !slot.contains("network")) return;
    if (params == null || params.isEmpty()) return;

    boolean remove = slot.contains("remove") || slot.contains("deleted");
    boolean createLike = !remove && (slot.contains("create") || slot.contains("added"));
    log.debug(
        "Observing Quassel network lifecycle RPC: serverId={}, slot={}, remove={}, params={}",
        session.serverId,
        slotName,
        remove,
        params);
    for (Object param : params) {
      observeNetworkLifecycleFromRpcParam(session, param, remove, createLike);
    }
  }

  private void observeNetworkLifecycleFromRpcParam(
      QuasselSession session, Object raw, boolean remove, boolean createLike) {
    if (session == null || raw == null) return;

    if (raw instanceof List<?> list) {
      for (Object value : list) {
        observeNetworkLifecycleFromRpcParam(session, value, remove, createLike);
      }
      return;
    }

    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      String type = Objects.toString(userType.typeName(), "").trim();
      Object value = userType.value();
      if ("NetworkInfo".equals(type) && value instanceof Map<?, ?> map) {
        observeNetworkLifecycleFromRpcParam(session, map, remove, createLike);
        return;
      }
      if ("NetworkId".equals(type)) {
        int networkId = tryParseInt(value);
        if (networkId < 0) return;
        if (remove) {
          log.debug(
              "Quassel network lifecycle RPC removed network by id: serverId={}, networkId={}",
              session.serverId,
              networkId);
          forgetKnownNetwork(session, networkId);
        } else {
          String observedName = createLike ? session.networks.claimCreatedName() : "";
          log.debug(
              "Quassel network lifecycle RPC observed network by id: serverId={}, networkId={}, nameHint={}",
              session.serverId,
              networkId,
              observedName);
          observeKnownNetwork(session, networkId, observedName);
        }
        return;
      }
      observeNetworkLifecycleFromRpcParam(session, value, remove, createLike);
      return;
    }

    if (raw instanceof Map<?, ?> map) {
      int networkId = networkIdFromStateMap(map, -1);
      if (remove && networkId >= 0) {
        log.debug(
            "Quassel network lifecycle RPC removed network by map: serverId={}, networkId={}, map={}",
            session.serverId,
            networkId,
            map);
        forgetKnownNetwork(session, networkId);
        return;
      }
      String networkName =
          firstNonBlank(
              mapValueIgnoreCase(map, "networkName"),
              mapValueIgnoreCase(map, "networkname"),
              mapValueIgnoreCase(map, "name"));
      log.debug(
          "Quassel network lifecycle RPC observed network map: serverId={}, networkId={}, networkName={}, map={}",
          session.serverId,
          networkId,
          networkName,
          map);
      observeKnownNetwork(session, networkId, networkName);
      observeNetworkStateSnapshot(session, networkId, map);
      observeNetworkCapabilities(session, networkId, map);
      observeNetworkMonitorSupport(session, networkId, map);
      return;
    }

    int networkId = tryParseInt(raw);
    if (networkId < 0) return;
    if (remove) {
      forgetKnownNetwork(session, networkId);
    } else {
      String observedName = createLike ? session.networks.claimCreatedName() : "";
      observeKnownNetwork(session, networkId, observedName);
    }
  }

  private void handleDisplayStatusMessage(String serverId, String network, String text) {
    String net = Objects.toString(network, "").trim();
    String rawLine = Objects.toString(text, "").trim();
    log.debug(
        "Quassel display status message: serverId={}, network={}, text={}", serverId, net, rawLine);
    String messageLine = rawLine;
    if (messageLine.isEmpty()) {
      messageLine = net;
    } else if (!net.isEmpty() && extractNumericCode(rawLine) == 0) {
      messageLine = net + ": " + messageLine;
    }
    if (messageLine.isEmpty()) return;
    emitServerResponseLine(serverId, Instant.now(), messageLine, rawLine, "", Map.of());
  }

  private void handleSyncOrInitData(
      QuasselSession session,
      int requestType,
      String className,
      String objectName,
      String slotName,
      List<Object> params) {
    String classToken = Objects.toString(className, "").trim();
    String slotToken = Objects.toString(slotName, "").trim();
    List<Object> values = params == null ? List.of() : params;
    log.debug(
        "Received Quassel sync/init envelope: serverId={}, requestType={}, className={}, objectName={}, slotName={}, paramCount={}",
        session == null ? "" : session.serverId,
        requestType,
        classToken,
        objectName,
        slotToken,
        values.size());
    if (classToken.toLowerCase(Locale.ROOT).contains("network")) {
      log.debug(
          "Received network-related sync/init envelope: serverId={}, requestType={}, className={}, objectName={}, slotName={}, params={}",
          session == null ? "" : session.serverId,
          requestType,
          classToken,
          objectName,
          slotToken,
          values);
    }

    if ("BufferSyncer".equals(classToken)) {
      if (session.nativeReadMarkerSupportObserved.compareAndSet(false, true)) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), "quassel-buffer-syncer")));
      }
      applyBufferInfoSnapshot(session, values);
      handleBufferSyncerSync(session, slotToken, values);
      return;
    }

    if ("BufferViewConfig".equals(classToken)) {
      applyBufferInfoSnapshot(session, values);
      return;
    }

    if ("BacklogManager".equals(classToken)
        && requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC
        && slotToken.contains("receiveBacklog")) {
      handleBacklogSync(session, values);
      return;
    }

    if ("CoreInfo".equals(classToken)) {
      session.identities.handleCoreInfoSync(objectName, slotToken, values);
      return;
    }

    if ("Identity".equals(classToken)) {
      session.identities.handleSync(objectName, values);
      return;
    }

    if ("Network".equals(classToken)) {
      handleNetworkPropertySync(session, objectName, slotToken, values);
      observeChannelMembershipFromNetworkSync(session, objectName, slotToken, values);
      maybeUpdateCurrentNickFromNetworkState(session, objectName, values);
      observeMaybeNetworkStateFromUnknownSync(session, classToken, objectName, slotToken, values);
      return;
    }

    if ("IrcUser".equals(classToken)) {
      QuasselCoreStateSyncTranslator.userState(
          Instant.now(),
          objectName,
          values,
          (networkId, networkName) -> observeKnownNetwork(session, networkId, networkName),
          event -> bus.onNext(new ServerIrcEvent(session.serverId, event)));
      return;
    }

    if ("IrcChannel".equals(classToken)) {
      QuasselCoreStateSyncTranslator.channelState(
          Instant.now(),
          objectName,
          values,
          (networkId, networkName) -> observeKnownNetwork(session, networkId, networkName),
          (target, networkId) -> qualifyTargetForNetwork(session, target, networkId),
          event -> bus.onNext(new ServerIrcEvent(session.serverId, event)));
      return;
    }

    if ("NetworkInfo".equals(classToken)) {
      handleNetworkInfoStateSync(session, objectName, values);
      return;
    }

    if (classToken.toLowerCase(Locale.ROOT).contains("identity")) {
      session.identities.observeUnknownState(
          values, parseNetworkId(objectName), classToken, objectName, slotToken);
    }
    observeMaybeNetworkStateFromUnknownSync(session, classToken, objectName, slotToken, values);
  }

  private void handleNetworkPropertySync(
      QuasselSession session, String objectName, String slotName, List<Object> values) {
    if (session == null || values.isEmpty()) return;
    int networkId = parseNetworkId(objectName);
    if (networkId < 0) return;
    Object value = values.getFirst();
    switch (slotName) {
      case "setNetworkName" -> {
        String name = Objects.toString(value, "").trim();
        if (!name.isEmpty()) {
          observeKnownNetwork(session, networkId, name);
          observeNetworkStateSnapshot(session, networkId, Map.of("networkName", name));
        }
      }
      case "setConnected" -> {
        Boolean connected = parseBoolean(value);
        if (connected != null) {
          observeNetworkStateSnapshot(session, networkId, Map.of("isConnected", connected));
        }
      }
      case "setConnectionState" -> {
        int state = tryParseInt(value);
        if (state >= 0) {
          observeNetworkStateSnapshot(session, networkId, Map.of("connectionState", state));
        }
      }
      case "setMyNick" ->
          observeCurrentNick(session, networkId, Objects.toString(value, ""), Instant.now());
      default -> {}
    }
  }

  private void observeMaybeNetworkStateFromUnknownSync(
      QuasselSession session,
      String classToken,
      String objectName,
      String slotToken,
      List<Object> values) {
    if (session == null) return;
    String className = Objects.toString(classToken, "").trim();
    if (!className.toLowerCase(Locale.ROOT).contains("network")) {
      return;
    }
    if (values == null || values.isEmpty()) return;

    int fallbackNetworkId = parseNetworkId(objectName);
    ArrayList<Map<?, ?>> candidates = new ArrayList<>();
    Map<String, Object> flattened = flattenNetworkStateFromKeyValueParams(values);
    if (!flattened.isEmpty()) {
      candidates.add(flattened);
    }
    for (Object value : values) {
      collectPotentialNetworkStateMaps(value, candidates);
    }
    if (candidates.isEmpty()) {
      log.debug(
          "Network-related sync envelope had no map payloads to inspect: serverId={}, className={}, objectName={}, slotName={}, params={}",
          session.serverId,
          className,
          objectName,
          slotToken,
          values);
      return;
    }

    int applied = 0;
    for (Map<?, ?> candidate : candidates) {
      if (candidate == null || candidate.isEmpty()) continue;
      int networkId = networkIdFromStateMap(candidate, fallbackNetworkId);
      String networkName =
          firstNonBlank(
              mapValueIgnoreCase(candidate, "networkName"),
              mapValueIgnoreCase(candidate, "networkname"),
              mapValueIgnoreCase(candidate, "name"));
      boolean looksLikeNetworkState =
          networkId >= 0
              || !networkName.isEmpty()
              || mapValueIgnoreCase(candidate, "ServerList") != null
              || mapValueIgnoreCase(candidate, "serverList") != null;
      if (!looksLikeNetworkState) continue;

      observeKnownNetwork(session, networkId, networkName);
      observeNetworkStateSnapshot(session, networkId, candidate);
      observeNetworkCapabilities(session, networkId, candidate);
      observeNetworkMonitorSupport(session, networkId, candidate);
      applied++;
      log.debug(
          "Applied network state from unknown sync class: serverId={}, className={}, objectName={}, slotName={}, networkId={}, networkName={}, state={}",
          session.serverId,
          className,
          objectName,
          slotToken,
          networkId,
          networkName,
          candidate);
    }

    if (applied == 0) {
      log.debug(
          "Network-related sync envelope maps were inspected but no network states were derived: serverId={}, className={}, objectName={}, slotName={}, mapCount={}",
          session.serverId,
          className,
          objectName,
          slotToken,
          candidates.size());
    }
  }

  private void observeChannelMembershipFromNetworkSync(
      QuasselSession session, String objectName, String slotName, List<Object> values) {
    if (session == null) return;
    session.membership.observeNetworkLifecycle(
        objectName,
        slotName,
        values,
        (channel, networkId) -> qualifyTargetForNetwork(session, channel, networkId));
  }

  private void applyBufferInfoSnapshot(QuasselSession session, List<Object> values) {
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

  private void maybeUpdateCurrentNickFromNetworkState(
      QuasselSession session, String objectName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("name"));
      observeKnownNetwork(session, networkId, networkName);
      observeNetworkStateSnapshot(session, networkId, map);
      observeNetworkCapabilities(session, networkId, map);
      observeNetworkMonitorSupport(session, networkId, map);
      Object maybeNick = map.get("myNick");
      String next = Objects.toString(maybeNick, "").trim();
      if (!next.isEmpty()) {
        observeCurrentNick(session, networkId, next, Instant.now());
      }
    }
  }

  private void handleNetworkInfoStateSync(
      QuasselSession session, String objectName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("name"));
      log.debug(
          "Quassel NetworkInfo sync observed: serverId={}, objectName={}, networkId={}, networkName={}, map={}",
          session == null ? "" : session.serverId,
          objectName,
          networkId,
          networkName,
          map);
      observeKnownNetwork(session, networkId, networkName);
      observeNetworkStateSnapshot(session, networkId, map);
      observeNetworkCapabilities(session, networkId, map);
      observeNetworkMonitorSupport(session, networkId, map);
    }
  }

  private void handleBufferSyncerSync(
      QuasselSession session, String slotName, List<Object> values) {
    if (session == null || values == null || values.isEmpty()) return;
    Instant now = Instant.now();
    for (ReadMarkerUpdate update :
        QuasselCoreBufferSyncerParser.parseReadMarkers(slotName, values)) {
      emitReadMarkerObserved(session, update.bufferId(), update.msgId(), now);
    }
  }

  private void emitReadMarkerObserved(
      QuasselSession session, int bufferId, long markerMsgId, Instant at) {
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
      QuasselSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeCapabilities(resolvedNetworkId, stateMap);
  }

  private void observeNetworkMonitorSupport(
      QuasselSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeMonitor(resolvedNetworkId, stateMap);
  }

  private void handleBacklogSync(QuasselSession session, List<Object> values) {
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
      QuasselSession session, QuasselCoreDatastreamCodec.MessageValue message) {
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

    if (isJoinMessage(typeBits)) {
      handleJoinMessage(session, at, target, fromDisplay, senderHostmask, networkId);
      return;
    }

    if (isPartMessage(typeBits)) {
      handlePartMessage(session, at, target, fromDisplay, senderHostmask, payloadText, networkId);
      return;
    }

    if (isQuitMessage(typeBits)) {
      if (!target.isEmpty()) {
        emitObservedHostmask(session, at, target, fromDisplay, senderHostmask);
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserQuitChannel(
                    at, target, fromDisplay, normalizeReason(payloadText))));
      }
      return;
    }

    if (isNickMessage(typeBits)) {
      String newNick = parseNickChange(payloadText, fromDisplay);
      if (!target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserNickChangedChannel(at, target, fromDisplay, newNick)));
      }
      if (isSelfNick(session, fromDisplay, networkId)) {
        observeCurrentNick(session, networkId, newNick, at);
      }
      return;
    }

    if (isTopicMessage(typeBits)) {
      String topic = parseTopic(payloadText);
      if (!target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId, new IrcEvent.ChannelTopicUpdated(at, target, topic)));
        return;
      }
    }

    if (isModeMessage(typeBits)) {
      if (!target.isEmpty()) {
        String details = parseModeDetails(payloadText);
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                ChannelModeObservationFactory.fromQuasselDisplayMessage(
                    at, target, fromDisplay, details)));
        return;
      }
    }

    if (isKickMessage(typeBits)) {
      if (!target.isEmpty()) {
        KickDetails kick = parseKickDetails(payloadText);
        String kickedNick = Objects.toString(kick.nick(), "").trim();
        if (!kickedNick.isEmpty()) {
          if (isSelfNick(session, kickedNick, networkId)) {
            session.membership.leave(target, networkId);
            bus.onNext(
                new ServerIrcEvent(
                    session.serverId,
                    new IrcEvent.KickedFromChannel(at, target, fromDisplay, kick.reason())));
          } else {
            bus.onNext(
                new ServerIrcEvent(
                    session.serverId,
                    new IrcEvent.UserKickedFromChannel(
                        at, target, kickedNick, fromDisplay, kick.reason())));
          }
          return;
        }
      }
    }

    if (isInviteMessage(typeBits)) {
      String channel = firstChannelToken(payloadText);
      if (channel.isEmpty()) channel = target;
      if (!channel.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.InvitedToChannel(
                    at,
                    channel,
                    fromDisplay,
                    currentNickForNetwork(session, networkId),
                    "",
                    false)));
        return;
      }
    }

    if (isNoticeMessage(typeBits)) {
      if (target.isEmpty() && isQueryBuffer(bufferInfo)) {
        target = targetForBuffer(session, bufferInfo, fromDisplay);
      }
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.Notice(at, fromDisplay, target, payloadText, messageId, ircv3Tags)));
      return;
    }

    if (isActionMessage(typeBits)) {
      if (isChannelBuffer(bufferInfo) && !target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.ChannelAction(
                    at, target, fromDisplay, payloadText, messageId, ircv3Tags)));
      } else {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.PrivateAction(at, fromDisplay, payloadText, messageId, ircv3Tags)));
      }
      return;
    }

    if (isPlainMessage(typeBits)) {
      if (isChannelBuffer(bufferInfo) && !target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.ChannelMessage(
                    at, target, fromDisplay, payloadText, messageId, ircv3Tags)));
        return;
      }
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.PrivateMessage(at, fromDisplay, payloadText, messageId, ircv3Tags)));
      return;
    }

    String statusLine =
        payloadText.isBlank() ? renderUnknownMessageType(message, target) : payloadText;
    if (isErrorMessage(typeBits)) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.Error(
                  at, statusLine.isBlank() ? "Quassel reported an error" : statusLine, null)));
      return;
    }

    bus.onNext(
        new ServerIrcEvent(
            session.serverId, serverResponse(at, statusLine, content, messageId, ircv3Tags)));
  }

  private void emitCapabilityChangesFromCapLine(
      QuasselSession session, Instant at, int networkId, QuasselCoreIrcEnvelope envelope) {
    if (session == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    session.features.observeCapLine(at, resolvedNetworkId, envelope);
  }

  private String resolveSignalTargetForRawTarget(
      QuasselSession session,
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

  private void handleJoinMessage(
      QuasselSession session,
      Instant at,
      String channel,
      String fromDisplay,
      String senderHostmask,
      int networkId) {
    if (channel.isEmpty()) return;
    if (isSelfNick(session, fromDisplay, networkId)) {
      session.membership.observeJoin(at, channel, networkId);
      return;
    }
    emitObservedHostmask(session, at, channel, fromDisplay, senderHostmask);
    bus.onNext(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.UserJoinedChannel(at, channel, fromDisplay)));
  }

  private void handlePartMessage(
      QuasselSession session,
      Instant at,
      String channel,
      String fromDisplay,
      String senderHostmask,
      String content,
      int networkId) {
    if (channel.isEmpty()) return;
    String reason = normalizeReason(content);
    if (isSelfNick(session, fromDisplay, networkId)) {
      session.membership.leave(channel, networkId);
      bus.onNext(
          new ServerIrcEvent(session.serverId, new IrcEvent.LeftChannel(at, channel, reason)));
      return;
    }
    emitObservedHostmask(session, at, channel, fromDisplay, senderHostmask);
    bus.onNext(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.UserPartedChannel(at, channel, fromDisplay, reason)));
  }

  private void emitBacklogHistoryBatch(
      QuasselSession session,
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
      QuasselSession session, QuasselCoreDatastreamCodec.BufferInfoValue incoming) {
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

  private static boolean isNickMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_NICK) != 0;
  }

  private static boolean isModeMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_MODE) != 0;
  }

  private static boolean isJoinMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_JOIN) != 0;
  }

  private static boolean isPartMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_PART) != 0;
  }

  private static boolean isQuitMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_QUIT) != 0;
  }

  private static boolean isKickMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_KICK) != 0;
  }

  private static boolean isTopicMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_TOPIC) != 0;
  }

  private static boolean isInviteMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_INVITE) != 0;
  }

  private static boolean isServerInfoMessage(int typeBits) {
    return (typeBits & (MESSAGE_TYPE_SERVER | MESSAGE_TYPE_INFO)) != 0;
  }

  private static boolean isErrorMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_ERROR) != 0;
  }

  private static boolean isBacklogMessage(int flags) {
    return (flags & MESSAGE_FLAG_BACKLOG) != 0;
  }

  private String targetForBuffer(
      QuasselSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      String fallbackFromNick) {
    return qualifiedTargetForBuffer(session, bufferInfo, fallbackFromNick);
  }

  private String historyTargetForBuffer(
      QuasselSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      String fallbackFromNick) {
    return qualifiedTargetForBuffer(session, bufferInfo, fallbackFromNick);
  }

  private String qualifiedTargetForBuffer(
      QuasselSession session,
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

  private static String currentNickForPrimaryNetwork(QuasselSession session) {
    if (session == null) return "";
    int primary = primaryNetworkId(session);
    if (primary >= 0) {
      String byNetwork =
          Objects.toString(session.networkCurrentNickByNetworkId.get(primary), "").trim();
      if (!byNetwork.isEmpty()) return byNetwork;
    }
    return Objects.toString(session.currentNick.get(), "").trim();
  }

  private static String currentNickForNetwork(QuasselSession session, int networkId) {
    if (session == null) return "";
    if (networkId >= 0) {
      String byNetwork =
          Objects.toString(session.networkCurrentNickByNetworkId.get(networkId), "").trim();
      if (!byNetwork.isEmpty()) return byNetwork;
    }
    return currentNickForPrimaryNetwork(session);
  }

  private void observeCurrentNick(
      QuasselSession session, int networkId, String nextNick, Instant at) {
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

  private static int primaryNetworkId(QuasselSession session) {
    return session == null
        ? -1
        : session.networks.primaryNetworkId(session.authResult.get(), session.buffers.values());
  }

  private void observeKnownNetworks(
      QuasselSession session, QuasselCoreAuthHandshake.AuthResult authResult) {
    if (session == null || authResult == null || authResult.networkIds() == null) return;
    for (Integer id : authResult.networkIds()) {
      if (id == null) continue;
      observeKnownNetwork(session, id.intValue(), "");
    }
  }

  private void observeKnownNetwork(QuasselSession session, int networkId, String networkName) {
    if (session == null || networkId < 0) return;
    session.networks.observe(networkId, networkName);
    emitQuasselNetworkSnapshotEvent(session, "observe-known-network");
  }

  private void forgetKnownNetwork(QuasselSession session, int networkId) {
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
      QuasselSession session, int networkId, Map<?, ?> stateMap) {
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
      QuasselSession session, QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {
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

  private static int firstKnownIdentityId(QuasselSession session) {
    return session == null ? -1 : session.identities.firstKnownId();
  }

  private List<QuasselCoreNetworkSummary> snapshotQuasselCoreNetworks(QuasselSession session) {
    return session == null
        ? List.of()
        : session.networks.snapshot(session.authResult.get(), session.buffers.values());
  }

  private void emitQuasselNetworkSnapshotEvent(QuasselSession session, String source) {
    if (session == null) return;
    String sid = normalizeServerId(session.serverId);
    if (sid.isEmpty()) return;
    List<QuasselCoreNetworkSummary> snapshot = snapshotQuasselCoreNetworks(session);
    observations.observeNetwork(new QuasselCoreNetworkSnapshotEvent(sid, snapshot, source));
  }

  private static LinkedHashSet<Integer> collectKnownNetworkIds(QuasselSession session) {
    return session == null
        ? new LinkedHashSet<>()
        : session.networks.knownIds(session.authResult.get(), session.buffers.values());
  }

  private int resolveQuasselNetworkId(
      QuasselSession session, String serverId, String networkIdOrName, String operation) {
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

  private static int resolveQuasselIdentityId(QuasselSession session, Integer requestedIdentityId) {
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

  private String qualifyTargetForNetwork(QuasselSession session, String baseTarget, int networkId) {
    String base = Objects.toString(baseTarget, "").trim();
    if (base.isEmpty()) return "";
    if (session == null || networkId < 0) return base;
    if (knownNetworkCount(session) <= 1) return base;
    String token = networkTokenForNetworkId(session, networkId);
    return QuasselCoreTargetRouting.qualifyTarget(base, token);
  }

  private static int knownNetworkCount(QuasselSession session) {
    return collectKnownNetworkIds(session).size();
  }

  private String networkTokenForNetworkId(QuasselSession session, int networkId) {
    if (session == null || networkId < 0) return "";
    String token = session.networks.token(networkId);
    if (!token.isEmpty()) return token;
    observeKnownNetwork(session, networkId, "");
    return session.networks.token(networkId);
  }

  private static boolean isSelfNick(QuasselSession session, String nick, int networkId) {
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

  private void emitServerResponseLine(
      String serverId,
      Instant at,
      String displayLine,
      String rawLine,
      String messageId,
      Map<String, String> ircv3Tags) {
    bus.onNext(
        new ServerIrcEvent(
            serverId, serverResponse(at, displayLine, rawLine, messageId, ircv3Tags)));
  }

  private void emitObservedHostmask(
      QuasselSession session, Instant at, String channel, String nick, String hostmask) {
    if (session == null) return;
    String normalizedNick = Objects.toString(nick, "").trim();
    String normalizedHostmask = Objects.toString(hostmask, "").trim();
    if (normalizedNick.isEmpty() || !PircbotxUtil.isUsefulHostmask(normalizedHostmask)) {
      return;
    }
    bus.onNext(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.UserHostmaskObserved(at, channel, normalizedNick, normalizedHostmask)));
  }

  private static String renderUnknownMessageType(
      QuasselCoreDatastreamCodec.MessageValue message, String target) {
    String prefix = target.isEmpty() ? "" : ("[" + target + "] ");
    if (isServerInfoMessage(message.typeBits())) {
      String content = Objects.toString(message.content(), "").trim();
      return content.isEmpty() ? (prefix + "(server)") : (prefix + content);
    }
    String content = Objects.toString(message.content(), "").trim();
    if (!content.isEmpty()) {
      return prefix + content;
    }
    return prefix + "(quassel message type " + message.typeBits() + ")";
  }

  private record HistoryRequestContext(
      QuasselSession session,
      String target,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      int limit) {}

  private void emitConnectionReadyIfNeeded(QuasselSession session) {
    if (session == null) return;
    if (session.phase.get() != QuasselSessionPhase.SESSION_ESTABLISHED) return;
    if (!session.syncObserved.get()) return;
    if (!session.connectionReadyEmitted.compareAndSet(false, true)) return;

    Disposable readinessTask = session.readinessFallbackTask.getAndSet(null);
    if (readinessTask != null && !readinessTask.isDisposed()) {
      try {
        readinessTask.dispose();
      } catch (Exception ignored) {
      }
    }

    bus.onNext(new ServerIrcEvent(session.serverId, new IrcEvent.ConnectionReady(Instant.now())));
    emitConnectionPhase(session, PHASE_SYNC_READY, "quassel-sync");
  }

  private void closeSession(QuasselSession session, String reason, boolean emitDisconnected) {
    if (session == null) return;
    session.closeRequested.set(true);
    session.closeReason.set(normalizeDisconnectReason(reason));

    Disposable readinessTask = session.readinessFallbackTask.getAndSet(null);
    if (readinessTask != null && !readinessTask.isDisposed()) {
      try {
        readinessTask.dispose();
      } catch (Exception ignored) {
      }
    }

    Disposable readTask = session.readLoopTask.getAndSet(null);
    if (readTask != null && !readTask.isDisposed()) {
      try {
        readTask.dispose();
      } catch (Exception ignored) {
      }
    }

    closeQuietly(session.socketRef.getAndSet(null));
    session.lag.clear();
    session.buffers.clear();
    session.pendingReadMarkers.clear();
    session.nativeReadMarkerSupportObserved.set(false);
    session.history.clear();
    session.targetNetworkHints.clear();
    session.membership.clear();
    session.networks.clearMetadata();
    session.identities.clear();
    session.networkCurrentNickByNetworkId.clear();
    session.features.clear();
    availabilityReasonByServer.put(session.serverId, session.closeReason.get());
    if (emitDisconnected) {
      emitDisconnectedOnce(session, session.closeReason.get());
    }
  }

  private void emitDisconnectedOnce(QuasselSession session, String reason) {
    if (session == null) return;
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

  private void emitConnectionPhase(QuasselSession session, String phase, String detail) {
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

  private QuasselSession requireEstablishedSession(String serverId, String operation)
      throws BackendNotAvailableException {
    String sid = normalizeServerId(serverId);
    QuasselSession session = sessions.get(sid);
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

  private void sendNetworkRequest(QuasselSession session, int networkId, String slotName)
      throws Exception {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    if (networkId < 0) {
      throw new IllegalArgumentException("network id is invalid");
    }
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    String slot = Objects.toString(slotName, "").trim();
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("slot name is blank");
    }

    log.debug(
        "Sending Quassel network sync call: serverId={}, className={}, objectName={}, slotName={}, paramCount=0",
        session.serverId,
        NETWORK_CLASS,
        Integer.toString(networkId),
        slot);
    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxySync(
              out, NETWORK_CLASS, Integer.toString(networkId), slot, List.of());
        });
  }

  private boolean maybeRepairNetworkIdentityBeforeConnect(QuasselSession session, int networkId)
      throws Exception {
    if (session == null || networkId < 0) return true;
    return new QuasselCoreNetworkConnectPreflight(
            session.serverId,
            session.networks,
            session.identities,
            observations,
            () -> resolveQuasselIdentityId(session, null),
            () -> collectKnownNetworkIds(session),
            new QuasselCoreNetworkConnectPreflight.Commands() {
              @Override
              public void requestNetworkInitState(int id) throws Exception {
                QuasselCoreIrcClientService.this.requestNetworkInitState(session, id);
              }

              @Override
              public void updateNetwork(int id, QuasselCoreNetworkUpdateRequest request)
                  throws Exception {
                sendUpdateNetworkRequest(session, id, request);
              }

              @Override
              public void createNetwork(int identityId, QuasselCoreNetworkCreateRequest request)
                  throws Exception {
                sendCreateNetworkRequest(
                    session, identityId, request, RPC_CREATE_NETWORK_SLOT, true);
              }
            })
        .prepare(networkId);
  }

  private void requestNetworkInitState(QuasselSession session, int networkId) throws Exception {
    if (session == null || networkId < 0) return;
    sendSignalProxyInitRequest(session, NETWORK_CLASS, Integer.toString(networkId));
  }

  private void sendSignalProxyInitRequest(
      QuasselSession session, String className, String objectName) throws Exception {
    if (session == null) return;
    String clazz = Objects.toString(className, "").trim();
    String object = Objects.toString(objectName, "").trim();
    if (clazz.isEmpty()) return;
    Socket socket = session.socketRef.get();
    if (socket == null) return;
    log.debug(
        "Sending Quassel init request: serverId={}, className={}, objectName={}",
        session.serverId,
        clazz,
        object);
    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyInitRequest(out, clazz, object, List.of());
        });
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

  private void sendNetworkRpcRequest(QuasselSession session, int networkId, String slotName)
      throws Exception {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    if (networkId < 0) {
      throw new IllegalArgumentException("network id is invalid");
    }
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    String slot = Objects.toString(slotName, "").trim();
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("rpc slot name is blank");
    }

    session.outbound.send(
        socket,
        (codec, out) -> {
          // Quassel's NetworkId is a type alias over Int on the wire.
          codec.writeSignalProxyRpcCall(out, slot, List.of(networkId));
        });
  }

  private void coordinateNetworkCreation(
      QuasselSession session, QuasselCoreNetworkCreateRequest request) throws Exception {
    new QuasselCoreNetworkCreationCoordinator(
            session.serverId,
            session.initialNick,
            session.identities,
            session.networks,
            observations,
            requested -> resolveQuasselIdentityId(session, requested),
            () -> collectKnownNetworkIds(session),
            new QuasselCoreNetworkCreationCoordinator.Commands() {
              @Override
              public void createIdentity(Map<String, Object> payload) throws Exception {
                sendCreateIdentityRequest(session, payload);
              }

              @Override
              public void createNetwork(
                  int identityId, QuasselCoreNetworkCreateRequest req, boolean legacy)
                  throws Exception {
                sendCreateNetworkRequest(
                    session,
                    identityId,
                    req,
                    legacy ? RPC_CREATE_NETWORK_SLOT_LEGACY : RPC_CREATE_NETWORK_SLOT,
                    !legacy);
              }
            })
        .create(request);
  }

  private void sendCreateIdentityRequest(
      QuasselSession session, Map<String, Object> identityPayload) throws Exception {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    Map<String, Object> payload =
        identityPayload == null || identityPayload.isEmpty()
            ? QuasselCoreIdentityRequests.defaultPayload(session.initialNick, null)
            : identityPayload;
    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("Identity", payload),
            Collections.emptyMap());
    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(out, RPC_CREATE_IDENTITY_SLOT, params);
        });
  }

  private void sendCreateNetworkRequest(
      QuasselSession session,
      int identityId,
      QuasselCoreNetworkCreateRequest request,
      String rpcSlot,
      boolean includeAutoJoinChannels)
      throws Exception {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    String slot = Objects.toString(rpcSlot, "").trim();
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("create-network rpc slot is blank");
    }
    Map<String, Object> networkInfo =
        QuasselCoreNetworkRequests.networkInfoPayload(
            -1, identityId, request, true, /* includeLegacyAliases= */ true);
    ArrayList<Object> params = new ArrayList<>(2);
    params.add(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkInfo", networkInfo));
    if (includeAutoJoinChannels) {
      params.add(request.autoJoinChannels());
    }

    log.debug(
        "Sending Quassel create network RPC: slot={}, networkName={}, host={}, port={}, tls={}, verifyTls={}, autoJoinChannels={}",
        slot,
        request.networkName(),
        request.serverHost(),
        request.serverPort(),
        request.useTls(),
        request.verifyTls(),
        request.autoJoinChannels());
    QuasselCoreAuthHandshake.AuthResult auth = session.authResult.get();
    log.debug(
        "Create-network session context: serverId={}, slot={}, knownNetworkIds={}, knownIdentityIds={}, authPrimaryNetworkId={}, authNetworkIds={}, authInitialBufferCount={}, networkStateKeys={}, networkDisplayKeys={}, networkTokenKeys={}, identityStateKeys={}, identityNames={}, payload={}",
        session.serverId,
        slot,
        collectKnownNetworkIds(session),
        session.identities.knownIds(),
        auth == null ? -1 : auth.primaryNetworkId(),
        auth == null ? List.of() : auth.networkIds(),
        auth == null || auth.initialBuffers() == null ? 0 : auth.initialBuffers().size(),
        session.networks.stateIds(),
        session.networks.displayNames().keySet(),
        session.networks.tokenIds(),
        session.identities.stateIds(),
        session.identities.names(),
        summarizeNetworkInfoForLog(networkInfo));

    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(out, slot, params);
        });
    if (includeAutoJoinChannels) {
      session.networks.rememberCreatedName(request.networkName());
    }
  }

  private void sendUpdateNetworkRequest(
      QuasselSession session, int networkId, QuasselCoreNetworkUpdateRequest request)
      throws Exception {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    if (networkId < 0) {
      throw new IllegalArgumentException("network id is invalid");
    }
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }

    Map<String, Object> existing = session.networks.state(networkId);
    String networkName =
        firstNonBlank(
            request.networkName(),
            mapValueIgnoreCase(existing, "networkName"),
            mapValueIgnoreCase(existing, "networkname"),
            mapValueIgnoreCase(existing, "name"),
            session.networks.displayNames().get(networkId));
    if (networkName.isBlank()) {
      networkName = "network-" + networkId;
    }

    int identityId =
        request.identityId() != null
            ? request.identityId().intValue()
            : parseNetworkIdentityId(existing);
    if (identityId < 0) {
      identityId = resolveQuasselIdentityId(session, null);
    }

    boolean enabled =
        request.enabled() != null
            ? request.enabled().booleanValue()
            : parseNetworkEnabled(existing);

    Map<String, Object> networkInfo =
        QuasselCoreNetworkRequests.networkInfoUpdatePayload(
            networkId, identityId, networkName, request, enabled);
    List<Object> params =
        List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkInfo", networkInfo));

    int resolvedIdentityId = identityId;
    session.outbound.send(
        socket,
        (codec, out) -> {
          log.debug(
              "Sending Quassel network sync call: serverId={}, className={}, objectName={}, slotName={}, paramCount=1, payload=NetworkInfo(identityId={}, summary={})",
              session.serverId,
              NETWORK_CLASS,
              Integer.toString(networkId),
              NETWORK_SET_INFO_SLOT,
              resolvedIdentityId,
              summarizeNetworkInfoForLog(networkInfo));
          codec.writeSignalProxySync(
              out, NETWORK_CLASS, Integer.toString(networkId), NETWORK_SET_INFO_SLOT, params);
        });

    observeKnownNetwork(session, networkId, networkName);
  }

  private void sendRemoveNetworkRequest(QuasselSession session, int networkId) throws Exception {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(
              out,
              RPC_REMOVE_NETWORK_SLOT,
              List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", networkId)));
        });
  }

  private void sendInput(
      QuasselSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      String userInput)
      throws Exception {
    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }

    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(
              out, "2sendInput(BufferInfo,QString)", List.of(bufferInfo, userInput));
        });
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

              QuasselSession session = requireEstablishedSession(sid, operation);
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
      QuasselSession session, String serverId, String operation, String rawLine) throws Exception {
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
      QuasselSession session, int bufferId, long markerMsgId) throws Exception {
    if (session == null) return;
    if (bufferId < 0 || markerMsgId <= 0L) return;

    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    int msgId = QuasselCoreHistorySupport.clampMsgId(markerMsgId);
    if (msgId <= 0) return;

    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", bufferId),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", msgId));
    session.outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxySync(
              out, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT, BUFFER_SYNCER_MARKER_SLOT, params);
          codec.writeSignalProxySync(
              out, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT, BUFFER_SYNCER_LAST_SEEN_SLOT, params);
        });
  }

  private QuasselCoreDatastreamCodec.BufferInfoValue resolveOutboundBufferInfo(
      QuasselSession session, int fallbackTypeBits, QualifiedTarget requestedTarget) {
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

  private static int firstKnownNetworkId(QuasselSession session) {
    return session == null
        ? -1
        : session.networks.firstKnownNetworkId(session.authResult.get(), session.buffers.values());
  }

  private static final class QuasselSession {
    private final String serverId;
    private final String initialNick;
    private final String connectedHost;
    private final int connectedPort;

    private final AtomicReference<Socket> socketRef = new AtomicReference<>();
    private final AtomicReference<Disposable> readLoopTask = new AtomicReference<>();
    private final AtomicReference<Disposable> readinessFallbackTask = new AtomicReference<>();
    private final AtomicReference<QuasselCoreProtocolProbe.ProbeSelection> probeSelection =
        new AtomicReference<>();
    private final AtomicReference<QuasselCoreAuthHandshake.AuthResult> authResult =
        new AtomicReference<>();
    private final AtomicReference<String> currentNick = new AtomicReference<>("");
    private final Map<Integer, String> networkCurrentNickByNetworkId = new ConcurrentHashMap<>();
    private final QuasselCoreNetworkCatalog networks;
    private final QuasselCoreIdentityState identities;
    private final QuasselCoreFeatureState features;
    private final QuasselCoreBufferCatalog buffers = new QuasselCoreBufferCatalog();
    private final QuasselCoreHistorySupport history = new QuasselCoreHistorySupport();
    private final QuasselCorePendingReadMarkers pendingReadMarkers =
        new QuasselCorePendingReadMarkers();
    private final QuasselCoreTargetNetworkHints targetNetworkHints =
        new QuasselCoreTargetNetworkHints();
    private final QuasselCoreChannelMembership membership;
    private final QuasselCoreLagTracker lag = new QuasselCoreLagTracker();
    private final AtomicBoolean nativeReadMarkerSupportObserved = new AtomicBoolean(false);
    private final AtomicBoolean syncObserved = new AtomicBoolean(false);
    private final AtomicBoolean connectionReadyEmitted = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final QuasselCoreBacklogTranslator backlog = new QuasselCoreBacklogTranslator();
    private final AtomicReference<QuasselSessionPhase> phase =
        new AtomicReference<>(QuasselSessionPhase.TRANSPORT_CONNECTING);
    private final AtomicReference<String> closeReason =
        new AtomicReference<>(DEFAULT_DISCONNECT_REASON);
    private final QuasselCoreSignalProxySender outbound;
    private final AtomicBoolean closeRequested = new AtomicBoolean(false);
    private final AtomicBoolean disconnectedEmitted = new AtomicBoolean(false);

    private QuasselSession(
        String serverId,
        String nick,
        String connectedHost,
        int connectedPort,
        QuasselCoreSignalProxySender sender,
        Consumer<String> identityObserved,
        Consumer<IrcEvent> eventObserved) {
      this.outbound = new QuasselCoreSerializedSignalProxySender(sender);
      this.membership = new QuasselCoreChannelMembership(eventObserved);
      this.features =
          new QuasselCoreFeatureState(MAX_NETWORK_IDENTITIES_PER_SESSION, eventObserved);
      this.identities =
          new QuasselCoreIdentityState(
              serverId, MAX_NETWORK_IDENTITIES_PER_SESSION, identityObserved);
      this.networks =
          new QuasselCoreNetworkCatalog(
              MAX_NETWORK_IDENTITIES_PER_SESSION, identityId -> identities.observe(identityId, ""));
      this.serverId = serverId;
      this.initialNick = nick;
      this.currentNick.set(nick);
      this.connectedHost = Objects.toString(connectedHost, "").trim();
      this.connectedPort = connectedPort;
    }
  }

  private enum QuasselSessionPhase {
    TRANSPORT_CONNECTING,
    TRANSPORT_CONNECTED,
    PROTOCOL_NEGOTIATED,
    AUTHENTICATING,
    SESSION_ESTABLISHED
  }
}
