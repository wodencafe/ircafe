package cafe.woden.ircclient.notify.pushy;

import cafe.woden.ircclient.app.notifications.IrcEventNotificationRule;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Optional Pushy integration for IRC event notifications.
 *
 * <p>This is intentionally best-effort and never throws to callers.
 */
@Component
@Lazy
public class PushyNotificationService {

  private static final Logger log = LoggerFactory.getLogger(PushyNotificationService.class);

  private final PushyProperties properties;
  private final HttpClient client;
  private final ExecutorService executor = VirtualThreads.newThreadPerTaskExecutor("ircafe-pushy");

  public PushyNotificationService(PushyProperties properties) {
    this.properties = properties != null
        ? properties
        : new PushyProperties(false, null, null, null, null, null, null, null);
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(this.properties.connectTimeoutSeconds()))
        .build();
  }

  public boolean notifyEvent(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body
  ) {
    if (!properties.configured()) return false;

    String endpoint = Objects.toString(properties.endpoint(), "").trim();
    String apiKey = Objects.toString(properties.apiKey(), "").trim();
    if (endpoint.isEmpty() || apiKey.isEmpty()) return false;

    String finalTitle = buildTitle(title);
    String finalBody = Objects.toString(body, "").trim();
    if (finalBody.isEmpty()) finalBody = Objects.toString(eventType, "Event");

    String payload = buildPayload(
        eventType,
        serverId,
        channel,
        sourceNick,
        sourceIsSelf,
        finalTitle,
        finalBody);
    if (payload == null || payload.isBlank()) return false;

    String url = endpoint + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    executor.execute(() -> sendPush(url, payload));
    return true;
  }

  @PreDestroy
  void shutdown() {
    try {
      executor.shutdownNow();
    } catch (Exception ignored) {
    }
  }

  private void sendPush(String url, String payload) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        String body = Objects.toString(response.body(), "").trim();
        if (body.length() > 240) body = body.substring(0, 240) + "...";
        log.warn("[ircafe] Pushy request failed: status={} body={}", status, body);
      }
    } catch (Exception e) {
      log.debug("[ircafe] Pushy request failed", e);
    }
  }

  private String buildPayload(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body
  ) {
    String to = Objects.toString(properties.deviceToken(), "").trim();
    String topic = Objects.toString(properties.topic(), "").trim();
    if (to.isEmpty() && topic.isEmpty()) return null;

    StringBuilder json = new StringBuilder(512);
    json.append('{');
    if (!to.isEmpty()) {
      appendJsonField(json, "to", to, false);
    } else {
      appendJsonField(json, "topic", topic, false);
    }
    json.append(',');
    json.append("\"notification\":{");
    appendJsonField(json, "title", title, false);
    json.append(',');
    appendJsonField(json, "body", body, false);
    json.append("},");
    json.append("\"data\":{");
    appendJsonField(json, "eventType", eventType != null ? eventType.name() : "", false);
    json.append(',');
    appendJsonField(json, "serverId", Objects.toString(serverId, ""), false);
    json.append(',');
    appendJsonField(json, "channel", Objects.toString(channel, ""), false);
    json.append(',');
    appendJsonField(json, "sourceNick", Objects.toString(sourceNick, ""), false);
    json.append(',');
    appendJsonField(json, "sourceIsSelf", sourceIsSelf == null ? "unknown" : sourceIsSelf.toString(), false);
    json.append(',');
    appendJsonField(json, "timestampMs", Long.toString(System.currentTimeMillis()), false);
    json.append('}');
    json.append('}');
    return json.toString();
  }

  private String buildTitle(String title) {
    String prefix = Objects.toString(properties.titlePrefix(), "").trim();
    String t = Objects.toString(title, "").trim();
    if (t.isEmpty()) t = "IRC Event";
    if (prefix.isEmpty()) return t;
    if (t.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) return t;
    return prefix + " - " + t;
  }

  private static void appendJsonField(StringBuilder out, String key, String value, boolean includeComma) {
    if (includeComma) out.append(',');
    out.append('"').append(escapeJson(key)).append('"').append(':');
    out.append('"').append(escapeJson(Objects.toString(value, ""))).append('"');
  }

  private static String escapeJson(String raw) {
    String s = Objects.toString(raw, "");
    StringBuilder out = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }
}
