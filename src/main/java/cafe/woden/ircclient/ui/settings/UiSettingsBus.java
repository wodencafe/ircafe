package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
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

  public UiSettingsBus(UiProperties props) {
    UiProperties.HostmaskDiscovery hm = props.hostmaskDiscovery();
    UiProperties.UserInfoEnrichment ue = props.userInfoEnrichment();
    UiProperties.Timestamps ts = props.timestamps();

    boolean timestampsEnabled = ts == null || ts.enabled() == null || Boolean.TRUE.equals(ts.enabled());
    String timestampFormat = ts != null ? ts.format() : "HH:mm:ss";
    boolean timestampsIncludeChatMessages = ts != null && Boolean.TRUE.equals(ts.includeChatMessages());

    boolean enrichmentEnabled = ue != null && Boolean.TRUE.equals(ue.enabled());
    boolean whoisFallbackEnabled = ue != null && Boolean.TRUE.equals(ue.whoisFallbackEnabled());
    boolean periodicRefreshEnabled = ue != null && Boolean.TRUE.equals(ue.periodicRefreshEnabled());

    List<NotificationRule> notificationRules = props.notificationRules() == null
        ? List.of()
        : props.notificationRules().stream()
            .filter(Objects::nonNull)
            .map(r -> {
              NotificationRule.Type t = r.type() != null
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

    this.current = new UiSettings(
        props.theme(),
        props.chatFontFamily(),
        props.chatFontSize(),
        props.autoConnectOnStart() == null || Boolean.TRUE.equals(props.autoConnectOnStart()),
        props.imageEmbedsEnabled(),
        props.imageEmbedsCollapsedByDefault(),
        props.imageEmbedsMaxWidthPx(),
        props.imageEmbedsMaxHeightPx(),
        props.imageEmbedsAnimateGifs(),
        props.linkPreviewsEnabled(),
        props.linkPreviewsCollapsedByDefault(),
        props.presenceFoldsEnabled(),
        props.ctcpRequestsInActiveTargetEnabled() == null || Boolean.TRUE.equals(props.ctcpRequestsInActiveTargetEnabled()),
        timestampsEnabled,
        timestampFormat,
        timestampsIncludeChatMessages,
        props.chatHistoryInitialLoadLines() != null ? props.chatHistoryInitialLoadLines() : 100,
        props.chatHistoryPageSize() != null ? props.chatHistoryPageSize() : 200,
        props.commandHistoryMaxSize() != null ? props.commandHistoryMaxSize() : 500,
        props.clientLineColorEnabled(),
        props.clientLineColor(),

        // Hostmask discovery / USERHOST
        hm == null || Boolean.TRUE.equals(hm.userhostEnabled()),
        hm != null && hm.userhostMinIntervalSeconds() != null ? hm.userhostMinIntervalSeconds() : 7,
        hm != null && hm.userhostMaxCommandsPerMinute() != null ? hm.userhostMaxCommandsPerMinute() : 6,
        hm != null && hm.userhostNickCooldownMinutes() != null ? hm.userhostNickCooldownMinutes() : 30,
        hm != null && hm.userhostMaxNicksPerCommand() != null ? hm.userhostMaxNicksPerCommand() : 5,

        // User info enrichment (fallback)
        enrichmentEnabled,
        ue != null && ue.userhostMinIntervalSeconds() != null ? ue.userhostMinIntervalSeconds() : 15,
        ue != null && ue.userhostMaxCommandsPerMinute() != null ? ue.userhostMaxCommandsPerMinute() : 3,
        ue != null && ue.userhostNickCooldownMinutes() != null ? ue.userhostNickCooldownMinutes() : 60,
        ue != null && ue.userhostMaxNicksPerCommand() != null ? ue.userhostMaxNicksPerCommand() : 5,

        whoisFallbackEnabled,
        ue != null && ue.whoisMinIntervalSeconds() != null ? ue.whoisMinIntervalSeconds() : 45,
        ue != null && ue.whoisNickCooldownMinutes() != null ? ue.whoisNickCooldownMinutes() : 120,

        periodicRefreshEnabled,
        ue != null && ue.periodicRefreshIntervalSeconds() != null ? ue.periodicRefreshIntervalSeconds() : 300,
        ue != null && ue.periodicRefreshNicksPerTick() != null ? ue.periodicRefreshNicksPerTick() : 2,

        props.notificationRuleCooldownSeconds() != null ? props.notificationRuleCooldownSeconds() : 15,

        notificationRules
    );
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
