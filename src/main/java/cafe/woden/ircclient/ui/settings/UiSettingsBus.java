package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Lightweight event hub for UI settings (font size, theme, etc.).
 */
@Component
@Lazy
public class UiSettingsBus {

  public static final String PROP_UI_SETTINGS = "uiSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile UiSettings current;

  public UiSettingsBus(UiProperties props) {
    this.current = new UiSettings(
        props.theme(),
        props.chatFontFamily(),
        props.chatFontSize(),
        props.imageEmbedsEnabled(),
        props.linkPreviewsEnabled(),
        props.presenceFoldsEnabled(),
        props.chatMessageTimestampsEnabled()
    );
  }

  public UiSettings get() {
    return current;
  }

  /**
   * Set and notify listeners.
   */
  public void set(UiSettings next) {
    UiSettings prev = this.current;
    this.current = next;
    pcs.firePropertyChange(PROP_UI_SETTINGS, prev, next);
  }

  /**
   * Re-fire the current value (useful after Look & Feel updates).
   */
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
