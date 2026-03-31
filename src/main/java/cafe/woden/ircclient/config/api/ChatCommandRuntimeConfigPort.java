package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract used by outbound chat command flows. */
@SecondaryPort
@ApplicationLayer
public interface ChatCommandRuntimeConfigPort {

  String DEFAULT_QUIT_MESSAGE = "Client shutdown: IRCafe https://github.com/wodencafe/ircafe";

  void rememberJoinedChannel(String serverId, String channel);

  void rememberNick(String serverId, String nick);

  void rememberInviteAutoJoinEnabled(boolean enabled);

  /** Read the default QUIT reason used when a user does not provide one explicitly. */
  String readDefaultQuitMessage();
}
