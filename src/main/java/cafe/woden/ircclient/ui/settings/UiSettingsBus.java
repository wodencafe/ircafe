package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.model.NotificationRule;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class UiSettingsBus {

  public static final String PROP_UI_SETTINGS = "uiSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile UiSettings current;

  public UiSettingsBus(UiProperties props, RuntimeConfigStore runtimeConfig) {
    UiProperties.HostmaskDiscovery hm = props.hostmaskDiscovery();
    UiProperties.UserInfoEnrichment ue = props.userInfoEnrichment();
    UiProperties.MonitorFallback mf = props.monitorFallback();
    UiProperties.Timestamps ts = props.timestamps();
    UiProperties.Tray tray = props.tray();

    boolean timestampsEnabled =
        ts == null || ts.enabled() == null || Boolean.TRUE.equals(ts.enabled());
    String timestampFormat = ts != null ? ts.format() : "HH:mm:ss";
    boolean timestampsIncludeChatMessages =
        ts == null
            || ts.includeChatMessages() == null
            || Boolean.TRUE.equals(ts.includeChatMessages());
    boolean timestampsIncludePresenceMessages =
        ts == null
            || ts.includePresenceMessages() == null
            || Boolean.TRUE.equals(ts.includePresenceMessages());

    boolean trayEnabled =
        tray == null || tray.enabled() == null || Boolean.TRUE.equals(tray.enabled());

    // Migration rule:
    // - New installs default to "close exits" (closeToTray=false)
    // - Existing installs keep legacy behavior (closeToTray=true) unless explicitly configured
    boolean trayCloseToTray;
    var persistedCloseToTray = runtimeConfig.readTrayCloseToTrayIfPresent();
    if (persistedCloseToTray.isPresent()) {
      trayCloseToTray = Boolean.TRUE.equals(persistedCloseToTray.get());
    } else if (runtimeConfig.runtimeConfigFileExistedOnStartup()) {
      trayCloseToTray = true;
      // Persist once so the behavior is stable across future versions.
      runtimeConfig.rememberTrayCloseToTray(true);
    } else {
      trayCloseToTray = false;
    }

    boolean trayMinimizeToTray = tray != null && Boolean.TRUE.equals(tray.minimizeToTray());
    boolean trayStartMinimized = tray != null && Boolean.TRUE.equals(tray.startMinimized());

    boolean trayNotifyHighlights =
        tray == null
            || tray.notifyHighlights() == null
            || Boolean.TRUE.equals(tray.notifyHighlights());
    boolean trayNotifyPrivateMessages =
        tray == null
            || tray.notifyPrivateMessages() == null
            || Boolean.TRUE.equals(tray.notifyPrivateMessages());
    boolean trayNotifyConnectionState =
        tray != null && Boolean.TRUE.equals(tray.notifyConnectionState());

    boolean trayNotifyOnlyWhenUnfocused =
        tray != null && Boolean.TRUE.equals(tray.notifyOnlyWhenUnfocused());
    boolean trayNotifyOnlyWhenMinimizedOrHidden =
        tray != null && Boolean.TRUE.equals(tray.notifyOnlyWhenMinimizedOrHidden());
    boolean trayNotifySuppressWhenTargetActive =
        tray != null && Boolean.TRUE.equals(tray.notifySuppressWhenTargetActive());

    boolean trayLinuxDbusActionsEnabled =
        tray == null
            || tray.linuxDbusActionsEnabled() == null
            || Boolean.TRUE.equals(tray.linuxDbusActionsEnabled());
    NotificationBackendMode trayNotificationBackendMode =
        NotificationBackendMode.fromToken(tray != null ? tray.notificationBackend() : null);
    MemoryUsageDisplayMode memoryUsageDisplayMode =
        MemoryUsageDisplayMode.fromToken(props.memoryUsageDisplayMode());
    int memoryUsageWarningNearMaxPercent =
        props.memoryUsageWarningNearMaxPercent() != null
            ? props.memoryUsageWarningNearMaxPercent()
            : 5;
    boolean memoryUsageWarningTooltipEnabled =
        props.memoryUsageWarningTooltipEnabled() == null
            || Boolean.TRUE.equals(props.memoryUsageWarningTooltipEnabled());
    boolean memoryUsageWarningToastEnabled =
        props.memoryUsageWarningToastEnabled() != null
            && Boolean.TRUE.equals(props.memoryUsageWarningToastEnabled());
    boolean memoryUsageWarningPushyEnabled =
        props.memoryUsageWarningPushyEnabled() != null
            && Boolean.TRUE.equals(props.memoryUsageWarningPushyEnabled());
    boolean memoryUsageWarningSoundEnabled =
        props.memoryUsageWarningSoundEnabled() != null
            && Boolean.TRUE.equals(props.memoryUsageWarningSoundEnabled());

    boolean enrichmentEnabled = ue != null && Boolean.TRUE.equals(ue.enabled());
    boolean whoisFallbackEnabled = ue != null && Boolean.TRUE.equals(ue.whoisFallbackEnabled());
    boolean periodicRefreshEnabled = ue != null && Boolean.TRUE.equals(ue.periodicRefreshEnabled());
    boolean typingIndicatorsSendEnabled =
        props.typingIndicatorsEnabled() == null
            || Boolean.TRUE.equals(props.typingIndicatorsEnabled());
    boolean typingIndicatorsReceiveEnabled =
        props.typingIndicatorsReceiveEnabled() == null
            ? typingIndicatorsSendEnabled
            : Boolean.TRUE.equals(props.typingIndicatorsReceiveEnabled());
    boolean typingIndicatorsTreeEnabled =
        props.typingIndicatorsTreeEnabled() == null
            ? typingIndicatorsReceiveEnabled
            : Boolean.TRUE.equals(props.typingIndicatorsTreeEnabled());
    boolean typingIndicatorsUsersListEnabled =
        props.typingIndicatorsUsersListEnabled() == null
            ? typingIndicatorsReceiveEnabled
            : Boolean.TRUE.equals(props.typingIndicatorsUsersListEnabled());
    boolean typingIndicatorsTranscriptEnabled =
        props.typingIndicatorsTranscriptEnabled() == null
            ? typingIndicatorsReceiveEnabled
            : Boolean.TRUE.equals(props.typingIndicatorsTranscriptEnabled());
    boolean typingIndicatorsSendSignalEnabled =
        props.typingIndicatorsSendSignalEnabled() == null
            ? typingIndicatorsSendEnabled
            : Boolean.TRUE.equals(props.typingIndicatorsSendSignalEnabled());

    List<NotificationRule> notificationRules =
        props.notificationRules() == null
            ? List.of()
            : props.notificationRules().stream()
                .filter(Objects::nonNull)
                .map(
                    r -> {
                      NotificationRule.Type t =
                          r.type() != null
                              ? NotificationRule.Type.valueOf(r.type().name())
                              : NotificationRule.Type.WORD;
                      return new NotificationRule(
                          r.label(),
                          t,
                          r.pattern(),
                          Boolean.TRUE.equals(r.enabled()),
                          Boolean.TRUE.equals(r.caseSensitive()),
                          Boolean.TRUE.equals(r.wholeWord()),
                          r.highlightFg());
                    })
                .toList();

    this.current =
        new UiSettings(
            props.theme(),
            props.chatFontFamily(),
            props.chatFontSize(),
            props.autoConnectOnStart() == null || Boolean.TRUE.equals(props.autoConnectOnStart()),
            trayEnabled,
            trayCloseToTray,
            trayMinimizeToTray,
            trayStartMinimized,
            trayNotifyHighlights,
            trayNotifyPrivateMessages,
            trayNotifyConnectionState,
            trayNotifyOnlyWhenUnfocused,
            trayNotifyOnlyWhenMinimizedOrHidden,
            trayNotifySuppressWhenTargetActive,
            trayLinuxDbusActionsEnabled,
            trayNotificationBackendMode,
            props.imageEmbedsEnabled(),
            props.imageEmbedsCollapsedByDefault(),
            props.imageEmbedsMaxWidthPx(),
            props.imageEmbedsMaxHeightPx(),
            props.imageEmbedsAnimateGifs(),
            props.linkPreviewsEnabled(),
            props.linkPreviewsCollapsedByDefault(),
            props.presenceFoldsEnabled(),
            props.ctcpRequestsInActiveTargetEnabled() == null
                || Boolean.TRUE.equals(props.ctcpRequestsInActiveTargetEnabled()),
            typingIndicatorsSendEnabled,
            typingIndicatorsReceiveEnabled,
            props.typingTreeIndicatorStyle(),
            typingIndicatorsTreeEnabled,
            typingIndicatorsUsersListEnabled,
            typingIndicatorsTranscriptEnabled,
            typingIndicatorsSendSignalEnabled,
            timestampsEnabled,
            timestampFormat,
            timestampsIncludeChatMessages,
            timestampsIncludePresenceMessages,
            props.chatHistoryInitialLoadLines() != null ? props.chatHistoryInitialLoadLines() : 100,
            props.chatHistoryPageSize() != null ? props.chatHistoryPageSize() : 200,
            props.chatHistoryAutoLoadWheelDebounceMs() != null
                ? props.chatHistoryAutoLoadWheelDebounceMs()
                : 2000,
            props.chatHistoryLoadOlderChunkSize() != null
                ? props.chatHistoryLoadOlderChunkSize()
                : 20,
            props.chatHistoryLoadOlderChunkDelayMs() != null
                ? props.chatHistoryLoadOlderChunkDelayMs()
                : 0,
            props.chatHistoryLoadOlderChunkEdtBudgetMs() != null
                ? props.chatHistoryLoadOlderChunkEdtBudgetMs()
                : 6,
            props.chatHistoryDeferRichTextDuringBatch() != null
                ? props.chatHistoryDeferRichTextDuringBatch()
                : false,
            props.chatHistoryRemoteRequestTimeoutSeconds() != null
                ? props.chatHistoryRemoteRequestTimeoutSeconds()
                : 6,
            props.chatHistoryRemoteZncPlaybackTimeoutSeconds() != null
                ? props.chatHistoryRemoteZncPlaybackTimeoutSeconds()
                : 18,
            props.chatHistoryRemoteZncPlaybackWindowMinutes() != null
                ? props.chatHistoryRemoteZncPlaybackWindowMinutes()
                : 360,
            props.commandHistoryMaxSize() != null ? props.commandHistoryMaxSize() : 500,
            props.chatTranscriptMaxLinesPerTarget() != null
                ? props.chatTranscriptMaxLinesPerTarget()
                : 4000,
            props.clientLineColorEnabled(),
            props.clientLineColor(),
            props.outgoingDeliveryIndicatorsEnabled() == null
                || Boolean.TRUE.equals(props.outgoingDeliveryIndicatorsEnabled()),
            props.serverTreeNotificationBadgesEnabled() == null
                || Boolean.TRUE.equals(props.serverTreeNotificationBadgesEnabled()),

            // Hostmask discovery / USERHOST
            hm == null || Boolean.TRUE.equals(hm.userhostEnabled()),
            hm != null && hm.userhostMinIntervalSeconds() != null
                ? hm.userhostMinIntervalSeconds()
                : 7,
            hm != null && hm.userhostMaxCommandsPerMinute() != null
                ? hm.userhostMaxCommandsPerMinute()
                : 6,
            hm != null && hm.userhostNickCooldownMinutes() != null
                ? hm.userhostNickCooldownMinutes()
                : 30,
            hm != null && hm.userhostMaxNicksPerCommand() != null
                ? hm.userhostMaxNicksPerCommand()
                : 5,

            // User info enrichment (fallback)
            enrichmentEnabled,
            ue != null && ue.userhostMinIntervalSeconds() != null
                ? ue.userhostMinIntervalSeconds()
                : 15,
            ue != null && ue.userhostMaxCommandsPerMinute() != null
                ? ue.userhostMaxCommandsPerMinute()
                : 3,
            ue != null && ue.userhostNickCooldownMinutes() != null
                ? ue.userhostNickCooldownMinutes()
                : 60,
            ue != null && ue.userhostMaxNicksPerCommand() != null
                ? ue.userhostMaxNicksPerCommand()
                : 5,
            whoisFallbackEnabled,
            ue != null && ue.whoisMinIntervalSeconds() != null ? ue.whoisMinIntervalSeconds() : 45,
            ue != null && ue.whoisNickCooldownMinutes() != null
                ? ue.whoisNickCooldownMinutes()
                : 120,
            periodicRefreshEnabled,
            ue != null && ue.periodicRefreshIntervalSeconds() != null
                ? ue.periodicRefreshIntervalSeconds()
                : 300,
            ue != null && ue.periodicRefreshNicksPerTick() != null
                ? ue.periodicRefreshNicksPerTick()
                : 2,
            mf != null && mf.isonPollIntervalSeconds() != null ? mf.isonPollIntervalSeconds() : 30,
            props.notificationRuleCooldownSeconds() != null
                ? props.notificationRuleCooldownSeconds()
                : 15,
            memoryUsageDisplayMode,
            memoryUsageWarningNearMaxPercent,
            memoryUsageWarningTooltipEnabled,
            memoryUsageWarningToastEnabled,
            memoryUsageWarningPushyEnabled,
            memoryUsageWarningSoundEnabled,
            notificationRules);
  }

  public UiSettings get() {
    return current;
  }

  public void set(UiSettings next) {
    UiSettings prev = this.current;
    this.current = next;
    pcs.firePropertyChange(PROP_UI_SETTINGS, prev, next);
  }

  public void refresh() {
    UiSettings cur = this.current;
    pcs.firePropertyChange(PROP_UI_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
