package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiTranscriptPort;
import cafe.woden.ircclient.config.LogProperties;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

/** Wires a {@link UiPort} decorator that logs transcript events to the embedded chat log DB. */
@Configuration
@ConditionalOnProperty(prefix = "ircafe.logging", name = "enabled", havingValue = "true")
@InfrastructureLayer
public class LoggingUiPortConfig {

  @Bean
  @Lazy
  public UiTranscriptPort loggingTranscriptUiPort(
      @Qualifier("swingUiPort") UiTranscriptPort swingUiTranscriptPort,
      ChatLogWriter writer,
      LogLineFactory factory,
      LogProperties props) {
    return new LoggingUiPortDecorator(swingUiTranscriptPort, writer, factory, props);
  }

  /** Primary {@link UiPort} when logging is enabled. */
  @Bean
  @Primary
  @Lazy
  public UiPort loggingUiPort(
      @Qualifier("swingUiPort") UiPort swingUiPort,
      @Qualifier("loggingTranscriptUiPort") UiTranscriptPort loggingTranscriptUiPort) {
    return new TranscriptDecoratingUiPort(swingUiPort, loggingTranscriptUiPort);
  }
}
