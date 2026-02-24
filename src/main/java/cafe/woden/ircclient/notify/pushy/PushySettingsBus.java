package cafe.woden.ircclient.notify.pushy;

import cafe.woden.ircclient.config.PushyProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class PushySettingsBus {

  private volatile PushyProperties current;

  public PushySettingsBus(PushyProperties properties) {
    this.current = sanitize(properties);
  }

  public PushyProperties get() {
    return current;
  }

  public void set(PushyProperties next) {
    this.current = sanitize(next);
  }

  private static PushyProperties sanitize(PushyProperties value) {
    if (value != null) return value;
    return new PushyProperties(false, null, null, null, null, null, null, null);
  }
}
