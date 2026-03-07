package cafe.woden.ircclient.ui.backend;

import cafe.woden.ircclient.irc.IrcBackendModePort;
import java.util.Objects;

/** Backend-aware UI profile for a specific server selection. */
public record BackendUiProfile(String serverId, BackendUiContext backendUiContext) {

  public BackendUiProfile {
    serverId = normalizeServerId(serverId);
    backendUiContext = backendUiContext == null ? BackendUiContext.ircOnly() : backendUiContext;
  }

  public static BackendUiProfile ircOnly(String serverId) {
    return new BackendUiProfile(serverId, BackendUiContext.ircOnly());
  }

  public static BackendUiProfile fromBackendModePort(
      IrcBackendModePort backendModePort, String serverId) {
    return new BackendUiProfile(serverId, BackendUiContext.fromBackendModePort(backendModePort));
  }

  public BackendUiProfile withServerId(String serverId) {
    return new BackendUiProfile(serverId, backendUiContext);
  }

  public BackendUiContext.BackendMode backendMode() {
    if (serverId.isEmpty()) return BackendUiContext.BackendMode.IRC;
    try {
      return backendUiContext.backendModeForServer(serverId);
    } catch (Exception ignored) {
      return BackendUiContext.BackendMode.IRC;
    }
  }

  public boolean isMatrixServer() {
    return backendMode() == BackendUiContext.BackendMode.MATRIX;
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
