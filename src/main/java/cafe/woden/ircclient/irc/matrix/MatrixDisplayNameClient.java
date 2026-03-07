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

/** Updates Matrix display name via {@code /_matrix/client/v3/profile/{userId}/displayname}. */
@Component
@InfrastructureLayer
final class MatrixDisplayNameClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-displayname/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixDisplayNameClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  UpdateResult setDisplayName(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String userId,
      String displayName) {
    URI endpoint = MatrixEndpointResolver.userDisplayNameUri(server, userId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return UpdateResult.failed(endpoint, "access token is blank");
    }
    String nick = normalize(displayName);
    if (nick.isEmpty()) {
      return UpdateResult.failed(endpoint, "display name is blank");
    }

    ObjectNode payload = JSON.createObjectNode();
    payload.put("displayname", nick);

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
        return UpdateResult.failed(endpoint, "HTTP " + code + " from displayname endpoint");
      }
      return UpdateResult.updated(endpoint, nick);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return UpdateResult.failed(endpoint, message);
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record UpdateResult(boolean updated, URI endpoint, String displayName, String detail) {
    static UpdateResult updated(URI endpoint, String displayName) {
      return new UpdateResult(
          true, Objects.requireNonNull(endpoint, "endpoint"), normalize(displayName), "");
    }

    static UpdateResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "display name update failed";
      }
      return new UpdateResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", message);
    }
  }
}
