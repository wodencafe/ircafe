package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreTraySettingsTest {

  @TempDir Path tempDir;

  @Test
  void persistsTraySettingsUnderNestedTraySection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberTrayEnabled(false);
    store.rememberTrayCloseToTray(true);
    store.rememberTrayCloseToTrayHintShown(true);
    store.rememberTrayMinimizeToTray(true);
    store.rememberTrayStartMinimized(true);
    store.rememberTrayNotifyHighlights(false);
    store.rememberTrayNotifyPrivateMessages(false);
    store.rememberTrayNotifyConnectionState(true);
    store.rememberTrayNotifyOnlyWhenUnfocused(false);
    store.rememberTrayNotifyOnlyWhenMinimizedOrHidden(true);
    store.rememberTrayNotifySuppressWhenTargetActive(true);
    store.rememberTrayLinuxDbusActionsEnabled(false);
    store.rememberTrayNotificationBackend(" NATIVE ");
    store.rememberTrayNotificationSoundsEnabled(false);
    store.rememberTrayNotificationSound(" NOTIF_2 ");
    store.rememberTrayNotificationSoundUseCustom(true);
    store.rememberTrayNotificationSoundCustomPath(" sounds/custom.wav ");

    Map<String, Object> tray = traySection(cfg);
    assertEquals(false, tray.get("enabled"));
    assertEquals(true, tray.get("closeToTray"));
    assertEquals(true, tray.get("closeToTrayHintShown"));
    assertEquals(true, tray.get("minimizeToTray"));
    assertEquals(true, tray.get("startMinimized"));
    assertEquals(false, tray.get("notifyHighlights"));
    assertEquals(false, tray.get("notifyPrivateMessages"));
    assertEquals(true, tray.get("notifyConnectionState"));
    assertEquals(false, tray.get("notifyOnlyWhenUnfocused"));
    assertEquals(true, tray.get("notifyOnlyWhenMinimizedOrHidden"));
    assertEquals(true, tray.get("notifySuppressWhenTargetActive"));
    assertEquals(false, tray.get("linuxDbusActionsEnabled"));
    assertEquals("native", tray.get("notificationBackend"));
    assertEquals(false, tray.get("notificationSoundsEnabled"));
    assertEquals("NOTIF_2", tray.get("notificationSound"));
    assertEquals(true, tray.get("notificationSoundUseCustom"));
    assertEquals("sounds/custom.wav", tray.get("notificationSoundCustomPath"));
  }

  @Test
  void normalizesTrayNotificationDefaultsAndRemovesBlankCustomSoundPath() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberTrayNotificationBackend(" ");
    store.rememberTrayNotificationSound(null);
    store.rememberTrayNotificationSoundCustomPath("sounds/custom.wav");
    store.rememberTrayNotificationSoundCustomPath(" ");

    Map<String, Object> tray = traySection(cfg);
    assertEquals("auto", tray.get("notificationBackend"));
    assertEquals("NOTIF_1", tray.get("notificationSound"));
    assertFalse(tray.containsKey("notificationSoundCustomPath"));
  }

  private static Map<String, Object> traySection(Path cfg) throws Exception {
    return RuntimeConfigYamlTestSupport.uiSection(cfg, "tray");
  }
}
