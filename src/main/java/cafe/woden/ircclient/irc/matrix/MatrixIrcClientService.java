package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.BackendNotAvailableException;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcBackendClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Service;

/** Matrix backend with homeserver probe + token-authenticated session bootstrap. */
@Service
@InfrastructureLayer
public class MatrixIrcClientService implements IrcBackendClientService {

  private static final String DEFAULT_UNAVAILABLE_REASON = "not connected";
  private static final String CONNECTING_REASON = "Matrix transport is connecting";
  private static final String UNSUPPORTED_OPERATION_REASON = "operation is not implemented yet";
  private static final String CTCP_ACTION_PREFIX = "\u0001ACTION ";
  private static final String CTCP_SUFFIX = "\u0001";
  private static final String TAG_IRCAFE_PM_TARGET = "ircafe/pm-target";
  private static final String TAG_MATRIX_MSGTYPE = "matrix.msgtype";
  private static final String TAG_MATRIX_MEDIA_URL = "matrix.media_url";
  private static final String TAG_MATRIX_UPLOAD_PATH = "matrix.upload_path";
  private static final String TAG_MATRIX_ROOM_ID = "matrix.room_id";
  private static final String TAG_DRAFT_REPLY = "draft/reply";
  private static final String TAG_DRAFT_EDIT = "draft/edit";
  private static final String RAW_TAG_MATRIX_MSGTYPE = "matrix/msgtype";
  private static final String RAW_TAG_MATRIX_MEDIA_URL = "matrix/media_url";
  private static final String RAW_TAG_MATRIX_UPLOAD_PATH = "matrix/upload_path";
  private static final String HISTORY_SELECTOR_TIMESTAMP_PREFIX = "timestamp=";
  private static final String HISTORY_SELECTOR_MSGID_PREFIX = "msgid=";
  private static final Set<String> MATRIX_MEDIA_MSGTYPES =
      Set.of("m.image", "m.file", "m.video", "m.audio");
  private static final int MATRIX_TYPING_TIMEOUT_MS = 30_000;
  private static final int SYNC_INTERVAL_SECONDS = 3;
  private static final int SYNC_TIMEOUT_MS = 0;
  private static final int ROOM_MESSAGE_INDEX_CAPACITY = 1_000;
  private static final int ROOM_REACTION_INDEX_CAPACITY = 1_000;
  private static final int MSGID_LOOKUP_SCAN_PAGE_LIMIT = 200;
  private static final int MSGID_LOOKUP_MAX_PAGES = 10;
  private static final int TIMESTAMP_CURSOR_SCAN_PAGE_LIMIT = 200;
  private static final int TIMESTAMP_CURSOR_MAX_PAGES = 20;
  private static final int UNREACT_LOOKUP_HISTORY_PAGE_LIMIT = 200;
  private static final int UNREACT_LOOKUP_MAX_PAGES = 10;
  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();

  private final ServerCatalog serverCatalog;
  private final MatrixHomeserverProbe homeserverProbe;
  private final MatrixDisplayNameClient displayNameClient;
  private final MatrixUserProfileClient userProfileClient;
  private final MatrixPresenceClient presenceClient;
  private final MatrixReadMarkerClient readMarkerClient;
  private final MatrixRoomMembershipClient roomMembershipClient;
  private final MatrixRoomStateClient roomStateClient;
  private final MatrixRoomDirectoryClient roomDirectoryClient;
  private final MatrixRoomRosterClient roomRosterClient;
  private final MatrixRoomHistoryClient roomHistoryClient;
  private final MatrixRoomTypingClient roomTypingClient;
  private final MatrixDirectRoomResolver directRoomResolver;
  private final MatrixMediaUploadClient mediaUploadClient;
  private final MatrixRoomMessageSender roomMessageSender;
  private final MatrixRawRoomAdminCommandHandler rawRoomAdminCommandHandler;
  private final MatrixRawModeCommandHandler rawModeCommandHandler;
  private final MatrixSyncClient syncClient;
  private final Map<String, String> availabilityReasonByServer = new ConcurrentHashMap<>();
  private final Map<String, MatrixSession> sessionsByServer = new ConcurrentHashMap<>();
  private final AtomicLong transactionSequence = new AtomicLong();

  public MatrixIrcClientService(
      ServerCatalog serverCatalog,
      MatrixHomeserverProbe homeserverProbe,
      MatrixDisplayNameClient displayNameClient,
      MatrixUserProfileClient userProfileClient,
      MatrixPresenceClient presenceClient,
      MatrixReadMarkerClient readMarkerClient,
      MatrixRoomMembershipClient roomMembershipClient,
      MatrixRoomStateClient roomStateClient,
      MatrixRoomDirectoryClient roomDirectoryClient,
      MatrixRoomRosterClient roomRosterClient,
      MatrixRoomHistoryClient roomHistoryClient,
      MatrixRoomTypingClient roomTypingClient,
      MatrixDirectRoomResolver directRoomResolver,
      MatrixMediaUploadClient mediaUploadClient,
      MatrixRoomMessageSender roomMessageSender,
      MatrixSyncClient syncClient) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    this.homeserverProbe = Objects.requireNonNull(homeserverProbe, "homeserverProbe");
    this.displayNameClient = Objects.requireNonNull(displayNameClient, "displayNameClient");
    this.userProfileClient = Objects.requireNonNull(userProfileClient, "userProfileClient");
    this.presenceClient = Objects.requireNonNull(presenceClient, "presenceClient");
    this.readMarkerClient = Objects.requireNonNull(readMarkerClient, "readMarkerClient");
    this.roomMembershipClient =
        Objects.requireNonNull(roomMembershipClient, "roomMembershipClient");
    this.roomStateClient = Objects.requireNonNull(roomStateClient, "roomStateClient");
    this.roomDirectoryClient = Objects.requireNonNull(roomDirectoryClient, "roomDirectoryClient");
    this.roomRosterClient = Objects.requireNonNull(roomRosterClient, "roomRosterClient");
    this.roomHistoryClient = Objects.requireNonNull(roomHistoryClient, "roomHistoryClient");
    this.roomTypingClient = Objects.requireNonNull(roomTypingClient, "roomTypingClient");
    this.directRoomResolver = Objects.requireNonNull(directRoomResolver, "directRoomResolver");
    this.mediaUploadClient = Objects.requireNonNull(mediaUploadClient, "mediaUploadClient");
    this.roomMessageSender = Objects.requireNonNull(roomMessageSender, "roomMessageSender");
    this.rawRoomAdminCommandHandler =
        new MatrixRawRoomAdminCommandHandler(
            this.serverCatalog,
            this.roomMembershipClient,
            this.roomStateClient,
            this.roomDirectoryClient,
            this::rawAdminSessionView,
            this::backendAvailabilityReason,
            bus::onNext);
    this.rawModeCommandHandler =
        new MatrixRawModeCommandHandler(
            this.serverCatalog,
            this.roomMembershipClient,
            this.roomStateClient,
            this.roomDirectoryClient,
            this::rawModeSessionView,
            this::backendAvailabilityReason,
            bus::onNext);
    this.syncClient = Objects.requireNonNull(syncClient, "syncClient");
  }

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.MATRIX;
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return bus.onBackpressureBuffer();
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    MatrixSession session = sessionsByServer.get(normalizeServerId(serverId));
    if (session == null || session.userId.isEmpty()) return Optional.empty();
    return Optional.of(session.userId);
  }

  @Override
  public Completable connect(String serverId) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              if (sessionsByServer.containsKey(sid)) {
                return;
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String host = eventHost(server);
              int port = eventPort(server);
              bus.onNext(
                  new ServerIrcEvent(
                      sid,
                      new IrcEvent.Connecting(Instant.now(), host, port, configuredNick(server))));
              availabilityReasonByServer.put(sid, CONNECTING_REASON);

              MatrixHomeserverProbe.ProbeResult probe;
              try {
                probe = homeserverProbe.probe(sid, server);
              } catch (IllegalArgumentException ex) {
                throw connectUnavailable(sid, invalidConfigurationDetail(ex));
              }

              if (!probe.reachable()) {
                throw connectUnavailable(
                    sid, "homeserver probe failed at " + probe.endpoint() + ": " + probe.detail());
              }

              String accessToken = configuredAccessToken(server);
              if (accessToken.isEmpty()) {
                throw connectUnavailable(sid, "Matrix access token is blank (set server password)");
              }

              MatrixHomeserverProbe.WhoamiResult whoami =
                  homeserverProbe.whoami(sid, server, accessToken);
              if (!whoami.authenticated()) {
                throw connectUnavailable(
                    sid, "authentication failed at " + whoami.endpoint() + ": " + whoami.detail());
              }

              String userId = normalize(whoami.userId());
              if (userId.isEmpty()) {
                throw connectUnavailable(sid, "authentication succeeded but user_id was blank");
              }

              MatrixSession nextSession = new MatrixSession(userId, accessToken);
              MatrixSession existing = sessionsByServer.putIfAbsent(sid, nextSession);
              if (existing != null) {
                availabilityReasonByServer.remove(sid);
                return;
              }

              availabilityReasonByServer.remove(sid);
              bus.onNext(
                  new ServerIrcEvent(
                      sid, new IrcEvent.Connected(Instant.now(), host, port, userId)));
              bus.onNext(new ServerIrcEvent(sid, new IrcEvent.ConnectionReady(Instant.now())));
              startSyncPolling(sid, server, nextSession);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable disconnect(String serverId) {
    return disconnect(serverId, "Client requested disconnect");
  }

  @Override
  public Completable disconnect(String serverId, String reason) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) return;
              MatrixSession removed = sessionsByServer.remove(sid);
              availabilityReasonByServer.remove(sid);
              if (removed == null) return;
              removed.closed.set(true);
              disposeSyncTask(removed);
              bus.onNext(
                  new ServerIrcEvent(
                      sid,
                      new IrcEvent.Disconnected(Instant.now(), normalizeDisconnectReason(reason))));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public void shutdownNow() {
    for (MatrixSession session : sessionsByServer.values()) {
      if (session == null) continue;
      session.closed.set(true);
      disposeSyncTask(session);
    }
    sessionsByServer.clear();
    availabilityReasonByServer.clear();
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String nick = Objects.toString(newNick, "").trim();
              if (nick.isEmpty()) {
                throw new IllegalArgumentException("new nick is blank");
              }
              if (nick.contains("\r") || nick.contains("\n")) {
                throw new IllegalArgumentException("new nick contains CR/LF");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "change-nick",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              MatrixDisplayNameClient.UpdateResult result =
                  displayNameClient.setDisplayName(
                      sid, server, session.accessToken, session.userId, nick);
              if (!result.updated()) {
                throw new IllegalStateException(
                    "Matrix nick change failed at " + result.endpoint() + ": " + result.detail());
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String msg = Objects.toString(awayMessage, "").trim();
              if (msg.contains("\r") || msg.contains("\n")) {
                throw new IllegalArgumentException("away message contains CR/LF");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "set-away",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              MatrixPresenceClient.PresenceResult result =
                  presenceClient.setAwayStatus(
                      sid, server, session.accessToken, session.userId, msg);
              if (!result.success()) {
                throw new IllegalStateException(
                    "Matrix set-away failed at " + result.endpoint() + ": " + result.detail());
              }

              bus.onNext(
                  new ServerIrcEvent(
                      sid, new IrcEvent.AwayStatusChanged(Instant.now(), result.away(), msg)));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "names",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, channel, server, session);
              MatrixRoomRosterClient.RosterResult result =
                  roomRosterClient.fetchJoinedMembers(sid, server, session.accessToken, roomId);
              if (!result.success()) {
                throw new IllegalStateException(
                    "Matrix names failed at " + result.endpoint() + ": " + result.detail());
              }

              List<IrcEvent.NickInfo> nicks = toNickInfos(result.members());
              bus.onNext(
                  new ServerIrcEvent(
                      sid,
                      new IrcEvent.NickListUpdated(Instant.now(), roomId, nicks, nicks.size(), 0)));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "join",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomIdOrAlias = normalizeJoinTarget(channel, server);
              MatrixRoomMembershipClient.JoinResult result =
                  roomMembershipClient.joinRoom(sid, server, session.accessToken, roomIdOrAlias);
              if (!result.joined()) {
                throw new IllegalStateException(
                    "Matrix join failed at " + result.endpoint() + ": " + result.detail());
              }

              String roomId = normalize(result.roomId());
              if (!looksLikeMatrixRoomId(roomId)) {
                throw new IllegalStateException("Matrix join succeeded but room_id was invalid");
              }
              if (looksLikeMatrixRoomAlias(roomIdOrAlias)) {
                session.rememberJoinedAlias(roomIdOrAlias, roomId);
              }
              bus.onNext(
                  new ServerIrcEvent(sid, new IrcEvent.JoinedChannel(Instant.now(), roomId)));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String userId = normalize(nick);
              if (userId.isEmpty()) {
                throw new IllegalArgumentException("nick is blank");
              }
              if (!looksLikeMatrixUserId(userId)) {
                throw new IllegalArgumentException("nick is not a Matrix user id");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "whois",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              MatrixUserProfileClient.ProfileResult result =
                  userProfileClient.fetchProfile(sid, server, session.accessToken, userId);
              if (!result.success()) {
                throw new IllegalStateException(
                    "Matrix whois failed at " + result.endpoint() + ": " + result.detail());
              }

              bus.onNext(
                  new ServerIrcEvent(
                      sid, new IrcEvent.WhoisResult(Instant.now(), userId, toWhoisLines(result))));
              bus.onNext(
                  new ServerIrcEvent(
                      sid,
                      new IrcEvent.WhoisProbeCompleted(
                          Instant.now(), userId, false, false, false)));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whowas(String serverId, String nick, int count) {
    return whois(serverId, nick);
  }

  @Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "part",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolvePartRoomId(sid, channel, server, session);
              MatrixRoomMembershipClient.LeaveResult result =
                  roomMembershipClient.leaveRoom(sid, server, session.accessToken, roomId);
              if (!result.left()) {
                throw new IllegalStateException(
                    "Matrix leave failed at " + result.endpoint() + ": " + result.detail());
              }
              session.forgetJoinedRoom(roomId);

              bus.onNext(
                  new ServerIrcEvent(
                      sid, new IrcEvent.LeftChannel(Instant.now(), roomId, normalize(reason))));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String text = Objects.toString(message, "");
              if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("message is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "send-to-channel",
                    sid,
                    backendAvailabilityReason(sid));
              }

              String txnId = nextTransactionId(sid);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, channel, server, session);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomMessage(
                      sid, server, session.accessToken, roomId, txnId, text);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix room send failed at " + result.endpoint() + ": " + result.detail());
              }

              emitLocalEchoEvent(sid, session, roomId, text, result.eventId());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String peerUserId = normalize(nick);
              if (peerUserId.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              if (!looksLikeMatrixUserId(peerUserId)) {
                throw new IllegalArgumentException("target is not a Matrix user id");
              }
              String text = Objects.toString(message, "");
              if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("message is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "send-private-message",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveDirectRoomId(sid, server, session, peerUserId);
              String txnId = nextTransactionId(sid);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomMessage(
                      sid, server, session.accessToken, roomId, txnId, text);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix private send failed at " + result.endpoint() + ": " + result.detail());
              }

              emitPrivateLocalEchoEvent(sid, session, peerUserId, roomId, text, result.eventId());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendNoticeToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String text = Objects.toString(message, "");
              if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("message is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "send-notice-to-channel",
                    sid,
                    backendAvailabilityReason(sid));
              }

              String txnId = nextTransactionId(sid);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, channel, server, session);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomNotice(
                      sid, server, session.accessToken, roomId, txnId, text);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix room notice failed at " + result.endpoint() + ": " + result.detail());
              }

              emitLocalNoticeEvent(
                  sid,
                  session,
                  roomId,
                  text,
                  result.eventId(),
                  Map.of(TAG_MATRIX_MSGTYPE, "m.notice"));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendNoticePrivate(String serverId, String nick, String message) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String peerUserId = normalize(nick);
              if (peerUserId.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              if (!looksLikeMatrixUserId(peerUserId)) {
                throw new IllegalArgumentException("target is not a Matrix user id");
              }
              String text = Objects.toString(message, "");
              if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("message is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "send-notice-private",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveDirectRoomId(sid, server, session, peerUserId);
              String txnId = nextTransactionId(sid);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomNotice(
                      sid, server, session.accessToken, roomId, txnId, text);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix private notice failed at "
                        + result.endpoint()
                        + ": "
                        + result.detail());
              }

              emitLocalNoticeEvent(
                  sid,
                  session,
                  peerUserId,
                  text,
                  result.eventId(),
                  privateMessageTags(peerUserId, roomId, "m.notice"));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      return Completable.error(new IllegalArgumentException("server id is blank"));
    }
    String line = Objects.toString(rawLine, "").trim();
    if (line.isEmpty()) {
      return Completable.error(new IllegalArgumentException("raw line is blank"));
    }
    if (line.contains("\r") || line.contains("\n")) {
      return Completable.error(new IllegalArgumentException("raw line contains CR/LF"));
    }

    MatrixSession session = sessionsByServer.get(sid);
    if (session == null) {
      return Completable.error(
          new BackendNotAvailableException(
              IrcProperties.Server.Backend.MATRIX, "raw", sid, backendAvailabilityReason(sid)));
    }

    RawCommand raw = parseRawCommand(line);
    if (raw.command().isEmpty()) {
      return Completable.error(new IllegalArgumentException("raw command is blank"));
    }

    return switch (raw.command()) {
      case "JOIN" -> joinChannel(sid, argOrBlank(raw, 0, "JOIN requires a room target"));
      case "PART" ->
          partChannel(
              sid, argOrBlank(raw, 0, "PART requires a room target"), joinArgs(raw.arguments(), 1));
      case "PRIVMSG" -> sendRawPrivmsg(sid, line, raw);
      case "NOTICE" -> sendRawNotice(sid, line, raw);
      case "TOPIC" -> sendRawTopic(sid, raw);
      case "KICK" -> sendRawKick(sid, raw);
      case "INVITE" -> sendRawInvite(sid, raw);
      case "WHO" -> sendRawWho(sid, raw);
      case "LIST" -> sendRawList(sid, raw);
      case "MODE" -> sendRawMode(sid, raw);
      case "WHOIS" -> whois(sid, argOrBlank(raw, 0, "WHOIS requires a target"));
      case "WHOWAS" -> sendRawWhowas(sid, raw);
      case "AWAY" -> setAway(sid, joinArgs(raw.arguments(), 0));
      case "NICK" -> changeNick(sid, joinArgs(raw.arguments(), 0));
      case "NAMES" -> requestNames(sid, argOrBlank(raw, 0, "NAMES requires a room target"));
      case "MARKREAD" -> sendRawMarkRead(sid, raw);
      case "TAGMSG" -> sendRawTagmsg(sid, line, raw);
      case "REDACT" -> sendRawRedact(sid, raw);
      case "CHATHISTORY" -> sendRawChatHistory(sid, raw);
      default -> rawCommandUnsupported(sid, raw.command());
    };
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public boolean isDraftUnreactAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
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
  public String typingAvailabilityReason(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      return DEFAULT_UNAVAILABLE_REASON;
    }
    if (sessionsByServer.containsKey(sid)) {
      return "";
    }
    return backendAvailabilityReason(sid);
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "send-typing",
                    sid,
                    backendAvailabilityReason(sid));
              }

              String normalizedState = normalizeTypingState(state);
              if (normalizedState.isEmpty()) {
                return;
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId =
                  resolveRealtimeSignalRoomId(sid, requestedTarget, server, session, false);
              if (roomId.isEmpty()) {
                return;
              }

              boolean typing = "active".equals(normalizedState);
              MatrixRoomTypingClient.TypingResult result =
                  roomTypingClient.setTyping(
                      sid,
                      server,
                      session.accessToken,
                      roomId,
                      session.userId,
                      typing,
                      MATRIX_TYPING_TIMEOUT_MS);
              if (!result.success()) {
                throw new IllegalStateException(
                    "Matrix typing send failed at " + result.endpoint() + ": " + result.detail());
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
                throw new IllegalArgumentException("server id is blank");
              }
              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "send-read-marker",
                    sid,
                    backendAvailabilityReason(sid));
              }

              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId =
                  resolveRealtimeSignalRoomId(sid, requestedTarget, server, session, true);
              if (roomId.isEmpty()) {
                return;
              }

              String eventId = session.latestRoomEventId(roomId);
              sendReadMarkerForEventId(sid, server, session, roomId, eventId);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, String selector, int limit) {
    String token = normalize(selector);
    if (token.isEmpty()) {
      return requestChatHistoryBefore(serverId, target, Instant.now(), limit);
    }

    Instant ts = parseHistoryTimestampSelector(token);
    if (ts != null) {
      return requestChatHistoryBefore(serverId, target, ts, limit);
    }

    String messageId = parseHistoryMessageIdSelector(token);
    if (!messageId.isEmpty()) {
      return requestChatHistoryBeforeByMessageId(
          serverId, target, messageId, limit, "chat-history-before");
    }

    return Completable.error(
        new IllegalArgumentException("selector must be msgid=... or timestamp=..."));
  }

  @Override
  public Completable requestChatHistoryLatest(
      String serverId, String target, String selector, int limit) {
    String token = normalize(selector);
    if (token.isEmpty() || "*".equals(token)) {
      return requestChatHistoryBefore(serverId, target, Instant.now(), limit);
    }
    Instant ts = parseHistoryTimestampSelector(token);
    if (ts != null) {
      return requestChatHistoryBefore(serverId, target, ts, limit);
    }
    String messageId = parseHistoryMessageIdSelector(token);
    if (!messageId.isEmpty()) {
      return requestChatHistoryBeforeByMessageId(
          serverId, target, messageId, limit, "chat-history-latest");
    }
    return Completable.error(
        new IllegalArgumentException("selector must be *, msgid=..., or timestamp=..."));
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId, String target, String startSelector, String endSelector, int limit) {
    String startToken = normalize(startSelector);
    String endToken = normalize(endSelector);
    if (startToken.isEmpty() || endToken.isEmpty()) {
      return Completable.error(
          new IllegalArgumentException(
              "CHATHISTORY BETWEEN requires <start-selector> <end-selector>"));
    }

    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "chat-history-between",
                    sid,
                    backendAvailabilityReason(sid));
              }

              int requestedLimit = normalizeHistoryLimit(limit);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveHistoryRoomId(sid, requestedTarget, server, session);
              Instant startAt =
                  resolveHistorySelectorInstant(
                      sid,
                      server,
                      session,
                      roomId,
                      startToken,
                      "chat-history-between",
                      "start selector");
              Instant endAt =
                  resolveHistorySelectorInstant(
                      sid,
                      server,
                      session,
                      roomId,
                      endToken,
                      "chat-history-between",
                      "end selector");
              Instant endExclusive = endAt == null ? Instant.now() : endAt;
              if (startAt != null && !startAt.isBefore(endExclusive)) {
                throw new IllegalArgumentException(
                    "start selector must be earlier than end selector");
              }

              Instant startInclusive = startAt == null ? Instant.EPOCH : startAt;
              String forwardCursor =
                  resolveForwardCursorForTimestamp(
                      sid, server, session, roomId, startInclusive.toEpochMilli());
              List<ChatHistoryEntry> entries =
                  fetchHistoryEntriesForwardFromCursor(
                      sid,
                      server,
                      session,
                      requestedTarget,
                      roomId,
                      forwardCursor,
                      startInclusive,
                      endExclusive,
                      requestedLimit);
              emitHistoryBatch(sid, requestedTarget, entries);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryAround(
      String serverId, String target, String selector, int limit) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              String token = normalize(selector);

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "chat-history-around",
                    sid,
                    backendAvailabilityReason(sid));
              }

              int requestedLimit = normalizeHistoryLimit(limit);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveHistoryRoomId(sid, requestedTarget, server, session);

              Instant centerAt;
              if (token.isEmpty() || "*".equals(token)) {
                centerAt = Instant.now();
              } else {
                centerAt =
                    resolveHistorySelectorInstant(
                        sid, server, session, roomId, token, "chat-history-around", "selector");
              }

              String forwardCursor =
                  resolveForwardCursorForTimestamp(
                      sid, server, session, roomId, centerAt.toEpochMilli());
              int scanLimit = Math.min(200, Math.max(requestedLimit * 4, requestedLimit + 20));
              List<ChatHistoryEntry> backwardCandidates =
                  fetchHistoryEntriesBackwardFromCursor(
                      sid,
                      server,
                      session,
                      requestedTarget,
                      roomId,
                      forwardCursor,
                      null,
                      null,
                      scanLimit);
              List<ChatHistoryEntry> forwardCandidates =
                  fetchHistoryEntriesForwardFromCursor(
                      sid,
                      server,
                      session,
                      requestedTarget,
                      roomId,
                      forwardCursor,
                      null,
                      null,
                      scanLimit);
              List<ChatHistoryEntry> candidates =
                  mergeHistoryCandidates(backwardCandidates, forwardCandidates);
              List<ChatHistoryEntry> entries =
                  selectEntriesAroundTimestamp(candidates, centerAt, requestedLimit);
              emitHistoryBatch(sid, requestedTarget, entries);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(
      String serverId, String target, Instant beforeExclusive, int limit) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "chat-history",
                    sid,
                    backendAvailabilityReason(sid));
              }

              int requestedLimit = normalizeHistoryLimit(limit);
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveHistoryRoomId(sid, requestedTarget, server, session);
              long beforeEpochMs =
                  beforeExclusive == null
                      ? System.currentTimeMillis()
                      : beforeExclusive.toEpochMilli();
              List<ChatHistoryEntry> entries =
                  fetchHistoryEntriesBefore(
                      sid, server, session, requestedTarget, roomId, beforeEpochMs, requestedLimit);
              emitHistoryBatch(sid, requestedTarget, entries);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && sessionsByServer.containsKey(sid);
  }

  @Override
  public String backendAvailabilityReason(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) {
      return DEFAULT_UNAVAILABLE_REASON;
    }
    if (sessionsByServer.containsKey(sid)) {
      return "";
    }
    String reason = Objects.toString(availabilityReasonByServer.get(sid), "").trim();
    return reason.isEmpty() ? DEFAULT_UNAVAILABLE_REASON : reason;
  }

  private Completable unavailable(String operation, String serverId) {
    String sid = normalizeServerId(serverId);
    String detail = operationUnavailableReason(sid);
    return Completable.error(
        new BackendNotAvailableException(
            IrcProperties.Server.Backend.MATRIX,
            Objects.toString(operation, "").trim(),
            sid,
            detail));
  }

  private String operationUnavailableReason(String serverId) {
    if (sessionsByServer.containsKey(serverId)) {
      return UNSUPPORTED_OPERATION_REASON;
    }
    return backendAvailabilityReason(serverId);
  }

  private BackendNotAvailableException connectUnavailable(String serverId, String detail) {
    String normalizedDetail = normalize(detail);
    if (normalizedDetail.isEmpty()) {
      normalizedDetail = DEFAULT_UNAVAILABLE_REASON;
    }
    availabilityReasonByServer.put(serverId, normalizedDetail);
    sessionsByServer.remove(serverId);
    return new BackendNotAvailableException(
        IrcProperties.Server.Backend.MATRIX, "connect", serverId, normalizedDetail);
  }

  private static String invalidConfigurationDetail(IllegalArgumentException ex) {
    String message = Objects.toString(ex == null ? "" : ex.getMessage(), "").trim();
    if (message.isEmpty()) {
      message = "unknown validation error";
    }
    return "invalid Matrix homeserver configuration: " + message;
  }

  private static String configuredAccessToken(IrcProperties.Server server) {
    if (server == null) return "";
    String token = normalize(server.serverPassword());
    if (!token.isEmpty()) return token;
    IrcProperties.Server.Sasl sasl = server.sasl();
    if (sasl == null) return "";
    return normalize(sasl.password());
  }

  private static String configuredNick(IrcProperties.Server server) {
    if (server == null) return "matrix";
    String nick = normalize(server.nick());
    if (!nick.isEmpty()) return nick;
    nick = normalize(server.login());
    return nick.isEmpty() ? "matrix" : nick;
  }

  private static String eventHost(IrcProperties.Server server) {
    try {
      return normalize(MatrixEndpointResolver.homeserverBaseUri(server).getHost());
    } catch (Exception ignored) {
      return normalize(server == null ? "" : server.host());
    }
  }

  private static int eventPort(IrcProperties.Server server) {
    try {
      int endpointPort = MatrixEndpointResolver.homeserverBaseUri(server).getPort();
      if (endpointPort > 0) return endpointPort;
    } catch (Exception ignored) {
    }
    if (server == null) return 443;
    if (server.port() > 0) return server.port();
    return server.tls() ? 443 : 80;
  }

  private static String normalizeDisconnectReason(String reason) {
    String msg = normalize(reason);
    return msg.isEmpty() ? "Client requested disconnect" : msg;
  }

  private static String normalizeServerId(String serverId) {
    return normalize(serverId);
  }

  private MatrixRawRoomAdminCommandHandler.SessionView rawAdminSessionView(String serverId) {
    MatrixSession session = sessionsByServer.get(normalizeServerId(serverId));
    if (session == null) return null;
    return new MatrixRawRoomAdminCommandHandler.SessionView() {
      @Override
      public String userId() {
        return session.userId;
      }

      @Override
      public String accessToken() {
        return session.accessToken;
      }

      @Override
      public String roomForAlias(String roomAlias) {
        return session.roomForAlias(roomAlias);
      }

      @Override
      public void rememberJoinedAlias(String roomAlias, String roomId) {
        session.rememberJoinedAlias(roomAlias, roomId);
      }

      @Override
      public void forgetJoinedRoom(String roomId) {
        session.forgetJoinedRoom(roomId);
      }
    };
  }

  private MatrixRawModeCommandHandler.SessionView rawModeSessionView(String serverId) {
    MatrixSession session = sessionsByServer.get(normalizeServerId(serverId));
    if (session == null) return null;
    return new MatrixRawModeCommandHandler.SessionView() {
      @Override
      public String userId() {
        return session.userId;
      }

      @Override
      public String accessToken() {
        return session.accessToken;
      }

      @Override
      public String roomForAlias(String roomAlias) {
        return session.roomForAlias(roomAlias);
      }

      @Override
      public void rememberJoinedAlias(String roomAlias, String roomId) {
        session.rememberJoinedAlias(roomAlias, roomId);
      }
    };
  }

  private Completable unsupportedChatHistoryMode(String operation, String serverId, String detail) {
    String sid = normalizeServerId(serverId);
    String message = normalize(detail);
    if (message.isEmpty()) {
      message = UNSUPPORTED_OPERATION_REASON;
    }
    if (!sid.isEmpty() && !sessionsByServer.containsKey(sid)) {
      message = backendAvailabilityReason(sid);
    }
    return Completable.error(
        new BackendNotAvailableException(
            IrcProperties.Server.Backend.MATRIX, operation, sid, message));
  }

  private static Instant parseHistoryTimestampSelector(String selector) {
    String token = normalize(selector);
    if (!token.toLowerCase(Locale.ROOT).startsWith(HISTORY_SELECTOR_TIMESTAMP_PREFIX)) {
      return null;
    }
    String value = normalize(token.substring(HISTORY_SELECTOR_TIMESTAMP_PREFIX.length()));
    if (value.isEmpty()) {
      throw new IllegalArgumentException("timestamp selector is blank");
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("timestamp selector is invalid: " + value, ex);
    }
  }

  private static boolean historyMsgidSelector(String selector) {
    return normalize(selector).toLowerCase(Locale.ROOT).startsWith(HISTORY_SELECTOR_MSGID_PREFIX);
  }

  private static String parseHistoryMessageIdSelector(String selector) {
    String token = normalize(selector);
    if (!historyMsgidSelector(token)) {
      return "";
    }
    String messageId = normalize(token.substring(HISTORY_SELECTOR_MSGID_PREFIX.length()));
    if (messageId.isEmpty()) {
      throw new IllegalArgumentException("msgid selector is blank");
    }
    return messageId;
  }

  private Completable requestChatHistoryBeforeByMessageId(
      String serverId, String target, String messageId, int limit, String operation) {
    return Completable.defer(
        () -> {
          String sid = normalizeServerId(serverId);
          if (sid.isEmpty()) {
            return Completable.error(new IllegalArgumentException("server id is blank"));
          }
          String requestedTarget = normalize(target);
          if (requestedTarget.isEmpty()) {
            return Completable.error(new IllegalArgumentException("target is blank"));
          }

          MatrixSession session = sessionsByServer.get(sid);
          if (session == null) {
            return Completable.error(
                new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    operation,
                    sid,
                    backendAvailabilityReason(sid)));
          }

          IrcProperties.Server server = serverCatalog.require(sid);
          String roomId = resolveHistoryRoomId(sid, requestedTarget, server, session);
          Instant marker =
              resolveHistoryMessageIdInstant(sid, server, session, roomId, messageId, operation);
          return requestChatHistoryBefore(sid, requestedTarget, marker, limit);
        });
  }

  private Instant resolveHistorySelectorInstant(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String selector,
      String operation,
      String selectorName) {
    String token = normalize(selector);
    if (token.isEmpty() || "*".equals(token)) {
      return null;
    }
    Instant ts = parseHistoryTimestampSelector(token);
    if (ts != null) {
      return ts;
    }
    String messageId = parseHistoryMessageIdSelector(token);
    if (!messageId.isEmpty()) {
      return resolveHistoryMessageIdInstant(
          serverId, server, session, roomId, messageId, operation);
    }
    throw new IllegalArgumentException(selectorName + " must be '*', msgid=..., or timestamp=...");
  }

  private Instant resolveHistoryMessageIdInstant(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String messageId,
      String operation) {
    long timestampMs =
        session == null ? 0L : session.roomEventTimestampMs(roomId, normalize(messageId));
    if (timestampMs <= 0L) {
      timestampMs =
          lookupMessageTimestampByScanningHistory(
              serverId, server, session, roomId, messageId, operation);
    }
    if (timestampMs <= 0L) {
      throw new BackendNotAvailableException(
          IrcProperties.Server.Backend.MATRIX,
          operation,
          normalizeServerId(serverId),
          "Matrix backend cannot resolve msgid selector in this session");
    }
    return Instant.ofEpochMilli(timestampMs);
  }

  private long lookupMessageTimestampByScanningHistory(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String messageId,
      String operation) {
    if (session == null) return 0L;
    String rid = normalize(roomId);
    String mid = normalize(messageId);
    if (rid.isEmpty() || mid.isEmpty()) return 0L;

    long cached = session.roomEventTimestampMs(rid, mid);
    if (cached > 0L) return cached;

    long beforeEpochMs = System.currentTimeMillis();
    String fromToken = resolveHistoryFromToken(serverId, server, session, rid, beforeEpochMs);
    if (fromToken.isEmpty()) return 0L;

    String cursor = fromToken;
    for (int page = 0; page < MSGID_LOOKUP_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              serverId, server, session.accessToken, rid, cursor, MSGID_LOOKUP_SCAN_PAGE_LIMIT);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new BackendNotAvailableException(
            IrcProperties.Server.Backend.MATRIX,
            operation,
            normalizeServerId(serverId),
            "Matrix backend could not scan history for msgid selector: " + result.detail());
      }

      session.rememberHistoryEvents(rid, result.events());
      long found = session.roomEventTimestampMs(rid, mid);
      if (found > 0L) return found;

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        break;
      }
      session.rememberHistoryCursor(rid, nextToken, beforeEpochMs);
      cursor = nextToken;
      if (result.events() == null || result.events().isEmpty()) {
        break;
      }
    }

    return session.roomEventTimestampMs(rid, mid);
  }

  private Completable sendRawPrivmsg(String serverId, String rawLine, RawCommand raw) {
    String target = argOrBlank(raw, 0, "PRIVMSG requires target and message");
    String message = joinArgs(raw.arguments(), 1);
    Map<String, String> tags = parseRawTags(rawLine);
    String matrixMsgType = rawMatrixMsgType(tags);
    if (message.isEmpty() && matrixMsgType.isEmpty()) {
      throw new IllegalArgumentException("PRIVMSG requires target and message");
    }
    String editTarget = normalize(tags.get("draft/edit"));
    if (!editTarget.isEmpty()) {
      return sendRawEdit(serverId, target, editTarget, message);
    }
    String replyTarget = normalize(tags.get("draft/reply"));
    if (!replyTarget.isEmpty()) {
      return sendRawReply(serverId, target, replyTarget, message);
    }
    if (!matrixMsgType.isEmpty()) {
      return sendRawMediaMessage(
          serverId,
          target,
          message,
          matrixMsgType,
          rawMatrixMediaUrl(tags),
          rawMatrixUploadPath(tags));
    }
    if (looksLikeMatrixUserId(target)) {
      return sendPrivateMessage(serverId, target, message);
    }
    return sendToChannel(serverId, target, message);
  }

  private Completable sendRawNotice(String serverId, String rawLine, RawCommand raw) {
    String target = argOrBlank(raw, 0, "NOTICE requires target and message");
    String message = joinArgs(raw.arguments(), 1);
    if (message.isEmpty()) {
      throw new IllegalArgumentException("NOTICE requires target and message");
    }
    Map<String, String> tags = parseRawTags(rawLine);
    String editTarget = normalize(tags.get("draft/edit"));
    if (!editTarget.isEmpty()) {
      return sendRawEdit(serverId, target, editTarget, message);
    }
    String replyTarget = normalize(tags.get("draft/reply"));
    if (!replyTarget.isEmpty()) {
      return sendRawReply(serverId, target, replyTarget, message);
    }
    if (looksLikeMatrixUserId(target)) {
      return sendNoticePrivate(serverId, target, message);
    }
    return sendNoticeToChannel(serverId, target, message);
  }

  private Completable sendRawMediaMessage(
      String serverId,
      String target,
      String message,
      String msgType,
      String mediaUrl,
      String uploadPath) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String mediaType = normalize(msgType);
              if (!isMatrixMediaMsgType(mediaType)) {
                throw new IllegalArgumentException("unsupported Matrix media msgtype");
              }
              String rawTarget = normalize(target);
              if (rawTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              String text = Objects.toString(message, "");

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "send-media-message",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String mediaRef = normalize(mediaUrl);
              String uploadSource = normalize(uploadPath);
              if (mediaRef.isEmpty()) {
                if (uploadSource.isEmpty()) {
                  throw new IllegalArgumentException("matrix media url tag is blank");
                }
                MatrixMediaUploadClient.UploadResult uploadResult =
                    mediaUploadClient.uploadFile(sid, server, session.accessToken, uploadSource);
                if (!uploadResult.success()) {
                  throw new IllegalStateException(
                      "Matrix media upload failed at "
                          + uploadResult.endpoint()
                          + ": "
                          + uploadResult.detail());
                }
                mediaRef = normalize(uploadResult.contentUri());
                if (mediaRef.isEmpty()) {
                  throw new IllegalStateException("Matrix media upload returned blank content URI");
                }
              }

              String txnId = nextTransactionId(sid);
              if (looksLikeMatrixUserId(rawTarget)) {
                String roomId = resolveDirectRoomId(sid, server, session, rawTarget);
                MatrixRoomMessageSender.SendResult result =
                    roomMessageSender.sendRoomMediaMessage(
                        sid, server, session.accessToken, roomId, txnId, text, mediaType, mediaRef);
                if (!result.accepted()) {
                  throw new IllegalStateException(
                      "Matrix private media send failed at "
                          + result.endpoint()
                          + ": "
                          + result.detail());
                }
                emitPrivateLocalMediaEchoEvent(
                    sid, session, rawTarget, roomId, text, result.eventId(), mediaType, mediaRef);
                return;
              }

              String roomId = resolveAliasOrRoomId(sid, rawTarget, server, session);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomMediaMessage(
                      sid, server, session.accessToken, roomId, txnId, text, mediaType, mediaRef);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix room media send failed at "
                        + result.endpoint()
                        + ": "
                        + result.detail());
              }
              emitLocalMediaEchoEvent(
                  sid, session, roomId, text, result.eventId(), mediaType, mediaRef);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private Completable sendRawWhowas(String serverId, RawCommand raw) {
    String target = argOrBlank(raw, 0, "WHOWAS requires a target");
    int count = 0;
    if (raw.arguments().size() >= 2) {
      String token = normalize(raw.arguments().get(1));
      if (!token.isEmpty()) {
        if (!looksLikeInteger(token)) {
          throw new IllegalArgumentException("WHOWAS count must be a positive integer");
        }
        count = parseRawChatHistoryLimit(token);
      }
    }
    return whowas(serverId, target, count);
  }

  private Completable sendRawTopic(String serverId, RawCommand raw) {
    return rawRoomAdminCommandHandler.handleTopic(serverId, raw.arguments());
  }

  private Completable sendRawKick(String serverId, RawCommand raw) {
    return rawRoomAdminCommandHandler.handleKick(serverId, raw.arguments());
  }

  private Completable sendRawInvite(String serverId, RawCommand raw) {
    return rawRoomAdminCommandHandler.handleInvite(serverId, raw.arguments());
  }

  private Completable sendRawWho(String serverId, RawCommand raw) {
    String token = argOrBlank(raw, 0, "WHO requires a target");
    if (looksLikeMatrixUserId(token)) {
      return whois(serverId, token);
    }
    if (looksLikeMatrixRoomId(token)
        || looksLikeMatrixRoomAlias(token)
        || (token.startsWith("#") && token.indexOf(':') < 0)) {
      return requestNames(serverId, token);
    }
    throw new IllegalArgumentException("WHO supports Matrix user ids and room targets only");
  }

  private Completable sendRawList(String serverId, RawCommand raw) {
    return rawRoomAdminCommandHandler.handleList(serverId, raw.arguments());
  }

  private Completable sendRawMode(String serverId, RawCommand raw) {
    return rawModeCommandHandler.handleMode(serverId, raw.arguments());
  }

  private Completable sendRawMarkRead(String serverId, RawCommand raw) {
    String target = argOrBlank(raw, 0, "MARKREAD requires a target");
    String selector = raw.arguments().size() > 1 ? normalize(raw.arguments().get(1)) : "";
    if (selector.isEmpty()) {
      return sendReadMarker(serverId, target, Instant.now());
    }
    Instant timestamp = parseHistoryTimestampSelector(selector);
    if (timestamp != null) {
      return sendReadMarker(serverId, target, timestamp);
    }
    String messageId = parseHistoryMessageIdSelector(selector);
    if (!messageId.isEmpty()) {
      return sendRawMarkReadByMessageId(serverId, target, messageId);
    }
    throw new IllegalArgumentException("MARKREAD selector must be timestamp=... or msgid=...");
  }

  private Completable sendRawMarkReadByMessageId(String serverId, String target, String messageId) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String requestedTarget = normalize(target);
              if (requestedTarget.isEmpty()) {
                throw new IllegalArgumentException("target is blank");
              }
              String markerMessageId = normalize(messageId);
              if (markerMessageId.isEmpty()) {
                throw new IllegalArgumentException("msgid selector is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "markread",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveHistoryRoomId(sid, requestedTarget, server, session);
              resolveHistoryMessageIdInstant(
                  sid, server, session, roomId, markerMessageId, "markread");
              sendReadMarkerForEventId(sid, server, session, roomId, markerMessageId);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private void sendReadMarkerForEventId(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String eventId) {
    String sid = normalizeServerId(serverId);
    String rid = normalize(roomId);
    String eid = normalize(eventId);
    if (sid.isEmpty() || session == null || rid.isEmpty() || eid.isEmpty()) {
      return;
    }
    MatrixReadMarkerClient.ReadMarkerResult result =
        readMarkerClient.updateReadMarker(sid, server, session.accessToken, rid, eid);
    if (!result.success()) {
      throw new IllegalStateException(
          "Matrix read marker failed at " + result.endpoint() + ": " + result.detail());
    }
  }

  private Completable sendRawTagmsg(String serverId, String rawLine, RawCommand raw) {
    String target = argOrBlank(raw, 0, "TAGMSG requires a target");
    Map<String, String> tags = parseRawTags(rawLine);
    String replyTarget = normalize(tags.get("draft/reply"));
    String reaction = normalize(tags.get("draft/react"));
    String unreaction = normalize(tags.get("draft/unreact"));
    if (!reaction.isEmpty() && !unreaction.isEmpty()) {
      throw new IllegalArgumentException(
          "TAGMSG cannot include both +draft/react and +draft/unreact");
    }
    if (!reaction.isEmpty() || !unreaction.isEmpty()) {
      if (replyTarget.isEmpty()) {
        throw new IllegalArgumentException("TAGMSG draft reactions require +draft/reply=<msgid>");
      }
      if (!reaction.isEmpty()) {
        return sendRawReaction(serverId, target, replyTarget, reaction);
      }
      return sendRawUnreaction(serverId, target, replyTarget, unreaction);
    }
    String typingState = normalizeTypingState(rawTypingTagValue(tags));
    if (typingState.isEmpty()) {
      throw new IllegalArgumentException("TAGMSG requires +typing=<active|paused|done>");
    }
    return sendTyping(serverId, target, typingState);
  }

  private Completable sendRawReply(
      String serverId, String target, String replyToEventId, String message) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String targetEventId = normalize(replyToEventId);
              if (targetEventId.isEmpty()) {
                throw new IllegalArgumentException("reply target is blank");
              }
              String text = Objects.toString(message, "");
              if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("message is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "reply",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveMutationRoomId(sid, target, server, session);
              String txnId = nextTransactionId(sid);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomReply(
                      sid, server, session.accessToken, roomId, txnId, targetEventId, text);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix room reply failed at " + result.endpoint() + ": " + result.detail());
              }
              session.rememberLatestRoomEvent(roomId, result.eventId());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private Completable sendRawEdit(
      String serverId, String target, String targetEventId, String message) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String editTarget = normalize(targetEventId);
              if (editTarget.isEmpty()) {
                throw new IllegalArgumentException("edit target is blank");
              }
              String text = Objects.toString(message, "");
              if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("message is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "edit",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveMutationRoomId(sid, target, server, session);
              String txnId = nextTransactionId(sid);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomEdit(
                      sid, server, session.accessToken, roomId, txnId, editTarget, text);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix room edit failed at " + result.endpoint() + ": " + result.detail());
              }
              session.rememberLatestRoomEvent(roomId, result.eventId());
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private Completable sendRawReaction(
      String serverId, String target, String targetEventId, String reaction) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String reactTarget = normalize(targetEventId);
              String react = normalize(reaction);
              if (reactTarget.isEmpty() || react.isEmpty()) {
                throw new IllegalArgumentException("reaction target or token is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "react",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveMutationRoomId(sid, target, server, session);
              String txnId = nextTransactionId(sid);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomReaction(
                      sid, server, session.accessToken, roomId, txnId, reactTarget, react);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix room reaction failed at " + result.endpoint() + ": " + result.detail());
              }
              session.rememberReactionEvent(
                  roomId, result.eventId(), reactTarget, react, session.userId);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private Completable sendRawUnreaction(
      String serverId, String target, String targetEventId, String reaction) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              String reactTarget = normalize(targetEventId);
              String react = normalize(reaction);
              if (reactTarget.isEmpty() || react.isEmpty()) {
                throw new IllegalArgumentException("reaction target or token is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "unreact",
                    sid,
                    backendAvailabilityReason(sid));
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveMutationRoomId(sid, target, server, session);
              String reactionEventId =
                  resolveReactionEventIdForUnreact(
                      sid, server, session, roomId, reactTarget, react, session.userId);
              if (reactionEventId.isEmpty()) {
                throw new IllegalStateException(
                    "Matrix reaction event is unknown for target/reaction in this session");
              }

              String txnId = nextTransactionId(sid);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomRedaction(
                      sid, server, session.accessToken, roomId, reactionEventId, txnId, "");
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix unreact failed at " + result.endpoint() + ": " + result.detail());
              }
              session.consumeReactionEvent(roomId, reactionEventId);
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private String resolveReactionEventIdForUnreact(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String targetEventId,
      String reaction,
      String sender) {
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (session == null
        || sid.isEmpty()
        || rid.isEmpty()
        || targetId.isEmpty()
        || key.isEmpty()
        || from.isEmpty()) {
      return "";
    }

    String reactionEventId = session.findReactionEventId(rid, targetId, key, from);
    if (!reactionEventId.isEmpty()) {
      return reactionEventId;
    }

    rememberReactionMutationsFromSync(sid, server, session, rid);
    reactionEventId = session.findReactionEventId(rid, targetId, key, from);
    if (!reactionEventId.isEmpty()) {
      return reactionEventId;
    }

    return findReactionEventIdFromHistory(sid, server, session, rid, targetId, key, from);
  }

  private void rememberReactionMutationsFromSync(
      String serverId, IrcProperties.Server server, MatrixSession session, String roomId) {
    if (session == null) return;
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    if (sid.isEmpty() || rid.isEmpty()) return;
    String since = normalize(session.sinceToken.get());
    MatrixSyncClient.SyncResult sync =
        syncClient.sync(sid, server, session.accessToken, since, SYNC_TIMEOUT_MS);
    if (sync == null || !sync.success()) {
      return;
    }
    rememberSyncReactionMutations(
        session, rid, sync.reactionEvents(), sync.redactionEvents(), new LinkedHashSet<>());
  }

  private String findReactionEventIdFromHistory(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String targetEventId,
      String reaction,
      String sender) {
    if (session == null) return "";
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (sid.isEmpty() || rid.isEmpty() || targetId.isEmpty() || key.isEmpty() || from.isEmpty()) {
      return "";
    }

    String fromToken =
        resolveHistoryFromToken(sid, server, session, rid, System.currentTimeMillis());
    if (fromToken.isEmpty()) {
      return "";
    }

    String reactionEventId =
        findReactionEventIdByScanningHistoryBackward(
            sid, server, session, rid, fromToken, targetId, key, from);
    if (!reactionEventId.isEmpty()) {
      return reactionEventId;
    }

    long targetTimestampMs = session.roomEventTimestampMs(rid, targetId);
    if (targetTimestampMs <= 0L) {
      try {
        targetTimestampMs =
            lookupMessageTimestampByScanningHistory(sid, server, session, rid, targetId, "unreact");
      } catch (RuntimeException ignored) {
        return "";
      }
    }
    if (targetTimestampMs <= 0L) {
      return "";
    }

    String cursorNearTarget;
    try {
      cursorNearTarget =
          resolveForwardCursorForTimestamp(sid, server, session, rid, targetTimestampMs);
    } catch (RuntimeException ignored) {
      return "";
    }
    if (cursorNearTarget.isEmpty()) {
      return "";
    }

    return findReactionEventIdByScanningHistoryForward(
        sid, server, session, rid, cursorNearTarget, targetId, key, from);
  }

  private String findReactionEventIdByScanningHistoryBackward(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String fromToken,
      String targetEventId,
      String reaction,
      String sender) {
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String cursor = normalize(fromToken);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (session == null
        || sid.isEmpty()
        || rid.isEmpty()
        || cursor.isEmpty()
        || targetId.isEmpty()
        || key.isEmpty()
        || from.isEmpty()) {
      return "";
    }

    Set<String> redactedReactionIds = new LinkedHashSet<>();
    for (int page = 0; page < UNREACT_LOOKUP_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              sid, server, session.accessToken, rid, cursor, UNREACT_LOOKUP_HISTORY_PAGE_LIMIT);
      if (result == null || !result.success()) {
        return "";
      }
      session.rememberHistoryEvents(rid, result.events());
      rememberHistoryReactionMutations(
          session, rid, result.reactionEvents(), result.redactionEvents(), redactedReactionIds);
      String reactionEventId = session.findReactionEventId(rid, targetId, key, from);
      if (!reactionEventId.isEmpty()) {
        return reactionEventId;
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        return "";
      }
      cursor = nextToken;
    }
    return "";
  }

  private String findReactionEventIdByScanningHistoryForward(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      String fromToken,
      String targetEventId,
      String reaction,
      String sender) {
    String sid = normalize(serverId);
    String rid = normalize(roomId);
    String cursor = normalize(fromToken);
    String targetId = normalize(targetEventId);
    String key = normalize(reaction);
    String from = normalize(sender);
    if (session == null
        || sid.isEmpty()
        || rid.isEmpty()
        || cursor.isEmpty()
        || targetId.isEmpty()
        || key.isEmpty()
        || from.isEmpty()) {
      return "";
    }

    Set<String> redactedReactionIds = new LinkedHashSet<>();
    for (int page = 0; page < UNREACT_LOOKUP_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesAfter(
              sid, server, session.accessToken, rid, cursor, UNREACT_LOOKUP_HISTORY_PAGE_LIMIT);
      if (result == null || !result.success()) {
        return "";
      }
      session.rememberHistoryEvents(rid, result.events());
      rememberHistoryReactionMutations(
          session, rid, result.reactionEvents(), result.redactionEvents(), redactedReactionIds);
      String reactionEventId = session.findReactionEventId(rid, targetId, key, from);
      if (!reactionEventId.isEmpty()) {
        return reactionEventId;
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        return "";
      }
      cursor = nextToken;
    }
    return "";
  }

  private static void rememberSyncReactionMutations(
      MatrixSession session,
      String roomId,
      List<MatrixSyncClient.RoomReactionEvent> reactionEvents,
      List<MatrixSyncClient.RoomRedactionEvent> redactionEvents,
      Set<String> redactedReactionIds) {
    if (session == null) return;
    String rid = normalize(roomId);
    if (rid.isEmpty()) return;
    Set<String> tombstones =
        redactedReactionIds == null ? new LinkedHashSet<>() : redactedReactionIds;

    for (MatrixSyncClient.RoomRedactionEvent redactionEvent :
        redactionEvents == null
            ? List.<MatrixSyncClient.RoomRedactionEvent>of()
            : redactionEvents) {
      if (redactionEvent == null) continue;
      if (!rid.equals(normalize(redactionEvent.roomId()))) continue;
      String redactsEventId = normalize(redactionEvent.redactsEventId());
      if (redactsEventId.isEmpty()) continue;
      tombstones.add(redactsEventId);
      session.consumeReactionEvent(rid, redactsEventId);
    }

    for (MatrixSyncClient.RoomReactionEvent reactionEvent :
        reactionEvents == null ? List.<MatrixSyncClient.RoomReactionEvent>of() : reactionEvents) {
      if (reactionEvent == null) continue;
      if (!rid.equals(normalize(reactionEvent.roomId()))) continue;
      String eventId = normalize(reactionEvent.eventId());
      String sender = normalize(reactionEvent.sender());
      String targetMessageId = normalize(reactionEvent.targetEventId());
      String reaction = normalize(reactionEvent.reaction());
      if (eventId.isEmpty()
          || sender.isEmpty()
          || targetMessageId.isEmpty()
          || reaction.isEmpty()
          || tombstones.contains(eventId)) {
        continue;
      }
      session.rememberReactionEvent(rid, eventId, targetMessageId, reaction, sender);
    }
  }

  private static void rememberHistoryReactionMutations(
      MatrixSession session,
      String roomId,
      List<MatrixRoomHistoryClient.RoomReactionEvent> reactionEvents,
      List<MatrixRoomHistoryClient.RoomRedactionEvent> redactionEvents,
      Set<String> redactedReactionIds) {
    if (session == null) return;
    String rid = normalize(roomId);
    if (rid.isEmpty()) return;
    Set<String> tombstones =
        redactedReactionIds == null ? new LinkedHashSet<>() : redactedReactionIds;

    for (MatrixRoomHistoryClient.RoomRedactionEvent redactionEvent :
        redactionEvents == null
            ? List.<MatrixRoomHistoryClient.RoomRedactionEvent>of()
            : redactionEvents) {
      if (redactionEvent == null) continue;
      String redactsEventId = normalize(redactionEvent.redactsEventId());
      if (redactsEventId.isEmpty()) continue;
      tombstones.add(redactsEventId);
      session.consumeReactionEvent(rid, redactsEventId);
    }

    for (MatrixRoomHistoryClient.RoomReactionEvent reactionEvent :
        reactionEvents == null
            ? List.<MatrixRoomHistoryClient.RoomReactionEvent>of()
            : reactionEvents) {
      if (reactionEvent == null) continue;
      String eventId = normalize(reactionEvent.eventId());
      String sender = normalize(reactionEvent.sender());
      String targetMessageId = normalize(reactionEvent.targetEventId());
      String reaction = normalize(reactionEvent.reaction());
      if (eventId.isEmpty()
          || sender.isEmpty()
          || targetMessageId.isEmpty()
          || reaction.isEmpty()
          || tombstones.contains(eventId)) {
        continue;
      }
      session.rememberReactionEvent(rid, eventId, targetMessageId, reaction, sender);
    }
  }

  private Completable sendRawRedact(String serverId, RawCommand raw) {
    String target = argOrBlank(raw, 0, "REDACT requires target and message id");
    String targetEventId = argOrBlank(raw, 1, "REDACT requires target and message id");
    String reason = joinArgs(raw.arguments(), 2);
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              MatrixSession session = sessionsByServer.get(sid);
              if (session == null) {
                throw new BackendNotAvailableException(
                    IrcProperties.Server.Backend.MATRIX,
                    "redact",
                    sid,
                    backendAvailabilityReason(sid));
              }
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveMutationRoomId(sid, target, server, session);
              String txnId = nextTransactionId(sid);
              MatrixRoomMessageSender.SendResult result =
                  roomMessageSender.sendRoomRedaction(
                      sid,
                      server,
                      session.accessToken,
                      roomId,
                      normalize(targetEventId),
                      txnId,
                      reason);
              if (!result.accepted()) {
                throw new IllegalStateException(
                    "Matrix room redaction failed at "
                        + result.endpoint()
                        + ": "
                        + result.detail());
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private Completable sendRawChatHistory(String serverId, RawCommand raw) {
    List<String> args = raw == null ? List.of() : raw.arguments();
    if (args.isEmpty()) {
      throw new IllegalArgumentException(
          "CHATHISTORY requires a mode (BEFORE|LATEST|BETWEEN|AROUND)");
    }
    String mode = normalize(args.get(0)).toUpperCase(Locale.ROOT);
    return switch (mode) {
      case "BEFORE" -> sendRawChatHistoryBefore(serverId, args);
      case "LATEST" -> sendRawChatHistoryLatest(serverId, args);
      case "BETWEEN" -> sendRawChatHistoryBetween(serverId, args);
      case "AROUND" -> sendRawChatHistoryAround(serverId, args);
      default ->
          throw new IllegalArgumentException(
              "CHATHISTORY mode must be BEFORE, LATEST, BETWEEN, or AROUND");
    };
  }

  private Completable sendRawChatHistoryBefore(String serverId, List<String> args) {
    if (args.size() < 3 || args.size() > 4) {
      throw new IllegalArgumentException("CHATHISTORY BEFORE requires <target> <selector> [limit]");
    }
    String target = normalize(args.get(1));
    String selector = normalize(args.get(2));
    if (target.isEmpty() || selector.isEmpty()) {
      throw new IllegalArgumentException("CHATHISTORY BEFORE requires <target> <selector> [limit]");
    }
    int limit = args.size() > 3 ? parseRawChatHistoryLimit(args.get(3)) : 50;
    return requestChatHistoryBefore(serverId, target, selector, limit);
  }

  private Completable sendRawChatHistoryLatest(String serverId, List<String> args) {
    if (args.size() < 2 || args.size() > 4) {
      throw new IllegalArgumentException(
          "CHATHISTORY LATEST requires <target> [selector|*] [limit]");
    }
    String target = normalize(args.get(1));
    if (target.isEmpty()) {
      throw new IllegalArgumentException(
          "CHATHISTORY LATEST requires <target> [selector|*] [limit]");
    }

    String selector = "*";
    int limit = 50;
    if (args.size() >= 3) {
      String third = normalize(args.get(2));
      if (args.size() == 3 && looksLikeInteger(third)) {
        limit = parseRawChatHistoryLimit(third);
      } else {
        selector = third;
        if (selector.isEmpty()) {
          throw new IllegalArgumentException(
              "CHATHISTORY LATEST requires <target> [selector|*] [limit]");
        }
        if (args.size() >= 4) {
          limit = parseRawChatHistoryLimit(args.get(3));
        }
      }
    }
    return requestChatHistoryLatest(serverId, target, selector, limit);
  }

  private Completable sendRawChatHistoryBetween(String serverId, List<String> args) {
    if (args.size() < 4 || args.size() > 5) {
      throw new IllegalArgumentException(
          "CHATHISTORY BETWEEN requires <target> <start-selector> <end-selector> [limit]");
    }
    String target = normalize(args.get(1));
    String startSelector = normalize(args.get(2));
    String endSelector = normalize(args.get(3));
    if (target.isEmpty() || startSelector.isEmpty() || endSelector.isEmpty()) {
      throw new IllegalArgumentException(
          "CHATHISTORY BETWEEN requires <target> <start-selector> <end-selector> [limit]");
    }
    int limit = args.size() > 4 ? parseRawChatHistoryLimit(args.get(4)) : 50;
    return requestChatHistoryBetween(serverId, target, startSelector, endSelector, limit);
  }

  private Completable sendRawChatHistoryAround(String serverId, List<String> args) {
    if (args.size() < 3 || args.size() > 4) {
      throw new IllegalArgumentException("CHATHISTORY AROUND requires <target> <selector> [limit]");
    }
    String target = normalize(args.get(1));
    String selector = normalize(args.get(2));
    if (target.isEmpty() || selector.isEmpty()) {
      throw new IllegalArgumentException("CHATHISTORY AROUND requires <target> <selector> [limit]");
    }
    int limit = args.size() > 3 ? parseRawChatHistoryLimit(args.get(3)) : 50;
    return requestChatHistoryAround(serverId, target, selector, limit);
  }

  private static String argOrBlank(RawCommand raw, int index, String error) {
    if (raw == null || raw.arguments() == null || index < 0 || index >= raw.arguments().size()) {
      throw new IllegalArgumentException(error);
    }
    String value = normalize(raw.arguments().get(index));
    if (value.isEmpty()) {
      throw new IllegalArgumentException(error);
    }
    return value;
  }

  private static String joinArgs(List<String> args, int startIndex) {
    if (args == null || args.isEmpty() || startIndex >= args.size()) {
      return "";
    }
    int start = Math.max(0, startIndex);
    return args.subList(start, args.size()).stream()
        .map(MatrixIrcClientService::normalize)
        .filter(s -> !s.isEmpty())
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private static String normalizeTypingState(String state) {
    String normalized = normalize(state).toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return "";
    }
    return switch (normalized) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }

  private static int parseRawChatHistoryLimit(String rawValue) {
    String token = normalize(rawValue);
    if (!looksLikeInteger(token)) {
      throw new IllegalArgumentException("CHATHISTORY limit must be a positive integer");
    }
    try {
      int parsed = Integer.parseInt(token);
      if (parsed <= 0) {
        throw new IllegalArgumentException("CHATHISTORY limit must be a positive integer");
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("CHATHISTORY limit must be a positive integer", ex);
    }
  }

  private static boolean looksLikeInteger(String rawValue) {
    String token = normalize(rawValue);
    if (token.isEmpty()) {
      return false;
    }
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (i == 0 && c == '+') {
        if (token.length() == 1) return false;
        continue;
      }
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static String rawTypingTagValue(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return "";
    }
    String plus = normalize(tags.get("+typing"));
    if (!plus.isEmpty()) {
      return plus;
    }
    return normalize(tags.get("typing"));
  }

  private static String rawMatrixMsgType(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return "";
    }
    return firstNonBlank(tags.get(RAW_TAG_MATRIX_MSGTYPE), tags.get("matrix.msgtype"));
  }

  private static String rawMatrixMediaUrl(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return "";
    }
    return firstNonBlank(
        tags.get(RAW_TAG_MATRIX_MEDIA_URL),
        tags.get("matrix.url"),
        tags.get("matrix.media_url"),
        tags.get("matrix/url"));
  }

  private static String rawMatrixUploadPath(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return "";
    }
    return firstNonBlank(
        tags.get(RAW_TAG_MATRIX_UPLOAD_PATH),
        tags.get(TAG_MATRIX_UPLOAD_PATH),
        tags.get("matrix.upload-path"),
        tags.get("matrix/upload-path"));
  }

  private static String firstNonBlank(String... values) {
    if (values == null || values.length == 0) {
      return "";
    }
    for (String value : values) {
      String token = normalize(value);
      if (!token.isEmpty()) {
        return token;
      }
    }
    return "";
  }

  private static boolean isMatrixMediaMsgType(String msgType) {
    return MATRIX_MEDIA_MSGTYPES.contains(normalize(msgType));
  }

  private static Map<String, String> parseRawTags(String rawLine) {
    String line = Objects.toString(rawLine, "");
    if (!line.startsWith("@")) {
      return Map.of();
    }
    int firstSpace = line.indexOf(' ');
    if (firstSpace <= 1) {
      return Map.of();
    }

    String rawTags = line.substring(1, firstSpace);
    if (rawTags.isEmpty()) {
      return Map.of();
    }

    Map<String, String> tags = new HashMap<>();
    for (String token : rawTags.split(";")) {
      String entry = normalize(token);
      if (entry.isEmpty()) continue;
      int eq = entry.indexOf('=');
      String key = eq < 0 ? entry : normalize(entry.substring(0, eq));
      if (key.isEmpty()) continue;
      String value = eq < 0 ? "" : entry.substring(eq + 1);
      tags.put(key, value);
      if (key.startsWith("+") && key.length() > 1) {
        tags.put(key.substring(1), value);
      }
    }
    if (tags.isEmpty()) {
      return Map.of();
    }
    return Map.copyOf(tags);
  }

  private Completable rawCommandUnsupported(String serverId, String command) {
    String sid = normalize(serverId);
    String cmd = normalize(command).toUpperCase(Locale.ROOT);
    if (cmd.isEmpty()) {
      cmd = "<blank>";
    }
    return Completable.error(
        new BackendNotAvailableException(
            IrcProperties.Server.Backend.MATRIX,
            "raw",
            sid,
            "raw command " + cmd + " is not supported by Matrix backend"));
  }

  private static RawCommand parseRawCommand(String rawLine) {
    String line = normalize(rawLine);
    if (line.isEmpty()) {
      return new RawCommand("", List.of());
    }
    if (line.startsWith("@")) {
      int firstSpace = line.indexOf(' ');
      line = firstSpace < 0 ? "" : normalize(line.substring(firstSpace + 1));
    }
    if (line.startsWith(":")) {
      int firstSpace = line.indexOf(' ');
      line = firstSpace < 0 ? "" : normalize(line.substring(firstSpace + 1));
    }
    if (line.isEmpty()) {
      return new RawCommand("", List.of());
    }

    int firstSpace = line.indexOf(' ');
    String command = firstSpace < 0 ? line : line.substring(0, firstSpace);
    String params = firstSpace < 0 ? "" : normalize(line.substring(firstSpace + 1));
    return new RawCommand(normalize(command).toUpperCase(Locale.ROOT), parseRawArgs(params));
  }

  private static List<String> parseRawArgs(String params) {
    String rest = normalize(params);
    if (rest.isEmpty()) {
      return List.of();
    }

    java.util.ArrayList<String> args = new java.util.ArrayList<>();
    if (rest.startsWith(":")) {
      args.add(rest.substring(1));
      return List.copyOf(args);
    }

    int trailingMarker = rest.indexOf(" :");
    String head = trailingMarker < 0 ? rest : rest.substring(0, trailingMarker);
    String trailing = trailingMarker < 0 ? "" : rest.substring(trailingMarker + 2);

    for (String token : head.split("\\s+")) {
      String arg = normalize(token);
      if (!arg.isEmpty()) {
        args.add(arg);
      }
    }
    if (trailingMarker >= 0) {
      args.add(trailing);
    }
    return args.isEmpty() ? List.of() : List.copyOf(args);
  }

  private String nextTransactionId(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty()) sid = "matrix";
    long seq = transactionSequence.incrementAndGet();
    long nowMs = System.currentTimeMillis();
    return sid + "-" + nowMs + "-" + seq;
  }

  private static boolean looksLikeMatrixRoomId(String token) {
    String value = normalize(token);
    if (!value.startsWith("!")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static boolean looksLikeMatrixUserId(String token) {
    String value = normalize(token);
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static boolean looksLikeMatrixRoomAlias(String token) {
    String value = normalize(token);
    if (!value.startsWith("#")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private String normalizeJoinTarget(String rawTarget, IrcProperties.Server server) {
    String target = normalize(rawTarget);
    if (target.isEmpty()) {
      throw new IllegalArgumentException("channel is blank");
    }
    if (looksLikeMatrixRoomId(target) || looksLikeMatrixRoomAlias(target)) {
      return target;
    }
    if (target.startsWith("#") && target.indexOf(':') < 0) {
      String host = eventHost(server);
      if (!host.isEmpty()) {
        return target + ":" + host;
      }
    }
    throw new IllegalArgumentException("target is not a Matrix room id or alias");
  }

  private String resolveAliasOrRoomId(
      String serverId, String rawTarget, IrcProperties.Server server, MatrixSession session) {
    String target = normalizeJoinTarget(rawTarget, server);
    if (looksLikeMatrixRoomId(target)) {
      return target;
    }

    String mapped = session == null ? "" : session.roomForAlias(target);
    if (!mapped.isEmpty()) {
      return mapped;
    }

    MatrixRoomDirectoryClient.ResolveResult resolved =
        roomDirectoryClient.resolveRoomAlias(serverId, server, session.accessToken, target);
    if (!resolved.resolved()) {
      throw new IllegalStateException(
          "Matrix room alias lookup failed at " + resolved.endpoint() + ": " + resolved.detail());
    }

    String roomId = normalize(resolved.roomId());
    if (!looksLikeMatrixRoomId(roomId)) {
      throw new IllegalStateException("Matrix room alias lookup returned invalid room id");
    }
    session.rememberJoinedAlias(target, roomId);
    return roomId;
  }

  private String resolvePartRoomId(
      String serverId, String rawTarget, IrcProperties.Server server, MatrixSession session) {
    return resolveAliasOrRoomId(serverId, rawTarget, server, session);
  }

  private String resolveHistoryRoomId(
      String serverId, String requestedTarget, IrcProperties.Server server, MatrixSession session) {
    String target = normalize(requestedTarget);
    if (looksLikeMatrixUserId(target)) {
      String roomId = session == null ? "" : session.roomForPeer(target);
      if (roomId.isEmpty() && session != null) {
        MatrixSyncClient.SyncResult sync =
            syncClient.sync(serverId, server, session.accessToken, "", SYNC_TIMEOUT_MS);
        if (sync != null && sync.success()) {
          String nextBatch = normalize(sync.nextBatch());
          if (!nextBatch.isEmpty()) {
            session.sinceToken.set(nextBatch);
          }
          session.rememberDirectRooms(sync.directPeerByRoom());
          roomId = session.roomForPeer(target);
        }
      }
      if (roomId.isEmpty()) {
        throw new IllegalStateException("Matrix direct room is unknown for target");
      }
      return roomId;
    }
    return resolveAliasOrRoomId(serverId, target, server, session);
  }

  private String resolveRealtimeSignalRoomId(
      String serverId,
      String requestedTarget,
      IrcProperties.Server server,
      MatrixSession session,
      boolean resolveAliasByLookup) {
    String target = normalize(requestedTarget);
    if (target.isEmpty()) {
      throw new IllegalArgumentException("target is blank");
    }
    if (looksLikeMatrixUserId(target)) {
      return session == null ? "" : session.roomForPeer(target);
    }
    if (looksLikeMatrixRoomId(target)) {
      return target;
    }
    String alias = normalizeJoinTarget(target, server);
    if (!looksLikeMatrixRoomAlias(alias)) {
      return "";
    }
    String cachedRoom = session == null ? "" : session.roomForAlias(alias);
    if (!cachedRoom.isEmpty()) {
      return cachedRoom;
    }
    if (!resolveAliasByLookup) {
      return "";
    }
    return resolveAliasOrRoomId(serverId, alias, server, session);
  }

  private String resolveMutationRoomId(
      String serverId, String requestedTarget, IrcProperties.Server server, MatrixSession session) {
    String target = normalize(requestedTarget);
    if (target.isEmpty()) {
      throw new IllegalArgumentException("target is blank");
    }
    if (looksLikeMatrixUserId(target)) {
      String roomId = session == null ? "" : session.roomForPeer(target);
      if (roomId.isEmpty()) {
        throw new IllegalStateException("Matrix direct room is unknown for target");
      }
      return roomId;
    }
    return resolveAliasOrRoomId(serverId, target, server, session);
  }

  private String resolveHistoryFromToken(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      long beforeEpochMs) {
    MatrixSession.HistoryCursor cursor = session == null ? null : session.historyCursor(roomId);
    if (cursor != null && !cursor.nextToken().isEmpty() && beforeEpochMs < cursor.beforeEpochMs()) {
      return cursor.nextToken();
    }

    String anchor = session == null ? "" : normalize(session.sinceToken.get());
    if (!anchor.isEmpty()) {
      return anchor;
    }

    MatrixSyncClient.SyncResult sync =
        syncClient.sync(serverId, server, session.accessToken, "", SYNC_TIMEOUT_MS);
    if (sync != null && sync.success()) {
      String nextBatch = normalize(sync.nextBatch());
      if (!nextBatch.isEmpty()) {
        session.sinceToken.set(nextBatch);
        anchor = nextBatch;
      }
      session.rememberDirectRooms(sync.directPeerByRoom());
    }
    return anchor;
  }

  private List<ChatHistoryEntry> fetchHistoryEntriesBefore(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String requestedTarget,
      String roomId,
      long beforeEpochMs,
      int requestedLimit) {
    String fromToken = resolveHistoryFromToken(serverId, server, session, roomId, beforeEpochMs);
    if (fromToken.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }

    MatrixRoomHistoryClient.HistoryResult result =
        roomHistoryClient.fetchMessagesBefore(
            serverId, server, session.accessToken, roomId, fromToken, requestedLimit);
    if (result == null) {
      throw new IllegalStateException("Matrix history fetch returned no result");
    }
    if (!result.success()) {
      throw new IllegalStateException(
          "Matrix history fetch failed at " + result.endpoint() + ": " + result.detail());
    }

    String nextToken = normalize(result.endToken());
    if (nextToken.isEmpty()) {
      nextToken = fromToken;
    }
    session.rememberHistoryCursor(roomId, nextToken, beforeEpochMs);
    session.rememberHistoryEvents(roomId, result.events());
    return toHistoryEntries(requestedTarget, roomId, result.events());
  }

  private String resolveForwardCursorForTimestamp(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String roomId,
      long targetEpochMs) {
    long targetMs = targetEpochMs <= 0L ? System.currentTimeMillis() : targetEpochMs;
    String cursor = resolveHistoryFromToken(serverId, server, session, roomId, targetMs);
    if (cursor.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }

    String scanCursor = cursor;
    for (int page = 0; page < TIMESTAMP_CURSOR_MAX_PAGES; page++) {
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              serverId,
              server,
              session.accessToken,
              roomId,
              scanCursor,
              TIMESTAMP_CURSOR_SCAN_PAGE_LIMIT);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new IllegalStateException(
            "Matrix history timestamp scan failed at "
                + result.endpoint()
                + ": "
                + result.detail());
      }

      session.rememberHistoryEvents(roomId, result.events());

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty()) {
        nextToken = scanCursor;
      }
      session.rememberHistoryCursor(roomId, nextToken, targetMs);

      long minTs = minHistoryTimestamp(result.events());
      long maxTs = maxHistoryTimestamp(result.events());
      if (minTs > 0L && maxTs > 0L && targetMs >= minTs && targetMs <= maxTs) {
        return nextToken;
      }
      if (minTs > 0L && targetMs > maxTs) {
        return scanCursor;
      }

      if (nextToken.equals(scanCursor)) {
        return nextToken;
      }
      scanCursor = nextToken;
    }

    return scanCursor;
  }

  private List<ChatHistoryEntry> fetchHistoryEntriesForwardFromCursor(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String requestedTarget,
      String roomId,
      String fromToken,
      Instant fromInclusive,
      Instant untilExclusive,
      int limit) {
    String cursor = normalize(fromToken);
    if (cursor.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }
    int requestedLimit = normalizeHistoryLimit(limit);
    List<ChatHistoryEntry> out = new java.util.ArrayList<>(requestedLimit);
    Set<String> seenMessageIds = new LinkedHashSet<>();
    Set<Integer> seenFallbackFingerprints = new LinkedHashSet<>();

    for (int page = 0; page < TIMESTAMP_CURSOR_MAX_PAGES && out.size() < requestedLimit; page++) {
      int remaining = requestedLimit - out.size();
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesAfter(
              serverId, server, session.accessToken, roomId, cursor, remaining);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new IllegalStateException(
            "Matrix history forward fetch failed at " + result.endpoint() + ": " + result.detail());
      }

      session.rememberHistoryEvents(roomId, result.events());
      List<ChatHistoryEntry> pageEntries =
          toHistoryEntries(requestedTarget, roomId, result.events());
      for (ChatHistoryEntry entry : pageEntries) {
        if (entry == null) continue;
        Instant at = entry.at();
        if (fromInclusive != null && at.isBefore(fromInclusive)) continue;
        if (untilExclusive != null && !at.isBefore(untilExclusive)) {
          return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
        out.add(entry);
        if (out.size() >= requestedLimit) {
          return List.copyOf(out);
        }
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        break;
      }
      cursor = nextToken;
    }

    if (out.isEmpty()) {
      return List.of();
    }
    return List.copyOf(out);
  }

  private List<ChatHistoryEntry> fetchHistoryEntriesBackwardFromCursor(
      String serverId,
      IrcProperties.Server server,
      MatrixSession session,
      String requestedTarget,
      String roomId,
      String fromToken,
      Instant fromInclusive,
      Instant untilExclusive,
      int limit) {
    String cursor = normalize(fromToken);
    if (cursor.isEmpty()) {
      throw new IllegalStateException("Matrix history pagination token is unavailable");
    }
    int requestedLimit = normalizeHistoryLimit(limit);
    List<ChatHistoryEntry> out = new java.util.ArrayList<>(requestedLimit);
    Set<String> seenMessageIds = new LinkedHashSet<>();
    Set<Integer> seenFallbackFingerprints = new LinkedHashSet<>();

    for (int page = 0; page < TIMESTAMP_CURSOR_MAX_PAGES && out.size() < requestedLimit; page++) {
      int remaining = requestedLimit - out.size();
      MatrixRoomHistoryClient.HistoryResult result =
          roomHistoryClient.fetchMessagesBefore(
              serverId, server, session.accessToken, roomId, cursor, remaining);
      if (result == null) {
        break;
      }
      if (!result.success()) {
        throw new IllegalStateException(
            "Matrix history backward fetch failed at "
                + result.endpoint()
                + ": "
                + result.detail());
      }

      session.rememberHistoryEvents(roomId, result.events());
      List<ChatHistoryEntry> pageEntries =
          toHistoryEntries(requestedTarget, roomId, result.events());
      for (ChatHistoryEntry entry : pageEntries) {
        if (entry == null) continue;
        Instant at = entry.at();
        if (fromInclusive != null && at.isBefore(fromInclusive)) continue;
        if (untilExclusive != null && !at.isBefore(untilExclusive)) continue;
        if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
        out.add(entry);
        if (out.size() >= requestedLimit) {
          out.sort(Comparator.comparing(ChatHistoryEntry::at));
          return List.copyOf(out);
        }
      }

      String nextToken = normalize(result.endToken());
      if (nextToken.isEmpty() || nextToken.equals(cursor)) {
        break;
      }
      cursor = nextToken;
      session.rememberHistoryCursor(roomId, cursor, System.currentTimeMillis());
    }

    if (out.isEmpty()) {
      return List.of();
    }
    out.sort(Comparator.comparing(ChatHistoryEntry::at));
    return List.copyOf(out);
  }

  private static List<ChatHistoryEntry> mergeHistoryCandidates(
      List<ChatHistoryEntry> first, List<ChatHistoryEntry> second) {
    if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
      return List.of();
    }
    Set<String> seenMessageIds = new LinkedHashSet<>();
    Set<Integer> seenFallbackFingerprints = new LinkedHashSet<>();
    List<ChatHistoryEntry> merged = new java.util.ArrayList<>();

    for (ChatHistoryEntry entry : first == null ? List.<ChatHistoryEntry>of() : first) {
      if (entry == null) continue;
      if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
      merged.add(entry);
    }
    for (ChatHistoryEntry entry : second == null ? List.<ChatHistoryEntry>of() : second) {
      if (entry == null) continue;
      if (!rememberHistoryEntry(entry, seenMessageIds, seenFallbackFingerprints)) continue;
      merged.add(entry);
    }

    if (merged.isEmpty()) {
      return List.of();
    }
    merged.sort(Comparator.comparing(ChatHistoryEntry::at));
    return List.copyOf(merged);
  }

  private static boolean rememberHistoryEntry(
      ChatHistoryEntry entry, Set<String> seenMessageIds, Set<Integer> seenFallbackFingerprints) {
    if (entry == null) {
      return false;
    }
    String msgId = normalize(entry.messageId());
    if (!msgId.isEmpty()) {
      return seenMessageIds.add(msgId);
    }
    return seenFallbackFingerprints.add(historyEntryFingerprint(entry));
  }

  private static int historyEntryFingerprint(ChatHistoryEntry entry) {
    Instant at = entry.at();
    return Objects.hash(
        at == null ? 0L : at.toEpochMilli(),
        entry.kind(),
        normalize(entry.target()),
        normalize(entry.from()),
        Objects.toString(entry.text(), ""));
  }

  private void emitHistoryBatch(String serverId, String target, List<ChatHistoryEntry> entries) {
    String sid = normalize(serverId);
    String normalizedTarget = normalize(target);
    if (sid.isEmpty() || normalizedTarget.isEmpty()) return;
    List<ChatHistoryEntry> safeEntries = entries == null ? List.of() : entries;
    String batchId = "matrix-history-" + nextTransactionId(sid);
    bus.onNext(
        new ServerIrcEvent(
            sid,
            new IrcEvent.ChatHistoryBatchReceived(
                Instant.now(), normalizedTarget, batchId, safeEntries)));
  }

  private static int normalizeHistoryLimit(int limit) {
    int normalized = limit <= 0 ? 50 : limit;
    return Math.max(1, Math.min(normalized, 200));
  }

  private static long minHistoryTimestamp(List<MatrixRoomHistoryClient.RoomHistoryEvent> events) {
    if (events == null || events.isEmpty()) return 0L;
    long min = Long.MAX_VALUE;
    boolean found = false;
    for (MatrixRoomHistoryClient.RoomHistoryEvent event : events) {
      if (event == null) continue;
      long ts = event.originServerTs();
      if (ts <= 0L) continue;
      if (ts < min) min = ts;
      found = true;
    }
    return found ? min : 0L;
  }

  private static long maxHistoryTimestamp(List<MatrixRoomHistoryClient.RoomHistoryEvent> events) {
    if (events == null || events.isEmpty()) return 0L;
    long max = 0L;
    for (MatrixRoomHistoryClient.RoomHistoryEvent event : events) {
      if (event == null) continue;
      long ts = event.originServerTs();
      if (ts > max) max = ts;
    }
    return max;
  }

  private static List<ChatHistoryEntry> selectEntriesAroundTimestamp(
      List<ChatHistoryEntry> entries, Instant centerAt, int limit) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    Instant center = centerAt == null ? Instant.now() : centerAt;
    int requestedLimit = normalizeHistoryLimit(limit);

    List<ChatHistoryEntry> safe = entries.stream().filter(Objects::nonNull).toList();
    if (safe.isEmpty()) {
      return List.of();
    }

    long centerMs = center.toEpochMilli();
    List<ChatHistoryEntry> ranked = new java.util.ArrayList<>(safe);
    ranked.sort(
        Comparator.comparingLong(
                (ChatHistoryEntry e) -> distanceAbs(e.at().toEpochMilli(), centerMs))
            .thenComparing(ChatHistoryEntry::at)
            .thenComparing(
                ChatHistoryEntry::messageId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

    if (ranked.size() > requestedLimit) {
      ranked = new java.util.ArrayList<>(ranked.subList(0, requestedLimit));
    }
    ranked.sort(Comparator.comparing(ChatHistoryEntry::at));
    return List.copyOf(ranked);
  }

  private static long distanceAbs(long left, long right) {
    long diff = left - right;
    return diff < 0L ? -diff : diff;
  }

  private static List<ChatHistoryEntry> toHistoryEntries(
      String requestedTarget,
      String roomId,
      List<MatrixRoomHistoryClient.RoomHistoryEvent> historyEvents) {
    if (historyEvents == null || historyEvents.isEmpty()) {
      return List.of();
    }
    String target = normalize(requestedTarget);
    String rid = normalize(roomId);
    if (target.isEmpty() || rid.isEmpty()) {
      return List.of();
    }

    List<ChatHistoryEntry> entries = new java.util.ArrayList<>(historyEvents.size());
    for (MatrixRoomHistoryClient.RoomHistoryEvent event : historyEvents) {
      if (event == null) continue;
      String sender = normalize(event.sender());
      String body = Objects.toString(event.body(), "");
      if (sender.isEmpty() || body.trim().isEmpty()) continue;

      String msgType = normalize(event.msgType());
      if (msgType.isEmpty()) {
        msgType = "m.text";
      }
      String mediaUrl = normalize(event.mediaUrl());
      ChatHistoryEntry.Kind kind =
          switch (msgType) {
            case "m.emote" -> ChatHistoryEntry.Kind.ACTION;
            case "m.notice" -> ChatHistoryEntry.Kind.NOTICE;
            default -> ChatHistoryEntry.Kind.PRIVMSG;
          };

      long ts = event.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      String messageId = normalize(event.eventId());
      String replyToMessageId = normalize(event.replyToEventId());
      Map<String, String> tags =
          withTag(
              withTag(
                  Map.of(TAG_MATRIX_ROOM_ID, rid, TAG_MATRIX_MSGTYPE, msgType),
                  TAG_MATRIX_MEDIA_URL,
                  mediaUrl),
              TAG_DRAFT_REPLY,
              replyToMessageId);
      entries.add(new ChatHistoryEntry(at, kind, target, sender, body, messageId, tags));
    }
    if (entries.isEmpty()) {
      return List.of();
    }
    return List.copyOf(entries);
  }

  private static List<IrcEvent.NickInfo> toNickInfos(
      List<MatrixRoomRosterClient.JoinedMember> members) {
    if (members == null || members.isEmpty()) {
      return List.of();
    }
    return members.stream()
        .map(MatrixIrcClientService::toNickInfo)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private static IrcEvent.NickInfo toNickInfo(MatrixRoomRosterClient.JoinedMember member) {
    if (member == null) return null;
    String userId = normalize(member.userId());
    if (userId.isEmpty()) return null;
    String displayName = normalize(member.displayName());
    if (displayName.isEmpty()) {
      displayName = null;
    }
    return new IrcEvent.NickInfo(
        userId,
        "",
        userId,
        IrcEvent.AwayState.UNKNOWN,
        null,
        IrcEvent.AccountState.UNKNOWN,
        null,
        displayName);
  }

  private static List<String> toWhoisLines(MatrixUserProfileClient.ProfileResult profile) {
    if (profile == null) {
      return List.of("Matrix profile is unavailable");
    }

    String userId = normalize(profile.userId());
    if (userId.isEmpty()) {
      return List.of("Matrix profile is unavailable");
    }

    String displayName = normalize(profile.displayName());
    String avatarUrl = normalize(profile.avatarUrl());

    List<String> lines = new java.util.ArrayList<>(4);
    lines.add("user id: " + userId);
    if (!displayName.isEmpty()) {
      lines.add("display name: " + displayName);
    }
    if (!avatarUrl.isEmpty()) {
      lines.add("avatar: " + avatarUrl);
    }
    if (lines.size() == 1) {
      lines.add("no profile fields published");
    }
    return List.copyOf(lines);
  }

  private String resolveDirectRoomId(
      String serverId, IrcProperties.Server server, MatrixSession session, String peerUserId) {
    String peer = normalize(peerUserId);
    String cachedRoomId = session.roomForPeer(peer);
    if (!cachedRoomId.isEmpty()) {
      return cachedRoomId;
    }

    MatrixDirectRoomResolver.ResolveResult resolve =
        directRoomResolver.resolveDirectRoom(serverId, server, session.accessToken, peer);
    if (!resolve.resolved()) {
      throw new IllegalStateException(
          "Matrix direct room resolution failed at "
              + resolve.endpoint()
              + ": "
              + resolve.detail());
    }

    String roomId = normalize(resolve.roomId());
    if (!looksLikeMatrixRoomId(roomId)) {
      throw new IllegalStateException("Matrix direct room resolution returned invalid room id");
    }
    session.rememberDirectRoom(peer, roomId);
    return roomId;
  }

  private void emitLocalEchoEvent(
      String serverId, MatrixSession session, String roomId, String text, String eventId) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId);
    String rid = normalize(roomId);
    if (sid.isEmpty() || sender.isEmpty() || rid.isEmpty()) return;

    String raw = Objects.toString(text, "");
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    if (isCtcpAction(raw)) {
      String action = extractCtcpAction(raw);
      bus.onNext(
          new ServerIrcEvent(
              sid,
              new IrcEvent.ChannelAction(
                  now, rid, sender, action, mid, Map.of(TAG_MATRIX_MSGTYPE, "m.emote"))));
      return;
    }

    bus.onNext(
        new ServerIrcEvent(
            sid,
            new IrcEvent.ChannelMessage(
                now, rid, sender, raw, mid, Map.of(TAG_MATRIX_MSGTYPE, "m.text"))));
  }

  private void emitPrivateLocalEchoEvent(
      String serverId,
      MatrixSession session,
      String peerUserId,
      String roomId,
      String text,
      String eventId) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId);
    String peer = normalize(peerUserId);
    String rid = normalize(roomId);
    if (sid.isEmpty() || sender.isEmpty() || peer.isEmpty() || rid.isEmpty()) return;

    String raw = Objects.toString(text, "");
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    if (isCtcpAction(raw)) {
      String action = extractCtcpAction(raw);
      bus.onNext(
          new ServerIrcEvent(
              sid,
              new IrcEvent.PrivateAction(
                  now, sender, action, mid, privateMessageTags(peer, rid, "m.emote"))));
      return;
    }

    bus.onNext(
        new ServerIrcEvent(
            sid,
            new IrcEvent.PrivateMessage(
                now, sender, raw, mid, privateMessageTags(peer, rid, "m.text"))));
  }

  private void emitLocalMediaEchoEvent(
      String serverId,
      MatrixSession session,
      String roomId,
      String text,
      String eventId,
      String msgType,
      String mediaUrl) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId);
    String rid = normalize(roomId);
    String type = normalize(msgType);
    String mediaRef = normalize(mediaUrl);
    if (sid.isEmpty() || sender.isEmpty() || rid.isEmpty() || type.isEmpty()) return;

    String raw = Objects.toString(text, "");
    String rendered = raw.trim().isEmpty() ? mediaRef : raw;
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    Map<String, String> tags =
        withTag(Map.of(TAG_MATRIX_MSGTYPE, type), TAG_MATRIX_MEDIA_URL, mediaRef);
    bus.onNext(
        new ServerIrcEvent(
            sid, new IrcEvent.ChannelMessage(now, rid, sender, rendered, mid, tags)));
  }

  private void emitPrivateLocalMediaEchoEvent(
      String serverId,
      MatrixSession session,
      String peerUserId,
      String roomId,
      String text,
      String eventId,
      String msgType,
      String mediaUrl) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId);
    String peer = normalize(peerUserId);
    String rid = normalize(roomId);
    String type = normalize(msgType);
    String mediaRef = normalize(mediaUrl);
    if (sid.isEmpty() || sender.isEmpty() || peer.isEmpty() || rid.isEmpty() || type.isEmpty())
      return;

    String raw = Objects.toString(text, "");
    String rendered = raw.trim().isEmpty() ? mediaRef : raw;
    Instant now = Instant.now();
    String mid = normalize(eventId);
    session.rememberLatestRoomEvent(rid, mid);

    Map<String, String> tags =
        withTag(privateMessageTags(peer, rid, type), TAG_MATRIX_MEDIA_URL, mediaRef);
    bus.onNext(
        new ServerIrcEvent(sid, new IrcEvent.PrivateMessage(now, sender, rendered, mid, tags)));
  }

  private void emitLocalNoticeEvent(
      String serverId,
      MatrixSession session,
      String target,
      String text,
      String eventId,
      Map<String, String> tags) {
    if (session == null) return;
    String sid = normalize(serverId);
    String sender = normalize(session.userId);
    String noticeTarget = normalize(target);
    if (sid.isEmpty() || sender.isEmpty() || noticeTarget.isEmpty()) return;

    String raw = Objects.toString(text, "");
    String mid = normalize(eventId);
    Map<String, String> safeTags = tags == null ? Map.of() : tags;
    String roomId = normalize(safeTags.get(TAG_MATRIX_ROOM_ID));
    if (roomId.isEmpty() && looksLikeMatrixRoomId(noticeTarget)) {
      roomId = noticeTarget;
    }
    session.rememberLatestRoomEvent(roomId, mid);

    bus.onNext(
        new ServerIrcEvent(
            sid, new IrcEvent.Notice(Instant.now(), sender, noticeTarget, raw, mid, safeTags)));
  }

  private static boolean isCtcpAction(String text) {
    String raw = Objects.toString(text, "");
    return raw.startsWith(CTCP_ACTION_PREFIX)
        && raw.endsWith(CTCP_SUFFIX)
        && raw.length() > (CTCP_ACTION_PREFIX.length() + CTCP_SUFFIX.length());
  }

  private static String extractCtcpAction(String text) {
    String raw = Objects.toString(text, "");
    if (!isCtcpAction(raw)) return raw;
    String action = raw.substring(CTCP_ACTION_PREFIX.length(), raw.length() - 1).trim();
    return action.isEmpty() ? raw : action;
  }

  private void startSyncPolling(
      String serverId, IrcProperties.Server server, MatrixSession session) {
    if (session == null) return;
    disposeSyncTask(session);
    Disposable task =
        RxVirtualSchedulers.io()
            .schedulePeriodicallyDirect(
                () -> pollSyncOnce(serverId, server, session),
                0L,
                SYNC_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    session.syncTask.set(task);
  }

  private void pollSyncOnce(String serverId, IrcProperties.Server server, MatrixSession session) {
    if (!isActiveSession(serverId, session)) return;

    String since = session.sinceToken.get();
    MatrixSyncClient.SyncResult result =
        syncClient.sync(serverId, server, session.accessToken, since, SYNC_TIMEOUT_MS);
    if (result == null || !result.success()) return;

    String nextBatch = normalize(result.nextBatch());
    if (!nextBatch.isEmpty()) {
      session.sinceToken.set(nextBatch);
    }
    session.rememberDirectRooms(result.directPeerByRoom());
    emitSyncTimelineEvents(serverId, session, result.events());
    emitSyncMembershipEvents(serverId, session, result.membershipEvents());
    emitSyncMutationEvents(
        serverId,
        session,
        result.messageEditEvents(),
        result.reactionEvents(),
        result.redactionEvents());
    emitSyncSignalEvents(serverId, session, result.typingEvents(), result.readReceipts());
  }

  private boolean isActiveSession(String serverId, MatrixSession session) {
    if (session == null || session.closed.get()) return false;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return false;
    return sessionsByServer.get(sid) == session;
  }

  private void emitSyncTimelineEvents(
      String serverId, MatrixSession session, List<MatrixSyncClient.RoomTimelineEvent> events) {
    if (events == null || events.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomTimelineEvent event : events) {
      if (event == null) continue;
      String roomId = normalize(event.roomId());
      String sender = normalize(event.sender());
      String body = Objects.toString(event.body(), "");
      if (roomId.isEmpty() || sender.isEmpty() || body.trim().isEmpty()) continue;

      String msgType = normalize(event.msgType());
      String replyToMessageId = normalize(event.replyToEventId());
      String messageId = normalize(event.eventId());
      String mediaUrl = normalize(event.mediaUrl());
      long ts = event.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      if (session != null) {
        session.rememberRoomEvent(roomId, messageId, ts);
      }
      String peerUserId = session == null ? "" : session.peerForRoom(roomId);
      boolean isDirectMessageRoom = !peerUserId.isEmpty();
      boolean fromSelf = session != null && sender.equals(session.userId);

      if (isDirectMessageRoom) {
        String normalizedType = msgType.isEmpty() ? "m.text" : msgType;
        if ("m.emote".equals(msgType)) {
          Map<String, String> tags =
              withTag(
                  withTag(
                      privateMessageTags(peerUserId, roomId, "m.emote", fromSelf),
                      TAG_MATRIX_MEDIA_URL,
                      mediaUrl),
                  TAG_DRAFT_REPLY,
                  replyToMessageId);
          bus.onNext(
              new ServerIrcEvent(
                  sid, new IrcEvent.PrivateAction(at, sender, body, messageId, tags)));
          continue;
        }
        if ("m.notice".equals(msgType)) {
          Map<String, String> tags =
              withTag(
                  withTag(
                      privateMessageTags(peerUserId, roomId, "m.notice", fromSelf),
                      TAG_MATRIX_MEDIA_URL,
                      mediaUrl),
                  TAG_DRAFT_REPLY,
                  replyToMessageId);
          bus.onNext(
              new ServerIrcEvent(
                  sid, new IrcEvent.Notice(at, sender, peerUserId, body, messageId, tags)));
          continue;
        }
        Map<String, String> tags =
            withTag(
                withTag(
                    privateMessageTags(peerUserId, roomId, normalizedType, fromSelf),
                    TAG_MATRIX_MEDIA_URL,
                    mediaUrl),
                TAG_DRAFT_REPLY,
                replyToMessageId);
        bus.onNext(
            new ServerIrcEvent(
                sid, new IrcEvent.PrivateMessage(at, sender, body, messageId, tags)));
        continue;
      }

      if ("m.emote".equals(msgType)) {
        Map<String, String> tags =
            withTag(
                withTag(Map.of(TAG_MATRIX_MSGTYPE, "m.emote"), TAG_MATRIX_MEDIA_URL, mediaUrl),
                TAG_DRAFT_REPLY,
                replyToMessageId);
        bus.onNext(
            new ServerIrcEvent(
                sid, new IrcEvent.ChannelAction(at, roomId, sender, body, messageId, tags)));
        continue;
      }

      if ("m.notice".equals(msgType)) {
        Map<String, String> tags =
            withTag(
                withTag(Map.of(TAG_MATRIX_MSGTYPE, "m.notice"), TAG_MATRIX_MEDIA_URL, mediaUrl),
                TAG_DRAFT_REPLY,
                replyToMessageId);
        bus.onNext(
            new ServerIrcEvent(
                sid, new IrcEvent.Notice(at, sender, roomId, body, messageId, tags)));
        continue;
      }

      String normalizedType = msgType.isEmpty() ? "m.text" : msgType;
      Map<String, String> tags =
          withTag(
              withTag(Map.of(TAG_MATRIX_MSGTYPE, normalizedType), TAG_MATRIX_MEDIA_URL, mediaUrl),
              TAG_DRAFT_REPLY,
              replyToMessageId);
      bus.onNext(
          new ServerIrcEvent(
              sid, new IrcEvent.ChannelMessage(at, roomId, sender, body, messageId, tags)));
    }
  }

  private void emitSyncMutationEvents(
      String serverId,
      MatrixSession session,
      List<MatrixSyncClient.RoomMessageEditEvent> messageEdits,
      List<MatrixSyncClient.RoomReactionEvent> reactions,
      List<MatrixSyncClient.RoomRedactionEvent> redactions) {
    emitSyncEditEvents(serverId, session, messageEdits);
    emitSyncReactionEvents(serverId, session, reactions);
    emitSyncRedactionEvents(serverId, session, redactions);
  }

  private void emitSyncEditEvents(
      String serverId, MatrixSession session, List<MatrixSyncClient.RoomMessageEditEvent> edits) {
    if (session == null || edits == null || edits.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomMessageEditEvent edit : edits) {
      if (edit == null) continue;
      String roomId = normalize(edit.roomId());
      String sender = normalize(edit.sender());
      String messageId = normalize(edit.eventId());
      String targetMessageId = normalize(edit.targetEventId());
      String body = Objects.toString(edit.body(), "");
      String msgType = normalize(edit.msgType());
      if (roomId.isEmpty()
          || sender.isEmpty()
          || targetMessageId.isEmpty()
          || body.trim().isEmpty()) {
        continue;
      }

      long ts = edit.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      session.rememberRoomEvent(roomId, messageId, ts);

      String normalizedType = msgType.isEmpty() ? "m.text" : msgType;
      String peerUserId = session.peerForRoom(roomId);
      boolean fromSelf = sender.equals(session.userId);
      if (!peerUserId.isEmpty()) {
        bus.onNext(
            new ServerIrcEvent(
                sid,
                new IrcEvent.PrivateMessage(
                    at,
                    sender,
                    body,
                    messageId,
                    withTag(
                        privateMessageTags(peerUserId, roomId, normalizedType, fromSelf),
                        TAG_DRAFT_EDIT,
                        targetMessageId))));
      } else {
        bus.onNext(
            new ServerIrcEvent(
                sid,
                new IrcEvent.ChannelMessage(
                    at,
                    roomId,
                    sender,
                    body,
                    messageId,
                    withTag(
                        Map.of(TAG_MATRIX_MSGTYPE, normalizedType),
                        TAG_DRAFT_EDIT,
                        targetMessageId))));
      }
    }
  }

  private void emitSyncReactionEvents(
      String serverId,
      MatrixSession session,
      List<MatrixSyncClient.RoomReactionEvent> reactionEvents) {
    if (session == null || reactionEvents == null || reactionEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomReactionEvent reactionEvent : reactionEvents) {
      if (reactionEvent == null) continue;
      String roomId = normalize(reactionEvent.roomId());
      String sender = normalize(reactionEvent.sender());
      String eventId = normalize(reactionEvent.eventId());
      String targetMessageId = normalize(reactionEvent.targetEventId());
      String reaction = normalize(reactionEvent.reaction());
      if (roomId.isEmpty() || sender.isEmpty() || targetMessageId.isEmpty() || reaction.isEmpty()) {
        continue;
      }

      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;

      long ts = reactionEvent.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      session.rememberReactionEvent(roomId, eventId, targetMessageId, reaction, sender);
      bus.onNext(
          new ServerIrcEvent(
              sid,
              new IrcEvent.MessageReactObserved(at, sender, target, reaction, targetMessageId)));
    }
  }

  private void emitSyncRedactionEvents(
      String serverId,
      MatrixSession session,
      List<MatrixSyncClient.RoomRedactionEvent> redactionEvents) {
    if (session == null || redactionEvents == null || redactionEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.RoomRedactionEvent redactionEvent : redactionEvents) {
      if (redactionEvent == null) continue;
      String roomId = normalize(redactionEvent.roomId());
      String sender = normalize(redactionEvent.sender());
      String redactsEventId = normalize(redactionEvent.redactsEventId());
      if (roomId.isEmpty() || redactsEventId.isEmpty()) continue;

      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;

      long ts = redactionEvent.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();
      MatrixSession.ReactionIndexEntry removedReaction =
          session.consumeReactionEvent(roomId, redactsEventId);
      if (removedReaction != null) {
        String reactionSender = normalize(removedReaction.sender());
        String reaction = normalize(removedReaction.reaction());
        String targetMessageId = normalize(removedReaction.targetEventId());
        if (!reactionSender.isEmpty() && !reaction.isEmpty() && !targetMessageId.isEmpty()) {
          bus.onNext(
              new ServerIrcEvent(
                  sid,
                  new IrcEvent.MessageUnreactObserved(
                      at, reactionSender, target, reaction, targetMessageId)));
          continue;
        }
      }

      bus.onNext(
          new ServerIrcEvent(
              sid, new IrcEvent.MessageRedactionObserved(at, sender, target, redactsEventId)));
    }
  }

  private void emitSyncSignalEvents(
      String serverId,
      MatrixSession session,
      List<MatrixSyncClient.TypingEvent> typingEvents,
      List<MatrixSyncClient.ReadReceiptEvent> readReceipts) {
    emitSyncTypingEvents(serverId, session, typingEvents);
    emitSyncReadMarkerEvents(serverId, session, readReceipts);
  }

  private void emitSyncMembershipEvents(
      String serverId,
      MatrixSession session,
      List<MatrixSyncClient.RoomMembershipEvent> membershipEvents) {
    if (session == null || membershipEvents == null || membershipEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;
    String selfUserId = normalize(session.userId);

    for (MatrixSyncClient.RoomMembershipEvent membershipEvent : membershipEvents) {
      if (membershipEvent == null) continue;
      String roomId = normalize(membershipEvent.roomId());
      String userId = normalize(membershipEvent.userId());
      if (roomId.isEmpty() || !looksLikeMatrixUserId(userId)) continue;

      String membership = normalize(membershipEvent.membership()).toLowerCase(Locale.ROOT);
      String prevMembership = normalize(membershipEvent.prevMembership()).toLowerCase(Locale.ROOT);
      String displayName = normalize(membershipEvent.displayName());
      String prevDisplayName = normalize(membershipEvent.prevDisplayName());
      String reason = normalize(membershipEvent.reason());
      long ts = membershipEvent.originServerTs();
      Instant at = ts > 0L ? Instant.ofEpochMilli(ts) : Instant.now();

      boolean joinedNow = "join".equals(membership);
      boolean joinedBefore = "join".equals(prevMembership);

      if (!userId.equals(selfUserId) && joinedNow && !joinedBefore) {
        bus.onNext(new ServerIrcEvent(sid, new IrcEvent.UserJoinedChannel(at, roomId, userId)));
      } else if (joinedBefore && !joinedNow) {
        if (userId.equals(selfUserId)) {
          session.forgetJoinedRoom(roomId);
          bus.onNext(new ServerIrcEvent(sid, new IrcEvent.LeftChannel(at, roomId, reason)));
        } else {
          bus.onNext(
              new ServerIrcEvent(sid, new IrcEvent.UserPartedChannel(at, roomId, userId, reason)));
        }
      }

      if (!displayName.isEmpty() && !displayName.equals(prevDisplayName)) {
        bus.onNext(
            new ServerIrcEvent(
                sid,
                new IrcEvent.UserSetNameObserved(
                    at, userId, displayName, IrcEvent.UserSetNameObserved.Source.SETNAME)));
      }
    }
  }

  private void emitSyncTypingEvents(
      String serverId, MatrixSession session, List<MatrixSyncClient.TypingEvent> typingEvents) {
    if (session == null || typingEvents == null || typingEvents.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;

    for (MatrixSyncClient.TypingEvent typingEvent : typingEvents) {
      if (typingEvent == null) continue;
      String roomId = normalize(typingEvent.roomId());
      if (roomId.isEmpty()) continue;
      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;

      Set<String> currentUsers = normalizeTypingUsers(typingEvent.userIds(), session.userId);
      Set<String> previousUsers = session.replaceTypingUsers(roomId, currentUsers);

      Instant now = Instant.now();
      for (String userId : currentUsers) {
        if (previousUsers.contains(userId)) continue;
        bus.onNext(
            new ServerIrcEvent(
                sid, new IrcEvent.UserTypingObserved(now, userId, target, "active")));
      }
      for (String userId : previousUsers) {
        if (currentUsers.contains(userId)) continue;
        bus.onNext(
            new ServerIrcEvent(sid, new IrcEvent.UserTypingObserved(now, userId, target, "done")));
      }
    }
  }

  private void emitSyncReadMarkerEvents(
      String serverId,
      MatrixSession session,
      List<MatrixSyncClient.ReadReceiptEvent> readReceipts) {
    if (session == null || readReceipts == null || readReceipts.isEmpty()) return;
    String sid = normalize(serverId);
    if (sid.isEmpty()) return;
    String selfUserId = normalize(session.userId);
    if (selfUserId.isEmpty()) return;

    for (MatrixSyncClient.ReadReceiptEvent receipt : readReceipts) {
      if (receipt == null) continue;
      String roomId = normalize(receipt.roomId());
      String fromUserId = normalize(receipt.userId());
      long ts = receipt.timestampMs();
      if (roomId.isEmpty() || fromUserId.isEmpty() || ts <= 0L) continue;
      if (!selfUserId.equals(fromUserId)) continue;
      if (!session.shouldEmitReadMarker(roomId, ts)) continue;

      String target = signalTargetForRoom(session, roomId);
      if (target.isEmpty()) continue;
      Instant markerAt = Instant.ofEpochMilli(ts);
      String marker = "timestamp=" + markerAt;
      bus.onNext(
          new ServerIrcEvent(
              sid, new IrcEvent.ReadMarkerObserved(markerAt, fromUserId, target, marker)));
    }
  }

  private static Set<String> normalizeTypingUsers(List<String> rawUsers, String selfUserId) {
    if (rawUsers == null || rawUsers.isEmpty()) {
      return Set.of();
    }
    String self = normalize(selfUserId);
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String raw : rawUsers) {
      String userId = normalize(raw);
      if (!looksLikeMatrixUserId(userId)) continue;
      if (!self.isEmpty() && self.equals(userId)) continue;
      normalized.add(userId);
    }
    if (normalized.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(normalized);
  }

  private static String signalTargetForRoom(MatrixSession session, String roomId) {
    String rid = normalize(roomId);
    if (rid.isEmpty()) return "";
    String peer = session == null ? "" : session.peerForRoom(rid);
    if (!peer.isEmpty()) {
      return peer;
    }
    return rid;
  }

  private static Map<String, String> privateMessageTags(
      String peerUserId, String roomId, String msgType) {
    return privateMessageTags(peerUserId, roomId, msgType, true);
  }

  private static Map<String, String> privateMessageTags(
      String peerUserId, String roomId, String msgType, boolean includePrivateTargetTag) {
    String peer = normalize(peerUserId);
    String rid = normalize(roomId);
    String type = normalize(msgType);
    if (type.isEmpty()) {
      type = "m.text";
    }

    if (includePrivateTargetTag && !peer.isEmpty()) {
      return Map.of(TAG_IRCAFE_PM_TARGET, peer, TAG_MATRIX_ROOM_ID, rid, TAG_MATRIX_MSGTYPE, type);
    }
    return Map.of(TAG_MATRIX_ROOM_ID, rid, TAG_MATRIX_MSGTYPE, type);
  }

  private static Map<String, String> withTag(Map<String, String> base, String key, String value) {
    String tagKey = normalize(key);
    String tagValue = normalize(value);
    if (tagKey.isEmpty() || tagValue.isEmpty()) {
      return base == null ? Map.of() : base;
    }
    LinkedHashMap<String, String> tags = new LinkedHashMap<>();
    if (base != null && !base.isEmpty()) {
      tags.putAll(base);
    }
    tags.put(tagKey, tagValue);
    return Map.copyOf(tags);
  }

  private static void disposeSyncTask(MatrixSession session) {
    if (session == null) return;
    Disposable task = session.syncTask.getAndSet(null);
    if (task == null || task.isDisposed()) return;
    try {
      task.dispose();
    } catch (Exception ignored) {
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  private static final class MatrixSession {
    private final String userId;

    private final String accessToken;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<Disposable> syncTask = new AtomicReference<>();
    private final AtomicReference<String> sinceToken = new AtomicReference<>("");
    private final Map<String, String> directRoomByPeer = new ConcurrentHashMap<>();
    private final Map<String, String> directPeerByRoom = new ConcurrentHashMap<>();
    private final Map<String, String> joinedRoomByAlias = new ConcurrentHashMap<>();
    private final Map<String, HistoryCursor> historyCursorByRoom = new ConcurrentHashMap<>();
    private final Map<String, String> latestEventByRoom = new ConcurrentHashMap<>();
    private final Map<String, LinkedHashMap<String, Long>> messageTimestampByIdByRoom =
        new ConcurrentHashMap<>();
    private final Map<String, LinkedHashMap<String, ReactionIndexEntry>> reactionByEventIdByRoom =
        new ConcurrentHashMap<>();
    private final Map<String, Set<String>> typingUsersByRoom = new ConcurrentHashMap<>();
    private final Map<String, Long> readMarkerTsByRoom = new ConcurrentHashMap<>();

    private MatrixSession(String userId, String accessToken) {
      this.userId = normalize(userId);
      this.accessToken = normalize(accessToken);
    }

    private void rememberDirectRoom(String peerUserId, String roomId) {
      String peer = normalize(peerUserId);
      String rid = normalize(roomId);
      if (peer.isEmpty() || rid.isEmpty()) return;
      directRoomByPeer.put(peer, rid);
      directPeerByRoom.put(rid, peer);
    }

    private void rememberDirectRooms(Map<String, String> directPeerByRoom) {
      if (directPeerByRoom == null || directPeerByRoom.isEmpty()) return;
      for (Map.Entry<String, String> entry : directPeerByRoom.entrySet()) {
        if (entry == null) continue;
        rememberDirectRoom(entry.getValue(), entry.getKey());
      }
    }

    private String roomForPeer(String peerUserId) {
      return normalize(directRoomByPeer.get(normalize(peerUserId)));
    }

    private String peerForRoom(String roomId) {
      return normalize(directPeerByRoom.get(normalize(roomId)));
    }

    private void rememberJoinedAlias(String roomAlias, String roomId) {
      String alias = normalize(roomAlias);
      String rid = normalize(roomId);
      if (alias.isEmpty() || rid.isEmpty()) return;
      joinedRoomByAlias.put(alias, rid);
    }

    private String roomForAlias(String roomAlias) {
      return normalize(joinedRoomByAlias.get(normalize(roomAlias)));
    }

    private void forgetJoinedRoom(String roomId) {
      String rid = normalize(roomId);
      if (rid.isEmpty()) return;
      joinedRoomByAlias.entrySet().removeIf(entry -> rid.equals(normalize(entry.getValue())));
      historyCursorByRoom.remove(rid);
      latestEventByRoom.remove(rid);
      messageTimestampByIdByRoom.remove(rid);
      reactionByEventIdByRoom.remove(rid);
      typingUsersByRoom.remove(rid);
      readMarkerTsByRoom.remove(rid);
    }

    private void rememberHistoryCursor(String roomId, String nextToken, long beforeEpochMs) {
      String rid = normalize(roomId);
      String token = normalize(nextToken);
      if (rid.isEmpty() || token.isEmpty()) return;
      historyCursorByRoom.put(rid, new HistoryCursor(token, beforeEpochMs));
    }

    private HistoryCursor historyCursor(String roomId) {
      return historyCursorByRoom.get(normalize(roomId));
    }

    private void rememberLatestRoomEvent(String roomId, String eventId) {
      rememberRoomEvent(roomId, eventId, System.currentTimeMillis());
    }

    private void rememberRoomEvent(String roomId, String eventId, long timestampMs) {
      String rid = normalize(roomId);
      String eid = normalize(eventId);
      if (rid.isEmpty() || eid.isEmpty()) return;
      long ts = timestampMs > 0L ? timestampMs : System.currentTimeMillis();
      latestEventByRoom.put(rid, eid);
      LinkedHashMap<String, Long> index =
          messageTimestampByIdByRoom.computeIfAbsent(rid, ignored -> new LinkedHashMap<>());
      synchronized (index) {
        index.put(eid, ts);
        while (index.size() > ROOM_MESSAGE_INDEX_CAPACITY) {
          String eldest = index.entrySet().iterator().next().getKey();
          index.remove(eldest);
        }
      }
    }

    private void rememberReactionEvent(
        String roomId,
        String reactionEventId,
        String targetEventId,
        String reaction,
        String sender) {
      String rid = normalize(roomId);
      String reactionId = normalize(reactionEventId);
      String targetId = normalize(targetEventId);
      String key = normalize(reaction);
      String from = normalize(sender);
      if (rid.isEmpty()
          || reactionId.isEmpty()
          || targetId.isEmpty()
          || key.isEmpty()
          || from.isEmpty()) {
        return;
      }
      LinkedHashMap<String, ReactionIndexEntry> index =
          reactionByEventIdByRoom.computeIfAbsent(rid, ignored -> new LinkedHashMap<>());
      synchronized (index) {
        index.put(reactionId, new ReactionIndexEntry(targetId, key, from));
        while (index.size() > ROOM_REACTION_INDEX_CAPACITY) {
          String eldest = index.entrySet().iterator().next().getKey();
          index.remove(eldest);
        }
      }
    }

    private ReactionIndexEntry consumeReactionEvent(String roomId, String reactionEventId) {
      String rid = normalize(roomId);
      String reactionId = normalize(reactionEventId);
      if (rid.isEmpty() || reactionId.isEmpty()) return null;
      LinkedHashMap<String, ReactionIndexEntry> index = reactionByEventIdByRoom.get(rid);
      if (index == null) return null;
      synchronized (index) {
        return index.remove(reactionId);
      }
    }

    private String findReactionEventId(
        String roomId, String targetEventId, String reaction, String sender) {
      String rid = normalize(roomId);
      String targetId = normalize(targetEventId);
      String key = normalize(reaction);
      String from = normalize(sender);
      if (rid.isEmpty() || targetId.isEmpty() || key.isEmpty() || from.isEmpty()) {
        return "";
      }
      LinkedHashMap<String, ReactionIndexEntry> index = reactionByEventIdByRoom.get(rid);
      if (index == null || index.isEmpty()) return "";
      synchronized (index) {
        String found = "";
        for (Map.Entry<String, ReactionIndexEntry> entry : index.entrySet()) {
          if (entry == null || entry.getValue() == null) continue;
          ReactionIndexEntry value = entry.getValue();
          if (!targetId.equals(normalize(value.targetEventId()))) continue;
          if (!key.equals(normalize(value.reaction()))) continue;
          if (!from.equals(normalize(value.sender()))) continue;
          found = normalize(entry.getKey());
        }
        return found;
      }
    }

    private void rememberHistoryEvents(
        String roomId, List<MatrixRoomHistoryClient.RoomHistoryEvent> events) {
      if (events == null || events.isEmpty()) return;
      String rid = normalize(roomId);
      if (rid.isEmpty()) return;
      for (MatrixRoomHistoryClient.RoomHistoryEvent event : events) {
        if (event == null) continue;
        rememberRoomEvent(rid, event.eventId(), event.originServerTs());
      }
    }

    private String latestRoomEventId(String roomId) {
      return normalize(latestEventByRoom.get(normalize(roomId)));
    }

    private long roomEventTimestampMs(String roomId, String eventId) {
      String rid = normalize(roomId);
      String eid = normalize(eventId);
      if (rid.isEmpty() || eid.isEmpty()) return 0L;
      LinkedHashMap<String, Long> index = messageTimestampByIdByRoom.get(rid);
      if (index == null) return 0L;
      synchronized (index) {
        Long ts = index.get(eid);
        return ts == null ? 0L : ts.longValue();
      }
    }

    private Set<String> replaceTypingUsers(String roomId, Set<String> users) {
      String rid = normalize(roomId);
      if (rid.isEmpty()) {
        return Set.of();
      }
      Set<String> next = (users == null || users.isEmpty()) ? Set.of() : Set.copyOf(users);
      Set<String> previous = typingUsersByRoom.get(rid);
      if (next.isEmpty()) {
        typingUsersByRoom.remove(rid);
      } else {
        typingUsersByRoom.put(rid, next);
      }
      if (previous == null || previous.isEmpty()) {
        return Set.of();
      }
      return previous;
    }

    private boolean shouldEmitReadMarker(String roomId, long markerTsMs) {
      String rid = normalize(roomId);
      if (rid.isEmpty() || markerTsMs <= 0L) {
        return false;
      }
      Long prior = readMarkerTsByRoom.get(rid);
      if (prior != null && markerTsMs <= prior.longValue()) {
        return false;
      }
      readMarkerTsByRoom.put(rid, markerTsMs);
      return true;
    }

    private record ReactionIndexEntry(String targetEventId, String reaction, String sender) {}

    private record HistoryCursor(String nextToken, long beforeEpochMs) {}
  }

  private record RawCommand(String command, List<String> arguments) {
    private RawCommand {
      command = normalize(command).toUpperCase(Locale.ROOT);
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
  }
}
