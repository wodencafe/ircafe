package cafe.woden.ircclient.notify.pushy;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
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
import org.springframework.beans.factory.annotation.Qualifier;
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
  private static final PushyProperties DISABLED_DEFAULTS =
      new PushyProperties(false, null, null, null, null, null, null, null);

  private final PushySettingsBus settingsBus;
  private final ExecutorService executor;

  public PushyNotificationService(
      PushySettingsBus settingsBus,
      @Qualifier(ExecutorConfig.PUSHY_NOTIFICATION_EXECUTOR) ExecutorService executor) {
    this.settingsBus = settingsBus;
    this.executor = executor;
  }

  public boolean notifyEvent(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body) {
    PushyProperties properties = currentProperties();
    if (!properties.configured()) return false;

    String endpoint = Objects.toString(properties.endpoint(), "").trim();
    String apiKey = Objects.toString(properties.apiKey(), "").trim();
    if (endpoint.isEmpty() || apiKey.isEmpty()) return false;

    String finalTitle = buildTitle(properties, title);
    String finalBody = Objects.toString(body, "").trim();
    if (finalBody.isEmpty()) finalBody = Objects.toString(eventType, "Event");

    String payload =
        buildPayload(
            properties,
            eventType,
            serverId,
            channel,
            sourceNick,
            sourceIsSelf,
            finalTitle,
            finalBody);
    if (payload == null || payload.isBlank()) return false;

    String url = endpoint + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    executor.execute(() -> sendPush(properties, url, payload));
    return true;
  }

  public PushResult sendTestNotification(PushyProperties settings, String title, String body) {
    PushyProperties properties = settings != null ? settings : currentProperties();
    if (!Boolean.TRUE.equals(properties.enabled())) {
      return PushResult.failed("Pushy is disabled.");
    }

    String endpoint = Objects.toString(properties.endpoint(), "").trim();
    String apiKey = Objects.toString(properties.apiKey(), "").trim();
    if (endpoint.isEmpty() || apiKey.isEmpty()) {
      return PushResult.failed("Pushy endpoint and API key are required.");
    }

    String finalTitle = buildTitle(properties, title);
    String finalBody = Objects.toString(body, "").trim();
    if (finalBody.isEmpty()) finalBody = "Pushy integration test from IRCafe.";

    String payload =
        buildPayload(
            properties,
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            "local",
            "status",
            "ircafe",
            false,
            finalTitle,
            finalBody);
    if (payload == null || payload.isBlank()) {
      return PushResult.failed("Set either a device token or topic destination.");
    }

    String url = endpoint + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    return sendPush(properties, url, payload);
  }

  private PushResult sendPush(PushyProperties properties, String url, String payload) {
    try {
      HttpClient client =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
              .build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        String body = Objects.toString(response.body(), "").trim();
        if (body.length() > 240) body = body.substring(0, 240) + "...";
        log.warn("[ircafe] Pushy request failed: status={} body={}", status, body);
        return PushResult.failed("Pushy request failed (" + status + ").");
      }
      return PushResult.success("Push sent (HTTP " + status + ").");
    } catch (Exception e) {
      log.debug("[ircafe] Pushy request failed", e);
      String msg = Objects.toString(e.getMessage(), "").trim();
      if (msg.isEmpty()) msg = e.getClass().getSimpleName();
      return PushResult.failed(msg);
    }
  }

  private String buildPayload(
      PushyProperties properties,
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      Boolean sourceIsSelf,
      String title,
      String body) {
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
    appendJsonField(
        json, "sourceIsSelf", sourceIsSelf == null ? "unknown" : sourceIsSelf.toString(), false);
    json.append(',');
    appendJsonField(json, "timestampMs", Long.toString(System.currentTimeMillis()), false);
    json.append('}');
    json.append('}');
    return json.toString();
  }

  private String buildTitle(PushyProperties properties, String title) {
    String prefix = Objects.toString(properties.titlePrefix(), "").trim();
    String t = Objects.toString(title, "").trim();
    if (t.isEmpty()) t = "IRC Event";
    if (prefix.isEmpty()) return t;
    if (t.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) return t;
    return prefix + " - " + t;
  }

  private PushyProperties currentProperties() {
    PushyProperties p = settingsBus != null ? settingsBus.get() : null;
    return p != null ? p : DISABLED_DEFAULTS;
  }

  public record PushResult(boolean success, String message) {
    public static PushResult success(String message) {
      return new PushResult(true, Objects.toString(message, "").trim());
    }

    public static PushResult failed(String message) {
      return new PushResult(false, Objects.toString(message, "").trim());
    }
  }

  private static void appendJsonField(
      StringBuilder out, String key, String value, boolean includeComma) {
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
