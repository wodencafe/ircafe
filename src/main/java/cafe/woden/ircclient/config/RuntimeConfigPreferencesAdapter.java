package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.PreferencesRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for preferences persistence backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigPreferencesAdapter implements PreferencesRuntimeConfigPort {

  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigPreferencesAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Path runtimeConfigPath() {
    return runtimeConfig.runtimeConfigPath();
  }

  @Override
  public void runMutationBatch(Runnable action) {
    runtimeConfig.runMutationBatch(action);
  }

  @Override
  public void rememberUiSettings(String theme, String chatFontFamily, int chatFontSize) {
    runtimeConfig.rememberUiSettings(theme, chatFontFamily, chatFontSize);
  }

  @Override
  public void rememberAutoConnectOnStart(boolean enabled) {
    runtimeConfig.rememberAutoConnectOnStart(enabled);
  }
}
