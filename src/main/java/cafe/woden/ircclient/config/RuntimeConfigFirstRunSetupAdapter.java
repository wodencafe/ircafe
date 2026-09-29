package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.FirstRunSetupConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Preserves existing installations and saves optional first-launch setup in the runtime config. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigFirstRunSetupAdapter implements FirstRunSetupConfigPort {
  private final RuntimeConfigStore runtimeConfig;
  private final ServerRegistry servers;
  private volatile boolean pending;

  public RuntimeConfigFirstRunSetupAdapter(
      RuntimeConfigStore runtimeConfig, ServerRegistry servers) {
    this.runtimeConfig = runtimeConfig;
    this.servers = servers;
    // RuntimeConfigStore seeds a file during construction. Use its original existence snapshot.
    pending =
        !runtimeConfig.runtimeConfigFileExistedOnStartup()
            && !runtimeConfig.readFirstRunSetupDismissed();
  }

  @Override
  public boolean shouldOfferSetup() {
    return pending;
  }

  @Override
  public synchronized void finishSetup(IrcProperties.Server server) {
    if (!pending) return;
    // Only a fresh profile reaches this path; replace the bundled starter server list.
    // Keep registry -> config lock ordering consistent with normal server edits.
    if (server != null) servers.setAll(List.of(server));
    runtimeConfig.rememberFirstRunSetupDismissed();
    if (!runtimeConfig.readFirstRunSetupDismissed()) {
      throw new IllegalStateException("Could not persist first-launch setup");
    }
    pending = false;
  }
}
