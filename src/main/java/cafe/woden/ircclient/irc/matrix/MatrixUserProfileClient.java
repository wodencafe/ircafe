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

/** Fetches Matrix profile data via {@code /_matrix/client/v3/profile/{userId}}. */
@Component
@InfrastructureLayer
final class MatrixUserProfileClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-profile/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixUserProfileClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  ProfileResult fetchProfile(
      String serverId, IrcProperties.Server server, String accessToken, String userId) {
    URI endpoint = MatrixEndpointResolver.userProfileUri(server, userId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return ProfileResult.failed(endpoint, "access token is blank");
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
        return ProfileResult.failed(endpoint, "HTTP " + code + " from profile endpoint");
      }

      ParsedProfile profile = parseProfile(body);
      return ProfileResult.success(endpoint, userId, profile.displayName(), profile.avatarUrl());
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return ProfileResult.failed(endpoint, message);
    }
  }

  private static ParsedProfile parseProfile(String body) {
    String json = normalize(body);
    if (json.isEmpty()) {
      return new ParsedProfile("", "");
    }
    try {
      JsonNode root = JSON.readTree(json);
      String displayName = normalize(root.path("displayname").asText(""));
      String avatarUrl = normalize(root.path("avatar_url").asText(""));
      return new ParsedProfile(displayName, avatarUrl);
    } catch (Exception ignored) {
      return new ParsedProfile("", "");
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record ProfileResult(
      boolean success,
      URI endpoint,
      String userId,
      String displayName,
      String avatarUrl,
      String detail) {
    static ProfileResult success(
        URI endpoint, String userId, String displayName, String avatarUrl) {
      return new ProfileResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          normalize(userId),
          normalize(displayName),
          normalize(avatarUrl),
          "");
    }

    static ProfileResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "profile fetch failed";
      }
      return new ProfileResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), "", "", "", message);
    }
  }

  private record ParsedProfile(String displayName, String avatarUrl) {}
}
