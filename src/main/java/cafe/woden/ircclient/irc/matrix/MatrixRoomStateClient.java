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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Reads/writes Matrix room state events (topic + power levels). */
@Component
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRoomStateClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-state/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  @NonNull private final ServerProxyResolver proxyResolver;

  TopicResult fetchRoomTopic(
      String serverId, IrcProperties.Server server, String accessToken, String roomId) {
    URI endpoint = MatrixEndpointResolver.roomStateEventUri(server, roomId, "m.room.topic");
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return TopicResult.failed(endpoint, "access token is blank");
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
        return TopicResult.failed(endpoint, "HTTP " + code + " from room state endpoint");
      }
      String topic = parseTopic(body);
      return TopicResult.success(endpoint, topic);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return TopicResult.failed(endpoint, message);
    }
  }

  UpdateResult updateRoomTopic(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String topic) {
    URI endpoint = MatrixEndpointResolver.roomStateEventUri(server, roomId, "m.room.topic");
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return UpdateResult.failed(endpoint, "access token is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    try {
      String payload = JSON.writeValueAsString(Map.of("topic", Objects.toString(topic, "")));
      HttpLite.Response<String> response =
          HttpLite.putString(
              endpoint,
              headers,
              payload,
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      if (code < 200 || code >= 300) {
        return UpdateResult.failed(endpoint, "HTTP " + code + " from room state endpoint");
      }
      return UpdateResult.updated(endpoint);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return UpdateResult.failed(endpoint, message);
    }
  }

  PowerLevelsResult fetchRoomPowerLevels(
      String serverId, IrcProperties.Server server, String accessToken, String roomId) {
    URI endpoint = MatrixEndpointResolver.roomStateEventUri(server, roomId, "m.room.power_levels");
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return PowerLevelsResult.failed(endpoint, "access token is blank");
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
        return PowerLevelsResult.failed(endpoint, "HTTP " + code + " from room state endpoint");
      }
      Map<String, Object> content = parseJsonObject(body);
      if (content.isEmpty()) {
        return PowerLevelsResult.success(endpoint, defaultPowerLevelsState());
      }
      return PowerLevelsResult.success(endpoint, content);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return PowerLevelsResult.failed(endpoint, message);
    }
  }

  UpdateResult updateRoomPowerLevels(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      Map<String, Object> stateContent) {
    URI endpoint = MatrixEndpointResolver.roomStateEventUri(server, roomId, "m.room.power_levels");
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return UpdateResult.failed(endpoint, "access token is blank");
    }

    Map<String, Object> payload =
        stateContent == null || stateContent.isEmpty()
            ? defaultPowerLevelsState()
            : deepCopyMap(stateContent);

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    try {
      String body = JSON.writeValueAsString(payload);
      HttpLite.Response<String> response =
          HttpLite.putString(
              endpoint, headers, body, plan.proxy(), plan.connectTimeoutMs(), plan.readTimeoutMs());
      int code = response.statusCode();
      if (code < 200 || code >= 300) {
        return UpdateResult.failed(endpoint, "HTTP " + code + " from room state endpoint");
      }
      return UpdateResult.updated(endpoint);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return UpdateResult.failed(endpoint, message);
    }
  }

  static Map<String, Object> defaultPowerLevelsState() {
    return Map.of("users_default", Integer.valueOf(0), "users", Map.of());
  }

  private static String parseTopic(String body) {
    String json = normalize(body);
    if (json.isEmpty()) return "";
    try {
      JsonNode root = JSON.readTree(json);
      return Objects.toString(root.path("topic").asText(""), "");
    } catch (Exception ignored) {
      return "";
    }
  }

  private static Map<String, Object> parseJsonObject(String body) {
    String json = normalize(body);
    if (json.isEmpty()) {
      return Map.of();
    }
    try {
      JsonNode root = JSON.readTree(json);
      if (!root.isObject()) {
        return Map.of();
      }
      return jsonObjectToMap(root);
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private static Map<String, Object> jsonObjectToMap(JsonNode node) {
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    if (node == null || !node.isObject()) {
      return out;
    }
    node.fields()
        .forEachRemaining(
            entry -> {
              if (entry == null) return;
              String key = normalize(entry.getKey());
              if (key.isEmpty()) return;
              out.put(key, jsonToValue(entry.getValue()));
            });
    return out;
  }

  private static Object jsonToValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isObject()) {
      return jsonObjectToMap(node);
    }
    if (node.isArray()) {
      ArrayList<Object> out = new ArrayList<>();
      for (JsonNode child : node) {
        out.add(jsonToValue(child));
      }
      return List.copyOf(out);
    }
    if (node.isBoolean()) {
      return Boolean.valueOf(node.asBoolean());
    }
    if (node.isIntegralNumber()) {
      return Long.valueOf(node.asLong());
    }
    if (node.isFloatingPointNumber()) {
      return Double.valueOf(node.asDouble());
    }
    return Objects.toString(node.asText(""), "");
  }

  private static Map<String, Object> deepCopyMap(Map<String, Object> input) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    if (input == null || input.isEmpty()) {
      return copy;
    }
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      if (entry == null) continue;
      String key = normalize(entry.getKey());
      if (key.isEmpty()) continue;
      copy.put(key, deepCopyValue(entry.getValue()));
    }
    return copy;
  }

  private static Object deepCopyValue(Object value) {
    if (value == null) return null;
    if (value instanceof Map<?, ?> mapValue) {
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        if (entry == null) continue;
        String key = normalize(Objects.toString(entry.getKey(), ""));
        if (key.isEmpty()) continue;
        out.put(key, deepCopyValue(entry.getValue()));
      }
      return out;
    }
    if (value instanceof List<?> listValue) {
      ArrayList<Object> out = new ArrayList<>();
      for (Object item : listValue) {
        out.add(deepCopyValue(item));
      }
      return List.copyOf(out);
    }
    return value;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record TopicResult(boolean success, URI endpoint, String topic, String detail) {
    static TopicResult success(URI endpoint, String topic) {
      return new TopicResult(true, Objects.requireNonNull(endpoint, "endpoint"), topic, "");
    }

    static TopicResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "topic request failed";
      }
      return new TopicResult(false, Objects.requireNonNull(endpoint, "endpoint"), "", message);
    }
  }

  record PowerLevelsResult(
      boolean success, URI endpoint, Map<String, Object> content, String detail) {
    static PowerLevelsResult success(URI endpoint, Map<String, Object> content) {
      Map<String, Object> data = content == null ? Map.of() : deepCopyMap(content);
      return new PowerLevelsResult(true, Objects.requireNonNull(endpoint, "endpoint"), data, "");
    }

    static PowerLevelsResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "power-level request failed";
      }
      return new PowerLevelsResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), Map.of(), message);
    }
  }

  record UpdateResult(boolean updated, URI endpoint, String detail) {
    static UpdateResult updated(URI endpoint) {
      return new UpdateResult(true, Objects.requireNonNull(endpoint, "endpoint"), "");
    }

    static UpdateResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "state update failed";
      }
      return new UpdateResult(false, Objects.requireNonNull(endpoint, "endpoint"), message);
    }
  }
}
