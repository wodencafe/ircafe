package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.irc.IrcRuntimeSettings;
import cafe.woden.ircclient.irc.IrcRuntimeSettingsProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class UiSettingsIrcRuntimeSettingsProvider implements IrcRuntimeSettingsProvider {

  private final UiSettingsBus settingsBus;

  public UiSettingsIrcRuntimeSettingsProvider(UiSettingsBus settingsBus) {
    this.settingsBus = settingsBus;
  }

  @Override
  public IrcRuntimeSettings current() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;
    if (s == null) {
      return IrcRuntimeSettings.defaults();
    }

    return new IrcRuntimeSettings(
        s.userhostDiscoveryEnabled(),
        s.userhostMinIntervalSeconds(),
        s.userhostMaxCommandsPerMinute(),
        s.userhostNickCooldownMinutes(),
        s.userhostMaxNicksPerCommand(),
        s.userInfoEnrichmentEnabled(),
        s.userInfoEnrichmentUserhostMinIntervalSeconds(),
        s.userInfoEnrichmentUserhostMaxCommandsPerMinute(),
        s.userInfoEnrichmentUserhostNickCooldownMinutes(),
        s.userInfoEnrichmentUserhostMaxNicksPerCommand(),
        s.userInfoEnrichmentWhoisFallbackEnabled(),
        s.userInfoEnrichmentWhoisMinIntervalSeconds(),
        s.userInfoEnrichmentWhoisNickCooldownMinutes(),
        s.userInfoEnrichmentPeriodicRefreshEnabled(),
        s.userInfoEnrichmentPeriodicRefreshIntervalSeconds(),
        s.userInfoEnrichmentPeriodicRefreshNicksPerTick());
  }
}
