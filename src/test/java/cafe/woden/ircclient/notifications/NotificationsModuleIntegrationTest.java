package cafe.woden.ircclient.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class NotificationsModuleIntegrationTest {

  @MockitoBean PushyNotificationService pushyNotificationService;
  @MockitoBean TrayNotificationsPort trayNotificationsPort;
  @MockitoBean UiSettingsPort uiSettingsPort;

  private final ApplicationContext applicationContext;
  private final IrcEventNotificationService ircEventNotificationService;
  private final IrcEventNotificationRulesBus rulesBus;
  private final NotificationStore notificationStore;
  private final IrcEventNotifierPort ircEventNotifierPort;
  private final NotificationRuleMatcherPort notificationRuleMatcherPort;

  NotificationsModuleIntegrationTest(
      ApplicationContext applicationContext,
      IrcEventNotificationService ircEventNotificationService,
      IrcEventNotificationRulesBus rulesBus,
      NotificationStore notificationStore,
      IrcEventNotifierPort ircEventNotifierPort,
      NotificationRuleMatcherPort notificationRuleMatcherPort) {
    this.applicationContext = applicationContext;
    this.ircEventNotificationService = ircEventNotificationService;
    this.rulesBus = rulesBus;
    this.notificationStore = notificationStore;
    this.ircEventNotifierPort = ircEventNotifierPort;
    this.notificationRuleMatcherPort = notificationRuleMatcherPort;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesNotificationModuleBeans() {
    assertEquals(1, applicationContext.getBeansOfType(IrcEventNotificationService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcEventNotificationRulesBus.class).size());
    assertEquals(1, applicationContext.getBeansOfType(NotificationStore.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcEventNotifierPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(NotificationRuleMatcherPort.class).size());
    assertNotNull(ircEventNotificationService);
    assertNotNull(rulesBus);
    assertNotNull(notificationStore);
    assertEquals(IrcEventNotificationService.class, AopUtils.getTargetClass(ircEventNotifierPort));
    assertEquals(
        NotificationRuleMatcher.class, AopUtils.getTargetClass(notificationRuleMatcherPort));
  }

  @Test
  void matchingEventRuleRecordsStoreAndDispatchesTrayAndPushyActions() {
    String serverId = "libera";
    String channel = "#ircafe";

    notificationStore.clearServer(serverId);
    clearInvocations(trayNotificationsPort, pushyNotificationService);

    IrcEventNotificationRule rule =
        new IrcEventNotificationRule(
            true,
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            IrcEventNotificationRule.SourceMode.ANY,
            null,
            IrcEventNotificationRule.ChannelScope.ALL,
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
    rulesBus.set(List.of(rule));

    boolean matched =
        ircEventNotificationService.notifyConfigured(
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            serverId,
            channel,
            "alice",
            Boolean.FALSE,
            "PM received",
            "hello from alice",
            "libera",
            "#status");

    assertTrue(matched);
    assertEquals(1, notificationStore.count(serverId));

    verify(trayNotificationsPort)
        .notifyCustom(
            serverId,
            channel,
            "PM received",
            "hello from alice",
            true,
            true,
            IrcEventNotificationRule.FocusScope.ANY,
            false,
            "NOTIF_1",
            false,
            null);
    verify(pushyNotificationService)
        .notifyEvent(
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            serverId,
            channel,
            "alice",
            Boolean.FALSE,
            "PM received",
            "hello from alice");
  }

  @Test
  void uiSettingsCooldownOverrideAffectsNotificationStoreDeduplication() {
    String serverId = "libera";
    TargetRef channelTarget = new TargetRef(serverId, "#ircafe");
    when(uiSettingsPort.get()).thenReturn(new UiSettingsSnapshot(List.of(), 0, 30, true, true));

    notificationStore.clearServer(serverId);
    notificationStore.recordRuleMatch(channelTarget, "alice", "Rule A", "first");
    notificationStore.recordRuleMatch(channelTarget, "alice", "Rule A", "second");

    assertEquals(2, notificationStore.listAllRuleMatches(serverId).size());
  }

  @Test
  void disabledRuleDoesNotMatchOrDispatchNotifications() {
    String serverId = "libera";
    String channel = "#ircafe";

    notificationStore.clearServer(serverId);
    clearInvocations(trayNotificationsPort, pushyNotificationService);

    IrcEventNotificationRule disabledRule =
        new IrcEventNotificationRule(
            false,
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            IrcEventNotificationRule.SourceMode.ANY,
            null,
            IrcEventNotificationRule.ChannelScope.ALL,
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
    rulesBus.set(List.of(disabledRule));

    assertTrue(
        !ircEventNotifierPort.hasEnabledRuleFor(
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED));

    boolean matched =
        ircEventNotifierPort.notifyConfigured(
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            serverId,
            channel,
            "alice",
            Boolean.FALSE,
            "PM received",
            "hello from alice",
            "libera",
            "#status");

    assertTrue(!matched);
    assertEquals(0, notificationStore.count(serverId));
    verifyNoInteractions(trayNotificationsPort, pushyNotificationService);
  }
}
