package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract used by outbound chat command flows. */
@ApplicationLayer
public interface ChatCommandRuntimeConfigPort {

  void rememberJoinedChannel(String serverId, String channel);

  void rememberNick(String serverId, String nick);

  void rememberInviteAutoJoinEnabled(boolean enabled);
}
