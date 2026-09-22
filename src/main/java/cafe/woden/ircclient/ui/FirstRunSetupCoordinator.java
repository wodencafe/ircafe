package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.FirstRunSetupConfigPort;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import cafe.woden.ircclient.ui.servers.FirstRunSetupDialog;
import java.awt.Window;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.stereotype.Component;

/** Presents optional setup before first-launch automatic connections or tray minimization. */
@Component
@InterfaceLayer
public final class FirstRunSetupCoordinator {
  private final FirstRunSetupConfigPort config;
  private final ServerRegistry servers;

  public FirstRunSetupCoordinator(FirstRunSetupConfigPort config, ServerRegistry servers) {
    this.config = config;
    this.servers = servers;
  }

  /** Called on the EDT; returns true when startup should leave connection choices to the user. */
  public boolean showIfNeeded(Window owner) {
    if (!config.shouldOfferSetup()) return false;
    IrcProperties.Server defaults =
        servers.servers().stream()
            .filter(server -> IrcProperties.Server.Backend.IRC.token().equals(server.backendId()))
            .findFirst()
            .orElse(null);
    new FirstRunSetupDialog(owner, config, defaults).setVisible(true);
    return true;
  }
}
