package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.NotificationRule;
import java.util.List;

/** App-level projection of UI settings needed by application services. */
public record UiSettingsSnapshot(
    List<NotificationRule> notificationRules,
    int notificationRuleCooldownSeconds,
    int monitorIsonFallbackPollIntervalSeconds,
    boolean ctcpRequestsInActiveTargetEnabled,
    boolean typingIndicatorsReceiveEnabled) {

  private static final int DEFAULT_NOTIFICATION_RULE_COOLDOWN_SECONDS = 15;
  private static final int DEFAULT_MONITOR_ISON_FALLBACK_POLL_INTERVAL_SECONDS = 30;

  public UiSettingsSnapshot {
    notificationRules = notificationRules == null ? List.of() : List.copyOf(notificationRules);

    if (notificationRuleCooldownSeconds < 0) {
      notificationRuleCooldownSeconds = DEFAULT_NOTIFICATION_RULE_COOLDOWN_SECONDS;
    }
    if (monitorIsonFallbackPollIntervalSeconds < 5) {
      monitorIsonFallbackPollIntervalSeconds = DEFAULT_MONITOR_ISON_FALLBACK_POLL_INTERVAL_SECONDS;
    }
  }

  public static UiSettingsSnapshot defaults() {
    return new UiSettingsSnapshot(
        List.of(),
        DEFAULT_NOTIFICATION_RULE_COOLDOWN_SECONDS,
        DEFAULT_MONITOR_ISON_FALLBACK_POLL_INTERVAL_SECONDS,
        true,
        true);
  }
}
