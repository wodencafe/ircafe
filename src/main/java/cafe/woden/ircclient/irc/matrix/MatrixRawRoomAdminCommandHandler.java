package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRawRoomAdminCommandHandler {
  interface SessionView {
    String userId();

    String accessToken();

    String roomForAlias(String roomAlias);

    void rememberJoinedAlias(String roomAlias, String roomId);

    void forgetJoinedRoom(String roomId);
  }

  private static final int MATRIX_LIST_DEFAULT_LIMIT = 100;
  private static final int MATRIX_LIST_MAX_LIMIT = 200;

  @NonNull private final ServerCatalog serverCatalog;
  @NonNull private final MatrixRoomMembershipClient roomMembershipClient;
  @NonNull private final MatrixRoomStateClient roomStateClient;
  @NonNull private final MatrixRoomDirectoryClient roomDirectoryClient;
  @NonNull private final Function<String, SessionView> sessionLookup;
  @NonNull private final Function<String, String> backendAvailabilityReasonLookup;
  @NonNull private final Consumer<ServerIrcEvent> eventEmitter;

  Completable handleTopic(String serverId, List<String> arguments) {
    String target = argOrBlank(arguments, 0, "TOPIC requires a room target");
    String topic = joinArgs(arguments, 1);
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              SessionView session = requireSession(sid, "topic");
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, target, server, session);

              if (topic.isEmpty()) {
                MatrixRoomStateClient.TopicResult result =
                    roomStateClient.fetchRoomTopic(sid, server, session.accessToken(), roomId);
                if (!result.success()) {
                  throw new IllegalStateException(
                      "Matrix topic fetch failed at " + result.endpoint() + ": " + result.detail());
                }
                emit(
                    sid,
                    new IrcEvent.ChannelTopicUpdated(
                        Instant.now(), roomId, Objects.toString(result.topic(), "")));
                return;
              }

              MatrixRoomStateClient.UpdateResult update =
                  roomStateClient.updateRoomTopic(
                      sid, server, session.accessToken(), roomId, topic);
              if (!update.updated()) {
                throw new IllegalStateException(
                    "Matrix topic update failed at " + update.endpoint() + ": " + update.detail());
              }
              emit(sid, new IrcEvent.ChannelTopicUpdated(Instant.now(), roomId, topic));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  Completable handleKick(String serverId, List<String> arguments) {
    String target = argOrBlank(arguments, 0, "KICK requires <room> <userId> [reason]");
    String userId = argOrBlank(arguments, 1, "KICK requires <room> <userId> [reason]");
    String reason = joinArgs(arguments, 2);
    if (!looksLikeMatrixUserId(userId)) {
      throw new IllegalArgumentException("KICK target must be a Matrix user id");
    }

    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              SessionView session = requireSession(sid, "kick");
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, target, server, session);
              MatrixRoomMembershipClient.ActionResult result =
                  roomMembershipClient.kickUser(
                      sid, server, session.accessToken(), roomId, userId, reason);
              if (!result.success()) {
                throw new IllegalStateException(
                    "Matrix kick failed at " + result.endpoint() + ": " + result.detail());
              }

              Instant now = Instant.now();
              String by = normalize(session.userId());
              if (userId.equals(by)) {
                session.forgetJoinedRoom(roomId);
                emit(sid, new IrcEvent.KickedFromChannel(now, roomId, by, reason));
              } else {
                emit(sid, new IrcEvent.UserKickedFromChannel(now, roomId, userId, by, reason));
              }
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  Completable handleInvite(String serverId, List<String> arguments) {
    String invitee = argOrBlank(arguments, 0, "INVITE requires <userId> <room>");
    String target = argOrBlank(arguments, 1, "INVITE requires <userId> <room>");
    if (!looksLikeMatrixUserId(invitee)) {
      throw new IllegalArgumentException("INVITE target must be a Matrix user id");
    }

    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              SessionView session = requireSession(sid, "invite");
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, target, server, session);
              MatrixRoomMembershipClient.ActionResult result =
                  roomMembershipClient.inviteUser(
                      sid, server, session.accessToken(), roomId, invitee);
              if (!result.success()) {
                throw new IllegalStateException(
                    "Matrix invite failed at " + result.endpoint() + ": " + result.detail());
              }

              emit(
                  sid,
                  new IrcEvent.InvitedToChannel(
                      Instant.now(), roomId, session.userId(), invitee, "", false));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  Completable handleList(String serverId, List<String> arguments) {
    RawListQuery query = parseRawListQuery(arguments);
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }

              SessionView session = requireSession(sid, "list");
              IrcProperties.Server server = serverCatalog.require(sid);
              MatrixRoomDirectoryClient.PublicRoomsResult result =
                  roomDirectoryClient.fetchPublicRooms(
                      sid,
                      server,
                      session.accessToken(),
                      query.searchTerm(),
                      query.sinceToken(),
                      query.limit());
              if (!result.success()) {
                throw new IllegalStateException(
                    "Matrix list failed at " + result.endpoint() + ": " + result.detail());
              }

              Instant now = Instant.now();
              String searchTerm = normalize(query.searchTerm());
              String banner =
                  searchTerm.isEmpty()
                      ? "Matrix public rooms"
                      : ("Matrix public rooms (search: " + searchTerm + ")");
              emit(sid, new IrcEvent.ChannelListStarted(now, banner));

              int listed = 0;
              List<MatrixRoomDirectoryClient.PublicRoom> rooms = result.rooms();
              if (rooms != null) {
                for (MatrixRoomDirectoryClient.PublicRoom room : rooms) {
                  if (room == null) continue;
                  String channel = matrixListChannelName(room);
                  if (channel.isEmpty()) continue;
                  int visibleUsers = Math.max(0, room.joinedMembers());
                  String topic = matrixListTopic(room);
                  emit(sid, new IrcEvent.ChannelListEntry(now, channel, visibleUsers, topic));
                  listed++;
                }
              }

              String nextBatch = normalize(result.nextBatch());
              String summary =
                  nextBatch.isEmpty()
                      ? ("Listed " + listed + " Matrix room(s).")
                      : ("Listed " + listed + " Matrix room(s). next_batch=" + nextBatch);
              emit(sid, new IrcEvent.ChannelListEnded(now, summary));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private SessionView requireSession(String serverId, String operation) {
    SessionView session = sessionLookup.apply(serverId);
    if (session != null) {
      return session;
    }
    throw new BackendNotAvailableException(
        IrcProperties.Server.Backend.MATRIX,
        operation,
        serverId,
        backendAvailabilityReasonLookup.apply(serverId));
  }

  private void emit(String serverId, IrcEvent event) {
    eventEmitter.accept(new ServerIrcEvent(serverId, event));
  }

  private String resolveAliasOrRoomId(
      String serverId, String rawTarget, IrcProperties.Server server, SessionView session) {
    String target = normalizeJoinTarget(rawTarget, server);
    if (looksLikeMatrixRoomId(target)) {
      return target;
    }

    String mapped = session == null ? "" : session.roomForAlias(target);
    if (!mapped.isEmpty()) {
      return mapped;
    }

    MatrixRoomDirectoryClient.ResolveResult resolved =
        roomDirectoryClient.resolveRoomAlias(serverId, server, session.accessToken(), target);
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

  private static String normalizeJoinTarget(String rawTarget, IrcProperties.Server server) {
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

  private static RawListQuery parseRawListQuery(List<String> rawArgs) {
    List<String> args = rawArgs == null ? List.of() : rawArgs;
    ArrayList<String> freeTokens = new ArrayList<>();
    String searchTerm = "";
    String sinceToken = "";
    int limit = MATRIX_LIST_DEFAULT_LIMIT;

    for (int i = 0; i < args.size(); i++) {
      String token = normalize(args.get(i));
      if (token.isEmpty()) continue;
      String lower = token.toLowerCase(Locale.ROOT);

      String sinceInline = listOptionValue(lower, token, "since");
      if (!sinceInline.isEmpty()) {
        sinceToken = sinceInline;
        continue;
      }
      if ("since".equals(lower) || "--since".equals(lower) || "-since".equals(lower)) {
        if (i + 1 < args.size()) {
          sinceToken = normalize(args.get(++i));
        }
        continue;
      }

      String searchInline = listOptionValue(lower, token, "search");
      if (searchInline.isEmpty()) {
        searchInline = listOptionValue(lower, token, "q");
      }
      if (!searchInline.isEmpty()) {
        searchTerm = searchInline;
        continue;
      }
      if ("search".equals(lower)
          || "--search".equals(lower)
          || "-search".equals(lower)
          || "q".equals(lower)
          || "--q".equals(lower)
          || "-q".equals(lower)) {
        if (i + 1 < args.size()) {
          searchTerm = normalize(args.get(++i));
        }
        continue;
      }

      String limitInline = listOptionValue(lower, token, "limit");
      if (limitInline.isEmpty()) {
        limitInline = listOptionValue(lower, token, "max");
      }
      if (!limitInline.isEmpty()) {
        Integer parsed = parsePositiveIntToken(limitInline);
        if (parsed != null) {
          limit = parsed.intValue();
        }
        continue;
      }
      if ("limit".equals(lower)
          || "--limit".equals(lower)
          || "-limit".equals(lower)
          || "max".equals(lower)
          || "--max".equals(lower)
          || "-max".equals(lower)) {
        if (i + 1 < args.size()) {
          Integer parsed = parsePositiveIntToken(args.get(++i));
          if (parsed != null) {
            limit = parsed.intValue();
          }
        }
        continue;
      }

      freeTokens.add(token);
    }

    if (searchTerm.isEmpty() && !freeTokens.isEmpty()) {
      searchTerm = normalize(String.join(" ", freeTokens));
    }
    return new RawListQuery(searchTerm, sinceToken, limit);
  }

  private static String listOptionValue(String tokenLower, String tokenRaw, String optionName) {
    String opt = normalize(optionName).toLowerCase(Locale.ROOT);
    if (opt.isEmpty()) return "";
    String value = "";
    if (tokenLower.startsWith(opt + "=")) {
      value = tokenRaw.substring(opt.length() + 1);
    } else if (tokenLower.startsWith("--" + opt + "=")) {
      value = tokenRaw.substring(opt.length() + 3);
    } else if (tokenLower.startsWith("-" + opt + "=")) {
      value = tokenRaw.substring(opt.length() + 2);
    }
    return normalize(value);
  }

  private static Integer parsePositiveIntToken(String token) {
    String value = normalize(token);
    if (value.isEmpty()) return null;
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0) return null;
      return Integer.valueOf(parsed);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static int normalizeMatrixListLimit(int limit) {
    int requested = limit <= 0 ? MATRIX_LIST_DEFAULT_LIMIT : limit;
    return Math.max(1, Math.min(requested, MATRIX_LIST_MAX_LIMIT));
  }

  private static String matrixListChannelName(MatrixRoomDirectoryClient.PublicRoom room) {
    if (room == null) return "";
    String alias = normalize(room.canonicalAlias());
    if (!alias.isEmpty()) {
      return alias;
    }
    return normalize(room.roomId());
  }

  private static String matrixListTopic(MatrixRoomDirectoryClient.PublicRoom room) {
    if (room == null) return "";
    String topic = normalize(room.topic());
    if (!topic.isEmpty()) {
      return topic;
    }
    return normalize(room.name());
  }

  private static String argOrBlank(List<String> args, int index, String error) {
    if (args == null || index < 0 || index >= args.size()) {
      throw new IllegalArgumentException(error);
    }
    String value = normalize(args.get(index));
    if (value.isEmpty()) {
      throw new IllegalArgumentException(error);
    }
    return value;
  }

  private static String joinArgs(List<String> args, int startIndex) {
    if (args == null || args.isEmpty() || startIndex >= args.size()) {
      return "";
    }
    return args.subList(startIndex, args.size()).stream()
        .map(MatrixRawRoomAdminCommandHandler::normalize)
        .filter(token -> !token.isEmpty())
        .reduce((left, right) -> left + " " + right)
        .orElse("");
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

  private static String eventHost(IrcProperties.Server server) {
    try {
      return normalize(MatrixEndpointResolver.homeserverBaseUri(server).getHost());
    } catch (Exception ignored) {
      return normalize(server == null ? "" : server.host());
    }
  }

  private static String normalizeServerId(String serverId) {
    return normalize(serverId);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  private record RawListQuery(String searchTerm, String sinceToken, int limit) {
    private RawListQuery {
      searchTerm = normalize(searchTerm);
      sinceToken = normalize(sinceToken);
      limit = normalizeMatrixListLimit(limit);
    }
  }
}
