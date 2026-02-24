package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ThemeTweakSettingsBus {

  public static final String PROP_THEME_TWEAK_SETTINGS = "themeTweakSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile ThemeTweakSettings current;

  public ThemeTweakSettingsBus(UiProperties props) {
    String density = props != null ? props.density() : null;
    Integer radius = props != null ? props.cornerRadius() : null;
    boolean uiFontOverrideEnabled =
        props != null && props.uiFontOverrideEnabled() != null && props.uiFontOverrideEnabled();
    String uiFontFamily = props != null ? props.uiFontFamily() : null;
    Integer uiFontSize = props != null ? props.uiFontSize() : null;

    ThemeTweakSettings.ThemeDensity d = ThemeTweakSettings.ThemeDensity.from(density);
    int r = radius != null ? radius : 10;
    int fs = uiFontSize != null ? uiFontSize : ThemeTweakSettings.DEFAULT_UI_FONT_SIZE;

    this.current = new ThemeTweakSettings(d, r, uiFontOverrideEnabled, uiFontFamily, fs);
  }

  public ThemeTweakSettings get() {
    return current;
  }

  public void set(ThemeTweakSettings next) {
    ThemeTweakSettings prev = this.current;
    this.current =
        next != null ? next : new ThemeTweakSettings(ThemeTweakSettings.ThemeDensity.AUTO, 10);
    pcs.firePropertyChange(PROP_THEME_TWEAK_SETTINGS, prev, this.current);
  }

  public void refresh() {
    ThemeTweakSettings cur = this.current;
    pcs.firePropertyChange(PROP_THEME_TWEAK_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
