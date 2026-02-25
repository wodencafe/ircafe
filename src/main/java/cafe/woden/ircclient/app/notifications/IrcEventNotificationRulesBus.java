package cafe.woden.ircclient.app.notifications;

import cafe.woden.ircclient.config.IrcEventNotificationRuleProperties;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
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
    List<IrcEventNotificationRuleProperties> raw =
        props != null ? props.ircEventNotificationRules() : null;
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

  private static List<IrcEventNotificationRule> mapRules(
      List<IrcEventNotificationRuleProperties> props) {
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
          IrcEventNotificationRule.SourceMode.ANY,
          null,
          IrcEventNotificationRule.ChannelScope.ALL,
          null,
          true,
          IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
          true,
          true,
          false,
          null,
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

    IrcEventNotificationRule.SourceMode sourceMode;
    try {
      sourceMode = IrcEventNotificationRule.SourceMode.valueOf(p.sourceMode().name());
    } catch (Exception ignored) {
      sourceMode = IrcEventNotificationRule.SourceMode.ANY;
    }

    IrcEventNotificationRule.ChannelScope channelScope;
    try {
      channelScope = IrcEventNotificationRule.ChannelScope.valueOf(p.channelScope().name());
    } catch (Exception ignored) {
      channelScope = IrcEventNotificationRule.ChannelScope.ALL;
    }

    IrcEventNotificationRule.FocusScope focusScope;
    try {
      focusScope = IrcEventNotificationRule.FocusScope.valueOf(p.focusScope().name());
    } catch (Exception ignored) {
      focusScope =
          Boolean.TRUE.equals(p.toastWhenFocused())
              ? IrcEventNotificationRule.FocusScope.ANY
              : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;
    }

    return new IrcEventNotificationRule(
        Boolean.TRUE.equals(p.enabled()),
        eventType,
        sourceMode,
        p.sourcePattern(),
        channelScope,
        p.channelPatterns(),
        Boolean.TRUE.equals(p.toastEnabled()),
        focusScope,
        Boolean.TRUE.equals(p.statusBarEnabled()),
        Boolean.TRUE.equals(p.notificationsNodeEnabled()),
        Boolean.TRUE.equals(p.soundEnabled()),
        p.soundId(),
        Boolean.TRUE.equals(p.soundUseCustom()),
        p.soundCustomPath(),
        Boolean.TRUE.equals(p.scriptEnabled()),
        p.scriptPath(),
        p.scriptArgs(),
        p.scriptWorkingDirectory());
  }

  private static List<IrcEventNotificationRule> sanitize(List<IrcEventNotificationRule> rules) {
    if (rules == null) return List.of();
    return List.copyOf(rules.stream().filter(Objects::nonNull).toList());
  }
}
