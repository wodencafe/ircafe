package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
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
    UiProperties.Timestamps ts = props.timestamps();

    boolean timestampsEnabled = ts == null || ts.enabled() == null || Boolean.TRUE.equals(ts.enabled());
    String timestampFormat = ts != null ? ts.format() : "HH:mm:ss";
    boolean timestampsIncludeChatMessages = ts != null && Boolean.TRUE.equals(ts.includeChatMessages());

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
        props.clientLineColorEnabled(),
        props.clientLineColor(),

        // Hostmask discovery / USERHOST
        hm == null || Boolean.TRUE.equals(hm.userhostEnabled()),
        hm != null ? hm.userhostMinIntervalSeconds() : 7,
        hm != null ? hm.userhostMaxCommandsPerMinute() : 6,
        hm != null ? hm.userhostNickCooldownMinutes() : 30,
        hm != null ? hm.userhostMaxNicksPerCommand() : 5
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
