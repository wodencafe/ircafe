package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.ClientTranslationRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for client translation settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigClientTranslationAdapter
    implements ClientTranslationRuntimeConfigPort {

  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigClientTranslationAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberClientTranslation(IrcProperties.Client.Translation translation) {
    runtimeConfig.rememberClientTranslation(translation);
  }
}
