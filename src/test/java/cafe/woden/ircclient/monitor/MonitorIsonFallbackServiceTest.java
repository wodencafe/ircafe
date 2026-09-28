package cafe.woden.ircclient.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import cafe.woden.ircclient.app.api.UiSettingsSnapshotTestFixtures;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.port.IrcMonitorPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MonitorIsonFallbackServiceTest {

  private final IrcMonitorPort irc = Mockito.mock(IrcMonitorPort.class);
  private final MonitorListService monitorListService = Mockito.mock(MonitorListService.class);
  private final UiPort ui = Mockito.mock(UiPort.class);
  private final UiSettingsPort uiSettingsPort = Mockito.mock(UiSettingsPort.class);
  private final PublishProcessor<ServerIrcEvent> events = PublishProcessor.create();
  private final PublishProcessor<MonitorListService.Change> changes = PublishProcessor.create();
  private final ScheduledExecutorService pollScheduler =
      Executors.newSingleThreadScheduledExecutor();

  private final MonitorIsonFallbackService service;

  MonitorIsonFallbackServiceTest() {
    when(irc.events()).thenReturn(events.onBackpressureBuffer());
    when(monitorListService.changes()).thenReturn(changes.onBackpressureBuffer());
    when(uiSettingsPort.get()).thenReturn(defaultUiSettings());
    when(irc.isMonitorAvailable("libera")).thenReturn(false);
    when(irc.sendRaw(eq("libera"), startsWith("ISON "))).thenReturn(Completable.complete());
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice", "bob"));
    service =
        new MonitorIsonFallbackService(irc, monitorListService, ui, uiSettingsPort, pollScheduler);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
    pollScheduler.shutdownNow();
  }

  @Test
  void activatesAfterReadyAndAppliesIsonPresenceTransitions() {
    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", connectionReady()));

    service.requestImmediateRefresh("libera");
    events.onNext(new ServerIrcEvent("libera", serverResponse(303, ":server 303 me :alice")));

    verify(irc, atLeastOnce()).sendRaw("libera", "ISON alice bob");
    verify(ui).setPrivateMessageOnlineState("libera", "alice", true);
    verify(ui).setPrivateMessageOnlineState("libera", "bob", false);
    verify(ui, atLeastOnce())
        .appendStatusAt(
            eq(TargetRef.monitorGroup("libera")), any(), eq("(monitor)"), eq("Online: alice"));
    verify(ui, atLeastOnce())
        .appendStatusAt(
            eq(TargetRef.monitorGroup("libera")), any(), eq("(monitor)"), eq("Offline: bob"));
  }

  @Test
  void suppressHintOnlyWhileFallbackIsEligible() {
    events.onNext(new ServerIrcEvent("libera", connected()));
    // Not ready yet.
    org.junit.jupiter.api.Assertions.assertFalse(
        service.shouldSuppressIsonServerResponse("libera"));

    events.onNext(new ServerIrcEvent("libera", connectionReady()));
    org.junit.jupiter.api.Assertions.assertTrue(service.shouldSuppressIsonServerResponse("libera"));
  }

  @Test
  void nativeMonitorPreventsIsonPolling() {
    when(irc.isMonitorAvailable("libera")).thenReturn(true);
    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", connectionReady()));
    service.requestImmediateRefresh("libera");

    assertFalse(service.isFallbackActive("libera"));
    verify(irc, never()).sendRaw(anyString(), anyString());
  }

  @Test
  void featureUpdateDisablesFallbackAndIgnoresOutstandingIsonReply() {
    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", connectionReady()));
    assertTrue(service.isFallbackActive("libera"));

    when(irc.isMonitorAvailable("libera")).thenReturn(true);
    events.onNext(
        new ServerIrcEvent(
            "libera", new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), "isupport")));
    events.onNext(new ServerIrcEvent("libera", serverResponse(303, ":server 303 me :alice")));

    assertFalse(service.isFallbackActive("libera"));
    assertFalse(service.shouldSuppressIsonServerResponse("libera"));
    verify(ui, never()).setPrivateMessageOnlineState(anyString(), anyString(), anyBoolean());
  }

  @Test
  void shutdownDisposesEventsRosterChangesAndPendingIsonCommand() throws Exception {
    CompletableSubject pending = CompletableSubject.create();
    when(irc.sendRaw(eq("libera"), startsWith("ISON "))).thenReturn(pending);
    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", connectionReady()));
    // Drain the immediate scheduled poll before checking ownership and shutting down.
    pollScheduler.submit(() -> {}).get(2, TimeUnit.SECONDS);
    assertTrue(events.hasSubscribers());
    assertTrue(changes.hasSubscribers());
    assertTrue(pending.hasObservers());

    service.shutdown();

    assertFalse(events.hasSubscribers());
    assertFalse(changes.hasSubscribers());
    assertFalse(pending.hasObservers());
    assertFalse(service.isFallbackActive("libera"));
    assertFalse(pollScheduler.isShutdown(), "the injected scheduler is owned by its configuration");
  }

  private static IrcEvent.ServerResponseLine serverResponse(int code, String rawLine) {
    return new IrcEvent.ServerResponseLine(Instant.now(), code, "", rawLine, "", Map.of());
  }

  private static IrcEvent.Connected connected() {
    return new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "ircafe");
  }

  private static IrcEvent.ConnectionReady connectionReady() {
    return new IrcEvent.ConnectionReady(Instant.now());
  }

  private static UiSettingsSnapshot defaultUiSettings() {
    return UiSettingsSnapshotTestFixtures.defaults();
  }
}
