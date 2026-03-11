package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a no-op {@link ChatLogMaintenance} when chat logging isn't enabled. */
@Configuration
@InfrastructureLayer
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
