package cafe.woden.ircclient.app.monitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UiSettingsSnapshot;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MonitorIsonFallbackServiceTest {

  private final IrcClientService irc = Mockito.mock(IrcClientService.class);
  private final MonitorListService monitorListService = Mockito.mock(MonitorListService.class);
  private final UiPort ui = Mockito.mock(UiPort.class);
  private final UiSettingsPort uiSettingsPort = Mockito.mock(UiSettingsPort.class);
  private final PublishProcessor<ServerIrcEvent> events = PublishProcessor.create();
  private final PublishProcessor<MonitorListService.Change> changes = PublishProcessor.create();

  private final MonitorIsonFallbackService service;

  MonitorIsonFallbackServiceTest() {
    when(irc.events()).thenReturn(events.onBackpressureBuffer());
    when(monitorListService.changes()).thenReturn(changes.onBackpressureBuffer());
    when(uiSettingsPort.get()).thenReturn(defaultUiSettings());
    when(irc.isMonitorAvailable("libera")).thenReturn(false);
    when(irc.sendRaw(eq("libera"), startsWith("ISON "))).thenReturn(Completable.complete());
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice", "bob"));
    service = new MonitorIsonFallbackService(irc, monitorListService, ui, uiSettingsPort);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void activatesAfterReadyAndAppliesIsonPresenceTransitions() {
    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(
        new ServerIrcEvent("libera", serverResponse(376, ":server 376 me :End of /MOTD")));

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

    events.onNext(
        new ServerIrcEvent("libera", serverResponse(376, ":server 376 me :End of /MOTD")));
    org.junit.jupiter.api.Assertions.assertTrue(service.shouldSuppressIsonServerResponse("libera"));
  }

  private static IrcEvent.ServerResponseLine serverResponse(int code, String rawLine) {
    return new IrcEvent.ServerResponseLine(Instant.now(), code, "", rawLine, "", Map.of());
  }

  private static IrcEvent.Connected connected() {
    return new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "ircafe");
  }

  private static UiSettingsSnapshot defaultUiSettings() {
    return new UiSettingsSnapshot(List.of(), 15, 30, true, true);
  }
}
