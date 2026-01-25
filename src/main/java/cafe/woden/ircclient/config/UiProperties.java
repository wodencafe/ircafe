package cafe.woden.ircclient.config;

import java.awt.Font;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI configuration.
 *
 * <p>These values can be overridden by the runtime YAML file imported via
 * {@code spring.config.import}.
 */
@ConfigurationProperties(prefix = "ircafe.ui")
public record UiProperties(
    String theme,
    String chatFontFamily,
    int chatFontSize
) {

  public UiProperties {
    if (theme == null || theme.isBlank()) {
      theme = "dark";
    }
    if (chatFontFamily == null || chatFontFamily.isBlank()) {
      chatFontFamily = Font.MONOSPACED;
    }
    if (chatFontSize <= 0) {
      chatFontSize = 12;
    }
  }
}
