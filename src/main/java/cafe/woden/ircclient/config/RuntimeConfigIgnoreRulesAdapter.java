package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.IgnoreRulesConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for ignore-list rule settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigIgnoreRulesAdapter implements IgnoreRulesConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigIgnoreRulesAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberHardIgnoreIncludesCtcp(boolean enabled) {
    runtimeConfig.rememberHardIgnoreIncludesCtcp(enabled);
  }

  @Override
  public void rememberSoftIgnoreIncludesCtcp(boolean enabled) {
    runtimeConfig.rememberSoftIgnoreIncludesCtcp(enabled);
  }

  @Override
  public void rememberIgnoreMask(String serverId, String mask) {
    runtimeConfig.rememberIgnoreMask(serverId, mask);
  }

  @Override
  public void rememberIgnoreMaskLevels(String serverId, String mask, List<String> levels) {
    runtimeConfig.rememberIgnoreMaskLevels(serverId, mask, levels);
  }

  @Override
  public void rememberIgnoreMaskChannels(String serverId, String mask, List<String> channels) {
    runtimeConfig.rememberIgnoreMaskChannels(serverId, mask, channels);
  }

  @Override
  public void rememberIgnoreMaskExpiresAt(String serverId, String mask, Long expiresAtEpochMs) {
    runtimeConfig.rememberIgnoreMaskExpiresAt(serverId, mask, expiresAtEpochMs);
  }

  @Override
  public void rememberIgnoreMaskPattern(
      String serverId, String mask, String pattern, String modeToken) {
    runtimeConfig.rememberIgnoreMaskPattern(serverId, mask, pattern, modeToken);
  }

  @Override
  public void rememberIgnoreMaskReplies(String serverId, String mask, boolean repliesEnabled) {
    runtimeConfig.rememberIgnoreMaskReplies(serverId, mask, repliesEnabled);
  }

  @Override
  public void forgetIgnoreMask(String serverId, String mask) {
    runtimeConfig.forgetIgnoreMask(serverId, mask);
  }

  @Override
  public void rememberSoftIgnoreMask(String serverId, String mask) {
    runtimeConfig.rememberSoftIgnoreMask(serverId, mask);
  }

  @Override
  public void forgetSoftIgnoreMask(String serverId, String mask) {
    runtimeConfig.forgetSoftIgnoreMask(serverId, mask);
  }
}
