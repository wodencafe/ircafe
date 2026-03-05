package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Executes Matrix room join/leave requests via the client API. */
@Component
@InfrastructureLayer
final class MatrixRoomMembershipClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-membership/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixRoomMembershipClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  JoinResult joinRoom(
      String serverId, IrcProperties.Server server, String accessToken, String roomIdOrAlias) {
    URI endpoint = MatrixEndpointResolver.joinRoomUri(server, roomIdOrAlias);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return JoinResult.failed(endpoint, "access token is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    try {
      HttpLite.Response<String> response =
          HttpLite.postString(
              endpoint, headers, "{}", plan.proxy(), plan.connectTimeoutMs(), plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return JoinResult.failed(endpoint, "HTTP " + code + " from join endpoint");
      }
      String roomId = parseRoomId(body);
      if (roomId.isEmpty()) {
        return JoinResult.failed(endpoint, "join response did not include room_id");
      }
      return JoinResult.joined(endpoint, roomId);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return JoinResult.failed(endpoint, message);
    }
  }

  LeaveResult leaveRoom(
      String serverId, IrcProperties.Server server, String accessToken, String roomId) {
    URI endpoint = MatrixEndpointResolver.leaveRoomUri(server, roomId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return LeaveResult.failed(endpoint, "access token is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    try {
      HttpLite.Response<String> response =
          HttpLite.postString(
              endpoint, headers, "{}", plan.proxy(), plan.connectTimeoutMs(), plan.readTimeoutMs());
      int code = response.statusCode();
      if (code < 200 || code >= 300) {
        return LeaveResult.failed(endpoint, "HTTP " + code + " from leave endpoint");
      }
      return LeaveResult.left(endpoint);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return LeaveResult.failed(endpoint, message);
    }
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

  record JoinResult(boolean joined, URI endpoint, String roomId, String detail) {
    static JoinResult joined(URI endpoint, String roomId) {
      return new JoinResult(
          true, Objects.requireNonNull(endpoint, "endpoint"), normalize(roomId), "");
    }

    static JoinResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "join failed";
      }
      return new JoinResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", message);
    }
  }

  record LeaveResult(boolean left, URI endpoint, String detail) {
    static LeaveResult left(URI endpoint) {
      return new LeaveResult(true, Objects.requireNonNull(endpoint, "endpoint"), "");
    }

    static LeaveResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "leave failed";
      }
      return new LeaveResult(false, Objects.requireNonNull(endpoint, "endpoint"), message);
    }
  }
}
