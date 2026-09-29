package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.InterceptorConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for interceptor settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigInterceptorAdapter implements InterceptorConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigInterceptorAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Path runtimeConfigPath() {
    return runtimeConfig.runtimeConfigPath();
  }

  @Override
  public Map<String, List<InterceptorDefinition>> readInterceptorDefinitions() {
    return runtimeConfig.readInterceptorDefinitions();
  }

  @Override
  public void rememberInterceptorDefinitions(
      Map<String, List<InterceptorDefinition>> defsByServer) {
    runtimeConfig.rememberInterceptorDefinitions(defsByServer);
  }
}
