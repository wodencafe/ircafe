package cafe.woden.ircclient.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional Pushy push-notification integration settings.
 *
 * <p>When enabled and configured, IRC event notifications can be forwarded to Pushy.
 */
@ConfigurationProperties(prefix = "ircafe.pushy")
public record PushyProperties(
    Boolean enabled,
    String endpoint,
    String apiKey,
    String deviceToken,
    String topic,
    String titlePrefix,
    Integer connectTimeoutSeconds,
    Integer readTimeoutSeconds
) {

  public PushyProperties {
    if (enabled == null) enabled = false;

    endpoint = trimToNull(endpoint);
    if (endpoint == null) endpoint = "https://api.pushy.me/push";

    apiKey = trimToNull(apiKey);
    deviceToken = trimToNull(deviceToken);
    topic = trimToNull(topic);

    titlePrefix = Objects.toString(titlePrefix, "").trim();
    if (titlePrefix.isEmpty()) titlePrefix = "IRCafe";

    if (connectTimeoutSeconds == null || connectTimeoutSeconds <= 0) connectTimeoutSeconds = 5;
    if (connectTimeoutSeconds > 30) connectTimeoutSeconds = 30;

    if (readTimeoutSeconds == null || readTimeoutSeconds <= 0) readTimeoutSeconds = 8;
    if (readTimeoutSeconds > 60) readTimeoutSeconds = 60;
  }

  public boolean configured() {
    if (!Boolean.TRUE.equals(enabled)) return false;
    if (apiKey == null || apiKey.isBlank()) return false;
    return (deviceToken != null && !deviceToken.isBlank())
        || (topic != null && !topic.isBlank());
  }

  private static String trimToNull(String raw) {
    String s = Objects.toString(raw, "").trim();
    return s.isEmpty() ? null : s;
  }
}
