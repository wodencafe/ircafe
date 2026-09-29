package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.ServerRegistryConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for persistent server registry state backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigServerRegistryAdapter implements ServerRegistryConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigServerRegistryAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Map<String, List<String>> readExplicitServerAutoJoinById() {
    return runtimeConfig.readExplicitServerAutoJoinById();
  }

  @Override
  public void writeServers(List<IrcProperties.Server> servers) {
    runtimeConfig.writeServers(servers);
  }
}
