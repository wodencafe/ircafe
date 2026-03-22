package cafe.woden.ircclient.config.api;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted UI settings and startup theme recovery state. */
@SecondaryPort
@ApplicationLayer
public interface UiSettingsRuntimeConfigPort {

  boolean runtimeConfigFileExistedOnStartup();

  Optional<Boolean> readTrayCloseToTrayIfPresent();

  void rememberTrayCloseToTray(boolean enabled);

  boolean readChatSmoothWheelScrollingEnabled(boolean defaultValue);

  boolean readChatHistoryLockViewportDuringLoadOlder(boolean defaultValue);

  int readServerTreeUnreadBadgeScalePercent(int defaultValue);

  void rememberUiSettings(String theme, String chatFontFamily, int chatFontSize);

  Optional<String> readStartupThemePending();

  void rememberStartupThemePending(String theme);

  void clearStartupThemePending();
}
