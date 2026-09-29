package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.ChatBehaviorRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for chat behavior runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigChatBehaviorAdapter implements ChatBehaviorRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigChatBehaviorAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public String readDefaultQuitMessage() {
    return runtimeConfig.readDefaultQuitMessage();
  }

  @Override
  public boolean readNickCompletionCycleWithTabEnabled(boolean defaultValue) {
    return runtimeConfig.readNickCompletionCycleWithTabEnabled(defaultValue);
  }

  @Override
  public boolean readNickCompletionAppendAddressSuffixEnabled(boolean defaultValue) {
    return runtimeConfig.readNickCompletionAppendAddressSuffixEnabled(defaultValue);
  }

  @Override
  public int readServerTreeUnreadBadgeScalePercent(int defaultValue) {
    return runtimeConfig.readServerTreeUnreadBadgeScalePercent(defaultValue);
  }

  @Override
  public void rememberPresenceFoldsEnabled(boolean enabled) {
    runtimeConfig.rememberPresenceFoldsEnabled(enabled);
  }

  @Override
  public void rememberCtcpRequestsInActiveTargetEnabled(boolean enabled) {
    runtimeConfig.rememberCtcpRequestsInActiveTargetEnabled(enabled);
  }

  @Override
  public void rememberDefaultQuitMessage(String message) {
    runtimeConfig.rememberDefaultQuitMessage(message);
  }

  @Override
  public void rememberNickCompletionCycleWithTabEnabled(boolean enabled) {
    runtimeConfig.rememberNickCompletionCycleWithTabEnabled(enabled);
  }

  @Override
  public void rememberNickCompletionAppendAddressSuffixEnabled(boolean enabled) {
    runtimeConfig.rememberNickCompletionAppendAddressSuffixEnabled(enabled);
  }

  @Override
  public void rememberTypingIndicatorsEnabled(boolean enabled) {
    runtimeConfig.rememberTypingIndicatorsEnabled(enabled);
  }

  @Override
  public void rememberTypingIndicatorsReceiveEnabled(boolean enabled) {
    runtimeConfig.rememberTypingIndicatorsReceiveEnabled(enabled);
  }

  @Override
  public void rememberTypingTreeIndicatorStyle(String style) {
    runtimeConfig.rememberTypingTreeIndicatorStyle(style);
  }

  @Override
  public void rememberTypingIndicatorsTreeEnabled(boolean enabled) {
    runtimeConfig.rememberTypingIndicatorsTreeEnabled(enabled);
  }

  @Override
  public void rememberTypingIndicatorsUsersListEnabled(boolean enabled) {
    runtimeConfig.rememberTypingIndicatorsUsersListEnabled(enabled);
  }

  @Override
  public void rememberMatrixUserListNameDisplayMode(String mode) {
    runtimeConfig.rememberMatrixUserListNameDisplayMode(mode);
  }

  @Override
  public void rememberTypingIndicatorsTranscriptEnabled(boolean enabled) {
    runtimeConfig.rememberTypingIndicatorsTranscriptEnabled(enabled);
  }

  @Override
  public void rememberTypingIndicatorsSendSignalEnabled(boolean enabled) {
    runtimeConfig.rememberTypingIndicatorsSendSignalEnabled(enabled);
  }

  @Override
  public void rememberServerTreeUnreadBadgeScalePercent(int scalePercent) {
    runtimeConfig.rememberServerTreeUnreadBadgeScalePercent(scalePercent);
  }

  @Override
  public void rememberServerTreeNotificationBadgesEnabled(boolean enabled) {
    runtimeConfig.rememberServerTreeNotificationBadgesEnabled(enabled);
  }
}
