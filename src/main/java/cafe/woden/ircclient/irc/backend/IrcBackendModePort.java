package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;

/** Per-server backend mode resolution used by backend-aware UI/application behavior. */
public interface IrcBackendModePort {

  default IrcProperties.Server.Backend backendForServer(String serverId) {
    return IrcProperties.Server.Backend.IRC;
  }

  default boolean isMatrixBackendServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return backendForServer(sid) == IrcProperties.Server.Backend.MATRIX;
  }
}
