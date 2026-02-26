package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AssertjSwingDiagnosticsServiceTest {

  @Test
  void startReportsDisabledWhenConfiguredOff() {
    ApplicationDiagnosticsService diagnostics = mock(ApplicationDiagnosticsService.class);
    UiProperties uiProps = mock(UiProperties.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<NotificationSoundService> soundProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<TrayNotificationsPort> trayProvider = mock(ObjectProvider.class);
    ScheduledExecutorService watchdogExec = mock(ScheduledExecutorService.class);

    when(uiProps.appDiagnostics())
        .thenReturn(
            new UiProperties.AppDiagnostics(
                new UiProperties.AssertjSwing(false, true, 2500, 500, 5000, false, false), null));

    AssertjSwingDiagnosticsService service =
        new AssertjSwingDiagnosticsService(
            diagnostics, uiProps, runtimeConfig, soundProvider, trayProvider, watchdogExec);

    service.start();

    verify(diagnostics).appendAssertjSwingStatus("Disabled by configuration.");
    verifyNoInteractions(watchdogExec);
  }

  @Test
  void shutdownCancelsExistingWatchdogTask() throws Exception {
    ApplicationDiagnosticsService diagnostics = mock(ApplicationDiagnosticsService.class);
    UiProperties uiProps = mock(UiProperties.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<NotificationSoundService> soundProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<TrayNotificationsPort> trayProvider = mock(ObjectProvider.class);
    ScheduledExecutorService watchdogExec = mock(ScheduledExecutorService.class);
    @SuppressWarnings("unchecked")
    ScheduledFuture<?> watchdogTask = mock(ScheduledFuture.class);

    AssertjSwingDiagnosticsService service =
        new AssertjSwingDiagnosticsService(
            diagnostics, uiProps, runtimeConfig, soundProvider, trayProvider, watchdogExec);

    setField(service, "watchdogTask", watchdogTask);
    service.shutdown();

    verify(watchdogTask).cancel(true);
    assertNull(getField(service, "watchdogTask"));
  }

  @Test
  void markFrozenPublishesEdtStackLinesToDiagnostics() throws Exception {
    ApplicationDiagnosticsService diagnostics = mock(ApplicationDiagnosticsService.class);
    UiProperties uiProps = mock(UiProperties.class);
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<NotificationSoundService> soundProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<TrayNotificationsPort> trayProvider = mock(ObjectProvider.class);
    ScheduledExecutorService watchdogExec = mock(ScheduledExecutorService.class);

    AssertjSwingDiagnosticsService service =
        new AssertjSwingDiagnosticsService(
            diagnostics, uiProps, runtimeConfig, soundProvider, trayProvider, watchdogExec);

    // Ensure the EDT thread exists in this JVM before capturing snapshot.
    SwingUtilities.invokeAndWait(() -> {});

    // Keep this unit test from starting asynchronous auto-capture threads.
    AtomicLong nextAutoCaptureAtMs = atomicLongField(service, "nextAutoCaptureAtMs");
    nextAutoCaptureAtMs.set(System.currentTimeMillis() + 60_000L);

    invokeMarkFrozen(service, 2503L);

    verify(diagnostics).appendAssertjSwingError("EDT freeze detected (~2503ms).");
    verify(diagnostics, atLeastOnce())
        .appendAssertjSwingStatus(
            argThat(msg -> msg != null && msg.startsWith("(edt-freeze-stack)")));
  }

  @Test
  void formatStackTraceForLogIncludesFrameLinesAndRemainder() throws Exception {
    StackTraceElement[] stack =
        new StackTraceElement[] {
          new StackTraceElement("cafe.woden.ircclient.ui.MainFrame", "show", "MainFrame.java", 42),
          new StackTraceElement(
              "cafe.woden.ircclient.ui.ServerTreeDockable",
              "refresh",
              "ServerTreeDockable.java",
              99),
          new StackTraceElement(
              "cafe.woden.ircclient.app.core.IrcMediator", "tick", "IrcMediator.java", 1234)
        };

    String formatted =
        invokeFormatStackTraceForLog("AWT-EventQueue-0", Thread.State.BLOCKED, stack, 2);

    assertTrue(formatted.contains("AWT-EventQueue-0 state=BLOCKED"));
    assertTrue(formatted.contains("at cafe.woden.ircclient.ui.MainFrame.show(MainFrame.java:42)"));
    assertTrue(formatted.contains("... 1 more"));
  }

  private static Object getField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static AtomicLong atomicLongField(Object target, String name) throws Exception {
    return (AtomicLong) getField(target, name);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void invokeMarkFrozen(Object target, long lagMs) throws Exception {
    Method m = target.getClass().getDeclaredMethod("markFrozen", long.class);
    m.setAccessible(true);
    m.invoke(target, lagMs);
  }

  private static String invokeFormatStackTraceForLog(
      String threadName, Thread.State state, StackTraceElement[] stack, int maxFrames)
      throws Exception {
    Method m =
        AssertjSwingDiagnosticsService.class.getDeclaredMethod(
            "formatStackTraceForLog",
            String.class,
            Thread.State.class,
            StackTraceElement[].class,
            int.class);
    m.setAccessible(true);
    return (String) m.invoke(null, threadName, state, stack, maxFrames);
  }
}
