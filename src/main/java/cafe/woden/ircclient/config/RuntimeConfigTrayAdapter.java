package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.TrayRuntimeConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for tray runtime settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigTrayAdapter implements TrayRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigTrayAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public Path runtimeConfigPath() {
    return runtimeConfig.runtimeConfigPath();
  }

  @Override
  public void rememberTrayEnabled(boolean enabled) {
    runtimeConfig.rememberTrayEnabled(enabled);
  }

  @Override
  public void rememberTrayCloseToTray(boolean enabled) {
    runtimeConfig.rememberTrayCloseToTray(enabled);
  }

  @Override
  public void rememberTrayMinimizeToTray(boolean enabled) {
    runtimeConfig.rememberTrayMinimizeToTray(enabled);
  }

  @Override
  public void rememberTrayStartMinimized(boolean enabled) {
    runtimeConfig.rememberTrayStartMinimized(enabled);
  }

  @Override
  public void rememberTrayNotifyHighlights(boolean enabled) {
    runtimeConfig.rememberTrayNotifyHighlights(enabled);
  }

  @Override
  public void rememberTrayNotifyPrivateMessages(boolean enabled) {
    runtimeConfig.rememberTrayNotifyPrivateMessages(enabled);
  }

  @Override
  public void rememberTrayNotifyConnectionState(boolean enabled) {
    runtimeConfig.rememberTrayNotifyConnectionState(enabled);
  }

  @Override
  public void rememberTrayNotifyOnlyWhenUnfocused(boolean enabled) {
    runtimeConfig.rememberTrayNotifyOnlyWhenUnfocused(enabled);
  }

  @Override
  public void rememberTrayNotifyOnlyWhenMinimizedOrHidden(boolean enabled) {
    runtimeConfig.rememberTrayNotifyOnlyWhenMinimizedOrHidden(enabled);
  }

  @Override
  public void rememberTrayNotifySuppressWhenTargetActive(boolean enabled) {
    runtimeConfig.rememberTrayNotifySuppressWhenTargetActive(enabled);
  }

  @Override
  public void rememberTrayLinuxDbusActionsEnabled(boolean enabled) {
    runtimeConfig.rememberTrayLinuxDbusActionsEnabled(enabled);
  }

  @Override
  public void rememberTrayNotificationBackend(String backendToken) {
    runtimeConfig.rememberTrayNotificationBackend(backendToken);
  }

  @Override
  public void rememberTrayNotificationSoundsEnabled(boolean enabled) {
    runtimeConfig.rememberTrayNotificationSoundsEnabled(enabled);
  }

  @Override
  public void rememberTrayNotificationSound(String soundId) {
    runtimeConfig.rememberTrayNotificationSound(soundId);
  }

  @Override
  public void rememberTrayNotificationSoundUseCustom(boolean useCustom) {
    runtimeConfig.rememberTrayNotificationSoundUseCustom(useCustom);
  }

  @Override
  public void rememberTrayNotificationSoundCustomPath(String relativePath) {
    runtimeConfig.rememberTrayNotificationSoundCustomPath(relativePath);
  }
}
