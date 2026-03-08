package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Authenticates Matrix sessions via {@code /_matrix/client/v3/login}. */
@Component
@InfrastructureLayer
final class MatrixLoginClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-login/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixLoginClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  LoginResult loginWithPassword(
      String serverId, IrcProperties.Server server, String username, String password) {
    URI endpoint = MatrixEndpointResolver.loginUri(server);
    String user = normalize(username);
    String pass = Objects.toString(password, "");
    if (user.isEmpty()) {
      return LoginResult.failed(endpoint, "username is blank");
    }
    if (pass.isBlank()) {
      return LoginResult.failed(endpoint, "password is blank");
    }

    ObjectNode payload = JSON.createObjectNode();
    payload.put("type", "m.login.password");
    ObjectNode identifier = payload.putObject("identifier");
    identifier.put("type", "m.id.user");
    identifier.put("user", user);
    payload.put("password", pass);

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    try {
      HttpLite.Response<String> response =
          HttpLite.postString(
              endpoint,
              REQUEST_HEADERS,
              payload.toString(),
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return LoginResult.failed(endpoint, "HTTP " + code + " from login endpoint");
      }
      JsonNode root = JSON.readTree(body);
      String accessToken = normalize(root.path("access_token").asText(""));
      String userId = normalize(root.path("user_id").asText(""));
      if (accessToken.isEmpty()) {
        return LoginResult.failed(endpoint, "login response did not include access_token");
      }
      if (userId.isEmpty()) {
        return LoginResult.failed(endpoint, "login response did not include user_id");
      }
      return LoginResult.authenticated(endpoint, userId, accessToken);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return LoginResult.failed(endpoint, message);
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record LoginResult(
      boolean authenticated, URI endpoint, String userId, String accessToken, String detail) {
    static LoginResult authenticated(URI endpoint, String userId, String accessToken) {
      return new LoginResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          normalize(userId),
          normalize(accessToken),
          "");
    }

    static LoginResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "authentication failed";
      }
      return new LoginResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", "", message);
    }
  }
}
