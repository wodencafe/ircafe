package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.UserCommandAliasesConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.model.UserCommandAlias;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for user-command alias settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigUserCommandAliasesAdapter implements UserCommandAliasesConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigUserCommandAliasesAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public List<UserCommandAlias> readUserCommandAliases() {
    return runtimeConfig.readUserCommandAliases();
  }

  @Override
  public boolean readUnknownCommandAsRawEnabled(boolean defaultValue) {
    return runtimeConfig.readUnknownCommandAsRawEnabled(defaultValue);
  }

  @Override
  public void rememberUserCommandAliases(List<UserCommandAlias> aliases) {
    runtimeConfig.rememberUserCommandAliases(aliases);
  }

  @Override
  public void rememberUnknownCommandAsRawEnabled(boolean enabled) {
    runtimeConfig.rememberUnknownCommandAsRawEnabled(enabled);
  }
}
