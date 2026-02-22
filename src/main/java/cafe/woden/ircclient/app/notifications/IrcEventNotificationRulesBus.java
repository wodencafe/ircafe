package cafe.woden.ircclient.app.notifications;

import cafe.woden.ircclient.config.IrcEventNotificationRuleProperties;
import cafe.woden.ircclient.config.UiProperties;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class IrcEventNotificationRulesBus {

  public static final String PROP_IRC_EVENT_NOTIFICATION_RULES = "ircEventNotificationRules";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile List<IrcEventNotificationRule> current;

  public IrcEventNotificationRulesBus(UiProperties props) {
    List<IrcEventNotificationRuleProperties> raw = props != null ? props.ircEventNotificationRules() : null;
    if (raw == null) {
      this.current = IrcEventNotificationRule.defaults();
    } else {
      this.current = mapRules(raw);
    }
  }

  public List<IrcEventNotificationRule> get() {
    return current;
  }

  public void set(List<IrcEventNotificationRule> next) {
    List<IrcEventNotificationRule> safe = sanitize(next);
    List<IrcEventNotificationRule> prev = this.current;
    this.current = safe;
    pcs.firePropertyChange(PROP_IRC_EVENT_NOTIFICATION_RULES, prev, safe);
  }

  public void refresh() {
    List<IrcEventNotificationRule> cur = this.current;
    pcs.firePropertyChange(PROP_IRC_EVENT_NOTIFICATION_RULES, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }

  private static List<IrcEventNotificationRule> mapRules(List<IrcEventNotificationRuleProperties> props) {
    if (props == null) return IrcEventNotificationRule.defaults();
    return sanitize(
        props.stream()
            .filter(Objects::nonNull)
            .map(IrcEventNotificationRulesBus::mapRule)
            .toList());
  }

  private static IrcEventNotificationRule mapRule(IrcEventNotificationRuleProperties p) {
    if (p == null) {
      return new IrcEventNotificationRule(
          false,
          IrcEventNotificationRule.EventType.INVITE_RECEIVED,
          IrcEventNotificationRule.SourceFilter.ANY,
          true,
          false,
          null,
          false,
          null,
          null,
          null);
    }

    IrcEventNotificationRule.EventType eventType;
    try {
      eventType = IrcEventNotificationRule.EventType.valueOf(p.eventType().name());
    } catch (Exception ignored) {
      eventType = IrcEventNotificationRule.EventType.INVITE_RECEIVED;
    }

    IrcEventNotificationRule.SourceFilter sourceFilter;
    try {
      sourceFilter = IrcEventNotificationRule.SourceFilter.valueOf(p.sourceFilter().name());
    } catch (Exception ignored) {
      sourceFilter = IrcEventNotificationRule.SourceFilter.ANY;
    }

    return new IrcEventNotificationRule(
        Boolean.TRUE.equals(p.enabled()),
        eventType,
        sourceFilter,
        Boolean.TRUE.equals(p.toastEnabled()),
        Boolean.TRUE.equals(p.soundEnabled()),
        p.soundId(),
        Boolean.TRUE.equals(p.soundUseCustom()),
        p.soundCustomPath(),
        p.channelWhitelist(),
        p.channelBlacklist());
  }

  private static List<IrcEventNotificationRule> sanitize(List<IrcEventNotificationRule> rules) {
    if (rules == null) return List.of();
    return List.copyOf(rules.stream().filter(Objects::nonNull).toList());
  }
}
