package cafe.woden.ircclient.app.monitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
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
  private final UiSettingsBus uiSettingsBus = Mockito.mock(UiSettingsBus.class);
  private final PublishProcessor<ServerIrcEvent> events = PublishProcessor.create();
  private final PublishProcessor<MonitorListService.Change> changes = PublishProcessor.create();

  private final MonitorIsonFallbackService service;

  MonitorIsonFallbackServiceTest() {
    when(irc.events()).thenReturn(events.onBackpressureBuffer());
    when(monitorListService.changes()).thenReturn(changes.onBackpressureBuffer());
    when(uiSettingsBus.get()).thenReturn(defaultUiSettings());
    when(irc.isMonitorAvailable("libera")).thenReturn(false);
    when(irc.sendRaw(eq("libera"), startsWith("ISON "))).thenReturn(Completable.complete());
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice", "bob"));
    service = new MonitorIsonFallbackService(irc, monitorListService, ui, uiSettingsBus);
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

  private static UiSettings defaultUiSettings() {
    return new UiSettings(
        "darcula",
        "Monospaced",
        12,
        true,
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
        "HH:mm:ss",
        true,
        100,
        200,
        true,
        "#6AA2FF",
        true,
        7,
        6,
        30,
        5);
  }
}
