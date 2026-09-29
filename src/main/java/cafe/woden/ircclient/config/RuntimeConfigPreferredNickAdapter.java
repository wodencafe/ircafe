package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.PreferredNickRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for preferred-nick persistence backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigPreferredNickAdapter implements PreferredNickRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigPreferredNickAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberNick(String serverId, String nick) {
    runtimeConfig.rememberNick(serverId, nick);
  }
}
