package cafe.woden.ircclient.config.api;

import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted ignore-list rules and toggles. */
@ApplicationLayer
public interface IgnoreRulesConfigPort {

  void rememberHardIgnoreIncludesCtcp(boolean enabled);

  void rememberSoftIgnoreIncludesCtcp(boolean enabled);

  void rememberIgnoreMask(String serverId, String mask);

  void rememberIgnoreMaskLevels(String serverId, String mask, List<String> levels);

  void rememberIgnoreMaskChannels(String serverId, String mask, List<String> channels);

  void rememberIgnoreMaskExpiresAt(String serverId, String mask, Long expiresAtEpochMs);

  void rememberIgnoreMaskPattern(String serverId, String mask, String pattern, String modeToken);

  void rememberIgnoreMaskReplies(String serverId, String mask, boolean repliesEnabled);

  void forgetIgnoreMask(String serverId, String mask);

  void rememberSoftIgnoreMask(String serverId, String mask);

  void forgetSoftIgnoreMask(String serverId, String mask);
}
