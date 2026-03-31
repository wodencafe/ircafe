package cafe.woden.ircclient.ui.shell;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import cafe.woden.ircclient.irc.port.IrcLagProbePort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class LagIndicatorServiceTest {

  @Test
  void passiveLagBackendsDoNotTriggerActiveProbeRequests() throws Exception {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    IrcLagProbePort lagProbePort = mock(IrcLagProbePort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(lagProbePort.currentNick("libera")).thenReturn(Optional.of("ircafe"));
    when(lagProbePort.isLagProbeReady("libera")).thenReturn(true);
    when(lagProbePort.shouldRequestLagProbe("libera")).thenReturn(false);
    when(lagProbePort.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.of(123L));
    LagIndicatorService service =
        new LagIndicatorService(runtimeConfig, statusBar, activeTargetPort, lagProbePort);

    try {
      invokeCheckLagSafely(service);
    } finally {
      service.shutdown();
    }

    verify(lagProbePort, never()).requestLagProbe("libera");
    verify(statusBar).setLagIndicatorReading(123L, "Round-trip lag to 'libera': 123 ms.");
  }

  @Test
  void passiveLagBackendsRequestInitialProbeBeforeAnyLagSampleExists() throws Exception {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    IrcLagProbePort lagProbePort = mock(IrcLagProbePort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(lagProbePort.currentNick("libera")).thenReturn(Optional.of("ircafe"));
    when(lagProbePort.isLagProbeReady("libera")).thenReturn(true);
    when(lagProbePort.shouldRequestLagProbe("libera")).thenReturn(false);
    when(lagProbePort.requestLagProbe("libera")).thenReturn(Completable.complete());
    when(lagProbePort.lastMeasuredLagMs("libera"))
        .thenReturn(OptionalLong.empty(), OptionalLong.empty(), OptionalLong.of(88L));
    LagIndicatorService service =
        new LagIndicatorService(runtimeConfig, statusBar, activeTargetPort, lagProbePort);

    try {
      invokeCheckLagSafely(service);
    } finally {
      service.shutdown();
    }

    verify(lagProbePort).requestLagProbe("libera");
    verify(statusBar).setLagIndicatorReading(88L, "Round-trip lag to 'libera': 88 ms.");
  }

  @Test
  void passiveLagBackendsRequestFallbackProbeWhenFreshLagTurnsStale() throws Exception {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    IrcLagProbePort lagProbePort = mock(IrcLagProbePort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(lagProbePort.currentNick("libera")).thenReturn(Optional.of("ircafe"));
    when(lagProbePort.isLagProbeReady("libera")).thenReturn(true);
    when(lagProbePort.shouldRequestLagProbe("libera")).thenReturn(false);
    when(lagProbePort.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.of(123L));
    LagIndicatorService service =
        new LagIndicatorService(runtimeConfig, statusBar, activeTargetPort, lagProbePort);

    try {
      invokeCheckLagSafely(service);
      when(lagProbePort.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.empty());
      when(lagProbePort.requestLagProbe("libera")).thenReturn(Completable.complete());

      invokeCheckLagSafely(service);
    } finally {
      service.shutdown();
    }

    verify(lagProbePort).requestLagProbe("libera");
    verify(statusBar).setLagIndicatorReading(null, "Refreshing lag for 'libera'...");
  }

  @Test
  void passiveLagBackendsRateLimitInitialProbeRetriesWhenLagStillMissing() throws Exception {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    IrcLagProbePort lagProbePort = mock(IrcLagProbePort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(lagProbePort.currentNick("libera")).thenReturn(Optional.of("ircafe"));
    when(lagProbePort.isLagProbeReady("libera")).thenReturn(true);
    when(lagProbePort.shouldRequestLagProbe("libera")).thenReturn(false);
    when(lagProbePort.requestLagProbe("libera")).thenReturn(Completable.complete());
    when(lagProbePort.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.empty());
    LagIndicatorService service =
        new LagIndicatorService(runtimeConfig, statusBar, activeTargetPort, lagProbePort);

    try {
      invokeCheckLagSafely(service);
      invokeCheckLagSafely(service);
    } finally {
      service.shutdown();
    }

    verify(lagProbePort).requestLagProbe("libera");
  }

  @Test
  void waitsForLagProbeReadinessBeforeSendingInitialProbe() throws Exception {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    IrcLagProbePort lagProbePort = mock(IrcLagProbePort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(lagProbePort.currentNick("libera")).thenReturn(Optional.of("ircafe"));
    when(lagProbePort.isLagProbeReady("libera")).thenReturn(false);
    when(lagProbePort.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.empty());
    LagIndicatorService service =
        new LagIndicatorService(runtimeConfig, statusBar, activeTargetPort, lagProbePort);

    try {
      invokeCheckLagSafely(service);
    } finally {
      service.shutdown();
    }

    verify(lagProbePort, never()).requestLagProbe("libera");
    verify(statusBar).setLagIndicatorReading(null, "Waiting for connection setup on 'libera'...");
  }

  @Test
  void passiveLagBackendsRetryWhenProbeRequestFailsBeforeSend() throws Exception {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    IrcLagProbePort lagProbePort = mock(IrcLagProbePort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(lagProbePort.currentNick("libera")).thenReturn(Optional.of("ircafe"));
    when(lagProbePort.isLagProbeReady("libera")).thenReturn(true);
    when(lagProbePort.shouldRequestLagProbe("libera")).thenReturn(false);
    when(lagProbePort.requestLagProbe("libera"))
        .thenReturn(Completable.error(new IllegalStateException("Registration not complete")));
    when(lagProbePort.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.empty());
    LagIndicatorService service =
        new LagIndicatorService(runtimeConfig, statusBar, activeTargetPort, lagProbePort);

    try {
      invokeCheckLagSafely(service);
      invokeCheckLagSafely(service);
    } finally {
      service.shutdown();
    }

    verify(lagProbePort, times(2)).requestLagProbe("libera");
  }

  @Test
  void activeLagBackendsStillRequestExplicitProbes() throws Exception {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ActiveTargetPort activeTargetPort = mock(ActiveTargetPort.class);
    IrcLagProbePort lagProbePort = mock(IrcLagProbePort.class);
    when(activeTargetPort.getActiveTarget()).thenReturn(new TargetRef("quassel", "#ircafe"));
    when(lagProbePort.currentNick("quassel")).thenReturn(Optional.of("ircafe"));
    when(lagProbePort.isLagProbeReady("quassel")).thenReturn(true);
    when(lagProbePort.shouldRequestLagProbe("quassel")).thenReturn(true);
    when(lagProbePort.requestLagProbe("quassel")).thenReturn(Completable.complete());
    when(lagProbePort.lastMeasuredLagMs("quassel")).thenReturn(OptionalLong.of(45L));
    LagIndicatorService service =
        new LagIndicatorService(runtimeConfig, statusBar, activeTargetPort, lagProbePort);

    try {
      invokeCheckLagSafely(service);
    } finally {
      service.shutdown();
    }

    verify(lagProbePort).requestLagProbe("quassel");
    verify(statusBar).setLagIndicatorReading(45L, "Round-trip lag to 'quassel': 45 ms.");
  }

  private static void invokeCheckLagSafely(LagIndicatorService service) throws Exception {
    Method method = LagIndicatorService.class.getDeclaredMethod("checkLagSafely");
    method.setAccessible(true);
    method.invoke(service);
  }
}
