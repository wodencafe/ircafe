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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Fetches the authenticated user's joined rooms via {@code /_matrix/client/v3/joined_rooms}. */
@Component
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixJoinedRoomsClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-joined-rooms/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  @NonNull private final ServerProxyResolver proxyResolver;

  JoinedRoomsResult fetchJoinedRooms(
      String serverId, IrcProperties.Server server, String accessToken) {
    URI endpoint = MatrixEndpointResolver.joinedRoomsUri(server);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return JoinedRoomsResult.failed(endpoint, "access token is blank");
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
        return JoinedRoomsResult.failed(endpoint, "HTTP " + code + " from joined rooms endpoint");
      }
      return JoinedRoomsResult.success(endpoint, parseJoinedRooms(body));
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return JoinedRoomsResult.failed(endpoint, message);
    }
  }

  private static List<String> parseJoinedRooms(String body) {
    String json = normalize(body);
    if (json.isEmpty()) return List.of();
    try {
      JsonNode root = JSON.readTree(json);
      JsonNode joinedRooms = root.path("joined_rooms");
      if (!joinedRooms.isArray()) {
        return List.of();
      }
      LinkedHashSet<String> dedup = new LinkedHashSet<>();
      for (JsonNode roomNode : joinedRooms) {
        String roomId = normalize(roomNode == null ? "" : roomNode.asText(""));
        if (!roomId.isEmpty()) {
          dedup.add(roomId);
        }
      }
      if (dedup.isEmpty()) {
        return List.of();
      }
      ArrayList<String> out = new ArrayList<>(dedup);
      out.sort(String::compareToIgnoreCase);
      return List.copyOf(out);
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record JoinedRoomsResult(boolean success, URI endpoint, List<String> roomIds, String detail) {
    static JoinedRoomsResult success(URI endpoint, List<String> roomIds) {
      List<String> ids = roomIds == null ? List.of() : List.copyOf(roomIds);
      return new JoinedRoomsResult(true, Objects.requireNonNull(endpoint, "endpoint"), ids, "");
    }

    static JoinedRoomsResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "joined rooms request failed";
      }
      return new JoinedRoomsResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), List.of(), message);
    }
  }
}
