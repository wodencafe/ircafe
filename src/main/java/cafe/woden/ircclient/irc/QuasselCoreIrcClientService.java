package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
public class QuasselCoreIrcClientService implements IrcBackendClientService {
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
  private static final String BACKLOG_MANAGER_CLASS = "BacklogManager";
  private static final String BACKLOG_MANAGER_OBJECT = "global";
  private static final String BACKLOG_REQUEST_SLOT = "requestBacklog(BufferId,MsgId,MsgId,int,int)";
  private static final long MIN_RECONNECT_DELAY_MS = 250L;
  private static final long LAG_SAMPLE_STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(2);
  private static final int MAX_BUFFER_INFOS_PER_SESSION = 8_192;
  private static final int MAX_HISTORY_TARGETS_PER_SESSION = 4_096;
  private static final int MAX_TARGET_NETWORK_HINTS_PER_SESSION = 4_096;
  private static final int MAX_NETWORK_NICKS_PER_SESSION = 256;
  private static final int MAX_NETWORK_IDENTITIES_PER_SESSION = 512;
  private static final String NETWORK_QUALIFIER_PREFIX = "{net:";
  private static final String NETWORK_QUALIFIER_SUFFIX = "}";

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
  private final Map<String, QuasselSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> availabilityReasonByServer = new ConcurrentHashMap<>();
  private final Map<String, Disposable> reconnectTasksByServer = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> reconnectAttemptsByServer = new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

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
              if (firstKnownNetworkId(session) < 0) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.QUASSEL_CORE,
                    "send raw",
                    sid,
                    "no active Quassel network is available yet");
              }

              QuasselCoreDatastreamCodec.BufferInfoValue statusBuffer =
                  resolveOutboundBufferInfo(session, BUFFER_STATUS, parseQualifiedTarget(""));
              String userInput = "/QUOTE " + raw;
              sendInput(session, statusBuffer, userInput);
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
    return false;
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    return false;
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    return false;
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    return false;
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    return 0L;
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    return 0;
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    return false;
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    return false;
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    return false;
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    if (!isSessionEstablished(serverId)) {
      return backendAvailabilityReason(serverId);
    }
    return "typing indicators are not implemented for Quassel backend yet";
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    return false;
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    return false;
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    return false;
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    return false;
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    return 0;
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

  private boolean isSessionEstablished(String serverId) {
    QuasselSession session = sessions.get(normalizeServerId(serverId));
    return session != null
        && session.socketRef.get() != null
        && session.phase.get() == QuasselSessionPhase.SESSION_ESTABLISHED;
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
      session.networkCurrentNickByNetworkId.clear();
      observeKnownNetworks(session, auth);
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
          availabilityReasonByServer.put(sid, "Quassel Core connection closed");
          emitDisconnectedOnce(session, "Quassel Core connection closed");
          scheduleReconnectIfEligible(session, "Quassel Core connection closed");
          return;
        }
        handleSignalProxyMessage(session, message);
      }
    } catch (Exception e) {
      if (!session.closeRequested.get() && !shuttingDown.get()) {
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
  }

  private void handleDisplayStatusMessage(String serverId, String network, String text) {
    String net = Objects.toString(network, "").trim();
    String rawLine = Objects.toString(text, "").trim();
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

    if ("BufferSyncer".equals(classToken) || "BufferViewConfig".equals(classToken)) {
      applyBufferInfoSnapshot(session, values);
      return;
    }

    if ("BacklogManager".equals(classToken)
        && requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC
        && slotToken.contains("receiveBacklog")) {
      handleBacklogSync(session, values);
      return;
    }

    if ("Network".equals(classToken)) {
      maybeUpdateCurrentNickFromNetworkState(session, objectName, values);
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
      observeKnownNetwork(session, networkId, networkName);
    }
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
    String target = targetForBuffer(session, bufferInfo, fromDisplay);
    String historyTarget = historyTargetForBuffer(session, bufferInfo, fromDisplay);
    int historyNetworkId = bufferInfo == null ? -1 : bufferInfo.networkId();
    noteTargetNetworkHint(session, historyTarget, historyNetworkId, true);
    noteHistoryObservation(session, historyTarget, message.messageId(), at);
    int typeBits = message.typeBits();

    if (isBacklogMessage(message.flags()) && isHistoryTextMessage(typeBits)) {
      emitBacklogHistoryBatch(session, at, target, message, messageId);
      return;
    }

    if (isJoinMessage(typeBits)) {
      handleJoinMessage(session, at, target, fromDisplay, networkId);
      return;
    }

    if (isPartMessage(typeBits)) {
      handlePartMessage(session, at, target, fromDisplay, content, networkId);
      return;
    }

    if (isQuitMessage(typeBits)) {
      if (!target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.UserQuitChannel(at, target, fromDisplay, normalizeReason(content))));
      }
      return;
    }

    if (isNickMessage(typeBits)) {
      String newNick = parseNickChange(content, fromDisplay);
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
      String topic = parseTopic(content);
      if (!target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId, new IrcEvent.ChannelTopicUpdated(at, target, topic)));
        return;
      }
    }

    if (isModeMessage(typeBits)) {
      if (!target.isEmpty()) {
        String details = parseModeDetails(content);
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.ChannelModeChanged(at, target, fromDisplay, details)));
        return;
      }
    }

    if (isKickMessage(typeBits)) {
      if (!target.isEmpty()) {
        KickDetails kick = parseKickDetails(content);
        String kickedNick = Objects.toString(kick.nick(), "").trim();
        if (!kickedNick.isEmpty()) {
          if (isSelfNick(session, kickedNick, networkId)) {
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
      String channel = firstChannelToken(content);
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
              new IrcEvent.Notice(at, fromDisplay, target, content, messageId, Map.of())));
      return;
    }

    if (isActionMessage(typeBits)) {
      if (isChannelBuffer(bufferInfo) && !target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.ChannelAction(at, target, fromDisplay, content, messageId, Map.of())));
      } else {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.PrivateAction(at, fromDisplay, content, messageId, Map.of())));
      }
      return;
    }

    if (isPlainMessage(typeBits)) {
      if (isChannelBuffer(bufferInfo) && !target.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                session.serverId,
                new IrcEvent.ChannelMessage(
                    at, target, fromDisplay, content, messageId, Map.of())));
        return;
      }
      bus.onNext(
          new ServerIrcEvent(
              session.serverId,
              new IrcEvent.PrivateMessage(at, fromDisplay, content, messageId, Map.of())));
      return;
    }

    String statusLine = content.isBlank() ? renderUnknownMessageType(message, target) : content;
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
            session.serverId, renderServerResponse(at, statusLine, content, messageId)));
  }

  private void handleJoinMessage(
      QuasselSession session, Instant at, String channel, String fromDisplay, int networkId) {
    if (channel.isEmpty()) return;
    if (isSelfNick(session, fromDisplay, networkId)) {
      bus.onNext(new ServerIrcEvent(session.serverId, new IrcEvent.JoinedChannel(at, channel)));
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
      QuasselCoreAuthHandshake.AuthResult auth = session.authResult.get();
      int networkId = auth == null ? -1 : auth.primaryNetworkId();
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
    String base = normalizedBufferName(bufferInfo);
    if (base.isEmpty() && isQueryBuffer(bufferInfo)) {
      base = Objects.toString(fallbackFromNick, "").trim();
    }
    if (base.isEmpty()) return "";
    if (!isChannelBuffer(bufferInfo) && !isQueryBuffer(bufferInfo)) {
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
    QuasselCoreAuthHandshake.AuthResult auth = session.authResult.get();
    if (auth == null) return -1;
    if (auth.primaryNetworkId() >= 0) return auth.primaryNetworkId();
    if (auth.networkIds() == null) return -1;
    for (Integer id : auth.networkIds()) {
      if (id != null && id.intValue() >= 0) return id.intValue();
    }
    return -1;
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
    if (session == null) return 0;
    Set<Integer> ids = new HashSet<>();
    QuasselCoreAuthHandshake.AuthResult auth = session.authResult.get();
    if (auth != null && auth.networkIds() != null) {
      for (Integer id : auth.networkIds()) {
        if (id != null && id.intValue() >= 0) ids.add(id.intValue());
      }
    }
    for (QuasselCoreDatastreamCodec.BufferInfoValue info : session.bufferInfosById.values()) {
      if (info != null && info.networkId() >= 0) {
        ids.add(info.networkId());
      }
    }
    ids.addAll(session.networkDisplayByNetworkId.keySet());
    ids.addAll(session.networkTokenByNetworkId.keySet());
    return ids.size();
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

    synchronized void observe(long messageId, long timestampEpochMs) {
      if (messageId <= 0L) return;
      long ts = timestampEpochMs > 0L ? timestampEpochMs : System.currentTimeMillis();
      if (messageId < oldestMsgId) oldestMsgId = messageId;
      if (messageId > newestMsgId) newestMsgId = messageId;
      if (ts < oldestTsEpochMs) oldestTsEpochMs = ts;
      if (ts > newestTsEpochMs) newestTsEpochMs = ts;
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
    session.networkDisplayByNetworkId.clear();
    session.networkTokenByNetworkId.clear();
    session.networkIdByTokenLower.clear();
    session.networkCurrentNickByNetworkId.clear();
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
    QuasselCoreAuthHandshake.AuthResult auth = session == null ? null : session.authResult.get();
    if (auth != null && auth.primaryNetworkId() >= 0) {
      return auth.primaryNetworkId();
    }
    if (auth != null && auth.networkIds() != null) {
      for (Integer id : auth.networkIds()) {
        if (id != null && id.intValue() >= 0) return id.intValue();
      }
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

  private static boolean containsCrlf(String value) {
    String v = Objects.toString(value, "");
    return v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
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
    private final Map<Integer, String> networkDisplayByNetworkId = new ConcurrentHashMap<>();
    private final Map<Integer, String> networkTokenByNetworkId = new ConcurrentHashMap<>();
    private final Map<String, Integer> networkIdByTokenLower = new ConcurrentHashMap<>();
    private final Map<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> bufferInfosById =
        new ConcurrentHashMap<>();
    private final Map<String, TargetHistoryState> historyByTarget = new ConcurrentHashMap<>();
    private final Map<String, Integer> targetNetworkHintsByTargetLower = new ConcurrentHashMap<>();
    private final AtomicReference<QuasselCoreDatastreamCodec.QtDateTimeValue> lagProbeToken =
        new AtomicReference<>();
    private final AtomicLong lagProbeSentAtMs = new AtomicLong(0L);
    private final AtomicLong lagLastMeasuredMs = new AtomicLong(-1L);
    private final AtomicLong lagLastMeasuredAtMs = new AtomicLong(0L);
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
