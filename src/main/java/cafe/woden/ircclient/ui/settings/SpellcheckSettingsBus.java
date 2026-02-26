package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class SpellcheckSettingsBus {

  public static final String PROP_SPELLCHECK_SETTINGS = "spellcheckSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile SpellcheckSettings current;

  public SpellcheckSettingsBus(UiProperties props) {
    this.current =
        new SpellcheckSettings(
            props == null || props.spellcheckEnabled() == null
                ? true
                : Boolean.TRUE.equals(props.spellcheckEnabled()),
            props == null || props.spellcheckUnderlineEnabled() == null
                ? true
                : Boolean.TRUE.equals(props.spellcheckUnderlineEnabled()),
            props == null || props.spellcheckSuggestOnTabEnabled() == null
                ? true
                : Boolean.TRUE.equals(props.spellcheckSuggestOnTabEnabled()),
            props != null ? props.spellcheckLanguageTag() : SpellcheckSettings.DEFAULT_LANGUAGE_TAG,
            props != null ? props.spellcheckCustomDictionary() : java.util.List.of());
  }

  public SpellcheckSettings get() {
    return current;
  }

  public void set(SpellcheckSettings next) {
    SpellcheckSettings prev = this.current;
    this.current = next != null ? next : SpellcheckSettings.defaults();
    pcs.firePropertyChange(PROP_SPELLCHECK_SETTINGS, prev, this.current);
  }

  public void refresh() {
    SpellcheckSettings cur = this.current;
    pcs.firePropertyChange(PROP_SPELLCHECK_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
