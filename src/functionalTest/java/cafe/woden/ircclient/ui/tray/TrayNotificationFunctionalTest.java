package cafe.woden.ircclient.ui.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.ui.settings.MemoryUsageDisplayMode;
import cafe.woden.ircclient.ui.settings.NotificationBackendMode;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.shell.StatusBar;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class TrayNotificationFunctionalTest {

  @Test
  void customNotificationEnqueuesStatusNoticeAndRoutesClickToTarget() throws Exception {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(baseSettings());

    TrayService trayService = mock(TrayService.class);
    cafe.woden.ircclient.ui.servertree.ServerTreeDockable serverTree =
        mock(cafe.woden.ircclient.ui.servertree.ServerTreeDockable.class);
    StatusBar statusBar = mock(StatusBar.class);
    NotificationSoundService soundService = mock(NotificationSoundService.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("libera", "#elsewhere"));

    AtomicReference<String> noticeText = new AtomicReference<>();
    AtomicReference<Runnable> noticeClick = new AtomicReference<>();
    doAnswer(
            inv -> {
              noticeText.set(inv.getArgument(0, String.class));
              noticeClick.set(inv.getArgument(1, Runnable.class));
              return null;
            })
        .when(statusBar)
        .enqueueNotification(anyString(), any(Runnable.class));

    @SuppressWarnings("unchecked")
    ObjectProvider<TrayService> trayProvider = mock(ObjectProvider.class);
    when(trayProvider.getIfAvailable()).thenReturn(trayService);
    when(trayProvider.getObject()).thenReturn(trayService);

    @SuppressWarnings("unchecked")
    ObjectProvider<cafe.woden.ircclient.ui.shell.MainFrame> mainFrameProvider =
        mock(ObjectProvider.class);
    when(mainFrameProvider.getIfAvailable()).thenReturn(null);

    @SuppressWarnings("unchecked")
    ObjectProvider<StatusBar> statusBarProvider = mock(ObjectProvider.class);
    when(statusBarProvider.getIfAvailable()).thenReturn(statusBar);

    @SuppressWarnings("unchecked")
    ObjectProvider<ActiveTargetPort> targetPortProvider = mock(ObjectProvider.class);
    when(targetPortProvider.getIfAvailable()).thenReturn(activeTargetPort);

    @SuppressWarnings("unchecked")
    ObjectProvider<cafe.woden.ircclient.ui.servertree.ServerTreeDockable> serverTreeProvider =
        mock(ObjectProvider.class);
    when(serverTreeProvider.getIfAvailable()).thenReturn(serverTree);
    when(serverTreeProvider.getObject()).thenReturn(serverTree);

    @SuppressWarnings("unchecked")
    ObjectProvider<cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend>
        gnomeDbusProvider = mock(ObjectProvider.class);
    when(gnomeDbusProvider.getIfAvailable()).thenReturn(null);

    TrayNotificationService service =
        new TrayNotificationService(
            settingsBus,
            trayProvider,
            mainFrameProvider,
            statusBarProvider,
            targetPortProvider,
            serverTreeProvider,
            gnomeDbusProvider,
            soundService);

    TargetRef target = new TargetRef("libera", "#functional");
    try {
      service.notifyCustom(
          target.serverId(),
          target.target(),
          "Rule Hit",
          "contains keyword",
          false,
          true,
          IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
          false,
          null,
          false,
          null);

      waitFor(() -> noticeClick.get() != null, Duration.ofSeconds(2));
      assertEquals("Rule Hit: contains keyword", noticeText.get());

      noticeClick.get().run();
      flushEdt();

      verify(trayService).showMainWindow();
      verify(serverTree).selectTarget(target);
    } finally {
      service.shutdown();
    }
  }

  private static UiSettings baseSettings() {
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
        false,
        false,
        true,
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

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }
}
