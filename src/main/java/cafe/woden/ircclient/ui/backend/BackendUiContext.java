package cafe.woden.ircclient.ui.backend;

import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.irc.backend.IrcBackendModePort;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

@FunctionalInterface
public interface BackendUiContext {

  enum BackendMode {
    IRC,
    MATRIX
  }

  BackendMode backendModeForServer(String serverId);

  default boolean isMatrixServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return backendModeForServer(sid) == BackendMode.MATRIX;
  }

  static BackendUiContext ircOnly() {
    return ignored -> BackendMode.IRC;
  }

  static BackendUiContext fromMatrixServerPredicate(Predicate<String> isMatrixServer) {
    Predicate<String> matrixPredicate = isMatrixServer == null ? ignored -> false : isMatrixServer;
    return serverId ->
        matrixPredicate.test(Objects.toString(serverId, "").trim())
            ? BackendMode.MATRIX
            : BackendMode.IRC;
  }

  static BackendUiContext fromBackendUiModeResolver(
      Function<String, BackendUiMode> uiModeResolver) {
    Function<String, BackendUiMode> resolver =
        uiModeResolver == null ? ignored -> BackendUiMode.IRC : uiModeResolver;
    return serverId ->
        resolver.apply(Objects.toString(serverId, "").trim()) == BackendUiMode.MATRIX
            ? BackendMode.MATRIX
            : BackendMode.IRC;
  }

  static BackendUiContext fromBackendModePort(IrcBackendModePort backendMode) {
    if (backendMode == null) return ircOnly();
    return fromMatrixServerPredicate(backendMode::isMatrixBackendServer);
  }
}
