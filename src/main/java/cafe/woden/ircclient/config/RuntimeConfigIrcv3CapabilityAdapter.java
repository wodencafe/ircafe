package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.Ircv3CapabilityConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for IRCv3 capability preferences backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigIrcv3CapabilityAdapter implements Ircv3CapabilityConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigIrcv3CapabilityAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Map<String, Boolean> readIrcv3Capabilities() {
    return runtimeConfig.readIrcv3Capabilities();
  }

  @Override
  public void rememberIrcv3CapabilityEnabled(String capability, boolean enabled) {
    runtimeConfig.rememberIrcv3CapabilityEnabled(capability, enabled);
  }
}
