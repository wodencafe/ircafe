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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Fetches Matrix room timeline updates via {@code /_matrix/client/v3/sync}. */
@Component
@InfrastructureLayer
final class MatrixSyncClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-sync/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixSyncClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  SyncResult sync(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String sinceToken,
      int timeoutMs) {
    URI endpoint = MatrixEndpointResolver.syncUri(server, sinceToken, timeoutMs);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return SyncResult.failed(endpoint, "access token is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers =
        Map.of(
            "User-Agent", REQUEST_HEADERS.get("User-Agent"),
            "Accept", REQUEST_HEADERS.get("Accept"),
            "Accept-Encoding", REQUEST_HEADERS.get("Accept-Encoding"),
            "Authorization", "Bearer " + token);

    try {
      HttpLite.Response<String> response =
          HttpLite.getString(
              endpoint, headers, plan.proxy(), plan.connectTimeoutMs(), plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return SyncResult.failed(endpoint, "HTTP " + code + " from sync endpoint");
      }

      JsonNode root = JSON.readTree(body);
      String nextBatch = normalize(root.path("next_batch").asText(""));
      List<RoomTimelineEvent> events = parseRoomTimelineEvents(root);
      Map<String, String> directPeerByRoom = parseDirectRoomMappings(root);
      List<TypingEvent> typingEvents = parseTypingEvents(root);
      List<ReadReceiptEvent> readReceipts = parseReadReceiptEvents(root);
      return SyncResult.success(
          endpoint, nextBatch, events, directPeerByRoom, typingEvents, readReceipts);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return SyncResult.failed(endpoint, message);
    }
  }

  private static List<RoomTimelineEvent> parseRoomTimelineEvents(JsonNode root) {
    List<RoomTimelineEvent> events = new ArrayList<>();
    JsonNode joinedRooms = root.path("rooms").path("join");
    if (!joinedRooms.isObject()) {
      return List.copyOf(events);
    }

    joinedRooms
        .fields()
        .forEachRemaining(
            roomEntry -> {
              if (roomEntry == null) return;
              String roomId = normalize(roomEntry.getKey());
              if (roomId.isEmpty()) return;

              JsonNode timelineEvents = roomEntry.getValue().path("timeline").path("events");
              if (!timelineEvents.isArray()) return;

              for (JsonNode event : timelineEvents) {
                if (event == null || event.isNull()) continue;
                String type = normalize(event.path("type").asText(""));
                if (!"m.room.message".equals(type)) continue;

                JsonNode content = event.path("content");
                String sender = normalize(event.path("sender").asText(""));
                String eventId = normalize(event.path("event_id").asText(""));
                String msgType = normalize(content.path("msgtype").asText(""));
                String body = Objects.toString(content.path("body").asText(""), "");
                long originServerTs = event.path("origin_server_ts").asLong(0L);

                if (sender.isEmpty()) continue;
                if (body.trim().isEmpty()) continue;
                if (msgType.isEmpty()) msgType = "m.text";

                events.add(
                    new RoomTimelineEvent(roomId, sender, eventId, msgType, body, originServerTs));
              }
            });

    return List.copyOf(events);
  }

  private static Map<String, String> parseDirectRoomMappings(JsonNode root) {
    Map<String, String> directPeerByRoom = new HashMap<>();
    JsonNode accountDataEvents = root.path("account_data").path("events");
    if (!accountDataEvents.isArray()) {
      return Map.of();
    }

    for (JsonNode event : accountDataEvents) {
      if (event == null || event.isNull()) continue;
      String type = normalize(event.path("type").asText(""));
      if (!"m.direct".equals(type)) continue;

      JsonNode content = event.path("content");
      if (!content.isObject()) continue;

      content
          .fields()
          .forEachRemaining(
              peerEntry -> {
                if (peerEntry == null) return;
                String peerUserId = normalize(peerEntry.getKey());
                if (!looksLikeMatrixUserId(peerUserId)) return;

                JsonNode roomIds = peerEntry.getValue();
                if (!roomIds.isArray()) return;
                for (JsonNode roomIdNode : roomIds) {
                  if (roomIdNode == null || roomIdNode.isNull()) continue;
                  String roomId = normalize(roomIdNode.asText(""));
                  if (!looksLikeMatrixRoomId(roomId)) continue;
                  directPeerByRoom.put(roomId, peerUserId);
                }
              });
    }

    return directPeerByRoom.isEmpty() ? Map.of() : Map.copyOf(directPeerByRoom);
  }

  private static List<TypingEvent> parseTypingEvents(JsonNode root) {
    List<TypingEvent> typingEvents = new ArrayList<>();
    JsonNode joinedRooms = root.path("rooms").path("join");
    if (!joinedRooms.isObject()) {
      return List.of();
    }

    joinedRooms
        .fields()
        .forEachRemaining(
            roomEntry -> {
              if (roomEntry == null) return;
              String roomId = normalize(roomEntry.getKey());
              if (!looksLikeMatrixRoomId(roomId)) return;

              JsonNode ephemeralEvents = roomEntry.getValue().path("ephemeral").path("events");
              if (!ephemeralEvents.isArray()) return;

              for (JsonNode event : ephemeralEvents) {
                if (event == null || event.isNull()) continue;
                String type = normalize(event.path("type").asText(""));
                if (!"m.typing".equals(type)) continue;

                JsonNode userIdsNode = event.path("content").path("user_ids");
                LinkedHashSet<String> userIds = new LinkedHashSet<>();
                if (userIdsNode.isArray()) {
                  for (JsonNode userIdNode : userIdsNode) {
                    if (userIdNode == null || userIdNode.isNull()) continue;
                    String userId = normalize(userIdNode.asText(""));
                    if (!looksLikeMatrixUserId(userId)) continue;
                    userIds.add(userId);
                  }
                }

                typingEvents.add(new TypingEvent(roomId, List.copyOf(userIds)));
              }
            });

    return typingEvents.isEmpty() ? List.of() : List.copyOf(typingEvents);
  }

  private static List<ReadReceiptEvent> parseReadReceiptEvents(JsonNode root) {
    List<ReadReceiptEvent> receipts = new ArrayList<>();
    JsonNode joinedRooms = root.path("rooms").path("join");
    if (!joinedRooms.isObject()) {
      return List.of();
    }

    joinedRooms
        .fields()
        .forEachRemaining(
            roomEntry -> {
              if (roomEntry == null) return;
              String roomId = normalize(roomEntry.getKey());
              if (!looksLikeMatrixRoomId(roomId)) return;

              JsonNode ephemeralEvents = roomEntry.getValue().path("ephemeral").path("events");
              if (!ephemeralEvents.isArray()) return;

              for (JsonNode event : ephemeralEvents) {
                if (event == null || event.isNull()) continue;
                String type = normalize(event.path("type").asText(""));
                if (!"m.receipt".equals(type)) continue;

                JsonNode content = event.path("content");
                if (!content.isObject()) continue;
                content
                    .fields()
                    .forEachRemaining(
                        eventEntry -> {
                          if (eventEntry == null) return;
                          String eventId = normalize(eventEntry.getKey());
                          if (eventId.isEmpty()) return;
                          JsonNode byReceiptType = eventEntry.getValue();
                          if (!byReceiptType.isObject()) return;

                          byReceiptType
                              .fields()
                              .forEachRemaining(
                                  typeEntry -> {
                                    if (typeEntry == null) return;
                                    String receiptType = normalize(typeEntry.getKey());
                                    if (!isReadReceiptType(receiptType)) return;
                                    JsonNode byUser = typeEntry.getValue();
                                    if (!byUser.isObject()) return;

                                    byUser
                                        .fields()
                                        .forEachRemaining(
                                            userEntry -> {
                                              if (userEntry == null) return;
                                              String userId = normalize(userEntry.getKey());
                                              if (!looksLikeMatrixUserId(userId)) return;
                                              JsonNode userData = userEntry.getValue();
                                              long ts = userData.path("ts").asLong(0L);
                                              if (ts <= 0L) return;
                                              receipts.add(
                                                  new ReadReceiptEvent(
                                                      roomId, eventId, userId, ts));
                                            });
                                  });
                        });
              }
            });

    return receipts.isEmpty() ? List.of() : List.copyOf(receipts);
  }

  private static boolean isReadReceiptType(String type) {
    String token = normalize(type);
    return "m.read".equals(token) || "m.read.private".equals(token);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  private static boolean looksLikeMatrixUserId(String token) {
    String value = normalize(token);
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static boolean looksLikeMatrixRoomId(String token) {
    String value = normalize(token);
    if (!value.startsWith("!")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  record SyncResult(
      boolean success,
      URI endpoint,
      String nextBatch,
      List<RoomTimelineEvent> events,
      Map<String, String> directPeerByRoom,
      List<TypingEvent> typingEvents,
      List<ReadReceiptEvent> readReceipts,
      String detail) {
    static SyncResult success(URI endpoint, String nextBatch, List<RoomTimelineEvent> events) {
      return success(endpoint, nextBatch, events, Map.of(), List.of(), List.of());
    }

    static SyncResult success(
        URI endpoint,
        String nextBatch,
        List<RoomTimelineEvent> events,
        Map<String, String> directPeerByRoom) {
      return success(endpoint, nextBatch, events, directPeerByRoom, List.of(), List.of());
    }

    static SyncResult success(
        URI endpoint,
        String nextBatch,
        List<RoomTimelineEvent> events,
        Map<String, String> directPeerByRoom,
        List<TypingEvent> typingEvents,
        List<ReadReceiptEvent> readReceipts) {
      List<RoomTimelineEvent> safeEvents = events == null ? List.of() : List.copyOf(events);
      Map<String, String> safeDirectPeerByRoom =
          directPeerByRoom == null ? Map.of() : Map.copyOf(directPeerByRoom);
      List<TypingEvent> safeTypingEvents =
          typingEvents == null ? List.of() : List.copyOf(typingEvents);
      List<ReadReceiptEvent> safeReadReceipts =
          readReceipts == null ? List.of() : List.copyOf(readReceipts);
      return new SyncResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          normalize(nextBatch),
          safeEvents,
          safeDirectPeerByRoom,
          safeTypingEvents,
          safeReadReceipts,
          "");
    }

    static SyncResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "sync failed";
      }
      return new SyncResult(
          false,
          Objects.requireNonNull(endpoint, "endpoint"),
          "",
          List.of(),
          Map.of(),
          List.of(),
          List.of(),
          message);
    }
  }

  record RoomTimelineEvent(
      String roomId,
      String sender,
      String eventId,
      String msgType,
      String body,
      long originServerTs) {}

  record TypingEvent(String roomId, List<String> userIds) {
    TypingEvent {
      roomId = normalize(roomId);
      userIds = userIds == null ? List.of() : List.copyOf(userIds);
    }
  }

  record ReadReceiptEvent(String roomId, String eventId, String userId, long timestampMs) {
    ReadReceiptEvent {
      roomId = normalize(roomId);
      eventId = normalize(eventId);
      userId = normalize(userId);
      if (timestampMs < 0L) {
        timestampMs = 0L;
      }
    }
  }
}
