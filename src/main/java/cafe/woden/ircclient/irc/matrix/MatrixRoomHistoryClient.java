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
import java.util.Set;
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
  private static final String ENCRYPTED_PLACEHOLDER_BODY = "[encrypted message unavailable]";
  private static final Set<String> MEDIA_MSGTYPES =
      Set.of("m.image", "m.file", "m.video", "m.audio");

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
      ChunkParseResult chunk = parseChunk(root.path("chunk"));
      return HistoryResult.success(
          endpoint, endToken, chunk.events(), chunk.reactionEvents(), chunk.redactionEvents());
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

  private static ChunkParseResult parseChunk(JsonNode chunk) {
    if (chunk == null || !chunk.isArray()) {
      return ChunkParseResult.empty();
    }
    List<RoomHistoryEvent> events = new ArrayList<>();
    List<RoomReactionEvent> reactionEvents = new ArrayList<>();
    List<RoomRedactionEvent> redactionEvents = new ArrayList<>();
    for (JsonNode event : chunk) {
      if (event == null || event.isNull()) continue;
      String type = normalize(event.path("type").asText(""));
      if ("m.room.message".equals(type)) {
        JsonNode content = event.path("content");
        String sender = normalize(event.path("sender").asText(""));
        String eventId = normalize(event.path("event_id").asText(""));
        String msgType = normalize(content.path("msgtype").asText(""));
        if (msgType.isEmpty()) msgType = "m.text";
        String mediaUrl = parseMediaUrl(content, msgType);
        String body = resolveMessageBody(content, msgType, mediaUrl);
        String replyToEventId = parseReplyToEventId(content);
        long originServerTs = event.path("origin_server_ts").asLong(0L);
        if (sender.isEmpty() || body.trim().isEmpty()) continue;

        events.add(
            new RoomHistoryEvent(
                sender, eventId, msgType, body, replyToEventId, originServerTs, mediaUrl));
        continue;
      }

      if ("m.room.encrypted".equals(type)) {
        String sender = normalize(event.path("sender").asText(""));
        String eventId = normalize(event.path("event_id").asText(""));
        long originServerTs = event.path("origin_server_ts").asLong(0L);
        if (sender.isEmpty()) continue;
        events.add(
            new RoomHistoryEvent(
                sender,
                eventId,
                "m.room.encrypted",
                ENCRYPTED_PLACEHOLDER_BODY,
                "",
                originServerTs,
                ""));
        continue;
      }

      if ("m.reaction".equals(type)) {
        JsonNode relatesTo = event.path("content").path("m.relates_to");
        String relType = normalize(relatesTo.path("rel_type").asText(""));
        String sender = normalize(event.path("sender").asText(""));
        String eventId = normalize(event.path("event_id").asText(""));
        String targetEventId = normalize(relatesTo.path("event_id").asText(""));
        String reaction = normalize(relatesTo.path("key").asText(""));
        long originServerTs = event.path("origin_server_ts").asLong(0L);
        if (!"m.annotation".equals(relType)) continue;
        if (sender.isEmpty()
            || eventId.isEmpty()
            || targetEventId.isEmpty()
            || reaction.isEmpty()) {
          continue;
        }
        reactionEvents.add(
            new RoomReactionEvent(sender, eventId, targetEventId, reaction, originServerTs));
        continue;
      }

      if ("m.room.redaction".equals(type)) {
        String sender = normalize(event.path("sender").asText(""));
        String eventId = normalize(event.path("event_id").asText(""));
        String redactsEventId = normalize(event.path("redacts").asText(""));
        String reason = normalize(event.path("content").path("reason").asText(""));
        long originServerTs = event.path("origin_server_ts").asLong(0L);
        if (redactsEventId.isEmpty()) continue;
        redactionEvents.add(
            new RoomRedactionEvent(sender, eventId, redactsEventId, reason, originServerTs));
      }
    }
    return ChunkParseResult.of(events, reactionEvents, redactionEvents);
  }

  private static String parseReplyToEventId(JsonNode content) {
    if (content == null || content.isNull() || !content.isObject()) {
      return "";
    }
    JsonNode relatesTo = content.path("m.relates_to");
    String replyViaStable = normalize(relatesTo.path("m.in_reply_to").path("event_id").asText(""));
    if (!replyViaStable.isEmpty()) return replyViaStable;
    String replyViaLegacy = normalize(relatesTo.path("in_reply_to").path("event_id").asText(""));
    if (!replyViaLegacy.isEmpty()) return replyViaLegacy;
    String topLevelStable = normalize(content.path("m.in_reply_to").path("event_id").asText(""));
    if (!topLevelStable.isEmpty()) return topLevelStable;
    return normalize(content.path("in_reply_to").path("event_id").asText(""));
  }

  private static String parseMediaUrl(JsonNode content, String msgType) {
    if (!isMediaMsgType(msgType)) {
      return "";
    }
    if (content == null || content.isNull() || !content.isObject()) {
      return "";
    }
    String direct = normalize(content.path("url").asText(""));
    if (!direct.isEmpty()) return direct;
    return normalize(content.path("file").path("url").asText(""));
  }

  private static String resolveMessageBody(JsonNode content, String msgType, String mediaUrl) {
    String body = content == null ? "" : Objects.toString(content.path("body").asText(""), "");
    if (!body.trim().isEmpty()) {
      return body;
    }
    if (isMediaMsgType(msgType)) {
      return normalize(mediaUrl);
    }
    return body;
  }

  private static boolean isMediaMsgType(String msgType) {
    return MEDIA_MSGTYPES.contains(normalize(msgType));
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record HistoryResult(
      boolean success,
      URI endpoint,
      String endToken,
      List<RoomHistoryEvent> events,
      List<RoomReactionEvent> reactionEvents,
      List<RoomRedactionEvent> redactionEvents,
      String detail) {
    static HistoryResult success(URI endpoint, String endToken, List<RoomHistoryEvent> events) {
      return success(endpoint, endToken, events, List.of(), List.of());
    }

    static HistoryResult success(
        URI endpoint,
        String endToken,
        List<RoomHistoryEvent> events,
        List<RoomReactionEvent> reactionEvents,
        List<RoomRedactionEvent> redactionEvents) {
      List<RoomHistoryEvent> safeEvents = events == null ? List.of() : List.copyOf(events);
      List<RoomReactionEvent> safeReactionEvents =
          reactionEvents == null ? List.of() : List.copyOf(reactionEvents);
      List<RoomRedactionEvent> safeRedactionEvents =
          redactionEvents == null ? List.of() : List.copyOf(redactionEvents);
      return new HistoryResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          normalize(endToken),
          safeEvents,
          safeReactionEvents,
          safeRedactionEvents,
          "");
    }

    static HistoryResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "history fetch failed";
      }
      return new HistoryResult(
          false,
          Objects.requireNonNull(endpoint, "endpoint"),
          "",
          List.of(),
          List.of(),
          List.of(),
          message);
    }
  }

  record RoomReactionEvent(
      String sender, String eventId, String targetEventId, String reaction, long originServerTs) {}

  record RoomRedactionEvent(
      String sender, String eventId, String redactsEventId, String reason, long originServerTs) {}

  record RoomHistoryEvent(
      String sender,
      String eventId,
      String msgType,
      String body,
      String replyToEventId,
      long originServerTs,
      String mediaUrl) {
    RoomHistoryEvent(
        String sender,
        String eventId,
        String msgType,
        String body,
        String replyToEventId,
        long originServerTs) {
      this(sender, eventId, msgType, body, replyToEventId, originServerTs, "");
    }

    RoomHistoryEvent(
        String sender, String eventId, String msgType, String body, long originServerTs) {
      this(sender, eventId, msgType, body, "", originServerTs, "");
    }
  }

  private record ChunkParseResult(
      List<RoomHistoryEvent> events,
      List<RoomReactionEvent> reactionEvents,
      List<RoomRedactionEvent> redactionEvents) {
    private static ChunkParseResult empty() {
      return of(List.of(), List.of(), List.of());
    }

    private static ChunkParseResult of(
        List<RoomHistoryEvent> events,
        List<RoomReactionEvent> reactionEvents,
        List<RoomRedactionEvent> redactionEvents) {
      List<RoomHistoryEvent> safeEvents = events == null ? List.of() : List.copyOf(events);
      List<RoomReactionEvent> safeReactionEvents =
          reactionEvents == null ? List.of() : List.copyOf(reactionEvents);
      List<RoomRedactionEvent> safeRedactionEvents =
          redactionEvents == null ? List.of() : List.copyOf(redactionEvents);
      return new ChunkParseResult(safeEvents, safeReactionEvents, safeRedactionEvents);
    }
  }
}
