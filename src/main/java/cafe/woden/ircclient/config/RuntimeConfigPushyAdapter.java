package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.PushyRuntimeConfigPort;
import cafe.woden.ircclient.config.properties.PushyProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for Pushy runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigPushyAdapter implements PushyRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigPushyAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberPushySettings(PushyProperties settings) {
    runtimeConfig.rememberPushySettings(settings);
  }
}
