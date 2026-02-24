package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.ui.SwingUiPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

/** Wires a {@link UiPort} decorator that logs transcript events to the embedded chat log DB. */
@Configuration
@ConditionalOnProperty(prefix = "ircafe.logging", name = "enabled", havingValue = "true")
public class LoggingUiPortConfig {

  /** Primary {@link UiPort} when logging is enabled. */
  @Bean
  @Primary
  @Lazy
  public UiPort loggingUiPort(
      SwingUiPort swingUiPort, ChatLogWriter writer, LogLineFactory factory, LogProperties props) {
    return new LoggingUiPortDecorator(swingUiPort, writer, factory, props);
  }
}
