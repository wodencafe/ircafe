package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Secondary adapter for runtime config path resolution backed by {@link RuntimeConfigStore}. */
@Component
@Primary
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigPathAdapter implements RuntimeConfigPathPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigPathAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Path runtimeConfigPath() {
    return runtimeConfig.runtimeConfigPath();
  }
}
