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

/** Sends Matrix room messages via the client API. */
@Component
@InfrastructureLayer
final class MatrixRoomMessageSender {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-send/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String CTCP_ACTION_PREFIX = "\u0001ACTION ";
  private static final String CTCP_SUFFIX = "\u0001";

  private final ServerProxyResolver proxyResolver;

  MatrixRoomMessageSender(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  SendResult sendRoomMessage(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String transactionId,
      String message) {
    return sendRoomMessage(serverId, server, accessToken, roomId, transactionId, message, "");
  }

  SendResult sendRoomNotice(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String transactionId,
      String message) {
    return sendRoomMessage(
        serverId, server, accessToken, roomId, transactionId, message, "m.notice");
  }

  private SendResult sendRoomMessage(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String transactionId,
      String message,
      String msgTypeOverride) {
    URI endpoint = MatrixEndpointResolver.roomSendMessageUri(server, roomId, transactionId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return SendResult.failed(endpoint, "access token is blank");
    }
    String bodyText = Objects.toString(message, "");
    if (bodyText.trim().isEmpty()) {
      return SendResult.failed(endpoint, "message is blank");
    }

    MessageContent content = messageContent(bodyText, msgTypeOverride);
    Map<String, Object> payload =
        Map.of(
            "msgtype", content.msgtype(),
            "body", content.body());
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);
    ProxyPlan plan = proxyResolver.planForServer(serverId);

    try {
      String payloadJson = JSON.writeValueAsString(payload);
      HttpLite.Response<String> response =
          HttpLite.postString(
              endpoint,
              headers,
              payloadJson,
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return SendResult.failed(endpoint, "HTTP " + code + " from room send endpoint");
      }

      String eventId = parseEventId(body);
      return SendResult.accepted(endpoint, eventId);
    } catch (IOException ex) {
      String msg = normalize(ex.getMessage());
      if (msg.isEmpty()) {
        msg = ex.getClass().getSimpleName();
      }
      return SendResult.failed(endpoint, msg);
    }
  }

  private static String parseEventId(String body) {
    String json = normalize(body);
    if (json.isEmpty()) return "";
    try {
      JsonNode root = JSON.readTree(json);
      return normalize(root.path("event_id").asText(""));
    } catch (Exception ignored) {
      return "";
    }
  }

  private static MessageContent messageContent(String text, String msgTypeOverride) {
    String raw = Objects.toString(text, "");
    String override = normalize(msgTypeOverride);
    if (!override.isEmpty()) {
      return new MessageContent(override, raw);
    }
    if (raw.startsWith(CTCP_ACTION_PREFIX)
        && raw.endsWith(CTCP_SUFFIX)
        && raw.length() > (CTCP_ACTION_PREFIX.length() + CTCP_SUFFIX.length())) {
      String emote = raw.substring(CTCP_ACTION_PREFIX.length(), raw.length() - 1).trim();
      if (!emote.isEmpty()) {
        return new MessageContent("m.emote", emote);
      }
    }
    return new MessageContent("m.text", raw);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record SendResult(boolean accepted, URI endpoint, String eventId, String detail) {
    static SendResult accepted(URI endpoint, String eventId) {
      return new SendResult(
          true, Objects.requireNonNull(endpoint, "endpoint"), normalize(eventId), "");
    }

    static SendResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "send failed";
      }
      return new SendResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", message);
    }
  }

  private record MessageContent(String msgtype, String body) {}
}
