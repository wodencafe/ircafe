package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted per-server startup auto-connect toggles. */
@SecondaryPort
@ApplicationLayer
public interface ServerAutoConnectRuntimeConfigPort {

  boolean readServerAutoConnectOnStart(String serverId, boolean defaultValue);

  void rememberServerAutoConnectOnStart(String serverId, boolean enabled);
}
