package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.LaunchJvmRuntimeConfigPort;
import cafe.woden.ircclient.config.api.LaunchJvmRuntimeConfigPort.LaunchJvmSnapshot;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for startup JVM settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigLaunchJvmAdapter implements LaunchJvmRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigLaunchJvmAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public LaunchJvmSnapshot readLaunchJvmSettings() {
    return new LaunchJvmSnapshot(
        runtimeConfig.readLaunchJvmJavaCommand("java"),
        runtimeConfig.readLaunchJvmXmsMiB(0),
        runtimeConfig.readLaunchJvmXmxMiB(0),
        runtimeConfig.readLaunchJvmGc(""),
        runtimeConfig.readLaunchJvmArgs(List.of()));
  }

  @Override
  public void rememberLaunchJvmSettings(LaunchJvmSnapshot settings) {
    if (settings == null) {
      return;
    }
    runtimeConfig.rememberLaunchJvmJavaCommand(settings.javaCommand());
    runtimeConfig.rememberLaunchJvmXmsMiB(settings.xmsMiB());
    runtimeConfig.rememberLaunchJvmXmxMiB(settings.xmxMiB());
    runtimeConfig.rememberLaunchJvmGc(settings.gc());
    runtimeConfig.rememberLaunchJvmArgs(settings.args());
  }
}
