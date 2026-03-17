package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/**
 * Resolves Matrix room aliases to room IDs via {@code /_matrix/client/v3/directory/room/{alias}}.
 */
@Component
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRoomDirectoryClient {

  private static final int DEFAULT_PUBLIC_ROOMS_LIMIT = 100;
  private static final int MAX_PUBLIC_ROOMS_LIMIT = 200;

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-directory/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  @NonNull private final ServerProxyResolver proxyResolver;

  ResolveResult resolveRoomAlias(
      String serverId, IrcProperties.Server server, String accessToken, String roomAlias) {
    URI endpoint = MatrixEndpointResolver.roomAliasDirectoryUri(server, roomAlias);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return ResolveResult.failed(endpoint, "access token is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    try {
      HttpLite.Response<String> response =
          HttpLite.getString(
              endpoint, headers, plan.proxy(), plan.connectTimeoutMs(), plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return ResolveResult.failed(endpoint, "HTTP " + code + " from room directory endpoint");
      }

      String roomId = parseRoomId(body);
      if (roomId.isEmpty()) {
        return ResolveResult.failed(endpoint, "room directory response did not include room_id");
      }
      return ResolveResult.resolved(endpoint, roomId);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return ResolveResult.failed(endpoint, message);
    }
  }

  PublicRoomsResult fetchPublicRooms(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String searchTerm,
      String sinceToken,
      int limit) {
    URI endpoint = MatrixEndpointResolver.publicRoomsUri(server);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return PublicRoomsResult.failed(endpoint, "access token is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    try {
      String payload =
          JSON.writeValueAsString(buildPublicRoomsRequest(searchTerm, sinceToken, limit));
      HttpLite.Response<String> response =
          HttpLite.postString(
              endpoint,
              headers,
              payload,
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return PublicRoomsResult.failed(endpoint, "HTTP " + code + " from public rooms endpoint");
      }

      ParsedPublicRooms parsed = parsePublicRooms(body);
      return PublicRoomsResult.success(endpoint, parsed.rooms(), parsed.nextBatch());
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return PublicRoomsResult.failed(endpoint, message);
    }
  }

  private static Map<String, Object> buildPublicRoomsRequest(
      String searchTerm, String sinceToken, int limit) {
    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
    payload.put("limit", normalizePublicRoomLimit(limit));

    String since = normalize(sinceToken);
    if (!since.isEmpty()) {
      payload.put("since", since);
    }

    String search = normalize(searchTerm);
    if (!search.isEmpty()) {
      payload.put("filter", Map.of("generic_search_term", search));
    }
    return payload;
  }

  private static ParsedPublicRooms parsePublicRooms(String body) {
    String json = normalize(body);
    if (json.isEmpty()) return ParsedPublicRooms.empty();
    try {
      JsonNode root = JSON.readTree(json);
      JsonNode chunk = root.path("chunk");
      List<PublicRoom> rooms = parsePublicRoomChunk(chunk);
      String nextBatch = normalize(root.path("next_batch").asText(""));
      return new ParsedPublicRooms(rooms, nextBatch);
    } catch (Exception ignored) {
      return ParsedPublicRooms.empty();
    }
  }

  private static List<PublicRoom> parsePublicRoomChunk(JsonNode chunkNode) {
    if (chunkNode == null || !chunkNode.isArray()) return List.of();
    ArrayList<PublicRoom> rooms = new ArrayList<>();
    LinkedHashSet<String> dedupe = new LinkedHashSet<>();
    for (JsonNode roomNode : chunkNode) {
      PublicRoom room = parsePublicRoom(roomNode);
      if (room == null) continue;
      String key = room.roomId().isEmpty() ? room.canonicalAlias() : room.roomId();
      if (key.isEmpty()) continue;
      if (!dedupe.add(key)) continue;
      rooms.add(room);
    }
    if (rooms.isEmpty()) return List.of();
    return List.copyOf(rooms);
  }

  private static PublicRoom parsePublicRoom(JsonNode roomNode) {
    if (roomNode == null || !roomNode.isObject()) return null;
    String roomId = normalize(roomNode.path("room_id").asText(""));
    String canonicalAlias = normalize(roomNode.path("canonical_alias").asText(""));
    if (canonicalAlias.isEmpty()) {
      canonicalAlias = firstAlias(roomNode.path("aliases"));
    }
    String name = normalize(roomNode.path("name").asText(""));
    String topic = normalize(roomNode.path("topic").asText(""));
    int joinedMembers = parseJoinedMembers(roomNode.path("num_joined_members"));
    if (roomId.isEmpty() && canonicalAlias.isEmpty()) {
      return null;
    }
    return new PublicRoom(roomId, canonicalAlias, name, topic, joinedMembers);
  }

  private static String firstAlias(JsonNode aliases) {
    if (aliases == null || !aliases.isArray()) {
      return "";
    }
    for (JsonNode aliasNode : aliases) {
      String alias = normalize(aliasNode == null ? "" : aliasNode.asText(""));
      if (!alias.isEmpty()) {
        return alias;
      }
    }
    return "";
  }

  private static int parseJoinedMembers(JsonNode value) {
    if (value == null || value.isNull()) return 0;
    if (value.isIntegralNumber()) {
      return Math.max(0, value.asInt(0));
    }
    try {
      return Math.max(0, Integer.parseInt(normalize(value.asText(""))));
    } catch (Exception ignored) {
      return 0;
    }
  }

  private static int normalizePublicRoomLimit(int limit) {
    int requested = limit <= 0 ? DEFAULT_PUBLIC_ROOMS_LIMIT : limit;
    return Math.max(1, Math.min(requested, MAX_PUBLIC_ROOMS_LIMIT));
  }

  private static String parseRoomId(String body) {
    String json = normalize(body);
    if (json.isEmpty()) return "";
    try {
      JsonNode root = JSON.readTree(json);
      return normalize(root.path("room_id").asText(""));
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record ResolveResult(boolean resolved, URI endpoint, String roomId, String detail) {
    static ResolveResult resolved(URI endpoint, String roomId) {
      return new ResolveResult(
          true, Objects.requireNonNull(endpoint, "endpoint"), normalize(roomId), "");
    }

    static ResolveResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "room directory lookup failed";
      }
      return new ResolveResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", message);
    }
  }

  record PublicRoomsResult(
      boolean success, URI endpoint, List<PublicRoom> rooms, String nextBatch, String detail) {
    static PublicRoomsResult success(URI endpoint, List<PublicRoom> rooms, String nextBatch) {
      List<PublicRoom> safeRooms = rooms == null ? List.of() : List.copyOf(rooms);
      return new PublicRoomsResult(
          true, Objects.requireNonNull(endpoint, "endpoint"), safeRooms, normalize(nextBatch), "");
    }

    static PublicRoomsResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "public rooms lookup failed";
      }
      return new PublicRoomsResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), List.of(), "", message);
    }
  }

  record PublicRoom(
      String roomId, String canonicalAlias, String name, String topic, int joinedMembers) {
    PublicRoom {
      roomId = normalize(roomId);
      canonicalAlias = normalize(canonicalAlias);
      name = normalize(name);
      topic = normalize(topic);
      joinedMembers = Math.max(0, joinedMembers);
    }
  }

  private record ParsedPublicRooms(List<PublicRoom> rooms, String nextBatch) {
    private ParsedPublicRooms {
      rooms = rooms == null ? List.of() : List.copyOf(rooms);
      nextBatch = normalize(nextBatch);
    }

    static ParsedPublicRooms empty() {
      return new ParsedPublicRooms(List.of(), "");
    }
  }
}
