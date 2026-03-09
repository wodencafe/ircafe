package cafe.woden.ircclient.logging;

import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@InfrastructureLayer
public class ChatLogWriterConfig {

  @Bean
  @ConditionalOnMissingBean(ChatLogWriter.class)
  public ChatLogWriter noopChatLogWriter() {
    return line -> {
      // no-op
    };
  }
}
