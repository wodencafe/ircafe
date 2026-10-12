package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Resolves room targets according to each operation's lookup policy. The caller owns scheduling;
 * cached mappings and sync cursors stay in the supplied session.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRoomTargetResolver {
  private static final int SYNC_TIMEOUT_MS = 0;

  @NonNull private final MatrixRoomDirectoryClient roomDirectoryClient;
  @NonNull private final MatrixDirectRoomResolver directRoomResolver;
  @NonNull private final MatrixSyncClient syncClient;
  @NonNull private final Function<IrcProperties.Server, String> homeserverHost;

  static boolean looksLikeMatrixRoomId(String token) {
    String value = normalize(token);
    if (!value.startsWith("!")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  static boolean looksLikeMatrixUserId(String token) {
    String value = normalize(token);
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  static boolean looksLikeMatrixRoomAlias(String token) {
    String value = normalize(token);
    if (!value.startsWith("#")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  String normalizeJoinTarget(String rawTarget, IrcProperties.Server server) {
    String target = normalize(rawTarget);
    if (target.isEmpty()) {
      throw new IllegalArgumentException("channel is blank");
    }
    if (looksLikeMatrixRoomId(target) || looksLikeMatrixRoomAlias(target)) {
      return target;
    }
    if (target.startsWith("#") && target.indexOf(':') < 0) {
      String host = homeserverHost.apply(server);
      if (!host.isEmpty()) {
        return target + ":" + host;
      }
    }
    throw new IllegalArgumentException("target is not a Matrix room id or alias");
  }

  String resolveAliasOrRoomId(
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

  String resolveHistoryRoomId(
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

  String resolveRealtimeSignalRoomId(
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

  String resolveMutationRoomId(
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

  String resolveDirectRoomId(
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

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
