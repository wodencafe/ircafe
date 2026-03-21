package cafe.woden.ircclient.config.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted shell and tray UI chrome state. */
@ApplicationLayer
public interface UiShellRuntimeConfigPort
    extends RuntimeConfigPathPort, UiSettingsRuntimeConfigPort {

  boolean readLagIndicatorEnabled(boolean defaultValue);

  void rememberLagIndicatorEnabled(boolean enabled);

  boolean readUpdateNotifierEnabled(boolean defaultValue);

  void rememberUpdateNotifierEnabled(boolean enabled);

  void rememberMemoryUsageDisplayMode(String mode);

  int readMemoryUsageRefreshIntervalMs(int defaultValue);

  void rememberMemoryUsageRefreshIntervalMs(int intervalMs);

  void rememberServerDockWidthPx(int serverDockWidthPx);

  void rememberUserDockWidthPx(int userDockWidthPx);

  void rememberLastSelectedTarget(String serverId, String target);

  boolean readTrayCloseToTrayHintShown(boolean defaultValue);

  void rememberTrayCloseToTrayHintShown(boolean shown);
}
