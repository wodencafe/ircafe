package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.OutgoingMessageRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for outgoing message display settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigOutgoingMessageAdapter implements OutgoingMessageRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigOutgoingMessageAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberClientLineColorEnabled(boolean enabled) {
    runtimeConfig.rememberClientLineColorEnabled(enabled);
  }

  @Override
  public void rememberClientLineColor(String hex) {
    runtimeConfig.rememberClientLineColor(hex);
  }

  @Override
  public void rememberOutgoingDeliveryIndicatorsEnabled(boolean enabled) {
    runtimeConfig.rememberOutgoingDeliveryIndicatorsEnabled(enabled);
  }
}
