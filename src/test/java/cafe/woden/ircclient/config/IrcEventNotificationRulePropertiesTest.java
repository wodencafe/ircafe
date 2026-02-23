package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IrcEventNotificationRulePropertiesTest {

  @Test
  void defaultsIncludeStatusBarAnyCompanionsForCoreModerationEvents() {
    List<IrcEventNotificationRuleProperties> defaults = IrcEventNotificationRuleProperties.defaultRules();
    assertHasStatusBarAnyCompanion(defaults, IrcEventNotificationRuleProperties.EventType.KICKED);
    assertHasStatusBarAnyCompanion(defaults, IrcEventNotificationRuleProperties.EventType.BANNED);
    assertHasStatusBarAnyCompanion(defaults, IrcEventNotificationRuleProperties.EventType.KLINED);
  }

  private static void assertHasStatusBarAnyCompanion(
      List<IrcEventNotificationRuleProperties> rules,
      IrcEventNotificationRuleProperties.EventType eventType
  ) {
    assertTrue(
        rules.stream().anyMatch(r ->
            r != null
                && r.eventType() == eventType
                && Boolean.TRUE.equals(r.enabled())
                && Boolean.FALSE.equals(r.toastEnabled())
                && r.focusScope() == IrcEventNotificationRuleProperties.FocusScope.ANY
                && Boolean.TRUE.equals(r.statusBarEnabled())
                && Boolean.FALSE.equals(r.soundEnabled())));
  }
}

