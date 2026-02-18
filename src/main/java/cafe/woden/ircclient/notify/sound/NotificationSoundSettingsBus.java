package cafe.woden.ircclient.notify.sound;

import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class NotificationSoundSettingsBus {

  public static final String PROP_NOTIFICATION_SOUND_SETTINGS = "notificationSoundSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile NotificationSoundSettings current;

  public NotificationSoundSettingsBus(UiProperties props) {
    UiProperties.Tray tray = props != null ? props.tray() : null;

    boolean enabled = tray == null || tray.notificationSoundsEnabled() == null || Boolean.TRUE.equals(tray.notificationSoundsEnabled());
    String id = tray != null ? tray.notificationSound() : BuiltInSound.NOTIF_1.name();
    if (id == null || id.isBlank()) id = BuiltInSound.NOTIF_1.name();

    boolean useCustom = tray != null && Boolean.TRUE.equals(tray.notificationSoundUseCustom());
    String customPath = tray != null ? tray.notificationSoundCustomPath() : null;

    this.current = new NotificationSoundSettings(enabled, id, useCustom, customPath);
  }

  public NotificationSoundSettings get() {
    return current;
  }

  public void set(NotificationSoundSettings next) {
    NotificationSoundSettings prev = this.current;
    this.current = next;
    pcs.firePropertyChange(PROP_NOTIFICATION_SOUND_SETTINGS, prev, next);
  }

  public void refresh() {
    NotificationSoundSettings cur = this.current;
    pcs.firePropertyChange(PROP_NOTIFICATION_SOUND_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
