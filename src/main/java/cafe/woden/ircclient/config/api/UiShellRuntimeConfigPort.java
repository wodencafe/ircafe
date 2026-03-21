package cafe.woden.ircclient.config.api;

import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted shell and tray UI chrome state. */
@ApplicationLayer
public interface UiShellRuntimeConfigPort
    extends RuntimeConfigPathPort, UiSettingsRuntimeConfigPort {

  record LastSelectedTarget(String serverId, String target) {
    public LastSelectedTarget {
      serverId = Objects.toString(serverId, "").trim();
      target = Objects.toString(target, "").trim();
    }

    public boolean isValid() {
      return !serverId.isEmpty() && !target.isEmpty();
    }
  }

  boolean readLagIndicatorEnabled(boolean defaultValue);

  void rememberLagIndicatorEnabled(boolean enabled);

  boolean readUpdateNotifierEnabled(boolean defaultValue);

  void rememberUpdateNotifierEnabled(boolean enabled);

  void rememberMemoryUsageDisplayMode(String mode);

  int readMemoryUsageRefreshIntervalMs(int defaultValue);

  void rememberMemoryUsageRefreshIntervalMs(int intervalMs);

  void rememberServerDockWidthPx(int serverDockWidthPx);

  void rememberUserDockWidthPx(int userDockWidthPx);

  Optional<LastSelectedTarget> readLastSelectedTarget();

  void rememberLastSelectedTarget(String serverId, String target);

  boolean readTrayCloseToTrayHintShown(boolean defaultValue);

  void rememberTrayCloseToTrayHintShown(boolean shown);
}
