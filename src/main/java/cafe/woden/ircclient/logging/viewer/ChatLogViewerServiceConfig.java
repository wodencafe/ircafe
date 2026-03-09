package cafe.woden.ircclient.logging.viewer;

import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op log viewer backend when logging isn't enabled. */
@Configuration
@InfrastructureLayer
public class ChatLogViewerServiceConfig {

  @Bean
  @ConditionalOnMissingBean(ChatLogViewerService.class)
  public ChatLogViewerService noOpChatLogViewerService() {
    return new NoOpChatLogViewerService();
  }
}
