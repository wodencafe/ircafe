package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.TimestampRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for timestamp display settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigTimestampAdapter implements TimestampRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigTimestampAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberTimestampsEnabled(boolean enabled) {
    runtimeConfig.rememberTimestampsEnabled(enabled);
  }

  @Override
  public void rememberTimestampFormat(String format) {
    runtimeConfig.rememberTimestampFormat(format);
  }

  @Override
  public void rememberTimestampsIncludeChatMessages(boolean includeChatMessages) {
    runtimeConfig.rememberTimestampsIncludeChatMessages(includeChatMessages);
  }

  @Override
  public void rememberTimestampsIncludePresenceMessages(boolean includePresenceMessages) {
    runtimeConfig.rememberTimestampsIncludePresenceMessages(includePresenceMessages);
  }
}
