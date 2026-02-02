package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.TargetRef;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Provides a no-op {@link ChatLogMaintenance} when chat logging isn't enabled.
 */
@Configuration
public class ChatLogMaintenanceConfig {

  @Bean
  @ConditionalOnMissingBean(ChatLogMaintenance.class)
  public ChatLogMaintenance noopChatLogMaintenance() {
    return new ChatLogMaintenance() {
      @Override
      public boolean enabled() {
        return false;
      }

      @Override
      public void clearTarget(TargetRef target) {
        // no-op
      }
    };
  }
}
