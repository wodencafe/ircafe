package cafe.woden.ircclient.logging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatLogWriterConfig {

  @Bean
  @ConditionalOnMissingBean(ChatLogWriter.class)
  public ChatLogWriter noopChatLogWriter() {
    return line -> {
      // no-op
    };
  }
}
