package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditService;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@InfrastructureLayer
public class ChatRedactionAuditServiceConfig {

  @Bean
  @ConditionalOnMissingBean(ChatRedactionAuditService.class)
  public ChatRedactionAuditService noOpChatRedactionAuditService() {
    return new NoOpChatRedactionAuditService();
  }
}
