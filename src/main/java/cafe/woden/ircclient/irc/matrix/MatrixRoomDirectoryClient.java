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

/**
 * Resolves Matrix room aliases to room IDs via {@code /_matrix/client/v3/directory/room/{alias}}.
 */
@Component
@InfrastructureLayer
final class MatrixRoomDirectoryClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-directory/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixRoomDirectoryClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

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
}
