package cafe.woden.ircclient.ui.tray;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.ui.settings.MemoryUsageDisplayMode;
import cafe.woden.ircclient.ui.settings.NotificationBackendMode;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.shell.MainFrame;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class TrayNotificationServiceTest {

  @Test
  void activeBufferSuppressionDoesNotApplyWhenWindowUnfocused() throws Exception {
    TargetRef target = new TargetRef("libera", "#ircafe");
    TrayNotificationService service = newService(baseSettings(true, true), false, target);
    try {
      assertTrue(invokePassesNotifyConditions(service, target, false));
    } finally {
      service.shutdown();
    }
  }

  @Test
  void activeBufferSuppressionAppliesWhenWindowFocused() throws Exception {
    TargetRef target = new TargetRef("libera", "#ircafe");
    TrayNotificationService service = newService(baseSettings(false, true), true, target);
    try {
      assertFalse(invokePassesNotifyConditions(service, target, false));
    } finally {
      service.shutdown();
    }
  }

  private static TrayNotificationService newService(
      UiSettings settings, boolean frameActive, TargetRef activeTarget) {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settings);

    MainFrame frame = mock(MainFrame.class);
    when(frame.isVisible()).thenReturn(true);
    when(frame.isActive()).thenReturn(frameActive);

    ActiveTargetPort targetCoordinator = mock(ActiveTargetPort.class);
    when(targetCoordinator.getActiveTarget()).thenReturn(activeTarget);

    @SuppressWarnings("unchecked")
    ObjectProvider<TrayService> trayProvider = mock(ObjectProvider.class);
    when(trayProvider.getIfAvailable()).thenReturn(null);
    @SuppressWarnings("unchecked")
    ObjectProvider<MainFrame> mainFrameProvider = mock(ObjectProvider.class);
    when(mainFrameProvider.getIfAvailable()).thenReturn(frame);
    @SuppressWarnings("unchecked")
    ObjectProvider<cafe.woden.ircclient.ui.shell.StatusBar> statusBarProvider =
        mock(ObjectProvider.class);
    when(statusBarProvider.getIfAvailable()).thenReturn(null);
    @SuppressWarnings("unchecked")
    ObjectProvider<ActiveTargetPort> targetCoordinatorProvider = mock(ObjectProvider.class);
    when(targetCoordinatorProvider.getIfAvailable()).thenReturn(targetCoordinator);
    @SuppressWarnings("unchecked")
    ObjectProvider<cafe.woden.ircclient.ui.servertree.ServerTreeDockable> serverTreeProvider =
        mock(ObjectProvider.class);
    when(serverTreeProvider.getIfAvailable()).thenReturn(null);
    @SuppressWarnings("unchecked")
    ObjectProvider<cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend>
        gnomeDbusProvider = mock(ObjectProvider.class);
    when(gnomeDbusProvider.getIfAvailable()).thenReturn(null);

    NotificationSoundService soundService = mock(NotificationSoundService.class);

    return new TrayNotificationService(
        settingsBus,
        trayProvider,
        mainFrameProvider,
        statusBarProvider,
        targetCoordinatorProvider,
        serverTreeProvider,
        gnomeDbusProvider,
        soundService);
  }

  private static boolean invokePassesNotifyConditions(
      TrayNotificationService service, TargetRef target, boolean allowWhenFocused)
      throws Exception {
    Method m =
        TrayNotificationService.class.getDeclaredMethod(
            "passesNotifyConditions", TargetRef.class, boolean.class);
    m.setAccessible(true);
    return (boolean) m.invoke(service, target, allowWhenFocused);
  }

  private static UiSettings baseSettings(
      boolean onlyWhenUnfocused, boolean suppressWhenTargetActive) {
    return new UiSettings(
        "darcula",
        "Monospaced",
        12,
        true,
        true,
        false,
        false,
        false,
        true,
        true,
        false,
        onlyWhenUnfocused,
        false,
        suppressWhenTargetActive,
        true,
        NotificationBackendMode.AUTO,
        false,
        false,
        0,
        0,
        true,
        false,
        false,
        true,
        true,
        true,
        true,
        "dots",
        true,
        "HH:mm:ss",
        true,
        true,
        100,
        200,
        2000,
        20,
        10,
        6,
        false,
        6,
        18,
        360,
        500,
        4000,
        true,
        "#6AA2FF",
        true,
        7,
        6,
        30,
        5,
        false,
        15,
        3,
        60,
        5,
        false,
        45,
        120,
        false,
        300,
        2,
        30,
        15,
        MemoryUsageDisplayMode.LONG,
        5,
        true,
        false,
        false,
        false,
        List.of());
  }
}
