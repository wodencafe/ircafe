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

/** Probes Matrix homeserver reachability via {@code /_matrix/client/v3/versions}. */
@Component
@InfrastructureLayer
final class MatrixHomeserverProbe {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-probe/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixHomeserverProbe(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  ProbeResult probe(String serverId, IrcProperties.Server server) {
    URI versionsUri = MatrixEndpointResolver.versionsUri(server);
    ProxyPlan plan = proxyResolver.planForServer(serverId);

    try {
      HttpLite.Response<String> response =
          HttpLite.getString(
              versionsUri,
              REQUEST_HEADERS,
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return ProbeResult.failed(versionsUri, "HTTP " + code + " from versions endpoint");
      }

      int advertisedVersions = countAdvertisedVersions(body);
      if (advertisedVersions <= 0) {
        return ProbeResult.failed(
            versionsUri, "versions endpoint response did not include a non-empty versions array");
      }

      return ProbeResult.reachable(versionsUri, advertisedVersions);
    } catch (IOException ex) {
      String msg = Objects.toString(ex.getMessage(), "").trim();
      if (msg.isEmpty()) {
        msg = ex.getClass().getSimpleName();
      }
      return ProbeResult.failed(versionsUri, msg);
    }
  }

  WhoamiResult whoami(String serverId, IrcProperties.Server server, String accessToken) {
    URI whoamiUri = MatrixEndpointResolver.whoamiUri(server);
    ProxyPlan plan = proxyResolver.planForServer(serverId);
    String token = Objects.toString(accessToken, "").trim();
    if (token.isEmpty()) {
      return WhoamiResult.failed(whoamiUri, "access token is blank");
    }

    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    try {
      HttpLite.Response<String> response =
          HttpLite.getString(
              whoamiUri, headers, plan.proxy(), plan.connectTimeoutMs(), plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return WhoamiResult.failed(whoamiUri, "HTTP " + code + " from whoami endpoint");
      }

      JsonNode root = JSON.readTree(body);
      String userId = Objects.toString(root.path("user_id").asText(""), "").trim();
      String deviceId = Objects.toString(root.path("device_id").asText(""), "").trim();
      if (userId.isEmpty()) {
        return WhoamiResult.failed(whoamiUri, "whoami response did not include user_id");
      }
      return WhoamiResult.authenticated(whoamiUri, userId, deviceId);
    } catch (IOException ex) {
      String msg = Objects.toString(ex.getMessage(), "").trim();
      if (msg.isEmpty()) {
        msg = ex.getClass().getSimpleName();
      }
      return WhoamiResult.failed(whoamiUri, msg);
    }
  }

  private static int countAdvertisedVersions(String body) {
    String json = Objects.toString(body, "").trim();
    if (json.isEmpty()) return 0;
    try {
      JsonNode root = JSON.readTree(json);
      JsonNode versions = root.path("versions");
      return versions.isArray() ? versions.size() : 0;
    } catch (Exception ignored) {
      return 0;
    }
  }

  record ProbeResult(boolean reachable, URI endpoint, String detail, int advertisedVersionCount) {
    static ProbeResult reachable(URI endpoint, int advertisedVersionCount) {
      return new ProbeResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          "",
          Math.max(0, advertisedVersionCount));
    }

    static ProbeResult failed(URI endpoint, String detail) {
      String msg = Objects.toString(detail, "").trim();
      if (msg.isEmpty()) msg = "probe failed";
      return new ProbeResult(false, Objects.requireNonNull(endpoint, "endpoint"), msg, 0);
    }
  }

  record WhoamiResult(
      boolean authenticated, URI endpoint, String userId, String deviceId, String detail) {
    static WhoamiResult authenticated(URI endpoint, String userId, String deviceId) {
      return new WhoamiResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          Objects.toString(userId, "").trim(),
          Objects.toString(deviceId, "").trim(),
          "");
    }

    static WhoamiResult failed(URI endpoint, String detail) {
      String msg = Objects.toString(detail, "").trim();
      if (msg.isEmpty()) msg = "authentication failed";
      return new WhoamiResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", "", msg);
    }
  }
}
