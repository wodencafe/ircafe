package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Publishes the current user-command alias list. */
@Component
@Lazy
public class UserCommandAliasesBus {

  public static final String PROP_USER_COMMAND_ALIASES = "userCommandAliases";
  public static final String PROP_UNKNOWN_COMMAND_AS_RAW = "unknownCommandAsRaw";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile List<UserCommandAlias> current;
  private volatile boolean unknownCommandAsRawEnabled;

  public UserCommandAliasesBus(RuntimeConfigStore runtimeConfig) {
    List<UserCommandAlias> seeded = runtimeConfig != null ? runtimeConfig.readUserCommandAliases() : List.of();
    this.current = sanitize(seeded);
    this.unknownCommandAsRawEnabled =
        runtimeConfig != null && runtimeConfig.readUnknownCommandAsRawEnabled(false);
  }

  public List<UserCommandAlias> get() {
    return current;
  }

  public void set(List<UserCommandAlias> next) {
    List<UserCommandAlias> safe = sanitize(next);
    List<UserCommandAlias> prev = this.current;
    this.current = safe;
    pcs.firePropertyChange(PROP_USER_COMMAND_ALIASES, prev, safe);
  }

  public boolean unknownCommandAsRawEnabled() {
    return unknownCommandAsRawEnabled;
  }

  public void setUnknownCommandAsRawEnabled(boolean enabled) {
    boolean prev = this.unknownCommandAsRawEnabled;
    this.unknownCommandAsRawEnabled = enabled;
    pcs.firePropertyChange(PROP_UNKNOWN_COMMAND_AS_RAW, prev, enabled);
  }

  public void refresh() {
    List<UserCommandAlias> cur = this.current;
    pcs.firePropertyChange(PROP_USER_COMMAND_ALIASES, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }

  private static List<UserCommandAlias> sanitize(List<UserCommandAlias> aliases) {
    if (aliases == null) return List.of();
    return List.copyOf(aliases.stream().filter(Objects::nonNull).toList());
  }
}
