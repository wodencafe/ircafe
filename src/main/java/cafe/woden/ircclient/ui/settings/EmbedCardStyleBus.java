package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Mutable runtime holder for the selected embed card style preset. */
@Component
@Lazy
public class EmbedCardStyleBus {

  public static final String PROP_EMBED_CARD_STYLE = "embedCardStyle";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile EmbedCardStyle current;

  public EmbedCardStyleBus(UiProperties props) {
    this.current = EmbedCardStyle.fromToken(props != null ? props.embedCardStyle() : null);
  }

  public EmbedCardStyle get() {
    return current;
  }

  public void set(EmbedCardStyle next) {
    EmbedCardStyle normalized = next != null ? next : EmbedCardStyle.DEFAULT;
    EmbedCardStyle prev = this.current;
    if (prev == normalized) return;
    this.current = normalized;
    pcs.firePropertyChange(PROP_EMBED_CARD_STYLE, prev, normalized);
  }

  public void addListener(PropertyChangeListener listener) {
    if (listener == null) return;
    pcs.addPropertyChangeListener(listener);
  }

  public void removeListener(PropertyChangeListener listener) {
    if (listener == null) return;
    pcs.removePropertyChangeListener(listener);
  }
}
