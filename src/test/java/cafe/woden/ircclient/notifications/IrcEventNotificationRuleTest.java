package cafe.woden.ircclient.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import org.junit.jupiter.api.Test;

class IrcEventNotificationRuleTest {

  @Test
  void defaultsIncludeStatusBarAnyCompanionsForCoreModerationEvents() {
    java.util.List<IrcEventNotificationRule> defaults = IrcEventNotificationRule.defaults();
    assertHasStatusBarAnyCompanion(defaults, IrcEventNotificationRule.EventType.KICKED);
    assertHasStatusBarAnyCompanion(defaults, IrcEventNotificationRule.EventType.BANNED);
    assertHasStatusBarAnyCompanion(defaults, IrcEventNotificationRule.EventType.KLINED);
  }

  @Test
  void defaultsUseEventSpecificBuiltInSoundMappings() {
    assertEquals(
        BuiltInSound.YOU_DEOPPED,
        IrcEventNotificationRule.defaultBuiltInSoundForEvent(
            IrcEventNotificationRule.EventType.YOU_DEOPPED));
    assertEquals(
        BuiltInSound.SOMEBODY_DEOPPED,
        IrcEventNotificationRule.defaultBuiltInSoundForEvent(
            IrcEventNotificationRule.EventType.DEOPPED));
    assertEquals(
        BuiltInSound.CHANNEL_INVITE_1,
        IrcEventNotificationRule.defaultBuiltInSoundForEvent(
            IrcEventNotificationRule.EventType.INVITE_RECEIVED));
    assertEquals(
        BuiltInSound.SOMEBODY_SENT_CTCP_1,
        IrcEventNotificationRule.defaultBuiltInSoundForEvent(
            IrcEventNotificationRule.EventType.CTCP_RECEIVED));
    assertEquals(
        BuiltInSound.NETSPLIT_1,
        IrcEventNotificationRule.defaultBuiltInSoundForEvent(
            IrcEventNotificationRule.EventType.NETSPLIT_DETECTED));
    assertEquals(
        BuiltInSound.WALLOPS_1,
        IrcEventNotificationRule.defaultBuiltInSoundForEvent(
            IrcEventNotificationRule.EventType.WALLOPS_RECEIVED));
  }

  @Test
  void sourceAndChannelFiltersAreApplied() {
    IrcEventNotificationRule rule =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.INVITE_RECEIVED,
            IrcEventNotificationRule.SourceMode.OTHERS,
            null,
            IrcEventNotificationRule.ChannelScope.ONLY,
            "#staff*",
            true,
            IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
            true,
            true,
            false,
            BuiltInSound.NOTIF_1.name(),
            false,
            null,
            false,
            null,
            null,
            null);

    assertTrue(
        rule.matches(
            IrcEventNotificationRule.EventType.INVITE_RECEIVED,
            "alice",
            Boolean.FALSE,
            "#staff-chat"));
    assertFalse(
        rule.matches(
            IrcEventNotificationRule.EventType.INVITE_RECEIVED,
            "alice",
            Boolean.TRUE,
            "#staff-chat"));
    assertFalse(
        rule.matches(
            IrcEventNotificationRule.EventType.INVITE_RECEIVED,
            "alice",
            Boolean.FALSE,
            "#general"));
    assertFalse(
        rule.matches(
            IrcEventNotificationRule.EventType.KICKED, "alice", Boolean.FALSE, "#staff-chat"));
  }

  @Test
  void sourceMatcherSupportsNickListGlobAndRegex() {
    IrcEventNotificationRule nickList =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.USER_JOINED,
            IrcEventNotificationRule.SourceMode.NICK_LIST,
            "alice bob",
            IrcEventNotificationRule.ChannelScope.ALL,
            null,
            true,
            IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
            true,
            true,
            false,
            BuiltInSound.NOTIF_1.name(),
            false,
            null,
            false,
            null,
            null,
            null);

    IrcEventNotificationRule glob =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.USER_JOINED,
            IrcEventNotificationRule.SourceMode.GLOB,
            "mod*",
            IrcEventNotificationRule.ChannelScope.ALL,
            null,
            true,
            IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
            true,
            true,
            false,
            BuiltInSound.NOTIF_1.name(),
            false,
            null,
            false,
            null,
            null,
            null);

    IrcEventNotificationRule regex =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.USER_JOINED,
            IrcEventNotificationRule.SourceMode.REGEX,
            "^op[0-9]+$",
            IrcEventNotificationRule.ChannelScope.ALL,
            null,
            true,
            IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
            true,
            true,
            false,
            BuiltInSound.NOTIF_1.name(),
            false,
            null,
            false,
            null,
            null,
            null);

    assertTrue(
        nickList.matches(
            IrcEventNotificationRule.EventType.USER_JOINED, "Alice", Boolean.FALSE, "#chan"));
    assertFalse(
        nickList.matches(
            IrcEventNotificationRule.EventType.USER_JOINED, "charlie", Boolean.FALSE, "#chan"));

    assertTrue(
        glob.matches(
            IrcEventNotificationRule.EventType.USER_JOINED, "Moderator", Boolean.FALSE, "#chan"));
    assertFalse(
        glob.matches(
            IrcEventNotificationRule.EventType.USER_JOINED, "user", Boolean.FALSE, "#chan"));

    assertTrue(
        regex.matches(
            IrcEventNotificationRule.EventType.USER_JOINED, "op42", Boolean.FALSE, "#chan"));
    assertFalse(
        regex.matches(
            IrcEventNotificationRule.EventType.USER_JOINED, "opx", Boolean.FALSE, "#chan"));
  }

  @Test
  void allExceptScopeAllowsServerWideEventsWithoutChannel() {
    IrcEventNotificationRule rule =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.KLINED,
            IrcEventNotificationRule.SourceMode.ANY,
            null,
            IrcEventNotificationRule.ChannelScope.ALL_EXCEPT,
            "#ops*",
            true,
            IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
            true,
            true,
            true,
            BuiltInSound.NOTIF_2.name(),
            false,
            null,
            false,
            null,
            null,
            null);

    assertTrue(rule.matches(IrcEventNotificationRule.EventType.KLINED, null, null, null));
    assertTrue(rule.matches(IrcEventNotificationRule.EventType.KLINED, null, null, "#general"));
    assertFalse(rule.matches(IrcEventNotificationRule.EventType.KLINED, null, null, "#ops"));
  }

  @Test
  void activeTargetOnlyScopeRequiresSameServerAndMatchingActiveChannel() {
    IrcEventNotificationRule rule =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.TOPIC_CHANGED,
            IrcEventNotificationRule.SourceMode.ANY,
            null,
            IrcEventNotificationRule.ChannelScope.ACTIVE_TARGET_ONLY,
            "#ignored",
            true,
            IrcEventNotificationRule.FocusScope.ANY,
            true,
            true,
            false,
            BuiltInSound.TOPIC_CHANGED_1.name(),
            false,
            null,
            false,
            null,
            null,
            null);

    assertFalse(
        rule.matches(IrcEventNotificationRule.EventType.TOPIC_CHANGED, null, null, "#chat"));
    assertFalse(
        rule.matches(
            IrcEventNotificationRule.EventType.TOPIC_CHANGED, null, null, "#chat", false, "#chat"));
    assertFalse(
        rule.matches(
            IrcEventNotificationRule.EventType.TOPIC_CHANGED, null, null, "#chat", true, "#other"));
    assertTrue(
        rule.matches(
            IrcEventNotificationRule.EventType.TOPIC_CHANGED, null, null, "#chat", true, "#chat"));
  }

  private static void assertHasStatusBarAnyCompanion(
      java.util.List<IrcEventNotificationRule> rules,
      IrcEventNotificationRule.EventType eventType) {
    assertTrue(
        rules.stream()
            .anyMatch(
                r ->
                    r != null
                        && r.eventType() == eventType
                        && r.enabled()
                        && !r.toastEnabled()
                        && r.focusScope() == IrcEventNotificationRule.FocusScope.ANY
                        && r.statusBarEnabled()
                        && !r.soundEnabled()));
  }
}
