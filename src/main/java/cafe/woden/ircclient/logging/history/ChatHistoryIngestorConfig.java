package cafe.woden.ircclient.logging.history;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op {@link ChatHistoryIngestor} when chat logging is disabled. */
@Configuration
public class ChatHistoryIngestorConfig {

  @Bean
  @ConditionalOnMissingBean(ChatHistoryIngestor.class)
  public ChatHistoryIngestor noOpChatHistoryIngestor() {
    return new NoOpChatHistoryIngestor();
  }
}
