package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ChatThemeSettingsBus {

  public static final String PROP_CHAT_THEME_SETTINGS = "chatThemeSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile ChatThemeSettings current;

  public ChatThemeSettingsBus(UiProperties props) {
    ChatThemeSettings.Preset preset = props != null
        ? ChatThemeSettings.Preset.from(props.chatThemePreset())
        : ChatThemeSettings.Preset.DEFAULT;

    String ts = props != null ? props.chatTimestampColor() : null;
    String sys = props != null ? props.chatSystemColor() : null;
    String mention = props != null ? props.chatMentionBgColor() : null;

    int strength = 35;
    if (props != null && props.chatMentionStrength() != null) {
      strength = props.chatMentionStrength();
    }

    this.current = new ChatThemeSettings(preset, ts, sys, mention, strength);
  }

  public ChatThemeSettings get() {
    return current;
  }

  public void set(ChatThemeSettings next) {
    ChatThemeSettings prev = this.current;
    this.current = next != null
        ? next
        : new ChatThemeSettings(ChatThemeSettings.Preset.DEFAULT, null, null, null, 35);
    pcs.firePropertyChange(PROP_CHAT_THEME_SETTINGS, prev, this.current);
  }

  public void refresh() {
    ChatThemeSettings cur = this.current;
    pcs.firePropertyChange(PROP_CHAT_THEME_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
