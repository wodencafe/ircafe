package cafe.woden.ircclient.config.api;

import java.util.Optional;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted UI settings and startup theme recovery state. */
@ApplicationLayer
public interface UiSettingsRuntimeConfigPort {

  void rememberUiSettings(String theme, String chatFontFamily, int chatFontSize);

  Optional<String> readStartupThemePending();

  void rememberStartupThemePending(String theme);

  void clearStartupThemePending();
}
