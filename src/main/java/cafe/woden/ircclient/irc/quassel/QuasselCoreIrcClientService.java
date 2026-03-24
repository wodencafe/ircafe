package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
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
public class QuasselCoreIrcClientService implements IrcBackendClientService {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreIrcClientService.class);

  private static final int BUFFER_STATUS = 0x01;
  private static final int BUFFER_CHANNEL = 0x02;
  private static final int BUFFER_QUERY = 0x04;
  private static final int MESSAGE_TYPE_PLAIN = 0x0001;
  private static final int MESSAGE_TYPE_NOTICE = 0x0002;
  private static final int MESSAGE_TYPE_ACTION = 0x0004;
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
  private static final int UNKNOWN_MSG_ID = -1;
  private static final int HISTORY_LIMIT_DEFAULT = 50;
  private static final int HISTORY_LIMIT_MAX = 200;
  private static final String NETWORK_CLASS = "Network";
  private static final String NETWORK_SET_INFO_SLOT = "requestSetNetworkInfo";
  private static final String BACKLOG_MANAGER_CLASS = "BacklogManager";
  private static final String BACKLOG_MANAGER_OBJECT = "global";
  private static final String BACKLOG_REQUEST_SLOT = "requestBacklog(BufferId,MsgId,MsgId,int,int)";
  private static final String RPC_CREATE_IDENTITY_SLOT = "2createIdentity(Identity,QVariantMap)";
  private static final String RPC_CREATE_NETWORK_SLOT = "2createNetwork(NetworkInfo,QStringList)";
  private static final String RPC_CREATE_NETWORK_SLOT_LEGACY = "2createNetwork(NetworkInfo)";
  private static final String SYNC_CONNECT_NETWORK_SLOT = "requestConnect";
  private static final String SYNC_DISCONNECT_NETWORK_SLOT = "requestDisconnect";
  private static final String RPC_REMOVE_NETWORK_SLOT = "2removeNetwork(NetworkId)";
  private static final String BUFFER_SYNCER_CLASS = "BufferSyncer";
  private static final String BUFFER_SYNCER_OBJECT = "global";
  private static final String BUFFER_SYNCER_MARKER_SLOT = "requestSetMarkerLine(BufferId,MsgId)";
  private static final String BUFFER_SYNCER_LAST_SEEN_SLOT =
      "requestSetLastSeenMsg(BufferId,MsgId)";
  private static final long MIN_RECONNECT_DELAY_MS = 250L;
  private static final long LAG_SAMPLE_STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(2);
  private static final int MAX_BUFFER_INFOS_PER_SESSION = 8_192;
  private static final int MAX_HISTORY_TARGETS_PER_SESSION = 4_096;
  private static final int MAX_TARGET_NETWORK_HINTS_PER_SESSION = 4_096;
  private static final int MAX_NETWORK_NICKS_PER_SESSION = 256;
  private static final int MAX_NETWORK_IDENTITIES_PER_SESSION = 512;
  private static final int MAX_HISTORY_MSGID_SAMPLES_PER_TARGET = 512;
  private static final int MAX_PENDING_NETWORK_CREATE_NAMES = 32;
  private static final long PENDING_NETWORK_CREATE_NAME_TTL_MS = TimeUnit.MINUTES.toMillis(2);
  private static final String NETWORK_ADD_IRC_CHANNEL_SLOT = "addircchannel";
  private static final String NETWORK_REMOVE_IRC_CHANNEL_SLOT = "removeircchannel";
  private static final String NETWORK_QUALIFIER_PREFIX = "{net:";
  private static final String NETWORK_QUALIFIER_SUFFIX = "}";
  private static final Set<String> TARGET_ROUTED_RAW_COMMANDS =
      Set.of("PRIVMSG", "NOTICE", "TAGMSG", "MARKREAD", "REDACT");
  private static final Set<String> EXTRA_PARSED_ENVELOPE_COMMANDS =
      Set.of("CAP", "FAIL", "WARN", "NOTE");
  private static final DateTimeFormatter MARKREAD_TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);

  private static final String BACKEND_UNAVAILABLE_REASON = "Quassel Core backend is not connected";
  private static final String HANDSHAKE_INCOMPLETE_REASON =
      "Quassel protocol negotiated, but login/session handshake is not complete";
  private static final String DEFAULT_DISCONNECT_REASON = "Client requested disconnect";
  private static final String FEATURE_PHASE_PREFIX = "quassel-phase=";
  private static final String FEATURE_DETAIL_PREFIX = ";detail=";

  private static final String PHASE_PROTOCOL_NEGOTIATED = "protocol-negotiated";

  private static final String PHASE_SYNC_READY = "sync-ready";
  private static final String PHASE_SETUP_REQUIRED = "setup-required";
  private static final List<String> DEFAULT_SETUP_STORAGE_BACKENDS = List.of("SQLite");
  private static final List<String> DEFAULT_SETUP_AUTHENTICATORS = List.of("Database");
  private static final String DEFAULT_NETWORK_CODEC = "UTF-8";

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();
  private final FlowableProcessor<QuasselCoreNetworkSnapshotEvent> quasselNetworkEvents =
      PublishProcessor.<QuasselCoreNetworkSnapshotEvent>create().toSerialized();
  private final FlowableProcessor<QuasselIdentityObservedEvent> quasselIdentityEvents =
      PublishProcessor.<QuasselIdentityObservedEvent>create().toSerialized();
  private final Map<String, QuasselSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> availabilityReasonByServer = new ConcurrentHashMap<>();
  private final Map<String, QuasselCoreSetupPrompt> pendingSetupByServer =
      new ConcurrentHashMap<>();
  private final Map<String, Disposable> reconnectTasksByServer = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> reconnectAttemptsByServer = new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  private record QuasselIdentityObservedEvent(String serverId, int identityId) {}

  private final ServerCatalog serverCatalog;
  private final QuasselCoreSocketConnector socketConnector;
  private final QuasselCoreProtocolProbe protocolProbe;
  private final QuasselCoreAuthHandshake authHandshake;
  private final QuasselCoreDatastreamCodec datastreamCodec;
  private final IrcProperties.Reconnect reconnectPolicy;

  @Autowired
  public QuasselCoreIrcClientService(
      ServerCatalog serverCatalog,
      QuasselCoreSocketConnector socketConnector,
      QuasselCoreProtocolProbe protocolProbe,
      QuasselCoreAuthHandshake authHandshake,
      QuasselCoreDatastreamCodec datastreamCodec) {
    this(serverCatalog, socketConnector, protocolProbe, authHandshake, datastreamCodec, null);
  }

  public QuasselCoreIrcClientService(
      ServerCatalog serverCatalog,
      QuasselCoreSocketConnector socketConnector,
      QuasselCoreProtocolProbe protocolProbe,
      QuasselCoreAuthHandshake authHandshake,
      QuasselCoreDatastreamCodec datastreamCodec,
      IrcProperties props) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    this.socketConnector = Objects.requireNonNull(socketConnector, "socketConnector");
    this.protocolProbe = Objects.requireNonNull(protocolProbe, "protocolProbe");
    this.authHandshake = Objects.requireNonNull(authHandshake, "authHandshake");
    this.datastreamCodec = Objects.requireNonNull(datastreamCodec, "datastreamCodec");
    IrcProperties.Client client = props == null ? null : props.client();
    this.reconnectPolicy = client == null ? null : client.reconnect();
  }

  @PreDestroy
  void onDestroy() {
    shutdownNow();
  }

  @Override
  public void shutdownNow() {
    if (!shuttingDown.compareAndSet(false, true)) return;
    cancelAllReconnectTasks();
    for (Map.Entry<String, QuasselSession> entry : sessions.entrySet()) {
      closeSession(entry.getValue(), "Client shutting down", false);
    }
    sessions.clear();
    availabilityReasonByServer.clear();
    pendingSetupByServer.clear();
    reconnectAttemptsByServer.clear();
  }

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.QUASSEL_CORE;
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
                throw new IllegalArgumentException("server id is blank");
              }
              QuasselCoreSetupPrompt prompt = pendingSetupByServer.get(sid);
              if (prompt == null) {
                throw new IllegalStateException("Quassel Core setup is not pending for " + sid);
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              QuasselCoreSetupRequest req =
                  normalizeSetupRequest(prompt, Objects.requireNonNull(request, "request"));

              cancelReconnectTask(sid, true);
              resetReconnectAttempts(sid);
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
    return quasselNetworkEvents.onBackpressureBuffer();
  }

  @Override
  public Completable quasselCoreConnectNetwork(String serverId, String networkIdOrName) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
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
                    session.networkDisplayByNetworkId,
                    session.networkStateByNetworkId.keySet());
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
                throw new IllegalArgumentException("server id is blank");
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
                throw new IllegalArgumentException("server id is blank");
              }
              QuasselSession session = requireEstablishedSession(sid, "quassel create network");
              QuasselCoreNetworkCreateRequest req =
                  normalizeQuasselCoreCreateRequest(Objects.requireNonNull(request, "request"));
              log.debug(
                  "Quassel create network preflight: serverId={}, requestedIdentityId={}, knownIdentityIds={}, identityStateKeys={}, identityNames={}, authNetworkIds={}",
                  sid,
                  req.identityId(),
                  session.knownIdentityIds,
                  session.identityStateByIdentityId.keySet(),
                  session.identityNameByIdentityId,
                  Optional.ofNullable(session.authResult.get())
                      .map(QuasselCoreAuthHandshake.AuthResult::networkIds)
                      .orElse(List.of()));
              if (req.identityId() == null && !hasKnownIdentity(session)) {
                maybeCreateDefaultIdentityForNetwork(session, sid, req);
              }
              int identityId = resolveQuasselIdentityId(session, req.identityId());
              log.debug(
                  "Quassel create network request: serverId={}, networkName={}, host={}, port={}, tls={}, verifyTls={}, autoJoinCount={}, requestedIdentityId={}, resolvedIdentityId={}",
                  sid,
                  req.networkName(),
                  req.serverHost(),
                  req.serverPort(),
                  req.useTls(),
                  req.verifyTls(),
                  req.autoJoinChannels() == null ? 0 : req.autoJoinChannels().size(),
                  req.identityId(),
                  identityId);
              Set<Integer> baselineNetworkIds = Set.copyOf(collectKnownNetworkIds(session));
              sendCreateNetworkRequest(session, identityId, req);
              maybeRetryLegacyCreateNetworkSlot(session, identityId, req, baselineNetworkIds);
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
                throw new IllegalArgumentException("server id is blank");
              }
              QuasselSession session = requireEstablishedSession(sid, "quassel update network");
              int networkId =
                  resolveQuasselNetworkId(session, sid, networkIdOrName, "quassel update network");
              QuasselCoreNetworkUpdateRequest req =
                  normalizeQuasselCoreUpdateRequest(Objects.requireNonNull(request, "request"));
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
                throw new IllegalArgumentException("server id is blank");
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
                throw new IllegalArgumentException("server id is blank");
              }
              cancelReconnectTask(sid, false);
              if (resetReconnectAttempts) {
                resetReconnectAttempts(sid);
              }
              availabilityReasonByServer.put(sid, "Quassel transport is connecting");

              IrcProperties.Server server = serverCatalog.require(sid);
              QuasselSession existing = sessions.get(sid);
              if (existing != null) return;

              String nick = configuredNick(server);
              QuasselSession next = new QuasselSession(sid, nick, server.host(), server.port());
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
              cancelReconnectTask(sid, true);
              resetReconnectAttempts(sid);

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
              String raw = Objects.toString(rawLine, "").trim();
              if (sid.isEmpty()) throw new IllegalArgumentException("server id is blank");
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
                throw new IllegalArgumentException("server id is blank");
              }
              QuasselSession session = requireEstablishedSession(sid, "send typing");
              if (!isTypingAvailable(sid)) {
                String reason = Objects.toString(typingAvailabilityReason(sid), "").trim();
                String suffix = reason.isEmpty() ? "" : (" (" + reason + ")");
                throw new IllegalStateException(
                    "Typing indicators not available (requires message-tags and typing capability)"
                        + suffix
                        + ": "
                        + sid);
              }

              String normalizedState = normalizeTypingState(state);
              if (normalizedState.isEmpty()) return;
              QualifiedTarget dest = sanitizeHistoryTarget(target);
              sendRawInternal(
                  session,
                  sid,
                  "send typing",
                  "@+typing=" + normalizedState + " TAGMSG " + dest.rawTarget());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
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
              long markerMsgId = resolveHistoryMsgIdByTimestamp(session, markerTarget, at);
              if (markerMsgId > 0L && bufferInfo.bufferId() >= 0) {
                sendBufferSyncerReadMarkerUpdate(session, bufferInfo.bufferId(), markerMsgId);
                return;
              }

              String markerTimestamp = MARKREAD_TS_FMT.format(at);
              sendRawInternal(
                  session,
                  sid,
                  "send read marker",
                  "MARKREAD " + requested.rawTarget() + " timestamp=" + markerTimestamp);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit) {
    return Completable.fromAction(
            () -> {
              HistoryRequestContext ctx =
                  prepareHistoryRequest(serverId, target, limit, "request chat history");
              Instant before = beforeExclusive == null ? Instant.now() : beforeExclusive;
              long anchorMsgId =
                  resolveHistoryMsgIdByTimestamp(ctx.session(), ctx.target(), before);
              int lastMsgId = anchorMsgId > 0 ? clampMsgId(anchorMsgId - 1L) : UNKNOWN_MSG_ID;
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
              HistoryRequestContext ctx =
                  prepareHistoryRequest(serverId, target, limit, "request chat history");
              HistorySelector parsed = parseHistorySelector(selector, false);
              long anchorMsgId = resolveHistorySelectorMsgId(ctx.session(), ctx.target(), parsed);
              int lastMsgId = anchorMsgId > 0 ? clampMsgId(anchorMsgId - 1L) : UNKNOWN_MSG_ID;
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
              HistoryRequestContext ctx =
                  prepareHistoryRequest(serverId, target, limit, "request latest chat history");
              HistorySelector parsed = parseHistorySelector(selector, true);

              int firstMsgId = UNKNOWN_MSG_ID;
              if (parsed.kind() != HistorySelectorKind.WILDCARD) {
                long anchorMsgId = resolveHistorySelectorMsgId(ctx.session(), ctx.target(), parsed);
                if (anchorMsgId > 0) {
                  firstMsgId = clampMsgId(anchorMsgId + 1L);
                }
              }
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
              HistoryRequestContext ctx =
                  prepareHistoryRequest(serverId, target, limit, "request bounded chat history");
              HistorySelector start = parseHistorySelector(startSelector, true);
              HistorySelector end = parseHistorySelector(endSelector, true);

              long startMsgId = resolveHistorySelectorMsgId(ctx.session(), ctx.target(), start);
              long endMsgId = resolveHistorySelectorMsgId(ctx.session(), ctx.target(), end);
              int firstMsgId = startMsgId > 0 ? clampMsgId(startMsgId) : UNKNOWN_MSG_ID;
              int lastMsgId = endMsgId > 0 ? clampMsgId(endMsgId) : UNKNOWN_MSG_ID;
              if (firstMsgId != UNKNOWN_MSG_ID
                  && lastMsgId != UNKNOWN_MSG_ID
                  && firstMsgId > lastMsgId) {
                int tmp = firstMsgId;
                firstMsgId = lastMsgId;
                lastMsgId = tmp;
              }

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
              HistoryRequestContext ctx =
                  prepareHistoryRequest(
                      serverId, target, limit, "request surrounding chat history");
              HistorySelector parsed = parseHistorySelector(selector, false);
              long anchorMsgId = resolveHistorySelectorMsgId(ctx.session(), ctx.target(), parsed);

              int firstMsgId = UNKNOWN_MSG_ID;
              int lastMsgId = UNKNOWN_MSG_ID;
              if (anchorMsgId > 0) {
                int halfWindow = Math.max(1, ctx.limit() / 2);
                firstMsgId = clampMsgId(Math.max(1L, anchorMsgId - halfWindow));
                lastMsgId = clampMsgId(anchorMsgId + halfWindow);
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
  public boolean isDraftReplyAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "draft/reply");
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "draft/react");
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "draft/unreact", "draft/react");
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "multiline", "draft/multiline");
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null) return 0L;
    MultilineLimitState limits = multilineLimitsForPreferredNetwork(session);
    if (limits == null) return 0L;
    return Math.max(0L, limits.maxBytes());
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null) return 0;
    MultilineLimitState limits = multilineLimitsForPreferredNetwork(session);
    if (limits == null) return 0;
    long max = Math.max(0L, limits.maxLines());
    if (max <= 0L) return 0;
    if (max >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return (int) max;
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "draft/message-edit");
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "draft/message-redaction");
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null) return false;
    return typingCapabilityEnabledOrUnknown(session);
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null) {
      return backendAvailabilityReason(serverId);
    }
    if (typingCapabilityEnabledOrUnknown(session)) {
      return "";
    }
    if (!session.capabilitySnapshotObserved.get()) {
      return "typing capability status is not yet available from Quassel backend state";
    }
    if (!hasCapabilityAny(session, "message-tags")) {
      return "message-tags not negotiated in Quassel backend network state";
    }
    return "typing capability not negotiated in Quassel backend network state";
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "read-marker", "draft/read-marker");
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "labeled-response");
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    return capabilityEnabledOrUnknown(session, "standard-replies");
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null) return false;
    MonitorSupportState monitor = monitorSupportForPreferredNetwork(session);
    return monitor != null && monitor.available();
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    QuasselSession session = findEstablishedSession(serverId);
    if (session == null) return 0;
    MonitorSupportState monitor = monitorSupportForPreferredNetwork(session);
    if (monitor == null || !monitor.available()) return 0;
    long max = Math.max(0L, monitor.limit());
    if (max <= 0L) return 0;
    if (max >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return (int) max;
  }

  @Override
  public Completable requestLagProbe(String serverId) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              QuasselSession session = requireEstablishedSession(sid, "request lag probe");
              Socket socket = session.socketRef.get();
              if (socket == null) {
                throw new IllegalStateException("Quassel socket is closed");
              }

              long nowMs = System.currentTimeMillis();
              QuasselCoreDatastreamCodec.QtDateTimeValue token =
                  QuasselCoreDatastreamCodec.utcDateTimeFromEpochMs(nowMs);
              session.lagProbeToken.set(token);
              session.lagProbeSentAtMs.set(nowMs);

              try {
                OutputStream out = socket.getOutputStream();
                synchronized (session.writeLock) {
                  datastreamCodec.writeSignalProxyHeartBeat(out, token);
                }
              } catch (Exception e) {
                session.lagProbeToken.set(null);
                session.lagProbeSentAtMs.set(0L);
                throw e;
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public OptionalLong lastMeasuredLagMs(String serverId) {
    QuasselSession session = sessions.get(normalizeServerId(serverId));
    if (session == null || session.socketRef.get() == null) return OptionalLong.empty();
    long measuredAt = session.lagLastMeasuredAtMs.get();
    if (measuredAt <= 0L) return OptionalLong.empty();
    long ageMs = Math.max(0L, System.currentTimeMillis() - measuredAt);
    if (ageMs > LAG_SAMPLE_STALE_AFTER_MS) return OptionalLong.empty();
    long lagMs = Math.max(0L, session.lagLastMeasuredMs.get());
    return OptionalLong.of(lagMs);
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

  private boolean capabilityEnabledOrUnknown(QuasselSession session, String... capabilities) {
    if (session == null) return false;
    if (!session.capabilitySnapshotObserved.get()) return false;
    return hasCapabilityAny(session, capabilities);
  }

  private boolean typingCapabilityEnabledOrUnknown(QuasselSession session) {
    if (session == null) return false;
    if (!session.capabilitySnapshotObserved.get()) return false;
    boolean messageTags = hasCapabilityAny(session, "message-tags");
    boolean typing = hasCapabilityAny(session, "typing", "draft/typing");
    return messageTags && typing;
  }

  private MonitorSupportState monitorSupportForPreferredNetwork(QuasselSession session) {
    if (session == null || session.monitorSupportByNetworkId.isEmpty()) return null;
    int primary = primaryNetworkId(session);
    if (primary >= 0) {
      MonitorSupportState byPrimary = session.monitorSupportByNetworkId.get(primary);
      if (byPrimary != null) return byPrimary;
    }
    for (MonitorSupportState state : session.monitorSupportByNetworkId.values()) {
      if (state != null) return state;
    }
    return null;
  }

  private MultilineLimitState multilineLimitsForPreferredNetwork(QuasselSession session) {
    if (session == null || session.multilineLimitsByNetworkId.isEmpty()) return null;
    int primary = primaryNetworkId(session);
    if (primary >= 0) {
      MultilineLimitState byPrimary = session.multilineLimitsByNetworkId.get(primary);
      if (byPrimary != null) return byPrimary;
    }
    for (MultilineLimitState state : session.multilineLimitsByNetworkId.values()) {
      if (state != null) return state;
    }
    return null;
  }

  private boolean hasCapabilityAny(QuasselSession session, String... capabilities) {
    if (session == null || capabilities == null || capabilities.length == 0) return false;
    if (session.enabledCapabilitiesByNetworkId.isEmpty()) return false;
    HashSet<String> wanted = new HashSet<>();
    for (String cap : capabilities) {
      String token = canonicalCapabilityToken(cap);
      if (!token.isEmpty()) {
        wanted.add(token);
      }
    }
    if (wanted.isEmpty()) return false;
    for (Set<String> enabled : session.enabledCapabilitiesByNetworkId.values()) {
      if (enabled == null || enabled.isEmpty()) continue;
      for (String cap : wanted) {
        if (enabled.contains(cap)) {
          return true;
        }
      }
    }
    return false;
  }

  private HistoryRequestContext prepareHistoryRequest(
      String serverId, String target, int limit, String operation)
      throws BackendNotAvailableException {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      throw new IllegalArgumentException("server id is blank");
    }
    QualifiedTarget tgt = sanitizeHistoryTarget(target);
    int lim = normalizeHistoryLimit(limit);
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
        findBufferByName(session, target.baseTarget(), typeBitsHint, preferredNetworkId);
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

    OutputStream out = socket.getOutputStream();
    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", bufferInfo.bufferId()),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", firstMsgId),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", lastMsgId),
            limit,
            0);
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxySync(
          out, BACKLOG_MANAGER_CLASS, BACKLOG_MANAGER_OBJECT, BACKLOG_REQUEST_SLOT, params);
    }
  }

  private static HistorySelector parseHistorySelector(String selector, boolean wildcardAllowed) {
    String raw = Objects.toString(selector, "").trim();
    if (wildcardAllowed && "*".equals(raw)) {
      return new HistorySelector(HistorySelectorKind.WILDCARD, UNKNOWN_MSG_ID, null);
    }

    String normalized = Ircv3ChatHistoryCommandBuilder.sanitizeSelector(raw);
    int eq = normalized.indexOf('=');
    String key = normalized.substring(0, eq).trim().toLowerCase(Locale.ROOT);
    String value = normalized.substring(eq + 1).trim();
    if ("msgid".equals(key)) {
      return new HistorySelector(HistorySelectorKind.MSGID, parsePositiveMsgId(value), null);
    }
    if ("timestamp".equals(key)) {
      try {
        return new HistorySelector(
            HistorySelectorKind.TIMESTAMP, UNKNOWN_MSG_ID, Instant.parse(value));
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("timestamp selector must be ISO-8601 (UTC)", e);
      }
    }
    throw new IllegalArgumentException(
        "Quassel history selectors support only msgid=... and timestamp=...");
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

  private long resolveHistoryMsgIdByTimestamp(
      QuasselSession session, String target, Instant timestamp) {
    if (session == null) return UNKNOWN_MSG_ID;
    String key = normalizeHistoryTargetKey(target);
    if (key.isEmpty()) return UNKNOWN_MSG_ID;
    TargetHistoryState state = session.historyByTarget.get(key);
    if (state == null) return UNKNOWN_MSG_ID;
    return state.anchorForTimestamp(timestamp);
  }

  private long resolveHistoryTimestampByMsgId(QuasselSession session, String target, long msgId) {
    if (session == null || msgId <= 0L) return UNKNOWN_MSG_ID;
    String key = normalizeHistoryTargetKey(target);
    if (key.isEmpty()) return UNKNOWN_MSG_ID;
    TargetHistoryState state = session.historyByTarget.get(key);
    if (state == null) return UNKNOWN_MSG_ID;
    return state.timestampForMsgId(msgId);
  }

  private void noteHistoryObservation(
      QuasselSession session, String target, long messageId, Instant at) {
    if (session == null || messageId <= 0) return;
    String key = normalizeHistoryTargetKey(target);
    if (key.isEmpty()) return;
    Instant when = at == null ? Instant.now() : at;
    session
        .historyByTarget
        .computeIfAbsent(key, ignored -> new TargetHistoryState())
        .observe(messageId, when.toEpochMilli());
    trimMapToMaxSize(session.historyByTarget, MAX_HISTORY_TARGETS_PER_SESSION);
  }

  private void noteTargetNetworkHint(
      QuasselSession session, String target, int networkId, boolean preferObservedNetwork) {
    if (session == null || networkId < 0) return;
    observeKnownNetwork(session, networkId, "");
    String key = normalizeTargetHintKey(target);
    if (key.isEmpty()) return;

    if (preferObservedNetwork) {
      session.targetNetworkHintsByTargetLower.put(key, networkId);
      trimMapToMaxSize(
          session.targetNetworkHintsByTargetLower, MAX_TARGET_NETWORK_HINTS_PER_SESSION);
      return;
    }

    int preferredDefault = firstKnownNetworkId(session);
    if (preferredDefault >= 0 && networkId != preferredDefault) {
      session.targetNetworkHintsByTargetLower.putIfAbsent(key, preferredDefault);
      trimMapToMaxSize(
          session.targetNetworkHintsByTargetLower, MAX_TARGET_NETWORK_HINTS_PER_SESSION);
      return;
    }
    session.targetNetworkHintsByTargetLower.putIfAbsent(key, networkId);
    trimMapToMaxSize(session.targetNetworkHintsByTargetLower, MAX_TARGET_NETWORK_HINTS_PER_SESSION);
  }

  private int preferredNetworkIdForTarget(
      QuasselSession session, String target, String networkToken) {
    if (session == null) return -1;
    String token = Objects.toString(networkToken, "").trim().toLowerCase(Locale.ROOT);
    if (!token.isEmpty()) {
      Integer byToken = session.networkIdByTokenLower.get(token);
      if (byToken != null && byToken.intValue() >= 0) {
        return byToken.intValue();
      }
    }
    String key = normalizeTargetHintKey(target);
    if (!key.isEmpty()) {
      Integer hinted = session.targetNetworkHintsByTargetLower.get(key);
      if (hinted != null && hinted.intValue() >= 0) {
        return hinted.intValue();
      }
    }
    return firstKnownNetworkId(session);
  }

  private static QualifiedTarget sanitizeHistoryTarget(String target) {
    QualifiedTarget parsed = parseQualifiedTarget(target);
    String base = parsed.baseTarget();
    if (base.isEmpty()) {
      throw new IllegalArgumentException("target is blank");
    }
    if (containsCrlf(base)) {
      throw new IllegalArgumentException("target contains CR/LF");
    }
    if (base.indexOf(' ') >= 0) {
      throw new IllegalArgumentException("target contains spaces");
    }
    return parsed;
  }

  private static int normalizeHistoryLimit(int limit) {
    int lim = limit <= 0 ? HISTORY_LIMIT_DEFAULT : limit;
    if (lim > HISTORY_LIMIT_MAX) return HISTORY_LIMIT_MAX;
    return lim;
  }

  private static long parsePositiveMsgId(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("msgid selector is blank");
    }
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0L) {
        throw new IllegalArgumentException("msgid selector must be a positive integer");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("msgid selector must be numeric for Quassel backlog", e);
    }
  }

  private static int clampMsgId(long value) {
    if (value <= 0L) return UNKNOWN_MSG_ID;
    if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return (int) value;
  }

  private static String normalizeHistoryTargetKey(String target) {
    String normalized = Objects.toString(target, "").trim();
    if (normalized.isEmpty()) return "";
    return normalized.toLowerCase(Locale.ROOT);
  }

  private static String normalizeTargetHintKey(String target) {
    QualifiedTarget parsed = parseQualifiedTarget(target);
    String normalized = Objects.toString(parsed.baseTarget(), "").trim();
    if (normalized.isEmpty()) return "";
    return normalized.toLowerCase(Locale.ROOT);
  }

  private static String normalizeMembershipKey(String target, int networkId) {
    QualifiedTarget parsed = parseQualifiedTarget(target);
    String base = Objects.toString(parsed.baseTarget(), "").trim().toLowerCase(Locale.ROOT);
    if (base.isEmpty()) return "";
    if (networkId >= 0) {
      return networkId + "|" + base;
    }
    String token = Objects.toString(parsed.networkToken(), "").trim().toLowerCase(Locale.ROOT);
    if (!token.isEmpty()) {
      return "net:" + token + "|" + base;
    }
    return "global|" + base;
  }

  private static boolean markChannelMembershipJoined(
      QuasselSession session, String target, int networkId) {
    if (session == null) return false;
    String key = normalizeMembershipKey(target, networkId);
    if (key.isEmpty()) return false;
    return session.joinedChannelMembershipKeys.add(key);
  }

  private static boolean markChannelMembershipLeft(
      QuasselSession session, String target, int networkId) {
    if (session == null) return false;
    String key = normalizeMembershipKey(target, networkId);
    if (key.isEmpty()) return false;
    return session.joinedChannelMembershipKeys.remove(key);
  }

  private static QualifiedTarget parseQualifiedTarget(String target) {
    String raw = Objects.toString(target, "").trim();
    if (raw.isEmpty()) return new QualifiedTarget("", "", "");
    if (raw.endsWith(NETWORK_QUALIFIER_SUFFIX)) {
      int marker = raw.lastIndexOf(NETWORK_QUALIFIER_PREFIX);
      if (marker > 0) {
        int tokenStart = marker + NETWORK_QUALIFIER_PREFIX.length();
        int tokenEnd = raw.length() - NETWORK_QUALIFIER_SUFFIX.length();
        if (tokenEnd > tokenStart) {
          String base = raw.substring(0, marker).trim();
          String token = raw.substring(tokenStart, tokenEnd).trim().toLowerCase(Locale.ROOT);
          if (!base.isEmpty() && !token.isEmpty()) {
            return new QualifiedTarget(raw, base, token);
          }
        }
      }
    }
    return new QualifiedTarget(raw, raw, "");
  }

  private void scheduleReconnectIfEligible(QuasselSession session, String reason) {
    if (session == null || shuttingDown.get() || session.closeRequested.get()) return;
    if (!session.reconnectScheduled.compareAndSet(false, true)) return;
    scheduleReconnect(session.serverId, reason);
  }

  private void scheduleReconnect(String serverId, String reason) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || shuttingDown.get()) return;
    IrcProperties.Reconnect policy = reconnectPolicy;
    if (policy == null || !policy.enabled()) return;
    if (!serverCatalog.containsId(sid)) return;

    AtomicLong attempts =
        reconnectAttemptsByServer.computeIfAbsent(sid, ignored -> new AtomicLong(0L));
    long attempt = attempts.incrementAndGet();
    if (policy.maxAttempts() > 0 && attempt > policy.maxAttempts()) {
      bus.onNext(
          new ServerIrcEvent(
              sid,
              new IrcEvent.Error(Instant.now(), "Reconnect aborted (max attempts reached)", null)));
      return;
    }

    long delayMs = computeReconnectDelayMs(policy, attempt);
    bus.onNext(
        new ServerIrcEvent(
            sid,
            new IrcEvent.Reconnecting(
                Instant.now(), attempt, delayMs, Objects.toString(reason, "Disconnected"))));

    Disposable scheduled =
        RxVirtualSchedulers.io()
            .scheduleDirect(
                () -> {
                  if (shuttingDown.get()) return;
                  if (!serverCatalog.containsId(sid)) {
                    bus.onNext(
                        new ServerIrcEvent(
                            sid,
                            new IrcEvent.Error(
                                Instant.now(), "Reconnect cancelled (server removed)", null)));
                    return;
                  }
                  var unused =
                      connectInternal(sid, false)
                          .subscribe(
                              () -> {},
                              err -> {
                                String detail = renderThrowableMessage(err);
                                String message =
                                    detail.isEmpty()
                                        ? "Reconnect attempt failed"
                                        : ("Reconnect attempt failed: " + detail);
                                bus.onNext(
                                    new ServerIrcEvent(
                                        sid, new IrcEvent.Error(Instant.now(), message, err)));
                                scheduleReconnect(sid, "Reconnect attempt failed");
                              });
                },
                delayMs,
                TimeUnit.MILLISECONDS);

    Disposable previous = reconnectTasksByServer.put(sid, scheduled);
    if (previous != null && !previous.isDisposed()) {
      try {
        previous.dispose();
      } catch (Exception ignored) {
      }
    }
  }

  private void cancelReconnectTask(String serverId, boolean clearAttempts) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    Disposable reconnectTask = reconnectTasksByServer.remove(sid);
    if (reconnectTask != null && !reconnectTask.isDisposed()) {
      try {
        reconnectTask.dispose();
      } catch (Exception ignored) {
      }
    }
    if (clearAttempts) {
      reconnectAttemptsByServer.remove(sid);
    }
  }

  private void resetReconnectAttempts(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    reconnectAttemptsByServer.remove(sid);
  }

  private void cancelAllReconnectTasks() {
    for (Map.Entry<String, Disposable> entry : reconnectTasksByServer.entrySet()) {
      Disposable task = entry.getValue();
      if (task != null && !task.isDisposed()) {
        try {
          task.dispose();
        } catch (Exception ignored) {
        }
      }
    }
    reconnectTasksByServer.clear();
  }

  private static long computeReconnectDelayMs(IrcProperties.Reconnect policy, long attempt) {
    if (policy == null) return MIN_RECONNECT_DELAY_MS;
    double multiplier = Math.pow(policy.multiplier(), Math.max(0L, attempt - 1L));
    long baseDelay = policy.initialDelayMs();
    long cappedDelay = (long) Math.min(baseDelay * multiplier, (double) policy.maxDelayMs());
    double jitterPct = policy.jitterPct();
    if (jitterPct <= 0d) {
      return Math.max(MIN_RECONNECT_DELAY_MS, cappedDelay);
    }
    double jitterFactor = 1.0 + ThreadLocalRandom.current().nextDouble(-jitterPct, jitterPct);
    long jittered = (long) Math.max(0L, cappedDelay * jitterFactor);
    return Math.max(MIN_RECONNECT_DELAY_MS, jittered);
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
      session.bufferInfosById.clear();
      session.bufferInfosById.putAll(auth.initialBuffers());
      trimMapToMaxSize(session.bufferInfosById, MAX_BUFFER_INFOS_PER_SESSION);
      session.targetNetworkHintsByTargetLower.clear();
      session.networkDisplayByNetworkId.clear();
      session.networkTokenByNetworkId.clear();
      session.networkIdByTokenLower.clear();
      session.networkStateByNetworkId.clear();
      session.removedNetworkIds.clear();
      session.identityStateByIdentityId.clear();
      session.identityNameByIdentityId.clear();
      session.knownIdentityIds.clear();
      session.networkCurrentNickByNetworkId.clear();
      session.enabledCapabilitiesByNetworkId.clear();
      session.monitorSupportByNetworkId.clear();
      session.multilineLimitsByNetworkId.clear();
      session.pendingCreatedNetworkNames.clear();
      session.capabilitySnapshotObserved.set(false);
      observeKnownNetworks(session, auth);
      observeKnownIdentities(session, auth);
      int primaryNetworkId = primaryNetworkId(session);
      if (primaryNetworkId >= 0) {
        session.networkCurrentNickByNetworkId.put(primaryNetworkId, session.initialNick);
      }
      for (QuasselCoreDatastreamCodec.BufferInfoValue initial : session.bufferInfosById.values()) {
        if (initial == null) continue;
        observeKnownNetwork(session, initial.networkId(), "");
        noteTargetNetworkHint(session, initial.bufferName(), initial.networkId(), false);
      }
      trimMapToMaxSize(session.networkCurrentNickByNetworkId, MAX_NETWORK_NICKS_PER_SESSION);
      session.lagProbeToken.set(null);
      session.lagProbeSentAtMs.set(0L);
      session.lagLastMeasuredMs.set(-1L);
      session.lagLastMeasuredAtMs.set(0L);
      session.syncObserved.set(false);
      session.connectionReadyEmitted.set(false);
      session.reconnectScheduled.set(false);
      session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
      availabilityReasonByServer.remove(sid);
      pendingSetupByServer.remove(sid);
      resetReconnectAttempts(sid);
      cancelReconnectTask(sid, false);

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
      pendingSetupByServer.put(sid, buildSetupPrompt(sid, reason, e.setupFields()));
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
    OutputStream out = socket.getOutputStream();
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxyHeartBeatReply(out, timestamp);
    }
  }

  private void handleHeartbeatReply(QuasselSession session, List<Object> params) {
    if (session == null || params == null || params.isEmpty()) return;
    Object value = params.get(0);
    if (!(value instanceof QuasselCoreDatastreamCodec.QtDateTimeValue timestamp)) return;

    QuasselCoreDatastreamCodec.QtDateTimeValue expected = session.lagProbeToken.get();
    if (expected == null || !expected.equals(timestamp)) return;
    if (!session.lagProbeToken.compareAndSet(expected, null)) return;

    long nowMs = System.currentTimeMillis();
    long sentAtMs = session.lagProbeSentAtMs.getAndSet(0L);
    long fallbackSentMs = QuasselCoreDatastreamCodec.epochMsFromQtDateTime(timestamp);
    long effectiveSentAt = sentAtMs > 0L ? sentAtMs : fallbackSentMs;
    long lagMs = Math.max(0L, nowMs - effectiveSentAt);
    session.lagLastMeasuredMs.set(lagMs);
    session.lagLastMeasuredAtMs.set(nowMs);
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
        QuasselCoreDatastreamCodec.BufferInfoValue merged =
            session.bufferInfosById.merge(
                info.bufferId(), info, QuasselCoreIrcClientService::mergeBufferInfo);
        trimMapToMaxSize(session.bufferInfosById, MAX_BUFFER_INFOS_PER_SESSION);
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
        session.bufferInfosById.remove(info.bufferId());
      }
    }

    observeNetworkLifecycleFromRpcSlot(session, slot, params);
    observeIdentityLifecycleFromRpcSlot(session, slot, params);
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

  private void observeIdentityLifecycleFromRpcSlot(
      QuasselSession session, String slotName, List<Object> params) {
    if (session == null) return;
    String slot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    if (slot.isEmpty() || !slot.contains("identity")) return;
    boolean remove = slot.contains("remove") || slot.contains("deleted");
    if (params == null || params.isEmpty()) {
      log.debug(
          "Observed identity lifecycle RPC with no params: serverId={}, slot={}, remove={}",
          session.serverId,
          slotName,
          remove);
      return;
    }

    for (Object param : params) {
      observeIdentityLifecycleFromRpcParam(session, slotName, slot, param, remove);
    }
  }

  private void observeIdentityLifecycleFromRpcParam(
      QuasselSession session, String slotName, String slotLower, Object raw, boolean remove) {
    if (session == null || raw == null) return;
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        observeIdentityLifecycleFromRpcParam(session, slotName, slotLower, value, remove);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      String type = Objects.toString(userType.typeName(), "").trim();
      Object value = userType.value();
      if ("IdentityId".equals(type)) {
        int identityId = tryParseInt(value);
        if (identityId >= 0) {
          if (remove) {
            session.knownIdentityIds.remove(identityId);
            session.identityStateByIdentityId.remove(identityId);
            session.identityNameByIdentityId.remove(identityId);
          } else {
            observeKnownIdentity(session, identityId, "");
          }
          log.debug(
              "Observed identity lifecycle RPC id user-type: serverId={}, slot={}, remove={}, identityId={}",
              session.serverId,
              slotName,
              remove,
              identityId);
        }
      }
      observeIdentityLifecycleFromRpcParam(session, slotName, slotLower, value, remove);
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      int identityId = identityIdFromStateMap(map, -1);
      String identityName = parseIdentityName(map);
      if (identityId >= 0) {
        if (remove) {
          session.knownIdentityIds.remove(identityId);
          session.identityStateByIdentityId.remove(identityId);
          session.identityNameByIdentityId.remove(identityId);
        } else {
          observeKnownIdentity(session, identityId, identityName);
          session.identityStateByIdentityId.put(identityId, normalizeObjectMap(map));
          trimMapToMaxSize(session.identityStateByIdentityId, MAX_NETWORK_IDENTITIES_PER_SESSION);
        }
      }
      log.debug(
          "Observed identity lifecycle RPC map: serverId={}, slot={}, remove={}, identityId={}, identityName={}, map={}",
          session.serverId,
          slotName,
          remove,
          identityId,
          identityName,
          map);
      return;
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
          String observedName = createLike ? claimPendingCreatedNetworkName(session) : "";
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
      String observedName = createLike ? claimPendingCreatedNetworkName(session) : "";
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
      handleCoreInfoStateSync(session, objectName, slotToken, values);
      return;
    }

    if ("Identity".equals(classToken)) {
      handleIdentityStateSync(session, objectName, values);
      return;
    }

    if ("Network".equals(classToken)) {
      observeChannelMembershipFromNetworkSync(session, objectName, slotToken, values);
      maybeUpdateCurrentNickFromNetworkState(session, objectName, values);
      observeMaybeNetworkStateFromUnknownSync(session, classToken, objectName, slotToken, values);
      return;
    }

    if ("IrcUser".equals(classToken)) {
      handleIrcUserStateSync(session, objectName, values);
      return;
    }

    if ("IrcChannel".equals(classToken)) {
      handleIrcChannelStateSync(session, objectName, values);
      return;
    }

    if ("NetworkInfo".equals(classToken)) {
      handleNetworkInfoStateSync(session, objectName, values);
      return;
    }

    if (classToken.toLowerCase(Locale.ROOT).contains("identity")) {
      observeIdentitiesFromUnknownState(
          session, values, parseNetworkId(objectName), classToken, objectName, slotToken);
    }
    observeMaybeNetworkStateFromUnknownSync(session, classToken, objectName, slotToken, values);
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
    String normalizedSlot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    if (normalizedSlot.isEmpty()) return;

    boolean isAdd = normalizedSlot.contains(NETWORK_ADD_IRC_CHANNEL_SLOT);
    boolean isRemove = normalizedSlot.contains(NETWORK_REMOVE_IRC_CHANNEL_SLOT);
    if (!isAdd && !isRemove) return;

    int networkId = parseNetworkId(objectName);
    ArrayList<String> candidates = new ArrayList<>();
    collectChannelNamesFromLifecyclePayload(values, candidates);
    if (candidates.isEmpty()) {
      String fallback = parseObjectLeafToken(objectName);
      if (looksLikeChannel(fallback)) {
        candidates.add(fallback);
      }
    }
    if (candidates.isEmpty()) return;

    LinkedHashSet<String> uniqueChannels = new LinkedHashSet<>();
    for (String raw : candidates) {
      String channel = Objects.toString(raw, "").trim();
      if (looksLikeChannel(channel)) {
        uniqueChannels.add(channel);
      }
    }
    if (uniqueChannels.isEmpty()) return;

    Instant now = Instant.now();
    for (String channel : uniqueChannels) {
      String qualified = qualifyTargetForNetwork(session, channel, networkId);
      if (isAdd) {
        if (markChannelMembershipJoined(session, qualified, networkId)) {
          bus.onNext(
              new ServerIrcEvent(session.serverId, new IrcEvent.JoinedChannel(now, qualified)));
        }
      } else {
        markChannelMembershipLeft(session, qualified, networkId);
      }
    }
  }

  private static void collectChannelNamesFromLifecyclePayload(Object raw, List<String> out) {
    if (raw == null || out == null) return;
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectChannelNamesFromLifecyclePayload(value, out);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      collectChannelNamesFromLifecyclePayload(userType.value(), out);
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.BufferInfoValue info) {
      String channel = Objects.toString(info.bufferName(), "").trim();
      if (!channel.isEmpty()) out.add(channel);
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      String channel =
          firstNonBlank(
              mapValueIgnoreCase(map, "name"),
              mapValueIgnoreCase(map, "channel"),
              mapValueIgnoreCase(map, "bufferName"));
      if (!channel.isEmpty()) out.add(channel);
      return;
    }
    if (raw instanceof byte[] bytes) {
      String channel = new String(bytes, StandardCharsets.UTF_8).trim();
      if (!channel.isEmpty()) out.add(channel);
      return;
    }
    String channel = Objects.toString(raw, "").trim();
    if (!channel.isEmpty()) out.add(channel);
  }

  private static void collectPotentialNetworkStateMaps(Object raw, List<Map<?, ?>> out) {
    if (raw == null || out == null) return;
    if (raw instanceof Map<?, ?> map) {
      out.add(map);
      for (Object value : map.values()) {
        collectPotentialNetworkStateMaps(value, out);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectPotentialNetworkStateMaps(value, out);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      collectPotentialNetworkStateMaps(userType.value(), out);
    }
  }

  private static Map<String, Object> flattenNetworkStateFromKeyValueParams(List<Object> values) {
    if (values == null || values.size() < 4 || (values.size() % 2) != 0) return Map.of();
    LinkedHashMap<String, Object> flattened = new LinkedHashMap<>();
    int decodedPairs = 0;
    for (int i = 0; i + 1 < values.size(); i += 2) {
      String key = decodeNetworkStateKey(values.get(i));
      if (key.isEmpty()) continue;
      flattened.put(key, values.get(i + 1));
      decodedPairs++;
    }
    // Guard against accidental flattening of non-key/value payloads.
    if (decodedPairs < 6) return Map.of();
    return Collections.unmodifiableMap(flattened);
  }

  private static String decodeNetworkStateKey(Object raw) {
    if (raw == null) return "";
    if (raw instanceof byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8).trim();
    }
    return Objects.toString(raw, "").trim();
  }

  private void observeIdentitiesFromUnknownState(
      QuasselSession session,
      Object raw,
      int fallbackIdentityId,
      String sourceClass,
      String objectName,
      String slotName) {
    if (session == null || raw == null) return;
    if (raw instanceof Map<?, ?> map) {
      int identityId = identityIdFromStateMap(map, fallbackIdentityId);
      String identityName = parseIdentityName(map);
      boolean hasIdentityKeys =
          containsAnyMapKeysIgnoreCase(
              map,
              "identityId",
              "identityid",
              "identityName",
              "identityname",
              "nicks",
              "awayNick",
              "realName",
              "ident",
              "kickReason",
              "partReason",
              "quitReason");
      boolean looksLikeIdentity = hasIdentityKeys || (identityId >= 0 && !identityName.isEmpty());
      if (looksLikeIdentity) {
        observeKnownIdentity(session, identityId, identityName);
        if (identityId >= 0) {
          session.identityStateByIdentityId.put(identityId, normalizeObjectMap(map));
          trimMapToMaxSize(session.identityStateByIdentityId, MAX_NETWORK_IDENTITIES_PER_SESSION);
        }
        log.debug(
            "Observed identity state from {} sync: serverId={}, objectName={}, slotName={}, identityId={}, identityName={}, map={}",
            sourceClass,
            session.serverId,
            objectName,
            slotName,
            identityId,
            identityName,
            map);
      }
      for (Object value : map.values()) {
        observeIdentitiesFromUnknownState(
            session, value, fallbackIdentityId, sourceClass, objectName, slotName);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        observeIdentitiesFromUnknownState(
            session, value, fallbackIdentityId, sourceClass, objectName, slotName);
      }
      return;
    }
    if (raw instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      String type = Objects.toString(userType.typeName(), "").trim();
      Object value = userType.value();
      if ("IdentityId".equals(type)) {
        int identityId = tryParseInt(value);
        if (identityId >= 0) {
          observeKnownIdentity(session, identityId, "");
          log.debug(
              "Observed identity id from {} sync user-type: serverId={}, objectName={}, slotName={}, identityId={}",
              sourceClass,
              session.serverId,
              objectName,
              slotName,
              identityId);
        }
      }
      int nestedFallback = "IdentityId".equals(type) ? tryParseInt(value) : fallbackIdentityId;
      observeIdentitiesFromUnknownState(
          session, value, nestedFallback, sourceClass, objectName, slotName);
      return;
    }
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
      QuasselCoreDatastreamCodec.BufferInfoValue merged =
          session.bufferInfosById.merge(
              info.bufferId(), info, QuasselCoreIrcClientService::mergeBufferInfo);
      trimMapToMaxSize(session.bufferInfosById, MAX_BUFFER_INFOS_PER_SESSION);
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

  private void handleCoreInfoStateSync(
      QuasselSession session, String objectName, String slotName, List<Object> values) {
    if (session == null || values == null || values.isEmpty()) return;
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map) || map.isEmpty()) continue;
      log.debug(
          "Quassel CoreInfo sync observed: serverId={}, objectName={}, slotName={}, keys={}, map={}",
          session.serverId,
          objectName,
          slotName,
          map.keySet(),
          map);
      Object identities = firstMapValueByKeyIgnoreCase(map, "Identities", "identities");
      if (identities != null) {
        observeIdentitiesFromUnknownState(
            session, identities, -1, "CoreInfo.Identities", objectName, slotName);
      }
    }
  }

  private void handleIdentityStateSync(
      QuasselSession session, String objectName, List<Object> values) {
    if (session == null || values == null || values.isEmpty()) return;
    int objectIdentityId = parseNetworkId(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map) || map.isEmpty()) continue;
      int identityId = identityIdFromStateMap(map, objectIdentityId);
      String identityName = parseIdentityName(map);
      observeKnownIdentity(session, identityId, identityName);
      if (identityId >= 0) {
        session.identityStateByIdentityId.put(identityId, normalizeObjectMap(map));
        trimMapToMaxSize(session.identityStateByIdentityId, MAX_NETWORK_IDENTITIES_PER_SESSION);
      }
      log.debug(
          "Quassel Identity sync observed: serverId={}, objectName={}, identityId={}, identityName={}, map={}",
          session.serverId,
          objectName,
          identityId,
          identityName,
          map);
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
    String slot = Objects.toString(slotName, "").trim().toLowerCase(Locale.ROOT);
    Instant now = Instant.now();

    if (slot.contains("setmarkerline") || slot.contains("setlastseenmsg")) {
      int bufferId = values.isEmpty() ? -1 : tryParseInt(values.get(0));
      long markerMsgId = values.size() < 2 ? -1L : tryParseLong(values.get(1));
      emitReadMarkerObserved(session, bufferId, markerMsgId, now);
      return;
    }

    LinkedHashSet<ReadMarkerUpdate> updates = new LinkedHashSet<>();
    for (Object value : values) {
      collectBufferSyncerReadMarkers(value, updates);
    }
    if (updates.isEmpty()) return;
    for (ReadMarkerUpdate update : updates) {
      emitReadMarkerObserved(session, update.bufferId(), update.msgId(), now);
    }
  }

  private static void collectBufferSyncerReadMarkers(Object raw, Set<ReadMarkerUpdate> out) {
    if (raw == null || out == null) return;
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectBufferSyncerReadMarkers(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;

    int directBufferId =
        firstIntFromMapKeys(map, "bufferId", "bufferid", "buffer", "buffer_id", "id");
    long directMsgId =
        firstLongFromMapKeys(
            map,
            "markerLine",
            "markerline",
            "lastSeenMsg",
            "lastseenmsg",
            "lastSeen",
            "lastseen",
            "msgId",
            "msgid",
            "messageId",
            "messageid");
    if (directBufferId >= 0 && directMsgId > 0L) {
      out.add(new ReadMarkerUpdate(directBufferId, directMsgId));
    }

    Object markerLines =
        firstMapValueByKeyIgnoreCase(
            map, "markerLines", "markerlines", "lastSeenMsgs", "lastseenmsgs");
    if (markerLines instanceof Map<?, ?> markerMap) {
      for (Map.Entry<?, ?> entry : markerMap.entrySet()) {
        int bufferId = tryParseInt(entry.getKey());
        long msgId = tryParseLong(entry.getValue());
        if (bufferId >= 0 && msgId > 0L) {
          out.add(new ReadMarkerUpdate(bufferId, msgId));
        }
      }
    }

    for (Object value : map.values()) {
      collectBufferSyncerReadMarkers(value, out);
    }
  }

  private void emitReadMarkerObserved(
      QuasselSession session, int bufferId, long markerMsgId, Instant at) {
    if (session == null || bufferId < 0 || markerMsgId <= 0L) return;
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo = session.bufferInfosById.get(bufferId);
    if (bufferInfo == null) return;

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
      resolvedEpochMs = fallback.toEpochMilli();
    }
    String marker = "timestamp=" + MARKREAD_TS_FMT.format(Instant.ofEpochMilli(resolvedEpochMs));
    bus.onNext(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.ReadMarkerObserved(fallback, from, target, marker)));
  }

  private void observeNetworkCapabilities(
      QuasselSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null || stateMap == null || stateMap.isEmpty()) return;
    boolean hasCapabilitySnapshot =
        containsAnyMapKeysIgnoreCase(
            stateMap,
            "capsEnabled",
            "capsenabled",
            "enabledCaps",
            "enabledcaps",
            "caps",
            "capabilities",
            "availableCaps");
    if (!hasCapabilitySnapshot) return;

    Set<String> enabled =
        extractCapabilityTokens(
            stateMap, "capsEnabled", "capsenabled", "enabledCaps", "enabledcaps");
    if (enabled.isEmpty()) {
      enabled = extractCapabilityTokens(stateMap, "caps", "capabilities", "availableCaps");
    }

    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    if (resolvedNetworkId < 0) return;

    session.capabilitySnapshotObserved.set(true);
    Set<String> previous = session.enabledCapabilitiesByNetworkId.get(resolvedNetworkId);
    session.enabledCapabilitiesByNetworkId.put(resolvedNetworkId, Set.copyOf(enabled));
    trimMapToMaxSize(session.enabledCapabilitiesByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);

    observeMultilineLimitState(session, resolvedNetworkId, stateMap, enabled);
    emitCapabilityDeltaEvents(session, previous, enabled, "SYNC");
  }

  private void observeMultilineLimitState(
      QuasselSession session, int networkId, Map<?, ?> stateMap, Set<String> enabledCaps) {
    if (session == null || networkId < 0) return;
    Set<String> caps = enabledCaps == null ? Set.of() : enabledCaps;
    boolean multilineEnabled = caps.contains("multiline") || caps.contains("draft/multiline");
    if (!multilineEnabled) {
      session.multilineLimitsByNetworkId.remove(networkId);
      return;
    }

    MultilineLimitState existing = session.multilineLimitsByNetworkId.get(networkId);
    MultilineLimitState parsed = extractMultilineLimitsFromStateMap(stateMap);
    if (parsed != null) {
      session.multilineLimitsByNetworkId.put(networkId, parsed);
    } else if (existing == null) {
      session.multilineLimitsByNetworkId.put(networkId, new MultilineLimitState(0L, 0L));
    }
    trimMapToMaxSize(session.multilineLimitsByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
  }

  private void observeNetworkMonitorSupport(
      QuasselSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null || stateMap == null || stateMap.isEmpty()) return;
    MonitorSupportState parsed = extractMonitorSupportFromStateMap(stateMap);
    if (parsed == null) return;
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    if (resolvedNetworkId < 0) return;
    session.monitorSupportByNetworkId.put(resolvedNetworkId, parsed);
    trimMapToMaxSize(session.monitorSupportByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
  }

  private void emitCapabilityDeltaEvents(
      QuasselSession session, Set<String> previous, Set<String> current, String subcommand) {
    if (session == null) return;
    Set<String> prev = previous == null ? Set.of() : previous;
    Set<String> next = current == null ? Set.of() : current;
    String sub = Objects.toString(subcommand, "").trim();
    if (sub.isEmpty()) sub = "SYNC";
    Instant now = Instant.now();
    boolean emitted = false;

    for (String cap : next) {
      if (cap == null || cap.isBlank()) continue;
      if (prev.contains(cap)) continue;
      bus.onNext(
          new ServerIrcEvent(
              session.serverId, new IrcEvent.Ircv3CapabilityChanged(now, sub, cap, true)));
      emitted = true;
    }
    for (String cap : prev) {
      if (cap == null || cap.isBlank()) continue;
      if (next.contains(cap)) continue;
      bus.onNext(
          new ServerIrcEvent(
              session.serverId, new IrcEvent.Ircv3CapabilityChanged(now, sub, cap, false)));
      emitted = true;
    }
    if (emitted) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.ConnectionFeaturesUpdated(now, "cap-" + sub.toLowerCase(Locale.ROOT))));
    }
  }

  private static Set<String> extractCapabilityTokens(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty() || keys == null || keys.length == 0) return Set.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String key : keys) {
      Object value = firstMapValueByKeyIgnoreCase(map, key);
      if (value == null) continue;
      collectCapabilityTokens(value, out);
    }
    if (out.isEmpty()) return Set.of();
    return Collections.unmodifiableSet(out);
  }

  private static MultilineLimitState extractMultilineLimitsFromStateMap(Map<?, ?> stateMap) {
    if (stateMap == null || stateMap.isEmpty()) return null;
    MultilineLimitCollector out = new MultilineLimitCollector();
    collectMultilineLimitsFromRaw(
        firstMapValueByKeyIgnoreCase(
            stateMap, "capsEnabled", "capsenabled", "enabledCaps", "enabledcaps"),
        out);
    collectMultilineLimitsFromRaw(
        firstMapValueByKeyIgnoreCase(stateMap, "caps", "capabilities", "availableCaps"), out);
    return out.toStateOrNull();
  }

  private static void collectMultilineLimitsFromRaw(Object raw, MultilineLimitCollector out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectMultilineLimitsFromRaw(
          new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof String text) {
      for (String token : text.split("\\s+")) {
        collectMultilineLimitsFromToken(token, out);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectMultilineLimitsFromRaw(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String capKey = canonicalCapabilityToken(entry.getKey());
      Object value = entry.getValue();
      if (isMultilineCapability(capKey)) {
        collectMultilineLimitsFromToken(entry.getKey(), out);
        if (!isCapabilityExplicitlyDisabled(value)) {
          collectMultilineLimitParams(value, out);
        }
      }
      collectMultilineLimitsFromRaw(value, out);
    }
  }

  private static void collectMultilineLimitsFromToken(Object token, MultilineLimitCollector out) {
    if (out == null) return;
    String raw = Objects.toString(token, "").trim();
    if (raw.isEmpty()) return;
    String cleaned = stripLeadingColon(raw);
    if (cleaned.isEmpty()) return;
    boolean disabled = cleaned.startsWith("-");
    String cap = canonicalCapabilityToken(cleaned);
    if (!isMultilineCapability(cap) || disabled) return;

    int eq = cleaned.indexOf('=');
    if (eq <= 0 || eq >= cleaned.length() - 1) return;
    collectMultilineLimitParams(cleaned.substring(eq + 1), out);
  }

  private static void collectMultilineLimitParams(Object raw, MultilineLimitCollector out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectMultilineLimitParams(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof Number n) {
      long value = n.longValue();
      if (value >= 0L) out.observeLines(value);
      return;
    }
    if (raw instanceof String text) {
      String params = Objects.toString(text, "").trim();
      if (params.isEmpty()) return;
      long maxBytes = parseNamedLongParam(params, "max-bytes", "maxbytes", "max_bytes", "bytes");
      long maxLines = parseNamedLongParam(params, "max-lines", "maxlines", "max_lines", "lines");
      if (maxBytes >= 0L) out.observeBytes(maxBytes);
      if (maxLines >= 0L) out.observeLines(maxLines);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectMultilineLimitParams(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim().toLowerCase(Locale.ROOT);
      Object value = entry.getValue();
      if (key.isEmpty()) {
        collectMultilineLimitParams(value, out);
        continue;
      }
      if ("max-bytes".equals(key)
          || "maxbytes".equals(key)
          || "max_bytes".equals(key)
          || "bytes".equals(key)) {
        long parsed = tryParseLong(value);
        if (parsed >= 0L) out.observeBytes(parsed);
        continue;
      }
      if ("max-lines".equals(key)
          || "maxlines".equals(key)
          || "max_lines".equals(key)
          || "lines".equals(key)) {
        long parsed = tryParseLong(value);
        if (parsed >= 0L) out.observeLines(parsed);
        continue;
      }
      collectMultilineLimitParams(value, out);
    }
  }

  private static MonitorSupportState extractMonitorSupportFromStateMap(Map<?, ?> stateMap) {
    if (stateMap == null || stateMap.isEmpty()) return null;
    MonitorSupportCollector out = new MonitorSupportCollector();
    collectMonitorSupportFromRaw(stateMap, out);
    return out.toStateOrNull();
  }

  private static void collectMonitorSupportFromRaw(Object raw, MonitorSupportCollector out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectMonitorSupportFromRaw(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof String text) {
      parseMonitorTokensFromText(text, out);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectMonitorSupportFromRaw(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      String lowerKey = key.toLowerCase(Locale.ROOT);
      Object value = entry.getValue();
      parseMonitorToken(key, out);

      if (lowerKey.contains("monitor")) {
        Boolean availability = parseBoolean(value);
        if (availability != null) {
          out.observeAvailability(availability.booleanValue());
        }
        long limit = tryParseLong(value);
        if (limit >= 0L) {
          out.observeLimit(limit);
        }
      }

      if ("monitor".equals(canonicalCapabilityToken(key))
          && !isCapabilityExplicitlyDisabled(value)) {
        out.observeAvailability(true);
        collectMonitorSupportFromRaw(value, out);
      } else {
        collectMonitorSupportFromRaw(value, out);
      }
    }
  }

  private static void parseMonitorTokensFromText(String raw, MonitorSupportCollector out) {
    if (out == null) return;
    String text = Objects.toString(raw, "").trim();
    if (text.isEmpty()) return;
    for (String token : text.split("[\\s,]+")) {
      parseMonitorToken(token, out);
    }
  }

  private static void parseMonitorToken(String raw, MonitorSupportCollector out) {
    if (out == null) return;
    String token = stripTrailingPunctuation(stripLeadingColon(Objects.toString(raw, "")));
    if (token.isEmpty()) return;
    String upper = token.toUpperCase(Locale.ROOT);
    if (upper.startsWith("-MONITOR")) {
      out.observeAvailability(false);
      out.observeLimit(0L);
      return;
    }
    if ("MONITOR".equals(upper)) {
      out.observeAvailability(true);
      return;
    }
    if (!upper.startsWith("MONITOR=")) return;
    int idx = token.indexOf('=');
    if (idx < 0 || idx >= token.length() - 1) {
      out.observeAvailability(true);
      return;
    }
    long parsed = tryParseLong(token.substring(idx + 1));
    if (parsed >= 0L) {
      out.observeLimit(parsed);
    } else {
      out.observeAvailability(true);
    }
  }

  private static String stripTrailingPunctuation(String raw) {
    String text = Objects.toString(raw, "").trim();
    while (!text.isEmpty()) {
      char last = text.charAt(text.length() - 1);
      if (last == ';' || last == ':' || last == '.') {
        text = text.substring(0, text.length() - 1).trim();
      } else {
        break;
      }
    }
    return text;
  }

  private static long parseNamedLongParam(String params, String... names) {
    String text = Objects.toString(params, "").trim();
    if (text.isEmpty() || names == null || names.length == 0) return -1L;
    String lower = text.toLowerCase(Locale.ROOT);
    for (String rawName : names) {
      String name = Objects.toString(rawName, "").trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty()) continue;
      String needle = name + "=";
      int idx = lower.indexOf(needle);
      while (idx >= 0) {
        int start = idx + needle.length();
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        if (end > start) {
          try {
            return Long.parseLong(text.substring(start, end));
          } catch (NumberFormatException ignored) {
          }
        }
        idx = lower.indexOf(needle, idx + 1);
      }
    }
    return -1L;
  }

  private static boolean isMultilineCapability(String cap) {
    String token = Objects.toString(cap, "").trim().toLowerCase(Locale.ROOT);
    return "multiline".equals(token) || "draft/multiline".equals(token);
  }

  private static void collectCapabilityTokens(Object raw, Set<String> out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectCapabilityTokens(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof String text) {
      String cleaned = text.replace(',', ' ');
      for (String token : cleaned.split("\\s+")) {
        String cap = canonicalCapabilityToken(token);
        if (!cap.isEmpty()) out.add(cap);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectCapabilityTokens(value, out);
      }
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String cap = canonicalCapabilityToken(entry.getKey());
        if (cap.isEmpty()) continue;
        if (!isCapabilityExplicitlyDisabled(entry.getValue())) {
          out.add(cap);
        }
      }
      return;
    }

    String cap = canonicalCapabilityToken(raw);
    if (!cap.isEmpty()) {
      out.add(cap);
    }
  }

  private static boolean isCapabilityExplicitlyDisabled(Object raw) {
    if (raw instanceof Boolean b) return !b;
    if (raw instanceof Number n) return n.intValue() == 0;
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return false;
    return "0".equals(token)
        || "false".equals(token)
        || "no".equals(token)
        || "off".equals(token)
        || "-".equals(token);
  }

  private static String canonicalCapabilityToken(Object raw) {
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return "";
    if (token.startsWith(":")) token = token.substring(1).trim();
    if (token.startsWith("+")) token = token.substring(1).trim();
    if (token.startsWith("-")) token = token.substring(1).trim();
    int eq = token.indexOf('=');
    if (eq > 0) token = token.substring(0, eq).trim();
    if (token.isEmpty()) return "";
    return token;
  }

  private static boolean containsAnyMapKeysIgnoreCase(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty() || keys == null || keys.length == 0) return false;
    for (String key : keys) {
      if (firstMapValueByKeyIgnoreCase(map, key) != null) return true;
    }
    return false;
  }

  private static Object firstMapValueByKeyIgnoreCase(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty() || keys == null || keys.length == 0) return null;
    for (String wanted : keys) {
      String needle = Objects.toString(wanted, "").trim().toLowerCase(Locale.ROOT);
      if (needle.isEmpty()) continue;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = Objects.toString(entry.getKey(), "").trim().toLowerCase(Locale.ROOT);
        if (needle.equals(key)) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  private static int firstIntFromMapKeys(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty()) return -1;
    for (String key : keys) {
      int parsed = tryParseInt(firstMapValueByKeyIgnoreCase(map, key));
      if (parsed >= 0) return parsed;
    }
    return -1;
  }

  private static long firstLongFromMapKeys(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty()) return -1L;
    for (String key : keys) {
      long parsed = tryParseLong(firstMapValueByKeyIgnoreCase(map, key));
      if (parsed > 0L) return parsed;
    }
    return -1L;
  }

  private void handleIrcUserStateSync(
      QuasselSession session, String objectName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    String objectNick = parseObjectLeafToken(objectName);
    Instant now = Instant.now();
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("name"));
      observeKnownNetwork(session, networkId, networkName);

      String nick = firstNonBlank(map.get("nick"), map.get("nickname"), objectNick);
      if (nick.isEmpty()) continue;

      String user = firstNonBlank(map.get("user"), map.get("username"));
      String host = firstNonBlank(map.get("host"), map.get("hostname"));
      if (!user.isEmpty() && !host.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId, new IrcEvent.UserHostChanged(now, nick, user, host)));
      }

      String realName =
          firstNonBlank(map.get("realName"), map.get("realname"), map.get("real_name"));
      if (!realName.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserSetNameObserved(
                    now, nick, realName, IrcEvent.UserSetNameObserved.Source.SETNAME)));
      }

      String account = firstNonBlank(map.get("account"), map.get("accountName"));
      if (!account.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserAccountStateObserved(
                    now, nick, IrcEvent.AccountState.LOGGED_IN, account)));
      } else if (map.containsKey("account") || map.containsKey("accountName")) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserAccountStateObserved(
                    now, nick, IrcEvent.AccountState.LOGGED_OUT)));
      }

      Boolean awayFlag = parseBoolean(map.get("away"));
      String awayMessage =
          firstNonBlank(map.get("awayMessage"), map.get("awayMsg"), map.get("awayReason"));
      if (awayFlag != null) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserAwayStateObserved(
                    now,
                    nick,
                    awayFlag ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE,
                    awayMessage)));
      } else if (!awayMessage.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserAwayStateObserved(
                    now, nick, IrcEvent.AwayState.AWAY, awayMessage)));
      }
    }
  }

  private void handleIrcChannelStateSync(
      QuasselSession session, String objectName, List<Object> values) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    String objectChannel = parseObjectLeafToken(objectName);
    Instant now = Instant.now();
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("network"));
      observeKnownNetwork(session, networkId, networkName);

      String channel =
          firstNonBlank(map.get("name"), map.get("channel"), map.get("bufferName"), objectChannel);
      if (!looksLikeChannel(channel)) continue;
      String topic = firstNonBlank(map.get("topic"), map.get("topicText"), map.get("topicString"));
      if (topic.isEmpty()) continue;
      String qualifiedChannel = qualifyTargetForNetwork(session, channel, networkId);
      bus.onNext(
          new ServerIrcEvent(
              session.serverId, new IrcEvent.ChannelTopicUpdated(now, qualifiedChannel, topic)));
    }
  }

  private void handleBacklogSync(QuasselSession session, List<Object> values) {
    if (values == null || values.isEmpty()) return;

    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo = null;
    ArrayList<QuasselCoreDatastreamCodec.MessageValue> messages = new ArrayList<>();
    for (Object value : values) {
      if (bufferInfo == null && value instanceof QuasselCoreDatastreamCodec.BufferInfoValue info) {
        bufferInfo = resolveBufferInfo(session, info);
      } else if (bufferInfo == null && value instanceof Number n) {
        QuasselCoreDatastreamCodec.BufferInfoValue byId = session.bufferInfosById.get(n.intValue());
        if (byId != null) bufferInfo = byId;
      }
      collectMessages(value, messages);
    }
    if (messages.isEmpty()) return;

    QuasselCoreDatastreamCodec.MessageValue first = messages.get(0);
    QuasselCoreDatastreamCodec.BufferInfoValue resolvedBuffer =
        bufferInfo == null ? resolveBufferInfo(session, first.bufferInfo()) : bufferInfo;
    String target = historyTargetForBuffer(session, resolvedBuffer, extractNick(first.sender()));
    if (target.isEmpty()) return;
    noteTargetNetworkHint(session, target, resolvedBuffer.networkId(), true);

    ArrayList<ChatHistoryEntry> entries = new ArrayList<>(messages.size());
    for (QuasselCoreDatastreamCodec.MessageValue msg : messages) {
      ChatHistoryEntry entry = toHistoryEntry(session, msg, target);
      if (entry != null) {
        noteHistoryObservation(session, entry.target(), msg.messageId(), entry.at());
        entries.add(entry);
      }
    }
    if (entries.isEmpty()) return;

    String batchId = "quassel-backlog-sync-" + session.backlogBatchSeq.incrementAndGet();
    bus.onNext(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.ChatHistoryBatchReceived(
                Instant.now(), target, batchId, List.copyOf(entries))));
  }

  private static void collectMessages(
      Object raw, List<QuasselCoreDatastreamCodec.MessageValue> out) {
    if (raw == null || out == null) return;
    if (raw instanceof QuasselCoreDatastreamCodec.MessageValue msg) {
      out.add(msg);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectMessages(value, out);
      }
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        collectMessages(value, out);
      }
    }
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
    ParsedIrcEnvelope ircEnvelope = parseIrcEnvelope(content);
    Map<String, String> ircv3Tags = ircEnvelope.ircv3Tags();
    String payloadText = payloadTextFromEnvelope(ircEnvelope, content);
    String target = targetForBuffer(session, bufferInfo, fromDisplay);
    String historyTarget = historyTargetForBuffer(session, bufferInfo, fromDisplay);
    int historyNetworkId = bufferInfo == null ? -1 : bufferInfo.networkId();
    noteTargetNetworkHint(session, historyTarget, historyNetworkId, true);
    noteHistoryObservation(session, historyTarget, message.messageId(), at);
    int typeBits = message.typeBits();
    emitObservedIrcv3Signals(session, at, fromDisplay, target, networkId, ircEnvelope, messageId);

    String envelopeCommand = ircEnvelope.command();
    if ("CAP".equals(envelopeCommand)) {
      emitCapabilityChangesFromCapLine(session, at, networkId, ircEnvelope);
    }
    if (isStandardReplyCommand(envelopeCommand)) {
      emitStandardReplyFromCommand(session, at, ircEnvelope, messageId);
      return;
    }
    if ("MARKREAD".equals(envelopeCommand)) {
      emitReadMarkerFromCommand(session, at, fromDisplay, target, networkId, ircEnvelope);
      return;
    }
    if ("REDACT".equals(envelopeCommand)) {
      emitRedactionFromCommand(session, at, fromDisplay, target, networkId, ircEnvelope);
      return;
    }
    if ("TAGMSG".equals(envelopeCommand) && payloadText.isBlank()) {
      return;
    }

    if (maybeEmitMonitorNumeric(session, at, networkId, content)) {
      return;
    }

    if (isBacklogMessage(message.flags()) && isHistoryTextMessage(typeBits)) {
      emitBacklogHistoryBatch(session, at, target, message, messageId);
      return;
    }

    if (isJoinMessage(typeBits)) {
      handleJoinMessage(session, at, target, fromDisplay, networkId);
      return;
    }

    if (isPartMessage(typeBits)) {
      handlePartMessage(session, at, target, fromDisplay, payloadText, networkId);
      return;
    }

    if (isQuitMessage(typeBits)) {
      if (!target.isEmpty()) {
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
            markChannelMembershipLeft(session, target, networkId);
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

    IrcEvent.ServerResponseLine response = renderServerResponse(at, statusLine, content, messageId);
    bus.onNext(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.ServerResponseLine(
                response.at(),
                response.code(),
                response.message(),
                response.rawLine(),
                response.messageId(),
                ircv3Tags)));
  }

  private static String payloadTextFromEnvelope(
      ParsedIrcEnvelope envelope, String fallbackContent) {
    if (envelope == null || !envelope.parsed()) {
      return Objects.toString(fallbackContent, "");
    }
    if ("PRIVMSG".equals(envelope.command()) || "NOTICE".equals(envelope.command())) {
      if (!envelope.trailing().isBlank()) {
        return envelope.trailing();
      }
    }
    if ("TAGMSG".equals(envelope.command())) {
      return "";
    }
    return Objects.toString(fallbackContent, "");
  }

  private void emitObservedIrcv3Signals(
      QuasselSession session,
      Instant at,
      String fromDisplay,
      String fallbackTarget,
      int networkId,
      ParsedIrcEnvelope envelope,
      String messageId) {
    if (session == null || envelope == null) return;
    Map<String, String> tags = envelope.ircv3Tags();
    if (tags == null || tags.isEmpty()) return;

    String from = Objects.toString(fromDisplay, "").trim();
    if (from.isEmpty()) from = "server";
    String convTarget =
        resolveSignalTarget(session, fromDisplay, fallbackTarget, networkId, envelope, tags);

    String replyTo = Ircv3Tags.firstTagValue(tags, "draft/reply", "+draft/reply");
    if (!replyTo.isBlank()) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId, new IrcEvent.MessageReplyObserved(at, from, convTarget, replyTo)));
    }

    String react = Ircv3Tags.firstTagValue(tags, "draft/react", "+draft/react");
    if (!react.isBlank()) {
      String targetMsgId = replyTo;
      if (targetMsgId.isBlank()) {
        targetMsgId =
            Ircv3Tags.firstTagValue(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
      }
      if (targetMsgId.isBlank()) {
        targetMsgId = Objects.toString(messageId, "").trim();
      }
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.MessageReactObserved(at, from, convTarget, react, targetMsgId)));
    }

    String unreact = Ircv3Tags.firstTagValue(tags, "draft/unreact", "+draft/unreact");
    if (!unreact.isBlank()) {
      String targetMsgId = replyTo;
      if (targetMsgId.isBlank()) {
        targetMsgId =
            Ircv3Tags.firstTagValue(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
      }
      if (targetMsgId.isBlank()) {
        targetMsgId = Objects.toString(messageId, "").trim();
      }
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.MessageUnreactObserved(at, from, convTarget, unreact, targetMsgId)));
    }

    String redactMsgId =
        Ircv3Tags.firstTagValue(
            tags, "draft/delete", "+draft/delete", "draft/redact", "+draft/redact");
    if (!redactMsgId.isBlank()) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.MessageRedactionObserved(at, from, convTarget, redactMsgId)));
    }

    String typing = Ircv3Tags.firstTagValue(tags, "typing", "+typing");
    if (!typing.isBlank()) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId, new IrcEvent.UserTypingObserved(at, from, convTarget, typing)));
    }

    String readMarker =
        Ircv3Tags.firstTagValue(
            tags, "draft/read-marker", "+draft/read-marker", "read-marker", "+read-marker");
    if (!readMarker.isBlank()) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId, new IrcEvent.ReadMarkerObserved(at, from, convTarget, readMarker)));
    }
  }

  private boolean maybeEmitMonitorNumeric(
      QuasselSession session, Instant at, int networkId, String rawLine) {
    if (session == null) return false;
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) return false;

    PircbotxMonitorParsers.ParsedMonitorSupport monitorSupport =
        PircbotxMonitorParsers.parseRpl005MonitorSupport(raw);
    if (monitorSupport != null) {
      int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
      if (resolvedNetworkId >= 0) {
        session.monitorSupportByNetworkId.put(
            resolvedNetworkId,
            new MonitorSupportState(
                monitorSupport.supported(), Math.max(0L, (long) monitorSupport.limit())));
        trimMapToMaxSize(session.monitorSupportByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
      }
    }

    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> onlineEntries =
        PircbotxMonitorParsers.parseRpl730MonitorOnlineEntries(raw);
    List<String> online = monitorNickList(onlineEntries);
    if (!online.isEmpty()) {
      emitMonitorHostmaskObservations(session, at, onlineEntries);
      bus.onNext(
          new ServerIrcEvent(session.serverId, new IrcEvent.MonitorOnlineObserved(at, online)));
      return true;
    }

    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> offlineEntries =
        PircbotxMonitorParsers.parseRpl731MonitorOfflineEntries(raw);
    List<String> offline = monitorNickList(offlineEntries);
    if (!offline.isEmpty()) {
      emitMonitorHostmaskObservations(session, at, offlineEntries);
      bus.onNext(
          new ServerIrcEvent(session.serverId, new IrcEvent.MonitorOfflineObserved(at, offline)));
      return true;
    }

    List<String> listed = PircbotxMonitorParsers.parseRpl732MonitorListNicks(raw);
    if (!listed.isEmpty()) {
      bus.onNext(
          new ServerIrcEvent(session.serverId, new IrcEvent.MonitorListObserved(at, listed)));
      return true;
    }

    if (PircbotxMonitorParsers.isRpl733MonitorListEnd(raw)) {
      bus.onNext(new ServerIrcEvent(session.serverId, new IrcEvent.MonitorListEnded(at)));
      return true;
    }

    PircbotxMonitorParsers.ParsedMonitorListFull full =
        PircbotxMonitorParsers.parseErr734MonitorListFull(raw);
    if (full != null) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.MonitorListFull(at, full.limit(), full.nicks(), full.message())));
      return true;
    }
    return false;
  }

  private static List<String> monitorNickList(
      List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(entries.size());
    for (PircbotxMonitorParsers.ParsedMonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      if (!nick.isEmpty()) out.add(nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private void emitMonitorHostmaskObservations(
      QuasselSession session,
      Instant at,
      List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries) {
    if (session == null || entries == null || entries.isEmpty()) return;
    for (PircbotxMonitorParsers.ParsedMonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      String hostmask = Objects.toString(entry.hostmask(), "").trim();
      if (nick.isEmpty() || !PircbotxUtil.isUsefulHostmask(hostmask)) continue;
      bus.onNext(
          new ServerIrcEvent(
              session.serverId, new IrcEvent.UserHostmaskObserved(at, "", nick, hostmask)));
    }
  }

  private void emitCapabilityChangesFromCapLine(
      QuasselSession session, Instant at, int networkId, ParsedIrcEnvelope envelope) {
    if (session == null || envelope == null || !envelope.parsed()) return;
    String subcommand = normalizeCapSubcommand(envelope);
    if (subcommand.isEmpty()) return;
    String caps = capListFromEnvelope(envelope);
    if (caps.isBlank()) return;

    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId(session);
    boolean emitted = false;
    for (String rawToken : caps.split("\\s+")) {
      String token = Objects.toString(rawToken, "").trim();
      if (token.isEmpty()) continue;

      boolean disabledToken = token.startsWith("-");
      String capName = canonicalCapabilityToken(token);
      if (capName.isBlank()) continue;

      boolean enabled =
          switch (subcommand) {
            case "ACK" -> !disabledToken;
            case "DEL", "NAK", "NEW", "LS" -> false;
            default -> false;
          };

      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.Ircv3CapabilityChanged(at, subcommand, capName, enabled)));
      emitted = true;

      if ("ACK".equals(subcommand) || "DEL".equals(subcommand) || "NAK".equals(subcommand)) {
        applyCapabilityStateDelta(session, resolvedNetworkId, capName, enabled);
      }

      if (isMultilineCapability(capName) && resolvedNetworkId >= 0) {
        if ("DEL".equals(subcommand) || disabledToken) {
          session.multilineLimitsByNetworkId.remove(resolvedNetworkId);
        } else if ("ACK".equals(subcommand)) {
          MultilineLimitCollector collector = new MultilineLimitCollector();
          collectMultilineLimitsFromToken(token, collector);
          MultilineLimitState parsed = collector.toStateOrNull();
          if (parsed != null) {
            session.multilineLimitsByNetworkId.put(resolvedNetworkId, parsed);
            trimMapToMaxSize(
                session.multilineLimitsByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
          } else {
            session.multilineLimitsByNetworkId.putIfAbsent(
                resolvedNetworkId, new MultilineLimitState(0L, 0L));
          }
        }
      }
    }

    if (emitted) {
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.ConnectionFeaturesUpdated(
                  at, "cap-" + subcommand.toLowerCase(Locale.ROOT))));
    }
  }

  private static String normalizeCapSubcommand(ParsedIrcEnvelope envelope) {
    if (envelope == null) return "";
    String sub = stripLeadingColon(envelope.secondParam());
    if (sub.isBlank()) {
      sub = stripLeadingColon(envelope.firstParam());
    }
    String action = sub.trim().toUpperCase(Locale.ROOT);
    if ("ACK".equals(action)
        || "DEL".equals(action)
        || "NAK".equals(action)
        || "NEW".equals(action)
        || "LS".equals(action)) {
      return action;
    }
    return "";
  }

  private static String capListFromEnvelope(ParsedIrcEnvelope envelope) {
    if (envelope == null) return "";
    String trailing = Objects.toString(envelope.trailing(), "").trim();
    if (!trailing.isEmpty()) {
      return trailing;
    }
    List<String> params = envelope.params();
    if (params == null || params.size() <= 2) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 2; i < params.size(); i++) {
      String token = stripLeadingColon(params.get(i));
      if (token.isBlank()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(token);
    }
    return sb.toString().trim();
  }

  private void applyCapabilityStateDelta(
      QuasselSession session, int networkId, String capability, boolean enabled) {
    if (session == null || networkId < 0) return;
    String cap = canonicalCapabilityToken(capability);
    if (cap.isBlank()) return;

    Set<String> previous = session.enabledCapabilitiesByNetworkId.get(networkId);
    LinkedHashSet<String> next = new LinkedHashSet<>(previous == null ? Set.of() : previous);
    if (enabled) {
      next.add(cap);
    } else {
      next.remove(cap);
    }
    session.capabilitySnapshotObserved.set(true);
    session.enabledCapabilitiesByNetworkId.put(networkId, Set.copyOf(next));
    trimMapToMaxSize(session.enabledCapabilitiesByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
  }

  private void emitStandardReplyFromCommand(
      QuasselSession session, Instant at, ParsedIrcEnvelope envelope, String fallbackMessageId) {
    if (session == null || envelope == null || !envelope.parsed()) return;
    IrcEvent.StandardReplyKind kind = toStandardReplyKind(envelope.command());
    if (kind == null) return;

    ParsedStandardReply parsed = parseStandardReply(envelope);
    Map<String, String> tags = envelope.ircv3Tags();
    String messageId =
        Ircv3Tags.firstTagValue(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
    if (messageId.isBlank()) {
      messageId = Objects.toString(fallbackMessageId, "").trim();
    }

    bus.onNext(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.StandardReply(
                at,
                kind,
                parsed.command(),
                parsed.code(),
                parsed.context(),
                parsed.description(),
                envelope.rawLine(),
                messageId,
                tags)));
  }

  private static ParsedStandardReply parseStandardReply(ParsedIrcEnvelope envelope) {
    if (envelope == null) return new ParsedStandardReply("", "", "", "");
    List<String> params = envelope.params();
    String command = paramAt(params, 0);
    String code = paramAt(params, 1);
    String context = "";
    String description = Objects.toString(envelope.trailing(), "").trim();

    if (description.isBlank() && params != null && params.size() > 2) {
      description = stripLeadingColon(params.get(params.size() - 1));
      context = joinParams(params, 2, params.size() - 1);
      return new ParsedStandardReply(command, code, context, description);
    }
    context = joinParams(params, 2, params == null ? 2 : params.size());
    return new ParsedStandardReply(command, code, context, description);
  }

  private static String paramAt(List<String> params, int index) {
    if (params == null || index < 0 || index >= params.size()) return "";
    return stripLeadingColon(params.get(index));
  }

  private static String joinParams(List<String> params, int fromInclusive, int toExclusive) {
    if (params == null) return "";
    int from = Math.max(0, fromInclusive);
    int to = Math.max(from, Math.min(params.size(), toExclusive));
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < to; i++) {
      String token = stripLeadingColon(params.get(i));
      if (token.isBlank()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(token);
    }
    return sb.toString().trim();
  }

  private static boolean isStandardReplyCommand(String command) {
    String c = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    return "FAIL".equals(c) || "WARN".equals(c) || "NOTE".equals(c);
  }

  private static IrcEvent.StandardReplyKind toStandardReplyKind(String command) {
    String c = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    return switch (c) {
      case "FAIL" -> IrcEvent.StandardReplyKind.FAIL;
      case "WARN" -> IrcEvent.StandardReplyKind.WARN;
      case "NOTE" -> IrcEvent.StandardReplyKind.NOTE;
      default -> null;
    };
  }

  private void emitReadMarkerFromCommand(
      QuasselSession session,
      Instant at,
      String fromDisplay,
      String fallbackTarget,
      int networkId,
      ParsedIrcEnvelope envelope) {
    if (session == null || envelope == null || !envelope.parsed()) return;
    String from = Objects.toString(fromDisplay, "").trim();
    if (from.isEmpty()) from = "server";
    String markerTarget = stripLeadingColon(envelope.firstParam());
    String marker = stripLeadingColon(envelope.secondParam());
    if (marker.isBlank()) {
      marker = stripLeadingColon(envelope.trailing());
    }
    String resolvedTarget =
        resolveSignalTargetForRawTarget(
            session, fromDisplay, fallbackTarget, networkId, markerTarget);
    bus.onNext(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.ReadMarkerObserved(at, from, resolvedTarget, marker)));
  }

  private void emitRedactionFromCommand(
      QuasselSession session,
      Instant at,
      String fromDisplay,
      String fallbackTarget,
      int networkId,
      ParsedIrcEnvelope envelope) {
    if (session == null || envelope == null || !envelope.parsed()) return;
    String from = Objects.toString(fromDisplay, "").trim();
    if (from.isEmpty()) from = "server";
    String redactTarget = stripLeadingColon(envelope.firstParam());
    String redactMsgId = stripLeadingColon(envelope.secondParam());
    if (redactMsgId.isBlank()) return;
    String resolvedTarget =
        resolveSignalTargetForRawTarget(
            session, fromDisplay, fallbackTarget, networkId, redactTarget);
    bus.onNext(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.MessageRedactionObserved(at, from, resolvedTarget, redactMsgId)));
  }

  private String resolveSignalTarget(
      QuasselSession session,
      String fromDisplay,
      String fallbackTarget,
      int networkId,
      ParsedIrcEnvelope envelope,
      Map<String, String> tags) {
    String channelContext =
        Ircv3Tags.firstTagValue(
            tags,
            "draft/channel-context",
            "+draft/channel-context",
            "channel-context",
            "+channel-context");
    String targetHint = stripLeadingColon(channelContext);
    if (targetHint.isBlank()) {
      targetHint = stripLeadingColon(envelope.firstParam());
    }
    return resolveSignalTargetForRawTarget(
        session, fromDisplay, fallbackTarget, networkId, targetHint);
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

  private static ParsedIrcEnvelope parseIrcEnvelope(String content) {
    String line = Objects.toString(content, "").trim();
    if (line.isEmpty()) return ParsedIrcEnvelope.empty(content);

    int idx = 0;
    Map<String, String> tags = Map.of();
    if (line.charAt(idx) == '@') {
      tags = Ircv3Tags.fromRawLine(line);
      int sp = line.indexOf(' ');
      if (sp <= 0 || sp >= line.length() - 1) {
        return ParsedIrcEnvelope.empty(content);
      }
      idx = sp + 1;
      while (idx < line.length() && line.charAt(idx) == ' ') idx++;
    }

    String source = "";
    if (idx < line.length() && line.charAt(idx) == ':') {
      int sp = line.indexOf(' ', idx);
      if (sp <= idx || sp >= line.length() - 1) {
        return ParsedIrcEnvelope.empty(content);
      }
      source = line.substring(idx + 1, sp).trim();
      idx = sp + 1;
      while (idx < line.length() && line.charAt(idx) == ' ') idx++;
    }
    if (idx >= line.length()) {
      return ParsedIrcEnvelope.empty(content);
    }

    int cmdStart = idx;
    while (idx < line.length() && line.charAt(idx) != ' ') idx++;
    String command = line.substring(cmdStart, idx).trim().toUpperCase(Locale.ROOT);
    boolean commandOfInterest =
        TARGET_ROUTED_RAW_COMMANDS.contains(command)
            || EXTRA_PARSED_ENVELOPE_COMMANDS.contains(command)
            || !tags.isEmpty();
    if (command.isBlank() || !commandOfInterest) {
      return ParsedIrcEnvelope.empty(content);
    }

    ArrayList<String> params = new ArrayList<>();
    String trailing = "";
    while (idx < line.length()) {
      while (idx < line.length() && line.charAt(idx) == ' ') idx++;
      if (idx >= line.length()) break;
      if (line.charAt(idx) == ':') {
        trailing = line.substring(idx + 1);
        break;
      }
      int start = idx;
      while (idx < line.length() && line.charAt(idx) != ' ') idx++;
      String param = line.substring(start, idx).trim();
      if (!param.isEmpty()) {
        params.add(param);
      }
    }

    return new ParsedIrcEnvelope(
        true,
        line,
        command,
        source,
        List.copyOf(params),
        trailing,
        tags == null || tags.isEmpty() ? Map.of() : tags);
  }

  private static String stripLeadingColon(String raw) {
    String text = Objects.toString(raw, "").trim();
    if (text.startsWith(":")) {
      return text.substring(1).trim();
    }
    return text;
  }

  private void handleJoinMessage(
      QuasselSession session, Instant at, String channel, String fromDisplay, int networkId) {
    if (channel.isEmpty()) return;
    if (isSelfNick(session, fromDisplay, networkId)) {
      if (markChannelMembershipJoined(session, channel, networkId)) {
        bus.onNext(new ServerIrcEvent(session.serverId, new IrcEvent.JoinedChannel(at, channel)));
      }
      return;
    }
    bus.onNext(
        new ServerIrcEvent(
            session.serverId, new IrcEvent.UserJoinedChannel(at, channel, fromDisplay)));
  }

  private void handlePartMessage(
      QuasselSession session,
      Instant at,
      String channel,
      String fromDisplay,
      String content,
      int networkId) {
    if (channel.isEmpty()) return;
    String reason = normalizeReason(content);
    if (isSelfNick(session, fromDisplay, networkId)) {
      markChannelMembershipLeft(session, channel, networkId);
      bus.onNext(
          new ServerIrcEvent(session.serverId, new IrcEvent.LeftChannel(at, channel, reason)));
      return;
    }
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
    ChatHistoryEntry entry = toHistoryEntry(session, message, targetFromBuffer);
    if (entry == null) return;
    String base = messageId.isEmpty() ? Long.toString(message.timestampEpochSeconds()) : messageId;
    if (base.isBlank()) base = Long.toString(System.currentTimeMillis());
    String batchId = "quassel-backlog-" + base + "-" + session.backlogBatchSeq.incrementAndGet();
    bus.onNext(
        new ServerIrcEvent(
            session.serverId,
            new IrcEvent.ChatHistoryBatchReceived(at, entry.target(), batchId, List.of(entry))));
  }

  private ChatHistoryEntry toHistoryEntry(
      QuasselSession session,
      QuasselCoreDatastreamCodec.MessageValue message,
      String targetFromBuffer) {
    if (message == null) return null;
    int typeBits = message.typeBits();
    if (!isHistoryTextMessage(typeBits)) return null;

    Instant at =
        message.timestampEpochSeconds() > 0
            ? Instant.ofEpochSecond(message.timestampEpochSeconds())
            : Instant.now();
    String from = extractNick(message.sender());
    String text = Objects.toString(message.content(), "");
    String target = historyTargetForBuffer(session, message.bufferInfo(), from);
    if (target.isEmpty()) {
      target = Objects.toString(targetFromBuffer, "").trim();
    }
    if (target.isEmpty()) return null;

    String messageId = message.messageId() > 0 ? Long.toString(message.messageId()) : "";
    ChatHistoryEntry.Kind kind;
    if (isActionMessage(typeBits)) {
      kind = ChatHistoryEntry.Kind.ACTION;
    } else if (isNoticeMessage(typeBits)) {
      kind = ChatHistoryEntry.Kind.NOTICE;
    } else {
      kind = ChatHistoryEntry.Kind.PRIVMSG;
    }
    return new ChatHistoryEntry(at, kind, target, from, text, messageId, Map.of());
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

    QuasselCoreDatastreamCodec.BufferInfoValue existing =
        session.bufferInfosById.get(incoming.bufferId());
    QuasselCoreDatastreamCodec.BufferInfoValue merged = mergeBufferInfo(existing, incoming);
    session.bufferInfosById.put(merged.bufferId(), merged);
    trimMapToMaxSize(session.bufferInfosById, MAX_BUFFER_INFOS_PER_SESSION);
    observeKnownNetwork(session, merged.networkId(), "");
    return merged;
  }

  private static QuasselCoreDatastreamCodec.BufferInfoValue mergeBufferInfo(
      QuasselCoreDatastreamCodec.BufferInfoValue existing,
      QuasselCoreDatastreamCodec.BufferInfoValue update) {
    if (existing == null) return update;
    if (update == null) return existing;

    int bufferId = update.bufferId() >= 0 ? update.bufferId() : existing.bufferId();
    int networkId = preferKnownInt(update.networkId(), existing.networkId());
    int typeBits = update.typeBits() != 0 ? update.typeBits() : existing.typeBits();
    int groupId = preferKnownInt(update.groupId(), existing.groupId());
    String bufferName = preferNonBlank(update.bufferName(), existing.bufferName());
    return new QuasselCoreDatastreamCodec.BufferInfoValue(
        bufferId, networkId, typeBits, groupId, bufferName);
  }

  private static int preferKnownInt(int preferred, int fallback) {
    if (preferred != 0 && preferred != -1) return preferred;
    return fallback;
  }

  private static String preferNonBlank(String preferred, String fallback) {
    String p = Objects.toString(preferred, "").trim();
    if (!p.isEmpty()) return p;
    return Objects.toString(fallback, "").trim();
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

  private static boolean isPlainMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_PLAIN) != 0;
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

  private static boolean isNoticeMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_NOTICE) != 0;
  }

  private static boolean isActionMessage(int typeBits) {
    return (typeBits & MESSAGE_TYPE_ACTION) != 0;
  }

  private static boolean isBacklogMessage(int flags) {
    return (flags & MESSAGE_FLAG_BACKLOG) != 0;
  }

  private static boolean isHistoryTextMessage(int typeBits) {
    return isPlainMessage(typeBits) || isActionMessage(typeBits) || isNoticeMessage(typeBits);
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
    if (session == null) return -1;
    LinkedHashSet<Integer> knownIds = collectKnownNetworkIds(session);
    if (knownIds.isEmpty()) return -1;
    QuasselCoreAuthHandshake.AuthResult auth = session.authResult.get();
    if (auth == null) {
      return knownIds.iterator().next();
    }
    int primary = auth.primaryNetworkId();
    if (primary >= 0 && knownIds.contains(primary)) return primary;
    if (auth.networkIds() != null) {
      for (Integer id : auth.networkIds()) {
        if (id != null && id.intValue() >= 0 && knownIds.contains(id.intValue())) {
          return id.intValue();
        }
      }
    }
    return knownIds.iterator().next();
  }

  private static int parseNetworkId(String raw) {
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return -1;
    int slash = token.lastIndexOf('/');
    if (slash >= 0 && slash < token.length() - 1) {
      token = token.substring(slash + 1).trim();
    }
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private static String parseObjectLeafToken(String objectName) {
    String token = Objects.toString(objectName, "").trim();
    if (token.isEmpty()) return "";
    int slash = token.lastIndexOf('/');
    if (slash >= 0 && slash < token.length() - 1) {
      token = token.substring(slash + 1).trim();
    }
    return token;
  }

  private static Boolean parseBoolean(Object raw) {
    if (raw instanceof Boolean b) {
      return b;
    }
    if (raw instanceof Number n) {
      return n.intValue() != 0;
    }
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return null;
    return switch (token) {
      case "1", "true", "yes", "on" -> Boolean.TRUE;
      case "0", "false", "no", "off" -> Boolean.FALSE;
      default -> null;
    };
  }

  private static void rememberPendingCreatedNetworkName(
      QuasselSession session, String networkName) {
    if (session == null) return;
    String name = Objects.toString(networkName, "").trim();
    if (name.isEmpty()) return;
    long nowMs = System.currentTimeMillis();
    prunePendingCreatedNetworkNames(session, nowMs);
    session.pendingCreatedNetworkNames.addLast(new PendingCreatedNetworkName(name, nowMs));
    while (session.pendingCreatedNetworkNames.size() > MAX_PENDING_NETWORK_CREATE_NAMES) {
      session.pendingCreatedNetworkNames.pollFirst();
    }
  }

  private static String claimPendingCreatedNetworkName(QuasselSession session) {
    if (session == null) return "";
    long nowMs = System.currentTimeMillis();
    prunePendingCreatedNetworkNames(session, nowMs);
    PendingCreatedNetworkName pending = session.pendingCreatedNetworkNames.pollFirst();
    if (pending == null) return "";
    String name = Objects.toString(pending.networkName(), "").trim();
    return name;
  }

  private static void prunePendingCreatedNetworkNames(QuasselSession session, long nowMs) {
    if (session == null) return;
    long cutoffMs = nowMs - PENDING_NETWORK_CREATE_NAME_TTL_MS;
    for (; ; ) {
      PendingCreatedNetworkName first = session.pendingCreatedNetworkNames.peekFirst();
      if (first == null) break;
      if (first.observedAtMs() >= cutoffMs) break;
      session.pendingCreatedNetworkNames.pollFirst();
    }
  }

  private void observeKnownNetworks(
      QuasselSession session, QuasselCoreAuthHandshake.AuthResult authResult) {
    if (session == null || authResult == null || authResult.networkIds() == null) return;
    for (Integer id : authResult.networkIds()) {
      if (id == null) continue;
      observeKnownNetwork(session, id.intValue(), "");
    }
  }

  private void observeKnownIdentities(
      QuasselSession session, QuasselCoreAuthHandshake.AuthResult authResult) {
    if (session == null || authResult == null || authResult.initialIdentities() == null) return;
    for (Map.Entry<Integer, Map<String, Object>> entry :
        authResult.initialIdentities().entrySet()) {
      if (entry == null || entry.getKey() == null || entry.getKey().intValue() < 0) continue;
      int identityId = entry.getKey().intValue();
      Map<String, Object> normalized = normalizeObjectMap(entry.getValue());
      String identityName =
          firstNonBlank(
              mapValueIgnoreCase(normalized, "identityName"),
              mapValueIgnoreCase(normalized, "identityname"));
      observeKnownIdentity(session, identityId, identityName);
      if (!normalized.isEmpty()) {
        session.identityStateByIdentityId.put(identityId, normalized);
      }
    }
    trimMapToMaxSize(session.identityStateByIdentityId, MAX_NETWORK_IDENTITIES_PER_SESSION);
  }

  private void observeKnownNetwork(QuasselSession session, int networkId, String networkName) {
    observeKnownNetwork(session, networkId, networkName, true);
  }

  private void observeKnownNetwork(
      QuasselSession session, int networkId, String networkName, boolean emitSnapshotEvent) {
    if (session == null || networkId < 0) return;
    session.removedNetworkIds.remove(networkId);
    String display = Objects.toString(networkName, "").trim();
    if (display.isEmpty()) {
      display = Objects.toString(session.networkDisplayByNetworkId.get(networkId), "").trim();
    }
    if (display.isEmpty()) {
      display = "network-" + networkId;
    }

    String token = sanitizeNetworkToken(display);
    if (token.isEmpty()) {
      token = "id-" + networkId;
    }
    String previousToken = session.networkTokenByNetworkId.get(networkId);
    if (previousToken != null && !previousToken.isBlank()) {
      session.networkIdByTokenLower.remove(previousToken.toLowerCase(Locale.ROOT));
    }
    String uniqueToken = token;
    Integer assigned = session.networkIdByTokenLower.get(uniqueToken.toLowerCase(Locale.ROOT));
    if (assigned != null && assigned.intValue() != networkId) {
      uniqueToken = token + "-" + networkId;
    }

    session.networkDisplayByNetworkId.put(networkId, display);
    session.networkTokenByNetworkId.put(networkId, uniqueToken);
    session.networkIdByTokenLower.put(uniqueToken.toLowerCase(Locale.ROOT), networkId);
    trimMapToMaxSize(session.networkDisplayByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
    trimMapToMaxSize(session.networkTokenByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
    trimMapToMaxSize(session.networkIdByTokenLower, MAX_NETWORK_IDENTITIES_PER_SESSION);
    if (emitSnapshotEvent) {
      emitQuasselNetworkSnapshotEvent(session, "observe-known-network");
    }
  }

  private void forgetKnownNetwork(QuasselSession session, int networkId) {
    if (session == null || networkId < 0) return;
    session.removedNetworkIds.add(networkId);
    session.networkCurrentNickByNetworkId.remove(networkId);
    session.networkStateByNetworkId.remove(networkId);
    session.networkDisplayByNetworkId.remove(networkId);
    session.networkTokenByNetworkId.remove(networkId);
    session.enabledCapabilitiesByNetworkId.remove(networkId);
    session.monitorSupportByNetworkId.remove(networkId);
    session.multilineLimitsByNetworkId.remove(networkId);
    for (Map.Entry<String, Integer> entry :
        new ArrayList<>(session.networkIdByTokenLower.entrySet())) {
      if (entry == null) continue;
      Integer mapped = entry.getValue();
      if (mapped == null || mapped.intValue() != networkId) continue;
      session.networkIdByTokenLower.remove(entry.getKey(), mapped);
    }
    for (Map.Entry<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> entry :
        new ArrayList<>(session.bufferInfosById.entrySet())) {
      if (entry == null || entry.getKey() == null) continue;
      QuasselCoreDatastreamCodec.BufferInfoValue info = entry.getValue();
      if (info == null || info.networkId() != networkId) continue;
      session.bufferInfosById.remove(entry.getKey(), info);
    }
    for (Map.Entry<String, Integer> entry :
        new ArrayList<>(session.targetNetworkHintsByTargetLower.entrySet())) {
      if (entry == null) continue;
      Integer mapped = entry.getValue();
      if (mapped == null || mapped.intValue() != networkId) continue;
      session.targetNetworkHintsByTargetLower.remove(entry.getKey(), mapped);
    }
    String membershipPrefix = networkId + "|";
    for (String membershipKey : new ArrayList<>(session.joinedChannelMembershipKeys)) {
      if (membershipKey == null || !membershipKey.startsWith(membershipPrefix)) continue;
      session.joinedChannelMembershipKeys.remove(membershipKey);
    }
    emitQuasselNetworkSnapshotEvent(session, "forget-known-network");
  }

  private void observeNetworkStateSnapshot(
      QuasselSession session, int networkId, Map<?, ?> stateMap) {
    if (session == null || networkId < 0 || stateMap == null || stateMap.isEmpty()) return;
    Map<String, Object> normalized = normalizeObjectMap(stateMap);
    if (normalized.isEmpty()) return;
    Map<String, Object> merged =
        session.networkStateByNetworkId.merge(
            networkId,
            normalized,
            (existing, incoming) -> {
              LinkedHashMap<String, Object> joined = new LinkedHashMap<>();
              if (existing != null && !existing.isEmpty()) {
                joined.putAll(existing);
              }
              if (incoming != null && !incoming.isEmpty()) {
                joined.putAll(incoming);
              }
              return Collections.unmodifiableMap(joined);
            });
    int identityId = parseNetworkIdentityId(merged);
    if (identityId >= 0) {
      observeKnownIdentity(session, identityId, "");
    }
    trimMapToMaxSize(session.networkStateByNetworkId, MAX_NETWORK_IDENTITIES_PER_SESSION);
    reconcileJoinedChannelsForNetworkState(session, networkId, merged);
    emitQuasselNetworkSnapshotEvent(session, "observe-network-state");
  }

  private void reconcileJoinedChannelsForNetworkState(
      QuasselSession session, int networkId, Map<?, ?> networkState) {
    if (session == null || networkId < 0) return;
    if (!parseNetworkConnected(networkState)) {
      clearChannelMembershipForNetwork(session, networkId);
      return;
    }
    emitJoinedChannelsFromKnownBuffers(session, networkId);
  }

  private void emitJoinedChannelsFromKnownBuffers(QuasselSession session, int networkId) {
    if (session == null || networkId < 0) return;
    Instant now = Instant.now();
    for (QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo : session.bufferInfosById.values()) {
      if (bufferInfo == null || bufferInfo.networkId() != networkId) continue;
      emitJoinedChannelFromBufferInfoIfNeeded(session, bufferInfo, now);
    }
  }

  private void emitJoinedChannelFromBufferInfoIfNetworkConnected(
      QuasselSession session, QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo) {
    if (session == null || bufferInfo == null) return;
    int networkId = bufferInfo.networkId();
    if (networkId < 0) return;
    Map<String, Object> state = session.networkStateByNetworkId.get(networkId);
    if (!parseNetworkConnected(state)) return;
    emitJoinedChannelFromBufferInfoIfNeeded(session, bufferInfo, Instant.now());
  }

  private void emitJoinedChannelFromBufferInfoIfNeeded(
      QuasselSession session,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      Instant observedAt) {
    if (session == null || bufferInfo == null) return;
    String channel = normalizedBufferName(bufferInfo);
    if (channel.isEmpty()) return;
    if (!isChannelBuffer(bufferInfo) && !looksLikeChannel(channel)) return;

    int networkId = bufferInfo.networkId();
    String qualified = qualifyTargetForNetwork(session, channel, networkId);
    if (qualified.isEmpty()) return;
    noteTargetNetworkHint(session, qualified, networkId, true);
    Instant at = observedAt == null ? Instant.now() : observedAt;
    if (markChannelMembershipJoined(session, qualified, networkId)) {
      bus.onNext(new ServerIrcEvent(session.serverId, new IrcEvent.JoinedChannel(at, qualified)));
    }
  }

  private static void clearChannelMembershipForNetwork(QuasselSession session, int networkId) {
    if (session == null || networkId < 0) return;
    String membershipPrefix = networkId + "|";
    for (String membershipKey : new ArrayList<>(session.joinedChannelMembershipKeys)) {
      if (membershipKey == null || !membershipKey.startsWith(membershipPrefix)) continue;
      session.joinedChannelMembershipKeys.remove(membershipKey);
    }
  }

  private void observeKnownIdentity(QuasselSession session, int identityId, String identityName) {
    if (session == null || identityId <= 0) return;
    String name = Objects.toString(identityName, "").trim();
    boolean added = session.knownIdentityIds.add(identityId);
    String previousName = session.identityNameByIdentityId.get(identityId);
    if (!name.isEmpty()) {
      session.identityNameByIdentityId.put(identityId, name);
      trimMapToMaxSize(session.identityNameByIdentityId, MAX_NETWORK_IDENTITIES_PER_SESSION);
    } else {
      session.identityNameByIdentityId.putIfAbsent(identityId, "");
    }
    if (added || (!name.isEmpty() && !name.equals(previousName))) {
      log.debug(
          "Observed Quassel identity: serverId={}, identityId={}, identityName='{}', knownIdentityIds={}",
          session.serverId,
          identityId,
          session.identityNameByIdentityId.get(identityId),
          session.knownIdentityIds);
    }
    quasselIdentityEvents.onNext(new QuasselIdentityObservedEvent(session.serverId, identityId));
  }

  private static int firstKnownIdentityId(QuasselSession session) {
    if (session == null || session.knownIdentityIds.isEmpty()) return -1;
    int best = Integer.MAX_VALUE;
    for (Integer candidate : session.knownIdentityIds) {
      if (candidate == null || candidate.intValue() < 0) continue;
      if (candidate.intValue() < best) {
        best = candidate.intValue();
      }
    }
    return best == Integer.MAX_VALUE ? -1 : best;
  }

  private static boolean hasKnownIdentity(QuasselSession session) {
    return firstKnownIdentityId(session) >= 0;
  }

  private List<QuasselCoreNetworkSummary> snapshotQuasselCoreNetworks(QuasselSession session) {
    if (session == null) return List.of();
    LinkedHashSet<Integer> knownIds = collectKnownNetworkIds(session);
    if (knownIds.isEmpty()) return List.of();

    ArrayList<Integer> orderedIds = new ArrayList<>(knownIds);
    Collections.sort(orderedIds);

    ArrayList<QuasselCoreNetworkSummary> out = new ArrayList<>(orderedIds.size());
    for (Integer id : orderedIds) {
      if (id == null || id.intValue() < 0) continue;
      int networkId = id.intValue();
      Map<String, Object> state = session.networkStateByNetworkId.get(networkId);
      String networkName =
          firstNonBlank(
              mapValueIgnoreCase(state, "networkName"),
              mapValueIgnoreCase(state, "networkname"),
              mapValueIgnoreCase(state, "name"),
              session.networkDisplayByNetworkId.get(networkId));
      if (networkName.isBlank()) networkName = "network-" + networkId;
      observeKnownNetwork(session, networkId, networkName, false);

      NetworkServerEndpoint endpoint = parsePrimaryNetworkServer(state);
      Map<String, Object> rawState =
          state == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(state));
      out.add(
          new QuasselCoreNetworkSummary(
              networkId,
              networkName,
              parseNetworkConnected(state),
              parseNetworkEnabled(state),
              parseNetworkIdentityId(state),
              endpoint.host(),
              endpoint.port(),
              endpoint.useTls(),
              rawState));
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private void emitQuasselNetworkSnapshotEvent(QuasselSession session, String source) {
    if (session == null) return;
    String sid = normalizeServerId(session.serverId);
    if (sid.isEmpty()) return;
    List<QuasselCoreNetworkSummary> snapshot = snapshotQuasselCoreNetworks(session);
    quasselNetworkEvents.onNext(new QuasselCoreNetworkSnapshotEvent(sid, snapshot, source));
  }

  private static boolean parseNetworkConnected(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return false;
    Boolean direct = parseBoolean(mapValueIgnoreCase(state, "isConnected"));
    if (direct != null) return direct;
    direct = parseBoolean(mapValueIgnoreCase(state, "connected"));
    if (direct != null) return direct;

    int connectionState =
        firstIntFromMapKeys(state, "connectionState", "connectionstate", "state", "status");
    if (connectionState >= 0) return connectionState != 0;
    return false;
  }

  private static boolean parseNetworkEnabled(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return true;
    Boolean enabled = parseBoolean(mapValueIgnoreCase(state, "isEnabled"));
    if (enabled != null) return enabled;
    enabled = parseBoolean(mapValueIgnoreCase(state, "enabled"));
    if (enabled != null) return enabled;
    Boolean initialized = parseBoolean(mapValueIgnoreCase(state, "isInitialized"));
    if (initialized != null) return initialized;
    return true;
  }

  private static int parseNetworkIdentityId(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return -1;
    Object identity = firstMapValueByKeyIgnoreCase(state, "identity", "identityId", "identityid");
    if (identity instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      int parsed = tryParseInt(userType.value());
      if (parsed > 0) return parsed;
    }
    int id = firstIntFromMapKeys(state, "identity", "identityId", "identityid");
    return id > 0 ? id : -1;
  }

  private static int identityIdFromStateMap(Map<?, ?> state, int fallbackIdentityId) {
    if (state == null || state.isEmpty()) return fallbackIdentityId;
    int id = firstIntFromMapKeys(state, "identityId", "identityid");
    if (id > 0) return id;
    Object wrapped = firstMapValueByKeyIgnoreCase(state, "identityId", "identityid");
    if (wrapped instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      int parsed = tryParseInt(userType.value());
      if (parsed > 0) return parsed;
    }
    return fallbackIdentityId;
  }

  private static String parseIdentityName(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return "";
    return firstNonBlank(
        mapValueIgnoreCase(state, "identityName"),
        mapValueIgnoreCase(state, "identityname"),
        mapValueIgnoreCase(state, "name"));
  }

  private static NetworkServerEndpoint parsePrimaryNetworkServer(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return NetworkServerEndpoint.EMPTY;
    Object rawServerList =
        firstMapValueByKeyIgnoreCase(state, "ServerList", "serverList", "servers");
    if (rawServerList instanceof List<?> list) {
      for (Object entry : list) {
        if (entry instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
          Object value = userType.value();
          if (value instanceof Map<?, ?> userMap) {
            NetworkServerEndpoint parsed = parseNetworkServerEntry(userMap);
            if (!parsed.host().isBlank()) return parsed;
          }
        }
        if (!(entry instanceof Map<?, ?> serverMap)) continue;
        NetworkServerEndpoint parsed = parseNetworkServerEntry(serverMap);
        if (!parsed.host().isBlank()) return parsed;
      }
    }
    if (rawServerList instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      Object value = userType.value();
      if (value instanceof Map<?, ?> userMap) {
        NetworkServerEndpoint parsed = parseNetworkServerEntry(userMap);
        if (!parsed.host().isBlank()) return parsed;
      }
    }
    if (rawServerList instanceof Map<?, ?> serverMap) {
      NetworkServerEndpoint parsed = parseNetworkServerEntry(serverMap);
      if (!parsed.host().isBlank()) return parsed;
    }
    return parseNetworkServerEntry(state);
  }

  private static NetworkServerEndpoint parseNetworkServerEntry(Map<?, ?> serverMap) {
    if (serverMap == null || serverMap.isEmpty()) return NetworkServerEndpoint.EMPTY;
    String host =
        firstNonBlank(
            mapValueIgnoreCase(serverMap, "server"),
            mapValueIgnoreCase(serverMap, "host"),
            mapValueIgnoreCase(serverMap, "hostname"));
    if (host.isBlank()) return NetworkServerEndpoint.EMPTY;

    int port = firstIntFromMapKeys(serverMap, "port");
    Boolean useTls = parseBoolean(mapValueIgnoreCase(serverMap, "useSSL"));
    if (useTls == null) useTls = parseBoolean(mapValueIgnoreCase(serverMap, "ssl"));
    boolean tls = Boolean.TRUE.equals(useTls);
    int resolvedPort = port > 0 ? port : (tls ? 6697 : 6667);
    return new NetworkServerEndpoint(host, resolvedPort, tls);
  }

  private static LinkedHashSet<Integer> collectKnownNetworkIds(QuasselSession session) {
    LinkedHashSet<Integer> ids = new LinkedHashSet<>();
    if (session == null) return ids;
    QuasselCoreAuthHandshake.AuthResult auth = session.authResult.get();
    if (auth != null && auth.networkIds() != null) {
      for (Integer id : auth.networkIds()) {
        if (id != null && id.intValue() >= 0) ids.add(id.intValue());
      }
    }
    for (QuasselCoreDatastreamCodec.BufferInfoValue info : session.bufferInfosById.values()) {
      if (info != null && info.networkId() >= 0) ids.add(info.networkId());
    }
    ids.addAll(session.networkDisplayByNetworkId.keySet());
    ids.addAll(session.networkTokenByNetworkId.keySet());
    ids.addAll(session.networkStateByNetworkId.keySet());
    ids.removeIf(
        id -> id == null || id.intValue() < 0 || session.removedNetworkIds.contains(id.intValue()));
    return ids;
  }

  private int resolveQuasselNetworkId(
      QuasselSession session, String serverId, String networkIdOrName, String operation) {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    String raw = Objects.toString(networkIdOrName, "").trim();
    if (raw.isEmpty()) {
      throw new IllegalArgumentException("network id/name is required");
    }

    int parsedNumeric = parseNetworkId(raw);
    if (parsedNumeric >= 0) return parsedNumeric;

    String lowered = raw.toLowerCase(Locale.ROOT);
    Integer byToken = session.networkIdByTokenLower.get(lowered);
    if (byToken != null && byToken.intValue() >= 0) return byToken.intValue();

    String sanitized = sanitizeNetworkToken(raw).toLowerCase(Locale.ROOT);
    if (!sanitized.isBlank()) {
      Integer bySanitized = session.networkIdByTokenLower.get(sanitized);
      if (bySanitized != null && bySanitized.intValue() >= 0) return bySanitized.intValue();
    }

    for (Map.Entry<Integer, String> entry : session.networkDisplayByNetworkId.entrySet()) {
      if (entry == null || entry.getKey() == null) continue;
      String display = Objects.toString(entry.getValue(), "").trim();
      if (display.equalsIgnoreCase(raw)) return entry.getKey().intValue();
    }
    for (Map.Entry<Integer, Map<String, Object>> entry :
        session.networkStateByNetworkId.entrySet()) {
      if (entry == null || entry.getKey() == null) continue;
      Map<String, Object> state = entry.getValue();
      String display =
          firstNonBlank(
              mapValueIgnoreCase(state, "networkName"),
              mapValueIgnoreCase(state, "networkname"),
              mapValueIgnoreCase(state, "name"));
      if (display.equalsIgnoreCase(raw)) return entry.getKey().intValue();
    }

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
        Map<String, Object> state = session.networkStateByNetworkId.get(primary);
        int fromPrimary = parseNetworkIdentityId(state);
        if (fromPrimary >= 0) return fromPrimary;
      }
      for (Map<String, Object> state : session.networkStateByNetworkId.values()) {
        int parsed = parseNetworkIdentityId(state);
        if (parsed >= 0) return parsed;
      }
      log.debug(
          "Falling back to default Quassel identity id=1: serverId={}, requestedIdentityId={}, knownIdentityIds={}, identityStateKeys={}, networkStateKeys={}",
          session.serverId,
          requestedIdentityId,
          session.knownIdentityIds,
          session.identityStateByIdentityId.keySet(),
          session.networkStateByNetworkId.keySet());
    }
    return 1;
  }

  private String qualifyTargetForNetwork(QuasselSession session, String baseTarget, int networkId) {
    String base = Objects.toString(baseTarget, "").trim();
    if (base.isEmpty()) return "";
    if (session == null || networkId < 0) return base;
    if (knownNetworkCount(session) <= 1) return base;
    String token = networkTokenForNetworkId(session, networkId);
    if (token.isEmpty()) return base;
    return base + NETWORK_QUALIFIER_PREFIX + token + NETWORK_QUALIFIER_SUFFIX;
  }

  private static int knownNetworkCount(QuasselSession session) {
    return collectKnownNetworkIds(session).size();
  }

  private String networkTokenForNetworkId(QuasselSession session, int networkId) {
    if (session == null || networkId < 0) return "";
    String token = Objects.toString(session.networkTokenByNetworkId.get(networkId), "").trim();
    if (!token.isEmpty()) return token;
    observeKnownNetwork(session, networkId, "");
    return Objects.toString(session.networkTokenByNetworkId.get(networkId), "").trim();
  }

  private static String sanitizeNetworkToken(String raw) {
    String in = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (in.isEmpty()) return "";
    StringBuilder out = new StringBuilder(in.length());
    boolean pendingDash = false;
    for (int i = 0; i < in.length(); i++) {
      char c = in.charAt(i);
      boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
      if (allowed) {
        if (pendingDash && out.length() > 0) {
          out.append('-');
        }
        out.append(c);
        pendingDash = false;
      } else {
        pendingDash = out.length() > 0;
      }
    }
    return out.toString();
  }

  private static String firstNonBlank(Object... values) {
    if (values == null || values.length == 0) return "";
    for (Object value : values) {
      String text = Objects.toString(value, "").trim();
      if (!text.isEmpty()) {
        return text;
      }
    }
    return "";
  }

  private static int networkIdFromStateMap(Map<?, ?> map, int fallbackNetworkId) {
    if (map == null || map.isEmpty()) return fallbackNetworkId;
    int byNetworkId = tryParseInt(map.get("networkId"));
    if (byNetworkId >= 0) return byNetworkId;
    int byNetwork = tryParseInt(map.get("network"));
    if (byNetwork >= 0) return byNetwork;
    int byId = tryParseInt(map.get("id"));
    if (byId >= 0) return byId;
    return fallbackNetworkId;
  }

  private static int tryParseInt(Object raw) {
    if (raw instanceof Number n) {
      return n.intValue();
    }
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return -1;
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private static long tryParseLong(Object raw) {
    if (raw instanceof Number n) {
      return n.longValue();
    }
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return -1L;
    try {
      return Long.parseLong(token);
    } catch (NumberFormatException ignored) {
      return -1L;
    }
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

  private IrcEvent.ServerResponseLine renderServerResponse(
      Instant at, String displayLine, String rawLine, String messageId) {
    String display = Objects.toString(displayLine, "").trim();
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) raw = display;
    int code = extractNumericCode(raw);
    String message = display;
    if (code != 0) {
      String fromRaw = renderNumericMessage(raw);
      if (!fromRaw.isEmpty()) {
        message = fromRaw;
      }
    }
    return new IrcEvent.ServerResponseLine(at, code, message, raw, messageId, Map.of());
  }

  private void emitServerResponseLine(
      String serverId,
      Instant at,
      String displayLine,
      String rawLine,
      String messageId,
      Map<String, String> ircv3Tags) {
    IrcEvent.ServerResponseLine response =
        renderServerResponse(at, displayLine, rawLine, messageId);
    Map<String, String> tags = ircv3Tags == null ? Map.of() : ircv3Tags;
    bus.onNext(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.ServerResponseLine(
                response.at(),
                response.code(),
                response.message(),
                response.rawLine(),
                response.messageId(),
                tags)));
  }

  private static int extractNumericCode(String rawLine) {
    String payload = stripIrcEnvelope(rawLine);
    if (payload.length() < 3) return 0;
    if (!Character.isDigit(payload.charAt(0))
        || !Character.isDigit(payload.charAt(1))
        || !Character.isDigit(payload.charAt(2))) {
      return 0;
    }
    if (payload.length() > 3 && payload.charAt(3) != ' ') return 0;
    try {
      return Integer.parseInt(payload.substring(0, 3));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String renderNumericMessage(String rawLine) {
    String payload = stripIrcEnvelope(rawLine);
    int code = extractNumericCode(payload);
    if (code == 0 || payload.length() <= 3) {
      return "";
    }
    String tail = payload.substring(3).trim();
    int trailingStart = tail.indexOf(" :");
    if (trailingStart >= 0 && trailingStart + 2 < tail.length()) {
      return tail.substring(trailingStart + 2).trim();
    }
    if (tail.startsWith(":")) {
      return tail.substring(1).trim();
    }
    return tail;
  }

  private static String stripIrcEnvelope(String rawLine) {
    String line = Objects.toString(rawLine, "").trim();
    if (line.isEmpty()) return "";
    if (line.startsWith("@")) {
      int sp = line.indexOf(' ');
      if (sp <= 0 || sp >= line.length() - 1) return "";
      line = line.substring(sp + 1).trim();
    }
    if (line.startsWith(":")) {
      int sp = line.indexOf(' ');
      if (sp <= 1 || sp >= line.length() - 1) return "";
      line = line.substring(sp + 1).trim();
    }
    return line;
  }

  private static String parseNickChange(String content, String fallback) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return fallback;
    String lower = text.toLowerCase(Locale.ROOT);
    int idx = lower.lastIndexOf(" is now known as ");
    if (idx >= 0) {
      String tail = text.substring(idx + " is now known as ".length()).trim();
      if (!tail.isEmpty()) return tail.split("\\s+")[0];
    }
    String[] parts = text.split("\\s+");
    if (parts.length > 0) {
      String last = parts[parts.length - 1].trim();
      if (!last.isEmpty()) return last;
    }
    return fallback;
  }

  private static String parseTopic(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    String lower = text.toLowerCase(Locale.ROOT);
    int idx = lower.indexOf(" topic to ");
    if (idx >= 0) {
      String topic = text.substring(idx + " topic to ".length()).trim();
      return stripWrappingQuotes(topic);
    }
    idx = lower.indexOf(" changed topic to ");
    if (idx >= 0) {
      String topic = text.substring(idx + " changed topic to ".length()).trim();
      return stripWrappingQuotes(topic);
    }
    return stripWrappingQuotes(text);
  }

  private static String parseModeDetails(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    String lower = text.toLowerCase(Locale.ROOT);
    int idx = lower.indexOf(" mode ");
    if (idx >= 0) {
      return text.substring(idx + " mode ".length()).trim();
    }
    idx = lower.indexOf(" set mode ");
    if (idx >= 0) {
      return text.substring(idx + " set mode ".length()).trim();
    }
    return text;
  }

  private static KickDetails parseKickDetails(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return new KickDetails("", "");
    String lower = text.toLowerCase(Locale.ROOT);
    String tail;
    if (lower.startsWith("kicked ")) {
      tail = text.substring("kicked ".length()).trim();
    } else {
      int idx = lower.indexOf(" kicked ");
      tail = idx >= 0 ? text.substring(idx + " kicked ".length()).trim() : text;
    }
    String nick = tail;
    String reason = "";
    int reasonStart = tail.indexOf('(');
    int reasonEnd = tail.lastIndexOf(')');
    if (reasonStart >= 0 && reasonEnd > reasonStart) {
      nick = tail.substring(0, reasonStart).trim();
      reason = tail.substring(reasonStart + 1, reasonEnd).trim();
    } else {
      String[] parts = tail.split("\\s+", 2);
      nick = parts.length > 0 ? parts[0].trim() : "";
      if (parts.length > 1) reason = parts[1].trim();
    }
    return new KickDetails(nick, reason);
  }

  private static String firstChannelToken(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    for (String token : text.split("\\s+")) {
      if (looksLikeChannel(token)) return token;
      String cleaned = stripPunctuation(token);
      if (looksLikeChannel(cleaned)) return cleaned;
    }
    return "";
  }

  private static boolean looksLikeChannel(String token) {
    String t = Objects.toString(token, "").trim();
    if (t.length() < 2) return false;
    char c = t.charAt(0);
    return c == '#' || c == '&' || c == '+' || c == '!';
  }

  private static String stripPunctuation(String token) {
    String t = Objects.toString(token, "").trim();
    while (!t.isEmpty() && (t.endsWith(",") || t.endsWith(".") || t.endsWith(":"))) {
      t = t.substring(0, t.length() - 1).trim();
    }
    return t;
  }

  private static String stripWrappingQuotes(String text) {
    String t = Objects.toString(text, "").trim();
    if (t.length() >= 2) {
      if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
        return t.substring(1, t.length() - 1).trim();
      }
    }
    return t;
  }

  private static String normalizeReason(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    int open = text.lastIndexOf('(');
    int close = text.lastIndexOf(')');
    if (open >= 0 && close > open) {
      String reason = text.substring(open + 1, close).trim();
      if (!reason.isEmpty()) return reason;
    }
    return text;
  }

  private static String extractNick(String sender) {
    String hostmask = Objects.toString(sender, "").trim();
    if (hostmask.isEmpty()) return "";
    int bang = hostmask.indexOf('!');
    if (bang <= 0) return hostmask;
    return hostmask.substring(0, bang);
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

  private record ParsedIrcEnvelope(
      boolean parsed,
      String rawLine,
      String command,
      String source,
      List<String> params,
      String trailing,
      Map<String, String> ircv3Tags) {
    private static ParsedIrcEnvelope empty(String rawLine) {
      return new ParsedIrcEnvelope(
          false, Objects.toString(rawLine, ""), "", "", List.of(), "", Map.of());
    }

    private String firstParam() {
      if (params == null || params.isEmpty()) return "";
      return Objects.toString(params.get(0), "").trim();
    }

    private String secondParam() {
      if (params == null || params.size() < 2) return "";
      return Objects.toString(params.get(1), "").trim();
    }
  }

  private record ParsedStandardReply(
      String command, String code, String context, String description) {}

  private record NetworkServerEndpoint(String host, int port, boolean useTls) {
    private static final NetworkServerEndpoint EMPTY = new NetworkServerEndpoint("", 0, false);
  }

  private record MonitorSupportState(boolean available, long limit) {}

  private record MultilineLimitState(long maxBytes, long maxLines) {}

  private static final class MonitorSupportCollector {
    private boolean known;
    private boolean available;
    private boolean limitSeen;
    private long limit;

    void observeAvailability(boolean enabled) {
      known = true;
      available = enabled;
      if (!enabled && !limitSeen) {
        limit = 0L;
      }
    }

    void observeLimit(long maxTargets) {
      if (maxTargets < 0L) return;
      known = true;
      limitSeen = true;
      limit = maxTargets;
      available = true;
    }

    MonitorSupportState toStateOrNull() {
      if (!known) return null;
      long parsedLimit = limitSeen ? Math.max(0L, limit) : 0L;
      return new MonitorSupportState(available, parsedLimit);
    }
  }

  private static final class MultilineLimitCollector {
    private boolean bytesObserved;
    private boolean linesObserved;
    private long maxBytes;
    private long maxLines;

    void observeBytes(long value) {
      if (value < 0L) return;
      bytesObserved = true;
      maxBytes = value;
    }

    void observeLines(long value) {
      if (value < 0L) return;
      linesObserved = true;
      maxLines = value;
    }

    MultilineLimitState toStateOrNull() {
      if (!bytesObserved && !linesObserved) return null;
      long bytes = bytesObserved ? Math.max(0L, maxBytes) : 0L;
      long lines = linesObserved ? Math.max(0L, maxLines) : 0L;
      return new MultilineLimitState(bytes, lines);
    }
  }

  private record OutboundRawRoute(
      String command,
      QualifiedTarget requestedTarget,
      String rewrittenRawLine,
      int targetTypeBitsHint) {}

  private record ReadMarkerUpdate(int bufferId, long msgId) {}

  private record QualifiedTarget(String rawTarget, String baseTarget, String networkToken) {}

  private record HistoryRequestContext(
      QuasselSession session,
      String target,
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      int limit) {}

  private enum HistorySelectorKind {
    WILDCARD,
    MSGID,
    TIMESTAMP
  }

  private record HistorySelector(HistorySelectorKind kind, long msgId, Instant timestamp) {}

  private static final class TargetHistoryState {
    private long oldestMsgId = Long.MAX_VALUE;
    private long newestMsgId = UNKNOWN_MSG_ID;
    private long oldestTsEpochMs = Long.MAX_VALUE;
    private long newestTsEpochMs = UNKNOWN_MSG_ID;
    private final NavigableMap<Long, Long> timestampByMsgId = new TreeMap<>();

    synchronized void observe(long messageId, long timestampEpochMs) {
      if (messageId <= 0L) return;
      long ts = timestampEpochMs > 0L ? timestampEpochMs : System.currentTimeMillis();
      if (messageId < oldestMsgId) oldestMsgId = messageId;
      if (messageId > newestMsgId) newestMsgId = messageId;
      if (ts < oldestTsEpochMs) oldestTsEpochMs = ts;
      if (ts > newestTsEpochMs) newestTsEpochMs = ts;

      timestampByMsgId.put(messageId, ts);
      if (timestampByMsgId.size() > MAX_HISTORY_MSGID_SAMPLES_PER_TARGET) {
        boolean dropOldest =
            timestampByMsgId.lastKey() - messageId < messageId - timestampByMsgId.firstKey();
        if (dropOldest) {
          timestampByMsgId.pollFirstEntry();
        } else {
          timestampByMsgId.pollLastEntry();
        }
      }
    }

    synchronized long anchorForTimestamp(Instant timestamp) {
      if (newestMsgId <= 0L) return UNKNOWN_MSG_ID;
      long ts = (timestamp == null ? Instant.now() : timestamp).toEpochMilli();
      if (oldestTsEpochMs != Long.MAX_VALUE && ts <= oldestTsEpochMs) {
        return oldestMsgId > 0L ? oldestMsgId : newestMsgId;
      }
      if (newestTsEpochMs > 0L && ts >= newestTsEpochMs) {
        return newestMsgId;
      }
      if (oldestTsEpochMs == Long.MAX_VALUE || newestTsEpochMs <= 0L) {
        return newestMsgId;
      }
      long midpoint = oldestTsEpochMs + ((newestTsEpochMs - oldestTsEpochMs) / 2L);
      return ts <= midpoint ? oldestMsgId : newestMsgId;
    }

    synchronized long timestampForMsgId(long messageId) {
      if (messageId <= 0L || timestampByMsgId.isEmpty()) return UNKNOWN_MSG_ID;
      Long exact = timestampByMsgId.get(messageId);
      if (exact != null && exact.longValue() > 0L) {
        return exact.longValue();
      }

      Map.Entry<Long, Long> floor = timestampByMsgId.floorEntry(messageId);
      Map.Entry<Long, Long> ceil = timestampByMsgId.ceilingEntry(messageId);
      if (floor == null && ceil == null) return UNKNOWN_MSG_ID;
      if (floor == null) return sanitizeTimestampSample(ceil.getValue());
      if (ceil == null) return sanitizeTimestampSample(floor.getValue());

      long floorDelta = Math.abs(messageId - floor.getKey());
      long ceilDelta = Math.abs(ceil.getKey() - messageId);
      return floorDelta <= ceilDelta
          ? sanitizeTimestampSample(floor.getValue())
          : sanitizeTimestampSample(ceil.getValue());
    }

    private static long sanitizeTimestampSample(Long sample) {
      if (sample == null || sample.longValue() <= 0L) return UNKNOWN_MSG_ID;
      return sample.longValue();
    }
  }

  private record KickDetails(String nick, String reason) {}

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
    session.lagProbeToken.set(null);
    session.lagProbeSentAtMs.set(0L);
    session.lagLastMeasuredMs.set(-1L);
    session.lagLastMeasuredAtMs.set(0L);
    session.bufferInfosById.clear();
    session.historyByTarget.clear();
    session.targetNetworkHintsByTargetLower.clear();
    session.joinedChannelMembershipKeys.clear();
    session.networkDisplayByNetworkId.clear();
    session.networkTokenByNetworkId.clear();
    session.networkIdByTokenLower.clear();
    session.networkStateByNetworkId.clear();
    session.identityStateByIdentityId.clear();
    session.identityNameByIdentityId.clear();
    session.knownIdentityIds.clear();
    session.networkCurrentNickByNetworkId.clear();
    session.enabledCapabilitiesByNetworkId.clear();
    session.monitorSupportByNetworkId.clear();
    session.multilineLimitsByNetworkId.clear();
    session.pendingCreatedNetworkNames.clear();
    session.capabilitySnapshotObserved.set(false);
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

  private static <K, V> void trimMapToMaxSize(Map<K, V> map, int maxSize) {
    if (map == null || maxSize <= 0) return;
    int size = map.size();
    if (size <= maxSize) return;
    int toRemove = size - maxSize;
    for (K key : map.keySet()) {
      if (toRemove <= 0) break;
      if (map.remove(key) != null) {
        toRemove--;
      }
    }
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

    OutputStream out = socket.getOutputStream();
    log.debug(
        "Sending Quassel network sync call: serverId={}, className={}, objectName={}, slotName={}, paramCount=0",
        session.serverId,
        NETWORK_CLASS,
        Integer.toString(networkId),
        slot);
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxySync(
          out, NETWORK_CLASS, Integer.toString(networkId), slot, List.of());
    }
  }

  private boolean maybeRepairNetworkIdentityBeforeConnect(QuasselSession session, int networkId)
      throws Exception {
    if (session == null || networkId < 0) return true;
    Map<String, Object> existing = refreshNetworkStateForConnectPreflight(session, networkId);
    int configuredIdentityId = parseNetworkIdentityId(existing);
    Map<String, Object> identityState =
        configuredIdentityId > 0
            ? session.identityStateByIdentityId.getOrDefault(configuredIdentityId, Map.of())
            : Map.of();
    boolean identityStateUsable = identityStateLooksUsable(identityState);
    boolean identityKnown =
        configuredIdentityId > 0 && session.knownIdentityIds.contains(configuredIdentityId);
    NetworkServerEndpoint endpoint = parsePrimaryNetworkServer(existing);
    String networkName =
        firstNonBlank(
            mapValueIgnoreCase(existing, "networkName"),
            mapValueIgnoreCase(existing, "networkname"),
            mapValueIgnoreCase(existing, "name"),
            session.networkDisplayByNetworkId.get(networkId),
            "network-" + networkId);
    boolean enabled = parseNetworkEnabled(existing);
    log.debug(
        "Quassel connect preflight state: serverId={}, networkId={}, configuredIdentityId={}, identityKnown={}, identityStateUsable={}, knownIdentityIds={}, identityNames={}, identityStateKeys={}, networkName={}, endpointHost={}, endpointPort={}, endpointTls={}, enabled={}, knownNetworkIds={}, networkStateKeys={}, rawState={}",
        session.serverId,
        networkId,
        configuredIdentityId,
        identityKnown,
        identityStateUsable,
        session.knownIdentityIds,
        session.identityNameByIdentityId,
        session.identityStateByIdentityId.keySet(),
        networkName,
        endpoint.host(),
        endpoint.port(),
        endpoint.useTls(),
        enabled,
        collectKnownNetworkIds(session),
        session.networkStateByNetworkId.keySet(),
        summarizeNetworkInfoForLog(existing));
    if (identityKnown && identityStateUsable) {
      log.debug(
          "Skipping network identity repair before connect because identity is already known and state looks usable: serverId={}, networkId={}, identityId={}",
          session.serverId,
          networkId,
          configuredIdentityId);
      return true;
    }

    int replacementIdentityId = resolveQuasselIdentityId(session, null);
    if (replacementIdentityId < 0) {
      log.debug(
          "Skipping network identity repair before connect because no replacement identity id is available: serverId={}, networkId={}",
          session.serverId,
          networkId);
      return true;
    }

    log.debug(
        "Quassel connect preflight found invalid/unknown identity or unusable identity state: serverId={}, networkId={}, configuredIdentityId={}, identityKnown={}, identityStateUsable={}, knownIdentityIds={}, replacementIdentityId={}, networkName={}, endpointHost={}, endpointPort={}, endpointTls={}, rawState={}",
        session.serverId,
        networkId,
        configuredIdentityId,
        identityKnown,
        identityStateUsable,
        session.knownIdentityIds,
        replacementIdentityId,
        networkName,
        endpoint.host(),
        endpoint.port(),
        endpoint.useTls(),
        summarizeNetworkInfoForLog(existing));
    if (endpoint.host().isBlank()) {
      log.debug(
          "Skipping network identity repair before connect due to missing host in network state: serverId={}, networkId={}",
          session.serverId,
          networkId);
      return true;
    }

    QuasselCoreNetworkUpdateRequest repairRequest =
        new QuasselCoreNetworkUpdateRequest(
            networkName,
            endpoint.host(),
            endpoint.port(),
            endpoint.useTls(),
            "",
            true,
            replacementIdentityId,
            enabled);
    sendUpdateNetworkRequest(session, networkId, normalizeQuasselCoreUpdateRequest(repairRequest));
    log.debug(
        "Submitted pre-connect network identity repair: serverId={}, networkId={}, identityId={}, host={}, port={}, tls={}, enabled={}",
        session.serverId,
        networkId,
        replacementIdentityId,
        endpoint.host(),
        endpoint.port(),
        endpoint.useTls(),
        enabled);
    // Network updates are async on core side; request a fresh snapshot before connect.
    requestNetworkInitState(session, networkId);
    Map<String, Object> repaired =
        awaitNetworkStateSnapshotForIdentity(session, networkId, replacementIdentityId, 2_500L);
    int confirmedIdentityId = parseNetworkIdentityId(repaired);
    if (confirmedIdentityId != replacementIdentityId) {
      log.debug(
          "Quassel connect preflight identity repair not yet confirmed by core: serverId={}, networkId={}, expectedIdentityId={}, confirmedIdentityId={}, state={}",
          session.serverId,
          networkId,
          replacementIdentityId,
          confirmedIdentityId,
          summarizeNetworkInfoForLog(repaired));
      QuasselCoreNetworkCreateRequest fallbackRequest =
          new QuasselCoreNetworkCreateRequest(
              networkName,
              endpoint.host(),
              endpoint.port(),
              endpoint.useTls(),
              "",
              true,
              replacementIdentityId,
              List.of());
      log.debug(
          "Attempting createNetwork fallback to force identity repair: serverId={}, networkId={}, networkName={}, identityId={}",
          session.serverId,
          networkId,
          networkName,
          replacementIdentityId);
      sendCreateNetworkRequest(
          session, replacementIdentityId, fallbackRequest, RPC_CREATE_NETWORK_SLOT, true);
      requestNetworkInitState(session, networkId);
      Map<String, Object> fallbackSnapshot =
          awaitNetworkStateSnapshotForIdentity(session, networkId, replacementIdentityId, 2_000L);
      int fallbackConfirmedIdentityId = parseNetworkIdentityId(fallbackSnapshot);
      if (fallbackConfirmedIdentityId != replacementIdentityId) {
        log.debug(
            "Quassel createNetwork fallback did not confirm identity repair: serverId={}, networkId={}, expectedIdentityId={}, confirmedIdentityId={}, state={}",
            session.serverId,
            networkId,
            replacementIdentityId,
            fallbackConfirmedIdentityId,
            summarizeNetworkInfoForLog(fallbackSnapshot));
        return false;
      }
      log.debug(
          "Quassel createNetwork fallback confirmed identity repair: serverId={}, networkId={}, confirmedIdentityId={}",
          session.serverId,
          networkId,
          fallbackConfirmedIdentityId);
      return true;
    }
    log.debug(
        "Post-repair network state snapshot: serverId={}, networkId={}, configuredIdentityId={}, state={}",
        session.serverId,
        networkId,
        confirmedIdentityId,
        summarizeNetworkInfoForLog(repaired));
    return true;
  }

  private Map<String, Object> refreshNetworkStateForConnectPreflight(
      QuasselSession session, int networkId) throws Exception {
    if (session == null || networkId < 0) return Map.of();
    Map<String, Object> existing = session.networkStateByNetworkId.get(networkId);
    if (networkStateLooksUsableForConnect(existing)) {
      return existing;
    }

    log.debug(
        "Quassel connect preflight requesting init-data refresh for network state: serverId={}, networkId={}, knownNetworkIds={}, networkStateKeys={}",
        session.serverId,
        networkId,
        collectKnownNetworkIds(session),
        session.networkStateByNetworkId.keySet());
    requestNetworkInitState(session, networkId);
    Map<String, Object> refreshed = awaitNetworkStateSnapshot(session, networkId, 800L);
    log.debug(
        "Quassel connect preflight init-data refresh result: serverId={}, networkId={}, refreshedStateLooksUsable={}, refreshedState={}",
        session.serverId,
        networkId,
        networkStateLooksUsableForConnect(refreshed),
        summarizeNetworkInfoForLog(refreshed));
    return refreshed;
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
    if (clazz.isEmpty() || object.isEmpty()) return;
    Socket socket = session.socketRef.get();
    if (socket == null) return;
    OutputStream out = socket.getOutputStream();
    log.debug(
        "Sending Quassel init request: serverId={}, className={}, objectName={}",
        session.serverId,
        clazz,
        object);
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxyInitRequest(out, clazz, object, List.of());
    }
  }

  private Map<String, Object> awaitNetworkStateSnapshot(
      QuasselSession session, int networkId, long timeoutMs) {
    if (session == null || networkId < 0) return Map.of();
    Map<String, Object> current = session.networkStateByNetworkId.get(networkId);
    if (networkStateLooksUsableForConnect(current)) {
      return current;
    }
    if (timeoutMs <= 0L) {
      return current == null ? Map.of() : current;
    }
    awaitQuasselNetworkCondition(
        session,
        timeoutMs,
        () -> {
          Map<String, Object> state = session.networkStateByNetworkId.get(networkId);
          return networkStateLooksUsableForConnect(state);
        });
    current = session.networkStateByNetworkId.get(networkId);
    return current == null ? Map.of() : current;
  }

  private Map<String, Object> awaitNetworkStateSnapshotForIdentity(
      QuasselSession session, int networkId, int expectedIdentityId, long timeoutMs) {
    if (session == null || networkId < 0) return Map.of();
    if (expectedIdentityId <= 0) {
      return awaitNetworkStateSnapshot(session, networkId, timeoutMs);
    }
    Map<String, Object> current = session.networkStateByNetworkId.get(networkId);
    if (parseNetworkIdentityId(current) == expectedIdentityId) {
      return current;
    }
    if (timeoutMs <= 0L) {
      return current == null ? Map.of() : current;
    }
    awaitQuasselNetworkCondition(
        session,
        timeoutMs,
        () -> {
          Map<String, Object> state = session.networkStateByNetworkId.get(networkId);
          return parseNetworkIdentityId(state) == expectedIdentityId;
        });
    current = session.networkStateByNetworkId.get(networkId);
    return current == null ? Map.of() : current;
  }

  private void awaitQuasselNetworkCondition(
      QuasselSession session, long timeoutMs, BooleanSupplier condition) {
    if (session == null || condition == null || timeoutMs <= 0L) return;
    if (condition.getAsBoolean()) return;
    String sid = normalizeServerId(session.serverId);
    if (sid.isEmpty()) return;
    try {
      quasselNetworkEvents
          .filter(event -> sid.equals(normalizeServerId(event.serverId())))
          .filter(event -> condition.getAsBoolean())
          .firstElement()
          .timeout(timeoutMs, TimeUnit.MILLISECONDS)
          .ignoreElement()
          .onErrorComplete()
          .blockingAwait();
    } catch (Exception ignored) {
    }
  }

  private void awaitQuasselIdentityCondition(
      QuasselSession session, long timeoutMs, BooleanSupplier condition) {
    if (session == null || condition == null || timeoutMs <= 0L) return;
    if (condition.getAsBoolean()) return;
    String sid = normalizeServerId(session.serverId);
    if (sid.isEmpty()) return;
    try {
      quasselIdentityEvents
          .filter(event -> sid.equals(normalizeServerId(event.serverId())))
          .filter(event -> condition.getAsBoolean())
          .firstElement()
          .timeout(timeoutMs, TimeUnit.MILLISECONDS)
          .ignoreElement()
          .onErrorComplete()
          .blockingAwait();
    } catch (Exception ignored) {
    }
  }

  private static boolean networkStateLooksUsableForConnect(Map<?, ?> state) {
    if (state == null || state.isEmpty()) return false;
    NetworkServerEndpoint endpoint = parsePrimaryNetworkServer(state);
    if (!endpoint.host().isBlank()) return true;
    if (parseNetworkIdentityId(state) > 0) return true;
    String networkName =
        firstNonBlank(
            mapValueIgnoreCase(state, "networkName"),
            mapValueIgnoreCase(state, "networkname"),
            mapValueIgnoreCase(state, "name"));
    return !networkName.isBlank();
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

  private static boolean identityStateLooksUsable(Map<?, ?> identityState) {
    if (identityState == null || identityState.isEmpty()) return false;
    int identityId = identityIdFromStateMap(identityState, -1);
    if (identityId <= 0) return false;
    String identityName =
        firstNonBlank(
            mapValueIgnoreCase(identityState, "identityName"),
            mapValueIgnoreCase(identityState, "identityname"),
            mapValueIgnoreCase(identityState, "name"));
    if (identityName.isBlank()) return false;
    Object rawNicks = firstMapValueByKeyIgnoreCase(identityState, "nicks", "Nicks", "nick", "Nick");
    if (rawNicks instanceof List<?> nicks) {
      for (Object nick : nicks) {
        if (!Objects.toString(nick, "").trim().isBlank()) return true;
      }
      return false;
    }
    String singleNick =
        firstNonBlank(
            mapValueIgnoreCase(identityState, "nick"), mapValueIgnoreCase(identityState, "Nick"));
    return !singleNick.isBlank();
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

    OutputStream out = socket.getOutputStream();
    synchronized (session.writeLock) {
      // Quassel's NetworkId is a type alias over Int on the wire.
      datastreamCodec.writeSignalProxyRpcCall(out, slot, List.of(networkId));
    }
  }

  private void sendCreateNetworkRequest(
      QuasselSession session, int identityId, QuasselCoreNetworkCreateRequest request)
      throws Exception {
    sendCreateNetworkRequest(session, identityId, request, RPC_CREATE_NETWORK_SLOT, true);
  }

  private void maybeCreateDefaultIdentityForNetwork(
      QuasselSession session, String serverId, QuasselCoreNetworkCreateRequest request)
      throws Exception {
    if (session == null) return;
    if (hasKnownIdentity(session)) {
      log.debug(
          "Skipping create identity bootstrap because identities are already known: serverId={}, knownIdentityIds={}, identityNames={}",
          serverId,
          session.knownIdentityIds,
          session.identityNameByIdentityId);
      return;
    }
    int objectIdentityId = firstKnownIdentityId(session);
    if (objectIdentityId >= 0) {
      log.debug(
          "Skipping create identity bootstrap because firstKnownIdentityId returned {}: serverId={}",
          objectIdentityId,
          serverId);
      return;
    }

    Map<String, Object> identityPayload = buildDefaultIdentityPayload(session, request);
    log.debug(
        "No known Quassel identity observed before network create. Sending create identity RPC: serverId={}, payload={}",
        serverId,
        summarizeNetworkInfoForLog(identityPayload));
    sendCreateIdentityRequest(session, identityPayload);
    int observedIdentity = awaitObservedIdentityId(session, 1_500L);
    if (observedIdentity >= 0) {
      log.debug(
          "Observed identity id {} after create identity RPC on serverId={}.",
          observedIdentity,
          serverId);
      return;
    }
    log.debug(
        "No identity observed after create identity RPC on serverId={}. Continuing with fallback identity resolution.",
        serverId);
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
            ? buildDefaultIdentityPayload(session, null)
            : identityPayload;
    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("Identity", payload),
            Collections.emptyMap());
    OutputStream out = socket.getOutputStream();
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxyRpcCall(out, RPC_CREATE_IDENTITY_SLOT, params);
    }
  }

  private int awaitObservedIdentityId(QuasselSession session, long timeoutMs) {
    if (session == null || timeoutMs <= 0L) return -1;
    int current = firstKnownIdentityId(session);
    if (current >= 0) return current;
    awaitQuasselIdentityCondition(session, timeoutMs, () -> firstKnownIdentityId(session) >= 0);
    return firstKnownIdentityId(session);
  }

  private static Map<String, Object> buildDefaultIdentityPayload(
      QuasselSession session, QuasselCoreNetworkCreateRequest request) {
    String baseNick =
        sanitizeIdentityNick(
            firstNonBlank(
                session == null ? "" : session.initialNick,
                request == null ? "" : request.networkName(),
                "ircafe"));
    String identityName = firstNonBlank(baseNick, "IRCafe");
    String realName = firstNonBlank(session == null ? "" : session.initialNick, baseNick);
    String ident = sanitizeIdentityNick(baseNick.toLowerCase(Locale.ROOT));

    LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
    identity.put("identityId", -1);
    identity.put("identityName", identityName);
    identity.put("nicks", List.of(baseNick));
    identity.put("realName", realName);
    identity.put("awayNick", baseNick + "_away");
    identity.put("awayNickEnabled", false);
    identity.put("awayReason", "");
    identity.put("awayReasonEnabled", false);
    identity.put("autoAwayEnabled", false);
    identity.put("autoAwayTime", 10);
    identity.put("autoAwayReason", "");
    identity.put("autoAwayReasonEnabled", false);
    identity.put("detachAwayEnabled", false);
    identity.put("detachAwayReason", "");
    identity.put("detachAwayReasonEnabled", false);
    identity.put("ident", ident);
    identity.put("kickReason", "");
    identity.put("partReason", "");
    identity.put("quitReason", "");
    return Collections.unmodifiableMap(identity);
  }

  private static String sanitizeIdentityNick(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return "ircafe";
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isWhitespace(ch) || ch == '\r' || ch == '\n') {
        out.append('_');
      } else {
        out.append(ch);
      }
    }
    String sanitized = out.toString().trim();
    if (sanitized.isEmpty()) return "ircafe";
    return sanitized;
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
        buildQuasselNetworkInfoPayload(
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
        session.knownIdentityIds,
        auth == null ? -1 : auth.primaryNetworkId(),
        auth == null ? List.of() : auth.networkIds(),
        auth == null || auth.initialBuffers() == null ? 0 : auth.initialBuffers().size(),
        session.networkStateByNetworkId.keySet(),
        session.networkDisplayByNetworkId.keySet(),
        session.networkTokenByNetworkId.keySet(),
        session.identityStateByIdentityId.keySet(),
        session.identityNameByIdentityId,
        summarizeNetworkInfoForLog(networkInfo));

    OutputStream out = socket.getOutputStream();
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxyRpcCall(out, slot, params);
    }
    if (includeAutoJoinChannels) {
      rememberPendingCreatedNetworkName(session, request.networkName());
    }
  }

  private void maybeRetryLegacyCreateNetworkSlot(
      QuasselSession session,
      int identityId,
      QuasselCoreNetworkCreateRequest request,
      Set<Integer> baselineNetworkIds)
      throws Exception {
    if (session == null || request == null) return;
    List<String> autoJoin = request.autoJoinChannels();
    if (autoJoin != null && !autoJoin.isEmpty()) {
      return;
    }

    if (awaitObservedNetworkAfterCreate(
        session, request.networkName(), baselineNetworkIds, 1_000L)) {
      return;
    }

    log.debug(
        "No network observed after create RPC {} for serverId={}, networkName={}. Retrying once with legacy slot {}.",
        RPC_CREATE_NETWORK_SLOT,
        session.serverId,
        request.networkName(),
        RPC_CREATE_NETWORK_SLOT_LEGACY);
    sendCreateNetworkRequest(session, identityId, request, RPC_CREATE_NETWORK_SLOT_LEGACY, false);
    if (awaitObservedNetworkAfterCreate(
        session, request.networkName(), baselineNetworkIds, 1_000L)) {
      log.debug(
          "Network '{}' observed after legacy create retry on serverId={}.",
          request.networkName(),
          session.serverId);
      return;
    }
    log.debug(
        "Network '{}' still not observed after legacy create retry on serverId={}.",
        request.networkName(),
        session.serverId);
  }

  private boolean awaitObservedNetworkAfterCreate(
      QuasselSession session,
      String expectedNetworkName,
      Set<Integer> baselineNetworkIds,
      long timeoutMs) {
    if (session == null) return false;
    String wanted = Objects.toString(expectedNetworkName, "").trim();
    Set<Integer> baseline = baselineNetworkIds == null ? Set.of() : Set.copyOf(baselineNetworkIds);
    if (timeoutMs <= 0L) return false;

    if ((!wanted.isEmpty() && isObservedNetworkName(session, wanted))
        || hasObservedNewNetworkId(session, baseline)) {
      return true;
    }
    awaitQuasselNetworkCondition(
        session,
        timeoutMs,
        () ->
            (!wanted.isEmpty() && isObservedNetworkName(session, wanted))
                || hasObservedNewNetworkId(session, baseline));
    return (!wanted.isEmpty() && isObservedNetworkName(session, wanted))
        || hasObservedNewNetworkId(session, baseline);
  }

  private static boolean hasObservedNewNetworkId(QuasselSession session, Set<Integer> baselineIds) {
    if (session == null) return false;
    Set<Integer> baseline = baselineIds == null ? Set.of() : baselineIds;
    for (Integer id : collectKnownNetworkIds(session)) {
      if (id == null || id.intValue() < 0) continue;
      if (!baseline.contains(id)) return true;
    }
    return false;
  }

  private static boolean isObservedNetworkName(QuasselSession session, String expectedNetworkName) {
    if (session == null) return false;
    String wanted = Objects.toString(expectedNetworkName, "").trim();
    if (wanted.isEmpty()) return false;

    for (String display : session.networkDisplayByNetworkId.values()) {
      if (Objects.toString(display, "").trim().equalsIgnoreCase(wanted)) {
        return true;
      }
    }

    for (Map<String, Object> state : session.networkStateByNetworkId.values()) {
      String name =
          firstNonBlank(
              mapValueIgnoreCase(state, "networkName"),
              mapValueIgnoreCase(state, "networkname"),
              mapValueIgnoreCase(state, "name"));
      if (name.equalsIgnoreCase(wanted)) {
        return true;
      }
    }
    return false;
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

    Map<String, Object> existing = session.networkStateByNetworkId.get(networkId);
    String networkName =
        firstNonBlank(
            request.networkName(),
            mapValueIgnoreCase(existing, "networkName"),
            mapValueIgnoreCase(existing, "networkname"),
            mapValueIgnoreCase(existing, "name"),
            session.networkDisplayByNetworkId.get(networkId));
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
        buildQuasselNetworkInfoUpdatePayload(networkId, identityId, networkName, request, enabled);
    List<Object> params =
        List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkInfo", networkInfo));

    OutputStream out = socket.getOutputStream();
    synchronized (session.writeLock) {
      log.debug(
          "Sending Quassel network sync call: serverId={}, className={}, objectName={}, slotName={}, paramCount=1, payload=NetworkInfo(identityId={}, summary={})",
          session.serverId,
          NETWORK_CLASS,
          Integer.toString(networkId),
          NETWORK_SET_INFO_SLOT,
          identityId,
          summarizeNetworkInfoForLog(networkInfo));
      datastreamCodec.writeSignalProxySync(
          out, NETWORK_CLASS, Integer.toString(networkId), NETWORK_SET_INFO_SLOT, params);
    }

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
    OutputStream out = socket.getOutputStream();
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxyRpcCall(
          out,
          RPC_REMOVE_NETWORK_SLOT,
          List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", networkId)));
    }
  }

  private static QuasselCoreNetworkCreateRequest normalizeQuasselCoreCreateRequest(
      QuasselCoreNetworkCreateRequest request) {
    String networkName = Objects.toString(request.networkName(), "").trim();
    if (networkName.isEmpty()) {
      throw new IllegalArgumentException("network name is required");
    }
    if (containsCrlf(networkName)) {
      throw new IllegalArgumentException("network name contains unsupported newlines");
    }

    String serverHost = Objects.toString(request.serverHost(), "").trim();
    if (serverHost.isEmpty()) {
      throw new IllegalArgumentException("server host is required");
    }
    if (containsCrlf(serverHost)) {
      throw new IllegalArgumentException("server host contains unsupported newlines");
    }

    boolean useTls = request.useTls();
    int port = request.serverPort();
    if (port <= 0) {
      port = useTls ? 6697 : 6667;
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("server port must be 1-65535");
    }

    String serverPassword = Objects.toString(request.serverPassword(), "");
    if (containsCrlf(serverPassword)) {
      throw new IllegalArgumentException("server password contains unsupported newlines");
    }

    Integer identityId = request.identityId();
    if (identityId != null && identityId.intValue() <= 0) {
      throw new IllegalArgumentException("identity id must be > 0");
    }

    List<String> autoJoin = new ArrayList<>();
    if (request.autoJoinChannels() != null) {
      for (String entry : request.autoJoinChannels()) {
        String token = Objects.toString(entry, "").trim();
        if (token.isEmpty()) continue;
        if (containsCrlf(token)) {
          throw new IllegalArgumentException("auto-join channel contains unsupported newlines");
        }
        autoJoin.add(token);
      }
    }

    return new QuasselCoreNetworkCreateRequest(
        networkName,
        serverHost,
        port,
        useTls,
        serverPassword,
        request.verifyTls(),
        identityId,
        autoJoin.isEmpty() ? List.of() : List.copyOf(autoJoin));
  }

  private static QuasselCoreNetworkUpdateRequest normalizeQuasselCoreUpdateRequest(
      QuasselCoreNetworkUpdateRequest request) {
    String networkName = Objects.toString(request.networkName(), "").trim();
    if (containsCrlf(networkName)) {
      throw new IllegalArgumentException("network name contains unsupported newlines");
    }

    String serverHost = Objects.toString(request.serverHost(), "").trim();
    if (serverHost.isEmpty()) {
      throw new IllegalArgumentException("server host is required");
    }
    if (containsCrlf(serverHost)) {
      throw new IllegalArgumentException("server host contains unsupported newlines");
    }

    boolean useTls = request.useTls();
    int port = request.serverPort();
    if (port <= 0) {
      port = useTls ? 6697 : 6667;
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("server port must be 1-65535");
    }

    String serverPassword = Objects.toString(request.serverPassword(), "");
    if (containsCrlf(serverPassword)) {
      throw new IllegalArgumentException("server password contains unsupported newlines");
    }

    Integer identityId = request.identityId();
    if (identityId != null && identityId.intValue() <= 0) {
      throw new IllegalArgumentException("identity id must be > 0");
    }

    return new QuasselCoreNetworkUpdateRequest(
        networkName,
        serverHost,
        port,
        useTls,
        serverPassword,
        request.verifyTls(),
        identityId,
        request.enabled());
  }

  private static Map<String, Object> buildQuasselNetworkInfoPayload(
      int networkId,
      int identityId,
      QuasselCoreNetworkCreateRequest request,
      boolean enabled,
      boolean includeLegacyAliases) {
    byte[] codecForServer = DEFAULT_NETWORK_CODEC.getBytes(StandardCharsets.UTF_8);
    byte[] codecForEncoding = DEFAULT_NETWORK_CODEC.getBytes(StandardCharsets.UTF_8);
    byte[] codecForDecoding = DEFAULT_NETWORK_CODEC.getBytes(StandardCharsets.UTF_8);
    LinkedHashMap<String, Object> info = new LinkedHashMap<>();
    info.put("NetworkId", new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", networkId));
    info.put("NetworkName", request.networkName());
    // Upstream NetworkInfo deserialization reads "Identity" as IdentityId user-type.
    info.put("Identity", new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", identityId));
    info.put("IdentityId", identityId);
    info.put("identity", identityId);
    info.put("CodecForServer", codecForServer);
    info.put("CodecForEncoding", codecForEncoding);
    info.put("CodecForDecoding", codecForDecoding);
    info.put(
        "ServerList",
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue(
                "Network::Server", buildQuasselServerPayload(request))));
    info.put("Perform", List.of());
    info.put("SkipCaps", List.of());
    info.put("AutoIdentifyService", "NickServ");
    info.put("AutoIdentifyPassword", "");
    info.put("SaslAccount", "");
    info.put("SaslPassword", "");
    info.put("SaslMechanism", "");
    info.put("MessageRateBurstSize", 5);
    info.put("MessageRateDelay", 2200);
    info.put("AutoReconnectInterval", 60);
    info.put("AutoReconnectRetries", 20);
    info.put("UseRandomServer", false);
    info.put("UseAutoIdentify", false);
    info.put("UseSasl", false);
    info.put("UseAutoReconnect", true);
    info.put("UnlimitedReconnectRetries", true);
    info.put("UseCustomMessageRate", false);
    info.put("UnlimitedMessageRate", false);
    info.put("RejoinChannels", true);
    info.put("AutoAwayActive", false);
    if (includeLegacyAliases) {
      // Preserve aliases for create flows where older cores expect lower-cased keys.
      info.put("networkId", networkId);
      info.put("networkName", request.networkName());
      info.put("identityId", identityId);
      info.put("codecForServer", DEFAULT_NETWORK_CODEC);
      info.put("codecForEncoding", DEFAULT_NETWORK_CODEC);
      info.put("codecForDecoding", DEFAULT_NETWORK_CODEC);
      info.put("perform", List.of());
      info.put("skipCaps", List.of());
      info.put("autoIdentifyService", "NickServ");
      info.put("autoIdentifyPassword", "");
      info.put("saslAccount", "");
      info.put("saslPassword", "");
      info.put("saslMechanism", "");
      info.put("msgRateBurstSize", 5);
      info.put("msgRateMessageDelay", 2200);
      info.put("autoReconnectInterval", 60);
      info.put("autoReconnectRetries", 20);
      info.put("useRandomServer", false);
      info.put("useAutoIdentify", false);
      info.put("useSasl", false);
      info.put("useAutoReconnect", true);
      info.put("unlimitedReconnectRetries", true);
      info.put("useCustomMessageRate", false);
      info.put("unlimitedMessageRate", false);
      info.put("rejoinChannels", true);
      info.put("autoAwayActive", false);
      info.put("isEnabled", enabled);
      info.put("isInitialized", true);
    }
    return Collections.unmodifiableMap(info);
  }

  private static Map<String, Object> buildQuasselNetworkInfoUpdatePayload(
      int networkId,
      int identityId,
      String networkName,
      QuasselCoreNetworkUpdateRequest request,
      boolean enabled) {
    QuasselCoreNetworkCreateRequest shape =
        new QuasselCoreNetworkCreateRequest(
            networkName,
            request.serverHost(),
            request.serverPort(),
            request.useTls(),
            request.serverPassword(),
            request.verifyTls(),
            identityId,
            List.of());
    Map<String, Object> base =
        buildQuasselNetworkInfoPayload(
            networkId, identityId, shape, enabled, /* includeLegacyAliases= */ false);
    return Collections.unmodifiableMap(new LinkedHashMap<>(base));
  }

  private static Map<String, Object> buildQuasselServerPayload(
      QuasselCoreNetworkCreateRequest request) {
    LinkedHashMap<String, Object> server = new LinkedHashMap<>();
    server.put("Host", request.serverHost());
    server.put("Port", request.serverPort());
    server.put("Password", Objects.toString(request.serverPassword(), ""));
    server.put("UseSSL", request.useTls());
    server.put("SslVerify", request.verifyTls());
    server.put("SslVersion", 0);
    server.put("UseProxy", false);
    server.put("ProxyType", 0);
    server.put("ProxyHost", "localhost");
    server.put("ProxyPort", 0);
    server.put("ProxyUser", "");
    server.put("ProxyPass", "");
    server.put("sslVerify", request.verifyTls());
    server.put("sslVersion", 0);
    // Legacy aliases retained for compatibility with existing parser fallback paths.
    server.put("hostname", request.serverHost());
    server.put("server", request.serverHost());
    server.put("host", request.serverHost());
    server.put("port", request.serverPort());
    server.put("password", Objects.toString(request.serverPassword(), ""));
    server.put("useSSL", request.useTls());
    server.put("useProxy", false);
    server.put("proxyType", 0);
    server.put("proxyHost", "localhost");
    server.put("proxyPort", 0);
    server.put("proxyUser", "");
    server.put("proxyPass", "");
    return Collections.unmodifiableMap(server);
  }

  private static String summarizeNetworkInfoForLog(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) return "{}";
    return summarizeValueForLog(payload, 0);
  }

  @SuppressWarnings("unchecked")
  private static String summarizeValueForLog(Object value, int depth) {
    if (value == null) return "null";
    if (depth > 3) return "<depth-limit>";
    if (value instanceof String s) return '"' + s + '"';
    if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
    if (value instanceof byte[] bytes) return "byte[" + bytes.length + "]";
    if (value instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      return "UserType("
          + userType.typeName()
          + "="
          + summarizeValueForLog(userType.value(), depth + 1)
          + ")";
    }
    if (value instanceof List<?> list) {
      StringBuilder out = new StringBuilder();
      out.append("List[");
      int index = 0;
      for (Object item : list) {
        if (index > 0) out.append(", ");
        if (index >= 8) {
          out.append("...");
          break;
        }
        out.append(summarizeValueForLog(item, depth + 1));
        index++;
      }
      out.append(']');
      return out.toString();
    }
    if (value instanceof Map<?, ?> map) {
      StringBuilder out = new StringBuilder();
      out.append('{');
      int index = 0;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (index > 0) out.append(", ");
        if (index >= 32) {
          out.append("...");
          break;
        }
        String key = Objects.toString(entry.getKey(), "");
        out.append(key).append('=');
        if (looksSensitiveLogKey(key)) {
          out.append("<redacted>");
        } else {
          out.append(summarizeValueForLog(entry.getValue(), depth + 1));
        }
        index++;
      }
      out.append('}');
      return out.toString();
    }
    return value.getClass().getSimpleName() + "(" + value + ")";
  }

  private static boolean looksSensitiveLogKey(String key) {
    String token = Objects.toString(key, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return false;
    return token.contains("pass")
        || token.contains("secret")
        || token.contains("token")
        || token.contains("auth");
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

    OutputStream out = socket.getOutputStream();
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxyRpcCall(
          out, "2sendInput(BufferInfo,QString)", List.of(bufferInfo, userInput));
    }
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
              if (sid.isEmpty()) throw new IllegalArgumentException("server id is blank");

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

  private static OutboundRawRoute routeOutboundRawLine(String rawLine) {
    String line = Objects.toString(rawLine, "").trim();
    if (line.isEmpty()) {
      return new OutboundRawRoute("", null, "", BUFFER_STATUS);
    }

    int len = line.length();
    int cursor = 0;
    if (line.charAt(0) == '@') {
      int space = line.indexOf(' ');
      if (space <= 0 || space >= (len - 1)) {
        return new OutboundRawRoute("", null, line, BUFFER_STATUS);
      }
      cursor = space + 1;
      while (cursor < len && line.charAt(cursor) == ' ') cursor++;
    }
    if (cursor < len && line.charAt(cursor) == ':') {
      int space = line.indexOf(' ', cursor);
      if (space <= cursor || space >= (len - 1)) {
        return new OutboundRawRoute("", null, line, BUFFER_STATUS);
      }
      cursor = space + 1;
      while (cursor < len && line.charAt(cursor) == ' ') cursor++;
    }
    if (cursor >= len) {
      return new OutboundRawRoute("", null, line, BUFFER_STATUS);
    }

    int commandStart = cursor;
    while (cursor < len && line.charAt(cursor) != ' ') cursor++;
    String command = line.substring(commandStart, cursor).trim().toUpperCase(Locale.ROOT);
    if (command.isEmpty() || !TARGET_ROUTED_RAW_COMMANDS.contains(command)) {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }

    while (cursor < len && line.charAt(cursor) == ' ') cursor++;
    if (cursor >= len || line.charAt(cursor) == ':') {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }

    int targetStart = cursor;
    while (cursor < len && line.charAt(cursor) != ' ') cursor++;
    int targetEnd = cursor;
    String rawTarget = line.substring(targetStart, targetEnd).trim();
    if (rawTarget.isEmpty()) {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }
    QualifiedTarget parsedTarget = parseQualifiedTarget(rawTarget);
    if (parsedTarget.baseTarget().isEmpty()) {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }

    String rewritten = line;
    if (!parsedTarget.networkToken().isEmpty() && !parsedTarget.baseTarget().equals(rawTarget)) {
      rewritten =
          line.substring(0, targetStart) + parsedTarget.baseTarget() + line.substring(targetEnd);
    }
    int typeBitsHint = looksLikeChannel(parsedTarget.baseTarget()) ? BUFFER_CHANNEL : BUFFER_QUERY;
    return new OutboundRawRoute(command, parsedTarget, rewritten, typeBitsHint);
  }

  private void sendBufferSyncerReadMarkerUpdate(
      QuasselSession session, int bufferId, long markerMsgId) throws Exception {
    if (session == null) return;
    if (bufferId < 0 || markerMsgId <= 0L) return;

    Socket socket = session.socketRef.get();
    if (socket == null) {
      throw new IllegalStateException("Quassel socket is closed");
    }
    int msgId = clampMsgId(markerMsgId);
    if (msgId <= 0) return;

    OutputStream out = socket.getOutputStream();
    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", bufferId),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", msgId));
    synchronized (session.writeLock) {
      datastreamCodec.writeSignalProxySync(
          out, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT, BUFFER_SYNCER_MARKER_SLOT, params);
      datastreamCodec.writeSignalProxySync(
          out, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT, BUFFER_SYNCER_LAST_SEEN_SLOT, params);
    }
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
        findBufferByName(session, requestedName, fallbackTypeBits, preferredNetworkId);
    if (byName != null) {
      return byName;
    }

    int networkId = preferredNetworkId >= 0 ? preferredNetworkId : firstKnownNetworkId(session);
    return new QuasselCoreDatastreamCodec.BufferInfoValue(
        -1, networkId, fallbackTypeBits, -1, requestedName);
  }

  private static int firstKnownNetworkId(QuasselSession session) {
    LinkedHashSet<Integer> knownIds = collectKnownNetworkIds(session);
    if (knownIds.isEmpty()) return -1;
    QuasselCoreAuthHandshake.AuthResult auth = session == null ? null : session.authResult.get();
    if (auth != null
        && auth.primaryNetworkId() >= 0
        && knownIds.contains(auth.primaryNetworkId())) {
      return auth.primaryNetworkId();
    }
    for (Integer id : knownIds) {
      if (id != null && id.intValue() >= 0) return id.intValue();
    }
    return -1;
  }

  private static QuasselCoreDatastreamCodec.BufferInfoValue findBufferByName(
      QuasselSession session, String bufferName, int typeBitsHint, int preferredNetworkId) {
    if (session == null) return null;
    String requested = Objects.toString(bufferName, "").trim();
    if (requested.isEmpty()) return null;

    QuasselCoreDatastreamCodec.BufferInfoValue preferredAnyType = null;
    QuasselCoreDatastreamCodec.BufferInfoValue fallback = null;
    QuasselCoreDatastreamCodec.BufferInfoValue fallbackAnyType = null;
    for (QuasselCoreDatastreamCodec.BufferInfoValue candidate : session.bufferInfosById.values()) {
      if (candidate == null) continue;
      if (!requested.equalsIgnoreCase(Objects.toString(candidate.bufferName(), "").trim()))
        continue;
      boolean typeMatch = (candidate.typeBits() & typeBitsHint) != 0;
      boolean preferredNetwork =
          preferredNetworkId >= 0 && candidate.networkId() == preferredNetworkId;
      if (preferredNetwork && typeMatch) {
        return candidate;
      }
      if (preferredNetwork && preferredAnyType == null) {
        preferredAnyType = candidate;
      }
      if (typeMatch && fallback == null) {
        fallback = candidate;
      }
      if (fallbackAnyType == null) {
        fallbackAnyType = candidate;
      }
    }
    if (preferredAnyType != null) return preferredAnyType;
    if (fallback != null) return fallback;
    return fallbackAnyType;
  }

  private static QuasselCoreSetupPrompt buildSetupPrompt(
      String serverId, String detail, Map<String, Object> setupFields) {
    Map<String, Object> rawFields = normalizeObjectMap(setupFields);
    List<String> storageBackends =
        extractSetupOptions(rawFields, "StorageBackends", "BackendInfo", "Backends");
    if (storageBackends.isEmpty()) {
      storageBackends = DEFAULT_SETUP_STORAGE_BACKENDS;
    }
    List<String> authenticators =
        extractSetupOptions(rawFields, "Authenticators", "AuthenticatorInfo");
    if (authenticators.isEmpty()) {
      authenticators = DEFAULT_SETUP_AUTHENTICATORS;
    }
    return new QuasselCoreSetupPrompt(
        serverId, Objects.toString(detail, "").trim(), storageBackends, authenticators, rawFields);
  }

  private static QuasselCoreSetupRequest normalizeSetupRequest(
      QuasselCoreSetupPrompt prompt, QuasselCoreSetupRequest request) {
    String adminUser = Objects.toString(request.adminUser(), "").trim();
    if (adminUser.isEmpty()) {
      throw new IllegalArgumentException("admin user is required");
    }
    String adminPassword = Objects.toString(request.adminPassword(), "");
    if (adminPassword.isBlank()) {
      throw new IllegalArgumentException("admin password is required");
    }

    List<String> storageOptions =
        prompt == null || prompt.storageBackends() == null ? List.of() : prompt.storageBackends();
    String storageBackend =
        selectSetupOption(
            request.storageBackend(),
            storageOptions,
            DEFAULT_SETUP_STORAGE_BACKENDS.isEmpty() ? "" : DEFAULT_SETUP_STORAGE_BACKENDS.get(0));

    List<String> authOptions =
        prompt == null || prompt.authenticators() == null ? List.of() : prompt.authenticators();
    String authenticator =
        selectSetupOption(
            request.authenticator(),
            authOptions,
            DEFAULT_SETUP_AUTHENTICATORS.isEmpty() ? "" : DEFAULT_SETUP_AUTHENTICATORS.get(0));

    Map<String, Object> storageSetupData = normalizeObjectMap(request.storageSetupData());
    Map<String, Object> authSetupData = normalizeObjectMap(request.authSetupData());

    return new QuasselCoreSetupRequest(
        adminUser, adminPassword, storageBackend, authenticator, storageSetupData, authSetupData);
  }

  private static String selectSetupOption(
      String requested, List<String> preferredOptions, String fallback) {
    String explicit = Objects.toString(requested, "").trim();
    if (!explicit.isEmpty()) return explicit;
    if (preferredOptions != null) {
      for (String candidate : preferredOptions) {
        String c = Objects.toString(candidate, "").trim();
        if (!c.isEmpty()) return c;
      }
    }
    String dflt = Objects.toString(fallback, "").trim();
    if (!dflt.isEmpty()) return dflt;
    throw new IllegalArgumentException("setup option is required");
  }

  private static List<String> extractSetupOptions(
      Map<String, Object> setupFields, String... candidateKeys) {
    if (setupFields == null || setupFields.isEmpty() || candidateKeys == null) return List.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String key : candidateKeys) {
      Object raw = mapValueIgnoreCase(setupFields, key);
      collectSetupOptions(raw, out);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private static void collectSetupOptions(Object raw, LinkedHashSet<String> out) {
    if (raw == null || out == null) return;
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectSetupOptions(value, out);
      }
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      // BackendInfo/AuthenticatorInfo entries are often nested maps with identifier/name keys.
      String token =
          firstNonBlankMapValue(
              map,
              "Backend",
              "BackendId",
              "StorageBackend",
              "StorageBackends",
              "Storage",
              "StorageId",
              "Authenticator",
              "AuthenticatorId",
              "AuthBackend",
              "AuthBackendId",
              "Identifier",
              "Id",
              "Key",
              "Name",
              "DisplayName",
              "Value");
      if (!token.isEmpty()) {
        out.add(token);
      }
      collectSetupOptions(mapValueIgnoreCase(map, "Backends"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "StorageBackends"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "Authenticators"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "BackendInfo"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "AuthenticatorInfo"), out);
      return;
    }
    String token = Objects.toString(raw, "").trim();
    if (!token.isEmpty()) out.add(token);
  }

  private static String firstNonBlankMapValue(Map<?, ?> map, String... keys) {
    if (map == null || keys == null) return "";
    for (String key : keys) {
      String value = Objects.toString(mapValueIgnoreCase(map, key), "").trim();
      if (!value.isEmpty()) return value;
    }
    return "";
  }

  private static Object mapValueIgnoreCase(Map<?, ?> map, String wantedKey) {
    if (map == null || wantedKey == null || wantedKey.isBlank()) return null;
    String wanted = wantedKey.trim();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry == null) continue;
      String key = Objects.toString(entry.getKey(), "").trim();
      if (key.equalsIgnoreCase(wanted)) return entry.getValue();
    }
    return null;
  }

  private static Map<String, Object> normalizeObjectMap(Map<?, ?> map) {
    if (map == null || map.isEmpty()) return Map.of();
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry == null) continue;
      String key = Objects.toString(entry.getKey(), "").trim();
      if (key.isEmpty()) continue;
      out.put(key, entry.getValue());
    }
    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  private static boolean containsCrlf(String value) {
    String v = Objects.toString(value, "");
    return v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
  }

  private static String normalizeTypingState(String state) {
    String normalized = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) return "";
    return switch (normalized) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }

  private record PendingCreatedNetworkName(String networkName, long observedAtMs) {}

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
    private final Map<Integer, String> networkDisplayByNetworkId = new ConcurrentHashMap<>();
    private final Map<Integer, String> networkTokenByNetworkId = new ConcurrentHashMap<>();
    private final Map<String, Integer> networkIdByTokenLower = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Object>> networkStateByNetworkId =
        new ConcurrentHashMap<>();
    private final Set<Integer> removedNetworkIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Map<String, Object>> identityStateByIdentityId =
        new ConcurrentHashMap<>();
    private final Map<Integer, String> identityNameByIdentityId = new ConcurrentHashMap<>();
    private final Set<Integer> knownIdentityIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Set<String>> enabledCapabilitiesByNetworkId =
        new ConcurrentHashMap<>();
    private final Map<Integer, MonitorSupportState> monitorSupportByNetworkId =
        new ConcurrentHashMap<>();
    private final Map<Integer, MultilineLimitState> multilineLimitsByNetworkId =
        new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<PendingCreatedNetworkName> pendingCreatedNetworkNames =
        new ConcurrentLinkedDeque<>();
    private final Map<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> bufferInfosById =
        new ConcurrentHashMap<>();
    private final Map<String, TargetHistoryState> historyByTarget = new ConcurrentHashMap<>();
    private final Map<String, Integer> targetNetworkHintsByTargetLower = new ConcurrentHashMap<>();
    private final Set<String> joinedChannelMembershipKeys = ConcurrentHashMap.newKeySet();
    private final AtomicReference<QuasselCoreDatastreamCodec.QtDateTimeValue> lagProbeToken =
        new AtomicReference<>();
    private final AtomicLong lagProbeSentAtMs = new AtomicLong(0L);
    private final AtomicLong lagLastMeasuredMs = new AtomicLong(-1L);
    private final AtomicLong lagLastMeasuredAtMs = new AtomicLong(0L);
    private final AtomicBoolean capabilitySnapshotObserved = new AtomicBoolean(false);
    private final AtomicBoolean syncObserved = new AtomicBoolean(false);
    private final AtomicBoolean connectionReadyEmitted = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicLong backlogBatchSeq = new AtomicLong(0L);
    private final AtomicReference<QuasselSessionPhase> phase =
        new AtomicReference<>(QuasselSessionPhase.TRANSPORT_CONNECTING);
    private final AtomicReference<String> closeReason =
        new AtomicReference<>(DEFAULT_DISCONNECT_REASON);
    private final Object writeLock = new Object();
    private final AtomicBoolean closeRequested = new AtomicBoolean(false);
    private final AtomicBoolean disconnectedEmitted = new AtomicBoolean(false);

    private QuasselSession(String serverId, String nick, String connectedHost, int connectedPort) {
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
