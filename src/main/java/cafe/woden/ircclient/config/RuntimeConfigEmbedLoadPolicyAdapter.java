package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter for embed load policy runtime settings backed by {@link RuntimeConfigStore}.
 */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigEmbedLoadPolicyAdapter implements EmbedLoadPolicyConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigEmbedLoadPolicyAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public List<String> readServerIds() {
    return runtimeConfig.readServerIds();
  }

  @Override
  public EmbedLoadPolicySnapshot readEmbedLoadPolicy() {
    return runtimeConfig.readEmbedLoadPolicy();
  }

  @Override
  public void rememberEmbedLoadPolicy(EmbedLoadPolicySnapshot snapshot) {
    runtimeConfig.rememberEmbedLoadPolicy(snapshot);
  }
}
