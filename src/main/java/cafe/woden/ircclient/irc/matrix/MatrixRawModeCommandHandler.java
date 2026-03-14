package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.BackendNotAvailableException;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRawModeCommandHandler {

  interface SessionView {
    String userId();

    String accessToken();

    String roomForAlias(String roomAlias);

    void rememberJoinedAlias(String roomAlias, String roomId);
  }

  @NonNull private final ServerCatalog serverCatalog;
  @NonNull private final MatrixRoomMembershipClient roomMembershipClient;
  @NonNull private final MatrixRoomStateClient roomStateClient;
  @NonNull private final MatrixRoomDirectoryClient roomDirectoryClient;
  @NonNull private final Function<String, SessionView> sessionLookup;
  @NonNull private final Function<String, String> backendAvailabilityReasonLookup;
  @NonNull private final Consumer<ServerIrcEvent> eventEmitter;

  Completable handleMode(String serverId, List<String> arguments) {
    String target = argOrBlank(arguments, 0, "MODE requires <room> [modes] [args...]");
    if (arguments == null || arguments.size() < 2) {
      return handleModeQuery(serverId, target);
    }
    String modeSpec = normalize(arguments.get(1));
    if (modeSpec.isEmpty()) {
      return handleModeQuery(serverId, target);
    }
    List<String> modeArgs =
        arguments.size() > 2 ? arguments.subList(2, arguments.size()) : List.of();
    return handleModeMutation(serverId, target, modeSpec, modeArgs);
  }

  Completable handleModeQuery(String serverId, String target) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              SessionView session = requireSession(sid, "mode");
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, target, server, session);
              MatrixRoomStateClient.PowerLevelsResult state =
                  roomStateClient.fetchRoomPowerLevels(sid, server, session.accessToken(), roomId);
              if (!state.success()) {
                throw new IllegalStateException(
                    "Matrix mode query failed at " + state.endpoint() + ": " + state.detail());
              }
              Instant now = Instant.now();
              emit(
                  sid,
                  new IrcEvent.ChannelModeObserved(
                      now,
                      roomId,
                      session.userId(),
                      "+matrix",
                      IrcEvent.ChannelModeKind.SNAPSHOT,
                      IrcEvent.ChannelModeProvenance.UNKNOWN));
              emit(
                  sid,
                  new IrcEvent.ServerResponseLine(
                      now,
                      324,
                      roomId + " " + renderMatrixPowerLevelSummary(state.content()),
                      "",
                      "",
                      Map.of()));
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  Completable handleModeMutation(
      String serverId, String target, String modeSpec, List<String> modeArgs) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException("server id is blank");
              }
              SessionView session = requireSession(sid, "mode");
              IrcProperties.Server server = serverCatalog.require(sid);
              String roomId = resolveAliasOrRoomId(sid, target, server, session);

              int argIndex = 0;
              boolean adding = true;
              boolean powerLevelsChanged = false;
              Map<String, Object> powerLevels = null;
              List<String> applied = new ArrayList<>();

              for (int i = 0; i < modeSpec.length(); i++) {
                char c = modeSpec.charAt(i);
                if (c == '+') {
                  adding = true;
                  continue;
                }
                if (c == '-') {
                  adding = false;
                  continue;
                }

                if (isMatrixRoleMode(c)) {
                  if (argIndex >= modeArgs.size()) {
                    throw new IllegalArgumentException(
                        "MODE " + modeSpec + " is missing nick arguments");
                  }
                  String userId = normalize(modeArgs.get(argIndex++));
                  if (!looksLikeMatrixUserId(userId)) {
                    throw new IllegalArgumentException("MODE target must be a Matrix user id");
                  }
                  if (powerLevels == null) {
                    powerLevels =
                        fetchOrDefaultPowerLevels(sid, server, session.accessToken(), roomId);
                  }
                  int usersDefault = matrixUsersDefault(powerLevels);
                  int level = adding ? matrixRolePowerLevel(c) : usersDefault;
                  setMatrixUserPowerLevel(powerLevels, userId, level);
                  powerLevelsChanged = true;
                  applied.add((adding ? "+" : "-") + c + " " + userId);
                  continue;
                }

                if (c == 'b') {
                  if (argIndex >= modeArgs.size()) {
                    throw new IllegalArgumentException(
                        "MODE " + modeSpec + " is missing ban arguments");
                  }
                  String userId = normalize(modeArgs.get(argIndex++));
                  if (!looksLikeMatrixUserId(userId)) {
                    throw new IllegalArgumentException("MODE ban target must be a Matrix user id");
                  }
                  MatrixRoomMembershipClient.ActionResult action =
                      adding
                          ? roomMembershipClient.banUser(
                              sid, server, session.accessToken(), roomId, userId, "")
                          : roomMembershipClient.unbanUser(
                              sid, server, session.accessToken(), roomId, userId);
                  if (!action.success()) {
                    throw new IllegalStateException(
                        "Matrix mode ban update failed at "
                            + action.endpoint()
                            + ": "
                            + action.detail());
                  }
                  applied.add((adding ? "+" : "-") + "b " + userId);
                  continue;
                }

                throw new IllegalArgumentException(
                    "MODE " + c + " is not supported by Matrix backend");
              }

              if (argIndex < modeArgs.size()) {
                throw new IllegalArgumentException(
                    "MODE has too many arguments for the mode sequence");
              }

              if (powerLevelsChanged) {
                MatrixRoomStateClient.UpdateResult update =
                    roomStateClient.updateRoomPowerLevels(
                        sid, server, session.accessToken(), roomId, powerLevels);
                if (!update.updated()) {
                  throw new IllegalStateException(
                      "Matrix mode update failed at " + update.endpoint() + ": " + update.detail());
                }
              }

              Instant now = Instant.now();
              for (String details : applied) {
                emit(
                    sid,
                    new IrcEvent.ChannelModeObserved(
                        now,
                        roomId,
                        session.userId(),
                        details,
                        IrcEvent.ChannelModeKind.DELTA,
                        IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT));
              }
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

  private Map<String, Object> fetchOrDefaultPowerLevels(
      String serverId, IrcProperties.Server server, String accessToken, String roomId) {
    MatrixRoomStateClient.PowerLevelsResult state =
        roomStateClient.fetchRoomPowerLevels(serverId, server, accessToken, roomId);
    if (!state.success()) {
      throw new IllegalStateException(
          "Matrix power-level fetch failed at " + state.endpoint() + ": " + state.detail());
    }
    Map<String, Object> content = state.content();
    if (content == null || content.isEmpty()) {
      return new LinkedHashMap<>(MatrixRoomStateClient.defaultPowerLevelsState());
    }
    return deepMutableCopy(content);
  }

  private static int matrixUsersDefault(Map<String, Object> powerLevels) {
    if (powerLevels == null || powerLevels.isEmpty()) {
      return 0;
    }
    return parseInteger(powerLevels.get("users_default"), 0);
  }

  private static void setMatrixUserPowerLevel(
      Map<String, Object> powerLevels, String userId, int level) {
    if (powerLevels == null) return;
    Map<String, Object> users = mutableMap(powerLevels.get("users"));
    users.put(normalize(userId), Integer.valueOf(level));
    powerLevels.put("users", users);
  }

  private static Map<String, Object> mutableMap(Object value) {
    if (value instanceof Map<?, ?> mapValue) {
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        if (entry == null) continue;
        String key = normalize(Objects.toString(entry.getKey(), ""));
        if (key.isEmpty()) continue;
        out.put(key, entry.getValue());
      }
      return out;
    }
    return new LinkedHashMap<>();
  }

  private static Map<String, Object> deepMutableCopy(Map<String, Object> input) {
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    if (input == null || input.isEmpty()) {
      return out;
    }
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      if (entry == null) continue;
      String key = normalize(entry.getKey());
      if (key.isEmpty()) continue;
      out.put(key, deepMutableValue(entry.getValue()));
    }
    return out;
  }

  private static Object deepMutableValue(Object value) {
    if (value instanceof Map<?, ?> mapValue) {
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        if (entry == null) continue;
        String key = normalize(Objects.toString(entry.getKey(), ""));
        if (key.isEmpty()) continue;
        out.put(key, deepMutableValue(entry.getValue()));
      }
      return out;
    }
    if (value instanceof List<?> listValue) {
      ArrayList<Object> out = new ArrayList<>(listValue.size());
      for (Object item : listValue) {
        out.add(deepMutableValue(item));
      }
      return out;
    }
    return value;
  }

  private static int parseInteger(Object raw, int fallback) {
    if (raw == null) return fallback;
    if (raw instanceof Number n) {
      return n.intValue();
    }
    String token = normalize(Objects.toString(raw, ""));
    if (!looksLikeInteger(token)) {
      return fallback;
    }
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static boolean isMatrixRoleMode(char mode) {
    return mode == 'v' || mode == 'h' || mode == 'o' || mode == 'a' || mode == 'q';
  }

  private static int matrixRolePowerLevel(char mode) {
    return switch (mode) {
      case 'v' -> 10;
      case 'h' -> 25;
      case 'o' -> 50;
      case 'a' -> 75;
      case 'q' -> 100;
      default -> 0;
    };
  }

  private static String renderMatrixPowerLevelSummary(Map<String, Object> powerLevels) {
    if (powerLevels == null || powerLevels.isEmpty()) {
      return "users_default=0";
    }
    int usersDefault = matrixUsersDefault(powerLevels);
    StringBuilder out = new StringBuilder().append("users_default=").append(usersDefault);
    Map<String, Object> users = mutableMap(powerLevels.get("users"));
    if (!users.isEmpty()) {
      users.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(String::compareToIgnoreCase))
          .forEach(
              entry ->
                  out.append(" ")
                      .append(entry.getKey())
                      .append("=")
                      .append(parseInteger(entry.getValue(), usersDefault)));
    }
    return out.toString();
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

  private static String eventHost(IrcProperties.Server server) {
    try {
      return normalize(MatrixEndpointResolver.homeserverBaseUri(server).getHost());
    } catch (Exception ignored) {
      return normalize(server == null ? "" : server.host());
    }
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

  private static String normalizeServerId(String serverId) {
    return normalize(serverId);
  }

  private static boolean looksLikeInteger(String token) {
    String value = normalize(token);
    if (value.isEmpty()) return false;
    int start = value.startsWith("+") || value.startsWith("-") ? 1 : 0;
    if (start >= value.length()) return false;
    for (int i = start; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) return false;
    }
    return true;
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

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
