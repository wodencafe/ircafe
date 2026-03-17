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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Fetches joined room members via {@code /_matrix/client/v3/rooms/{roomId}/joined_members}. */
@Component
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRoomRosterClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-roster/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  @NonNull private final ServerProxyResolver proxyResolver;

  RosterResult fetchJoinedMembers(
      String serverId, IrcProperties.Server server, String accessToken, String roomId) {
    URI endpoint = MatrixEndpointResolver.roomJoinedMembersUri(server, roomId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return RosterResult.failed(endpoint, "access token is blank");
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
        return RosterResult.failed(endpoint, "HTTP " + code + " from joined members endpoint");
      }

      return RosterResult.success(endpoint, parseJoinedMembers(body));
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return RosterResult.failed(endpoint, message);
    }
  }

  private static List<JoinedMember> parseJoinedMembers(String body) {
    String json = normalize(body);
    if (json.isEmpty()) return List.of();
    try {
      JsonNode root = JSON.readTree(json);
      JsonNode joined = root.path("joined");
      if (!joined.isObject()) {
        return List.of();
      }

      List<JoinedMember> members = new ArrayList<>();
      joined
          .fields()
          .forEachRemaining(
              entry -> {
                if (entry == null) return;
                String userId = normalize(entry.getKey());
                if (!looksLikeMatrixUserId(userId)) return;
                JsonNode member = entry.getValue();
                String displayName =
                    member == null || member.isNull()
                        ? ""
                        : normalize(member.path("display_name").asText(""));
                members.add(new JoinedMember(userId, displayName));
              });
      return List.copyOf(members);
    } catch (Exception ignored) {
      return List.of();
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

  record RosterResult(boolean success, URI endpoint, List<JoinedMember> members, String detail) {
    static RosterResult success(URI endpoint, List<JoinedMember> members) {
      List<JoinedMember> safeMembers = members == null ? List.of() : List.copyOf(members);
      return new RosterResult(true, Objects.requireNonNull(endpoint, "endpoint"), safeMembers, "");
    }

    static RosterResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "joined members fetch failed";
      }
      return new RosterResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), List.of(), message);
    }
  }

  record JoinedMember(String userId, String displayName) {
    JoinedMember {
      userId = normalize(userId);
      displayName = normalize(displayName);
    }
  }
}
