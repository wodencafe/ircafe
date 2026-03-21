package cafe.woden.ircclient.config.api;

import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for per-server built-in server-tree node visibility. */
@ApplicationLayer
public interface ServerTreeBuiltInVisibilityConfigPort {

  @ApplicationLayer
  record ServerTreeBuiltInNodesVisibility(
      boolean server,
      boolean notifications,
      boolean logViewer,
      boolean monitor,
      boolean interceptors) {
    public static ServerTreeBuiltInNodesVisibility defaults() {
      return new ServerTreeBuiltInNodesVisibility(true, true, true, true, true);
    }

    public boolean isDefaultVisible() {
      return server && notifications && logViewer && monitor && interceptors;
    }
  }

  Map<String, ServerTreeBuiltInNodesVisibility> readServerTreeBuiltInNodesVisibility();

  void rememberServerTreeBuiltInNodesVisibility(
      String serverId, ServerTreeBuiltInNodesVisibility visibility);
}
