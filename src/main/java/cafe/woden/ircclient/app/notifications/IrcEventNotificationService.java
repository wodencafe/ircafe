package cafe.woden.ircclient.app.notifications;

import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Evaluates configured IRC-event notification rules and dispatches matching tray notifications.
 */
@Component
@Lazy
public class IrcEventNotificationService {

  private final IrcEventNotificationRulesBus rulesBus;
  private final TrayNotificationService trayNotificationService;

  public IrcEventNotificationService(
      IrcEventNotificationRulesBus rulesBus,
      TrayNotificationService trayNotificationService
  ) {
    this.rulesBus = rulesBus;
    this.trayNotificationService = trayNotificationService;
  }

  /**
   * Returns true if a rule matched and a notification dispatch was attempted.
   */
  public boolean notifyConfigured(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      Boolean sourceIsSelf,
      String title,
      String body
  ) {
    if (eventType == null || trayNotificationService == null) return false;

    List<IrcEventNotificationRule> rules = rulesBus != null ? rulesBus.get() : List.of();
    if (rules == null || rules.isEmpty()) return false;

    IrcEventNotificationRule matched = null;
    for (IrcEventNotificationRule r : rules) {
      if (r == null) continue;
      if (r.matches(eventType, sourceIsSelf, channel)) {
        matched = r;
        break;
      }
    }
    if (matched == null) return false;

    boolean showToast = matched.toastEnabled();
    boolean playSound = matched.soundEnabled();
    if (!showToast && !playSound) return true;

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;

    String target = Objects.toString(channel, "").trim();
    if (target.isEmpty()) target = "status";

    String t = Objects.toString(title, "").trim();
    if (t.isEmpty()) t = eventType.toString();
    String b = Objects.toString(body, "").trim();

    trayNotificationService.notifyCustom(
        sid,
        target,
        t,
        b,
        showToast,
        playSound,
        matched.soundId(),
        matched.soundUseCustom(),
        matched.soundCustomPath());
    return true;
  }

  public boolean hasEnabledRuleFor(IrcEventNotificationRule.EventType eventType) {
    if (eventType == null) return false;
    List<IrcEventNotificationRule> rules = rulesBus != null ? rulesBus.get() : List.of();
    if (rules == null || rules.isEmpty()) return false;
    for (IrcEventNotificationRule r : rules) {
      if (r == null) continue;
      if (!r.enabled()) continue;
      if (r.eventType() == eventType) return true;
    }
    return false;
  }
}
