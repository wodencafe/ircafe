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
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Fetches Matrix room history via {@code /_matrix/client/v3/rooms/{roomId}/messages}. */
@Component
@InfrastructureLayer
final class MatrixRoomHistoryClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-history/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixRoomHistoryClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  HistoryResult fetchMessagesBefore(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String fromToken,
      int limit) {
    return fetchMessages(
        serverId, server, accessToken, roomId, fromToken, "", Direction.BACKWARD, limit);
  }

  HistoryResult fetchMessagesAfter(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String fromToken,
      int limit) {
    return fetchMessages(
        serverId, server, accessToken, roomId, fromToken, "", Direction.FORWARD, limit);
  }

  HistoryResult fetchMessages(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String fromToken,
      String toToken,
      Direction direction,
      int limit) {
    Direction dir = direction == null ? Direction.BACKWARD : direction;
    URI endpoint =
        MatrixEndpointResolver.roomMessagesUri(
            server, roomId, fromToken, toToken, dir.queryToken(), limit);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return HistoryResult.failed(endpoint, "access token is blank");
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
        return HistoryResult.failed(endpoint, "HTTP " + code + " from room messages endpoint");
      }

      JsonNode root = JSON.readTree(body);
      String endToken = normalize(root.path("end").asText(""));
      List<RoomHistoryEvent> events = parseChunk(root.path("chunk"));
      return HistoryResult.success(endpoint, endToken, events);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return HistoryResult.failed(endpoint, message);
    }
  }

  enum Direction {
    BACKWARD("b"),
    FORWARD("f");

    private final String queryToken;

    Direction(String queryToken) {
      this.queryToken = queryToken;
    }

    private String queryToken() {
      return queryToken;
    }
  }

  private static List<RoomHistoryEvent> parseChunk(JsonNode chunk) {
    if (chunk == null || !chunk.isArray()) {
      return List.of();
    }
    List<RoomHistoryEvent> events = new ArrayList<>();
    for (JsonNode event : chunk) {
      if (event == null || event.isNull()) continue;
      String type = normalize(event.path("type").asText(""));
      if (!"m.room.message".equals(type)) continue;

      JsonNode content = event.path("content");
      String sender = normalize(event.path("sender").asText(""));
      String eventId = normalize(event.path("event_id").asText(""));
      String msgType = normalize(content.path("msgtype").asText(""));
      String body = Objects.toString(content.path("body").asText(""), "");
      long originServerTs = event.path("origin_server_ts").asLong(0L);
      if (sender.isEmpty() || body.trim().isEmpty()) continue;
      if (msgType.isEmpty()) msgType = "m.text";

      events.add(new RoomHistoryEvent(sender, eventId, msgType, body, originServerTs));
    }
    return List.copyOf(events);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record HistoryResult(
      boolean success,
      URI endpoint,
      String endToken,
      List<RoomHistoryEvent> events,
      String detail) {
    static HistoryResult success(URI endpoint, String endToken, List<RoomHistoryEvent> events) {
      List<RoomHistoryEvent> safeEvents = events == null ? List.of() : List.copyOf(events);
      return new HistoryResult(
          true, Objects.requireNonNull(endpoint, "endpoint"), normalize(endToken), safeEvents, "");
    }

    static HistoryResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "history fetch failed";
      }
      return new HistoryResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), "", List.of(), message);
    }
  }

  record RoomHistoryEvent(
      String sender, String eventId, String msgType, String body, long originServerTs) {}
}
