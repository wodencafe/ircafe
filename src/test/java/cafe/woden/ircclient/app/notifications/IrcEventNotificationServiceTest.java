package cafe.woden.ircclient.app.notifications;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.app.TrayNotificationsPort;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class IrcEventNotificationServiceTest {

  @Test
  void appliesAllMatchingRulesForSameEventType() {
    IrcEventNotificationRulesBus rulesBus = mock(IrcEventNotificationRulesBus.class);
    TrayNotificationsPort tray = mock(TrayNotificationsPort.class);
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
    ExecutorService exec = Executors.newSingleThreadExecutor();
    try {
      IrcEventNotificationService service =
          new IrcEventNotificationService(rulesBus, tray, store, pushy, exec);

      boolean matched =
          service.notifyConfigured(
              IrcEventNotificationRule.EventType.KLINED,
              "libera",
              null,
              "alice",
              Boolean.FALSE,
              "User restricted",
              "alice appears restricted",
              "libera",
              "#general");

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
          .recordIrcEvent(eq("libera"), eq("status"), eq("alice"), anyString(), anyString());
    } finally {
      exec.shutdownNow();
    }
  }

  @Test
  void activeChannelOnlyScopeRequiresActiveTargetChannelOnSameServer() {
    IrcEventNotificationRulesBus rulesBus = mock(IrcEventNotificationRulesBus.class);
    TrayNotificationsPort tray = mock(TrayNotificationsPort.class);
    NotificationStore store = mock(NotificationStore.class);
    PushyNotificationService pushy = null;

    IrcEventNotificationRule activeOnlyRule =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.TOPIC_CHANGED,
            IrcEventNotificationRule.SourceMode.ANY,
            null,
            IrcEventNotificationRule.ChannelScope.ACTIVE_TARGET_ONLY,
            null,
            true,
            IrcEventNotificationRule.FocusScope.ANY,
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
    when(rulesBus.get()).thenReturn(List.of(activeOnlyRule));

    ExecutorService exec = Executors.newSingleThreadExecutor();
    try {
      IrcEventNotificationService service =
          new IrcEventNotificationService(rulesBus, tray, store, pushy, exec);

      boolean noMatchDifferentChannel =
          service.notifyConfigured(
              IrcEventNotificationRule.EventType.TOPIC_CHANGED,
              "libera",
              "#chat",
              "alice",
              Boolean.FALSE,
              "Topic changed",
              "changed",
              "libera",
              "#other");
      boolean noMatchDifferentServer =
          service.notifyConfigured(
              IrcEventNotificationRule.EventType.TOPIC_CHANGED,
              "libera",
              "#chat",
              "alice",
              Boolean.FALSE,
              "Topic changed",
              "changed",
              "oftc",
              "#chat");
      boolean matchesActiveChannel =
          service.notifyConfigured(
              IrcEventNotificationRule.EventType.TOPIC_CHANGED,
              "libera",
              "#chat",
              "alice",
              Boolean.FALSE,
              "Topic changed",
              "changed",
              "libera",
              "#chat");

      org.junit.jupiter.api.Assertions.assertFalse(noMatchDifferentChannel);
      org.junit.jupiter.api.Assertions.assertFalse(noMatchDifferentServer);
      assertTrue(matchesActiveChannel);
    } finally {
      exec.shutdownNow();
    }
  }
}
