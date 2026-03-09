package cafe.woden.ircclient.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.pushy.PushySettingsBus;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettingsBus;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.pushy.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class NotifyModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final PushySettingsBus pushySettingsBus;
  private final PushyNotificationService pushyNotificationService;
  private final NotificationSoundSettingsBus notificationSoundSettingsBus;
  private final NotificationSoundService notificationSoundService;

  NotifyModuleIntegrationTest(
      ApplicationContext applicationContext,
      PushySettingsBus pushySettingsBus,
      PushyNotificationService pushyNotificationService,
      NotificationSoundSettingsBus notificationSoundSettingsBus,
      NotificationSoundService notificationSoundService) {
    this.applicationContext = applicationContext;
    this.pushySettingsBus = pushySettingsBus;
    this.pushyNotificationService = pushyNotificationService;
    this.notificationSoundSettingsBus = notificationSoundSettingsBus;
    this.notificationSoundService = notificationSoundService;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesNotificationInfrastructureBeans() {
    assertEquals(1, applicationContext.getBeansOfType(PushySettingsBus.class).size());
    assertEquals(1, applicationContext.getBeansOfType(PushyNotificationService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(NotificationSoundSettingsBus.class).size());
    assertEquals(1, applicationContext.getBeansOfType(NotificationSoundService.class).size());
    assertNotNull(pushySettingsBus);
    assertNotNull(pushyNotificationService);
    assertNotNull(notificationSoundSettingsBus);
    assertNotNull(notificationSoundService);
  }

  @Test
  void pushyDisabledConfigurationShortCircuitsTestNotification() {
    assertNotNull(pushySettingsBus.get());
    assertFalse(Boolean.TRUE.equals(pushySettingsBus.get().enabled()));

    PushyNotificationService.PushResult result =
        pushyNotificationService.sendTestNotification(pushySettingsBus.get(), "Test", "Body");

    assertFalse(result.success());
    assertTrue(result.message().toLowerCase(Locale.ROOT).contains("disabled"));
  }

  @Test
  void soundSettingsBusProvidesNonBlankDefaultSoundId() {
    var settings = notificationSoundSettingsBus.get();
    assertNotNull(settings);
    assertNotNull(settings.soundId());
    assertTrue(!settings.soundId().isBlank());
  }
}
