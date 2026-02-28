package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApplicationDiagnosticsServiceTest {

  @Test
  void appendAssertjSwingStatusRoutesToAssertjBuffer() {
    UiPort ui = Mockito.mock(UiPort.class);
    ApplicationDiagnosticsService service = new ApplicationDiagnosticsService(ui);

    service.appendAssertjSwingStatus("  checkpoint passed  ");

    TargetRef target = TargetRef.applicationAssertjSwing();
    verify(ui).ensureTargetExists(target);
    verify(ui).appendStatus(target, "(assertj-swing)", "checkpoint passed");
  }

  @Test
  void notifyUiStallRecoveredNormalizesDurationAndEnqueuesNotice() {
    UiPort ui = Mockito.mock(UiPort.class);
    ApplicationDiagnosticsService service = new ApplicationDiagnosticsService(ui);

    service.notifyUiStallRecovered(-7L);

    TargetRef target = TargetRef.applicationAssertjSwing();
    String message = "Recovered from UI stall (0 ms).";
    verify(ui).ensureTargetExists(target);
    verify(ui).appendStatus(target, "(assertj-swing)", message);
    verify(ui).enqueueStatusNotice(message, target);
  }

  @Test
  void blankDiagnosticMessagesAreIgnored() {
    UiPort ui = Mockito.mock(UiPort.class);
    ApplicationDiagnosticsService service = new ApplicationDiagnosticsService(ui);

    service.appendJhiccupStatus("   ");
    service.appendAssertjSwingError(null);

    verifyNoInteractions(ui);
  }

  @Test
  void assertjAndJhiccupEventsAreBufferedAndCanBeCleared() {
    UiPort ui = Mockito.mock(UiPort.class);
    ApplicationDiagnosticsService service = new ApplicationDiagnosticsService(ui);

    service.appendAssertjSwingStatus("freeze watchdog ready");
    service.appendAssertjSwingError("EDT freeze detected");
    service.appendJhiccupStatus("jHiccup started");

    assertEquals(2, service.recentAssertjSwingEvents(10).size());
    assertEquals(1, service.recentJhiccupEvents(10).size());
    assertEquals("EDT freeze detected", service.recentAssertjSwingEvents(1).getFirst().summary());

    service.clearAssertjSwingEvents();
    service.clearJhiccupEvents();

    assertTrue(service.recentAssertjSwingEvents(10).isEmpty());
    assertTrue(service.recentJhiccupEvents(10).isEmpty());
  }

  @Test
  void unhandledExceptionsAreBufferedAndCanBeCleared() throws Exception {
    UiPort ui = Mockito.mock(UiPort.class);
    ApplicationDiagnosticsService service = new ApplicationDiagnosticsService(ui);

    invokeHandleUncaughtException(service, new IllegalArgumentException("boom"));

    assertTrue(
        service.recentUnhandledErrorEvents(20).stream()
            .anyMatch(e -> e.summary().contains("IllegalArgumentException: boom")));

    service.clearUnhandledErrorEvents();

    assertTrue(service.recentUnhandledErrorEvents(10).isEmpty());
  }

  @Test
  void changeStreamsEmitOnAppendAndClear() {
    UiPort ui = Mockito.mock(UiPort.class);
    ApplicationDiagnosticsService service = new ApplicationDiagnosticsService(ui);

    var assertjChanges = service.assertjSwingChangeStream().test();
    var jhiccupChanges = service.jhiccupChangeStream().test();

    service.appendAssertjSwingStatus("assertj");
    service.appendJhiccupStatus("jhiccup");
    service.clearAssertjSwingEvents();
    service.clearJhiccupEvents();

    assertEquals(java.util.List.of(1L, 2L), assertjChanges.values());
    assertEquals(java.util.List.of(1L, 2L), jhiccupChanges.values());
  }

  @Test
  void unhandledErrorChangeStreamEmitsOnAppendAndClear() throws Exception {
    UiPort ui = Mockito.mock(UiPort.class);
    ApplicationDiagnosticsService service = new ApplicationDiagnosticsService(ui);

    var unhandledChanges = service.unhandledErrorChangeStream().test();

    invokeHandleUncaughtException(service, new IllegalStateException("first"));
    service.clearUnhandledErrorEvents();

    assertEquals(java.util.List.of(1L, 2L), unhandledChanges.values());
  }

  private static void invokeHandleUncaughtException(
      ApplicationDiagnosticsService service, Throwable error) throws Exception {
    Method m =
        ApplicationDiagnosticsService.class.getDeclaredMethod(
            "handleUncaughtException", Thread.class, Throwable.class);
    m.setAccessible(true);
    m.invoke(service, Thread.currentThread(), error);
  }
}
