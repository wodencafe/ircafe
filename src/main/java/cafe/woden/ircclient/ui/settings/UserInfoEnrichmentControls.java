package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;

final class UserInfoEnrichmentControls {
  final JCheckBox enabled;
  final JSpinner userhostMinIntervalSeconds;
  final JSpinner userhostMaxPerMinute;
  final JSpinner userhostNickCooldownMinutes;
  final JSpinner userhostMaxNicksPerCommand;
  final JCheckBox whoisFallbackEnabled;
  final JSpinner whoisMinIntervalSeconds;
  final JSpinner whoisNickCooldownMinutes;
  final JCheckBox periodicRefreshEnabled;
  final JSpinner periodicRefreshIntervalSeconds;
  final JSpinner periodicRefreshNicksPerTick;

  UserInfoEnrichmentControls(
      JCheckBox enabled,
      JSpinner userhostMinIntervalSeconds,
      JSpinner userhostMaxPerMinute,
      JSpinner userhostNickCooldownMinutes,
      JSpinner userhostMaxNicksPerCommand,
      JCheckBox whoisFallbackEnabled,
      JSpinner whoisMinIntervalSeconds,
      JSpinner whoisNickCooldownMinutes,
      JCheckBox periodicRefreshEnabled,
      JSpinner periodicRefreshIntervalSeconds,
      JSpinner periodicRefreshNicksPerTick) {
    this.enabled = enabled;
    this.userhostMinIntervalSeconds = userhostMinIntervalSeconds;
    this.userhostMaxPerMinute = userhostMaxPerMinute;
    this.userhostNickCooldownMinutes = userhostNickCooldownMinutes;
    this.userhostMaxNicksPerCommand = userhostMaxNicksPerCommand;
    this.whoisFallbackEnabled = whoisFallbackEnabled;
    this.whoisMinIntervalSeconds = whoisMinIntervalSeconds;
    this.whoisNickCooldownMinutes = whoisNickCooldownMinutes;
    this.periodicRefreshEnabled = periodicRefreshEnabled;
    this.periodicRefreshIntervalSeconds = periodicRefreshIntervalSeconds;
    this.periodicRefreshNicksPerTick = periodicRefreshNicksPerTick;
  }
}
