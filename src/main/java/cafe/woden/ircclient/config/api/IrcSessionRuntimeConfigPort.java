package cafe.woden.ircclient.config.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract used by app-core IRC session orchestration flows. */
@ApplicationLayer
public interface IrcSessionRuntimeConfigPort {

  boolean isIrcv3CapabilityEnabled(String capability, boolean defaultEnabled);

  void rememberJoinedChannel(String serverId, String channel);

  void forgetJoinedChannel(String serverId, String channel);

  void rememberPrivateMessageTarget(String serverId, String nick);

  void forgetPrivateMessageTarget(String serverId, String nick);

  List<String> readJoinedChannels(String serverId);

  void rememberIrcv3CapabilityEnabled(String capability, boolean enabled);
}
