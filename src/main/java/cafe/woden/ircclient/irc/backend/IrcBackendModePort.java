package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/** Per-server backend mode resolution used by backend-aware UI/application behavior. */
public interface IrcBackendModePort {
  BackendDescriptorCatalog BACKEND_DESCRIPTORS = BackendDescriptorCatalog.builtIns();

  default String backendIdForServer(String serverId) {
    return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
  }

  @Deprecated(forRemoval = false)
  default IrcProperties.Server.Backend backendForServer(String serverId) {
    return BACKEND_DESCRIPTORS.backendForId(backendIdForServer(serverId)).orElse(null);
  }

  @Deprecated(forRemoval = false)
  default boolean isMatrixBackendServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return BACKEND_DESCRIPTORS
        .idFor(IrcProperties.Server.Backend.MATRIX)
        .equals(BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendIdForServer(sid)));
  }

  @Deprecated(forRemoval = false)
  default boolean supportsQuasselCoreCommands(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return BACKEND_DESCRIPTORS
        .idFor(IrcProperties.Server.Backend.QUASSEL_CORE)
        .equals(BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendIdForServer(sid)));
  }
}
