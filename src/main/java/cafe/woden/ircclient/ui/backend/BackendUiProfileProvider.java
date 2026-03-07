package cafe.woden.ircclient.ui.backend;

import cafe.woden.ircclient.irc.IrcBackendModePort;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Central provider for backend-aware UI profiles scoped by server id. */
@Component
public class BackendUiProfileProvider {

  private final BackendUiContext backendUiContext;

  public BackendUiProfileProvider(@Qualifier("ircClientService") IrcBackendModePort backendMode) {
    this.backendUiContext = BackendUiContext.fromBackendModePort(backendMode);
  }

  public BackendUiContext backendUiContext() {
    return backendUiContext;
  }

  public BackendUiProfile profileForServer(String serverId) {
    return new BackendUiProfile(normalizeServerId(serverId), backendUiContext);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
