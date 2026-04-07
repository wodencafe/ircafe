package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.util.InstalledPluginDescriptor;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Exposes declared plugin metadata discovered from the configured plugin directory. */
@SecondaryPort
@ApplicationLayer
public interface InstalledPluginsPort {

  default Path pluginDirectory() {
    return null;
  }

  default List<InstalledPluginDescriptor> installedPlugins() {
    return List.of();
  }

  default List<InstalledPluginProblem> pluginProblems() {
    return List.of();
  }

  default <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
    return List.copyOf(Objects.requireNonNullElse(builtInServices, List.of()));
  }
}
