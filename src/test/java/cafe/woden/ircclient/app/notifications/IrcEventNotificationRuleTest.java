package cafe.woden.ircclient.app.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.notify.sound.BuiltInSound;
import org.junit.jupiter.api.Test;

class IrcEventNotificationRuleTest {

  @Test
  void sourceAndChannelFiltersAreApplied() {
    IrcEventNotificationRule rule = new IrcEventNotificationRule(
        true,
        IrcEventNotificationRule.EventType.INVITE_RECEIVED,
        IrcEventNotificationRule.SourceFilter.OTHERS,
        true,
        false,
        BuiltInSound.NOTIF_1.name(),
        false,
        null,
        "#staff*",
        "#staff-secret");

    assertTrue(rule.matches(IrcEventNotificationRule.EventType.INVITE_RECEIVED, Boolean.FALSE, "#staff-chat"));
    assertFalse(rule.matches(IrcEventNotificationRule.EventType.INVITE_RECEIVED, Boolean.TRUE, "#staff-chat"));
    assertFalse(rule.matches(IrcEventNotificationRule.EventType.INVITE_RECEIVED, Boolean.FALSE, "#staff-secret"));
    assertFalse(rule.matches(IrcEventNotificationRule.EventType.KICKED, Boolean.FALSE, "#staff-chat"));
  }

  @Test
  void whitelistRequiresChannelWhenSet() {
    IrcEventNotificationRule rule = new IrcEventNotificationRule(
        true,
        IrcEventNotificationRule.EventType.KLINED,
        IrcEventNotificationRule.SourceFilter.ANY,
        true,
        true,
        BuiltInSound.NOTIF_2.name(),
        false,
        null,
        "#ops*",
        null);

    assertFalse(rule.matches(IrcEventNotificationRule.EventType.KLINED, null, null));
    assertTrue(rule.matches(IrcEventNotificationRule.EventType.KLINED, null, "#ops"));
  }
}
