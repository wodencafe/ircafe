package cafe.woden.ircclient.monitor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class MonitorSyncServiceTest {

  private final IrcClientService irc = mock(IrcClientService.class);
  private final MonitorListService monitorListService = mock(MonitorListService.class);
  private final PublishProcessor<ServerIrcEvent> events = PublishProcessor.create();
  private final MonitorSyncService service;

  MonitorSyncServiceTest() {
    when(irc.events()).thenReturn(events.onBackpressureBuffer());
    when(irc.sendRaw(anyString(), anyString())).thenReturn(Completable.complete());
    service = new MonitorSyncService(irc, monitorListService);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void readyEventAppliesMonitorListInChunksAndOnlyOncePerConnection() {
    when(irc.isMonitorAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMonitorLimit("libera")).thenReturn(2);
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice", "bob", "carol"));

    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", serverResponse(376)));

    var order = inOrder(irc);
    order.verify(irc).sendRaw("libera", "MONITOR C");
    order.verify(irc).sendRaw("libera", "MONITOR +alice,bob");
    order.verify(irc).sendRaw("libera", "MONITOR +carol");
    verify(irc, times(3)).sendRaw(eq("libera"), anyString());

    // Additional 005 lines after first sync should not replay until reconnect.
    events.onNext(new ServerIrcEvent("libera", serverResponse(5)));
    verify(irc, times(3)).sendRaw(eq("libera"), anyString());
  }

  @Test
  void disconnectClearsSyncStateAndReconnectResyncs() {
    when(irc.isMonitorAvailable("libera")).thenReturn(true);
    when(irc.negotiatedMonitorLimit("libera")).thenReturn(0);
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice"));

    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", serverResponse(376)));
    events.onNext(new ServerIrcEvent("libera", disconnected()));
    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", serverResponse(422)));

    verify(irc, times(2)).sendRaw("libera", "MONITOR C");
    verify(irc, times(2)).sendRaw("libera", "MONITOR +alice");
  }

  @Test
  void doesNotSyncWhenMonitorCapabilityUnavailable() {
    when(irc.isMonitorAvailable("libera")).thenReturn(false);
    when(monitorListService.listNicks("libera")).thenReturn(List.of("alice"));

    events.onNext(new ServerIrcEvent("libera", connected()));
    events.onNext(new ServerIrcEvent("libera", serverResponse(376)));

    verify(irc, never()).sendRaw(eq("libera"), anyString());
  }

  private static IrcEvent.Connected connected() {
    return new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "ircafe");
  }

  private static IrcEvent.Disconnected disconnected() {
    return new IrcEvent.Disconnected(Instant.now(), "bye");
  }

  private static IrcEvent.ServerResponseLine serverResponse(int code) {
    return new IrcEvent.ServerResponseLine(Instant.now(), code, "", "raw", "", Map.of());
  }
}
