package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.MonitorRosterConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for monitor roster state backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigMonitorRosterAdapter implements MonitorRosterConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigMonitorRosterAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void replaceMonitorNicks(String serverId, List<String> nicks) {
    runtimeConfig.replaceMonitorNicks(serverId, nicks);
  }

  @Override
  public List<String> readMonitorNicks(String serverId) {
    return runtimeConfig.readMonitorNicks(serverId);
  }
}
