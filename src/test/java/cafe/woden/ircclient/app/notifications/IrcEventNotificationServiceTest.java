package cafe.woden.ircclient.app.notifications;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class IrcEventNotificationServiceTest {

  @Test
  void appliesAllMatchingRulesForSameEventType() {
    IrcEventNotificationRulesBus rulesBus = mock(IrcEventNotificationRulesBus.class);
    TrayNotificationService tray = mock(TrayNotificationService.class);
    NotificationStore store = mock(NotificationStore.class);
    PushyNotificationService pushy = null;

    IrcEventNotificationRule statusRule =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.KLINED,
            IrcEventNotificationRule.SourceMode.ANY,
            null,
            IrcEventNotificationRule.ChannelScope.ALL,
            null,
            false,
            IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
            true,
            true,
            false,
            "NOTIF_1",
            false,
            null,
            false,
            null,
            null,
            null);

    IrcEventNotificationRule soundRule =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.KLINED,
            IrcEventNotificationRule.SourceMode.ANY,
            null,
            IrcEventNotificationRule.ChannelScope.ALL,
            null,
            false,
            IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
            false,
            false,
            true,
            "NOTIF_3",
            false,
            null,
            false,
            null,
            null,
            null);

    when(rulesBus.get()).thenReturn(List.of(statusRule, soundRule));

    IrcEventNotificationService service =
        new IrcEventNotificationService(rulesBus, tray, store, pushy);

    boolean matched =
        service.notifyConfigured(
            IrcEventNotificationRule.EventType.KLINED,
            "libera",
            null,
            "alice",
            Boolean.FALSE,
            "User restricted",
            "alice appears restricted");

    assertTrue(matched);
    verify(tray, times(2))
        .notifyCustom(
            eq("libera"),
            eq("status"),
            anyString(),
            anyString(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.any(IrcEventNotificationRule.FocusScope.class),
            org.mockito.ArgumentMatchers.anyBoolean(),
            anyString(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.isNull());
    verify(store, times(1))
        .recordIrcEvent(
            eq("libera"),
            eq("status"),
            eq("alice"),
            anyString(),
            anyString());
  }
}
