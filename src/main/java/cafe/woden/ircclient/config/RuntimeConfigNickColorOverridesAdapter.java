package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.NickColorOverridesConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for per-nick color overrides backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigNickColorOverridesAdapter implements NickColorOverridesConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigNickColorOverridesAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberNickColorOverrides(Map<String, String> overrides) {
    runtimeConfig.rememberNickColorOverrides(overrides);
  }
}
