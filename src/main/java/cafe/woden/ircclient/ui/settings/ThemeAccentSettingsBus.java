package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ThemeAccentSettingsBus {

  public static final String PROP_THEME_ACCENT_SETTINGS = "themeAccentSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile ThemeAccentSettings current;

  public ThemeAccentSettingsBus(UiProperties props) {
    String accent = props != null ? props.accentColor() : null;
    int strength = props != null && props.accentStrength() != null ? props.accentStrength() : 70;
    this.current = new ThemeAccentSettings(accent, strength);
  }

  public ThemeAccentSettings get() {
    return current;
  }

  public void set(ThemeAccentSettings next) {
    ThemeAccentSettings prev = this.current;
    this.current = next != null ? next : new ThemeAccentSettings(null, 70);
    pcs.firePropertyChange(PROP_THEME_ACCENT_SETTINGS, prev, this.current);
  }

  public void refresh() {
    ThemeAccentSettings cur = this.current;
    pcs.firePropertyChange(PROP_THEME_ACCENT_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
