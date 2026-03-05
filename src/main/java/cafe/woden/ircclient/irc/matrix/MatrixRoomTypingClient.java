package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/**
 * Sends Matrix typing notifications via {@code /_matrix/client/v3/rooms/{roomId}/typing/{userId}}.
 */
@Component
@InfrastructureLayer
final class MatrixRoomTypingClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-typing/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixRoomTypingClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  TypingResult setTyping(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String userId,
      boolean typing,
      int timeoutMs) {
    URI endpoint = MatrixEndpointResolver.roomTypingUri(server, roomId, userId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return TypingResult.failed(endpoint, "access token is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    ObjectNode payload = JSON.createObjectNode();
    payload.put("typing", typing);
    if (typing) {
      payload.put("timeout", Math.max(1_000, timeoutMs));
    }

    try {
      HttpLite.Response<String> response =
          HttpLite.putString(
              endpoint,
              headers,
              payload.toString(),
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      if (code < 200 || code >= 300) {
        return TypingResult.failed(endpoint, "HTTP " + code + " from typing endpoint");
      }
      return TypingResult.success(endpoint, typing);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return TypingResult.failed(endpoint, message);
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record TypingResult(boolean success, URI endpoint, boolean typing, String detail) {
    static TypingResult success(URI endpoint, boolean typing) {
      return new TypingResult(true, Objects.requireNonNull(endpoint, "endpoint"), typing, "");
    }

    static TypingResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "typing update failed";
      }
      return new TypingResult(false, Objects.requireNonNull(endpoint, "endpoint"), false, message);
    }
  }
}
