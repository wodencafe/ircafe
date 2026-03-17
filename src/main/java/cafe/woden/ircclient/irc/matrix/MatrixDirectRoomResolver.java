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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Resolves per-peer Matrix DM rooms by creating/locating direct rooms via {@code /createRoom}. */
@Component
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixDirectRoomResolver {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-dm/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  @NonNull private final ServerProxyResolver proxyResolver;

  ResolveResult resolveDirectRoom(
      String serverId, IrcProperties.Server server, String accessToken, String peerUserId) {
    URI endpoint = MatrixEndpointResolver.createRoomUri(server);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return ResolveResult.failed(endpoint, "access token is blank");
    }

    String peer = normalize(peerUserId);
    if (!looksLikeMatrixUserId(peer)) {
      return ResolveResult.failed(endpoint, "target is not a Matrix user id");
    }

    Map<String, Object> payload =
        Map.of("is_direct", true, "preset", "trusted_private_chat", "invite", List.of(peer));
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);
    ProxyPlan plan = proxyResolver.planForServer(serverId);

    try {
      String payloadJson = JSON.writeValueAsString(payload);
      HttpLite.Response<String> response =
          HttpLite.postString(
              endpoint,
              headers,
              payloadJson,
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return ResolveResult.failed(endpoint, "HTTP " + code + " from createRoom endpoint");
      }

      String roomId = parseRoomId(body);
      if (roomId.isEmpty()) {
        return ResolveResult.failed(endpoint, "createRoom response did not include room_id");
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

  private static boolean looksLikeMatrixUserId(String token) {
    String value = normalize(token);
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record ResolveResult(boolean resolved, URI endpoint, String roomId, String detail) {
    static ResolveResult resolved(URI endpoint, String roomId) {
      return new ResolveResult(true, Objects.requireNonNull(endpoint, "endpoint"), roomId, "");
    }

    static ResolveResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "direct room resolution failed";
      }
      return new ResolveResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", message);
    }
  }
}
