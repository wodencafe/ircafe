package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.NickColorRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for nickname coloring settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigNickColorAdapter implements NickColorRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigNickColorAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberNickColoringEnabled(boolean enabled) {
    runtimeConfig.rememberNickColoringEnabled(enabled);
  }

  @Override
  public void rememberNickColorMinContrast(double minContrast) {
    runtimeConfig.rememberNickColorMinContrast(minContrast);
  }
}
