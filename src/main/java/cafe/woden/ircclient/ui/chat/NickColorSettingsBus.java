package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class NickColorSettingsBus {

  public static final String PROP_NICK_COLOR_SETTINGS = "nickColorSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile NickColorSettings current;

  public NickColorSettingsBus(UiProperties props) {
    Boolean en = (props != null) ? props.nickColoringEnabled() : null;
    boolean enabled = (en == null) || Boolean.TRUE.equals(en);

    double mc = (props != null) ? props.nickColorMinContrast() : 3.0;
    if (mc <= 0) mc = 3.0;

    this.current = new NickColorSettings(enabled, mc);
  }

  public NickColorSettings get() {
    return current;
  }

  public void set(NickColorSettings next) {
    NickColorSettings prev = this.current;
    this.current = next;
    pcs.firePropertyChange(PROP_NICK_COLOR_SETTINGS, prev, next);
  }

  public void refresh() {
    NickColorSettings cur = this.current;
    pcs.firePropertyChange(PROP_NICK_COLOR_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
