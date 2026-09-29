package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.ConnectionRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for connection orchestration settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigConnectionAdapter implements ConnectionRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigConnectionAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Map<String, Boolean> readServerAutoConnectOnStartByServer() {
    return runtimeConfig.readServerAutoConnectOnStartByServer();
  }

  @Override
  public List<String> readPrivateMessageTargets(String serverId) {
    return runtimeConfig.readPrivateMessageTargets(serverId);
  }

  @Override
  public List<String> readKnownChannels(String serverId) {
    return runtimeConfig.readKnownChannels(serverId);
  }
}
