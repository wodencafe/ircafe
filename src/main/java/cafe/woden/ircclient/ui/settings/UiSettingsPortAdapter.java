package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** UI-side adapter that exposes UiSettingsBus through the app-owned settings port. */
@Component
public class UiSettingsPortAdapter implements UiSettingsPort {

  private final UiSettingsBus bus;

  public UiSettingsPortAdapter(UiSettingsBus bus) {
    this.bus = Objects.requireNonNull(bus, "bus");
  }

  @Override
  public UiSettingsSnapshot get() {
    UiSettings settings = bus.get();
    if (settings == null) {
      return UiSettingsSnapshot.defaults();
    }
    return new UiSettingsSnapshot(
        settings.notificationRules(),
        settings.notificationRuleCooldownSeconds(),
        settings.monitorIsonFallbackPollIntervalSeconds(),
        settings.ctcpRequestsInActiveTargetEnabled(),
        settings.typingIndicatorsReceiveEnabled());
  }

  @Override
  public void addListener(PropertyChangeListener listener) {
    bus.addListener(listener);
  }

  @Override
  public void removeListener(PropertyChangeListener listener) {
    bus.removeListener(listener);
  }
}
