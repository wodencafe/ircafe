package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.NotificationRule;
import cafe.woden.ircclient.config.api.NotificationRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for notification runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigNotificationAdapter implements NotificationRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigNotificationAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberNotificationRuleCooldownSeconds(int seconds) {
    runtimeConfig.rememberNotificationRuleCooldownSeconds(seconds);
  }

  @Override
  public void rememberNotificationRules(List<NotificationRule> rules) {
    runtimeConfig.rememberNotificationRules(rules);
  }

  @Override
  public void rememberIrcEventNotificationRules(List<IrcEventNotificationRule> rules) {
    runtimeConfig.rememberIrcEventNotificationRules(rules);
  }
}
