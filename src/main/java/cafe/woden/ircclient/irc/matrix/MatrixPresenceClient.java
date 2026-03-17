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
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Sets Matrix presence state via {@code /_matrix/client/v3/presence/{userId}/status}. */
@Component
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixPresenceClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-presence/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  @NonNull private final ServerProxyResolver proxyResolver;

  PresenceResult setAwayStatus(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String userId,
      String awayMessage) {
    URI endpoint = MatrixEndpointResolver.userPresenceStatusUri(server, userId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return PresenceResult.failed(endpoint, "access token is blank");
    }

    String msg = normalize(awayMessage);
    boolean away = !msg.isEmpty();
    ObjectNode payload = JSON.createObjectNode();
    payload.put("presence", away ? "unavailable" : "online");
    if (away) {
      payload.put("status_msg", msg);
    } else {
      payload.put("status_msg", "");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

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
        return PresenceResult.failed(endpoint, "HTTP " + code + " from presence endpoint");
      }
      return PresenceResult.success(endpoint, away, msg);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return PresenceResult.failed(endpoint, message);
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record PresenceResult(
      boolean success, URI endpoint, boolean away, String awayMessage, String detail) {
    static PresenceResult success(URI endpoint, boolean away, String awayMessage) {
      return new PresenceResult(
          true, Objects.requireNonNull(endpoint, "endpoint"), away, normalize(awayMessage), "");
    }

    static PresenceResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "presence update failed";
      }
      return new PresenceResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), false, "", message);
    }
  }
}
