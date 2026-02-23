package cafe.woden.ircclient.logging.viewer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op log viewer backend when logging isn't enabled. */
@Configuration
public class ChatLogViewerServiceConfig {

  @Bean
  @ConditionalOnMissingBean(ChatLogViewerService.class)
  public ChatLogViewerService noOpChatLogViewerService() {
    return new NoOpChatLogViewerService();
  }
}
