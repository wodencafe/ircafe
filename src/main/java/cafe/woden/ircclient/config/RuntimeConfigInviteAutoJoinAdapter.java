package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.InviteAutoJoinConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for invite auto-join settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigInviteAutoJoinAdapter implements InviteAutoJoinConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigInviteAutoJoinAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public boolean readInviteAutoJoinEnabled(boolean defaultValue) {
    return runtimeConfig.readInviteAutoJoinEnabled(defaultValue);
  }

  @Override
  public void rememberInviteAutoJoinEnabled(boolean enabled) {
    runtimeConfig.rememberInviteAutoJoinEnabled(enabled);
  }
}
