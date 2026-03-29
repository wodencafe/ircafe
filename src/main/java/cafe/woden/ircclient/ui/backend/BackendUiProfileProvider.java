package cafe.woden.ircclient.ui.backend;

import cafe.woden.ircclient.irc.backend.IrcBackendModePort;
import java.util.Objects;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Central provider for backend-aware UI profiles scoped by server id. */
@Component
@InterfaceLayer
public class BackendUiProfileProvider {

  private final IrcBackendModePort backendMode;
  private final BackendUiContext backendUiContext;

  public BackendUiProfileProvider(@Qualifier("ircClientService") IrcBackendModePort backendMode) {
    this.backendMode = Objects.requireNonNull(backendMode, "backendMode");
    this.backendUiContext = BackendUiContext.fromBackendModePort(backendMode);
  }

  public BackendUiContext backendUiContext() {
    return backendUiContext;
  }

  public BackendUiProfile profileForServer(String serverId) {
    return new BackendUiProfile(normalizeServerId(serverId), backendUiContext);
  }

  public boolean supportsQuasselCoreCommands(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return backendMode.supportsQuasselCoreCommands(sid);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
