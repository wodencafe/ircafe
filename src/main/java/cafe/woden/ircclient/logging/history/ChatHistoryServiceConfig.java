package cafe.woden.ircclient.logging.history;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op {@link ChatHistoryService} when logging is disabled. */
@Configuration
public class ChatHistoryServiceConfig {

  @Bean
  @ConditionalOnMissingBean(ChatHistoryService.class)
  public ChatHistoryService noOpChatHistoryService() {
    return new NoOpChatHistoryService();
  }
}
